"""A continuous ISL fingerspelling reader: CTC over both hands, on ISL-Fingerspelling (prepare_isl_fingerspell.py),
scored on its held-out signer. Same network as the ASL speller (spell_ctc.py); CtcSpellReader.kt reads it with
"input": "two_hands".

    python isl_spell_ctc.py train <isl_fingerspell.pkl> <out.pt> [epochs]
    python isl_spell_ctc.py eval <isl_fingerspell.pkl> <model.pt>
    python isl_spell_ctc.py gate <isl_fingerspell.pkl> <words.pkl> <model.pt>   # how often word signs read as spelling
    python isl_spell_ctc.py export <isl_fingerspell.pkl> <model.pt> <min_peak>     # to the content bundle, with a fixture
"""
import json
import pathlib
import os
import pickle
import sys

import numpy as np
import torch
from torch import nn

import runtime
from export import MODELS, RESOURCES, folded, frames_json, rounded
from features import spell_resample
from spell_ctc import edit_distance, rot

ALPHABET = "abcdefghijklmnopqrstuvwxyz "
HIDDEN, KERNEL = int(os.environ.get("ISPELL_HIDDEN", 256)), 5
DILATIONS = tuple(int(d) for d in os.environ.get("ISPELL_DILATIONS", "1,2,4,8,1,2").split(","))
# Word sign clips (a format 1 training cache) taught as no letters at all, a quarter of each batch; without them
# one word sign in twenty reads as a confident spelling.
NEGATIVES = os.environ.get("ISPELL_NEGATIVES")
FEATURES = 2 * 42 + 2 + 4 + 4 + 2  # two hand shapes, right wrist from left, wrists from the chest, wrist steps, presence


def isl_spell_features(ts, left, right, pose, has_left, has_right, has_pose, aspect):
    """Both hands per frame, x and y with y scaled by height/width: each hand from its wrist in its own wrist to
    middle-knuckle lengths, the right wrist from the left, both wrists from the shoulder midpoint in shoulder widths,
    and each wrist's step since the previous frame. Returns (frames, FEATURES)."""
    k = np.array([1.0, 1.0 / aspect])
    seen = has_pose.nonzero()[0]
    if len(seen):
        sh = pose[seen][:, [11, 12]] * k
        chest, width = sh.mean((0, 1)), float(np.linalg.norm(sh[:, 0] - sh[:, 1], axis=1).mean())
    else:
        chest, width = np.zeros(2), 0.0
    out = np.zeros((len(idx := spell_resample(ts)), FEATURES), np.float32)
    prev = [None, None]
    for t, j in enumerate(idx):
        wrists = [None, None]
        for h, (hand, has) in enumerate(((left, has_left), (right, has_right))):
            p = hand[j] * k if has[j] else None
            size = float(np.hypot(*(p[9] - p[0]))) if p is not None else 0.0
            if size < 1e-6:
                prev[h] = None
                continue
            out[t, 42 * h:42 * h + 42] = ((p - p[0]) / size).reshape(-1)
            if width > 1e-6:
                out[t, 86 + 2 * h:88 + 2 * h] = (p[0] - chest) / width
                if prev[h] is not None:
                    out[t, 90 + 2 * h:92 + 2 * h] = (p[0] - prev[h]) / width
            out[t, 94 + h] = 1
            prev[h], wrists[h] = p[0], p[0]
        if wrists[0] is not None and wrists[1] is not None and width > 1e-6:
            out[t, 84:86] = (wrists[1] - wrists[0]) / width
    return out


class IslSpellNet(nn.Module):
    """Per-frame linear layer, residual dilated convolutions, per-frame logits; class 0 is the CTC blank."""

    def __init__(self):
        super().__init__()
        self.embed = nn.Linear(FEATURES, HIDDEN)
        self.convs = nn.ModuleList(nn.Conv1d(HIDDEN, HIDDEN, KERNEL, padding=d * (KERNEL // 2), dilation=d) for d in DILATIONS)
        self.bns = nn.ModuleList(nn.BatchNorm1d(HIDDEN) for _ in DILATIONS)
        self.drop = nn.Dropout(0.2)
        self.head = nn.Linear(HIDDEN, len(ALPHABET) + 1)

    def forward(self, x):
        h = torch.relu(self.embed(x)).transpose(1, 2)
        for conv, bn in zip(self.convs, self.bns):
            h = h + torch.relu(bn(conv(self.drop(h))))
        return self.head(h.transpose(1, 2))


def target(s):
    if "text" not in s:
        return ""
    return "".join(c for c in s["text"].lower() if c in ALPHABET).strip()


def timestamps(s):
    return (np.arange(len(s["pose"])) * 1000 / s["fps"]).astype(np.int64)


def augmented(s, rng):
    """Mirrored half the time (a left-handed signer swaps the hands), rotated slightly, scaled, played 0.8 to 1.25
    times as fast, and each hand lost for a few frames."""
    k = np.array([1.0, 1.0 / s["aspect"]])
    left, right, pose = s["left"], s["right"], s["pose"]
    hl, hr = s["has_left"], s["has_right"]
    if rng.random() < 0.5:
        flip = lambda a: np.stack([1 - a[..., 0], a[..., 1]], -1)
        left, right, hl, hr = flip(right), flip(left), hr, hl
        pose = flip(pose)[:, [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 11] + list(range(13, pose.shape[1]))]
    r = rot(rng.normal(0, 0.1)) * rng.uniform(0.9, 1.1)
    left, right, pose = ((a[..., :2] * k) @ r.T / k for a in (left, right, pose))
    hl = hl & (rng.random(len(hl)) >= rng.uniform(0, 0.15))
    hr = hr & (rng.random(len(hr)) >= rng.uniform(0, 0.15))
    ts = (timestamps(s) * rng.uniform(0.8, 1.25)).astype(np.int64)
    return isl_spell_features(ts, left, right, pose, hl, hr, s["has_pose"], s["aspect"])


def plain(s):
    return isl_spell_features(timestamps(s), s["left"][..., :2], s["right"][..., :2], s["pose"][..., :2],
                              s["has_left"], s["has_right"], s["has_pose"], s["aspect"])


def greedy(logits):
    best = logits.argmax(-1)
    return "".join(ALPHABET[c - 1] for i, c in enumerate(best) if c != 0 and (i == 0 or c != best[i - 1]))


def scores(model, samples, dev):
    model.eval()
    errors = chars = errors_ns = chars_ns = exact = 0
    with torch.no_grad():
        for s in samples:
            got, want = greedy(model(torch.tensor(plain(s)[None]).to(dev))[0].float().cpu().numpy()).strip(), target(s)
            errors += edit_distance(got, want)
            chars += len(want)
            errors_ns += edit_distance(got.replace(" ", ""), want.replace(" ", ""))
            chars_ns += len(want.replace(" ", ""))
            exact += got == want
    return errors / chars, errors_ns / chars_ns, exact / len(samples)


def load(path):
    samples = [s for s in pickle.load(open(path, "rb")) if target(s)]
    return [s for s in samples if s["split"] == "train"], [s for s in samples if s["split"] == "test"]


def train(path, out, epochs=200):
    tr, te = load(path)
    neg = []
    if NEGATIVES:
        words = pickle.load(open(NEGATIVES, "rb"))
        neg = [dict(s, fps=25, aspect=s.get("aspect") or words["aspect"]) for s in words["samples"] if s["split"] == "train"]
    dev = torch.device("cuda")
    rng = np.random.default_rng(7)
    torch.manual_seed(7)
    model = IslSpellNet().to(dev)
    opt = torch.optim.AdamW(model.parameters(), lr=2e-3, weight_decay=0.01)
    per_epoch = (len(tr) + 31) // 32
    sched = torch.optim.lr_scheduler.OneCycleLR(opt, max_lr=2e-3, total_steps=epochs * per_epoch)
    ctc = nn.CTCLoss(blank=0, zero_infinity=True)
    for epoch in range(epochs):
        model.train()
        for _ in range(per_epoch):
            pick = [tr[i] for i in rng.integers(len(tr), size=24 if neg else 32)]
            pick += [neg[i] for i in rng.integers(len(neg), size=8)] if neg else []
            feats = [augmented(s, rng) for s in pick]
            x = np.zeros((32, max(len(f) for f in feats), FEATURES), np.float32)
            for i, f in enumerate(feats):
                x[i, :len(f)] = f
            targets = [[ALPHABET.index(c) + 1 for c in target(s)] for s in pick]
            logp = model(torch.tensor(x).to(dev)).log_softmax(-1).transpose(0, 1)
            loss = ctc(logp, torch.tensor([c for t in targets for c in t], device=dev),
                       torch.tensor([len(f) for f in feats]), torch.tensor([len(t) for t in targets]))
            opt.zero_grad()
            loss.backward()
            opt.step()
            sched.step()
        if epoch % 20 == 19 or epoch == epochs - 1:
            cer, cer_ns, exact = scores(model, te, dev)
            print(f"epoch {epoch + 1}: loss {loss.item():.3f} held-out signer CER {cer:.4f} "
                  f"(letters only {cer_ns:.4f}, exact {exact:.4f}, n={len(te)})", flush=True)
    ckpt = {"state": model.cpu().state_dict(), "cer": cer, "cer_letters": cer_ns, "epochs": epochs, "negatives": bool(neg)}
    torch.save(ckpt, out)  # nosemgrep: trailofbits.python.pickles-in-pytorch.pickles-in-pytorch


def evaluate(path, model_path):
    _, te = load(path)
    model = IslSpellNet()
    model.load_state_dict(torch.load(model_path, weights_only=True)["state"])  # nosemgrep: trailofbits.python.pickles-in-pytorch.pickles-in-pytorch
    dev = torch.device("cuda")
    model.to(dev)
    print("held-out signer CER {:.4f} letters only {:.4f} exact {:.4f}".format(*scores(model, te, dev)), f"(n={len(te)})")
    for s in te[:15]:
        with torch.no_grad():
            print(f"  {target(s)!r} -> {greedy(model(torch.tensor(plain(s)[None]).to(dev))[0].cpu().numpy())!r}")


def peaks(logits):
    """SignReaders.kt's CtcSpell: per letter read, its best softmax probability over the frames it spans."""
    p = np.exp(logits - logits.max(1, keepdims=True))
    p /= p.sum(1, keepdims=True)
    out, last = [], 0
    for row in p:
        best = int(row.argmax())
        if best and best != last:
            out.append(row[best])
        elif best:
            out[-1] = max(out[-1], row[best])
        last = best
    return out


def gate(path, words_path, model_path, letters=3):
    _, te = load(path)
    words = pickle.load(open(words_path, "rb"))
    clips = [dict(s, fps=25, aspect=s.get("aspect") or words["aspect"]) for s in words["samples"] if s["split"] == "test"]
    model = IslSpellNet()
    model.load_state_dict(torch.load(model_path, weights_only=True)["state"])  # nosemgrep: trailofbits.python.pickles-in-pytorch.pickles-in-pytorch
    model.eval()
    with torch.no_grad():
        run = lambda s: model(torch.tensor(plain(s)[None]))[0].numpy()
        spelled = [(peaks(l := run(s)), greedy(l).strip() == target(s)) for s in te]
        signed = [peaks(run(s)) for s in clips]
    for peak in (0.5, 0.6, 0.7, 0.8, 0.9):
        ok = lambda q: len(q) >= letters and min(q) >= peak
        print(f"peak {peak}: word signs read as spelling {np.mean([ok(q) for q in signed]):.4f} (n={len(signed)}), "
              f"spelled passed {np.mean([ok(q) for q, _ in spelled]):.4f}, passed and exact "
              f"{np.mean([ok(q) and e for q, e in spelled]):.4f} (n={len(spelled)})")


def export(path, model_path, min_peak):
    ckpt = torch.load(model_path, weights_only=True)  # nosemgrep: trailofbits.python.pickles-in-pytorch.pickles-in-pytorch
    model = IslSpellNet()
    model.load_state_dict(ckpt["state"])
    model.eval()
    with torch.no_grad():
        tensors = [model.embed.weight, model.embed.bias]
        for conv, bn in zip(model.convs, model.bns):
            tensors += list(folded(conv, bn))
        tensors += [model.head.weight, model.head.bias]
        blob = b"".join(t.detach().numpy().astype("<f2").tobytes() for t in tensors)
    (MODELS / "isl_spell_ctc.bin").write_bytes(blob)
    spec = {
        "format": 2, "input": "two_hands",
        "architecture": "per-frame linear+relu; residual blocks h += relu(conv1d(h)) (same padding, dilated, batch "
                        "norm folded); per-frame linear to logits; class 0 is the CTC blank, greedy decoding",
        "fps": 15, "max_frames": 300, "features": FEATURES, "hidden": HIDDEN, "kernel": KERNEL,
        "dilations": list(DILATIONS), "alphabet": ALPHABET, "min_peak": float(min_peak),
        "tensors": [{"shape": list(t.shape)} for t in tensors],
        "dtype": "float16",
        "weights": "float16 little-endian, tensors in the order above, row-major; conv weight is [out][in][kernel]",
        "feature_spec": "frames resampled to `fps` by nearest timestamp (ties to the earlier), at most `max_frames`; "
                        "y scaled by height/width; per frame: left then right hand, 21x(x,y) minus that wrist divided "
                        "by its wrist to middle-knuckle length (84); right wrist minus left wrist (2); each wrist minus "
                        "the shoulder midpoint (4) and each wrist's step since the previous frame with that hand (4), "
                        "these three in shoulder widths averaged over every frame with a pose; presence flags (2); a "
                        "missing hand is zeros",
        "dataset": "ISL-Fingerspelling (ISH News, WSLP 2025, huggingface.co ISL Fingerspelling Dataset, CC BY-NC 4.0), "
                   "signers 00 and 01; INCLUDE and Kaggle ISL word clips as non-spelling; landmarks by MediaPipe "
                   "Holistic",
        "epochs": ckpt.get("epochs"), "held_out_signer_cer": round(ckpt["cer"], 4),
    }
    (MODELS / "isl_spell_ctc.json").write_text(json.dumps(spec, indent=1) + "\n", encoding="utf-8", newline="\n")
    print(f"isl_spell_ctc: weights {len(blob)} bytes")

    # The fixture is what the shipped half-precision file computes, so Kotlin must match it exactly.
    _, shipped = runtime.speller("isl_spell_ctc")
    _, te = load(path)
    cases = []
    for s in (te[0], te[100], te[200]):
        s = rounded(s)
        ts = timestamps(s)
        logits = shipped(plain(s).astype(np.float64))
        frames = [dict(f, t=int(t)) for f, t in zip(frames_json(s), ts)]
        cases.append({"text": target(s), "decoded": greedy(logits), "aspect": s["aspect"], "frames": frames,
                      "logits": [[round(float(v), 5) for v in row] for row in logits]})
    (RESOURCES / "isl_spell_ctc_fixture.json").write_text(json.dumps({"cases": cases}), encoding="utf-8")


if __name__ == "__main__":
    a = sys.argv[1:]
    if a[0] == "train":
        train(a[1], a[2], int(a[3]) if len(a) > 3 else 200)
    elif a[0] == "gate":
        gate(*a[1:])
    elif a[0] == "export":
        export(*a[1:])
    else:
        evaluate(*a[1:])
