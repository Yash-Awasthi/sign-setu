"""Downloads the RKMVU-CBE ISL channel (one word or phrase per clip, word in the title; "-2" marks a variant) to
C:\\work\\ml-data\\rkmvu\\videos, best format up to 720p. Resumes through yt-dlp's archive and manifest.jsonl.

    python fetch_rkmvu.py [--sleep 0]
"""
import pathlib
import re
import subprocess
import threading
import time
from concurrent.futures import ThreadPoolExecutor
import sys

import guard
from guard import DATA

CHANNEL = "https://www.youtube.com/channel/UCmM7KPLEthAXiPVAgBF6rhA/videos"
OUT = DATA / "rkmvu"
FFMPEG = str(DATA.parent / "venvs" / "test2isl" / "Lib" / "site-packages" / "imageio_ffmpeg" / "binaries" / "ffmpeg-win-x86_64-v7.1.exe")
YT = [str(pathlib.Path(sys.executable).parent / "yt-dlp.exe"), "--js-runtimes", "node", "--retries", "5",
      "--fragment-retries", "5", "--no-progress", "--ffmpeg-location", FFMPEG]


def word(title: str) -> tuple[str, int]:
    m = re.match(r"^(.*?)\s*-\s*(\d+)\s*$", title)
    return (m.group(1).strip(), int(m.group(2))) if m else (title.strip(), 1)


def main():
    sleep = sys.argv[sys.argv.index("--sleep") + 1] if "--sleep" in sys.argv else "0"
    OUT.mkdir(parents=True, exist_ok=True)
    listing = OUT / "list.tsv"
    if not listing.exists():
        r = guard.retry(lambda: subprocess.run(YT + ["--flat-playlist", "--print", "%(id)s\t%(title)s\t%(duration)s",
                                                     CHANNEL], capture_output=True, text=True, check=True,
                                                encoding="utf-8"))
        guard.write_atomic(listing, r.stdout.encode("utf-8"))
    rows = [l.split("\t") for l in listing.read_text(encoding="utf-8").splitlines() if l.strip()]
    man = guard.Manifest(OUT / "manifest.jsonl")
    todo = [r for r in rows if not man.done(r[0])]
    # Clips over 30 s are stories and sentences, not word signs: skipped.
    todo = [r for r in todo if not (len(r) > 2 and r[2].replace('.', '', 1).isdigit() and float(r[2]) > 30)]
    status = "done"
    lock = threading.Lock()
    workers = int(sys.argv[sys.argv.index("--workers") + 1]) if "--workers" in sys.argv else 5
    done = [0]

    def fetch(chunk):
        nonlocal status
        if status != "done":
            return
        # Downloads use little RAM, so only a full disk stops them.
        if guard.check(OUT) == "paused_disk":
            status = "paused_disk"
            return
        args = YT + ["-f", "bv*[height<=720]+ba/b[height<=720]", "--merge-output-format", "mp4",
                     "--download-archive", str(OUT / "archive.txt"), "-o", str(OUT / "videos" / "%(id)s.%(ext)s"),
                     "--sleep-interval", sleep, "--print", "after_move:%(id)s	%(height)s"]
        r = subprocess.run(args + [f"https://www.youtube.com/watch?v={c[0]}" for c in chunk], capture_output=True,
                           text=True, encoding="utf-8", errors="replace")
        if "Sign in to confirm" in r.stderr and sleep == "0":
            print("bot check: rerun at 480p with --sleep 5", flush=True)
            status = "paused_bot"
        if "HTTP Error 429" in r.stderr:
            time.sleep(120)  # rate limited: this worker backs off; the clips retry on the next run
        with lock:
            archived = set((OUT / "archive.txt").read_text().split()) if (OUT / "archive.txt").exists() else set()
            for vid, title, dur in chunk:
                w, variant = word(title)
                if vid in archived:
                    man.mark(vid, "ok", word=w, variant=variant, title=title, duration=dur)
                else:
                    err = [l for l in r.stderr.splitlines() if vid in l and "ERROR" in l]
                    st = "quarantined" if man.attempts(vid) >= 4 else "failed"
                    man.mark(vid, st, err[0] if err else "not downloaded", title=title)
            done[0] += len(chunk)
            print(f"{done[0]}/{len(todo)}", flush=True)

    with ThreadPoolExecutor(workers) as ex:
        list(ex.map(fetch, [todo[k:k + 10] for k in range(0, len(todo), 10)]))
    guard.job_line("Q1_rkmvu", man, status)


if __name__ == "__main__":
    main()
