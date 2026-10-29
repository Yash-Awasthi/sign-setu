"""int8 check for the installed format 1 reader: weights quantised per output channel (symmetric, 8 bit) and read
back, top-1 against the float16 file on 200 val windows. int8 ships only at 99% agreement or more.

    python int8_check.py CACHE
"""
import pathlib
import sys

import numpy as np
import pandas as pd
import torch

import exp
from cache import Clips


def main(root):
    model, labels, frames = exp.app_model()
    index = pd.read_csv(root / "index.csv", keep_default_na=False)
    rows = np.flatnonzero(((index["split"] == "val") & (index["source"] != "nonsign")).to_numpy())
    rows = np.random.default_rng(0).permutation(rows)[:200]
    ds = Clips(root, rows, train=False, t=frames)
    ref = exp.predict(model, ds, torch.device("cpu"))
    with torch.no_grad():
        for name, w in model.state_dict().items():
            if name.endswith("weight") and w.dim() > 1:
                scale = w.abs().flatten(1).max(1).values.clamp_min(1e-12) / 127
                shape = (-1,) + (1,) * (w.dim() - 1)
                w.copy_((w / scale.view(shape)).round().clamp(-127, 127) * scale.view(shape))
    q = exp.predict(model, ds, torch.device("cpu"))
    agree = float((q.argmax(1) == ref.argmax(1)).float().mean())
    diff = float((q - ref).abs().max())
    verdict = "int8 allowed" if agree >= 0.99 else "float16 stays"
    line = (f"int8 check (installed reader, per-channel symmetric weights, {len(rows)} val windows of {root.name}): "
            f"top-1 agreement {agree:.3f}, max logit difference {diff:.4f}; {verdict}")
    print(line)
    with open(exp.EVAL, "a", encoding="utf-8") as f:
        f.write(f"\n{line}\n")
    print(f"JOB int8_check ok=1 failed=0 quarantined=0 status=done agree={agree:.3f}")


if __name__ == "__main__":
    main(pathlib.Path(sys.argv[1]))
