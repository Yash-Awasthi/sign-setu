"""Classifier plus prototypes: the W-row reader answers with its app words; stage E's nearest prototype answers instead
when it names a word outside the app's head, its cosine clears tau_e and the reader's top probability is under tau_c.
Both thresholds are fitted two-fold on validation (split by word) and scored on the other fold.

    python fuse.py [--row W_s1] [--embed E_W_s1] [--cache C:/work/ml-data/cache_v4b]
"""
import json
import pathlib
import sys

import numpy as np
import pandas as pd
import torch

import exp
from cache import Clips
from embed import Embedder, embed_all
from guard import DATA


def main(a):
    opt = lambda k, d: a[a.index(k) + 1] if k in a else d  # noqa: E731
    root = pathlib.Path(opt("--cache", str(DATA / "cache_v4b")))
    row, emb = opt("--row", "W_s1"), opt("--embed", "E_W_s1")
    exps = json.loads((exp.HERE / "experiments.json").read_text())
    cfg = exps[row.split("_s")[0]]
    vocab = json.loads((root / "vocab.json").read_text(encoding="utf-8"))
    app_words = json.loads((exp.APP_MODELS / "sign_reader.json").read_text(encoding="utf-8"))["labels"]
    index = pd.read_csv(root / "index.csv", keep_default_na=False)
    dev = torch.device("cuda")
    torch.cuda.set_per_process_memory_fraction(0.3)
    frames = cfg.get("frames", 48)

    val = np.flatnonzero((index["split"] == "val").to_numpy() & (index["source"] != "nonsign").to_numpy()
                         & (index["scored"].astype(str) == "True").to_numpy() & index["label"].isin(vocab).to_numpy())
    y = index["label"].to_numpy()[val]
    clf = exp.make_model(cfg, len(vocab), vocab).to(dev)
    clf.load_state_dict(torch.load(exp.RUNS / row / "best.pt", weights_only=True))
    lg = exp.app_only(exp.predict(clf, Clips(root, val, train=False, t=frames), dev), vocab, set(app_words))
    p = lg.softmax(1).numpy()
    c_word, c_prob = np.array(vocab)[p.argmax(1)], p.max(1)

    trunk = exp.make_model({**cfg, "init": None}, len(vocab), vocab)
    model = Embedder(trunk).to(dev)
    model(torch.zeros(2, frames, 218, device=dev), torch.ones(2, 1, device=dev))
    model.load_state_dict(torch.load(exp.RUNS / emb / "embed.pt", weights_only=True))
    pr = np.load(exp.RUNS / emb / "prototypes.npz")
    q = embed_all(model, Clips(root, val, train=False, t=frames), dev)
    sim = q @ pr["vectors"].T
    e_word, e_sim = pr["words"][sim.argmax(1)], sim.max(1)
    outside = ~np.isin(e_word, app_words)

    rng = np.random.default_rng(7)
    words = np.unique(y)
    half = set(rng.permutation(words)[: len(words) // 2])
    fold = np.isin(y, list(half))
    grid = [(tc, te) for tc in np.linspace(0.1, 1.01, 20) for te in np.linspace(0.2, 0.9, 15)]

    def fused(m, tc, te):
        use_e = outside[m] & (e_sim[m] >= te) & (c_prob[m] < tc)
        return np.where(use_e, e_word[m], c_word[m]) == y[m]
    right = np.zeros(len(y), bool)
    picked = []
    for f in (fold, ~fold):
        tc, te = max(grid, key=lambda g: fused(f, *g).mean())
        right[~f] = fused(~f, tc, te)
        picked.append((round(float(tc), 3), round(float(te), 3)))
    once = index.loc[index["split"] == "train", "label"].value_counts()
    one = np.isin(y, once.index[once == 1])
    src = index["source"].to_numpy()[val]
    res = {"n": int(len(y)), "fused": float(right.mean()), "classifier": float((c_word == y).mean()),
           "prototypes": float((e_word == y).mean()), "thresholds_per_fold": picked,
           "one_clip_n": int(one.sum()), "one_clip_fused": float(right[one].mean()),
           "one_clip_classifier": float((c_word == y)[one].mean()),
           "per_source_fused": {s: float(right[src == s].mean()) for s in np.unique(src)}}
    res["agree_classifier_prototypes"] = float((c_word == e_word).mean())
    res["prototype_whenever_outside"] = float(np.where(outside, e_word, c_word).__eq__(y).mean())
    res["prototype_whenever_outside_one_clip"] = float(np.where(outside, e_word, c_word).__eq__(y)[one].mean())
    # Harm check: clips whose true word is an app word, fused (untuned rule) against the classifier alone.
    app = np.isin(y, app_words)
    simple = np.where(outside, e_word, c_word) == y
    res["app_word_clips"] = int(app.sum())
    res["app_word_classifier_right"] = int((c_word == y)[app].sum())
    res["app_word_fused_right"] = int(simple[app].sum())
    print(json.dumps(res, indent=1), flush=True)
    with open(exp.EVAL, "a", encoding="utf-8") as f:
        f.write(f"\nFusion ({row} restricted to app words + {emb} prototypes for other words, thresholds two-fold by word on "
                f"val): all scored val n={res['n']} fused {res['fused']:.3f}, classifier alone {res['classifier']:.3f}, "
                f"prototypes alone {res['prototypes']:.3f}; one-clip words n={res['one_clip_n']} fused "
                f"{res['one_clip_fused']:.3f} vs classifier {res['one_clip_classifier']:.3f}; per source "
                f"{ {k: round(v, 3) for k, v in res['per_source_fused'].items()} }; thresholds {picked}\n")


if __name__ == "__main__":
    main(sys.argv[1:])
