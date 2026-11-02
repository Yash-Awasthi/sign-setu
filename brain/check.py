"""Latency and local-network security check of a running brain, as a phone on the station network would see it.

    python check.py https://192.168.1.20:8000 TOKEN PIN [--audio speech.wav] [--runs 5]

Security: plain HTTP is refused, a certificate other than the pinned one is refused, no token or a wrong token gets
401, a flood gets 429. Latency: median and worst of each route over --runs calls. Exit 1 on any failed check.
"""
import argparse
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


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("url")
    ap.add_argument("token")
    ap.add_argument("pin")
    ap.add_argument("--audio", help="a WAV of speech; a tone is used without one")
    ap.add_argument("--lang", default="en")
    ap.add_argument("--runs", type=int, default=5)
    a = ap.parse_args()
    sys.stdout.reconfigure(encoding="utf-8")  # replies in Indian scripts on a Windows console
    failed = []

    def check(name: str, ok: bool, detail: str = "") -> None:
        print(f"{'ok  ' if ok else 'FAIL'} {name} {detail}".rstrip())
        if not ok:
            failed.append(name)

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

    c = pinned_client(a.url, a.pin, a.token)
    st = c.get("/v1/status")
    check("status with token", st.status_code == 200, st.text)
    audio = open(a.audio, "rb").read() if a.audio else tone_wav()
    sentence = "Where were you yesterday evening?"
    routes = {
        "asr": lambda: c.post("/v1/asr", files={"audio": ("a.wav", audio, "audio/wav")}, data={"lang": a.lang}),
        "translate en-hi": lambda: c.post("/v1/translate", json={"texts": [sentence], "src": "en", "tgt": "hi"}),
        "translate hi-en": lambda: c.post("/v1/translate", json={"texts": ["आप कल शाम कहाँ थे?"], "src": "hi", "tgt": "en"}),
        "gloss": lambda: c.post("/v1/gloss", json={"text": sentence, "lang": "en", "vocabulary": ["you", "yesterday", "evening", "where", "home", "go"]}),
        "chat": lambda: c.post("/v1/chat", json={"messages": [{"role": "user", "content": "Say hello in five words."}], "max_tokens": 20}),
    }
    for name, call in routes.items():
        times, last = [], None
        for i in range(a.runs):
            t0 = time.perf_counter()
            last = call()
            times.append((time.perf_counter() - t0) * 1000)
        ok = last is not None and last.status_code == 200
        # The first call loads the model; the median of the rest is the working latency.
        warm = times[1:] or times
        check(f"{name}", ok, f"first {times[0]:.0f} ms, median {statistics.median(warm):.0f} ms, worst {max(warm):.0f} ms | {last.text[:120] if last is not None else ''}")

    codes = [bad.get("/v1/status").status_code for _ in range(30)]
    check("a token guesser is rate limited", 429 in codes, f"{codes.count(429)} of 30 refused")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
