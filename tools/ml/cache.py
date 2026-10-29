"""Flat landmark cache: raw.npy (all frames, models.RAW float32, uncompressed) opened with mmap_mode="r", so DataLoader
workers share the OS page cache instead of each unpickling a copy, plus index.csv (one row per clip).

    python cache.py build OUT SOURCE.pkl [...] [--limit N]      pickled sample lists (train.py schema)
    python cache.py check OUT [--workers 4]                     one epoch through a DataLoader, peak RAM of the tree
"""
import hashlib
import json
import pathlib
import sys
import threading
import time

import numpy as np
import pandas as pd
import torch

from features import resample_index
from models import FACE, RAW, from_cache_frame

T = 48
REGISTRY = pathlib.Path(__file__).resolve().parent / "cache_registry.json"


def raw_frames(s):
    n = len(s["pose"])
    face = s.get("face", np.zeros((n, FACE, 2), np.float32))
    has_face = s.get("has_face", np.zeros(n, bool))
    return from_cache_frame(s["left"], s["right"], s["pose"], face, s["has_left"], s["has_right"], s["has_pose"], has_face)


def build(out: pathlib.Path, samples, meta_keys=("label", "split", "signer", "source", "clip")):
    """samples: iterable of dicts with per-part arrays; written in two passes so only one clip is in memory at a time."""
    out.mkdir(parents=True, exist_ok=True)
    samples = list(samples)
    total = sum(len(s["pose"]) for s in samples)
    arr = np.lib.format.open_memmap(out / "raw.tmp.npy", "w+", np.float32, (total, RAW))
    rows, at = [], 0
    for s in samples:
        x = raw_frames(s)
        arr[at:at + len(x)] = x
        rows.append({"start": at, "length": len(x), "aspect": float(s.get("aspect", 848 / 480)),
                     **{k: s.get(k, "") for k in meta_keys}})
        at += len(x)
    arr.flush()
    del arr
    (out / "raw.tmp.npy").replace(out / "raw.npy")
    pd.DataFrame(rows).to_csv(out / "index.csv", index=False)
    h = hashlib.sha256()
    with open(out / "raw.npy", "rb") as f:
        while b := f.read(1 << 24):
            h.update(b)
    reg = json.loads(REGISTRY.read_text()) if REGISTRY.exists() else {}
    reg[str(out)] = {"sha256": h.hexdigest(), "clips": len(rows), "frames": total, "date": time.strftime("%Y-%m-%d")}
    REGISTRY.write_text(json.dumps(reg, indent=1) + "\n", encoding="utf-8")
    print(f"cache {out}: {len(rows)} clips, {total} frames, {total * RAW * 4 / 2**30:.2f} GB", flush=True)


def frames_of(root, index):
    """Row to the memmap holding its frames: a joined index (join_cache.py) names each row's cache in "cache"."""
    if "cache" not in index:
        raw = np.load(pathlib.Path(root) / "raw.npy", mmap_mode="r")
        return lambda r: raw
    maps = {c: np.load(pathlib.Path(c) / "raw.npy", mmap_mode="r") for c in index["cache"].unique()}
    col = index["cache"].to_numpy()
    return lambda r: maps[col[r]]


class Clips(torch.utils.data.Dataset):
    """One clip resampled to T raw frames; time jitter only, everything else is batched on the GPU."""

    def __init__(self, root, rows=None, train=True, t=T):
        self.root = pathlib.Path(root)
        self.index = pd.read_csv(self.root / "index.csv", keep_default_na=False)
        self.rows = np.arange(len(self.index)) if rows is None else np.asarray(rows)
        self.start = self.index["start"].to_numpy()
        self.length = self.index["length"].to_numpy()
        self.aspect = self.index["aspect"].to_numpy(np.float32)
        self.train, self.t = train, t
        self.raw = None  # opened lazily in each worker; an open memmap does not pickle across spawn

    def __len__(self):
        return len(self.rows)

    def __getitem__(self, i):
        if self.raw is None:
            self.raw = frames_of(self.root, self.index)
        r = self.rows[i]
        a, n = int(self.start[r]), int(self.length[r])
        idx = np.arange(n)
        if self.train and n > 4:
            rng = np.random.default_rng()
            lo, hi = rng.integers(0, n * 15 // 100 + 1, 2)
            idx = idx[lo:n - hi]
            # Slow-down and speed-up: resample to 0.8 to 1.2 times as many frames with drops and repeats.
            idx = np.sort(rng.choice(idx, max(2, int(len(idx) * rng.uniform(0.8, 1.2))), replace=True))
        pick = idx[resample_index(len(idx), self.t)]
        return torch.from_numpy(np.ascontiguousarray(self.raw(r)[a + pick])), self.aspect[r], int(r)


def check(out: pathlib.Path, workers=4):
    import psutil
    from models import ConvTransformer
    me = psutil.Process()
    peak = [0.0]
    stop = threading.Event()

    def watch():
        while not stop.wait(0.2):
            procs = [me] + me.children(recursive=True)
            total = 0
            for p in procs:
                try:
                    total += p.memory_info().rss
                except psutil.Error:
                    pass
            peak[0] = max(peak[0], total / 2**30)

    threading.Thread(target=watch, daemon=True).start()
    ds = Clips(out)
    dl = torch.utils.data.DataLoader(ds, batch_size=64, shuffle=True, num_workers=workers, persistent_workers=False)
    dev = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model = ConvTransformer(classes=400).to(dev)
    opt = torch.optim.AdamW(model.parameters(), 1e-3)
    t0, n = time.time(), 0
    for x, aspect, r in dl:
        y = torch.randint(0, 400, (len(x),), device=dev)
        loss = torch.nn.functional.cross_entropy(model(x.to(dev), aspect[:, None].to(dev)), y)
        opt.zero_grad()
        loss.backward()
        opt.step()
        n += len(x)
    stop.set()
    ok = peak[0] < 12
    print(f"CHECK {n} clips, {workers} workers, {time.time() - t0:.0f} s, peak RAM {peak[0]:.2f} GB: "
          f"{'PASS' if ok else 'FAIL'}", flush=True)
    return ok


if __name__ == "__main__":
    import pickle
    cmd, out = sys.argv[1], pathlib.Path(sys.argv[2])
    if cmd == "build":
        rest = sys.argv[3:]
        limit = int(rest[rest.index("--limit") + 1]) if "--limit" in rest else None
        srcs = [a for a in rest if a.endswith(".pkl")]
        samples = []
        for src in srcs:
            with open(src, "rb") as f:
                d = pickle.load(f)
            for i, s in enumerate(d["samples"]):
                s.setdefault("aspect", d["aspect"])
                s.setdefault("source", pathlib.Path(src).stem)
                s.setdefault("clip", f"{pathlib.Path(src).stem}/{i}")
                samples.append(s)
        build(out, samples[:limit])
    else:
        w = sys.argv[sys.argv.index("--workers") + 1] if "--workers" in sys.argv else 4
        sys.exit(0 if check(out, int(w)) else 1)
