"""B13 slices on new-signer val clips of the app's words, as the app shows them: as recorded, mirrored (a
left-handed signer), each hand dropped for the whole clip (a phone held in one hand), and the clips that are
naturally one-handed (one hand seen in under 10% of frames).

    python slices.py CACHE NAME:RUN:ROW:MODEL_CACHE [...]   RUN "app" reads the installed reader
"""
import json
import pathlib
import sys

import numpy as np
import pandas as pd
import torch

import exp
from cache import Clips
from models import F as FACE_COLS, L, P, PRES, R


def mirror(x):
    y = x.clone()
    pres = y[..., PRES]
    for sl, k in ((L, 0), (R, 1), (P, 2), (FACE_COLS, 3)):
        v = y[..., sl].reshape(*y.shape[:2], -1, 2)
        v = torch.stack([torch.where(pres[..., k:k + 1] > 0, 1 - v[..., 0], v[..., 0]), v[..., 1]], -1)
        y[..., sl] = v.reshape(*y.shape[:2], -1)
    left, right = y[..., L].clone(), y[..., R].clone()
    y[..., L], y[..., R] = right, left
    y[..., P] = y[..., P].reshape(*y.shape[:2], 33, 2)[:, :, exp.POSE_SWAP].reshape(*y.shape[:2], 66)
    y[..., PRES.start], y[..., PRES.start + 1] = y[..., PRES.start + 1].clone(), y[..., PRES.start].clone()
    return y


def drop(x, k):
    y = x.clone()
    y[..., (L, R)[k]] = 0
    y[..., PRES.start + k] = 0
    return y


def main(root, specs):
    index = pd.read_csv(root / "index.csv", keep_default_na=False)
    app = json.loads((exp.APP_MODELS / "sign_reader.json").read_text(encoding="utf-8"))["labels"]
    rows = np.flatnonzero(((index["split"] == "val") & index["source"].isin(["islrtc", "rkmvu"])
                           & (index["scored"].astype(str) == "True") & index["label"].isin(app)).to_numpy())
    ds = Clips(root, rows, train=False, t=32)
    x = torch.stack([ds[i][0] for i in range(len(ds))])
    a = torch.tensor([[ds[i][1]] for i in range(len(ds))]).float()
    y = index["label"].to_numpy()[rows]
    seen = x[..., PRES.start:PRES.start + 2].mean(1)
    natural = ((seen < 0.1) & (seen.flip(1) >= 0.1)).any(1).numpy()
    exps = json.loads((exp.HERE / "experiments.json").read_text())
    lines = []
    for spec in specs:
        name, run, row, mc = spec.split(":", 3)
        if run == "app":
            model, labels, _ = exp.app_model()
        else:
            labels = json.loads((pathlib.Path(mc) / "vocab.json").read_text(encoding="utf-8"))
            model = exp.make_model(exps[row], len(labels), labels)
            model.load_state_dict(torch.load(exp.RUNS / run / "best.pt", weights_only=True, map_location="cpu"))
        model.eval()
        names = np.array(labels)
        keep = torch.as_tensor(np.isin(names, app))

        def right(xx):
            with torch.no_grad():
                return names[model(xx, a).masked_fill(~keep, -1e9).argmax(1).numpy()] == y

        r = {"as recorded": right(x), "mirrored": right(mirror(x)), "left hand dropped": right(drop(x, 0)),
             "right hand dropped": right(drop(x, 1))}
        r["naturally one-handed"] = r["as recorded"][natural]
        lines.append(f"{name}: " + ", ".join(f"{k} {v.mean():.3f}" for k, v in r.items()))
        print(lines[-1], flush=True)
    with open(exp.EVAL, "a", encoding="utf-8") as f:
        f.write(f"\nB13 slices (new-signer val, app words as the app shows them, n={len(rows)}, naturally one-handed "
                f"n={int(natural.sum())}): " + "; ".join(lines) + "\n")
    print("JOB slices ok=1 failed=0 quarantined=0 status=done")


if __name__ == "__main__":
    main(pathlib.Path(sys.argv[1]), sys.argv[2:])
