"""Checks a Sign Setu report's verification seal away from the phone.

Give it the text of the report's QR code (`--qr`), or the exported PDF (`--pdf`), which carries the same seal in its
metadata. The seal line (the first five fields) must verify under the ECDSA P-256 device key the seal carries. With
`--key-fingerprint` the key must also be the one printed on the certificate. Reports exported by older builds were
encrypted; `--passphrase` still opens one of those, and is not needed for a current export.

Exit status: 0 valid, 1 signature invalid or key mismatch, 2 no seal or malformed.
Needs `cryptography`, and `pypdf` for `--pdf`.
"""
import argparse
import base64
import hashlib
import sys

SEAL_KEY = "/SignSetuSeal"


def seal_from_pdf(path: str, passphrase: str = "") -> str | None:
    """The seal the file carries: None when it carries none, "" when the file cannot be read as a PDF at all."""
    from pypdf import PdfReader

    try:
        reader = PdfReader(path)
        # Current exports are not encrypted; an older one opens with the passphrase shown when it was made.
        if reader.is_encrypted and not reader.decrypt(passphrase):
            raise SystemExit("the passphrase does not open this report")
        meta = reader.metadata or {}
        seal = meta.get(SEAL_KEY)
    except SystemExit:
        raise
    except Exception:  # noqa: BLE001 - a damaged or foreign file is an answer, not a traceback
        return ""
    return seal if isinstance(seal, str) else None


def check(payload: str, fingerprint: str | None = None) -> tuple[int, str]:
    from cryptography.exceptions import InvalidSignature
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import ec

    fields = payload.strip().split("|")
    if len(fields) != 8 or fields[0] != "SS1":
        return 2, "not a Sign Setu seal"
    line = "|".join(fields[:5])
    session, count, head, root = fields[1], fields[2], fields[3], fields[4]
    digest, sig, key = fields[5], fields[6], fields[7]
    if sig == "-" or key == "-":
        return 1, f"seal for session {session} carries no device signature"
    # Anything that is not base64 of a DER key is a seal this tool cannot check, never an error it dies of.
    try:
        der = base64.b64decode(key, validate=True)
        found = hashlib.sha256(der).hexdigest()
    except Exception:  # noqa: BLE001
        return 2, "the seal's key is not base64 of a DER public key"
    if fingerprint and found.lower() != fingerprint.lower().replace(":", "").replace(" ", ""):
        return 1, f"the seal's key {found} is not the certificate's key"
    try:
        public = serialization.load_der_public_key(der)
    except Exception:  # noqa: BLE001
        return 2, "the seal's key is not a readable DER public key"
    if not isinstance(public, ec.EllipticCurvePublicKey):
        return 2, "the seal's key is not an EC key"
    try:
        raw = base64.b64decode(sig + "=" * (-len(sig) % 4), validate=True)
    except Exception:  # noqa: BLE001
        return 2, "the seal's signature is not base64"
    try:
        public.verify(raw, line.encode(), ec.ECDSA(hashes.SHA256()))
    except InvalidSignature:
        return 1, "INVALID: the signature does not match the seal line; treat the report as altered"
    except Exception:  # noqa: BLE001 - a signature of the wrong shape is not a valid one
        return 1, "INVALID: the signature cannot be checked against the seal line"
    return 0, (f"valid: session {session}, {count} sealed entries, chain head {head}, Merkle root {root}, "
               f"printed-rows digest {digest}, device key {found}")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    src = ap.add_mutually_exclusive_group(required=True)
    src.add_argument("--qr", help="the QR code's text")
    src.add_argument("--pdf", help="the exported report")
    ap.add_argument("--passphrase", help="only for a report exported by an older build, which was encrypted")
    ap.add_argument("--key-fingerprint", help="the device key SHA-256 printed on the certificate")
    a = ap.parse_args()
    payload = a.qr if a.qr else seal_from_pdf(a.pdf, a.passphrase or "")
    if payload is None:
        print("this report has no seal")
        sys.exit(2)
    if payload == "":
        print("this file cannot be read as a PDF")
        sys.exit(2)
    code, message = check(payload, a.key_fingerprint)
    print(message)
    sys.exit(code)


if __name__ == "__main__":
    main()
