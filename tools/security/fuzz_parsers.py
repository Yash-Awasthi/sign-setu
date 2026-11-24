"""Malformed input against the two readers that decide whether a record is authentic, on purpose and without mercy.

Two rules, and a violation of either is a release blocker (plan.md, M2.1): a reader must never crash on a file it cannot
understand, and it must never answer "valid" for anything it was not given. This throws garbage at

  * `tools/security/verify_seal.py` — the off-phone seal check, over payloads and over PDF files;
  * `brain/brain/inbox.py`'s record reader — the station's reader of `session.json` and `events.json` in a conversation zip,

with a fixed seed, so a failure can be reproduced. Run: `python tools/security/fuzz_parsers.py [--trials N] [--seed N]`.
Exit 0 clean, 1 a violation (each printed with the seed and the input's shape).
"""
from __future__ import annotations

import argparse
import base64
import io
import json
import logging
import random
import sys
import zipfile
from pathlib import Path

# pypdf narrates every file it refuses; the point here is that it refuses them, not how it says so.
logging.getLogger("pypdf").setLevel(logging.CRITICAL)

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(ROOT / "brain"))

import verify_seal  # noqa: E402
from brain.inbox import _record  # noqa: E402

SEAL_CODES = {0, 1, 2}


def a_signed_seal(seed: int = 7) -> str:
    """A genuine seal: the shape a phone writes, signed with a throwaway P-256 key."""
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import ec

    key = ec.generate_private_key(ec.SECP256R1())
    line = "SS1|session-1|3|" + "ab" * 32 + "|" + "cd" * 32
    sig = key.sign(line.encode(), ec.ECDSA(hashes.SHA256()))
    der = key.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
    return f"{line}|9474-ca87-91d8-0b9d|{base64.b64encode(sig).decode()}|{base64.b64encode(der).decode()}"


def mutations(payload: str, rng: random.Random, trials: int):
    """Payloads that differ from a genuine one, in the ways a hostile or broken writer would differ."""
    fields = payload.split("|")
    junk = [
        "",
        "|",
        "|||||||",
        "SS1",
        "SS2" + payload[3:],
        payload + "|extra",
        payload[:-1],
        payload[: len(payload) // 2],
        payload.replace("|", "|", 1) + "|||",
        "SS1|" + "x" * 100_000 + "|1|h|r|d|s|k",
        "SS1|s|-1|h|r|d|s|k",
        "SS1|s|abc|h|r|d|s|k",
        "SS1|s|3|h|r|d|-|-",
        "SS1|s|3|h|r|d|AAAA|-",
        "SS1|s|3|h|r|d|-|AAAA",
        "SS1|s|3|h|r|d|" + "!" * 64 + "|" + "!" * 64,
        "SS1|s|3|h|r|d|" + base64.b64encode(b"\x00" * 70).decode() + "|" + base64.b64encode(b"\x00" * 91).decode(),
        "SS1|\u202e\u0000|3|h|r|d|s|k",
        "SS1|s|3|h|r|d|" + "A" * 20_000 + "|" + "A" * 20_000,
    ]
    for candidate in junk:
        yield candidate
    for _ in range(trials):
        f = list(fields)
        kind = rng.choice(["flip", "drop", "swap", "dup", "pad"])
        i = rng.randrange(len(f))
        if kind == "flip":
            j = rng.randrange(len(f[i]) + 1)
            f[i] = f[i][:j] + rng.choice("Aa0|=-\x00\u202e") + f[i][j + 1 :]
        elif kind == "drop":
            del f[i]
        elif kind == "swap":
            k = rng.randrange(len(f))
            f[i], f[k] = f[k], f[i]
        elif kind == "dup":
            f.insert(i, f[i])
        else:
            f[i] = f[i] + "A" * rng.randrange(1, 5000)
        yield "|".join(f)


def signed_line(payload: str) -> str:
    """The five fields the signature covers; the digest, signature and key travel outside it."""
    return "|".join(payload.strip().split("|")[:5])


def fuzz_seal(trials: int, rng: random.Random) -> list[str]:
    """Never a crash, and never "valid" for a signed line other than the genuine one.

    A mutation outside the signed line — the printed-rows digest above all — can still verify: that is finding F-10 in
    docs/04-security.md, a documented limit of this seal and not something this fuzzer should hide."""
    problems = []
    good = a_signed_seal()
    good_line = signed_line(good)
    code, _ = verify_seal.check(good)
    if code != 0:
        problems.append(f"the genuine seal does not verify (code {code})")
    for payload in mutations(good, rng, trials):
        try:
            code, _message = verify_seal.check(payload, fingerprint=None)
        except Exception as e:  # noqa: BLE001 - any escape is the finding
            problems.append(f"seal reader raised {type(e).__name__}: {e} on {payload[:60]!r}")
            continue
        if code not in SEAL_CODES:
            problems.append(f"seal reader answered code {code} on {payload[:60]!r}")
        if code == 0 and signed_line(payload) != good_line:
            problems.append(f"seal reader called an altered line valid: {payload[:80]!r}")
    return problems


def fuzz_pdf(trials: int, rng: random.Random, tmp: Path) -> list[str]:
    """Any byte string at all: a file the reader cannot read is not a crash."""
    problems = []
    pdf = tmp / "seal-check.pdf"
    samples = [b"", b"%PDF-1.4", b"\x00" * 64, b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n" + bytes(rng.randrange(256) for _ in range(2048))]
    for _ in range(trials):
        n = rng.randrange(0, 4096)
        samples.append(bytes(rng.randrange(256) for _ in range(n)))
    for blob in samples:
        pdf.write_bytes(blob)
        try:
            payload = verify_seal.seal_from_pdf(str(pdf))
        except SystemExit:
            continue
        except Exception as e:  # noqa: BLE001 - an unreadable file must not escape as a traceback
            problems.append(f"pdf reader raised {type(e).__name__}: {e} on {len(blob)} bytes")
            continue
        if payload == "":
            continue
        if payload is not None and not isinstance(payload, str):
            problems.append(f"pdf reader returned {type(payload).__name__}, not text")
    pdf.unlink(missing_ok=True)
    return problems


def a_record_zip(session: bytes | str, events: bytes | str) -> bytes:
    out = io.BytesIO()
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for name, data in (("session.json", session), ("events.json", events)):
            z.writestr(name, data if isinstance(data, (bytes, bytearray)) else data.encode())
    return out.getvalue()


def fuzz_record(trials: int, rng: random.Random, tmp: Path) -> list[str]:
    """Any zip at all: the station gets (None, []) rather than a traceback."""
    problems = []
    path = tmp / "record.zip"
    good = a_record_zip(json.dumps({"id": "session-1"}), json.dumps([{"hash": "aa"}, {"hash": "bb"}]))
    cases = [
        b"",
        b"not a zip at all",
        a_record_zip("{", "[]"),
        a_record_zip("[]", "[]"),
        a_record_zip("null", "null"),
        a_record_zip(json.dumps({"id": None}), json.dumps([])),
        a_record_zip(json.dumps({"id": {"nested": True}}), json.dumps([{"hash": {"no": "yes"}}])),
        a_record_zip(json.dumps({"id": "s"}), json.dumps([1, 2, 3])),
        a_record_zip(json.dumps({"id": "s"}), json.dumps({"hash": "aa"})),
        a_record_zip("[" * 100_000 + "]" * 100_000, "[]"),
        a_record_zip(json.dumps({"id": "s"}), "[" * 100_000 + "]" * 100_000),
        a_record_zip(json.dumps({"id": "x" * 10_000_000}), "[]"),
        b"",
    ]
    with zipfile.ZipFile(io.BytesIO(good)):
        pass
    for _ in range(trials):
        # A zip whose members are cut short or hold random bytes.
        blob = bytearray(good)
        cut = rng.randrange(len(blob))
        cases.append(bytes(blob[:cut]) or b"\x00")
    for blob in cases:
        path.write_bytes(blob)
        try:
            session, rows = _record(path)
        except Exception as e:  # noqa: BLE001 - any escape is the finding
            problems.append(f"record reader raised {type(e).__name__}: {e} on {len(blob)} bytes")
            continue
        if session is not None and not isinstance(session, str):
            problems.append(f"record reader returned a {type(session).__name__} as the session id")
        if not isinstance(rows, list):
            problems.append(f"record reader returned a {type(rows).__name__} as the rows")
    session, rows = _record_zip(tmp / "record.zip")
    if (session, rows) != ("session-1", ["aa", "bb"]):
        problems.append(f"the genuine record is misread: {session!r}, {rows!r}")
    path.unlink(missing_ok=True)
    return problems


def _record_zip(path: Path) -> tuple[str | None, list]:
    path.write_bytes(a_record_zip(json.dumps({"id": "session-1"}), json.dumps([{"hash": "aa"}, {"hash": "bb"}])))
    return _record(path)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--trials", type=int, default=300, help="mutated inputs per reader (default 300)")
    ap.add_argument("--seed", type=int, default=1, help="the seed that reproduces a failure (default 1)")
    a = ap.parse_args()
    rng = random.Random(a.seed)
    import tempfile

    with tempfile.TemporaryDirectory() as tmpdir:
        tmp = Path(tmpdir)
        problems = {
            "verify_seal.py payloads": fuzz_seal(a.trials, rng),
            "verify_seal.py PDFs": fuzz_pdf(a.trials, rng, tmp),
            "the station's record reader": fuzz_record(a.trials, rng, tmp),
        }
    total = sum(len(v) for v in problems.values())
    for reader, found in problems.items():
        print(f"{reader}: {'clean' if not found else str(len(found)) + ' violation(s)'}")
        for p in found[:10]:
            print(f"  {p}", file=sys.stderr)
    if total:
        print(f"\n{total} violation(s) with seed {a.seed}; a crash or a false valid is a release blocker", file=sys.stderr)
        return 1
    print(f"\nno crash and no false valid over {a.trials} malformed inputs per reader, seed {a.seed}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
