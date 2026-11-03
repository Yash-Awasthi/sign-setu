"""Live speech to text: while the person talks, the phone sends their speech in short pieces and reads back the words
so far. The whole take is still transcribed once more afterwards (/v1/asr), and that reading is the one recorded.

Each pass transcribes the audio after the last committed word; a word is committed once two passes in a row agree on
it (local agreement, as in Machacek, Dabre and Bojar, "Turning Whisper into Real-Time Transcription System", 2023).
Words after that are shown as tentative. Committed audio is cut from the buffer once it grows long, so a pass stays
short however long the person talks. The end of speech is a run of silence after speech, found by the VAD that
faster-whisper ships. Audio lives only in memory and is dropped when the take stops or goes idle.
"""
import secrets
import threading
import time
import unicodedata

import numpy as np
from faster_whisper.vad import VadOptions, get_speech_timestamps

from .languages import WHISPER
from .models import Asr

RATE = 16000
MAX_SECONDS = 60  # the phone's own cap on one take
MAX_CHUNK = 64 * 1024  # 2 seconds of 16-bit PCM
MAX_TAKES = 4
IDLE = 20.0
END_SILENCE = 1.5
PASS_EVERY = 0.5
KEEP = 12.0
SEAM = 1.0
STALE = 4.0
_STOPS = (".", "?", "!", "।", "॥", "؟", "۔")  # Latin, Devanagari danda, Urdu
_VAD = VadOptions(min_silence_duration_ms=300, speech_pad_ms=100)


def _norm(w: str) -> str:
    # Punctuation and case only: stripping marks would merge different Indic words.
    return "".join(c for c in w.casefold() if not unicodedata.category(c).startswith(("P", "S")))


def _after(committed: list[str], end: float, words: list[tuple[float, float, str]]) -> list[tuple[float, float, str]]:
    """The words of a pass that follow the committed ones. Whisper's word times shift between passes, and stretch at
    the edges of the audio, so a cut by time alone drops or repeats words at the seam. The committed tail is found in
    the pass by its text instead, nearest the committed end; time is the fallback when the text is not there."""
    said = [_norm(w) for _, _, w in words]
    tail = [_norm(w) for w in committed[-4:]]
    for n in range(len(tail), 0, -1):
        hits = [i for i in range(len(words) - n + 1) if said[i:i + n] == tail[-n:]]
        # Only near where the committed words ended: a like phrase later on ("is your mother nearby? is your father
        # nearby?") would skip the words between. One word alone matches too easily, so it must be closer still.
        hits = [i for i in hits if abs(words[i + n - 1][1] - end) < (2 * SEAM if n > 1 else SEAM)]
        if hits:
            i = min(hits, key=lambda i: abs(words[i + n - 1][1] - end))
            return words[i + n:]
    return [x for x in words if (x[0] + x[1]) / 2 > end]


class _Take:
    def __init__(self, lang: str | None):
        self.lang = lang
        self.audio = np.zeros(0, dtype=np.float32)  # from [offset] seconds to now
        self.offset = 0.0
        self.received = 0  # samples, cut ones included
        self.passed = 0
        self.committed: list[str] = []
        self.ends: list[float] = []  # each committed word's end
        self.cut_words = 0  # committed words whose audio has been cut
        self.committed_end = 0.0
        self.tentative: list[tuple[float, float, str]] = []
        self.heard_to = 0.0  # how far into the take the last transcription reached
        self.sentence_ends: list[float] = []
        self.speech = False
        self.end = False
        self.touched = time.monotonic()
        self.lock = threading.Lock()

    def finish(self) -> None:
        self.committed += [w for _, _, w in self.tentative]
        self.tentative = []
        self.end = True

    def view(self) -> dict:
        return {"text": " ".join(self.committed), "tentative": " ".join(w for _, _, w in self.tentative), "end": self.end,
                "lang": self.lang}


class Live:
    """Takes in progress, transcribed by one worker thread, since passes share the CPU with every other request."""

    def __init__(self, asr: Asr, on_fail=lambda e: None):
        self.asr = asr
        self.on_fail = on_fail  # a pass that raised: the API takes live speech out of the heartbeat
        self._takes: dict[str, _Take] = {}
        self._lock = threading.Lock()
        self._wake = threading.Event()
        self._worker: threading.Thread | None = None

    def start(self, lang: str | None) -> str:
        with self._lock:
            self._expire()
            if len(self._takes) >= MAX_TAKES:
                raise OverflowError("too many live takes")
            tid = secrets.token_urlsafe(18)
            # A language Whisper lacks is detected on the first pass and then held, so passes agree with each other.
            self._takes[tid] = _Take(lang if lang in WHISPER else None)
            if self._worker is None:
                self._worker = threading.Thread(target=self._run, name="live-asr", daemon=True)
                self._worker.start()
        return tid

    def feed(self, tid: str, pcm: bytes) -> dict | None:
        """Adds 16 kHz mono 16-bit little-endian [pcm] (may be empty) and returns the words so far."""
        t = self._takes.get(tid)
        if t is None:
            return None
        x = np.frombuffer(pcm[: len(pcm) // 2 * 2], dtype="<i2").astype(np.float32) / 32768.0
        with t.lock:
            t.touched = time.monotonic()
            room = MAX_SECONDS * RATE - t.received
            if len(x) and room > 0 and not t.end:
                x = x[:room]
                t.audio = np.concatenate([t.audio, x])
                t.received += len(x)
            if t.received >= MAX_SECONDS * RATE and not t.end:
                t.finish()
            out = t.view()
        self._wake.set()
        return out

    def stop(self, tid: str) -> dict | None:
        with self._lock:
            t = self._takes.pop(tid, None)
        if t is None:
            return None
        with t.lock:
            t.finish()
            t.audio = np.zeros(0, dtype=np.float32)
            return t.view()

    def _expire(self) -> None:
        now = time.monotonic()
        for tid in [k for k, t in self._takes.items() if now - t.touched > IDLE]:
            self._takes.pop(tid)

    def _run(self) -> None:
        while True:
            self._wake.wait(1.0)
            self._wake.clear()
            with self._lock:
                self._expire()
                due = [t for t in self._takes.values() if not t.end and t.received - t.passed >= PASS_EVERY * RATE]
            for t in due:
                try:
                    self._pass(t)
                except Exception as e:
                    self.on_fail(e)
            if due:
                self._wake.set()

    def _pass(self, t: _Take) -> None:
        with t.lock:
            audio, offset, t.passed = t.audio.copy(), t.offset, t.received
            # Only words already cut from the audio: Whisper copies a prompt it hears again, or, kept from
            # repeating, drops those words.
            prompt = " ".join(t.committed[: t.cut_words][-40:])
            done = max(0.0, t.committed_end - offset)
        now = len(audio) / RATE
        spans = get_speech_timestamps(audio, _VAD)
        last = spans[-1]["end"] / RATE if spans else None
        silent = now - max(done, last or 0.0) >= END_SILENCE
        # Nothing new to transcribe: no speech after the committed words, or the last pass already heard it all.
        if last is None or last <= done or last + offset <= t.heard_to:
            with t.lock:
                if t.speech and silent:
                    t.finish()
            return
        words, lang = self.asr.words(audio, t.lang, prompt)
        hyp = _after(t.committed, t.committed_end, [(a + offset, b + offset, w) for a, b, w in words])
        with t.lock:
            if t.end:
                return  # stopped while this pass ran
            t.speech = True
            t.heard_to = offset + now
            t.lang = t.lang or lang
            n = 0
            while n < min(len(hyp), len(t.tentative)) and _norm(hyp[n][2]) == _norm(t.tentative[n][2]):
                n += 1
            # A word Whisper keeps changing would hold back everything after it: past STALE it is taken as heard.
            while n < len(hyp) and hyp[n][1] < offset + now - STALE:
                n += 1
            if n:
                t.committed += [w for _, _, w in hyp[:n]]
                t.ends += [b for _, b, _ in hyp[:n]]
                t.committed_end = hyp[n - 1][1]
                # A word's end stretches into the silence after it, sometimes into the next word: capped by its start.
                t.sentence_ends += [min(b, a + 1.2) for a, b, w in hyp[:n] if w.endswith(_STOPS)]
            t.tentative = hyp[n:]
            if now - last >= END_SILENCE:
                t.finish()
            # Cut committed audio once the buffer is long, at the end of a sentence, where Whisper starts cleanly; the
            # prompt carries the words cut as context. Without a sentence end for twice as long, at the last word.
            elif len(t.audio) / RATE > KEEP:
                at = next((b for b in reversed(t.sentence_ends) if b > t.offset), None)
                if at is None and len(t.audio) / RATE > 2 * KEEP:
                    at = t.committed_end
                if at is not None and t.tentative:
                    at = min(at, t.tentative[0][0] - 0.1)  # never into words not yet committed
                if at is not None and at > t.offset:
                    t.cut_words = len(t.committed) if at >= t.committed_end else t.cut_words + sum(
                        1 for e in t.ends[t.cut_words:] if e <= at)
                    cut = int((at - t.offset) * RATE)
                    t.audio = t.audio[cut:]
                    t.offset += cut / RATE
