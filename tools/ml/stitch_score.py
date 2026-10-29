"""Stitched sentence proxy, scored once with the shipped reader: SignCapture thresholds (tuned on the val stitches)
cut each sentence, the reader names each cut restricted to the app's words, and the gloss sequence is compared with
the truth. Optimistic: no coarticulation. Confidence intervals resample whole intents.

    python stitch_score.py [--run WF_s1] [--split test]
"""
import json
import pathlib
import sys

import numpy as np
import torch

import capture
import exp
from features import resample_index
from guard import DATA



def wer(ref, hyp):
    d = list(range(len(hyp) + 1))
    for i, r in enumerate(ref, 1):
        prev, d[0] = d[0], i
        for j, h in enumerate(hyp, 1):
            prev, d[j] = d[j], min(d[j] + 1, d[j - 1] + 1, prev + (r != h))
    return d[len(hyp)]


def main(a):
    run = a[a.index("--run") + 1] if "--run" in a else "WF_s1"
    split = a[a.index("--split") + 1] if "--split" in a else "test"
    cfg = json.loads((exp.HERE / "experiments.json").read_text())["W"]
    labels = json.loads((DATA / "cache_v5" / "vocab.json").read_text(encoding="utf-8"))
    app = json.loads((DATA / "staging" / "APP_head" / "sign_reader.json").read_text(encoding="utf-8"))["labels"]
    m = exp.make_model(cfg, len(labels), labels)
    m.load_state_dict(torch.load(exp.RUNS / run / "best.pt", weights_only=True, map_location="cpu"))
    m.eval()
    cols = [labels.index(w) for w in app]
    knobs = json.loads((capture.STITCH / "capture_knobs.json").read_text())
    raw, meta = capture.load(split)
    per = []
    for s in meta:
        x = np.asarray(raw[s["start"]:s["start"] + s["length"]])
        segs = capture.segments(x, s["aspect"], **knobs)
        ins, dele = capture.match(segs, s["spans"])
        hyp = []
        for a0, b0 in segs:
            clip = x[a0:b0 + 1]
            clip = clip[resample_index(len(clip), cfg["frames"])]
            with torch.no_grad():
                lg = m(torch.from_numpy(clip[None]).float(), torch.tensor([[s["aspect"]]]).float())[0, cols]
            hyp.append(app[int(lg.argmax())])
        per.append((s["intent"], wer(s["glosses"], hyp), len(s["glosses"]), hyp == s["glosses"], ins, dele))
    intents = sorted({p[0] for p in per})
    by = {i: [p for p in per if p[0] == i] for i in intents}

    def stats(group):
        rows = [p for i in group for p in by[i]]
        n = sum(p[2] for p in rows)
        return sum(p[1] for p in rows) / n, np.mean([p[3] for p in rows]), sum(p[4] for p in rows) / n, sum(p[5] for p in rows) / n
    point = stats(intents)
    rng = np.random.default_rng(7)
    boot = np.array([stats(list(rng.choice(intents, len(intents)))) for _ in range(1000)])
    ci = np.percentile(boot, [2.5, 97.5], axis=0)
    names = ("gloss WER", "sentence exact", "segment insertion", "segment deletion")
    line = (f"\nStitched sentence proxy ({split}, {run}, optimistic: no coarticulation), {len(intents)} intents x "
            f"{len(per) // max(len(intents), 1)} samplings, {len(set(s['signer'] for s in meta))} signer; CI over intents: "
            + "; ".join(f"{n} {v:.3f} [{lo:.3f}, {hi:.3f}]" for n, v, lo, hi in zip(names, point, ci[0], ci[1]))
            + ". Indicative only; no claim for the gloss-to-text gate.\n")
    print(line, flush=True)
    with open(exp.EVAL, "a", encoding="utf-8") as f:
        f.write(line)


if __name__ == "__main__":
    main(sys.argv[1:])
