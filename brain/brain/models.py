"""Speech recognition, translation and the local language model, each loaded on first use and run one call at a
time (they share the station's CPU)."""
import io
import os
import re
import threading
import time
import wave
from pathlib import Path

import httpx

from .languages import INDICTRANS2, WHISPER, flores


class Asr:
    """faster-whisper with INT8 weights on the CPU."""

    def __init__(self, size: str):
        self.size = size
        self._model = None
        self._lock = threading.Lock()
        self.error: str | None = None

    @property
    def loaded(self) -> bool:
        return self._model is not None

    def warm(self) -> None:
        """Loads the model in the background, so the station offers speech to text only once it can do it."""
        def load():
            try:
                why = self.check()
                if why:
                    raise ValueError(why)
                with self._lock:
                    self._get()
                print(f"Speech to text: faster-whisper {self.size} loaded", flush=True)
            except Exception as e:  # missing or broken model files: phones keep the recordings without text
                self.error = f"{type(e).__name__}: {e}"
                print(f"Speech to text unavailable: faster-whisper {self.size} did not load ({self.error})", flush=True)
        threading.Thread(target=load, name="asr-load", daemon=True).start()

    def check(self) -> str | None:
        """Why this model cannot be used, before any load: not a faster-whisper size and not a model folder."""
        from faster_whisper import available_models

        if self.size in available_models() or Path(self.size).is_dir() or re.fullmatch(r"[\w.-]+/[\w.-]+", self.size):
            return None
        return f"{self.size!r} is neither a faster-whisper size ({', '.join(available_models())}) nor a model folder or a Hugging Face repo (owner/name)"

    def _get(self):
        if self._model is None:
            from faster_whisper import WhisperModel

            self._model = WhisperModel(self.size, device="cpu", compute_type="int8", cpu_threads=max(1, (os.cpu_count() or 4) - 2))
        return self._model

    def transcribe(self, audio: bytes, lang: str | None) -> dict:
        """[audio] is a 16-bit PCM WAV, as the phone sends (decoded here, no ffmpeg); returns text, detected language
        and seconds of speech. Anything else is a ValueError: bad input, not a broken model."""
        samples = _wav_samples(audio)
        if samples is None:
            raise ValueError("not a 16-bit PCM WAV")
        seconds = _wav_seconds(audio)
        with self._lock:
            segments, info = self._get().transcribe(
                samples, language=lang if lang in WHISPER else None, beam_size=1, vad_filter=True,
                condition_on_previous_text=False,
            )
            text = " ".join(s.text.strip() for s in segments).strip()
        return {"text": text, "lang": info.language, "lang_probability": round(info.language_probability, 3), "seconds": seconds or info.duration}

    def words(self, samples, lang: str | None, prompt: str = "") -> tuple[list[tuple[float, float, str]], str]:
        """Words with their start and end in seconds, for 16 kHz float32 [samples]; and the language heard. One greedy
        decode: the retries at higher temperatures can take ten times as long, too slow for words shown live. Without
        them a decode can loop on one word; no 6-token run repeats, which still lets a word double ("धीरे धीरे")."""
        with self._lock:
            segments, info = self._get().transcribe(
                samples, language=lang if lang in WHISPER else None, beam_size=1, temperature=(0.0, 0.4), no_repeat_ngram_size=6, vad_filter=True,
                condition_on_previous_text=False, word_timestamps=True, initial_prompt=prompt or None,
            )
            out = [(w.start, w.end, w.word.strip()) for seg in segments for w in (seg.words or []) if w.word.strip()]
        return out, info.language


def _wav_samples(audio: bytes):
    """16-bit PCM WAV as float32 mono at 16 kHz, or None for anything else."""
    import numpy as np

    try:
        with wave.open(io.BytesIO(audio)) as w:
            ch, rate = w.getnchannels(), w.getframerate()
            # A header claiming no channels or no rate would divide by zero below and read as a broken model.
            if w.getsampwidth() != 2 or not 1 <= ch <= 8 or not 1000 <= rate <= 384000:
                return None
            x = np.frombuffer(w.readframes(w.getnframes()), dtype="<i2").astype(np.float32) / 32768.0
    except (wave.Error, EOFError):
        return None
    if ch > 1:
        x = x[: len(x) // ch * ch].reshape(-1, ch).mean(axis=1)
    if rate != 16000 and len(x):
        n = int(len(x) * 16000 / rate)
        x = np.interp(np.linspace(0, len(x) - 1, n), np.arange(len(x)), x).astype(np.float32)
    return x


def _wav_seconds(audio: bytes) -> float | None:
    try:
        with wave.open(io.BytesIO(audio)) as w:
            return w.getnframes() / float(w.getframerate())
    except (wave.Error, EOFError, ZeroDivisionError):
        return None


class Translator:
    """IndicTrans2 (distilled 200M) between English and Indian languages, NLLB-200 (distilled 600M) for the rest and
    as the fallback; Indian to Indian goes through English with IndicTrans2."""

    def __init__(self, prefer: str = "indictrans2"):
        self.prefer = prefer
        self._models: dict[str, tuple] = {}
        self._missing: set[str] = set()
        self._lock = threading.Lock()

    @property
    def loaded(self) -> list[str]:
        return sorted(self._models)

    def _load(self, repo: str):
        # A model not on this computer fails at once after the first try: the brain never downloads while it serves.
        if repo in self._missing:
            raise OSError(f"{repo} is not downloaded (python -m brain.fetch)")
        if repo not in self._models:
            from transformers import AutoModelForSeq2SeqLM, AutoTokenizer

            trust = repo.startswith("ai4bharat/")
            try:
                tok = AutoTokenizer.from_pretrained(repo, trust_remote_code=trust)
                model = AutoModelForSeq2SeqLM.from_pretrained(repo, trust_remote_code=trust).eval()
            except OSError:
                self._missing.add(repo)
                raise
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

    remote = False

    def __init__(self, url: str = "http://127.0.0.1:11434", model: str = "gemma3:1b"):
        self.url = url.rstrip("/")
        self.model = model
        self._up = False
        self._checked = -1e9
        self.error: str | None = None

    def available(self) -> bool:
        # Asked on every gloss: the answer is kept 30 s, so a missing Ollama costs one probe, not one per call.
        now = time.monotonic()
        if now - self._checked < 30:
            return self._up
        try:
            r = httpx.get(f"{self.url}/api/tags", timeout=1)
            names = [m.get("name", "") for m in r.json().get("models", [])] if r.is_success else []
            self._up = any(n.startswith(self.model) for n in names)
            self.error = None if self._up else (f"Ollama has no model {self.model!r} (it has: {', '.join(names) or 'none'})" if r.is_success else f"Ollama answered {r.status_code}")
        except httpx.HTTPError as e:
            self._up = False
            self.error = f"Ollama not reachable at {self.url} ({type(e).__name__})"
        self._checked = now
        return self._up

    def chat(self, messages: list[dict], temperature: float = 0.2, max_tokens: int = 256, fmt: str | None = None) -> str:
        r = httpx.post(
            f"{self.url}/api/chat",
            json={"model": self.model, "messages": messages, "stream": False, "options": {"temperature": temperature, "num_predict": max_tokens},
                  **({"format": fmt} if fmt else {})},
            timeout=120,
        )
        r.raise_for_status()
        return r.json()["message"]["content"].strip()


class ApiLlm:
    """A model behind the department's API key: an OpenAI-compatible chat API (OpenAI, Groq, Mistral, Gemini, OpenRouter,
    vLLM, LM Studio) or Anthropic's Messages API. Text leaves the station, so the routes mask it first (mask.py)."""

    remote = True

    def __init__(self, kind: str, model: str, key: str, url: str | None = None):
        self.kind, self.model, self.key = kind, model, key
        self.url = (url or ("https://api.openai.com/v1" if kind == "openai" else "")).rstrip("/")
        self.error: str | None = None if key else f"no API key for the {kind} model (put it in llm.key in the brain's state folder)"
        self._claude = None

    def available(self) -> bool:
        return bool(self.key)

    def chat(self, messages: list[dict], temperature: float = 0.2, max_tokens: int = 256, fmt: str | None = None) -> str:
        return self._anthropic(messages, max_tokens) if self.kind == "anthropic" else self._openai(messages, temperature, max_tokens, fmt)

    def _openai(self, messages, temperature, max_tokens, fmt) -> str:
        # Reasoning models spend the first tokens thinking; the floor leaves room for the answer itself.
        body = {"model": self.model, "messages": messages, "temperature": temperature, "max_tokens": max(max_tokens, API_MIN_TOKENS)}
        headers = {"Authorization": f"Bearer {self.key}"}
        r = httpx.post(f"{self.url}/chat/completions", json={**body, **({"response_format": {"type": "json_object"}} if fmt == "json" else {})},
                       headers=headers, timeout=API_TIMEOUT)
        if r.status_code == 400 and fmt:  # a service without JSON mode: the prompt still asks for JSON
            r = httpx.post(f"{self.url}/chat/completions", json=body, headers=headers, timeout=API_TIMEOUT)
        r.raise_for_status()
        return (r.json()["choices"][0]["message"]["content"] or "").strip()

    def _anthropic(self, messages, max_tokens) -> str:
        import anthropic

        if self._claude is None:
            self._claude = anthropic.Anthropic(api_key=self.key, base_url=self.url or None, timeout=API_TIMEOUT, max_retries=1)
        system = "\n\n".join(m["content"] for m in messages if m["role"] == "system")
        turns = [m for m in messages if m["role"] != "system"]
        args = {"model": self.model, "max_tokens": max(max_tokens, CLAUDE_MIN_TOKENS), "messages": turns, **({"system": system} if system else {})}
        try:
            # Short answers want little thinking; a declined request is re-run on a fallback model by the API itself.
            out = self._claude.beta.messages.create(**args, output_config={"effort": "low"},
                                                    betas=["server-side-fallback-2026-07-01"], fallbacks="default")
        except anthropic.BadRequestError:  # a model without effort or fallbacks
            out = self._claude.messages.create(**args)
        if out.stop_reason == "refusal":
            raise RuntimeError("the model declined")
        return "".join(b.text for b in out.content if b.type == "text").strip()


API_TIMEOUT = 30.0
API_MIN_TOKENS = 512
CLAUDE_MIN_TOKENS = 2048
