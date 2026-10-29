"""Improvement-queue items that need no training (plan section 0): I1 test-time augmentation, I2 probability
ensembles, I3 fusion v2. Each is scored in app view (restricted to the app's words when the true word is one, 868-way
otherwise) against the champion with paired McNemar on new-signer clips, plus the Kaggle validation signer.

    python improve.py I1|I2|I3 [--champion W_s1] [--cache C:/work/ml-data/cache_v4b]
"""
import json
import math
import pathlib
import sys

import numpy as np
import pandas as pd
import torch

import exp
from cache import Clips
from guard import DATA

ROOT = DATA / "cache_v4b"


def load_row(run, vocab, dev):
    cfg = json.loads((exp.HERE / "experiments.json").read_text())[run.split("_s")[0]]
    m = exp.make_model({**cfg, "init": None}, len(vocab), vocab).to(dev)
    m.load_state_dict(torch.load(exp.RUNS / run / "best.pt", weights_only=True))
    m.eval()
    return m, cfg.get("frames", 48)


def jitter(x, aspect, scale, angle):
    """Every part's points scaled and rotated about the frame centre (aspect-corrected), presence untouched."""
    x = x.clone()
    b, t, _ = x.shape
    ky = torch.cat([torch.ones_like(aspect), 1 / aspect], 1).view(b, 1, 1, 2)
    rot = torch.tensor([[math.cos(angle), -math.sin(angle)], [math.sin(angle), math.cos(angle)]], device=x.device)
    for sl in (exp.L, exp.R, exp.P, exp.FACE_COLS):
        v = x[..., sl].reshape(b, t, -1, 2) * ky
        c = torch.tensor([0.5, 0.5], device=x.device) * ky
        v = ((v - c) * scale) @ rot.T + c
        x[..., sl] = (v / ky).reshape(b, t, -1)
    return x


def logits(model, rows, frames, dev, tta=False):
    ds = Clips(ROOT, rows, train=False, t=frames)
    out = []
    torch.backends.cudnn.allow_tf32 = torch.backends.cuda.matmul.allow_tf32 = False
    with torch.no_grad():
        for x, aspect, _ in torch.utils.data.DataLoader(ds, batch_size=64):
            x, aspect = x.to(dev), aspect[:, None].to(dev)
            if tta:
                views = [(1.0, 0.0), (0.95, 0.0), (1.05, 0.0), (1.0, -0.05), (1.0, 0.05)]
                out.append(torch.stack([model(jitter(x, aspect, s, a), aspect) for s, a in views]).mean(0).cpu())
            else:
                out.append(model(x, aspect).cpu())
    return torch.cat(out).double()


def app_view(prob, vocab, app, y):
    """Right or wrong as the app would show it: the app's words only when the true word is one."""
    names = np.array(vocab)
    keep = np.isin(names, list(app))
    restricted = np.where(keep[None], prob, -1.0)
    return np.where(np.isin(y, list(app)), names[restricted.argmax(1)] == y, names[prob.argmax(1)] == y)


def report(name, right, ref, new, kag, sig, champion):
    lo, hi = exp.bootstrap(ref[kag], sig[kag])
    p = exp.mcnemar(right[new], ref[new])
    k = float(right[kag].mean())
    verdict = "pass" if p < 0.01 and right[new].sum() > ref[new].sum() and k >= lo else "no"
    line = (f"{name}: app-view new-signer {right[new].mean():.3f} vs {champion} {ref[new].mean():.3f} "
            f"({int((right & ~ref)[new].sum())} to {int((ref & ~right)[new].sum())}, p={p:.3g}), Kaggle C {k:.3f} "
            f"(champion {ref[kag].mean():.3f} [{lo:.3f}, {hi:.3f}]); {verdict}")
    print(line, flush=True)
    with open(exp.EVAL, "a", encoding="utf-8") as f:
        f.write(f"\n{line}\n")


def main(a):
    item = a[0]
    champion = a[a.index("--champion") + 1] if "--champion" in a else "W_s1"
    dev = torch.device("cuda")
    torch.cuda.set_per_process_memory_fraction(0.3)
    index = pd.read_csv(ROOT / "index.csv", keep_default_na=False)
    vocab = json.loads((ROOT / "vocab.json").read_text(encoding="utf-8"))
    app = set(json.loads((exp.APP_MODELS / "sign_reader.json").read_text(encoding="utf-8"))["labels"])
    val = np.flatnonzero((index["split"] == "val").to_numpy() & (index["scored"].astype(str) == "True").to_numpy()
                         & index["label"].isin(vocab).to_numpy())
    y, src, sig = (index[c].to_numpy()[val] for c in ("label", "source", "signer"))
    new, kag = ~np.isin(src, ["kaggle", "include", "nonsign"]), src == "kaggle"
    w, frames = load_row(champion, vocab, dev)
    pw = logits(w, val, frames, dev).softmax(1).numpy()
    ref = app_view(pw, vocab, app, y)
    if item == "I1":
        pt = logits(w, val, frames, dev, tta=True).softmax(1).numpy()
        report("I1 TTA (5 views: scale 0.95/1.05, rotation -/+0.05 rad)", app_view(pt, vocab, app, y), ref, new, kag, sig,
               champion)
    elif item == "I2":
        for other in ("B0F_s1", "B4_s1"):
            m, fr = load_row(other, vocab, dev)
            po = logits(m, val, fr, dev).softmax(1).numpy()
            report(f"I2 ensemble {champion} + {other}", app_view((pw + po) / 2, vocab, app, y), ref, new, kag, sig, champion)
    elif item == "I3":
        from embed import Embedder, embed_all
        cfg = json.loads((exp.HERE / "experiments.json").read_text())["W"]
        model = Embedder(exp.make_model({**cfg, "init": None}, len(vocab), vocab)).to(dev)
        model(torch.zeros(2, frames, 218, device=dev), torch.ones(2, 1, device=dev))
        model.load_state_dict(torch.load(exp.RUNS / "E_W_s1" / "embed.pt", weights_only=True))
        pr = np.load(exp.RUNS / "E_W_s1" / "prototypes.npz")
        q = embed_all(model, Clips(ROOT, val, train=False, t=frames), dev)
        e_word = pr["words"][(q @ pr["vectors"].T).argmax(1)]
        names = np.array(vocab)
        keep = np.isin(names, list(app))
        pr_app = np.where(keep[None], pw, 0)
        pr_app = pr_app / pr_app.sum(1, keepdims=True)
        # Fixed in advance, not tuned: prototypes only when W's top probability over the app's words is under 0.5.
        unsure = pr_app.max(1) < 0.5
        c_word = names[pr_app.argmax(1)]
        fused = np.where(unsure & ~np.isin(e_word, list(app)), e_word, c_word) == y
        is_app = np.isin(y, list(app))
        lost = int((ref & ~fused)[is_app].sum())
        report(f"I3 fusion v2 (prototype when W's app-word top probability < 0.5; app-word clips lost {lost})",
               fused, ref, new, kag, sig, champion)


if __name__ == "__main__":
    main(sys.argv[1:])
