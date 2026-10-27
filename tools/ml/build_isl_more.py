"""INCLUDE plus the Kaggle 60-word clips landmarked by prepare_isl_more.py, in include.pkl's schema with a signer field.
Kaggle signers come from kaggle_signers.json (no ids ship; matched by face and clothing per recording day). Signer C
is split "kaggle_test"."""
import json
import os
import pathlib
import pickle
import sys

import numpy as np

# Training and evaluation data stay outside the repository.
DATA_ROOT = pathlib.Path(os.environ.get("SIGNSETU_DATA", "ml-data"))
HELD_OUT = "C"


def main(data: pathlib.Path, out: pathlib.Path) -> None:
    with open(data / "include_pose" / "include.pkl", "rb") as f:
        cache = pickle.load(f)
    for s in cache["samples"]:
        s["signer"] = "include"
    root = data / "isl_more"
    signers = json.loads((root / "kaggle_signers.json").read_text())
    for p in sorted((root / "landmarks").glob("kaggle__*.pkl")):
        _, word, stem = p.stem.split("__")
        signer = signers[f"{word}/{stem}.mp4"]
        with open(p, "rb") as f:
            c = pickle.load(f)
        cache["samples"].append({
            "split": "kaggle_test" if signer == HELD_OUT else "train", "label": word.lower(), "signer": "kaggle_" + signer,
            "aspect": float(c["aspect"]), **{k: c[k].astype(np.float32) for k in ("left", "right", "pose")},
            **{k: c[k].astype(bool) for k in ("has_left", "has_right", "has_pose")}})
    with open(out, "wb") as f:
        pickle.dump(cache, f)
    ss = cache["samples"]
    print(f"{len(ss)} samples, {len({s['label'] for s in ss})} labels,",
          {sp: sum(s["split"] == sp for s in ss) for sp in ("train", "test", "kaggle_test")})


if __name__ == "__main__":
    main(DATA_ROOT, pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else DATA_ROOT / "isl_more" / "include_kaggle.pkl")
