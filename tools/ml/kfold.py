"""Five-fold retrieval, split by signer: the yardstick every later number is compared against.

Reads a landmark cache (`index.csv` + memory-mapped `raw.npy`) strictly read-only. Each clip is resampled
to 32 frames and flattened, the gallery is one training clip per word, and every clip in the held-out fold
is a query. Results are pooled across folds; per-fold numbers are printed as a sanity check only.

    python kfold.py --cache C:/work/ml-data/cache_v6b --folds 5 --gallery 1
    python kfold.py --cache C:/work/ml-data/cache_v6b --folds 5 --gallery 1 --matcher dtw --limit 300

Measured baseline (2026-11-12): top-1 0.071, top-5 0.136 on 4,090 queries.
"""
import argparse
import csv
import os
import pathlib

import numpy as np
import concurrent.futures as cf

# Module-level state shared with worker processes (set via Pool initializer).
_QR_PATH = None
_GR_PATH = None


def _dtw_init_memmap(qr_path, gr_path):
    global _QR_PATH, _GR_PATH
    _QR_PATH = qr_path
    _GR_PATH = gr_path


def _dtw_row_memmap(arg):
    """DTW distances from query row n to every gallery clip; arrays loaded via memmap (tiny pickle)."""
    n, n_q, n_g, dim = arg
    Qr = np.load(_QR_PATH, mmap_mode="r").reshape(n_q, FRAMES, -1)
    Gr = np.load(_GR_PATH, mmap_mode="r").reshape(n_g, FRAMES, -1)
    d = np.array([banded_dtw(Qr[n], Gr[j]) for j in range(n_g)], dtype=np.float32)
    return d

FRAMES = 32


def clip_vector(raw, start, length):
    """One clip as a flat float32 vector: 32 frames, nearest index, memory-mapped rows only."""
    idx = np.linspace(0, length - 1, FRAMES).round().astype(np.int64) + start
    return np.asarray(raw[idx], dtype=np.float32).reshape(-1)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cache", default="C:/work/ml-data/cache_v6b")
    ap.add_argument("--folds", type=int, default=5)
    ap.add_argument("--gallery", type=int, default=1, help="clips per word in the gallery")
    ap.add_argument("--matcher", default="cosine", choices=["cosine", "dtw"])
    ap.add_argument("--limit", type=int, help="cap the queries per fold, for a quick smoke run")
    ap.add_argument("--queries", default="all", choices=["all", "notrain"],
                    help="all clips in the held-out fold, or only the non-train splits")
    a = ap.parse_args()

    cache = pathlib.Path(a.cache)
    rows = list(csv.DictReader(open(cache / "index.csv", encoding="utf-8")))
    raw = np.load(cache / "raw.npy", mmap_mode="r")
    print(f"index {len(rows):,} clips, raw {raw.shape} {raw.dtype}", flush=True)

    # Fold by person, never by clip: a fold's people never appear in its training folds.
    signers = sorted({r["signer"] for r in rows})
    fold_of = {s: i % a.folds for i, s in enumerate(signers)}
    label = np.array([r["label"] for r in rows])
    fold = np.array([fold_of[r["signer"]] for r in rows])
    print(f"{len(signers)} signers, {a.folds} folds, gallery {a.gallery}/word", flush=True)

    tot = hit1 = hit5 = 0

    # One persistent worker pool for DTW across all folds (Windows spawns a new process per
    # pool creation; recreating the pool per fold eventually fails). Query/gallery arrays are
    # shared through temp memmap files so each task picks up only a row index.
    dtw_pool = None
    qr_path = gr_path = None
    if a.matcher == "dtw":
        import tempfile
        td = tempfile.gettempdir()
        qr_path = os.path.join(td, "kfold_qr.npy")
        gr_path = os.path.join(td, "kfold_gr.npy")
        workers = max(1, os.cpu_count() or 4)
        dtw_pool = cf.ProcessPoolExecutor(
            max_workers=workers,
            initializer=_dtw_init_memmap,
            initargs=(qr_path, gr_path),
        )

    for k in range(a.folds):
        train_idx = np.flatnonzero(fold != k)
        query_idx = np.flatnonzero(fold == k)
        if a.queries == "notrain":
            query_idx = np.array([i for i in query_idx if rows[i]["split"] != "train"], dtype=np.int64)

        # Gallery: the first `gallery` training clips of each word, in index order.
        seen = {}
        gallery = []
        for i in train_idx:
            n = seen.get(label[i], 0)
            if n < a.gallery:
                seen[label[i]] = n + 1
                gallery.append(i)
        gallery = np.array(gallery)

        G = np.stack([clip_vector(raw, int(rows[i]["start"]), int(rows[i]["length"])) for i in gallery])
        G /= np.linalg.norm(G, axis=1, keepdims=True) + 1e-8
        Gl = label[gallery]

        q = query_idx if not a.limit else query_idx[: a.limit]
        Q = np.stack([clip_vector(raw, int(rows[i]["start"]), int(rows[i]["length"])) for i in q])
        Q /= np.linalg.norm(Q, axis=1, keepdims=True) + 1e-8

        if a.matcher == "dtw":
            # The one-clip store is small enough to warp frame by frame; this is the honest test of
            # whether a single dictionary example can be matched at all.
            Qr = Q.reshape(len(q), FRAMES, -1)
            Gr = G.reshape(len(gallery), FRAMES, -1)
            np.save(qr_path, Qr)
            np.save(gr_path, Gr)
            n_q, n_g, dim = Qr.shape[0], Gr.shape[0], Qr.shape[2]
            D = np.stack(list(dtw_pool.map(_dtw_row_memmap, [(n, n_q, n_g, dim) for n in range(len(q))])))
            order = np.argsort(D, axis=1)[:, :5]
            top1 = order[:, 0]
            top5 = order
            best1, best5 = top1, top5
            score = None
        else:
            sims = Q @ G.T
            part = np.argpartition(-sims, min(4, sims.shape[1] - 1), axis=1)[:, :5]
            order = np.take_along_axis(part, np.argsort(-np.take_along_axis(sims, part, axis=1), axis=1), axis=1)
            best1, best5 = order[:, 0], order

        t1 = int((Gl[best1] == label[q]).sum())
        t5 = int((Gl[best5] == label[q][:, None]).any(axis=1).sum())
        print(f"fold {k}: {len(q):5,d} queries, top-1 {t1 / len(q):.3f}, top-5 {t5 / len(q):.3f}", flush=True)
        tot += len(q)
        hit1 += t1
        hit5 += t5

    print(f"POOLED n={tot:,} top-1 {hit1 / tot:.3f} top-5 {hit5 / tot:.3f}", flush=True)

    if dtw_pool is not None:
        dtw_pool.shutdown(wait=True)


def banded_dtw(a, b, band=None):
    """Banded DTW over frame distance; the band keeps it linear instead of quadratic in time.
    Pairwise frame distances are precomputed with a single vectorized op; the DP recurrence is
    unchanged, so results are identical to the per-cell loop."""
    n, m = len(a), len(b)
    band = band or max(4, abs(n - m) + 4)
    diff = a[:, None, :] - b[None, :, :]
    dist = np.sqrt(np.einsum("ijk,ijk->ij", diff, diff)).astype(np.float32)
    cost = np.full((n + 1, m + 1), np.inf, dtype=np.float32)
    cost[0, 0] = 0.0
    for i in range(1, n + 1):
        lo, hi = max(1, i - band), min(m, i + band)
        for j in range(lo, hi + 1):
            cost[i, j] = dist[i - 1, j - 1] + min(
                cost[i - 1, j], cost[i, j - 1], cost[i - 1, j - 1]
            )
    return float(cost[n, m] / max(n, m))


if __name__ == "__main__":
    main()
