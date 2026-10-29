"""I11 (evaluation only): sl-hwgat's HandCorrection idea on a copy of a cache. A hand missing for some frames is filled
by a quadratic through the 3 nearest frames where it was seen (a local k=2 spline; no scipy here); missing at the start
or end, it sits on its own wrist from the pose. Filled frames are marked present.

    python handfill.py C:/work/ml-data/cache_v5 C:/work/ml-data/cache_v5_hc
"""
import hashlib
import json
import pathlib
import shutil
import sys

import numpy as np
import pandas as pd

import cache
from models import L, P, PRES, R


def fill(x):
    x = x.copy()
    t = np.arange(len(x))
    pose = x[:, P].reshape(len(x), 33, 2)
    for k, sl, wrist in ((0, L, 15), (1, R, 16)):
        seen = x[:, PRES.start + k] > 0
        if seen.all() or not seen.any():
            continue
        have = t[seen]
        pts = x[:, sl].reshape(len(x), 21, 2)
        for i in t[~seen]:
            if i < have[0] or i > have[-1]:
                if x[i, PRES.start + 2] <= 0:
                    continue
                pts[i] = pts[have[0] if i < have[0] else have[-1]] - pts[have[0] if i < have[0] else have[-1], 0] + pose[i, wrist]
            else:
                near = have[np.argsort(np.abs(have - i))[:3]]
                if len(near) < 3:
                    a, b = near[0], near[-1]
                    w = 0 if a == b else (i - a) / (b - a)
                    pts[i] = pts[a] * (1 - w) + pts[b] * w
                else:
                    # Lagrange quadratic through the three nearest seen frames.
                    acc = np.zeros_like(pts[i])
                    for j in range(3):
                        lj = np.prod([(i - near[m]) / (near[j] - near[m]) for m in range(3) if m != j])
                        acc += lj * pts[near[j]]
                    pts[i] = acc
            x[i, sl] = pts[i].reshape(-1)
            x[i, PRES.start + k] = 1.0
    return x


def main(src, dst):
    src, dst = pathlib.Path(src), pathlib.Path(dst)
    dst.mkdir(parents=True, exist_ok=True)
    for f in src.iterdir():
        if f.name != "raw.npy" and f.is_file():
            shutil.copyfile(f, dst / f.name)
    idx = pd.read_csv(src / "index.csv", keep_default_na=False)
    raw = np.load(src / "raw.npy", mmap_mode="r")
    out = np.lib.format.open_memmap(dst / "raw.npy", "w+", raw.dtype, raw.shape)
    for s, n in zip(idx["start"], idx["length"]):
        out[s:s + n] = fill(np.asarray(raw[s:s + n]))
    out.flush()
    del out
    h = hashlib.sha256()
    with open(dst / "raw.npy", "rb") as f:
        while b := f.read(1 << 24):
            h.update(b)
    reg = json.loads(cache.REGISTRY.read_text(encoding="utf-8"))
    reg[str(dst)] = {**reg.get(str(src), {}), "sha256": h.hexdigest(), "derived_from": str(src), "transform": "handfill"}
    cache.REGISTRY.write_text(json.dumps(reg, indent=1) + "\n", encoding="utf-8")
    print(f"handfill {src} -> {dst}", flush=True)


if __name__ == "__main__":
    main(*sys.argv[1:3])
