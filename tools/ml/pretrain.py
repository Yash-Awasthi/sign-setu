"""B12 masked pose pretraining of the B4 encoder: spans of 2, 4 and 8 frames and whole hands or the face are hidden,
and the encoder learns to rebuild the hidden normalised points. Train rows only, so no val or test signer is seen.

    python pretrain.py --cache C:/work/ml-data/cache_interim [--cache C:/work/ml-data/cache_v4 ...] [--epochs 400]
        [--out runs/B12_pretrain_v2 --init runs/B12_pretrain/encoder.pt]   (continue an encoder into a new folder)

Checkpoint every 10 epochs and on exit (atomic); resume on start. While runs/pause_pretrain exists it checkpoints and exits, so a
B row gets the GPU; runs/B12_pretrain/running exists only while it trains.
"""
import io
import pathlib
import json
import math
import sys
import time

import numpy as np
import pandas as pd
import torch
from torch import nn

import guard
from cache import Clips
from exp import RUNS, augment
from models import FACE, PRES, ConvTransformer, F, L, R

OUT = RUNS / "B12_pretrain"
PAUSE = RUNS / "pause_pretrain"
# Hideable parts: raw slice, presence index, node range. Pose stays: Points centres every clip on the shoulders.
PARTS = [(L, 0, (0, 21)), (R, 1, (21, 42)), (F, 3, (48, 48 + FACE))]


def hide(x, g, ratio=0.4, part=0.3):
    """Hidden copy of x and the hidden-node mask (B, T, N)."""
    b, t, _ = x.shape
    frames = torch.zeros(b, 1, t, device=x.device)
    for span in (2, 4, 8):
        start = (torch.rand(b, 1, t, device=x.device, generator=g) < ratio / (3 * span)).float()
        frames = frames.maximum(nn.functional.max_pool1d(nn.functional.pad(start, (span - 1, 0)), span, 1))
    frames = frames[:, 0, :, None] > 0
    nodes = frames.expand(b, t, 48 + FACE).clone()
    x = x * ~frames
    pick = torch.randint(0, len(PARTS), (b,), device=x.device, generator=g)
    on = torch.rand(b, device=x.device, generator=g) < part
    for i, (sl, k, (a, z)) in enumerate(PARTS):
        m = (on & (pick == i)).view(b, 1, 1)
        x[..., sl] = x[..., sl] * ~m
        x[..., PRES.start + k] = x[..., PRES.start + k] * ~m[..., 0]
        nodes[:, :, a:z] |= m
    return x, nodes


def save(path, obj):
    buf = io.BytesIO()
    torch.save(obj, buf)  # nosemgrep: trailofbits.python.pickles-in-pytorch.pickles-in-pytorch
    guard.write_atomic(path, buf.getvalue())


def main():
    global OUT
    a = sys.argv[1:]
    caches = [a[i + 1] for i, k in enumerate(a) if k == "--cache"]
    OUT = pathlib.Path(a[a.index("--out") + 1]) if "--out" in a else OUT
    epochs = int(a[a.index("--epochs") + 1]) if "--epochs" in a else 400
    dev = torch.device("cuda")
    torch.cuda.set_per_process_memory_fraction(float(a[a.index("--vram") + 1]) if "--vram" in a else 0.35)
    # Train rows of every cache held on the GPU and batched there (exp.GpuBatches): no loader workers to wait on.
    from exp import GpuBatches
    banks = []
    for c in caches:
        idx = pd.read_csv(f"{c}/index.csv", keep_default_na=False)
        banks.append(GpuBatches(pathlib.Path(c), idx, np.flatnonzero(idx["split"].to_numpy() == "train"), dev))
    bs = int(a[a.index("--batch") + 1]) if "--batch" in a else 512
    total_rows = sum(len(b.rows) for b in banks)

    class Steps:
        def __len__(self):
            return max(1, total_rows // bs)

        def __iter__(self):
            for _ in range(len(self)):
                b = banks[int(torch.randint(len(banks), (1,), generator=g_cpu))]
                x, asp, rows = b.batch(torch.randint(len(b.rows), (bs,), device=dev, generator=g), g)
                yield x, asp, rows
    g_cpu = torch.Generator().manual_seed(1)
    dl = Steps()
    data = range(total_rows)
    enc = ConvTransformer(classes=1).to(dev)
    dec = nn.Linear(192, (48 + FACE) * 2).to(dev)
    opt = torch.optim.AdamW(list(enc.parameters()) + list(dec.parameters()), lr=1e-3, weight_decay=0.05)
    epoch = 0
    if (OUT / "last.pt").exists():
        ck = torch.load(OUT / "last.pt", weights_only=True)  # nosemgrep: trailofbits.python.pickles-in-pytorch.pickles-in-pytorch
        enc.load_state_dict(ck["encoder"])
        dec.load_state_dict(ck["decoder"])
        opt.load_state_dict(ck["opt"])
        epoch = ck["epoch"]
    elif "--init" in a:
        # A new run from a finished encoder: its weights, a fresh decoder, optimiser and schedule.
        enc.load_state_dict(torch.load(a[a.index("--init") + 1], weights_only=True)["encoder"])  # nosemgrep: trailofbits.python.pickles-in-pytorch.pickles-in-pytorch
    steps, warm = epochs * len(dl), 5 * len(dl)
    g = torch.Generator(device=dev).manual_seed(1 + epoch)
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "running").touch()
    status = "done"
    print(f"pretrain on {len(data)} train clips from {caches}, epoch {epoch}/{epochs}", flush=True)

    def checkpoint():
        save(OUT / "last.pt", {"encoder": enc.state_dict(), "decoder": dec.state_dict(), "opt": opt.state_dict(),
                               "epoch": epoch, "caches": caches})
        save(OUT / "encoder.pt", {"encoder": enc.state_dict(), "epoch": epoch, "caches": caches})
    try:
        while epoch < epochs:
            # Low RAM sheds landmark workers instead; only a full disk or a B row stops pretraining.
            if (s := guard.check(OUT)) == "paused_disk" or PAUSE.exists():
                status = s if s == "paused_disk" else "paused_for_b_row"
                break
            enc.train()
            t0, total, n = time.time(), 0.0, 0
            for i, (raw, aspect, _) in enumerate(dl):
                step = epoch * len(dl) + i
                for p in opt.param_groups:
                    p["lr"] = 1e-3 * min(1.0, (step + 1) / warm) * 0.5 * (1 + math.cos(math.pi * min(1.0, step / steps)))
                aspect = aspect.view(-1, 1)
                with torch.no_grad():
                    full = augment(raw, aspect, {}, g)
                    target, present, _ = enc.points(full, aspect)
                    seen, hidden = hide(full, g)
                    w = (hidden & (present > 0)).float()
                with torch.autocast("cuda", dtype=torch.bfloat16):
                    pred = dec(enc.frames(seen, aspect)).float().view_as(target)
                err = nn.functional.smooth_l1_loss(pred, target, reduction="none", beta=0.1).sum(-1)
                loss = (err * w).sum() / w.sum().clamp(min=1)
                if not torch.isfinite(loss):
                    status = "failed_nan"
                    raise SystemExit
                opt.zero_grad(set_to_none=True)
                loss.backward()
                nn.utils.clip_grad_norm_(enc.parameters(), 1.0)
                opt.step()
                total, n = total + loss.item(), n + 1
            epoch += 1
            if epoch % 10 == 0:  # an epoch is a couple of seconds; saving each one cost more than the training
                checkpoint()
            with open(OUT / "log.jsonl", "a", encoding="utf-8") as f:
                f.write(json.dumps({"epoch": epoch, "loss": total / max(n, 1), "s": round(time.time() - t0, 1)}) + "\n")
            print(f"epoch {epoch} loss {total / max(n, 1):.4f} {time.time() - t0:.0f} s", flush=True)
    except KeyboardInterrupt:
        status = "interrupted"
    finally:
        if status != "failed_nan" and epoch:
            checkpoint()
        (OUT / "running").unlink(missing_ok=True)
        print(f"JOB B12_pretrain ok={epoch} failed=0 quarantined=0 status={status}", flush=True)


if __name__ == "__main__":
    main()
