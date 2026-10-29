"""Non-sign false-accept rate on val: the share of rest and card-pause windows the app would show as a word, i.e. its
top label over the app's words (plus the non-sign class when the reader has one) is a word at READ_MIN_SCORE or above
after the reader's temperature. New-signer app-word clips shown at that floor are reported beside it.

    python nonsign_fa.py CACHE NAME:RUN:ROW:T [...]   RUN "app" reads APP_READER_DIR (or the installed reader)
"""
import json
import pathlib
import sys

import numpy as np
import pandas as pd
import torch

import exp
from cache import Clips

FLOOR = 0.4  # READ_MIN_SCORE in SignChoices.kt


def main(root, specs):
    index = pd.read_csv(root / "index.csv", keep_default_na=False)
    rows = np.flatnonzero(((index["split"] == "val") & (index["source"] == "nonsign")).to_numpy())
    vocab = json.loads((root / "vocab.json").read_text(encoding="utf-8"))
    app_words = set(json.loads((exp.APP_MODELS / "sign_reader.json").read_text(encoding="utf-8"))["labels"])
    signs = np.flatnonzero(((index["split"] == "val") & index["source"].isin(["islrtc", "rkmvu"])
                            & (index["scored"].astype(str) == "True") & index["label"].isin(app_words)).to_numpy())
    dev = torch.device("cpu")  # small sets; the GPU stays with training rows
    exps = json.loads((exp.HERE / "experiments.json").read_text())
    out = []
    for spec in specs:
        name, run, row, t = spec.split(":")
        if run == "app":
            model, labels, frames = exp.app_model()
        else:
            cfg = exps[row]
            labels, frames = vocab, cfg.get("frames", 48)
            model = exp.make_model(cfg, len(labels), labels)
            model.load_state_dict(torch.load(exp.RUNS / run / "best.pt", weights_only=True, map_location="cpu"))
        names = np.array(labels)
        keep = torch.as_tensor(np.isin(names, list(app_words | {exp.NONSIGN})))

        def shown(r):
            lg = exp.predict(model.to(dev), Clips(root, r, train=False, t=frames), dev)
            conf, top = (lg.masked_fill(~keep, -1e9) / float(t)).softmax(1).max(1)
            pred = names[top.numpy()]
            return pred, (pred != exp.NONSIGN) & (conf.numpy() >= FLOOR)

        pred, fa = shown(rows)
        sig = index["signer"].to_numpy()[rows]
        lo, hi = exp.bootstrap(fa, sig)
        # The other side of the floor: new-signer app-word clips shown at all, and shown right.
        y = index["label"].to_numpy()[signs]
        spred, sshow = shown(signs)
        out.append(f"{name} {fa.mean():.3f} [{lo:.3f}, {hi:.3f}] (top label a word {(pred != exp.NONSIGN).mean():.3f}; "
                   f"new-signer app-word clips shown {sshow.mean():.3f}, shown right {(sshow & (spred == y)).mean():.3f}, "
                   f"n={len(signs)})")
        print(f"FA {out[-1]} n={len(rows)}", flush=True)
    with open(exp.EVAL, "a", encoding="utf-8") as f:
        f.write(f"\nNon-sign false-accept on val ({root.name}, n={len(rows)} rest and card-pause windows, a word shown at "
                f"p >= {FLOOR} after T, app words): " + "; ".join(out) + "\n")
    print("JOB nonsign_fa ok=1 failed=0 quarantined=0 status=done")


if __name__ == "__main__":
    main(pathlib.Path(sys.argv[1]), sys.argv[2:])
