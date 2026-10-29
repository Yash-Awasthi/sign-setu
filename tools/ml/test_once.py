"""The single test read-out: the shipped reader (WF seed 1), W and the app reader from before W, on the same test
clips (cache_v5 test split, app words only, as the app shows them). Run once; test is closed to choices after this.

    python test_once.py
"""
import json
import os
import pathlib

import numpy as np
import pandas as pd
import torch

import calibrate
import exp
from cache import Clips
from guard import DATA

ROOT = DATA / "cache_v5"


def raps_cover(prob, y, spec):
    th = spec.get("conformal", {}).get("thresholds", {})
    return {lvl: float(np.mean([int(t) in s for t, s in zip(y, calibrate.raps_sets(torch.as_tensor(prob), q))]))
            for lvl, q in th.items()}


def main():
    index = pd.read_csv(ROOT / "index.csv", keep_default_na=False)
    app_spec = json.loads((DATA / "staging" / "APP_head" / "sign_reader.json").read_text(encoding="utf-8"))
    app = app_spec["labels"]
    rows = np.flatnonzero((index["split"] == "test").to_numpy() & (index["scored"].astype(str) == "True").to_numpy()
                          & index["label"].isin(app).to_numpy())
    y_word = index["label"].to_numpy()[rows]
    y = np.array([app.index(w) for w in y_word])
    sig = index["signer"].to_numpy()[rows]
    src = index["source"].to_numpy()[rows]
    cpu = torch.device("cpu")
    out = {}

    def restricted(model, labels, frames):
        lg = exp.predict(model, Clips(ROOT, rows, train=False, t=frames), cpu).double()
        cols = [labels.index(w) for w in app]
        return lg[:, cols]  # app-word logits in the app's label order

    cfg_w = json.loads((exp.HERE / "experiments.json").read_text())["W"]
    import sys
    # --soup: the report-only read-out for the 5-seed soup chosen without test (test stays closed to choices).
    readers = ((("WF soup of 5 seeds (shipped)", "WF_s99", ROOT, DATA / "staging" / "WF_soup5"),) if "--soup" in sys.argv
               else (("WF seed 1 (shipped)", "WF_s1", ROOT, DATA / "staging" / "WF"),
                     ("W", "W_s1", DATA / "cache_v4b", DATA / "staging" / "W")))
    for name, run, vocab_cache, spec_dir in readers:
        labels = json.loads((vocab_cache / "vocab.json").read_text(encoding="utf-8"))
        m = exp.make_model(cfg_w, len(labels), labels)
        m.load_state_dict(torch.load(exp.RUNS / run / "best.pt", weights_only=True, map_location=cpu))
        spec = json.loads((spec_dir / "sign_reader.json").read_text(encoding="utf-8"))
        out[name] = (restricted(m, labels, cfg_w["frames"]), spec)
    os.environ["APP_READER_DIR"] = str(DATA / "staging" / "APP_head")
    a, labels, frames = exp.app_model()
    out["app reader before W"] = (restricted(a, labels, frames), app_spec)

    ref = None
    lines = [f"\n### Test, read once (2026-10-29): cache_v5 test split, app words only, n={len(rows)} "
             f"({', '.join(f'{s} {int((sig == s).sum())}' for s in np.unique(sig))})\n",
             "| reader | top-1 [95%] | per signer | ECE at its T | RAPS coverage (50%, 80%) | McNemar vs shipped |",
             "|---|---|---|---|---|---|"]
    for name, (lg, spec) in out.items():
        t = float(spec.get("temperature", 1.0))
        prob = (lg / t).softmax(1).numpy()
        right = prob.argmax(1) == y
        lo, hi = exp.bootstrap(right, sig)
        per = {s: round(float(right[sig == s].mean()), 3) for s in np.unique(sig)}
        ece = exp.ece(prob.max(1), right)
        cov = raps_cover(prob, y, spec)
        if ref is None:
            ref, mc = right, "-"
        else:
            mc = f"{int((ref & ~right).sum())} to {int((right & ~ref).sum())}, p={exp.mcnemar(ref, right):.3g}"
        lines.append(f"| {name} | {right.mean():.3f} [{lo:.3f}, {hi:.3f}] | {per} | {ece:.3f} (T {t:.2f}) | "
                     f"{', '.join(f'{v:.3f}' for v in cov.values())} | {mc} |")
    lines.append("\nTest is closed for choices from here on.\n")
    text = "\n".join(lines)
    print(text, flush=True)
    with open(exp.EVAL, "a", encoding="utf-8") as f:
        f.write(text + "\n")


if __name__ == "__main__":
    main()
