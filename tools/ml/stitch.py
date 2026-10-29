"""Stitched sentence proxy (C 0.3, 0.4, 6.1 to 6.4 and 2.5 until real sentences exist): phrasebook intents "signed" by
concatenating one held-out signer's real word clips in gloss order, with a real rest window of 0.3 to 1.0 s between
signs and a 3-frame blend at each join. Optimistic: no coarticulation.

    python stitch.py [--cache C:/work/ml-data/cache_v4] [--samples 5]

Writes C:\\work\\ml-data\\stitch\\<split>.npz (raw frames, one array) and <split>.json (per sentence: intent, signer,
glosses, frame spans, row offsets). Val signers tune the capture thresholds; test signers are scored once.
"""
import json
import pathlib
import sys

import numpy as np
import pandas as pd
from guard import DATA

APP = pathlib.Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "assets" / "content"
OUT = DATA / "stitch"
FRAME_MS = 30
NONSIGN = "__nonsign__"


def blend(a, b, n=3):
    """n frames easing from a's last frame to b's first, so wrists never jump."""
    w = np.linspace(0, 1, n + 2)[1:-1, None]
    return (a[-1][None] * (1 - w) + b[0][None] * w).astype(np.float32)


def main(a):
    root = pathlib.Path(a[a.index("--cache") + 1] if "--cache" in a else str(DATA / "cache_v4"))
    samples = int(a[a.index("--samples") + 1]) if "--samples" in a else 5
    index = pd.read_csv(root / "index.csv", keep_default_na=False)
    raw = np.load(root / "raw.npy", mmap_mode="r")
    book = json.loads((APP / "intents.json").read_text(encoding="utf-8"))
    slots = {k: list(v) for k, v in book["slots"].items()}
    rng = np.random.default_rng(7)
    OUT.mkdir(parents=True, exist_ok=True)
    report = {}
    for split in ("val", "test"):
        held = index[(index["split"] == split) & ~index["source"].isin(["kaggle", "include"])]
        frames, meta, at = [], [], 0
        for signer, g in held.groupby("signer"):
            words = g[g["label"] != NONSIGN].groupby("label").groups
            rests = g[g["label"] == NONSIGN].index.to_numpy()
            if not len(rests):
                continue
            for intent in book["intents"]:
                for k in range(samples):
                    glosses = []
                    for item in intent["pattern"]:
                        name = item.rstrip("?")
                        if name.startswith("@") or name in ("$NAME", "$NUM") or (item.endswith("?") and rng.random() < 0.5):
                            continue
                        options = slots.get(name[1:], []) if name.startswith("$") else name.split("|")
                        options = [o for o in options if o in words]
                        if not options:
                            glosses = None
                            break
                        glosses.append(str(rng.choice(options)))
                    if not glosses:
                        continue
                    parts, spans, t = [], [], 0
                    for i, w in enumerate(glosses):
                        r = int(rng.choice(words[w]))
                        clip = np.asarray(raw[index.at[r, "start"]:index.at[r, "start"] + index.at[r, "length"]])
                        if i:
                            rr = int(rng.choice(rests))
                            rest = np.asarray(raw[index.at[rr, "start"]:index.at[rr, "start"] + index.at[rr, "length"]])
                            n = max(1, min(len(rest), int(rng.uniform(300, 1000) / FRAME_MS)))
                            off = int(rng.integers(0, len(rest) - n + 1))
                            rest = rest[off:off + n]
                            parts += [blend(parts[-1], rest), rest, blend(rest, clip)]
                            t += 6 + len(rest)
                        spans.append([t, t + len(clip)])
                        parts.append(clip)
                        t += len(clip)
                    x = np.concatenate(parts)
                    frames.append(x)
                    meta.append({"intent": intent.get("id"), "sample": k, "signer": signer, "glosses": glosses,
                                 "spans": spans, "start": at, "length": len(x), "aspect": float(index.at[r, "aspect"])})
                    at += len(x)
        if frames:
            np.save(OUT / f"{split}.npy", np.concatenate(frames))
        (OUT / f"{split}.json").write_text(json.dumps(meta, ensure_ascii=False), encoding="utf-8")
        report[split] = {"sentences": len(meta), "intents": len({m["intent"] for m in meta}),
                         "signers": sorted({m["signer"] for m in meta})}
    print(json.dumps(report), flush=True)


if __name__ == "__main__":
    main(sys.argv[1:])
