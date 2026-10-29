"""Contributed sign recordings into a landmark cache (cache.py layout), one signer id per contributor, so contributed
clips join the signer-grouped splits: a contributor lands in train or test, never both.

    python import_contrib.py OUT SOURCE [...]
SOURCE is a gateway file (CONTRIB_DIR/<signer>.jsonl) or a folder of phone exports (files/contrib/sessions/*.json).
"""
import json
import pathlib
import sys

import numpy as np

import cache
from models import FACE

ARM_POSE = range(11, 17)


def records(src: pathlib.Path):
    """(signer, record) pairs from a gateway JSON lines file or a folder of single-record phone files."""
    files = sorted(src.rglob("*.json*")) if src.is_dir() else [src]
    for f in files:
        if f.suffix == ".jsonl":
            for line in f.read_text(encoding="utf-8").splitlines():
                if line.strip():
                    r = json.loads(line)
                    yield r["signer"], r["record"]
        else:
            r = json.loads(f.read_text(encoding="utf-8"))
            yield r.get("session", {}).get("signer", "unknown"), r


def sample(frames, aspect):
    """Recorder frames (hands 21x2, arms = pose 11..16) as the per-part arrays cache.build takes."""
    n = len(frames)
    left, right = np.zeros((n, 21, 2), np.float32), np.zeros((n, 21, 2), np.float32)
    pose = np.zeros((n, 33, 2), np.float32)
    has = {k: np.zeros(n, bool) for k in ("has_left", "has_right", "has_pose")}
    for i, f in enumerate(frames):
        if f.get("left"):
            left[i] = np.reshape(f["left"], (21, 2))
            has["has_left"][i] = True
        if f.get("right"):
            right[i] = np.reshape(f["right"], (21, 2))
            has["has_right"][i] = True
        if f.get("arms"):
            pose[i, list(ARM_POSE)] = np.reshape(f["arms"], (6, 2))
            has["has_pose"][i] = True
    return {"left": left, "right": right, "pose": pose, "face": np.zeros((n, FACE, 2), np.float32),
            "has_face": np.zeros(n, bool), **has, "aspect": aspect}


def main(out: pathlib.Path, sources):
    samples = []
    for src in sources:
        for signer, r in records(pathlib.Path(src)):
            w = r["window"]
            gloss = [g.lower() for g in (r.get("correction") and r["correction"].split() or r["gloss"])]
            signs = w.get("signs", [])
            # One sign per gloss word when the counts agree; otherwise the window is kept whole as a sentence.
            parts = list(zip(signs, gloss)) if len(signs) == len(gloss) else [({"frames": [f for s in signs for f in s["frames"]]}, " ".join(gloss))]
            for k, (s, label) in enumerate(parts):
                if len(s["frames"]) < 3:
                    continue
                samples.append({**sample(s["frames"], float(w.get("aspect", 480 / 848))), "label": label,
                                "signer": f"contrib:{signer}", "source": f"contrib:{r['kind']}", "split": "",
                                "clip": f"{signer}/{w.get('eventId', 0)}/{k}"})
    if not samples:
        print("no contributed clips", flush=True)
        return
    cache.build(out, samples)
    signers = {s["signer"] for s in samples}
    print(f"{len(samples)} clips from {len(signers)} contributors", flush=True)


if __name__ == "__main__":
    main(pathlib.Path(sys.argv[1]), sys.argv[2:])
