"""A ported read gate (GATE_PY, the RKMVERI group's: confidence, margin, prototype cosine, still wrists, two
equal reads to confirm) on top of the installed reader, against the app's 0.4 floor, on nonsign_fa.py's windows.
Each clip gives two reads, on its first and last 85% of frames, standing in for two consecutive live windows.
Prototypes: mean unit embedding (the head's input) of each app word's training clips.

    python gate_compare.py CACHE GATE_PY
"""
import importlib.util
import json
import pathlib
import sys

import numpy as np
import pandas as pd
import torch

import exp
from cache import Clips
from features import resample_index
from models import L, PRES, R

FLOOR = 0.4  # READ_MIN_SCORE in SignChoices.kt


def main(root, gate_py):
    spec = importlib.util.spec_from_file_location("read_gate", gate_py)
    rg = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(rg)
    index = pd.read_csv(root / "index.csv", keep_default_na=False)
    model, labels, frames = exp.app_model()
    temp = json.loads((exp.APP_MODELS / "sign_reader.json").read_text(encoding="utf-8"))["temperature"]
    dev = torch.device("cuda")
    model = model.to(dev).eval()
    feats = {}
    model.head.register_forward_hook(lambda m, i, o: feats.__setitem__("e", i[0]))
    raw = np.load(root / "raw.npy", mmap_mode="r")
    st, ln, asp = index["start"].to_numpy(), index["length"].to_numpy(), index["aspect"].to_numpy(np.float32)
    names = np.array(labels)
    pos = {w: i for i, w in enumerate(labels)}
    split, src, lab = index["split"].to_numpy(), index["source"].to_numpy(), index["label"].to_numpy()

    def run(x, a):
        with torch.no_grad():
            z = model(torch.from_numpy(x).to(dev), torch.from_numpy(a).to(dev)).float()
        return (z / temp).softmax(1).cpu().numpy(), feats["e"].float().cpu().numpy()

    def windows(rows, lo, hi):
        xs, ws = [], []
        for r in rows:
            c = np.asarray(raw[st[r]:st[r] + ln[r]])
            n = len(c)
            c = c[int(lo * n):max(int(lo * n) + 2, int(hi * n))]
            xs.append(c[resample_index(len(c), frames)])
            ws.append([c[c[:, PRES.start + k] > 0][:, sl.start:sl.start + 2] for k, sl in ((0, L), (1, R))])
        return np.stack(xs).astype(np.float32), asp[rows][:, None], ws

    def batched(rows, lo=0.0, hi=1.0):
        P, E, W = [], [], []
        for i in range(0, len(rows), 256):
            x, a, w = windows(rows[i:i + 256], lo, hi)
            p, e = run(x, a)
            P.append(p), E.append(e), W.extend(w)
        return np.concatenate(P), np.concatenate(E), W

    tr = np.flatnonzero((split == "train") & np.isin(lab, labels))
    _, e_tr, _ = batched(tr)
    protos = rg.prototypes(e_tr, np.array([pos[w] for w in lab[tr]]), len(labels))
    ns = np.flatnonzero((split == "val") & (src == "nonsign"))
    signs = np.flatnonzero((split == "val") & np.isin(src, ["islrtc", "rkmvu"]) & (index["scored"].astype(str) == "True").to_numpy()
                           & np.isin(lab, labels))
    out = []
    for cfg_name, kw in (("defaults", {}), ("calibrated values", {"min_conf": 0.35, "min_proto_sim": 0.45})):
        res = {}
        for what, rows in (("nonsign", ns), ("signs", signs)):
            reads = [batched(rows, 0.0, 0.85), batched(rows, 0.15, 1.0)]
            shown, right, why = [], [], {}
            for j, r in enumerate(rows):
                g = rg.Gate(**kw)
                word = None
                for p, e, w in reads:
                    no = g.reject(p[j], e[j], protos, w[j])
                    why[no] = why.get(no, 0) + 1
                    seen = any(len(v) for v in w[j])
                    word = g.step(None if no else names[int(p[j].argmax())], seen) or word
                shown.append(word is not None)
                right.append(word == lab[r])
            res[what] = (np.mean(shown), np.mean(right), {k or "pass": v for k, v in why.items()})
        line = (f"gate ({cfg_name}): non-sign shown {res['nonsign'][0]:.3f}; new-signer app-word clips shown "
                f"{res['signs'][0]:.3f}, shown right {res['signs'][1]:.3f}; per-read reasons non-sign {res['nonsign'][2]}, "
                f"signs {res['signs'][2]}")
        print(line, flush=True)
        out.append(line)
    floor_ns = (np.concatenate([batched(ns)[0]]).max(1) >= FLOOR).mean()
    p_s, _, _ = batched(signs)
    floor_s = p_s.max(1) >= FLOOR
    base = (f"floor {FLOOR} alone: non-sign shown {floor_ns:.3f}; signs shown {floor_s.mean():.3f}, shown right "
            f"{(floor_s & (names[p_s.argmax(1)] == lab[signs])).mean():.3f}")
    print(base)
    with open(exp.EVAL, "a", encoding="utf-8") as f:
        f.write(f"\nPorted read gate on WF soup5 (T {temp}, app words; cache_v6b val, non-sign n={len(ns)}, new-signer "
                f"app-word clips n={len(signs)}; two reads per clip on its first and last 85%; prototypes from {len(tr)} "
                f"training clips): {base}. " + " ".join(out) + "\n")
    print("JOB gate_compare ok=1 failed=0 quarantined=0 status=done")


if __name__ == "__main__":
    main(pathlib.Path(sys.argv[1]), sys.argv[2])
