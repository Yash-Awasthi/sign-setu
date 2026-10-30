"""Hand crops for the handshape model: for every cache row with a source video (ISLRTC, RKMVU, Kaggle), 8 of the
32 frames the reader samples (every 4th), both hands, a square box around the hand's 21 points plus 25%, resized to
96 px. INCLUDE ships landmarks only and gets no crops.

    python crops.py CACHE OUT [--workers 4] [--limit N] [--sources islrtc,rkmvu,kaggle]

OUT: crops.npy (rows, 8, 2, 96, 96, 3) uint8 memmap, present.npy (rows, 8, 2) bool, index.csv (cache row, clip,
source, split, signer, label, offset). A cache row is a span of its clip's landmarks; its offset is found by matching
the row's frames to the landmark file, and frame times come from that file.
"""
import multiprocessing as mp
import os
import pathlib
import pickle
import sys

# One thread per worker (decoder and maths libraries): each otherwise takes several cores, starving landmarking.
for _v in ("OMP_NUM_THREADS", "MKL_NUM_THREADS", "OPENBLAS_NUM_THREADS"):
    os.environ.setdefault(_v, "1")

import numpy as np  # noqa: E402
import pandas as pd  # noqa: E402

from cache import frames_of, raw_frames  # noqa: E402
from features import resample_index  # noqa: E402
from guard import DATA  # noqa: E402

SIZE, PICK, MARGIN, READER_T = 96, 8, 0.25, 32
LM = DATA / "landmarks"
KAGGLE_LM = DATA / "isl_more" / "landmarks"
KAGGLE_VIDEO = DATA / "isl_kaggle60" / "Video_Dataset" / "Video_Dataset"
N_INCLUDE = 4284  # include_kaggle/<k>: samples before this index are INCLUDE's
_KAGGLE = None


def kaggle_files():
    global _KAGGLE
    if _KAGGLE is None:
        _KAGGLE = sorted(KAGGLE_LM.glob("kaggle__*.pkl"))
    return _KAGGLE


def source_of(clip, source):
    """Video path, landmark dict and frame times in ms for one cache clip id."""
    if source == "kaggle":
        p = kaggle_files()[int(clip.split("/")[-1]) - N_INCLUDE]
        _, word, stem = p.stem.split("__")
        c = pickle.load(open(p, "rb"))  # nosemgrep: trailofbits.python.pickles-in-pytorch.pickles-in-pytorch
        return KAGGLE_VIDEO / word / f"{stem}.mp4", c, np.arange(len(c["pose"])) * 1000.0 / float(c["fps"])
    rel = clip.replace("\\", "/")
    c = dict(np.load(LM / (rel + ".npz")))
    return DATA / rel, c, c["t"].astype(np.float64)


def box(points, w, h):
    x, y = points[:, 0] * w, points[:, 1] * h
    cx, cy = (x.min() + x.max()) / 2, (y.min() + y.max()) / 2
    side = max(x.max() - x.min(), y.max() - y.min()) * (1 + MARGIN) + 8
    return int(cx - side / 2), int(cy - side / 2), int(side)


def cut(img, b):
    import cv2
    x, y, s = b
    h, w = img.shape[:2]
    pad = cv2.copyMakeBorder(img, s, s, s, s, cv2.BORDER_CONSTANT, value=0)
    return cv2.resize(pad[y + s:y + 2 * s, x + s:x + 2 * s], (SIZE, SIZE), interpolation=cv2.INTER_AREA)


def one(job):
    """Crops of one cache row, or None when its video or landmarks cannot be matched."""
    import cv2
    import torch
    cv2.setNumThreads(1)
    torch.set_num_threads(1)
    clip, source, x = job
    try:
        video, c, t = source_of(clip, source)
    except (OSError, IndexError, ValueError):
        return None
    full = raw_frames(c)
    n = len(x)
    starts = np.where(np.abs(full[:len(full) - n + 1] - x[0]).max(1) < 1e-4)[0]
    off = next((int(o) for o in starts if np.allclose(full[o:o + n], x, atol=1e-4)), None)
    if off is None:
        return None
    frames = off + resample_index(n, READER_T)[::READER_T // PICK]
    cap = cv2.VideoCapture(str(video), cv2.CAP_FFMPEG, [cv2.CAP_PROP_N_THREADS, 1])
    if not cap.isOpened():
        return None
    out = np.zeros((PICK, 2, SIZE, SIZE, 3), np.uint8)
    seen = np.zeros((PICK, 2), bool)
    # One seek, then forward reads: a seek per frame decodes from the keyframe each time.
    cap.set(cv2.CAP_PROP_POS_MSEC, float(t[frames[0]]))
    ok, img = cap.read()
    at = cap.get(cv2.CAP_PROP_POS_MSEC)
    for k, f in enumerate(frames):
        while ok and at < t[f] - 1:
            ok = cap.grab()
            at = cap.get(cv2.CAP_PROP_POS_MSEC)
            img = None
        if not ok:
            break
        if img is None:
            ok, img = cap.retrieve()
            if not ok:
                break
        h, w = img.shape[:2]
        for j, side in enumerate(("left", "right")):
            if c[f"has_{side}"][f]:
                out[k, j] = cut(img, box(np.asarray(c[side][f]), w, h))
                seen[k, j] = True
    cap.release()
    return out, seen, off


def main(cache, out, workers=4, limit=0, sources=("islrtc", "rkmvu", "kaggle")):
    cache, out = pathlib.Path(cache), pathlib.Path(out)
    index = pd.read_csv(cache / "index.csv", keep_default_na=False)
    rows = index[index["source"].isin(sources)]
    rows = rows.head(limit) if limit else rows
    raw = frames_of(cache, index)
    out.mkdir(parents=True, exist_ok=True)
    shape = (len(rows), PICK, 2, SIZE, SIZE, 3)
    progress = out / "progress.npz"
    # A rerun into the same OUT resumes: rows tried before (saved every 500) are skipped.
    if progress.exists() and (out / "crops.npy").exists():
        crops = np.lib.format.open_memmap(out / "crops.npy", "r+")
        assert crops.shape == shape, "OUT holds crops of another row set"
        p = np.load(progress)
        present, offsets, tried = p["present"], p["offsets"], p["tried"]
    else:
        crops = np.lib.format.open_memmap(out / "crops.npy", "w+", np.uint8, shape)
        present, offsets, tried = np.zeros(shape[:3], bool), np.full(len(rows), -1), np.zeros(len(rows), bool)
    todo = np.where(~tried)[0]

    def jobs():
        for i in todo:
            r = rows.index[i]
            yield rows.at[r, "clip"], rows.at[r, "source"], np.asarray(raw(r)[rows.at[r, "start"]:rows.at[r, "start"] + rows.at[r, "length"]])

    with mp.Pool(workers, maxtasksperchild=200) as pool:
        for k, (i, res) in enumerate(zip(todo, pool.imap(one, jobs(), chunksize=4))):
            if res is not None:
                crops[i], present[i], offsets[i] = res
            tried[i] = True
            if k % 500 == 0:
                crops.flush()
                np.savez(progress, present=present, offsets=offsets, tried=tried)
                print(f"crops {int(tried.sum())}/{len(rows)}", flush=True)
    crops.flush()
    np.savez(progress, present=present, offsets=offsets, tried=tried)
    np.save(out / "present.npy", present)
    meta = rows[["clip", "source", "split", "signer", "label"]].copy()
    meta.insert(0, "row", rows.index)
    meta["offset"] = offsets
    meta.to_csv(out / "index.csv", index=False)
    print(f"JOB crops ok={int((offsets >= 0).sum())} failed={int((offsets < 0).sum())} rows={len(rows)} "
          f"hands_seen={present.mean():.3f} status=done")


if __name__ == "__main__":
    a = sys.argv[1:]
    opt = lambda k, d: type(d)(a[a.index(k) + 1]) if k in a else d  # noqa: E731
    main(a[0], a[1], opt("--workers", 4), opt("--limit", 0), tuple(opt("--sources", "islrtc,rkmvu,kaggle").split(",")))
