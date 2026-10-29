"""Moves data folders from C:\\work\\ml-data to D:\\ml-data and leaves a junction at the old path: copy while hashing,
hash the copy, compare size and sha256 per file, and only then remove the C: copy. Resumable per file.

    python move_to_d.py islrtc_dict isl_kaggle60 ...
"""
import hashlib
import os
import pathlib
import shutil
import subprocess
import sys

import guard
from guard import DATA

SRC, DST = DATA, pathlib.Path(r"D:\ml-data")


def sha(path, copy_to=None):
    h = hashlib.sha256()
    out = open(copy_to, "wb") if copy_to else None
    with open(path, "rb") as f:
        while b := f.read(1 << 22):
            h.update(b)
            if out:
                out.write(b)
    if out:
        out.flush()
        os.fsync(out.fileno())
        out.close()
    return h.hexdigest()


def move(name, man):
    src, dst = SRC / name, DST / name
    if os.path.realpath(src).lower().startswith("d:"):
        return "already a junction"
    files = [p for p in src.rglob("*") if p.is_file()]
    for p in files:
        rel = str(p.relative_to(SRC))
        if man.done(rel):
            continue
        if guard.free_disk_gb(DST) < guard.MIN_DISK_GB:
            return "paused_disk"
        q = DST / rel
        q.parent.mkdir(parents=True, exist_ok=True)
        tmp = q.with_name(q.name + ".tmp")
        try:
            a = sha(p, tmp)
            os.replace(tmp, q)
            b = sha(q)
            if a != b or p.stat().st_size != q.stat().st_size:
                raise OSError("hash or size mismatch")
            man.mark(rel, "ok", sha256=a, bytes=q.stat().st_size)
        except OSError as e:
            man.mark(rel, "failed", repr(e))
    bad = [str(p.relative_to(SRC)) for p in files if not man.done(str(p.relative_to(SRC)))]
    if bad:
        return f"failed {len(bad)} files"
    # Every file is verified on D:; the C: tree goes and the junction takes its place.
    shutil.rmtree(src)
    subprocess.run(["cmd", "/c", "mklink", "/J", str(src), str(dst)], check=True, capture_output=True)
    return "moved"


def main(names):
    man = guard.Manifest(DST / "move_manifest.jsonl")
    status = "done"
    for n in names:
        r = move(n, man)
        print(f"{n}: {r}", flush=True)
        if r == "paused_disk":
            status = r
            break
        if r.startswith("failed"):
            status = "failed"
    guard.job_line("Q0_move", man, status)


if __name__ == "__main__":
    main(sys.argv[1:])
