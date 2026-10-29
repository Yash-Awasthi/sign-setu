"""Stage E: sub-centre ArcFace embedding on the B winner, prototypes for every training word (dictionary words outside
the classifier and home signs included), and one-clip retrieval of held-out words on validation signers.

    python embed.py [--cache C:/work/ml-data/cache_v4] [--epochs 20] [--run B4_s1]

Writes runs/E_<run>/embed.pt (trunk + projection), prototypes.npz (word, vector) and one EVAL.md row.
"""
import json
import pathlib
import sys

import numpy as np
import pandas as pd
import torch
import torch.nn.functional as F
from torch import nn

from exp import EVAL, HERE, RUNS, Clips, augment, bootstrap, make_model, verify
from guard import DATA


class SubCentreArcFace(nn.Module):
    """K centres per word; the nearest centre carries the additive angular margin, so a word with two signing
    variants is not forced onto one point."""

    def __init__(self, dim, classes, k=3, s=30.0, m=0.3, hard=0.0):
        super().__init__()
        self.w = nn.Parameter(torch.randn(classes * k, dim) * 0.01)
        self.k, self.s, self.m, self.hard = k, s, m, hard

    def forward(self, z, y):
        cos = (F.normalize(z) @ F.normalize(self.w).T).view(len(z), -1, self.k).amax(2)
        theta = torch.acos(cos.clamp(-1 + 1e-6, 1 - 1e-6))
        hit = F.one_hot(y, cos.shape[1]).bool()
        out = torch.where(hit, torch.cos(theta + self.m), cos)
        if self.hard:
            # Hard negatives: the 3 nearest wrong words (look-alikes) count as closer than they are.
            near = cos.masked_fill(hit, -2).topk(3, 1).indices
            out = out.scatter_add(1, near, torch.full(near.shape, self.hard, device=z.device, dtype=out.dtype))
        return F.cross_entropy(self.s * out, y)


class Embedder(nn.Module):
    def __init__(self, trunk, dim=256):
        super().__init__()
        self.trunk = trunk
        self.trunk.head = nn.Identity()
        self.proj = nn.LazyLinear(dim)

    def forward(self, raw, aspect):
        return F.normalize(self.proj(self.trunk(raw, aspect)))


def embed_all(model, ds, dev):
    model.eval()
    out = []
    with torch.no_grad():
        for x, aspect, _ in torch.utils.data.DataLoader(ds, batch_size=256, num_workers=2):
            out.append(model(x.to(dev), aspect[:, None].to(dev)).float().cpu())
    return torch.cat(out).numpy() if out else np.zeros((0, 256), np.float32)


def main():
    a = sys.argv[1:]
    opt = lambda k, d: type(d)(a[a.index(k) + 1]) if k in a else d  # noqa: E731
    root = pathlib.Path(opt("--cache", str(DATA / "cache_v4")))
    epochs = opt("--epochs", 20)
    best = json.loads((RUNS / "best.json").read_text())
    rid = opt("--run", f"{best['id']}_s1")
    exps = json.loads((HERE / "experiments.json").read_text())
    cfg = exps[rid.split("_s")[0]]
    while cfg.get("parent") and "arch" not in cfg:
        cfg = {**exps[cfg["parent"]], **cfg, "parent": exps[cfg["parent"]].get("parent")}
    dev = torch.device("cuda")
    torch.cuda.set_per_process_memory_fraction(0.35)  # shares the card with a B row
    verify(root)
    index = pd.read_csv(root / "index.csv", keep_default_na=False)
    vocab = json.loads((root / "vocab.json").read_text(encoding="utf-8"))
    split = index["split"].to_numpy()
    real = index["source"].to_numpy() != "nonsign"
    tr = np.flatnonzero((split == "train") & real)
    words = sorted(set(index["label"].iloc[tr]))
    cls = {w: i for i, w in enumerate(words)}
    ys = torch.as_tensor([cls[w] for w in index["label"].iloc[tr]])

    trunk = make_model({**cfg, "init": None}, len(vocab), vocab)
    trunk.load_state_dict(torch.load(RUNS / rid / "best.pt", weights_only=True))
    model = Embedder(trunk).to(dev)
    frames = cfg.get("frames", 48)
    model(torch.zeros(2, frames, 218, device=dev), torch.ones(2, 1, device=dev))
    hard = float(a[a.index("--hard") + 1]) if "--hard" in a else 0.0
    arc = SubCentreArcFace(256, len(words), hard=hard).to(dev)
    opt_ = torch.optim.AdamW([{"params": model.trunk.parameters(), "lr": 1e-4},
                              {"params": list(model.proj.parameters()) + list(arc.parameters()), "lr": 1e-3}],
                             weight_decay=0.05)
    ds = Clips(root, tr, train=True, t=frames)
    pos = {r: i for i, r in enumerate(tr)}
    g = torch.Generator(device=dev).manual_seed(1)
    out = RUNS / (f"E_{rid}" + ("_hard" if hard else ""))
    out.mkdir(parents=True, exist_ok=True)
    for epoch in range(epochs):
        model.train()
        if cfg.get("from_app"):
            model.trunk.bn1.eval()  # folded into the app's weights: identities
            model.trunk.bn2.eval()
        total, n = 0.0, 0
        for x, aspect, rows in torch.utils.data.DataLoader(ds, batch_size=128, shuffle=True, num_workers=2, drop_last=True):
            x, aspect = x.to(dev), aspect[:, None].to(dev)
            y = ys[[pos[int(r)] for r in rows]].to(dev)
            with torch.autocast("cuda", dtype=torch.bfloat16):
                z = model(augment(x, aspect, cfg, g), aspect)
            loss = arc(z.float(), y)
            opt_.zero_grad(set_to_none=True)
            loss.backward()
            opt_.step()
            total, n = total + loss.item(), n + 1
        print(f"epoch {epoch + 1} loss {total / max(n, 1):.3f}", flush=True)
    torch.save(model.state_dict(), out / "embed.pt")

    gal = embed_all(model, Clips(root, tr, train=False, t=frames), dev)
    lab = index["label"].iloc[tr].to_numpy()
    protos = np.stack([gal[lab == w].mean(0) for w in words])
    protos /= np.linalg.norm(protos, axis=1, keepdims=True)
    np.savez(out / "prototypes.npz", words=np.array(words), vectors=protos.astype(np.float32))

    # One-clip retrieval: validation clips of words that training holds from another signer, against all prototypes.
    val = np.flatnonzero((split == "val") & real & index["label"].isin(set(words)).to_numpy())
    q = embed_all(model, Clips(root, val, train=False, t=frames), dev)
    rank = np.argsort(-(q @ protos.T), 1)[:, :5]
    truth = np.array([cls[w] for w in index["label"].iloc[val]])
    top1, top5 = rank[:, 0] == truth, (rank == truth[:, None]).any(1)
    # One-clip words: the slice the classifier cannot learn; compared with the classifier's own top-1 on it.
    once = index.loc[tr, "label"].value_counts()
    outside = index["label"].iloc[val].isin(set(once.index[once == 1])).to_numpy()
    sig = index["signer"].iloc[val].to_numpy()
    lo, hi = bootstrap(top1, sig) if len(val) else (np.nan, np.nan)
    res = {"n": int(len(val)), "top1": float(top1.mean()) if len(val) else np.nan, "lo": lo, "hi": hi,
           "top5": float(top5.mean()) if len(val) else np.nan, "n_outside": int(outside.sum()),
           "top1_outside": float(top1[outside].mean()) if outside.any() else np.nan, "words": len(words)}
    (out / "metrics.json").write_text(json.dumps(res, indent=1), encoding="utf-8")
    if "### E. Embedding" not in EVAL.read_text(encoding="utf-8"):
        with open(EVAL, "a", encoding="utf-8") as f:
            f.write("\n### E. Embedding\n\n| trunk | words | val clips | retrieval top-1 [95%] / top-5 | outside classifier: n, top-1 |\n"
                    "|---|---|---|---|---|\n")
    with open(EVAL, "a", encoding="utf-8") as f:
        f.write(f"| {rid} | {len(words)} | {res['n']} | {res['top1']:.3f} [{lo:.3f}, {hi:.3f}] / {res['top5']:.3f} | "
                f"{res['n_outside']}, {res['top1_outside']:.3f} |\n")
    print(f"JOB E ok=1 failed=0 quarantined=0 status=done top1={res['top1']:.4f}", flush=True)


if __name__ == "__main__":
    main()
