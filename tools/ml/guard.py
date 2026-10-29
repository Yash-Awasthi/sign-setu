"""Shared job plumbing for long data and training jobs: per-item manifest, atomic writes, retries and the RAM and disk
guard checked between items. Every job ends with one line: JOB <name> ok= failed= quarantined= status=<...>."""
import ctypes
import json
import os
import pathlib
import shutil
import sys
import time

# The working set beside the repository (sign-setu/../ml-data) unless SIGNSETU_DATA points elsewhere.
DATA = pathlib.Path(os.environ.get("SIGNSETU_DATA", pathlib.Path(__file__).resolve().parents[3] / "ml-data"))

# Jobs run side by side on a 24 GB laptop; all of them together stop at 19 GB used.
MIN_RAM_GB = 5.0
# C: holds the working set and must keep 40 GB free.
MIN_DISK_GB = 40.0


class _Mem(ctypes.Structure):
    _fields_ = [("len", ctypes.c_ulong), ("load", ctypes.c_ulong), ("total", ctypes.c_ulonglong),
                ("avail", ctypes.c_ulonglong), ("pt", ctypes.c_ulonglong), ("pa", ctypes.c_ulonglong),
                ("vt", ctypes.c_ulonglong), ("va", ctypes.c_ulonglong), ("ve", ctypes.c_ulonglong)]


def free_ram_gb() -> float:
    m = _Mem()
    m.len = ctypes.sizeof(m)
    ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(m))
    return m.avail / 2**30


def free_disk_gb(path) -> float:
    # A junction reports the free space of the volume it points to only once resolved.
    p = pathlib.Path(os.path.realpath(path))
    while not p.exists():
        p = p.parent
    return shutil.disk_usage(p).free / 2**30


def check(out_path) -> str | None:
    """None when the job may start its next item, else the pause status."""
    if free_disk_gb(out_path) < MIN_DISK_GB:
        return "paused_disk"
    if free_ram_gb() < MIN_RAM_GB:
        return "paused_ram"
    return None


def write_atomic(path, data: bytes) -> None:
    path = pathlib.Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".tmp")
    with open(tmp, "wb") as f:
        f.write(data)
        f.flush()
        os.fsync(f.fileno())
    # A virus scan or indexer briefly holds a fresh file open on Windows, failing the rename.
    retry(lambda: os.replace(tmp, path), base=0.5)


def retry(fn, tries=5, base=2.0):
    """Call with exponential back-off; the last error propagates."""
    for k in range(tries):
        try:
            return fn()
        except Exception:
            if k == tries - 1:
                raise
            time.sleep(base * 2**k)


class Manifest:
    """Append-only JSON lines, last line per item wins, so a rerun resumes and a crash loses at most one line."""

    def __init__(self, path):
        self.path = pathlib.Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.items = {}
        if self.path.exists():
            for line in self.path.read_text(encoding="utf-8").splitlines():
                try:
                    r = json.loads(line)
                except ValueError:  # a torn last line from a crash
                    continue
                self.items[r["id"]] = r
        self.f = open(self.path, "a", encoding="utf-8")

    def done(self, item_id) -> bool:
        return self.items.get(item_id, {}).get("state") in ("ok", "quarantined")

    def attempts(self, item_id) -> int:
        return self.items.get(item_id, {}).get("attempts", 0)

    def mark(self, item_id, state, reason="", **extra):
        r = {"id": item_id, "state": state, "reason": reason[:300], "attempts": self.attempts(item_id) + (state != "ok"),
             **extra}
        self.items[item_id] = r
        self.f.write(json.dumps(r, ensure_ascii=False) + "\n")
        self.f.flush()
        os.fsync(self.f.fileno())

    def counts(self):
        c = {"ok": 0, "failed": 0, "quarantined": 0}
        for r in self.items.values():
            if r["state"] in c:
                c[r["state"]] += 1
        return c


def job_line(name, manifest: Manifest, status: str) -> str:
    c = manifest.counts()
    line = f"JOB {name} ok={c['ok']} failed={c['failed']} quarantined={c['quarantined']} status={status}"
    print(line, flush=True)
    return line


if __name__ == "__main__":
    print(f"RAM free {free_ram_gb():.1f} GB; disk free {free_disk_gb(sys.argv[1] if len(sys.argv) > 1 else '.'):.0f} GB")
