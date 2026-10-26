"""Writes the most typical recorded clip of every sentence as a prediction file for export_clips.py.

The typical clip is the medoid: the one with the smallest summed DTW distance to the sentence's other clips, so a
mislabelled or badly tracked recording is never the one shipped.

    python pick_clips.py --data_dir processed_data --labels labels.csv --out medoids.json
"""
import argparse
import json
from pathlib import Path

import numpy as np
import pandas as pd


def dtw(a: np.ndarray, b: np.ndarray) -> float:
    """Mean per-step distance along the best alignment of two (frames, features) arrays."""
    cost = np.linalg.norm(a[:, None, :] - b[None, :, :], axis=-1)
    acc = np.full((len(a) + 1, len(b) + 1), np.inf)
    acc[0, 0] = 0.0
    for i in range(1, len(a) + 1):
        row = acc[i]
        prev = acc[i - 1]
        for j in range(1, len(b) + 1):
            row[j] = cost[i - 1, j - 1] + min(prev[j], prev[j - 1], row[j - 1])
    return float(acc[-1, -1] / (len(a) + len(b)))


def medoid(clips: list) -> int:
    """Index of the clip closest to all the others; xy of the 48 points, since depth is the noisiest axis."""
    xy = [c[:, 52:].reshape(len(c), 48, 3)[:, :, :2].reshape(len(c), -1) for c in clips]
    totals = np.zeros(len(clips))
    for i in range(len(clips)):
        for j in range(i + 1, len(clips)):
            d = dtw(xy[i], xy[j])
            totals[i] += d
            totals[j] += d
    return int(np.argmin(totals))


def pick(data_dir: Path, labels: pd.DataFrame) -> dict:
    out = {}
    for sentence, rows in labels.groupby("sentence"):
        names = sorted(rows["filename"])
        clips = [np.load(data_dir / f"{n}.npy") for n in names]
        best = medoid(clips)
        out[sentence.strip()] = {"source": names[best],
                                 "frames": [{"coordinates": f[52:].tolist()} for f in clips[best]]}
        print(f"{sentence.strip()}: {names[best]} of {len(names)}")
    return out


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--data_dir", default="processed_data")
    parser.add_argument("--labels", default="labels.csv")
    parser.add_argument("--out", default="medoids.json")
    args = parser.parse_args()
    picked = pick(Path(args.data_dir), pd.read_csv(args.labels))
    Path(args.out).write_text(json.dumps(picked), encoding="utf-8")
    print(f"{args.out}: {len(picked)} sentences")
