"""Conversations sent from phones: each arrives as one zip (the app's StationExport.kt; its README.txt describes the
contents) in pieces, and is saved as it came into the station's inbox folder once its SHA-256 matches. Protecting
the folder (disk encryption, access rights, how long it is kept) is the department's.

A session sent again (reopened, more written, sent again) replaces its earlier zip when the earlier one's rows, by
their hash-chain links, are where the new one starts: a session's record only grows, so nothing is lost. The same
rows sent twice are kept once. Rows that differ from the start (another phone, or a record that did not only grow)
keep both zips side by side.

A piece names its offset, so a piece resent after a dropped reply is taken once; a transfer left unfinished for an
hour is deleted.
"""
import hashlib
import json
import re
import secrets
import threading
import time
import unicodedata
import zipfile
from pathlib import Path

MAX_PIECE = 4 * 1024 * 1024
MAX_FILE = 1024 * 1024 * 1024
STALE = 3600.0
MAX_RECORD = 256 * 1024 * 1024  # largest session.json or events.json read back, against a zip that inflates without end
# Names Windows keeps for devices: "CON.zip" can be neither written nor deleted there.
_RESERVED = {"con", "prn", "aux", "nul", *(f"com{i}" for i in range(10)), *(f"lpt{i}" for i in range(10))}


class _Transfer:
    def __init__(self, part: Path, name: str, size: int, sha256: str):
        self.part, self.name, self.size, self.sha256 = part, name, size, sha256
        self.written = 0
        self.touched = time.monotonic()
        self.closed = False  # finished or expired: its .part is gone or renamed, and no piece may write to it
        self.lock = threading.Lock()


class Inbox:
    def __init__(self, folder: Path):
        self.folder = Path(folder)
        self.folder.mkdir(parents=True, exist_ok=True)
        for old in self.folder.glob("*.signsetu-part"):
            old.unlink(missing_ok=True)  # a transfer cut by a restart starts again; own suffix spares others' .part files
        self._open: dict[str, _Transfer] = {}
        self._lock = threading.Lock()
        # One save at a time: the dedupe scan and the free-name search must not race another transfer's.
        self._save = threading.Lock()

    def start(self, name: str, size: int, sha256: str) -> str:
        if not 0 < size <= MAX_FILE:
            raise ValueError("size out of range")
        if not re.fullmatch(r"[0-9a-f]{64}", sha256):
            raise ValueError("sha256 must be 64 lowercase hex digits")
        with self._lock:
            self._expire()
            tid = secrets.token_urlsafe(18)
            part = self.folder / f"{tid}.signsetu-part"
            part.write_bytes(b"")
            self._open[tid] = _Transfer(part, _safe(name), size, sha256)
        return tid

    def add(self, tid: str, at: int, data: bytes) -> int | None:
        """Writes [data] at offset [at]; returns the bytes held so far, or None for an unknown transfer."""
        t = self._open.get(tid)
        if t is None:
            return None
        with t.lock:
            if t.closed:
                return None
            t.touched = time.monotonic()
            if at + len(data) <= t.written:
                return t.written  # already held: a resend
            if at != t.written or t.written + len(data) > t.size:
                raise ValueError(f"expected offset {t.written}")
            with t.part.open("ab") as f:
                f.write(data)
            t.written += len(data)
            return t.written

    def done(self, tid: str) -> str | None:
        """Checks the whole file and saves it; returns its name in the inbox, or None for an unknown transfer."""
        with self._lock:
            t = self._open.pop(tid, None)
        if t is None:
            return None
        with t.lock, self._save:
            t.closed = True
            h = hashlib.sha256()
            with t.part.open("rb") as f:
                for block in iter(lambda: f.read(1 << 20), b""):
                    h.update(block)
            if t.written != t.size or h.hexdigest() != t.sha256:
                t.part.unlink(missing_ok=True)
                raise ValueError("the file did not arrive whole; send it again")
            session, rows = _record(t.part)
            if session:
                for old in self.folder.glob("*.zip"):
                    held_session, held = _record(old)
                    if held_session != session:
                        continue
                    if rows == held:
                        t.part.unlink(missing_ok=True)
                        return old.name  # the same record again: kept once
                    if rows[: len(held)] == held:
                        try:
                            old.unlink()  # the new zip holds every row of this one, and more
                        except OSError:
                            pass  # open elsewhere (Windows): both stay, and nothing is lost
            final = self.folder / t.name
            n = 1
            while final.exists():
                final = self.folder / f"{Path(t.name).stem}-{n}.zip"
                n += 1
            t.part.rename(final)
            return final.name

    def _expire(self) -> None:
        now = time.monotonic()
        for tid in [k for k, t in self._open.items() if now - t.touched > STALE]:
            t = self._open.pop(tid)
            with t.lock:
                t.closed = True
                t.part.unlink(missing_ok=True)


def _record(path: Path) -> tuple[str | None, list]:
    """The session id and the rows' hash-chain links of a conversation zip; (None, []) for anything else."""
    try:
        with zipfile.ZipFile(path) as z:
            if any(z.getinfo(n).file_size > MAX_RECORD for n in ("session.json", "events.json")):
                return None, []
            session = json.loads(z.read("session.json")).get("id")
            rows = [e.get("hash") for e in json.loads(z.read("events.json"))]
        return session, rows
    except (OSError, KeyError, ValueError, AttributeError, TypeError, zipfile.BadZipFile, RuntimeError, NotImplementedError):
        return None, []


def _safe(name: str) -> str:
    # The phone's name for the conversation, slashes and all, never a path.
    base = name[:-4] if name.lower().endswith(".zip") else name
    # Letters, vowel signs and digits of any script stay, so a Hindi title reads as typed.
    kept = "".join(c if unicodedata.category(c)[0] in "LMN" or c in ".-" else "_" for c in base)
    stem = re.sub(r"_+", "_", kept).strip("._")[:120].rstrip("._") or "conversation"
    if stem.split(".")[0].lower() in _RESERVED:
        stem = f"_{stem}"
    return f"{stem}.zip"
