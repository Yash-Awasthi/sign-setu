"""Handshape model from hand crops (crops.py). Step labels: pseudo-labels per crop, the nearest of K k-means centres
of the hand's 15 finger-bend cosines (exp.joint_angles; mirror-free, so both hands share centres). Centres are fitted
on training signers' frames only; validation signers are only assigned.

    python handshape.py labels CACHE CROPS_DIR [--k 48]
    python handshape.py train CROPS_DIR OUT [--epochs 15] [--seed 1] [--app-words] [--balanced] [--lr 2e-3] [--patience 0] [--in-ram]

labels writes CROPS_DIR/labels.npy (rows, 8, 2) int16, -1 where the hand is not seen, and centres.npy.
train fits a small crop encoder (depthwise-separable convolutions, 96 px, ~0.1 M weights) with two heads: the crop's
pseudo-label, and the clip's word from the mean of its crops. About a tenth of the training signers (by hash) are
held out whole as the tune set that picks the epoch; validation signers are scored once, at the end. --app-words keeps
only clips of the app's words (the word head then has 314 rows); --balanced weighs the shape loss by inverse cluster
size (plain accuracy then drops, since rare shapes are favoured); --in-ram loads the crops instead of mapping them;
--patience stops after that many epochs without a better tune score. Writes OUT/val_word.npz (val rows, word right).
Writes OUT/encoder.pt and a JOB line.
"""
import hashlib
import json
import pathlib
import sys

import numpy as np
import pandas as pd
import torch
from torch import nn

from cache import frames_of
from crops import PICK, READER_T
from exp import joint_angles
from features import resample_index
from models import PRES


def bends(cache, rows):
    """(rows, 8, 2, 15) bend cosines and (rows, 8, 2) seen, at the frames crops.py cut."""
    index = pd.read_csv(pathlib.Path(cache) / "index.csv", keep_default_na=False)
    raw = frames_of(cache, index)
    out = np.zeros((len(rows), PICK, 2, 15), np.float32)
    seen = np.zeros((len(rows), PICK, 2), bool)
    for i, r in enumerate(rows):
        s, n = int(index.at[r, "start"]), int(index.at[r, "length"])
        x = np.asarray(raw(r)[s + resample_index(n, READER_T)[::READER_T // PICK]])
        ky = np.array([1.0, 1.0 / float(index.at[r, "aspect"])], np.float32)
        hands = np.stack([x[:, 0:42].reshape(-1, 21, 2), x[:, 42:84].reshape(-1, 21, 2)], 1) * ky
        out[i] = joint_angles(torch.from_numpy(hands)).numpy()
        seen[i] = x[:, PRES][:, :2] > 0.5
    return out, seen


def kmeans(x, k, iters=30, seed=0):
    g = torch.Generator().manual_seed(seed)
    x = torch.from_numpy(x)
    c = x[torch.randperm(len(x), generator=g)[:k]].clone()
    for _ in range(iters):
        a = torch.cdist(x, c).argmin(1)
        for j in range(k):
            m = x[a == j]
            if len(m):
                c[j] = m.mean(0)
    return c.numpy()


def labels(cache, crops, k=48):
    crops = pathlib.Path(crops)
    meta = pd.read_csv(crops / "index.csv", keep_default_na=False)
    b, seen = bends(cache, meta["row"].to_numpy())
    train = (meta["split"] == "train").to_numpy()
    fit = b[train][seen[train]]
    centres = kmeans(fit, k)
    lab = np.full(seen.shape, -1, np.int16)
    lab[seen] = torch.cdist(torch.from_numpy(b[seen]), torch.from_numpy(centres)).argmin(1).numpy()
    np.save(crops / "labels.npy", lab)
    np.save(crops / "centres.npy", centres)
    used = np.bincount(lab[lab >= 0], minlength=k)
    print(f"JOB handshape labels k={k} fit_frames={len(fit)} labelled={int(seen.sum())} "
          f"smallest_cluster={int(used.min())} largest={int(used.max())} status=done")


def block(cin, cout, stride):
    return nn.Sequential(nn.Conv2d(cin, cin, 3, stride, 1, groups=cin, bias=False), nn.BatchNorm2d(cin), nn.ReLU6(),
                         nn.Conv2d(cin, cout, 1, bias=False), nn.BatchNorm2d(cout), nn.ReLU6())


class Encoder(nn.Module):
    """96 px RGB crop to a 128-d embedding."""

    def __init__(self, dim=128):
        super().__init__()
        self.net = nn.Sequential(nn.Conv2d(3, 16, 3, 2, 1, bias=False), nn.BatchNorm2d(16), nn.ReLU6(),
                                 block(16, 32, 2), block(32, 64, 2), block(64, 64, 1), block(64, 128, 2),
                                 block(128, 128, 1), block(128, 256, 2), nn.AdaptiveAvgPool2d(1), nn.Flatten(),
                                 nn.Linear(256, dim))

    def forward(self, x):
        return self.net(x)


def augment(x, g):
    """Colour and greyscale jitter against one signer's skin and sleeves, small shifts; x (n, 3, 96, 96) in 0..1."""
    n = len(x)
    x = x * (0.7 + 0.6 * torch.rand(n, 3, 1, 1, generator=g, device=x.device))
    grey = torch.rand(n, 1, 1, 1, generator=g, device=x.device) < 0.3
    x = torch.where(grey, x.mean(1, keepdim=True).expand_as(x), x)
    dx, dy = (torch.randint(-8, 9, (2,), generator=g, device=x.device)).tolist()
    return torch.roll(x, (dy, dx), (2, 3)).clamp(0, 1)


def train(crops, out, epochs=15, seed=1, app_words=False, lr=2e-3, patience=0, balanced=False, in_ram=False):
    crops, out = pathlib.Path(crops), pathlib.Path(out)
    out.mkdir(parents=True, exist_ok=True)
    torch.manual_seed(seed)
    meta = pd.read_csv(crops / "index.csv", keep_default_na=False)
    img = np.load(crops / "crops.npy", mmap_mode=None if in_ram else "r")
    lab = np.load(crops / "labels.npy")
    ok = (meta["offset"] >= 0).to_numpy()
    if app_words:
        app = json.loads((pathlib.Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "assets" / "content" /
                          "models" / "sign_reader.json").read_text(encoding="utf-8"))["labels"]
        ok = ok & meta["label"].isin(app).to_numpy()
    words = sorted(set(meta.loc[ok & (meta["split"] == "train"), "label"]))
    wid = {w: i for i, w in enumerate(words)}
    y_word = np.array([wid.get(w, -1) for w in meta["label"]])
    tune_signer = meta["signer"].map(lambda s: int(hashlib.sha1(s.encode()).hexdigest(), 16) % 10 == 0).to_numpy()
    train_split = ok & (meta["split"] == "train").to_numpy()
    tr = np.where(train_split & ~tune_signer)[0]
    tu = np.where(train_split & tune_signer & (y_word >= 0))[0]
    va = np.where(ok & (meta["split"] == "val").to_numpy() & (y_word >= 0))[0]
    k = int(lab.max()) + 1
    dev = torch.device("cuda")
    torch.cuda.set_per_process_memory_fraction(0.35)
    enc = Encoder().to(dev)
    shape_head, word_head = nn.Linear(128, k).to(dev), nn.Linear(128, len(words)).to(dev)
    params = list(enc.parameters()) + list(shape_head.parameters()) + list(word_head.parameters())
    opt = torch.optim.AdamW(params, lr, weight_decay=0.02)
    sched = torch.optim.lr_scheduler.OneCycleLR(opt, lr, total_steps=epochs * (len(tr) // 64 + 1))
    ce = nn.CrossEntropyLoss(ignore_index=-1)
    counts = np.bincount(lab[tr][lab[tr] >= 0], minlength=k).astype(np.float32)
    shape_w = torch.tensor(counts.sum() / np.maximum(counts, 1) / k, device=dev) if balanced else None
    ce_shape = nn.CrossEntropyLoss(weight=shape_w, ignore_index=-1)
    g = torch.Generator(device=dev).manual_seed(seed)
    rng = np.random.default_rng(seed)

    def batch(rows):
        x = torch.from_numpy(np.ascontiguousarray(img[np.sort(rows)])).to(dev)  # (b, 8, 2, 96, 96, 3)
        b = len(rows)
        x = x.view(b * 16, 96, 96, 3).permute(0, 3, 1, 2).float() / 255
        return x, torch.from_numpy(lab[np.sort(rows)].astype(np.int64)).to(dev).view(-1),             torch.from_numpy(y_word[np.sort(rows)]).to(dev)

    def embed_clips(x, seen):
        e = enc(x).view(-1, 16, 128)
        w = seen.view(-1, 16, 1).float()
        return e, (e * w).sum(1) / w.sum(1).clamp(min=1)

    @torch.no_grad()
    def score(rows):
        enc.eval()
        right_s = n_s = right_w = 0
        for b in range(0, len(rows), 128):
            x, ys, yw = batch(rows[b:b + 128])
            e, clip = embed_clips(x, ys >= 0)
            m = ys >= 0
            right_s += int((shape_head(e.view(-1, 128)).argmax(1)[m] == ys[m]).sum())
            n_s += int(m.sum())
            right_w += int((word_head(clip).argmax(1) == yw).sum())
        return right_s / max(n_s, 1), right_w / max(len(rows), 1)

    best, state = None, None
    for ep in range(epochs):
        enc.train()
        rng.shuffle(tr)
        for b in range(0, len(tr), 64):
            x, ys, yw = batch(tr[b:b + 64])
            e, clip = embed_clips(augment(x, g), ys >= 0)
            loss = ce_shape(shape_head(e.view(-1, 128)), ys) + ce(word_head(clip), yw)
            opt.zero_grad()
            loss.backward()
            opt.step()
            sched.step()
        acc_s, acc_w = score(tu)
        print(f"epoch {ep + 1}: loss {loss.item():.3f} tune shape {acc_s:.3f} word {acc_w:.3f}", flush=True)
        if patience and best is not None and ep + 1 - best["epoch"] > patience:
            break
        if best is None or acc_s + acc_w > best["tune_shape"] + best["tune_word"]:
            best = {"epoch": ep + 1, "tune_shape": acc_s, "tune_word": acc_w}
            state = {k: {n: v.detach().clone() for n, v in m.state_dict().items()}
                     for k, m in (("encoder", enc), ("shape_head", shape_head), ("word_head", word_head))}
    for k, m in (("encoder", enc), ("shape_head", shape_head), ("word_head", word_head)):
        m.load_state_dict(state[k])
    best["val_shape"], best["val_word"] = score(va)
    with torch.no_grad():
        enc.eval()
        hit = []
        for b in range(0, len(va), 128):
            x, ys, yw = batch(va[b:b + 128])
            hit.append((word_head(embed_clips(x, ys >= 0)[1]).argmax(1) == yw).cpu().numpy())
    np.savez(out / "val_word.npz", rows=meta["row"].to_numpy()[np.sort(va)], right=np.concatenate(hit))
    torch.save({**state,  # nosemgrep: trailofbits.python.pickles-in-pytorch.pickles-in-pytorch
                "words": words, "k": k}, out / "encoder.pt")
    print(f"JOB handshape encoder best_epoch={best['epoch']} tune_shape={best['tune_shape']:.3f} tune_word={best['tune_word']:.3f} "
          f"val_shape={best['val_shape']:.3f} val_word={best['val_word']:.3f} train_clips={len(tr)} tune_clips={len(tu)} "
          f"val_clips={len(va)} words={len(words)} status=done")


if __name__ == "__main__":
    a = sys.argv[1:]
    opt = lambda key, d: type(d)(a[a.index(key) + 1]) if key in a else d  # noqa: E731
    if a[0] == "labels":
        labels(a[1], a[2], opt("--k", 48))
    else:
        train(a[1], a[2], opt("--epochs", 15), opt("--seed", 1), "--app-words" in a, opt("--lr", 2e-3), opt("--patience", 0),
              "--balanced" in a, "--in-ram" in a)
