"""Latency and local-network security check of a running brain, as a phone on the station network would see it.

    python check.py TOKEN [--url https://192.168.1.20:8000 --pin PIN] [--ticket TICKET [--relay URL]] [--audio speech.wav] [--runs 5]

With --url and --pin: security. Plain HTTP is refused, a certificate other than the pinned one is refused, no token or a wrong token gets
401, a flood gets 429. With --ticket: every service over iroh, through the relay named in the ticket (or --relay). Latency: median and worst of each route over --runs calls. Exit 1 on any failed check.
"""
import argparse
import asyncio
import json
import threading
import hashlib
import io
import math
import ssl
import statistics
import struct
import sys
import time
import wave

import httpx


def tone_wav(seconds: float = 2.0, rate: int = 16000) -> bytes:
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(b"".join(struct.pack("<h", int(3000 * math.sin(2 * math.pi * 220 * i / rate))) for i in range(int(seconds * rate))))
    return buf.getvalue()


def pinned_client(url: str, pin: str, token: str | None) -> httpx.Client:
    # The certificate is self-signed: trust is the pin, checked on every connection, exactly as the app does.
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    client = httpx.Client(base_url=url, verify=ctx, headers=headers, timeout=180)
    host, port = httpx.URL(url).host, httpx.URL(url).port or 443
    import socket

    with socket.create_connection((host, port), timeout=5) as raw, ctx.wrap_socket(raw, server_hostname=host) as tls:
        der = tls.getpeercert(binary_form=True)
    if hashlib.sha256(der).hexdigest() != pin.lower():
        raise SystemExit("FAIL the server's certificate does not match the pin")
    return client


_dumps = json.dumps  # the request method's own json argument hides the module


class IrohClient:
    """The phone's path: one bidirectional iroh stream per request (brain/link.py). Same call shape as httpx.Client."""

    def __init__(self, ticket: str, token: str | None, relay: str | None):
        import iroh

        self.iroh, self.token = iroh, token
        self.loop = asyncio.new_event_loop()
        threading.Thread(target=self.loop.run_forever, daemon=True).start()
        addr = iroh.EndpointTicket.from_string(ticket.strip()).endpoint_addr()
        url = relay or addr.relay_url()
        self.relay = url
        mode = iroh.RelayMode.custom_from_urls([url]) if url else iroh.RelayMode.disabled()

        async def go():
            ep = await iroh.Endpoint.bind(iroh.EndpointOptions(preset=iroh.preset_minimal(), alpns=[b"signsetu/brain/1"], relay_mode=mode))
            return ep, await ep.connect(addr, b"signsetu/brain/1")

        self.ep, self.conn = self._run(go())

    def _run(self, coro, timeout=180):
        return asyncio.run_coroutine_threadsafe(coro, self.loop).result(timeout)

    @staticmethod
    def _frame(b: bytes) -> bytes:
        return struct.pack(">I", len(b)) + b

    async def _call(self, method, path, body, ctype, token, beats=0):
        bi = await self.conn.open_bi()
        send, recv = bi.send(), bi.recv()
        head = {"m": method, "p": path, "t": ctype, "a": f"Bearer {token}" if token else None}
        try:
            await send.write_all(self._frame(json.dumps(head).encode()) + self._frame(body or b""))
            await send.finish()
        except Exception:
            pass  # the station stopped the stream (a body over its limit): its answer is still read below

        async def frame():
            (n,) = struct.unpack(">I", await recv.read_exact(4))
            return await recv.read_exact(n) if n else b""

        h = json.loads(await frame())
        out = [await frame() for _ in range(max(1, beats))]
        return h["s"], b"".join(out) if beats == 0 else out

    def request(self, method, path, content=None, json=None, files=None, data=None, token="__own__", ctype=None, timeout=180):
        token = self.token if token == "__own__" else token
        body = content
        if json is not None:
            ctype, body = "application/json", _dumps(json).encode()
        elif files:
            boundary = "ssb" + "x" * 16
            crlf = chr(13) + chr(10)
            parts = [f'--{boundary}{crlf}Content-Disposition: form-data; name="{k}"{crlf}{crlf}{v}{crlf}'.encode() for k, v in (data or {}).items()]
            for k, (fn, fb, ct) in files.items():
                parts.append(f'--{boundary}{crlf}Content-Disposition: form-data; name="{k}"; filename="{fn}"{crlf}Content-Type: {ct}{crlf}{crlf}'.encode() + fb + crlf.encode())
            body, ctype = b"".join(parts) + f"--{boundary}--{crlf}".encode(), f"multipart/form-data; boundary={boundary}"
        status, out = self._run(self._call(method, path, body, ctype, token), timeout)
        return Reply(status, out)

    def get(self, path, **kw):
        return self.request("GET", path, **kw)

    def post(self, path, **kw):
        return self.request("POST", path, **kw)

    def beat(self):
        """The first heartbeat: status code and the station's status."""
        status, frames = self._run(self._call("GET", "/v1/beat", None, None, self.token, beats=1))
        return status, json.loads(frames[0]) if status == 200 else {}


class Reply:
    def __init__(self, status: int, body: bytes):
        self.status_code, self.content = status, body
        self.text = body.decode("utf-8", "replace")

    def json(self):
        return json.loads(self.content)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("token")
    ap.add_argument("--url", help="the station's LAN address, https://host:8000 (needs --pin)")
    ap.add_argument("--pin")
    ap.add_argument("--ticket", help="the station's iroh ticket: checks every service as a phone off the LAN uses it")
    ap.add_argument("--relay", help="relay URL when the ticket names none")
    ap.add_argument("--audio", help="a WAV of speech; a tone is used without one")
    ap.add_argument("--lang", default="en")
    ap.add_argument("--runs", type=int, default=5)
    a = ap.parse_args()
    if not (a.url and a.pin) and not a.ticket:
        ap.error("give --url with --pin, or --ticket, or both")
    sys.stdout.reconfigure(encoding="utf-8")  # replies in Indian scripts on a Windows console
    failed = []

    def check(name: str, ok: bool, detail: str = "") -> None:
        print(f"{'ok  ' if ok else 'FAIL'} {name} {detail}".rstrip())
        if not ok:
            failed.append(name)

    if a.url:
        plain = a.url.replace("https://", "http://")
        try:
            r = httpx.get(plain + "/healthz", timeout=5)
            check("plain HTTP refused", False, f"got {r.status_code}")
        except httpx.HTTPError:
            check("plain HTTP refused", True)
        try:
            pinned_client(a.url, "0" * 64, None)
            check("wrong pin refused", False)
        except SystemExit:
            check("wrong pin refused", True)
        anon = pinned_client(a.url, a.pin, None)
        check("health without token", anon.get("/healthz").status_code == 200)
        check("no token gets 401", anon.get("/v1/status").status_code == 401)
        bad = pinned_client(a.url, a.pin, "x" * 43)
        check("wrong token gets 401", bad.get("/v1/status").status_code == 401)
        lan = pinned_client(a.url, a.pin, a.token)
        lan_status = lan.get("/v1/status")
        services("LAN ", lan, a, check, lan_status.json().get("caps") if lan_status.status_code == 200 else None)

    if a.ticket:
        try:
            t0 = time.perf_counter()
            ic = IrohClient(a.ticket, a.token, a.relay)
            check("iroh connect", True, f"{(time.perf_counter() - t0) * 1000:.0f} ms via relay {ic.relay or 'none (direct only)'}")
        except Exception as e:
            check("iroh connect", False, f"{type(e).__name__}: {e}")
            sys.exit(1)
        wrong = ic.get("/v1/status", token="x" * 43)
        check("iroh: wrong token gets 401", wrong.status_code == 401, f"got {wrong.status_code}")
        status, beat = ic.beat()
        check("iroh: heartbeat", status == 200 and "caps" in beat, f"caps {beat.get('caps')}")
        services("iroh ", ic, a, check, beat.get("caps", {}))
    if a.url:
        # Last: the flood uses up the wrong-token allowance that every iroh phone shares with the LAN loopback.
        codes = [bad.get("/v1/status").status_code for _ in range(30)]
        check("a token guesser is rate limited", 429 in codes, f"{codes.count(429)} of 30 refused")
    sys.exit(1 if failed else 0)


def services(tag: str, c, a, check, caps: dict | None = None) -> None:
    """Every route with its latency. A service the station does not offer (caps false) is reported, not failed:
    the phone then does without it."""
    st = c.get("/v1/status")
    check(f"{tag}status with token", st.status_code == 200, st.text[:200])
    audio = open(a.audio, "rb").read() if a.audio else tone_wav()
    sentence = "Where were you yesterday evening?"
    hand = [0.5 + 0.01 * (i % 7) for i in range(42)]
    pose = [0.5 + 0.02 * (i % 9) - (0.1 if i in (22, 23) else 0) for i in range(66)]
    sign_frames = [{"left": hand, "right": hand, "pose": pose} for _ in range(20)]
    live_id = {}

    def live():
        r = c.post("/v1/live", json={"lang": a.lang})
        if r.status_code != 200:
            return r
        tid = r.json()["id"]
        pcm = bytes(2) * 8000
        c.post(f"/v1/live/{tid}", content=pcm)
        return c.post(f"/v1/live/{tid}/stop")

    def archive():
        body = bytes([0x50, 0x4B, 5, 6]) + bytes(18)
        r = c.post("/v1/archive", json={"name": "check.zip", "size": len(body), "sha256": hashlib.sha256(body).hexdigest()})
        if r.status_code != 200:
            return r
        tid = r.json()["id"]
        c.post(f"/v1/archive/{tid}?at=0", content=body)
        return c.post(f"/v1/archive/{tid}/done")

    routes = {
        "asr": ("asr", lambda: c.post("/v1/asr", files={"audio": ("a.wav", audio, "audio/wav")}, data={"lang": a.lang})),
        "live": ("live", live),
        "sign": ("isl", lambda: c.post("/v1/sign", json={"aspect": 1.78, "frames": sign_frames, "top": 3})),
        "translate en-hi": (None, lambda: c.post("/v1/translate", json={"texts": [sentence], "src": "en", "tgt": "hi"})),
        "translate hi-en": (None, lambda: c.post("/v1/translate", json={"texts": ["आप कल शाम कहाँ थे?"], "src": "hi", "tgt": "en"})),
        "gloss": ("gloss", lambda: c.post("/v1/gloss", json={"text": sentence, "lang": "en", "vocabulary": ["you", "yesterday", "evening", "where", "home", "go"]})),
        "plan": ("plan", lambda: c.post("/v1/plan", json={"turns": [{"role": "officer", "text": "Are you hurt?"}, {"role": "user", "text": "thirsty"}],
                                                       "items": [{"id": "n1", "text": "Do you want water?", "cues": ["water"]}, {"id": "n2", "text": "Are you hungry?", "cues": ["food"]}]})),
        "chat": ("llm", lambda: c.post("/v1/chat", json={"messages": [{"role": "user", "content": "Say hello in five words."}], "max_tokens": 20})),
        "archive": ("archive", archive),
    }
    for name, (cap, call) in routes.items():
        if caps is not None and cap and not caps.get(cap):
            print(f"skip {tag}{name}: the station does not offer {cap} (the phone does without it)")
            continue
        times, last = [], None
        for _ in range(a.runs):
            t0 = time.perf_counter()
            last = call()
            times.append((time.perf_counter() - t0) * 1000)
        # The first call loads the model; the median of the rest is the working latency.
        warm = times[1:] or times
        check(f"{tag}{name}", last.status_code in (200, 422) if name == "sign" else last.status_code == 200,
              f"first {times[0]:.0f} ms, median {statistics.median(warm):.0f} ms, worst {max(warm):.0f} ms | {last.text[:100]}")


if __name__ == "__main__":
    main()
