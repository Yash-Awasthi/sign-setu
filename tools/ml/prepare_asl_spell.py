"""Builds the real fingerspelling cache for `spell_ctc.py` from Google's ASL Fingerspelling set (Kaggle
`asl-fingerspelling`, CC BY 4.0), downloaded and unzipped by hand. Phrases with digits are dropped, as digits share
hand shapes with letters; other symbols are signed but not targets, so the reader learns to stay blank on them."""
import csv
import os
import pathlib
import pickle
import re
import sys
from concurrent.futures import ProcessPoolExecutor

import numpy as np

from prepare_asl_signs import aspect

# Training and evaluation data stay outside the repository.
DATA_ROOT = pathlib.Path(os.environ.get("SIGNSETU_DATA", "ml-data"))

FPS = 30  # the set's capture rate; frames carry no timestamps
TEST_SHARE = 10  # every tenth participant, by id, never trains


def part(args) -> list:
    path, rows = args
    import pyarrow.parquet as pq
    cols = ["sequence_id", "frame"] + [f"{a}_{h}_hand_{i}" for h in ("left", "right") for a in "xy" for i in range(21)]
    t = pq.read_table(path, columns=cols)
    seq, frame = t.column("sequence_id").to_numpy(), t.column("frame").to_numpy().astype(np.int64)
    pts = {h: np.stack([np.stack([t.column(f"{a}_{h}_hand_{i}").to_numpy(zero_copy_only=False) for i in range(21)], 1)
                        for a in "xy"], 2).astype(np.float32) for h in ("left", "right")}
    out = []
    for sid, r in rows.items():
        m = np.flatnonzero(seq == sid)
        if not len(m):
            continue
        m = m[np.argsort(frame[m], kind="stable")]
        frames = frame[m]
        s = {"text": re.sub("[^a-z]", "", r["phrase"].lower()), "signer": r["participant_id"],
             "ts": (frames - frames[0]) * 1000 // FPS}
        for h in ("left", "right"):
            xy = pts[h][m]
            s["has_" + h] = np.isfinite(xy).all((1, 2))
            s[h] = np.nan_to_num(xy)
        if s["text"] and (s["has_left"].any() or s["has_right"].any()):
            out.append(s)
    return out


def main(src: pathlib.Path, out: pathlib.Path, workers: int = 6) -> None:
    by_file: dict[str, dict] = {}
    for r in csv.DictReader(open(src / "train.csv", encoding="utf-8")):
        if not re.search("[0-9]", r["phrase"]):
            by_file.setdefault(pathlib.Path(r["path"]).name, {})[int(r["sequence_id"])] = r
    landmarks = next(p.parent for p in src.rglob("*.parquet"))
    with ProcessPoolExecutor(workers) as ex:
        samples = [s for ss in ex.map(part, [(landmarks / f, rows) for f, rows in by_file.items()]) for s in ss]
    signers = sorted({s["signer"] for s in samples}, key=int)
    test = set(signers[::TEST_SHARE])
    by_signer = {}
    for s in samples:
        by_signer.setdefault(s["signer"], []).append(s)
    for ss in by_signer.values():
        a = aspect(ss)
        for s in ss:
            s.update(aspect=a, split="test" if s["signer"] in test else "train")
    with open(out, "wb") as f:
        pickle.dump({"samples": samples}, f)
    print(f"{len(samples)} sequences, {len(signers)} signers, {len(test)} held out -> {out}")


if __name__ == "__main__":
    a = sys.argv[1:]
    main(pathlib.Path(a[0]) if a else DATA_ROOT / "kaggle_spell",
         pathlib.Path(a[1]) if len(a) > 1 else DATA_ROOT / "asl_spell" / "asl_spell_real.pkl",
         int(a[2]) if len(a) > 2 else 6)
