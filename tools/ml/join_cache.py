"""An index over an ISL cache and the ASL train cache (asl_cache.py) for co-training with a head per language (B5).
No frames are copied: each row names its source cache in the "cache" column and reads that cache's raw.npy. The ISL
cache's splits, test hash and vocabulary are kept; the ASL glosses ("asl:...") are added to the vocabulary.

    python join_cache.py ISL_CACHE ASL_CACHE OUT
"""
import json
import pathlib
import shutil
import sys

import pandas as pd


def main(isl: pathlib.Path, asl: pathlib.Path, out: pathlib.Path) -> None:
    out.mkdir(parents=True, exist_ok=True)
    parts = []
    for root in (isl, asl):
        part = pd.read_csv(root / "index.csv", keep_default_na=False)
        part["cache"] = str(root)
        parts.append(part)
    index = pd.concat(parts, ignore_index=True).fillna("")
    index.to_csv(out / "index.csv", index=False)
    vocab = json.loads((isl / "vocab.json").read_text(encoding="utf-8"))
    vocab += sorted(set(parts[1]["label"]) - set(vocab))
    (out / "vocab.json").write_text(json.dumps(vocab, ensure_ascii=False), encoding="utf-8")
    for name in ("test_hash.txt", "g2.json"):
        if (isl / name).exists():
            shutil.copyfile(isl / name, out / name)
    print(f"index {out}: {len(parts[0])} ISL + {len(parts[1])} ASL clips, {len(vocab)} words", flush=True)


if __name__ == "__main__":
    main(*(pathlib.Path(p).resolve() for p in sys.argv[1:4]))
