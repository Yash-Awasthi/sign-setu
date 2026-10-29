"""SignCapture.kt replayed on raw landmark frames (C 2.5), and its thresholds tuned on the stitched val sentences.

    python capture.py tune                 grid over still speed, pause and minimum sign length on stitch/val
    python capture.py score [--split test] insertion and deletion rate at the tuned (or the app's) thresholds

Same rules as the app: a hand moves when its points travel faster than still_speed frame-widths per second on
average; a sign closes after pause_ms without movement, needs min_ms and 3 moving frames, and a short drop of both
wrists to below the shoulders is the arms returning to rest, not a sign.
"""
import itertools
import json
import pathlib
import sys

import numpy as np

from models import L, P, PRES, R
from guard import DATA

STITCH = DATA / "stitch"
APP = {"still_speed": 0.3, "pause_ms": 400, "min_ms": 200}
FRAME_MS = 30
RETRACT_MAX_MS, RETRACT_DROP, RETRACT_LOW = 500, 0.6, 1.2


def hand(x, sl, k):
    return x[:, sl].reshape(len(x), 21, 2), x[:, PRES.start + k] > 0


def segments(x, aspect, still_speed, pause_ms, min_ms):
    """Frame spans [a, b] the capture hands to the reader."""
    t = np.arange(len(x)) * FRAME_MS
    moved = np.zeros(len(x), bool)
    for sl, k in ((L, 0), (R, 1)):
        pts, seen = hand(x, sl, k)
        step = np.r_[0, np.linalg.norm(np.diff(pts, axis=0), axis=2).mean(1)] / (FRAME_MS / 1000)
        appear = seen & ~np.r_[False, seen[:-1]]
        moved |= seen & ((step > still_speed) | appear)
    pose = x[:, P].reshape(len(x), 33, 2)
    out, start, last, active = [], -1, -1, 0

    def dropping(a, b):
        if t[b] - t[a] > RETRACT_MAX_MS or x[b, PRES.start + 2] <= 0 or x[a, PRES.start + 2] <= 0:
            return False
        e = pose[b]
        width = np.hypot(e[11, 0] - e[12, 0], (e[11, 1] - e[12, 1]) / aspect)
        if width < 1e-3:
            return False
        sy, seen = (e[11, 1] + e[12, 1]) / 2, 0
        for sl, k in ((L, 0), (R, 1)):
            if x[a, PRES.start + k] <= 0 or x[b, PRES.start + k] <= 0:
                continue
            seen += 1
            down = (x[b, sl.start + 1] - x[a, sl.start + 1]) / aspect
            below = (x[b, sl.start + 1] - sy) / aspect
            if down < RETRACT_DROP * width or below < RETRACT_LOW * width:
                return False
        return seen > 0

    def close():
        if t[last] - t[start] >= min_ms and active >= 3 and not dropping(start, last):
            out.append((start, last))

    for i in range(len(x)):
        if start >= 0 and t[i] - t[last] >= pause_ms:
            close()
            start, active = -1, 0
        if moved[i]:
            if start < 0:
                start = max(0, i - 1)
            last, active = i, active + 1
    if start >= 0:
        close()
    return out


def match(segs, spans):
    """One-to-one by overlap: unmatched segments are insertions, unmatched signs deletions."""
    used, hit = set(), 0
    for a, b in spans:
        best = max(((min(b, e) - max(a, s), j) for j, (s, e) in enumerate(segs) if j not in used), default=(0, -1))
        if best[0] > 0:
            used.add(best[1])
            hit += 1
    return len(segs) - len(used), len(spans) - hit


def load(split):
    return np.load(STITCH / f"{split}.npy", mmap_mode="r"), json.loads((STITCH / f"{split}.json").read_text(encoding="utf-8"))


def rates(split, knobs):
    raw, meta = load(split)
    ins = dele = signs = 0
    per = []
    for m in meta:
        x = np.asarray(raw[m["start"]:m["start"] + m["length"]])
        i, d = match(segments(x, m["aspect"], **knobs), m["spans"])
        ins, dele, signs = ins + i, dele + d, signs + len(m["spans"])
        per.append((m["intent"], i, d, len(m["spans"])))
    return ins / signs, dele / signs, per


def main(a):
    if a[0] == "tune":
        grid = [dict(zip(APP, v)) for v in itertools.product([0.2, 0.3, 0.4, 0.5], [250, 300, 400, 500, 700], [100, 200, 300])]
        res = sorted(((sum(rates("val", g)[:2]), g) for g in grid), key=lambda r: r[0])
        best = res[0][1]
        (STITCH / "capture_knobs.json").write_text(json.dumps(best), encoding="utf-8")
        app = rates("val", APP)
        print(json.dumps({"tuned": best, "val_ins_del": rates("val", best)[:2], "app_val_ins_del": app[:2]}), flush=True)
    else:
        split = a[a.index("--split") + 1] if "--split" in a else "test"
        knobs = json.loads((STITCH / "capture_knobs.json").read_text()) if (STITCH / "capture_knobs.json").exists() else APP
        ins, dele, per = rates(split, knobs)
        print(json.dumps({"split": split, "knobs": knobs, "insertion": ins, "deletion": dele, "sentences": len(per)}), flush=True)


if __name__ == "__main__":
    main(sys.argv[1:])
