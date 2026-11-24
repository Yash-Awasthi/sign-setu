"""Diffs the app's own verdict on an exported report against the off-phone check's.

`SealAgreementDeviceTest` writes a report and the verdict the app reached over it (`app.json`). This runs
`verify_seal.py` over the same bytes and compares every field the seal carries, so the two readers cannot drift apart:
the session, the sealed-entry count, the chain head, the Merkle root, the printed-rows digest and the key fingerprint,
and the verdict itself — the app says MATCHES_DEVICE exactly when the off-phone check says valid.

Exit status: 0 agree, 1 disagree, 2 the files cannot be read.
Run: python tools/security/seal_agreement.py --pdf report.pdf --app app.json
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import verify_seal  # noqa: E402

# The app's verdicts that mean "this seal holds".
HOLDING = {"MATCHES_DEVICE", "SIGNATURE_VALID"}


def fields_of(payload: str) -> dict[str, str]:
    """The seal line's named fields, as both readers see them."""
    f = payload.strip().split("|")
    if len(f) != 8 or f[0] != "SS1":
        raise ValueError(f"not a Sign Setu seal: {payload[:40]}")
    return {
        "session": f[1],
        "count": f[2],
        "head": f[3],
        "root": f[4],
        "digest": f[5],
    }


def compare(app: dict, tool: dict[str, str], tool_verdict: str) -> list[str]:
    """Every disagreement, in words. Empty when the two agree."""
    out = []
    for field in ("session", "head", "root", "digest"):
        if str(app.get(field)) != tool[field]:
            out.append(f"{field}: app {app.get(field)} != off-phone {tool[field]}")
    if int(app.get("count", -1)) != int(tool["count"]):
        out.append(f"count: app {app.get('count')} != off-phone {tool['count']}")
    if tool_verdict == "valid" and app.get("verdict") not in HOLDING:
        out.append(f"the off-phone check says valid, the app says {app.get('verdict')}")
    if tool_verdict == "invalid" and app.get("verdict") in HOLDING:
        out.append(f"the off-phone check says invalid, the app says {app.get('verdict')}")
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description="Diff the app's seal verdict against verify_seal.py's.")
    ap.add_argument("--pdf", required=True)
    ap.add_argument("--app", required=True, help="app.json written by SealAgreementDeviceTest")
    a = ap.parse_args()

    payload = verify_seal.seal_from_pdf(a.pdf)
    if not payload:
        print("this report has no seal", file=sys.stderr)
        return 2
    code, message = verify_seal.check(payload, json.loads(Path(a.app).read_text())["fingerprint"] if Path(a.app).exists() else None)
    if code == 2:
        print(f"the off-phone check cannot read the seal: {message}", file=sys.stderr)
        return 2
    tool_verdict = "valid" if code == 0 else "invalid"
    app = json.loads(Path(a.app).read_text())

    problems = compare(app, fields_of(payload), tool_verdict)
    print(f"off-phone: {tool_verdict} — {message}")
    print(f"app:       {app.get('verdict')}")
    if problems:
        for p in problems:
            print(f"  DISAGREE {p}", file=sys.stderr)
        return 1
    print("the two readers agree on every seal field")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
