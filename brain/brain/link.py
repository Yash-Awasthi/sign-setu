"""The brain's iroh link: phones reach it from any network, not only the station LAN.

The brain binds an iroh endpoint under a key kept in its state directory, so its identity never changes, and prints a
ticket that names that key and how to reach it. A phone dials the ticket over QUIC, end-to-end encrypted to that key:
directly where a hole can be punched, otherwise through an iroh relay, which forwards ciphertext it cannot read. No port
is opened at the station. The relay is the department's own (`--relay`); no third-party relay or address lookup is ever
used, so without one only direct paths work.

Each request is one bidirectional stream: a u32 length and a UTF-8 JSON header ({"m": method, "p": path, "t":
content type, "a": authorization}), then a u32 length and the body. The reply is a u32 length and a JSON header
({"s": status, "t": content type}), then a u32 length and the body. The bridge forwards each request to the brain's own
HTTPS API on the loopback address, so the token, size limits and rate limits apply exactly as on the LAN.

GET /v1/beat is the one stream that stays open: the reply header, then the brain's status every BEAT seconds as one
more frame each, until the phone closes it. It is how a phone learns what the station can do, and that it is there,
without asking for each feature (docs/18-station.md).
"""
import asyncio
import json
import sys
import struct
import threading
import time
from pathlib import Path
from urllib.parse import unquote

import httpx
import iroh

from .security import PEER_HEADER, write_private

ALPN = b"signsetu/brain/1"
MAX_HEADER = 16 * 1024
MAX_BODY = 8 * 1024 * 1024 + 1024
ALLOWED_PATHS = ("/healthz", "/v1/")
BEAT = 5.0  # a phone counts the station gone after 12 s without one (IrohBrain.STALE_MS)


def secret_key(state: Path) -> bytes:
    path = state / "iroh.key"
    if path.exists():
        return path.read_bytes()
    key = iroh.SecretKey.generate().to_bytes()
    # Written whole or not at all: a torn key would change the brain's identity and unpair every phone.
    write_private(path, key)
    return key


def _path_ok(path: str) -> bool:
    """Only the API's own routes, with no way out of them however the path is encoded."""
    plain = unquote(path)
    return (path.startswith(ALLOWED_PATHS) and ".." not in plain and "\\" not in plain and path.isprintable()
            and not any(c.isspace() for c in path))


async def _read_frame(recv, limit: int) -> bytes:
    (n,) = struct.unpack(">I", await recv.read_exact(4))
    if n > limit:
        raise ValueError(f"frame of {n} bytes over the {limit} limit")
    return await recv.read_exact(n) if n else b""


def _frame(data: bytes) -> bytes:
    return struct.pack(">I", len(data)) + data


async def _local(http, method: str, path: str, **kw):
    """A request to the brain's own API. A pooled connection the server closed while idle fails at once without
    reaching it; the request then goes once more on a fresh connection, so a phone never sees that race."""
    try:
        return await http.request(method, path, **kw)
    except (httpx.RemoteProtocolError, httpx.ReadError, httpx.WriteError, httpx.ConnectError):
        return await http.request(method, path, **kw)


class Link:
    """Accepts phones over iroh and forwards their requests to the local API at [local] (HTTPS, pinned to [cert])."""

    def __init__(self, state: Path, local: str, cert: Path, relay: str | None = None):
        self.state = state
        self.local = local
        self.cert = cert
        self.relay = relay
        self.ticket: str | None = None
        self._ready = threading.Event()
        self._error: BaseException | None = None
        # The event loop holds tasks weakly: without these references a running request could be collected.
        self._tasks: set[asyncio.Task] = set()

    def _spawn(self, coro) -> None:
        task = asyncio.create_task(coro)
        self._tasks.add(task)
        task.add_done_callback(self._tasks.discard)

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
            mode = iroh.RelayMode.custom_from_urls([self.relay]) if self.relay else iroh.RelayMode.disabled()
            ep = await iroh.Endpoint.bind(iroh.EndpointOptions(preset=iroh.preset_minimal(), secret_key=secret_key(self.state),
                                                               alpns=[ALPN], relay_mode=mode))
            if self.relay:
                try:
                    await asyncio.wait_for(ep.online(), 10)
                except asyncio.TimeoutError:
                    print(f"iroh: relay {self.relay} not reachable yet; phones off the station network wait for it", file=sys.stderr, flush=True)
            # Built by hand so the ticket names the relay even while it is down; iroh reconnects to it on its own.
            addr = ep.addr()
            self.ticket = str(iroh.EndpointTicket.from_addr(iroh.EndpointAddr(addr.id(), self.relay, addr.direct_addresses())))
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
                self._spawn(self._serve(incoming, http))

    async def _serve(self, incoming, http) -> None:
        try:
            conn = await (await incoming.accept()).connect()
        except Exception:
            return
        if conn.alpn() != ALPN:
            conn.close(1, b"wrong protocol")
            return
        # One line per phone connection and its end, so a status check shows how often phones redial.
        peer = str(conn.remote_id())
        who = peer[:8]
        start = time.monotonic()
        print(f"{time.strftime('%H:%M:%S')} phone {who} connected", flush=True)
        try:
            while True:
                bi = await conn.accept_bi()
                self._spawn(self._request(bi, http, peer))
        except Exception as e:
            why = conn.close_reason() or f"{type(e).__name__}: {e}"
            print(f"{time.strftime('%H:%M:%S')} phone {who} gone after {time.monotonic() - start:.0f} s ({why})", flush=True)

    async def _request(self, bi, http, peer: str) -> None:
        recv, send = bi.recv(), bi.send()
        try:
            head = json.loads(await _read_frame(recv, MAX_HEADER))
            body = await _read_frame(recv, MAX_BODY)
            path = str(head.get("p", ""))
            method = str(head.get("m", "GET")).upper()
            if method == "GET" and path == "/v1/beat":
                await self._beat(send, http, head.get("a"), peer)
                return
            if method not in ("GET", "POST") or not _path_ok(path):
                status, ctype, out = 404, "application/json", b'{"error":"not found"}'
            else:
                headers = {k: str(v) for k, v in (("authorization", head.get("a")), ("content-type", head.get("t"))) if v}
                headers[PEER_HEADER] = peer
                r = await _local(http, method, path, content=body or None, headers=headers)
                status, ctype, out = r.status_code, r.headers.get("content-type", "application/json"), r.content
        except Exception as e:
            status, ctype, out = 400, "application/json", json.dumps({"error": f"bad request: {type(e).__name__}"}).encode()
        try:
            await send.write_all(_frame(json.dumps({"s": status, "t": ctype}).encode()) + _frame(out))
            await send.finish()
        except Exception:
            pass

    async def _beat(self, send, http, authorization, peer: str) -> None:
        """Status frames until the phone goes; each is a fresh /v1/status, so the token is checked on every beat."""
        headers = {PEER_HEADER: peer}
        if authorization:
            headers["authorization"] = str(authorization)
        first = True
        try:
            while True:
                r = await _local(http, "GET", "/v1/status", headers=headers)
                frame = _frame(r.content)
                if first:
                    frame = _frame(json.dumps({"s": r.status_code, "t": "application/json"}).encode()) + frame
                    first = False
                await send.write_all(frame)
                if r.status_code != 200:
                    await send.finish()
                    return
                await asyncio.sleep(BEAT)
        except Exception as e:  # the phone closed the stream or the connection
            print(f"{time.strftime('%H:%M:%S')} heartbeat stream ended: {type(e).__name__}: {e}", flush=True)
            return
