"""Speech recognition, translation and the local language model, each loaded on first use and run one call at a
time (they share the station's CPU)."""
import io
import os
import threading
import time
import wave

import httpx

from .languages import INDICTRANS2, WHISPER, flores


class Asr:
    """faster-whisper with INT8 weights on the CPU."""

    def __init__(self, size: str = "small"):
        self.size = size
        self._model = None
        self._lock = threading.Lock()

    @property
    def loaded(self) -> bool:
        return self._model is not None

    def _get(self):
        if self._model is None:
            from faster_whisper import WhisperModel

            self._model = WhisperModel(self.size, device="cpu", compute_type="int8", cpu_threads=max(1, (os.cpu_count() or 4) - 2))
        return self._model

    def transcribe(self, audio: bytes, lang: str | None) -> dict:
        """[audio] is a WAV (decoded here, no ffmpeg) or any format PyAV reads; returns text, detected language and
        seconds of speech."""
        seconds = _wav_seconds(audio)
        samples = _wav_samples(audio)
        with self._lock:
            segments, info = self._get().transcribe(
                samples if samples is not None else io.BytesIO(audio), language=lang if lang in WHISPER else None, beam_size=1, vad_filter=True,
                condition_on_previous_text=False,
            )
            text = " ".join(s.text.strip() for s in segments).strip()
        return {"text": text, "lang": info.language, "lang_probability": round(info.language_probability, 3), "seconds": seconds or info.duration}


def _wav_samples(audio: bytes):
    """16-bit PCM WAV as float32 mono at 16 kHz, or None for anything else."""
    import numpy as np

    try:
        with wave.open(io.BytesIO(audio)) as w:
            if w.getsampwidth() != 2:
                return None
            x = np.frombuffer(w.readframes(w.getnframes()), dtype="<i2").astype(np.float32) / 32768.0
            ch, rate = w.getnchannels(), w.getframerate()
    except (wave.Error, EOFError):
        return None
    if ch > 1:
        x = x.reshape(-1, ch).mean(axis=1)
    if rate != 16000:
        n = int(len(x) * 16000 / rate)
        x = np.interp(np.linspace(0, len(x) - 1, n), np.arange(len(x)), x).astype(np.float32)
    return x


def _wav_seconds(audio: bytes) -> float | None:
    try:
        with wave.open(io.BytesIO(audio)) as w:
            return w.getnframes() / float(w.getframerate())
    except (wave.Error, EOFError):
        return None


class Translator:
    """IndicTrans2 (distilled 200M) between English and Indian languages, NLLB-200 (distilled 600M) for the rest and
    as the fallback; Indian to Indian goes through English with IndicTrans2."""

    def __init__(self, prefer: str = "indictrans2"):
        self.prefer = prefer
        self._models: dict[str, tuple] = {}
        self._lock = threading.Lock()

    @property
    def loaded(self) -> list[str]:
        return sorted(self._models)

    def _load(self, repo: str):
        if repo not in self._models:
            from transformers import AutoModelForSeq2SeqLM, AutoTokenizer

            trust = repo.startswith("ai4bharat/")
            tok = AutoTokenizer.from_pretrained(repo, trust_remote_code=trust)
            model = AutoModelForSeq2SeqLM.from_pretrained(repo, trust_remote_code=trust).eval()
            self._models[repo] = (tok, model)
        return self._models[repo]

    def _indic(self, texts: list[str], src: str, tgt: str) -> list[str]:
        import torch

        repo = "ai4bharat/indictrans2-en-indic-dist-200M" if src == "en" else "ai4bharat/indictrans2-indic-en-dist-200M"
        tok, model = self._load(repo)
        s, t = flores(src), flores(tgt)
        # IndicTrans2 reads Brahmic scripts as Devanagari and writes them so: the same conversion its toolkit does.
        batch = [f"{s} {t} {to_devanagari(x.strip(), src)}" for x in texts]
        enc = tok(batch, truncation=True, padding="longest", return_tensors="pt")
        with torch.inference_mode():
            # The model's own code predates the transformers cache objects; without the cache it decodes correctly.
            out = model.generate(**enc, num_beams=4, max_length=256, use_cache=False)
        dec = tok.batch_decode(out, skip_special_tokens=True, clean_up_tokenization_spaces=True)
        return [from_devanagari(x.strip(), tgt) for x in dec]

    def _nllb(self, texts: list[str], src: str, tgt: str) -> list[str]:
        import torch

        tok, model = self._load("facebook/nllb-200-distilled-600M")
        tok.src_lang = flores(src)
        enc = tok(texts, return_tensors="pt", padding=True, truncation=True, max_length=256)
        with torch.inference_mode():
            out = model.generate(**enc, forced_bos_token_id=tok.convert_tokens_to_ids(flores(tgt)), num_beams=4, max_length=256)
        return tok.batch_decode(out, skip_special_tokens=True)

    def translate(self, texts: list[str], src: str, tgt: str) -> tuple[list[str], str]:
        if src == tgt:
            return texts, "none"
        with self._lock:
            indic_ok = self.prefer == "indictrans2" and src in INDICTRANS2 and tgt in INDICTRANS2
            if indic_ok:
                try:
                    if "en" in (src, tgt):
                        return self._indic(texts, src, tgt), "indictrans2"
                    return self._indic(self._indic(texts, src, "en"), "en", tgt), "indictrans2-pivot"
                except Exception as e:  # missing, gated or failing: NLLB serves instead, and the reason is printed once
                    if not getattr(self, "_warned", False):
                        print(f"IndicTrans2 unavailable ({type(e).__name__}: {e}); using NLLB-200", flush=True)
                        self._warned = True
            return self._nllb(texts, src, tgt), "nllb-200"


# Brahmic scripts the model sees as Devanagari; Urdu (Arabic script) and English pass unchanged.
_BRAHMIC = {"bn": "bn", "as": "as", "ta": "ta", "te": "te", "gu": "gu", "kn": "kn", "ml": "ml", "or": "or", "pa": "pa"}


def to_devanagari(text: str, lang: str) -> str:
    if lang not in _BRAHMIC:
        return text
    from indicnlp.transliterate.unicode_transliterate import UnicodeIndicTransliterator

    return UnicodeIndicTransliterator.transliterate(text, _BRAHMIC[lang], "hi")


def from_devanagari(text: str, lang: str) -> str:
    if lang not in _BRAHMIC:
        return text
    from indicnlp.transliterate.unicode_transliterate import UnicodeIndicTransliterator

    return UnicodeIndicTransliterator.transliterate(text, "hi", _BRAHMIC[lang])


class Llm:
    """A model served by Ollama on this machine; never a remote service."""

    def __init__(self, url: str = "http://127.0.0.1:11434", model: str = "gemma3:1b"):
        self.url = url.rstrip("/")
        self.model = model
        self._up = False
        self._checked = -1e9

    def available(self) -> bool:
        # Asked on every gloss: the answer is kept 30 s, so a missing Ollama costs one probe, not one per call.
        now = time.monotonic()
        if now - self._checked < 30:
            return self._up
        try:
            r = httpx.get(f"{self.url}/api/tags", timeout=1)
            self._up = r.is_success and any(m.get("name", "").startswith(self.model) for m in r.json().get("models", []))
        except httpx.HTTPError:
            self._up = False
        self._checked = now
        return self._up

    def chat(self, messages: list[dict], temperature: float = 0.2, max_tokens: int = 256) -> str:
        r = httpx.post(
            f"{self.url}/api/chat",
            json={"model": self.model, "messages": messages, "stream": False, "options": {"temperature": temperature, "num_predict": max_tokens}},
            timeout=120,
        )
        r.raise_for_status()
        return r.json()["message"]["content"].strip()
