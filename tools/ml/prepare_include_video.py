"""Downloads the INCLUDE videos and caches them as landmarks from the app's own Holistic model, in include.pkl's schema."""
import concurrent.futures
import csv
import json
import os
import pathlib
import pickle
import shutil
import sys
import time
import urllib.request
import zipfile

import numpy as np

from prepare import ASPECT, label
from prepare_islrtc import landmarks

# Training and evaluation data stay outside the repository.
DATA_ROOT = pathlib.Path(os.environ.get("SIGNSETU_DATA", "ml-data"))

RECORD = "https://zenodo.org/api/records/4010759"


def key(rel: str) -> str:
    # The CSVs name every clip .MOV, but some files are .MP4.
    return str(pathlib.PurePosixPath(rel.replace("\\", "/")).with_suffix("")).lower().replace(" ", "")


def fetch(f: dict, videos: pathlib.Path) -> None:
    archive = videos.parent / f["key"]
    done = videos / (f["key"] + ".done")
    if done.exists():
        return
    part = archive.with_suffix(".part")
    for _ in range(50):
        if archive.exists():
            break
        have = part.stat().st_size if part.exists() else 0
        # Zenodo stalls long downloads, so each attempt times out and resumes where the last one stopped.
        try:
            req = urllib.request.Request(f["links"]["self"], headers={"Range": f"bytes={have}-"})
            with urllib.request.urlopen(req, timeout=60) as r, open(part, "ab" if r.status == 206 else "wb") as out:
                shutil.copyfileobj(r, out, 1 << 20)
        except OSError as e:
            print("retrying", f["key"], repr(e), flush=True)
            time.sleep(10)
            continue
        if part.stat().st_size == f["size"]:
            part.replace(archive)
    zipfile.ZipFile(archive).extractall(videos)
    done.touch()
    print("unzipped", f["key"], flush=True)


def extract(video: pathlib.Path, out: pathlib.Path) -> str | None:
    if out.exists():
        return None
    try:
        c = landmarks(video)
    except Exception as e:  # an unreadable clip is reported, not fatal
        return f"{video}: {e!r}"
    if not len(c["pose"]):
        return f"{video}: no frames"
    tmp = out.with_suffix(".tmp")
    with open(tmp, "wb") as f:
        pickle.dump(c, f)
    tmp.replace(out)
    return None


def main(data: pathlib.Path, root: pathlib.Path) -> None:
    videos, cache = root / "videos", root / "landmarks"
    videos.mkdir(parents=True, exist_ok=True)
    cache.mkdir(exist_ok=True)
    files = [f for f in json.load(urllib.request.urlopen(RECORD))["files"] if f["key"].endswith(".zip")]
    with concurrent.futures.ThreadPoolExecutor(4) as pool:
        list(pool.map(lambda f: fetch(f, videos), files))
    found = {key(str(p.relative_to(videos))): p for p in videos.rglob("*") if p.suffix.lower() in (".mov", ".mp4")}

    rows, missing = [], []
    for split in ("train", "test"):
        with open(data / "raw" / "INCLUDE" / "Train_Test_Split" / f"{split}_include.csv", newline="") as f:
            for row in csv.DictReader(f):
                video = found.get(key(row["FilePath"]))
                if video is None:
                    missing.append(row["FilePath"])
                    continue
                rows.append((split, row["FilePath"], video, cache / (key(row["FilePath"]).replace("/", "__") + ".pkl")))
    print(f"{len(rows)} clips listed, {len(missing)} without a video: {missing}", flush=True)

    workers = max(1, (os.cpu_count() or 4) - 2)
    with concurrent.futures.ProcessPoolExecutor(workers) as pool:
        jobs = [pool.submit(extract, video, out) for _, _, video, out in rows]
        for n, job in enumerate(concurrent.futures.as_completed(jobs), 1):
            if err := job.result():
                print("failed", err, flush=True)
            if n % 100 == 0:
                print(f"{n}/{len(jobs)} landmarked", flush=True)

    samples = []
    for split, rel, _, out in rows:
        if not out.exists():
            continue
        with open(out, "rb") as f:
            c = pickle.load(f)
        samples.append({
            "split": split, "label": label(pathlib.PurePosixPath(rel).parts[1]),
            **{k: c[k].astype(np.float32) for k in ("left", "right", "pose")},
            **{k: c[k].astype(bool) for k in ("has_left", "has_right", "has_pose")},
            "aspect": float(c["aspect"]),
        })
    with open(data / "include_app.pkl", "wb") as f:
        pickle.dump({"aspect": ASPECT, "samples": samples}, f)
    labels = sorted({s["label"] for s in samples})
    counts = {sp: sum(s["split"] == sp for s in samples) for sp in ("train", "test")}
    print(f"{len(samples)} samples, {len(labels)} labels, {counts}")


if __name__ == "__main__":
    main(pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else str(DATA_ROOT / "include_pose")),
         pathlib.Path(sys.argv[2] if len(sys.argv) > 2 else str(DATA_ROOT / "include_video")))
