"""Evaluation-only ISLRTC dictionary clips of INCLUDE words, each read whole, in include.pkl's schema (split "test").
match.json maps an INCLUDE label to its clips; the clips come from the dataset prepare_islrtc.py reads."""
import concurrent.futures
import json
import os
import pathlib
import pickle
import sys

import numpy as np

from prepare_islrtc import landmarks

# Training and evaluation data stay outside the repository.
DATA_ROOT = pathlib.Path(os.environ.get("SIGNSETU_DATA", "ml-data"))


def sample(label: str, path: pathlib.Path) -> dict:
    c = landmarks(path)
    return {"split": "test", "label": label, "aspect": float(c["aspect"]),
            **{k: c[k].astype(np.float32) for k in ("left", "right", "pose")},
            **{k: c[k].astype(bool) for k in ("has_left", "has_right", "has_pose")}}


def main(data: pathlib.Path) -> None:
    match = json.loads((data / "match.json").read_text(encoding="utf-8"))
    jobs = [(label, data / "raw" / rel) for label, rels in match.items() for rel in rels]
    missing = [str(p) for _, p in jobs if not p.exists()]
    if missing:
        sys.exit(f"{len(missing)} clips missing, first {missing[0]}")
    with concurrent.futures.ProcessPoolExecutor(max(1, (os.cpu_count() or 4) - 2)) as pool:
        samples = list(pool.map(sample, *zip(*jobs)))
    with open(data / "islrtc_words.pkl", "wb") as f:
        pickle.dump({"aspect": 16 / 9, "samples": samples}, f)
    print(f"{len(samples)} clips, {len({s['label'] for s in samples})} words")


if __name__ == "__main__":
    main(pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else DATA_ROOT / "islrtc")
