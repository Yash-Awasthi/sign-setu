"""ASL Citizen train-split clips landmarked so far (landmarks_asl, manifest "ok") as a flat cache: label "asl:GLOSS",
signer "asl:P<n>", source "asl", split "train". Rebuild as Q4 grows.

    python asl_cache.py OUT [--min N]   (stops without writing when fewer than N clips are ready)
"""
import csv
import json
import pathlib
import sys

import numpy as np

import cache
from guard import DATA

TRAIN = DATA / "asl_citizen" / "ASL_Citizen" / "splits" / "train.csv"
LM = DATA / "landmarks_asl"


def main(out, at_least=0):
    with open(TRAIN, encoding="utf-8") as f:
        meta = {"ASL_Citizen/videos/" + r["Video file"]: r for r in csv.DictReader(f)}
    done = [json.loads(l) for l in open(LM / "manifest.jsonl", encoding="utf-8")]
    ready = sorted({d["id"] for d in done if d["state"] == "ok"} & set(meta))
    if len(ready) < at_least:
        sys.exit(f"STOP: {len(ready)} ASL clips ready, {at_least} wanted")

    def samples():
        for rel in ready:
            c = np.load(LM / (rel + ".npz"))
            r = meta[rel]
            yield {k: c[k] for k in ("left", "right", "pose", "face", "has_left", "has_right", "has_pose", "has_face")} | {
                "aspect": float(c["aspect"]), "label": "asl:" + r["Gloss"], "split": "train",
                "signer": "asl:" + r["Participant ID"], "source": "asl", "clip": rel}
    cache.build(pathlib.Path(out), samples())


if __name__ == "__main__":
    a = sys.argv[1:]
    main(a[0], int(a[a.index("--min") + 1]) if "--min" in a else 0)
