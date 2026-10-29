"""Stage B: one experiment row of experiments.json, trained and scored on the stage S cache, one EVAL.md row.

    python exp.py B3 [--seed 1] [--epochs 60] [--resume] [--test] [--cache C:/work/ml-data/cache_v4]

Augmentation runs batched on the GPU; workers only slice the memory-mapped cache. Checkpoint every 5 epochs and on
interrupt; a NaN loss rolls back and halves the learning rate (twice is failed_nan); CUDA out of memory halves the
batch and resumes. --test also scores the test split (milestones only).
"""
import json
import math
import os
import pathlib
import sys
import time

import numpy as np
import pandas as pd
import torch
import torch.nn.functional as F

from cache import Clips, frames_of
from models import ARCHS, F as FACE_COLS, L, P, PRES, R, Shipped
from guard import DATA

HERE = pathlib.Path(__file__).resolve().parent
RUNS = DATA / "runs"
EVAL = HERE / "EVAL.md"
APP_MODELS = HERE.parents[1] / "app" / "src" / "main" / "assets" / "content" / "models"
POSE_SWAP = [0, 4, 5, 6, 1, 2, 3, 8, 7, 10, 9, 12, 11, 14, 13, 16, 15, 18, 17, 20, 19, 22, 21, 24, 23, 26, 25, 28, 27,
             30, 29, 32, 31]


def augment(x, aspect, cfg, g):
    """x: (B, T, RAW) raw frames on the GPU. Mirror, affine, hand drop and, per row, the experiment's extras."""
    x = x.clone()
    b, t, _ = x.shape
    pts = lambda sl: x[..., sl].reshape(b, t, -1, 2)  # noqa: E731
    flip = torch.rand(b, device=x.device, generator=g) < 0.5
    if flip.any():
        y = x[flip]
        pres = y[..., PRES]
        for sl, k in ((L, 0), (R, 1), (P, 2), (FACE_COLS, 3)):
            v = y[..., sl].reshape(*y.shape[:2], -1, 2)
            v = torch.stack([torch.where(pres[..., k:k + 1] > 0, 1 - v[..., 0], v[..., 0]), v[..., 1]], -1)
            y[..., sl] = v.reshape(*y.shape[:2], -1)
        # A left-handed signer: hands, presence flags and pose sides all swap.
        left, right = y[..., L].clone(), y[..., R].clone()
        y[..., L], y[..., R] = right, left
        pose = y[..., P].reshape(*y.shape[:2], 33, 2)
        y[..., P] = pose[:, :, POSE_SWAP].reshape(*y.shape[:2], 66)
        y[..., PRES.start], y[..., PRES.start + 1] = y[..., PRES.start + 1].clone(), y[..., PRES.start].clone()
        x[flip] = y
    pres = x[..., PRES].clone()
    th = (torch.rand(b, device=x.device, generator=g) - 0.5) * 0.4
    sc = 0.9 + 0.2 * torch.rand(b, 2, device=x.device, generator=g)
    sh = (torch.rand(b, 2, device=x.device, generator=g) - 0.5) * 0.1
    if cfg.get("synthetic"):
        # A different camera angle: yaw of up to 30 degrees foreshortens x about the body.
        sc[:, 0] *= torch.cos((torch.rand(b, device=x.device, generator=g) - 0.5) * math.radians(60))
    rot = torch.stack([torch.cos(th), -torch.sin(th), torch.sin(th), torch.cos(th)], 1).view(b, 1, 2, 2)
    if cfg.get("shear"):
        # HWGAT-style shear (sl-hwgat dataTransform.py): off-diagonal terms drawn with this standard deviation.
        o = torch.randn(b, 2, device=x.device, generator=g) * cfg["shear"]
        shear = torch.stack([torch.ones_like(o[:, 0]), o[:, 0], o[:, 1], torch.ones_like(o[:, 0])], 1).view(b, 1, 2, 2)
        rot = rot @ shear
    ky = torch.cat([torch.ones_like(aspect), 1 / aspect], 1).view(b, 1, 1, 2)
    for sl, k in ((L, 0), (R, 1), (P, 2), (FACE_COLS, 3)):
        v = pts(sl) * ky
        c = torch.tensor([0.5, 0.5], device=x.device) * ky
        v = (((v - c) * sc.view(b, 1, 1, 2)) @ rot.transpose(2, 3)) + c + sh.view(b, 1, 1, 2)
        if cfg.get("strong") and k < 2:
            # train.py's SIGN_AUG=strong: each hand larger or smaller about its centre and shifted by up to a few tenths
            # of a shoulder width, as other signers hold their hands.
            pose_v = pts(P) * ky
            sw = (pose_v[:, :, 11] - pose_v[:, :, 12]).norm(dim=-1).mean(1).view(b, 1, 1, 1)
            ctr = v.mean(2, keepdim=True)
            s_ = 0.85 + 0.35 * torch.rand(b, 1, 1, 1, device=x.device, generator=g)
            v = ctr + (v - ctr) * s_ + torch.randn(b, 1, 1, 2, device=x.device, generator=g) * 0.12 * sw
        if cfg.get("synthetic") and k < 2:
            # Other bodies: each hand larger or smaller about its wrist.
            s = 0.85 + 0.35 * torch.rand(b, 1, 1, 1, device=x.device, generator=g)
            v = v[:, :, :1] + (v - v[:, :, :1]) * s
        x[..., sl] = (v / ky).reshape(b, t, -1) * pres[..., k:k + 1]
    # The phone loses a hand for a few frames at a time.
    rate = 0.3 * torch.rand(b, 1, 2, device=x.device, generator=g)
    drop = torch.rand(b, t, 2, device=x.device, generator=g) < rate
    if cfg.get("one_hand"):
        whole = torch.rand(b, 1, 1, device=x.device, generator=g) < cfg["one_hand"]
        side = torch.rand(b, 1, 1, device=x.device, generator=g) < 0.5
        drop |= whole & torch.cat([side, ~side], 2)
    for k, sl in ((0, L), (1, R)):
        keep = (~drop[..., k:k + 1]).float()
        x[..., sl] *= keep
        x[..., PRES.start + k] *= keep[..., 0]
    # INCLUDE and Kaggle carry no face, so a face would name the source; half the clips lose it in training.
    face = (torch.rand(b, 1, 1, device=x.device, generator=g) >= cfg.get("face_drop", 0.5)).float()
    x[..., FACE_COLS] *= face
    x[..., PRES.start + 3] *= face[..., 0]
    return x


FINGERS = [[0, 1, 2, 3, 4], [0, 5, 6, 7, 8], [0, 9, 10, 11, 12], [0, 13, 14, 15, 16], [0, 17, 18, 19, 20]]
NONSIGN = "__nonsign__"


def joint_angles(hand):
    """(..., 21, 2) hand points to the cosines of the 15 finger joint bends, free of scale and in-plane rotation."""
    out = []
    for f in FINGERS:
        for j in range(1, 4):
            a, b = hand[..., f[j], :] - hand[..., f[j - 1], :], hand[..., f[j + 1], :] - hand[..., f[j], :]
            out.append((a * b).sum(-1) / (a.norm(dim=-1) * b.norm(dim=-1)).clamp(min=1e-6))
    return torch.stack(out, -1)


def phonology(nodes, mask, cents=None):
    """B8 targets per clip from the hand seen most: handshape (nearest k-means centre of joint bends at the frame
    nearest mid-clip), location (3 x 3 grid of the mean wrist about the shoulders), movement (8 directions of wrist
    travel, or still). -100 where the hand is seen in under 2 frames. Without cents, returns the mid-clip bends."""
    b, t = nodes.shape[:2]
    left = mask[:, :, 0].sum(1) >= mask[:, :, 21].sum(1)
    hand = torch.where(left.view(b, 1, 1, 1), nodes[:, :, :21], nodes[:, :, 21:42])
    seen = torch.where(left.view(b, 1), mask[:, :, 0], mask[:, :, 21]) > 0
    ok = seen.sum(1) >= 2
    i = torch.arange(t, device=nodes.device)
    mid = (seen * (t - (i - t / 2).abs())).argmax(1)
    bends = joint_angles(hand[torch.arange(b), mid])
    if cents is None:
        return bends[ok]
    shape = torch.cdist(bends, cents).argmin(1)
    n = seen.sum(1, keepdim=True).clamp(min=1)
    w = (hand[:, :, 0] * seen[..., None]).sum(1) / n
    loc = torch.bucketize(w[:, 1], torch.tensor([-0.7, 0.8], device=w.device)) * 3 + \
        torch.bucketize(w[:, 0], torch.tensor([-0.5, 0.5], device=w.device))
    first = (seen * (t - i)).argmax(1)
    last = (seen * (i + 1)).argmax(1)
    d = hand[torch.arange(b), last, 0] - hand[torch.arange(b), first, 0]
    ang = ((torch.atan2(d[:, 1], d[:, 0]) + math.pi) / (2 * math.pi) * 8).long().clamp(max=7)
    move = torch.where(d.norm(dim=1) < 0.3, torch.full_like(ang, 8), ang)
    miss = torch.full_like(shape, -100)
    return [torch.where(ok, v, miss) for v in (shape, loc, move)]


def handshape_centres(ds, dev, k=32, iters=20):
    """k-means over mid-clip joint bends of the training clips, for B8 handshape targets."""
    from models import Points
    pts, bends = Points(), []
    for x, aspect, _ in torch.utils.data.DataLoader(ds, batch_size=512, num_workers=2):
        nodes, mask, _ = pts(x.to(dev), aspect[:, None].to(dev))
        bends.append(phonology(nodes, mask))
    z = torch.cat(bends)
    g = torch.Generator(device=dev).manual_seed(0)
    c = z[torch.randperm(len(z), generator=g, device=dev)[:k]].clone()
    for _ in range(iters):
        a = torch.cdist(z, c).argmin(1)
        for j in range(k):
            if (a == j).any():
                c[j] = z[a == j].mean(0)
    return c


def soft_targets(cfg, classes, vocab, dev, eps=0.1):
    """B9: label smoothing mass goes half to every class and half to the class's look-alikes (3 nearest classes by
    the parent's head weights) and to words of the same meaning (same text once variant marks are dropped)."""
    import re
    q = torch.full((classes, classes), 0.5 * eps / classes, device=dev)
    near = [[] for _ in range(classes)]
    parent = RUNS / f"{cfg['parent']}_s1" / "best.pt"
    if parent.exists():
        wts = torch.load(parent, weights_only=True)["head.weight"].to(dev)  # nosemgrep: trailofbits.python.pickles-in-pytorch.pickles-in-pytorch
        if len(wts) == classes:
            sim = F.normalize(wts, dim=1) @ F.normalize(wts, dim=1).T
            sim.fill_diagonal_(-2)
            near = sim.topk(3, dim=1).indices.tolist()
    base = [re.sub(r"\s*\(.*?\)|\s*-?\d+$", "", w).strip() for w in vocab]
    same = {}
    for i, w in enumerate(base):
        same.setdefault(w, []).append(i)
    for i in range(classes):
        group = sorted(set(near[i]) | set(same[base[i]]) - {i})
        if group:
            q[i, group] += 0.5 * eps / len(group)
        else:
            q[i] += 0.5 * eps / classes
        q[i, i] += 1 - eps
    return q / q.sum(1, keepdim=True)


class SignerShift(torch.utils.data.Dataset):
    """B11: each signer's mean wrist offset (shoulder-width units, from its first 20 clips) taken off its hands, as
    the phone would after the first 20 windows."""

    def __init__(self, ds, index, rows_all):
        self.ds, self.rows, self.root = ds, ds.rows, ds.root
        sig = index["signer"].to_numpy()
        off = {}
        raw = np.load(ds.root / "raw.npy", mmap_mode="r")
        st, ln, asp = index["start"].to_numpy(), index["length"].to_numpy(), index["aspect"].to_numpy(np.float32)
        for s in np.unique(sig[rows_all]):
            first = [r for r in rows_all if sig[r] == s][:20]
            w = [self.wrists(np.asarray(raw[st[r]:st[r] + ln[r]]), asp[r]) for r in first]
            w = [v for v in w if v is not None]
            off[s] = np.nanmean(np.stack(w), 0) if w else np.zeros((2, 2), np.float32)
        self.off = {r: np.nan_to_num(off[sig[r]]).astype(np.float32) for r in rows_all}

    @staticmethod
    def frame(x, aspect):
        ky = np.array([1.0, 1.0 / aspect], np.float32)
        pose = x[:, P].reshape(len(x), 33, 2) * ky
        hp = x[:, PRES.start + 2] > 0
        if not hp.any():
            return None
        center = ((pose[hp, 11] + pose[hp, 12]) / 2).mean(0)
        scale = max(float(np.linalg.norm(pose[hp, 11] - pose[hp, 12], axis=1).mean()), 1e-3)
        return ky, center, scale

    def wrists(self, x, aspect):
        f = self.frame(x, aspect)
        if f is None:
            return None
        ky, center, scale = f
        out = np.full((2, 2), np.nan, np.float32)
        for k, sl in ((0, L), (1, R)):
            seen = x[:, PRES.start + k] > 0
            if seen.any():
                out[k] = ((x[seen, sl.start:sl.start + 2] * ky - center) / scale).mean(0)
        return out

    def __len__(self):
        return len(self.ds)

    def __getitem__(self, i):
        x, aspect, r = self.ds[i]
        x = x.clone()
        f = self.frame(x.numpy(), float(aspect))
        if f is not None:
            ky, _, scale = f
            for k, sl in ((0, L), (1, R)):
                seen = x[:, PRES.start + k] > 0
                shift = torch.from_numpy(np.tile(self.off[r][k] * scale / ky, 21))
                x[seen, sl] -= shift
        return x, aspect, r


_GPU_DATA = {}


class GpuBatches:
    """Training clips held on the GPU, batched and time-jittered there exactly as Clips does on the CPU (crop up to
    15% each end, redraw 0.8 to 1.2 times as many frames with repeats, resample to T), so no loader workers compete
    with landmarking."""

    def __init__(self, root, index, rows, dev, t=48, speed=(0.8, 1.2)):
        raw = frames_of(root, index)
        st, ln = index["start"].to_numpy()[rows], index["length"].to_numpy()[rows]
        self.x = torch.from_numpy(np.concatenate([np.asarray(raw(r)[s:s + n]) for r, s, n in zip(rows, st, ln)])).to(dev)
        self.off = torch.as_tensor(np.r_[0, np.cumsum(ln)[:-1]], device=dev)
        self.n = torch.as_tensor(ln, device=dev)
        self.aspect = torch.as_tensor(index["aspect"].to_numpy(np.float32)[rows], device=dev)
        self.rows, self.t, self.dev, self.speed = torch.as_tensor(rows, device=dev), t, dev, speed

    def batch(self, pick, g):
        n = self.n[pick]
        b, dev = len(pick), self.dev
        cap = n * 15 // 100 + 1
        lo = (torch.rand(b, device=dev, generator=g) * cap).long()
        hi = (torch.rand(b, device=dev, generator=g) * cap).long()
        jitter = n > 4
        lo, hi = torch.where(jitter, lo, 0), torch.where(jitter, hi, 0)
        m = n - lo - hi
        lo_s, hi_s = self.speed
        k = torch.where(jitter, (m * (lo_s + (hi_s - lo_s) * torch.rand(b, device=dev, generator=g))).long().clamp(min=2), m)
        width = int(k.max())
        draw = (torch.rand(b, width, device=dev, generator=g) * m[:, None]).long()
        ar = torch.arange(width, device=dev)
        # Without jitter the frames are taken in order; with it, k sorted draws (repeats allowed) stand in for them.
        draw = torch.where(jitter[:, None], draw, ar[None].expand(b, -1))
        draw = torch.where(ar[None] < k[:, None], draw, torch.iinfo(torch.long).max).sort(1).values
        i = torch.arange(self.t, device=dev)
        pos = (i[None] * (k[:, None] - 1) * 2 + (self.t - 1)) // (2 * (self.t - 1))
        frame = draw.gather(1, pos) + lo[:, None]
        x = self.x[(self.off[pick][:, None] + frame).reshape(-1)].reshape(b, self.t, -1)
        return x, self.aspect[pick], self.rows[pick]


def bootstrap(right, signers, n=1000, seed=7):
    """95% interval of accuracy, resampling signers, then clips within each signer."""
    rng = np.random.default_rng(seed)
    groups = [np.flatnonzero(signers == s) for s in np.unique(signers)]
    out = []
    for _ in range(n):
        pick = [g[rng.integers(0, len(g), len(g))] for g in (groups[i] for i in rng.integers(0, len(groups), len(groups)))]
        out.append(right[np.concatenate(pick)].mean())
    return float(np.percentile(out, 2.5)), float(np.percentile(out, 97.5))


def ece(conf, right, bins=10):
    e = 0.0
    for lo in np.linspace(0, 1, bins, endpoint=False):
        m = (conf > lo) & (conf <= lo + 1 / bins)
        if m.any():
            e += m.mean() * abs(right[m].mean() - conf[m].mean())
    return float(e)


def auroc(pos, neg):
    if not len(pos) or not len(neg):
        return float("nan")
    s = np.concatenate([pos, neg])
    r = s.argsort().argsort() + 1
    return float((r[: len(pos)].sum() - len(pos) * (len(pos) + 1) / 2) / (len(pos) * len(neg)))


def mcnemar(a, b):
    """Exact two-sided McNemar p-value from paired right/wrong vectors."""
    n01, n10 = int((a & ~b).sum()), int((~a & b).sum())
    n, k = n01 + n10, min(n01, n10)
    if n == 0:
        return 1.0
    return min(1.0, 2 * sum(math.comb(n, i) for i in range(k + 1)) / 2**n)


def predict(model, ds, dev, bs=64):
    model.eval()
    # Scores must match the phone's float32 CPU math: TF32 convolutions also flip near-ties.
    torch.backends.cudnn.allow_tf32 = torch.backends.cuda.matmul.allow_tf32 = False
    dl = torch.utils.data.DataLoader(ds, batch_size=bs, num_workers=0)  # small sets: spawning workers costs more than slicing the memmap here
    out = []
    with torch.no_grad():
        for x, aspect, _ in dl:
            # Full precision: bf16 flipped enough near-ties to fake a 3-point gap between two equal readers.
            out.append(model(x.to(dev), aspect[:, None].to(dev)).float().cpu())
    return torch.cat(out) if out else torch.zeros(0)


def score(logits, labels, index, rows, vocab_set):
    """Metrics in label-string space, so a model with another label list (the shipped one) scores the same way."""
    idx = index.iloc[rows]
    y = idx["label"].to_numpy()
    scored = (idx["scored"].astype(str) == "True").to_numpy() & np.isin(y, list(vocab_set))
    p = logits.softmax(1).numpy()
    top = np.argsort(-p, 1)[:, :5]
    names = np.array(labels)
    right1 = names[top[:, 0]] == y
    right5 = (names[top] == y[:, None]).any(1)
    s = scored
    sig = idx["signer"].to_numpy()
    lo, hi = bootstrap(right1[s], sig[s]) if s.any() else (float("nan"),) * 2
    per_signer = pd.Series(right1[s]).groupby(sig[s]).agg(["mean", "size"])
    per_class = pd.Series(right1[s]).groupby(y[s]).mean()
    conf = p.max(1)
    unknown = ~np.isin(y, list(vocab_set))
    pred = names[top[:, 0]]
    # Macro-F1 over the scored words.
    f1 = []
    for c in np.unique(y[s]):
        tp = int(((pred == c) & (y == c) & s).sum())
        f1.append(2 * tp / max(1, 2 * tp + int(((pred == c) & (y != c) & s).sum()) + int(((pred != c) & (y == c) & s).sum())))
    big = per_signer[per_signer["size"] >= 20] if (per_signer["size"] >= 20).any() else per_signer
    worst = big["mean"].idxmin() if len(big) else ""
    src = idx["source"].to_numpy()
    # Selection metric: mean per-signer top-1 over new-signer clusters (ISLRTC, RKMVU); the Kaggle val signer shares
    # the training studio, so it is reported but never chooses.
    new = s & ~np.isin(src, ["kaggle", "include"])
    # Words with one training clip are their own slice (stage E prototypes take them later).
    once = index.loc[index["split"] == "train", "label"].value_counts()
    rare = new & np.isin(y, once.index[once == 1])
    per_new = pd.Series(right1[new]).groupby(sig[new]).mean()
    sel_lo, sel_hi = bootstrap(right1[new], sig[new]) if new.any() else (float("nan"),) * 2
    calib = calibrated(logits.float().numpy(), names, y, s)
    ns = y == NONSIGN
    nonsign_fa = float((pred[ns] != NONSIGN).mean()) if ns.any() and NONSIGN in set(names) else float("nan")
    wrong = s & ~right1
    confused = pd.Series(list(zip(y[wrong], pred[wrong]))).value_counts().head(10)
    return {"n": int(s.sum()), "top1": float(right1[s].mean()) if s.any() else float("nan"), "lo": lo, "hi": hi,
            # Pooled over every new-signer clip: a per-signer mean lets a 1-clip signer swing it by half.
            "sel": float(right1[new].mean()) if new.any() else float("nan"), "sel_lo": sel_lo, "sel_hi": sel_hi,
            "sel_signers": float(per_new[pd.Series(sig[new]).value_counts().reindex(per_new.index) >= 20].mean())
            if len(per_new) else float("nan"),
            "n_new": int(new.sum()), "n_rare": int(rare.sum()),
            "top1_rare": float(right1[rare].mean()) if rare.any() else float("nan"),
            "top5": float(right5[s].mean()) if s.any() else float("nan"),
            "macro": float(per_class.mean()) if len(per_class) else float("nan"),
            "macro_f1": float(np.mean(f1)) if f1 else float("nan"),
            "worst_signer": float(big["mean"].min()) if len(big) else float("nan"), "worst_signer_id": str(worst),
            "per_signer": {str(k): round(float(v), 4) for k, v in per_signer["mean"].items()},
            "per_source": {str(k): round(float(v), 4) for k, v in pd.Series(right1[s]).groupby(src[s]).mean().items()},
            "ece": ece(conf[s], right1[s]) if s.any() else float("nan"),
            **calib, "nonsign_false_accept": nonsign_fa,
            "confused": [f"{a} -> {b} ({n})" for (a, b), n in confused.items()],
            "auroc_open": auroc(conf[s], conf[unknown]),
            "right": right1, "scored": s}


def app_only(logits, labels, words):
    keep = torch.as_tensor(np.isin(np.array(labels), list(words)))
    return logits.masked_fill(~keep, -1e9)


def quick_sel(logits, labels, index, rows, vocab_set):
    """The selection metric alone (pooled new-signer top-1), for early stopping: no bootstrap or calibration."""
    idx = index.iloc[rows]
    y, src, sig = idx["label"].to_numpy(), idx["source"].to_numpy(), idx["signer"].to_numpy()
    new = (idx["scored"].astype(str) == "True").to_numpy() & np.isin(y, list(vocab_set)) & ~np.isin(src, ["kaggle", "include"])
    right = np.array(labels)[logits.argmax(1).numpy()] == y
    return float(right[new].mean()) if new.any() else float("nan")


def calibrated(logits, names, y, s, alpha=0.1, k_reg=5, lam=0.01):
    """Temperature (NLL, ECE) and RAPS sets at 90% target coverage, two-fold over the scored clips: fitted on one
    half, measured on the other, then swapped, so no clip scores its own calibration."""
    pos = {w: i for i, w in enumerate(names)}
    rows = np.flatnonzero(s & np.isin(y, names))
    if len(rows) < 20:
        return {"nll_t": float("nan"), "ece_t": float("nan"), "temperature": float("nan"), "raps_size": float("nan"),
                "raps_coverage": float("nan")}
    t_idx = np.array([pos[w] for w in y[rows]])
    z = logits[rows]
    folds = [np.arange(len(rows)) % 2 == k for k in (0, 1)]
    out = {"nll_t": [], "ece_t": [], "temperature": [], "raps_size": [], "raps_coverage": []}

    def soft(v, temp):
        v = v / temp
        v = np.exp(v - v.max(1, keepdims=True))
        return v / v.sum(1, keepdims=True)

    for fit, use in (folds, folds[::-1]):
        grid = np.linspace(0.5, 5, 46)
        nll = [-np.log(soft(z[fit], g)[np.arange(fit.sum()), t_idx[fit]] + 1e-12).mean() for g in grid]
        temp = float(grid[int(np.argmin(nll))])
        pf, pu = soft(z[fit], temp), soft(z[use], temp)
        out["temperature"].append(temp)
        out["nll_t"].append(float(-np.log(pu[np.arange(use.sum()), t_idx[use]] + 1e-12).mean()))
        out["ece_t"].append(ece(pu.max(1), pu.argmax(1) == t_idx[use]))

        def raps(pp, t=None):
            order = np.argsort(-pp, 1)
            srt = np.take_along_axis(pp, order, 1)
            reg = lam * np.maximum(0, np.arange(1, pp.shape[1] + 1) - k_reg)
            cum = np.cumsum(srt, 1) + reg
            if t is None:
                return cum, order
            rank = np.argmax(order == t[:, None], 1)
            return cum[np.arange(len(t)), rank]
        score_fit = raps(pf, t_idx[fit])
        q = np.quantile(score_fit, min(1.0, np.ceil((len(score_fit) + 1) * (1 - alpha)) / len(score_fit)))
        cum, order = raps(pu)
        size = (cum <= q).sum(1) + 1
        size = np.minimum(size, pu.shape[1])
        covered = np.argmax(order == t_idx[use][:, None], 1) < size
        out["raps_size"].append(float(size.mean()))
        out["raps_coverage"].append(float(covered.mean()))
    return {k: float(np.mean(v)) for k, v in out.items()}


def make_model(cfg, classes, vocab=()):
    if cfg.get("from_app"):
        # Row W: the app's reader, its 314 head rows copied into a head over the full vocabulary.
        app, labels, _ = app_model()
        m = Shipped(classes, hidden=app.head.in_features // 2, kernel=app.conv1.kernel_size[0])
        state = {k: v for k, v in app.state_dict().items() if not k.startswith("head.")}
        m.load_state_dict(state, strict=False)
        pos = {w: i for i, w in enumerate(labels)}
        with torch.no_grad():
            for j, w in enumerate(vocab):
                if w in pos:
                    m.head.weight[j] = app.head.weight[pos[w]]
                    m.head.bias[j] = app.head.bias[pos[w]]
        return m
    m = ARCHS[cfg["arch"]](classes=classes, **({"hidden": cfg["hidden"]} if "hidden" in cfg else {}))
    if cfg.get("init"):
        # Pretrained encoder (pretrain.py); the classifier layers start fresh.
        enc = torch.load(cfg["init"], weights_only=True)["encoder"]  # nosemgrep: trailofbits.python.pickles-in-pytorch.pickles-in-pytorch
        m.load_state_dict({k: v for k, v in enc.items() if not k.startswith(("head.", "top."))}, strict=False)
    return m


def app_model():
    """The reader in the app exactly as shipped: float16 weights from sign_reader.bin with batch norm folded, so the
    batch norm layers here are identities."""
    # APP_READER_DIR points at another copy, e.g. the reader from before a model was installed into the app.
    src = pathlib.Path(os.environ.get("APP_READER_DIR", APP_MODELS))
    spec = json.loads((src / "sign_reader.json").read_text(encoding="utf-8"))
    flat = np.fromfile(src / "sign_reader.bin", dtype="<f2").astype(np.float32)
    m = Shipped(len(spec["labels"]), hidden=spec["hidden"], kernel=spec["kernel"])
    state, at = m.state_dict(), 0
    keys = ["embed.weight", "embed.bias", "conv1.weight", "conv1.bias", "conv2.weight", "conv2.bias", "head.weight", "head.bias"]
    for k, t in zip(keys, spec["tensors"]):
        n = int(np.prod(t["shape"]))
        state[k] = torch.from_numpy(flat[at:at + n].reshape(t["shape"]).copy())
        at += n
    assert at == len(flat), "sign_reader.bin size does not match its tensors"
    for bn in ("bn1", "bn2"):
        state[f"{bn}.weight"].fill_(1)
        state[f"{bn}.bias"].zero_()
        state[f"{bn}.running_mean"].zero_()
        state[f"{bn}.running_var"].fill_(1 - m.bn1.eps)
    m.load_state_dict(state)
    return m, spec["labels"], spec["frames"]


def train(cfg, ds_train, ys, signers, classes, run, dev, epochs, val_eval, resume, vocab=()):
    model = make_model(cfg, classes, vocab).to(dev)
    full = cfg.get("recipe") == "full"
    # Decay warms up with the step count: a flat 0.999 keeps the random start for the first few thousand steps.
    ema_fn = lambda avg, p, n: avg + (p - avg) * (1 - min(0.999, (1 + float(n)) / (10 + float(n))))  # noqa: E731
    ema = torch.optim.swa_utils.AveragedModel(model, avg_fn=ema_fn) if full else None
    lr = (1e-3 if full else 2e-3) * cfg.get("lr_scale", 1.0)
    opt = torch.optim.AdamW(model.parameters(), lr=lr, weight_decay=0.05)
    adv = aux = cents = None
    if cfg.get("adversarial"):
        n_sig = int(signers.max()) + 1
        adv = torch.nn.Linear(model.head.in_features, n_sig).to(dev)
        opt.add_param_group({"params": adv.parameters()})
    if cfg.get("aux"):
        cents = handshape_centres(ds_train, dev)
        aux = torch.nn.ModuleList(torch.nn.Linear(model.head.in_features, k) for k in (len(cents), 9, 9)).to(dev)
        opt.add_param_group({"params": aux.parameters()})
    feats = {}
    if adv is not None or aux is not None:
        model.head.register_forward_hook(lambda m, i, o: feats.__setitem__("x", i[0]))
    q = soft_targets(cfg, classes, vocab, dev) if cfg.get("meaning_smooth") else None
    # B5: one head over both languages' words; a clip's softmax runs over its own language's words only, so the rows
    # of each language form a head of their own over the shared trunk.
    asl = torch.tensor([w.startswith("asl:") for w in vocab], device=dev) if cfg.get("per_language") else None

    def ce(out, yy):
        if asl is not None:
            # Softmax and the 0.1 label smoothing both over the clip's own language's words only.
            own = asl[None] == asl[yy][:, None]
            logp = F.log_softmax(out.float().masked_fill(~own, float("-inf")), 1)
            nll = -logp.gather(1, yy[:, None]).squeeze(1)
            spread = -logp.masked_fill(~own, 0.0).sum(1) / own.sum(1)
            return (0.9 * nll + 0.1 * spread).mean()
        if q is not None:
            return -(q[yy] * F.log_softmax(out.float(), 1)).sum(1).mean()
        return F.cross_entropy(out.float(), yy, label_smoothing=0.1)

    def side(t):
        return F.cross_entropy(*t, ignore_index=-100) if (t[1] >= 0).any() else torch.zeros((), device=dev)
    # Square-root signer balance: a signer's share grows with the root of its clip count. Equal shares starved INCLUDE,
    # 4,284 clips from several people filed as one signer, to 1/57 of the batches (B1 train top-1 0.14 on it).
    own = signers[ds_train.rows]
    w = 1.0 / np.sqrt(np.bincount(own)[own])
    if cfg.get("asl_share") is not None:
        # B5: this share of each batch is ASL clips, the rest ISL, each side keeping its own signer balance.
        asl_row = np.array([vocab[ys[r]].startswith("asl:") for r in ds_train.rows])
        w[asl_row] *= cfg["asl_share"] / w[asl_row].sum()
        w[~asl_row] *= (1 - cfg["asl_share"]) / w[~asl_row].sum()
    # B3 needs batch 64 with 2 accumulated steps (effective 128, comparable) to fit beside another row.
    accum = cfg.get("accum", 1)
    bs, start, nan_strikes, best, best_epoch = cfg.get("batch", 128), 0, 0, -1.0, 0
    ck = run / "last.pt"
    if resume and ck.exists():
        s = torch.load(ck, weights_only=False)  # nosemgrep: trailofbits.python.pickles-in-pytorch.pickles-in-pytorch
        model.load_state_dict(s["model"]); opt.load_state_dict(s["opt"]); start = s["epoch"]; bs = s["bs"]
        best, best_epoch = s["best"], s["best_epoch"]
        if ema and s.get("ema"):
            ema.load_state_dict(s["ema"])

    def save(epoch):
        torch.save({"model": model.state_dict(), "opt": opt.state_dict(), "epoch": epoch, "bs": bs, "best": best,  # nosemgrep: trailofbits.python.pickles-in-pytorch.pickles-in-pytorch
                    "best_epoch": best_epoch, "ema": ema.state_dict() if ema else None}, run / "last.tmp")
        os.replace(run / "last.tmp", ck)

    g = torch.Generator(device=dev).manual_seed(cfg.get("seed", 1))
    steps_per_epoch = max(1, len(ds_train) // bs)
    epoch = start
    # One loader for the whole run: Windows spawns workers afresh (and re-imports torch) for every new loader.
    if isinstance(ds_train, Clips):
        # Plain clips live on the GPU and are batched there; B11's per-signer shift keeps the CPU loader.
        # Kept across the out-of-memory restart, which re-enters train(): a second copy would not fit.
        key = (str(ds_train.root), len(ds_train.rows), cfg.get("frames", 48))
        if key not in _GPU_DATA:
            _GPU_DATA.clear()
            _GPU_DATA[key] = GpuBatches(ds_train.root, pd.read_csv(ds_train.root / "index.csv", keep_default_na=False),
                                        ds_train.rows, dev, cfg.get("frames", 48), tuple(cfg.get("speed", (0.8, 1.2))))
        gb = _GPU_DATA[key]
        wt = torch.as_tensor(w, device=dev, dtype=torch.float)

        def batches():
            for _ in range(steps_per_epoch):
                x, aspect, rows = gb.batch(torch.multinomial(wt, bs, replacement=True, generator=g), g)
                yield x, aspect, rows.cpu()
    else:
        sampler = torch.utils.data.WeightedRandomSampler(w, steps_per_epoch * bs, generator=torch.Generator().manual_seed(start))
        dl = torch.utils.data.DataLoader(ds_train, batch_size=bs, sampler=sampler, num_workers=4, drop_last=True,
                                         persistent_workers=True, prefetch_factor=4)
        batches = dl.__iter__
    try:
        while epoch < epochs:
            model.train()
            if cfg.get("from_app"):
                # The app's batch norm is folded into its weights: these layers stay identities.
                model.bn1.eval()
                model.bn2.eval()
            total = 0.0
            for i, (x, aspect, rows) in enumerate(batches()):
                it = epoch * steps_per_epoch + i
                warm = min(1.0, it / (2 * steps_per_epoch))
                for pg in opt.param_groups:
                    pg["lr"] = lr * warm * 0.5 * (1 + math.cos(math.pi * it / (epochs * steps_per_epoch)))
                x, aspect = x.to(dev, non_blocking=True), aspect.view(-1, 1).to(dev)
                y = torch.as_tensor(ys[rows.numpy()], device=dev)
                try:
                    x = augment(x, aspect, cfg, g)
                    if aux is not None:
                        with torch.no_grad():
                            tg = phonology(*model.points(x, aspect)[:2], cents)
                    lam, perm = 1.0, torch.arange(len(x), device=dev)
                    if full and torch.rand(1, generator=g, device=dev).item() < 0.5:
                        lam = float(np.random.default_rng(it).beta(0.2, 0.2))
                        perm = torch.randperm(len(x), device=dev, generator=g)
                    with torch.autocast("cuda", dtype=torch.bfloat16):
                        out = model(lam * x + (1 - lam) * x[perm], aspect)
                    loss = lam * ce(out, y) + (1 - lam) * ce(out, y[perm])
                    if aux is not None:
                        # B8: handshape, location and movement heads on the pooled features.
                        f = feats["x"].float()
                        for h, t in zip(aux, tg):
                            loss = loss + cfg["aux"] * (lam * side((h(f), t)) + (1 - lam) * side((h(f), t[perm])))
                    if adv is not None:
                        # Gradient reversal: the signer head learns signers, the trunk learns to hide them.
                        f = feats["x"].float()
                        rev = f.detach() + cfg["adversarial"] * (f.detach() - f)
                        loss = loss + F.cross_entropy(adv(rev), torch.as_tensor(signers[rows.numpy()], device=dev))
                    if torch.isfinite(loss):
                        (loss / accum).backward()
                except torch.cuda.OutOfMemoryError:
                    torch.cuda.empty_cache()
                    bs = max(16, bs // 2)
                    print(f"OOM: batch {bs}", flush=True)
                    raise _Restart()
                if not torch.isfinite(loss):
                    nan_strikes += 1
                    if nan_strikes > 2:
                        return None, "failed_nan"
                    lr /= 2
                    print(f"NaN: rollback, lr {lr}", flush=True)
                    raise _Restart()
                if (i + 1) % accum == 0:
                    torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
                    opt.step()
                    opt.zero_grad(set_to_none=True)
                    if ema:
                        ema.update_parameters(model)
                total += loss.item()
            epoch += 1
            # Warm-started rows check every epoch and only choose after min_epochs: on 59 clips the first epochs
            # otherwise always win and every variant collapses back to its parent.
            every, min_ep, patience = cfg.get("eval_every", 5), cfg.get("min_epochs", 0), cfg.get("patience", 20)
            if epoch % every == 0 or epoch == epochs or epoch == 1:
                m = val_eval(ema.module if ema else model)
                print(f"epoch {epoch}: loss {total / steps_per_epoch:.3f} val top-1 {m:.4f}", flush=True)
                if m > best and epoch >= min_ep:
                    best, best_epoch = m, epoch
                    torch.save((ema.module if ema else model).state_dict(), run / "best.pt")  # nosemgrep: trailofbits.python.pickles-in-pytorch.pickles-in-pytorch
                save(epoch)
                if epoch >= min_ep and epoch - max(best_epoch, min_ep) >= patience:
                    break
    except _Restart:
        save(epoch)
        return train(cfg, ds_train, ys, signers, classes, run, dev, epochs, val_eval, True, vocab)
    except KeyboardInterrupt:
        save(epoch)
        raise
    model.load_state_dict(torch.load(run / "best.pt", weights_only=True))  # nosemgrep: trailofbits.python.pickles-in-pytorch.pickles-in-pytorch
    return model, "done"


class IslOnly(torch.nn.Module):
    """A co-trained reader as the ISL app sees it: the ASL head's rows never win."""

    def __init__(self, model, vocab):
        super().__init__()
        self.model = model
        self.register_buffer("asl", torch.tensor([w.startswith("asl:") for w in vocab]))

    def forward(self, x, aspect):
        return self.model(x, aspect).masked_fill(self.asl.to(x.device), -1e9)


class _Restart(Exception):
    pass


def eval_row(rid, cfg, data, val, old, test, accepted, note="", fit="-", vs_shipped="-"):
    def cell(m):
        if not (m and m["n"]):
            return "-"
        return (f"new signers {m['sel']:.3f} [{m['sel_lo']:.3f}, {m['sel_hi']:.3f}] (n={m['n_new']}); all {m['top1']:.3f} "
                f"[{m['lo']:.3f}, {m['hi']:.3f}] / {m['top5']:.3f}")
    if "### B. Experiments (new-signer val)" not in EVAL.read_text(encoding="utf-8"):
        with open(EVAL, "a", encoding="utf-8") as f:
            f.write("\n### B. Experiments (new-signer val)\n\n| ID | data | change | val: new-signer mean top-1 [95%]; all top-1 / top-5 | macro-F1 | "
                    "worst signer | per source | NLL, ECE after T | RAPS size, coverage | non-sign FA | train top-1, gap | "
                    "McNemar vs app model | old 269 | test | accepted |\n|" + "---|" * 15 + "\n")
    if val:
        src = ", ".join(f"{k} {v:.3f}" for k, v in val["per_source"].items())
        mid = (f"{val['macro_f1']:.3f} | {val['worst_signer_id']} {val['worst_signer']:.3f} | {src} | "
               f"{val['nll_t']:.3f}, {val['ece_t']:.3f} | {val['raps_size']:.1f}, {val['raps_coverage']:.3f} | "
               f"{val['nonsign_false_accept']:.3f}")
    else:
        mid = " | ".join("-" * 6)
    with open(EVAL, "a", encoding="utf-8") as f:
        f.write(f"| {rid} | {data} | {cfg['change']}{' (' + note + ')' if note else ''} | {cell(val)} | {mid} | {fit} | "
                f"{vs_shipped} | {old['top1']:.3f} | {cell(test) if test else '-'} | {accepted} |\n")
    if val and val["confused"]:
        with open(EVAL, "a", encoding="utf-8") as f:
            f.write(f"\n{rid} top confused pairs (true -> predicted): {'; '.join(val['confused'])}\n\n")


def verify(root):
    """Hard stops: the cache must be the one registered, and its test rows the ones fixed at the split."""
    import hashlib
    if not (root / "raw.npy").exists():
        # A joined index (join_cache.py): every cache it reads is checked instead.
        for src in pd.read_csv(root / "index.csv", usecols=["cache"])["cache"].unique():
            verify(pathlib.Path(src))
        return
    reg = json.loads((HERE / "cache_registry.json").read_text()).get(str(root)) or sys.exit(f"STOP: {root} not in registry")
    h = hashlib.sha256()
    with open(root / "raw.npy", "rb") as f:
        while b := f.read(1 << 24):
            h.update(b)
    if h.hexdigest() != reg["sha256"]:
        sys.exit(f"STOP: cache hash of {root} does not match the registry")
    test = root / "test_hash.txt"
    if test.exists():
        idx = pd.read_csv(root / "index.csv", keep_default_na=False)
        now = hashlib.sha256("\n".join(sorted(idx.loc[idx["split"] == "test", "clip"])).encode()).hexdigest()
        if now != test.read_text().strip():
            sys.exit("STOP: test split changed")


def main():
    a = sys.argv[1:]
    if os.name == "nt":
        import psutil
        # B rows outrank the landmark pool for CPU; loader workers inherit the class.
        psutil.Process().nice(psutil.ABOVE_NORMAL_PRIORITY_CLASS)
    rid = a[0]
    opt = lambda k, d: type(d)(a[a.index(k) + 1]) if k in a else d  # noqa: E731
    seed, epochs = opt("--seed", 1), opt("--epochs", 60)
    root = pathlib.Path(opt("--cache", str(DATA / "cache_v4")))
    exps = json.loads((HERE / "experiments.json").read_text())
    cfg = {**exps[rid], "seed": seed}
    if cfg.get("skip"):
        print(f"JOB {rid} status=skipped reason={cfg['skip']}")
        return
    parent = cfg.get("parent")
    if parent == "best":
        best = json.loads((RUNS / "best.json").read_text())
        parent = best["id"]
        cfg = {**exps[parent], **{k: v for k, v in cfg.items() if k != "parent"}, "parent": parent}
    torch.manual_seed(seed)
    # EXP_DEVICE=cpu re-scores without touching a GPU that training rows hold.
    dev = torch.device(os.environ.get("EXP_DEVICE", "cuda"))
    # Two rows share the 8 GB card; their fractions sum to 0.85 or less, under the 7 GB rule.
    if dev.type == "cuda":
        torch.cuda.set_per_process_memory_fraction(cfg.get("vram", 0.45))
    verify(root)
    index = pd.read_csv(root / "index.csv", keep_default_na=False)
    vocab = json.loads((root / "vocab.json").read_text(encoding="utf-8"))
    vset, cls = set(vocab), {w: i for i, w in enumerate(vocab)}
    split = index["split"].to_numpy()
    rows = lambda name: np.flatnonzero(split == name)  # noqa: E731
    val_rows, old_rows, test_rows = rows("val"), rows("old269"), rows("test")
    # --dry checks the plumbing on any cache: no EVAL.md row, no best.json.
    dry = "--dry" in a
    run = RUNS / ("_dry" if dry else "") / f"{rid}_s{seed}"
    run.mkdir(parents=True, exist_ok=True)

    frames = cfg.get("frames", 48)
    app_words = set(json.loads((APP_MODELS / "sign_reader.json").read_text(encoding="utf-8"))["labels"])

    def clips(rows, train=False):
        d = Clips(root, rows, train=train, t=frames)
        return SignerShift(d, index, np.arange(len(index))) if cfg.get("signer_norm") else d

    if cfg.get("app"):
        model, labels, frames = app_model()
        model = model.to(dev)
        # Scored only on val clips of the app's own words.
        vset = set(labels) & vset
        status = "done"
        data = "INCLUDE + Kaggle (app as shipped)"
    else:
        src = set(cfg["sources"]) | ({"nonsign"} if cfg.get("nonsign") else set())
        tr = np.flatnonzero((split == "train") & index["source"].isin(src).to_numpy() & index["label"].isin(vset).to_numpy())
        ys = np.full(len(index), -1)
        ys[tr] = [cls[w] for w in index["label"].iloc[tr]]
        sig_codes = pd.factorize(index["signer"])[0]
        ds_train = clips(tr, train=True)
        val_ds = clips(val_rows)
        labels = vocab

        def val_eval(m):
            lg = predict(IslOnly(m, labels) if cfg.get("per_language") else m, val_ds, dev)
            if cfg.get("select_restricted"):
                r = app_only(lg, labels, app_words)
                kag = val_rows[index["source"].to_numpy()[val_rows] == "kaggle"]
                pos = np.searchsorted(val_rows, kag)
                right = np.array(labels)[r[pos].argmax(1).numpy()] == index["label"].to_numpy()[kag]
                sel = quick_sel(r, labels, index, val_rows, vset & app_words)
                print(f"  restricted new-signer {sel:.4f}, Kaggle C {right.mean():.4f}", flush=True)
                return sel
            return quick_sel(lg, labels, index, val_rows, vset)
        t0 = time.time()
        if "--eval" in a:
            # Score a finished run again without training: its best checkpoint. --model-cache names the cache it was
            # trained on, whose vocabulary its head uses; scoring stays in label strings, so another cache's val works.
            if "--model-cache" in a:
                labels = json.loads((pathlib.Path(opt("--model-cache", "")) / "vocab.json").read_text(encoding="utf-8"))
            src_run = RUNS / (opt("--from-run", "") or run.name)
            model = make_model(cfg, len(labels), labels).to(dev)
            model.load_state_dict(torch.load(src_run / "best.pt", weights_only=True, map_location=dev))  # nosemgrep: trailofbits.python.pickles-in-pytorch.pickles-in-pytorch
            status = "done"
        else:
            model, status = train(cfg, ds_train, ys, sig_codes, len(vocab), run, dev, epochs, val_eval, "--resume" in a, vocab)
        data = " + ".join(sorted(src)) + f", {len(tr)} clips, {time.time() - t0:.0f} s"
        if model is None:
            eval_row(rid, cfg, data, None, {"top1": float("nan")}, None, "no", status)
            print(f"JOB {rid} status={status}")
            return
    if cfg.get("per_language"):
        model = IslOnly(model, labels).to(dev)
    lv = predict(model, clips(val_rows), dev)
    val = score(lv, labels, index, val_rows, vset)
    # As the app would show it: logits outside the app's 314 words masked, scored on those words only.
    val_r = score(app_only(lv, labels, app_words), labels, index, val_rows, vset & app_words)
    old = score(predict(model, clips(old_rows), dev), labels, index, old_rows, vset) if len(old_rows) else {"top1": float("nan")}
    test = score(predict(model, clips(test_rows), dev), labels, index, test_rows, vset) if "--test" in a else None
    np.savez(run / "val_preds.npz", right=val["right"], scored=val["scored"])
    np.savez(run / "val_preds_app_words.npz", right=val_r["right"], scored=val_r["scored"])
    inc = np.flatnonzero((split == "include_test") & index["label"].isin(app_words).to_numpy())
    if len(inc):
        # The shipped floor: INCLUDE's test split (signers seen in training), the app's words, as the app shows them.
        li = app_only(predict(model, clips(inc), dev), labels, app_words)
        hit = np.array(labels)[li.argmax(1).numpy()] == index["label"].to_numpy()[inc]
        lo_i, hi_i = bootstrap(hit, np.zeros(len(hit)))
        with open(EVAL, "a", encoding="utf-8") as f:
            f.write(f"\n{rid} INCLUDE test floor (0.979): {hit.mean():.3f} [{lo_i:.3f}, {hi_i:.3f}] (n={len(inc)})\n")
        print(f"ROW {rid} INCLUDE test {hit.mean():.3f} [{lo_i:.3f}, {hi_i:.3f}] n={len(inc)}", flush=True)
    fit = "-"
    if not cfg.get("app") and labels == vocab:
        # Overfit guard: train top-1 on 2,000 unaugmented training clips; a gap over 25 points flags the row.
        sub = np.random.default_rng(0).permutation(tr)[:2000]
        acc = float((predict(model, clips(sub), dev).argmax(1).numpy() == ys[sub]).mean())
        gap = acc - val["top1"]
        fit = f"{acc:.3f}, {gap:+.3f}" + (" FLAG" if gap > 0.25 else "")
    accepted, note = "baseline", ""
    if parent:
        pp = RUNS / f"{parent}_s1" / "metrics.json"
        if pp.exists():
            pm = json.loads(pp.read_text())
            pr = np.load(RUNS / f"{parent}_s1" / "val_preds.npz")
            both = val["scored"] & pr["scored"] if len(pr["right"]) == len(val["right"]) else None
            p = mcnemar(val["right"][both], pr["right"][both]) if both is not None else float("nan")
            accepted = "yes" if val["sel"] > pm["sel_hi"] else "unclear" if val["sel"] > pm["sel"] else "no"
            note = f"McNemar p={p:.3g} vs {parent}"
    vs_shipped = "-"
    # Against the reader in the app, on the val clips both score (the app knows 314 words).
    ref = RUNS / "APP_s1" / "val_preds.npz"
    if rid != "APP" and ref.exists():
        pr = np.load(ref)
        if len(pr["right"]) == len(val["right"]):
            # The gate compares what each would show in the app: this row restricted to the app's words.
            both = val_r["scored"] & pr["scored"]
            vs_shipped = (f"{val_r['right'][both].mean():.3f} vs {pr['right'][both].mean():.3f}, "
                          f"p={mcnemar(val_r['right'][both], pr['right'][both]):.3g}")
    metrics = {k: v for k, v in val.items() if k not in ("right", "scored")}
    restricted = {k: v for k, v in val_r.items() if k not in ("right", "scored")}
    (run / "metrics.json").write_text(json.dumps({**metrics, "restricted": restricted, "old269": old["top1"], "status": status},
                                                 indent=1), encoding="utf-8")
    if dry:
        print(f"DRY {rid} val {metrics} old {old['top1']}")
        return
    best_path = RUNS / "best.json"
    if accepted in ("yes", "baseline") and (not best_path.exists() or val["sel"] > json.loads(best_path.read_text())["sel"]):
        best_path.write_text(json.dumps({"id": rid, "sel": val["sel"]}), encoding="utf-8")
    eval_row(rid, cfg, data, val, old, test, accepted, note, fit, vs_shipped)
    print(f"ROW {rid} new-signer {val['sel']:.3f} [{val['sel_lo']:.3f}, {val['sel_hi']:.3f}] all {val['top1']:.3f} one-clip words {val['top1_rare']:.3f} (n={val['n_rare']}) worst {val['worst_signer_id']} "
          f"{val['worst_signer']:.3f} ECE_T {val['ece_t']:.3f} gap {fit} vs_shipped {vs_shipped}", flush=True)
    print(f"ROW {rid} restricted to app words: new-signer {val_r['sel']:.3f} [{val_r['sel_lo']:.3f}, {val_r['sel_hi']:.3f}] "
          f"(n={val_r['n_new']}) Kaggle C {val_r['per_source'].get('kaggle', float('nan')):.3f}", flush=True)
    with open(EVAL, "a", encoding="utf-8") as f:
        f.write(f"\n{rid} as the app shows it (logits masked to its 314 words, scored on those words): new-signer "
                f"{val_r['sel']:.3f} [{val_r['sel_lo']:.3f}, {val_r['sel_hi']:.3f}] (n={val_r['n_new']}), per source "
                f"{val_r['per_source']}, against the app {vs_shipped}\n\n")
    print(f"JOB {rid} ok=1 failed=0 quarantined=0 status={status} val_top1={val['top1']:.4f} accepted={accepted}")


if __name__ == "__main__":
    main()
