"""The brain's iroh link: phones reach it from any network, not only the station LAN.

The brain binds an iroh endpoint under a key kept in its state directory, so its identity never changes, and prints a
ticket that names that key and how to reach it. A phone dials the ticket over QUIC, end-to-end encrypted to that key:
directly where a hole can be punched, otherwise through an iroh relay, which forwards ciphertext it cannot read. No port
is opened at the station.

Each request is one bidirectional stream: a u32 length and a UTF-8 JSON header ({"m": method, "p": path, "t":
content type, "a": authorization}), then a u32 length and the body. The reply is a u32 length and a JSON header
({"s": status, "t": content type}), then a u32 length and the body. The bridge forwards each request to the brain's own
HTTPS API on the loopback address, so the token, size limits and rate limits apply exactly as on the LAN.
"""
import asyncio
import json
import os
import struct
import threading
from pathlib import Path

import httpx
import iroh

ALPN = b"signsetu/brain/1"
MAX_HEADER = 16 * 1024
MAX_BODY = 8 * 1024 * 1024 + 1024
ALLOWED_PATHS = ("/healthz", "/v1/")


def _private(p: Path) -> None:
    try:
        os.chmod(p, 0o600)
    except OSError:
        pass


def secret_key(state: Path) -> bytes:
    path = state / "iroh.key"
    if path.exists():
        return path.read_bytes()
    key = iroh.SecretKey.generate().to_bytes()
    path.write_bytes(key)
    _private(path)
    return key


async def _read_frame(recv, limit: int) -> bytes:
    (n,) = struct.unpack(">I", await recv.read_exact(4))
    if n > limit:
        raise ValueError(f"frame of {n} bytes over the {limit} limit")
    return await recv.read_exact(n) if n else b""


def _frame(data: bytes) -> bytes:
    return struct.pack(">I", len(data)) + data


class Link:
    """Accepts phones over iroh and forwards their requests to the local API at [local] (HTTPS, pinned to [cert])."""

    def __init__(self, state: Path, local: str, cert: Path):
        self.state = state
        self.local = local
        self.cert = cert
        self.ticket: str | None = None
        self._ready = threading.Event()
        self._error: BaseException | None = None

    def start(self, timeout: float = 30.0) -> str:
        """Binds in a thread of its own and returns the ticket once the endpoint is reachable."""
        threading.Thread(target=lambda: asyncio.run(self._run()), name="iroh-link", daemon=True).start()
        if not self._ready.wait(timeout):
            raise TimeoutError("the iroh endpoint did not come online")
        if self._error:
            raise self._error
        return self.ticket

    async def _run(self) -> None:
        try:
            ep = await iroh.Endpoint.bind(iroh.EndpointOptions(preset=iroh.preset_n0(), secret_key=secret_key(self.state), alpns=[ALPN]))
            await ep.online()
            self.ticket = str(iroh.EndpointTicket.from_addr(ep.addr()))
        except BaseException as e:  # reported to start(), which raises it in the main thread
            self._error = e
            self._ready.set()
            return
        self._ready.set()
        async with httpx.AsyncClient(base_url=self.local, verify=str(self.cert), timeout=180) as http:
            while True:
                incoming = await ep.accept_next()
                if incoming is None:
                    return
                asyncio.create_task(self._serve(incoming, http))

    async def _serve(self, incoming, http) -> None:
        try:
            conn = await (await incoming.accept()).connect()
        except Exception:
            return
        if conn.alpn() != ALPN:
            conn.close(1, b"wrong protocol")
            return
        try:
            while True:
                bi = await conn.accept_bi()
                asyncio.create_task(self._request(bi, http))
        except Exception:
            return  # the phone closed the connection

    async def _request(self, bi, http) -> None:
        recv, send = bi.recv(), bi.send()
        try:
            head = json.loads(await _read_frame(recv, MAX_HEADER))
            body = await _read_frame(recv, MAX_BODY)
            path = str(head.get("p", ""))
            method = str(head.get("m", "GET")).upper()
            if method not in ("GET", "POST") or not path.startswith(ALLOWED_PATHS) or ".." in path:
                status, ctype, out = 404, "application/json", b'{"error":"not found"}'
            else:
                headers = {k: v for k, v in (("authorization", head.get("a")), ("content-type", head.get("t"))) if v}
                r = await http.request(method, path, content=body or None, headers=headers)
                status, ctype, out = r.status_code, r.headers.get("content-type", "application/json"), r.content
        except Exception as e:
            status, ctype, out = 400, "application/json", json.dumps({"error": f"bad request: {type(e).__name__}"}).encode()
        try:
            await send.write_all(_frame(json.dumps({"s": status, "t": ctype}).encode()) + _frame(out))
            await send.finish()
        except Exception:
            pass
