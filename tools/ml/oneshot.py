"""Adding a sign from one video: the reader's pooled features as an embedding, one prototype per word, nearest by
cosine. Experimental, not shipped.

    python oneshot.py split <cache.pkl> <islrtc.pkl> <out.pkl> [words]   # hold words with dictionary clips out of training
    python oneshot.py eval <cache.pkl> <islrtc.pkl> <held.pkl> <reader.pt>
"""
import os
import pickle
import sys

import numpy as np
import torch

import train as conv

COPIES = int(os.environ.get("ONESHOT_COPIES", 0))
DTW = os.environ.get("ONESHOT_DTW")  # score HomeSigns.kt's raw-feature matcher instead, new words among new only


def split(cache_path, islrtc_path, out, n=60):
    cache = pickle.load(open(cache_path, "rb"))
    dictionary = {s["label"] for s in pickle.load(open(islrtc_path, "rb"))["samples"]}
    words = sorted({s["label"] for s in cache["samples"]} & dictionary)
    held = sorted(np.random.default_rng(7).choice(words, int(n), replace=False).tolist())
    cache["held"] = held
    cache["samples"] = [s for s in cache["samples"] if s["split"] != "train" or s["label"] not in held]
    pickle.dump(cache, open(out, "wb"))
    print(len(held), "held out:", " ".join(held))


def embed(model, samples, aspect, rng=None):
    model.eval()
    with torch.no_grad():
        x = conv.batch(samples, aspect, rng)
        h = torch.relu(model.embed(x)).transpose(1, 2)
        h = torch.relu(model.bn1(model.conv1(h)))
        h = torch.relu(model.bn2(model.conv2(h)))
        return torch.nn.functional.normalize(torch.cat([h.mean(2), h.amax(2)], 1), dim=1)


def one_clip(model, clips, aspect):
    # Training augmentation (mirror, time, rotation, hand placement) fills in how other signers vary the one clip.
    if not COPIES:
        return embed(model, clips, aspect)
    rng = np.random.default_rng(3)
    return torch.nn.functional.normalize(
        torch.stack([embed(model, clips, aspect, rng) for _ in range(COPIES)]).mean(0), dim=1)


def dtw(a, b, band=4):
    """HomeSigns.kt's matcher: banded time warping, mean per-frame distance along the best path; lower is closer."""
    n = len(a)
    d = np.linalg.norm(a[:, None] - b[None], axis=2)
    cost = np.full((n + 1, n + 1), np.inf)
    cost[0, 0] = 0
    for i in range(1, n + 1):
        for j in range(max(1, i - band), min(n, i + band) + 1):
            cost[i, j] = d[i - 1, j - 1] + min(cost[i - 1, j], cost[i, j - 1], cost[i - 1, j - 1])
    return cost[n, n] / n


def score(name, e, labels, protos, names):
    top = (e @ protos.T if DTW is None else -torch.tensor([[dtw(a, b) for b in protos] for a in e])).topk(3, 1).indices
    y = torch.tensor([names.index(l) for l in labels])
    print(f"{name}: top-1 {(top[:, 0] == y).float().mean():.4f} top-3 {(top == y[:, None]).any(1).float().mean():.4f} "
          f"(n={len(labels)})")


def evaluate(cache_path, islrtc_path, held_path, reader_path):
    full = pickle.load(open(cache_path, "rb"))
    held = pickle.load(open(held_path, "rb"))["held"]
    aspect = full["aspect"]
    ckpt = torch.load(reader_path, weights_only=True)  # nosemgrep: trailofbits.python.pickles-in-pytorch.pickles-in-pytorch
    model = conv.SignNet(len(ckpt["labels"]))
    model.load_state_dict(ckpt["state"])
    dictionary = {}
    for s in pickle.load(open(islrtc_path, "rb"))["samples"]:
        dictionary.setdefault(s["label"], s)  # one dictionary clip per word
    train = [s for s in full["samples"] if s["split"] == "train"]
    words = sorted({s["label"] for s in full["samples"]})
    seen = [w for w in words if w not in held]
    # Seen words: mean of their training clips. Held words: one clip, from the dictionary or from one INCLUDE signer.
    seen_protos = torch.stack([embed(model, [s for s in train if s["label"] == w], aspect).mean(0) for w in seen])
    by_dict, by_one = (one_clip(model, [pick(w) for w in held], aspect) for pick in (
        dictionary.get, lambda w: next(s for s in train if s["label"] == w)))
    for split_name in ("test", "kaggle_test"):
        test = [s for s in full["samples"] if s["split"] == split_name]
        new = [s for s in test if s["label"] in held]
        old = [s for s in test if s["label"] not in held]
        if not new:
            continue
        if DTW:
            raw = lambda clips: conv.batch(clips, aspect).numpy()
            for how, pick in (("dictionary clip", dictionary.get), ("one signer clip", lambda w: next(
                    s for s in train if s["label"] == w))):
                score(f"{split_name} new words among new, {how}, time warping", raw(new), [s["label"] for s in new],
                      raw([pick(w) for w in held]), held)
            continue
        e_new, e_old = embed(model, new, aspect), embed(model, old, aspect)
        y_new, y_old = [s["label"] for s in new], [s["label"] for s in old]
        for how, protos in (("dictionary clip", by_dict), ("one signer clip", by_one)):
            score(f"{split_name} new words among new, {how}", e_new, y_new, protos, held)
            everything = torch.nn.functional.normalize(torch.cat([seen_protos, protos]), dim=1)
            score(f"{split_name} new words among all, {how}", e_new, y_new, everything, seen + held)
            score(f"{split_name} old words among all, {how}", e_old, y_old, everything, seen + held)


if __name__ == "__main__":
    a = sys.argv[1:]
    split(*a[1:]) if a[0] == "split" else evaluate(*a[1:])
