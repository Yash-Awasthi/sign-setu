"""Speech for the languages Cartesia voices better than Indic Parler-TTS; build_audio.py leaves those languages to this
script. One voice per language, written to audio/. Resumes where it stopped. Needs CARTESIA_API_KEY.

    python tools/content/build_natural_audio.py
    python tools/content/build_content.py --manifest-only
"""
import io
import json
import os
import pathlib
import sys
import urllib.error
import urllib.request

import numpy as np
import soundfile as sf

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from build_audio import ASSETS, NATURAL_ONLY, OUT_RATE, cut_off, finish, jobs, loud_end, trim  # noqa: E402

# Cartesia voices by language; Chhattisgarhi is read by the Hindi voice. Sonic-3 refuses Assamese, Nepali, Odia and Urdu.
CARTESIA_VOICES = {
    "en": "7ea5e9c2-b719-4dc3-b870-5ba5f14d31d8",
    "hi": "6b02ffe5-e3cb-48c0-a023-c72f85953375", "hne": "6b02ffe5-e3cb-48c0-a023-c72f85953375",
    "ta": "d4470f50-295e-4e11-82a2-158d45bf6abc", "bn": "48b9e1de-e2fa-4914-8b32-31c437813548",
    "gu": "4590a461-bc68-4a50-8d14-ac04f5923d22", "kn": "4c00ff98-199c-4967-99ae-7b69125edcff",
    "ml": "b426013c-002b-4e89-8874-8cd20b68373a", "mr": "5c32dce6-936a-4892-b131-bafe474afe5f",
    "pa": "9bf2ddcd-bb35-4e90-81b3-21c8183b24f4", "te": "4418bb06-8329-49a1-bb11-53bb64ca0547",
}


class QuotaSpent(Exception):
    pass


def provider(code: str) -> str | None:
    return "cartesia" if code in CARTESIA_VOICES else None


def post(url: str, headers: dict, body: dict) -> bytes:
    req = urllib.request.Request(url, json.dumps(body).encode(), {**headers, "Content-Type": "application/json"})
    try:
        return urllib.request.urlopen(req, timeout=120).read()
    except urllib.error.HTTPError as e:
        detail = e.read()[:300]
        if e.code in (401, 402, 429) or b"quota" in detail.lower() or b"credit" in detail.lower():
            raise QuotaSpent(f"{e.code} {detail!r}") from e
        raise RuntimeError(f"{url} {e.code} {detail!r}") from e


def speak(code: str, text: str) -> np.ndarray:
    """Mono 16-bit PCM at OUT_RATE."""
    raw = post("https://api.cartesia.ai/tts/bytes",
               {"X-API-Key": os.environ["CARTESIA_API_KEY"], "Cartesia-Version": "2025-04-16"},
               {"model_id": "sonic-3", "transcript": text, "language": "hi" if code == "hne" else code,
                "voice": {"mode": "id", "id": CARTESIA_VOICES[code]},
                "output_format": {"container": "raw", "encoding": "pcm_s16le", "sample_rate": OUT_RATE}})
    return np.frombuffer(raw, dtype="<i2").astype(np.float32) / 32768


def main() -> None:
    spent: set[str] = set()
    made = 0
    for code, text, out in jobs():
        name = provider(code)
        if name is None or name in spent or (out.exists() and not cut_off(out)):
            continue
        try:
            # A take that still ends mid-word gets one more try; the old file stays until a new one is made.
            raw = speak(code, text)
            if loud_end(raw, OUT_RATE):
                raw = speak(code, text)
            wav = trim(raw, OUT_RATE)
        except QuotaSpent as e:
            print(f"{name} stopped: {e}")
            spent.add(name)
            continue
        wav = finish(wav, OUT_RATE)
        out.parent.mkdir(parents=True, exist_ok=True)
        buf = io.BytesIO()
        sf.write(buf, wav, OUT_RATE, format="OGG", subtype="OPUS")
        out.write_bytes(buf.getvalue())
        made += 1
        print(f"{out.relative_to(ASSETS)}  {len(wav) / OUT_RATE:.1f}s  {name}{'  still cut' if loud_end(raw, OUT_RATE) else ''}")
    print(f"{made} recordings written")


if __name__ == "__main__":
    main()
