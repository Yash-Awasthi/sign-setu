"""Stages row W as the app's format 1 reader: its trunk plus the head rows of the app's 314 words, exported by
export.py (float16 weights and the Kotlin parity fixture) into C:/work/ml-data/staging/W and calibrated on new-signer
validation clips (calibrate.py's old ISLRTC set may be in W's training). Nothing reaches the app assets here;
--install copies the staged files in once the gate has passed.

    python ship_w.py [--run W_s1] [--cache C:/work/ml-data/cache_v4b]
    python ship_w.py --install

Temperature and RAPS are fitted two-fold over the pooled new-signer validation clips (split by word). The fitted
temperature is kept only if it lowers ECE on both folds; otherwise T stays 1. The gate needs the float16 file to match
the float32 trunk on top-1 and each RAPS level to cover at least its target minus 0.03 on both folds.
"""
import json
import os
import pathlib
import random
import shutil
import sys

os.environ["SIGN_HIDDEN"] = "320"  # the app reader's width; export.py builds SignNet from it

import numpy as np  # noqa: E402
import pandas as pd  # noqa: E402
import torch  # noqa: E402

import calibrate  # noqa: E402
import exp  # noqa: E402
import export  # noqa: E402
import runtime  # noqa: E402
from cache import Clips  # noqa: E402
from guard import DATA  # noqa: E402

STAGE = DATA / "staging" / "W"
FILES = {"sign_reader.bin": export.MODELS, "sign_reader.json": export.MODELS, "sign_reader_fixture.json": export.RESOURCES,
         "sign_confusion.json": export.MODELS}


def install():
    global STAGE
    STAGE = DATA / "staging" / (sys.argv[sys.argv.index("--stage") + 1] if "--stage" in sys.argv else "W")
    gate = json.loads((STAGE / "gate.json").read_text())
    if not gate["pass"]:
        sys.exit(f"STOP: staged gate failed {gate}")
    for name, dest in FILES.items():
        shutil.copyfile(STAGE / name, dest / name)
        print("installed", dest / name)


def main(a):
    if "--install" in a:
        return install()
    global STAGE
    STAGE = DATA / "staging" / (a[a.index("--stage") + 1] if "--stage" in a else "W")
    run = exp.RUNS / (a[a.index("--run") + 1] if "--run" in a else "W_s1")
    root = pathlib.Path(a[a.index("--cache") + 1] if "--cache" in a else DATA / "cache_v4b")
    cfg = json.loads((exp.HERE / "experiments.json").read_text())[a[a.index("--row") + 1] if "--row" in a else "W"]
    vocab = json.loads((root / "vocab.json").read_text(encoding="utf-8"))
    spec_old = json.loads((exp.APP_MODELS / "sign_reader.json").read_text(encoding="utf-8"))
    app_labels = spec_old["labels"]
    model = exp.make_model(cfg, len(vocab), vocab)
    model.load_state_dict(torch.load(run / "best.pt", weights_only=True, map_location="cpu"))  # nosemgrep: trailofbits.python.pickles-in-pytorch.pickles-in-pytorch
    rows = [vocab.index(w) for w in app_labels]
    state = {k: v for k, v in model.state_dict().items() if not k.startswith("head.")}
    state["head.weight"], state["head.bias"] = model.head.weight[rows].clone(), model.head.bias[rows].clone()
    small = exp.Shipped(len(app_labels), hidden=320)
    small.load_state_dict(state)
    small.eval()

    index = pd.read_csv(root / "index.csv", keep_default_na=False)
    # --full-val: calibrate on every val signer (Kaggle C included), not only the new ones.
    skip = ["nonsign"] if "--full-val" in a else ["nonsign", "kaggle"]
    val = index[(index["split"] == "val") & index["label"].isin(app_labels) & ~index["source"].isin(skip)]
    logits = exp.predict(small, Clips(root, val.index.to_numpy(), train=False, t=cfg["frames"]), torch.device("cpu")).double()
    y = torch.tensor([app_labels.index(w) for w in val["label"]])
    random.seed(7)
    torch.manual_seed(7)
    words = sorted(set(val["label"]))
    random.shuffle(words)
    half = set(words[: len(words) // 2])
    in_half = torch.as_tensor(val["label"].isin(half).to_numpy())
    folds = [(in_half, ~in_half), (~in_half, in_half)]
    temps = [calibrate.fit_temperature(logits[f], y[f]) for f, _ in folds]
    better = all(calibrate.ece((logits[u] / t).softmax(1), y[u]) < calibrate.ece(logits[u].softmax(1), y[u])
                 for (_, u), t in zip(folds, temps))
    t = float(np.mean(temps)) if better else 1.0
    cover, qhats = [], {}
    for c in calibrate.COVERAGES:
        qs = []
        for f, u in folds:
            sc = calibrate.raps_scores((logits[f] / t).softmax(1), y[f])
            n = int(f.sum())
            q = float(torch.quantile(sc, min(1.0, np.ceil((n + 1) * c) / n)))
            sets = calibrate.raps_sets((logits[u] / t).softmax(1), q)
            cover.append((c, float(np.mean([int(yy) in s for yy, s in zip(y[u], sets)]))))
            qs.append(q)
        qhats[c] = float(np.mean(qs))
    print(f"temperature folds {temps}, kept {t:.3f}; RAPS coverage per fold {cover}", flush=True)

    STAGE.mkdir(parents=True, exist_ok=True)
    export.MODELS = export.RESOURCES = runtime.MODELS = STAGE
    ckpt = run / "sign_reader_w.pt"
    torch.save({"state": state, "labels": app_labels, "top1": float((logits.argmax(1) == y).double().mean()),  # nosemgrep: trailofbits.python.pickles-in-pytorch.pickles-in-pytorch
                "top3": float((logits.topk(3, 1).indices == y[:, None]).any(1).double().mean()),
                "epochs": "B5 co-training, soup of 5 seeds" if "asl" in cfg.get("sources", ()) else "W fine-tune"}, ckpt)
    export.main("sign_reader", ckpt, DATA / "include_pose" / "include.pkl")
    spec = json.loads((STAGE / "sign_reader.json").read_text(encoding="utf-8"))
    spec["dataset"] = (export.DATASETS["sign_reader"] + "; fine-tuned with ISLRTC dictionary clips (islrtc.nic.in) and "
                       "RKMVU-CBE ISL channel clips, landmarks by MediaPipe")
    if "asl" in cfg.get("sources", ()):
        spec["dataset"] += "; co-trained with ASL Citizen (Microsoft Research) landmarks through a separate ASL head that is not shipped"
    if "spell_odds" in spec_old:
        spec["spell_odds"] = spec_old["spell_odds"]
    spec["temperature"] = round(t, 4)
    spec["conformal"] = {"method": "RAPS", "k_reg": calibrate.K_REG, "lambda": calibrate.LAMBDA,
                         "thresholds": {f"{c:.2f}": round(q, 4) for c, q in qhats.items()},
                         "fit": f"validation clips ({'all signers' if '--full-val' in a else 'new signers, ISLRTC and RKMVU'}), two-fold by word, n={len(y)}"}
    (STAGE / "sign_reader.json").write_text(json.dumps(spec, indent=1) + "\n", encoding="utf-8", newline="\n")

    # The confusion table the tagger's noise and the read-back use: every validation reading (new signers and the
    # Kaggle signer), true word to first guess, at the fitted temperature.
    allv = index[(index["split"] == "val") & index["label"].isin(app_labels) & (index["source"] != "nonsign")]
    first = exp.predict(small, Clips(root, allv.index.to_numpy(), train=False, t=cfg["frames"]), torch.device("cpu")).argmax(1)
    table = {}
    for w, k in zip(allv["label"], first.tolist()):
        row = table.setdefault(w, {})
        row[app_labels[k]] = row.get(app_labels[k], 0) + 1
    (STAGE / "sign_confusion.json").write_text(json.dumps({
        "note": "Validation readings of the shipped sign_reader (new signers and the held-out Kaggle signer): true word to "
                "first guess, counts. Written by tools/ml/ship_w.py; edit the script, not this file.",
        "temperature": round(t, 4), "counts": table}, ensure_ascii=False, sort_keys=True), encoding="utf-8")

    # Gate: the staged float16 file against the float32 trunk on the same clips, and fold coverage.
    _, f16 = runtime.reader("sign_reader")
    sample = val.index.to_numpy()[:40]
    ds = Clips(root, sample, train=False, t=cfg["frames"])
    with torch.no_grad():
        x = torch.stack([ds[i][0] for i in range(len(ds))])
        asp = torch.tensor([[ds[i][1]] for i in range(len(ds))])
        nodes, _, pres = small.points(x, asp)
        ref = small(x, asp).double().numpy()
    from models import shipped_features
    feats = shipped_features(nodes, pres).double().numpy()
    got = f16(feats)
    diff = float(np.abs(got - ref).max())
    agree = float((got.argmax(1) == ref.argmax(1)).mean())
    ok_cover = all(v >= c - 0.03 for c, v in cover)
    # Float16 weights move logits by about 1e-2, so the bar is top-1 agreement; the Kotlin fixture test checks 1e-3.
    gate = {"max_abs_diff_f16": diff, "top1_agreement_f16": agree, "coverage_per_fold": cover, "temperature": t,
            "pass": bool(agree >= 0.99 and ok_cover)}
    (STAGE / "gate.json").write_text(json.dumps(gate, indent=1), encoding="utf-8")
    print("gate", gate, flush=True)


if __name__ == "__main__":
    main(sys.argv[1:])
