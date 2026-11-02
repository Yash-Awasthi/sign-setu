"""Pairing secrets and request checks for the brain on a station LAN.

The brain serves HTTPS only, with a self-signed certificate made on first start. A phone pairs by entering the
address, the bearer token and the certificate's SHA-256 pin; it then trusts that one certificate and nothing else.
Requests must come from a private or loopback address, carry the token, and stay under the size cap.
"""
import datetime
import hashlib
import hmac
import ipaddress
import os
import secrets
import socket
import time
from collections import defaultdict, deque
from pathlib import Path

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.x509.oid import NameOID


def lan_addresses() -> list[str]:
    """IPv4 addresses this machine has on private networks, for the pairing line and the certificate."""
    found = set()
    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            found.add(info[4][0])
    except OSError:
        pass
    # The address the default route uses; no packet is sent.
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(("10.255.255.255", 1))
            found.add(s.getsockname()[0])
    except OSError:
        pass
    return sorted(a for a in found if ipaddress.ip_address(a).is_private and not a.startswith("127."))


class Secrets:
    """The certificate, its key and the token, kept in [state] with owner-only file modes where the OS allows."""

    def __init__(self, state: Path):
        self.state = state
        state.mkdir(parents=True, exist_ok=True)
        self.cert = state / "brain.crt"
        self.key = state / "brain.key"
        self.token_file = state / "token"
        if not (self.cert.exists() and self.key.exists()):
            self._make_cert()
        if not self.token_file.exists():
            self.token_file.write_text(secrets.token_urlsafe(32), encoding="ascii")
            _private(self.token_file)
        self.token = self.token_file.read_text(encoding="ascii").strip()

    def _make_cert(self) -> None:
        key = ec.generate_private_key(ec.SECP256R1())
        name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "signsetu-brain")])
        alt = [x509.DNSName("localhost"), x509.IPAddress(ipaddress.ip_address("127.0.0.1"))]
        alt += [x509.IPAddress(ipaddress.ip_address(a)) for a in lan_addresses()]
        now = datetime.datetime.now(datetime.timezone.utc)
        cert = (
            x509.CertificateBuilder().subject_name(name).issuer_name(name).public_key(key.public_key())
            .serial_number(x509.random_serial_number()).not_valid_before(now - datetime.timedelta(minutes=5))
            .not_valid_after(now + datetime.timedelta(days=825))
            .add_extension(x509.SubjectAlternativeName(alt), critical=False)
            .sign(key, hashes.SHA256())
        )
        self.key.write_bytes(key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
        _private(self.key)
        self.cert.write_bytes(cert.public_bytes(serialization.Encoding.PEM))

    @property
    def pin(self) -> str:
        """SHA-256 of the certificate (DER), as the phone compares it."""
        der = x509.load_pem_x509_certificate(self.cert.read_bytes()).public_bytes(serialization.Encoding.DER)
        return hashlib.sha256(der).hexdigest()

    def rotate_token(self) -> str:
        self.token_file.write_text(secrets.token_urlsafe(32), encoding="ascii")
        _private(self.token_file)
        self.token = self.token_file.read_text(encoding="ascii").strip()
        return self.token


def _private(p: Path) -> None:
    try:
        os.chmod(p, 0o600)
    except OSError:
        pass


def bearer_ok(header: str | None, token: str) -> bool:
    if not header or not header.startswith("Bearer "):
        return False
    return hmac.compare_digest(header[7:].strip().encode(), token.encode())


def client_allowed(host: str | None) -> bool:
    """Only the station's own network: private ranges and loopback. A forwarded port to the internet stays closed."""
    if not host:
        return False
    try:
        ip = ipaddress.ip_address(host)
    except ValueError:
        return False
    return ip.is_private or ip.is_loopback


class RateLimit:
    """At most [limit] requests per [window] seconds from one address; a token guesser is slowed to a crawl."""

    def __init__(self, limit: int = 60, window: float = 60.0):
        self.limit = limit
        self.window = window
        self.seen: dict[str, deque] = defaultdict(deque)

    def allow(self, host: str) -> bool:
        now = time.monotonic()
        q = self.seen[host]
        while q and now - q[0] > self.window:
            q.popleft()
        if len(q) >= self.limit:
            return False
        q.append(now)
        return True
