"""Brings working data from D:\\ml-data back to C:\\work\\ml-data (C: is the working set, D: backup and cold storage).
Copy while hashing, hash the copy, compare size and sha256 per file; resumable per file.

    python to_c.py move landmarks q3 runs ...         verified, then the D: copy goes; no junction
    python to_c.py copy islrtc_dict [--first LIST]    D: copy kept; the C: junction becomes the real folder
                                                      only after every file verifies; LIST (paths under
                                                      C:\\work\\ml-data, one per line) copies first
"""
import os
import pathlib
import shutil
import subprocess
import sys

import guard
from guard import DATA
from move_to_d import sha

SRC, DST = pathlib.Path(r"D:\ml-data"), DATA


def transfer(name, man, keep, first=()):
    src, final = SRC / name, DST / name
    # A junction to D: (or nothing) sits at the final path until the end, so readers keep working meanwhile.
    stage = final if keep is False else DST / (name + ".copy")
    if keep and final.exists() and not os.path.realpath(final).lower().startswith("d:"):
        return "already on C:"
    # Half-downloaded .part files are not data; they stay with the D: backup.
    files = [p for p in src.rglob("*") if p.is_file() and p.suffix != ".part"]
    order = {f: i for i, f in enumerate(first)}
    files.sort(key=lambda p: order.get(str(p.relative_to(SRC)).replace("\\", "/"), len(order)))
    for p in files:
        rel = str(p.relative_to(SRC))
        if man.done(rel):
            continue
        if guard.free_disk_gb(DST) < guard.MIN_DISK_GB:
            return "paused_disk"
        q = stage / p.relative_to(src)
        q.parent.mkdir(parents=True, exist_ok=True)
        tmp = q.with_name(q.name + ".tmp")
        try:
            a = sha(p, tmp)
            os.replace(tmp, q)
            if a != sha(q) or p.stat().st_size != q.stat().st_size:
                raise OSError("hash or size mismatch")
            man.mark(rel, "ok", sha256=a, bytes=q.stat().st_size)
        except OSError as e:
            man.mark(rel, "failed", repr(e))
    bad = [p for p in files if not man.done(str(p.relative_to(SRC)))]
    if bad:
        return f"failed {len(bad)} files"
    if not keep:
        shutil.rmtree(src)
        return "moved"
    if final.exists():
        os.rmdir(final)  # the junction only; its D: target stays as the backup
    os.rename(stage, final)
    return "copied"


def main(a):
    mode, names = a[0], [x for x in a[1:] if not x.startswith("--")]
    first = []
    if "--first" in a:
        lst = a[a.index("--first") + 1]
        names.remove(lst)
        first = [x.replace("\\", "/") for x in pathlib.Path(lst).read_text(encoding="utf-8").splitlines() if x]
    man = guard.Manifest(DST / "to_c_manifest.jsonl")
    status = "done"
    for n in names:
        r = transfer(n, man, keep=mode == "copy", first=first)
        print(f"{n}: {r}", flush=True)
        if r == "paused_disk":
            status = r
            break
        if r.startswith("failed"):
            status = "failed"
    guard.job_line(f"to_c_{mode}", man, status)


if __name__ == "__main__":
    main(sys.argv[1:])
