"""Fine-tunes the app's ASL reader on ASL Citizen clips of its own words (glosses matched by name, variant numbers
dropped). Signers are split three ways by hash: train, tune (picks the epoch) and validation (scored once, shipped
reader against the tuned one, McNemar). ASL Citizen's own test split is not landmarked and never read.

    python asl_tune.py OUT [--epochs 30] [--seed 1] [--cache C:/work/ml-data/cache_asl_full] [--kd] [--rehearse PKL]

--rehearse PKL (prepare_asl_signs.py --no-test output) adds, to every batch, as many asl-signs clips of all 250 words
from signers the shipped reader already trained on, the words ASL Citizen lacks drawn REHEARSE_WEIGHT times as often:
the old signers and those words stay in play, but they give no held-out number.

--kd keeps the shipped reader as a teacher: half the loss is KL to its outputs (temperature 2) over all 250 words, on
the batch and on as many training-signer clips of glosses outside the 250, and the head rows of the unmatched words
stay frozen. The JOB line reports, on validation clips of outside glosses, how often the top word is an unmatched one
and the probability mass on them, against the shipped reader.

Writes OUT/best.pt (the 250-word reader) and a JOB line. The words with no ASL Citizen clips still change: the shared
trunk moves, and their head rows are pushed down on every batch, so the old data would be needed to judge them.
"""
import copy
import hashlib
import json
import os
import pathlib
import re
import shutil
import sys

import numpy as np
import pandas as pd
import torch

import exp
from cache import frames_of
from features import resample_index
from guard import DATA

FRAMES = 32


def norm(w):
    return re.sub(r"[^a-z]", "", re.sub(r"\d+$", "", w.lower()))


def shipped_asl():
    """The app's ASL reader through exp.app_model (which reads sign_reader.*): its files under that name."""
    src = DATA / "staging" / "asl_shipped"
    src.mkdir(parents=True, exist_ok=True)
    for ext in ("json", "bin"):
        shutil.copyfile(exp.APP_MODELS / f"asl_reader.{ext}", src / f"sign_reader.{ext}")
    os.environ["APP_READER_DIR"] = str(src)
    return exp.app_model()


def part(signer):
    h = int(hashlib.sha1(signer.encode()).hexdigest(), 16) % 10
    return "val" if h < 2 else "tune" if h < 3 else "train"


KD_T, KD_ALPHA = 2.0, 0.5
REHEARSE_WEIGHT = 3.0


def rehearsal(pkl, word, unmatched):
    """Raw frames, labels, aspects and sampling weights of the asl-signs training clips."""
    import pickle
    from models import FACE, from_cache_frame
    rare = set(unmatched.tolist())
    xs, ys, asp = [], [], []
    for c in pickle.load(open(pkl, "rb"))["samples"]:  # nosemgrep: trailofbits.python.pickles-in-pytorch.pickles-in-pytorch
        k = word.get(norm(c["label"]), -1)
        if c["split"] != "train" or k < 0:
            continue
        n = len(c["pose"])
        xs.append(from_cache_frame(c["left"], c["right"], c["pose"], np.zeros((n, FACE, 2), np.float32),
                                   c["has_left"], c["has_right"], c["has_pose"], np.zeros(n, bool)))
        ys.append(k)
        asp.append(float(c["aspect"]))
    ys = np.array(ys)
    w = np.where(np.isin(ys, list(rare)), REHEARSE_WEIGHT, 1.0)
    return xs, ys, np.array(asp, np.float32), w / w.sum()


def main(out, epochs=30, seed=1, cache=DATA / "cache_asl_full", kd=False, rehearse=None):
    torch.manual_seed(seed)
    out = pathlib.Path(out)
    out.mkdir(parents=True, exist_ok=True)
    model, labels, frames = shipped_asl()
    word = {norm(w): i for i, w in enumerate(labels)}
    index = pd.read_csv(pathlib.Path(cache) / "index.csv", keep_default_na=False)
    y = index["label"].str.removeprefix("asl:").map(lambda g: word.get(norm(g), -1)).to_numpy()
    use = y >= 0
    parts = index["signer"].map(part).to_numpy()
    rows = {p: np.where(use & (parts == p))[0] for p in ("train", "tune", "val")}
    outside = {p: np.where(~use & (parts == p))[0] for p in ("train", "val")}
    unmatched = torch.tensor([i for i in range(len(labels)) if not (y == i).any()])
    dev = torch.device("cuda")
    torch.cuda.set_per_process_memory_fraction(0.3)
    raw = frames_of(cache, index)
    st, ln, asp = index["start"].to_numpy(), index["length"].to_numpy(), index["aspect"].to_numpy(np.float32)

    def clips(r, jitter, g=None):
        x = []
        for i in r:
            n = int(ln[i])
            idx = np.arange(n)
            if jitter and n > 4:
                lo, hi = g.integers(0, n * 15 // 100 + 1, 2)
                idx = idx[lo:n - hi]
                idx = np.sort(g.choice(idx, max(2, int(len(idx) * g.uniform(0.8, 1.2))), replace=True))
            x.append(np.asarray(raw(i)[st[i] + idx[resample_index(len(idx), FRAMES)]]))
        return torch.from_numpy(np.stack(x)).to(dev), torch.from_numpy(asp[r]).to(dev)[:, None]

    @torch.no_grad()
    def right(m, r):
        m.eval()
        out_ = []
        for b in range(0, len(r), 256):
            x, a = clips(r[b:b + 256], False)
            out_.append((m(x, a).argmax(1).cpu().numpy() == y[r[b:b + 256]]))
        return np.concatenate(out_)

    @torch.no_grad()
    def probs(m, r):
        m.eval()
        return torch.cat([torch.softmax(m(*clips(r[b:b + 256], False)), 1).cpu() for b in range(0, len(r), 256)])

    model = model.to(dev)
    teacher = copy.deepcopy(model).eval() if kd else None
    oov_val = np.random.default_rng(0).choice(outside["val"], min(3000, len(outside["val"])), replace=False)
    base_oov = probs(model, oov_val)
    base_val = right(model, rows["val"])
    base_tune = right(model, rows["tune"]).mean()
    cfg = json.loads((exp.HERE / "experiments.json").read_text())["B5A50F"]
    if rehearse:
        rx, ry, ra, rw = rehearsal(rehearse, word, unmatched)
        print(f"rehearsal: {len(ry)} clips of {len(set(ry.tolist()))} words, "
              f"{int(np.isin(ry, unmatched.numpy()).sum())} of them unmatched", flush=True)

    def rehearse_batch(n):
        pick = rng.choice(len(ry), n, p=rw)
        x = np.stack([rx[i][resample_index(len(rx[i]), FRAMES)] for i in pick])
        return torch.from_numpy(x).to(dev), torch.from_numpy(ra[pick]).to(dev)[:, None], torch.from_numpy(ry[pick]).to(dev)
    model.bn1.eval()
    model.bn2.eval()
    opt = torch.optim.AdamW([p for n, p in model.named_parameters() if not n.startswith("bn")], 1e-4, weight_decay=0.01)
    g = torch.Generator(device=dev).manual_seed(seed)
    rng = np.random.default_rng(seed)
    best, state = (base_tune, 0), {k: v.clone() for k, v in model.state_dict().items()}
    for ep in range(epochs):
        model.train()
        model.bn1.eval()
        model.bn2.eval()
        tr = rng.permutation(rows["train"])
        for b in range(0, len(tr), 64):
            x, a = clips(tr[b:b + 64], True, rng)
            x = exp.augment(x, a, cfg, g)
            yb = torch.from_numpy(y[tr[b:b + 64]]).to(dev)
            if rehearse:
                xr, ar, yr = rehearse_batch(len(x))
                x, a, yb = torch.cat([x, exp.augment(xr, ar, cfg, g)]), torch.cat([a, ar]), torch.cat([yb, yr])
            logits = model(x, a)
            loss = torch.nn.functional.cross_entropy(logits, yb, label_smoothing=0.1)
            if kd:
                xo, ao = clips(rng.choice(outside["train"], len(x), replace=False), True, rng)
                xo = exp.augment(xo, ao, cfg, g)
                xa, aa = torch.cat([x, xo]), torch.cat([a, ao])
                with torch.no_grad():
                    soft = torch.softmax(teacher(xa, aa) / KD_T, 1)
                kl = torch.nn.functional.kl_div(torch.log_softmax(model(xa, aa) / KD_T, 1), soft, reduction="batchmean") * KD_T ** 2
                loss = (1 - KD_ALPHA) * loss + KD_ALPHA * kl
            opt.zero_grad()
            loss.backward()
            opt.step()
            if kd:
                # Restored rather than zero-graded: AdamW's weight decay would still shrink them.
                with torch.no_grad():
                    model.head.weight[unmatched] = teacher.head.weight[unmatched]
                    model.head.bias[unmatched] = teacher.head.bias[unmatched]
        t = right(model, rows["tune"]).mean()
        print(f"epoch {ep + 1}: loss {loss.item():.3f} tune {t:.3f} (shipped {base_tune:.3f})", flush=True)
        if t > best[0]:
            best, state = (t, ep + 1), {k: v.clone() for k, v in model.state_dict().items()}
    model.load_state_dict(state)
    val = right(model, rows["val"])
    torch.save({"state": {k: v.cpu() for k, v in state.items()}, "labels": labels, "epoch": best[1]}, out / "best.pt")  # nosemgrep: trailofbits.python.pickles-in-pytorch.pickles-in-pytorch
    p = exp.mcnemar(val, base_val)
    oov = probs(model, oov_val)
    shift = (f"unmatched_top1 shipped={np.isin(base_oov.argmax(1).numpy(), unmatched.numpy()).mean():.3f} "
             f"tuned={np.isin(oov.argmax(1).numpy(), unmatched.numpy()).mean():.3f} unmatched_mass shipped="
             f"{base_oov[:, unmatched].sum(1).mean():.3f} tuned={oov[:, unmatched].sum(1).mean():.3f}")
    signers = {k: int(index.loc[v, "signer"].nunique()) for k, v in rows.items()}
    print(f"JOB asl_tune best_epoch={best[1]} tune={best[0]:.3f} tune_shipped={base_tune:.3f} val={val.mean():.3f} "
          f"val_shipped={base_val.mean():.3f} tuned_only={int((val & ~base_val).sum())} shipped_only={int((base_val & ~val).sum())} "
          f"p={p:.3g} {shift} kd={kd} rehearse={bool(rehearse)} words={len(set(y[use]))} clips={ {k: len(v) for k, v in rows.items()} } signers={signers} status=done")


if __name__ == "__main__":
    a = sys.argv[1:]
    opt = lambda k, d: type(d)(a[a.index(k) + 1]) if k in a else d  # noqa: E731
    main(a[0], opt("--epochs", 30), opt("--seed", 1), opt("--cache", str(DATA / "cache_asl_full")), "--kd" in a,
         opt("--rehearse", "") or None)
