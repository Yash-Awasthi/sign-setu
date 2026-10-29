"""Training cache for the ISL letter reader: real photographs plus synthetic poses in train, real held-out signers in test.

    python mix_isl_spell.py <isl_real.pkl> <synthetic.pkl> <out.pkl> [names.txt]

With names.txt, each train letter also appears inside spelled names: a held letter with the end of the one before and
the start of the one after, the held letter taking 50 to 80% of the window, as in slow spelling of a real name.
"""
import pickle
import sys

import numpy as np

REPEAT = 8
CONTEXT_PER_NAME = 2
KEYS = ("left", "right", "pose", "has_left", "has_right", "has_pose")


def stretch(s: dict, n: int) -> dict:
    idx = np.linspace(0, len(s["pose"]) - 1, n).round().astype(int)
    return {k: s[k][idx] for k in KEYS}


def in_name(prev: dict | None, cur: dict, nxt: dict | None, rng) -> dict:
    """[cur] held for most of the window, with the edges of its neighbours in a name around it."""
    held = 22
    edge = int(round(held * (1 / rng.uniform(0.5, 0.8) - 1) / 2))
    parts = ([stretch(prev, edge * 3)] if prev and edge else []) + [stretch(cur, held)] + ([stretch(nxt, edge * 3)] if nxt and edge else [])
    if prev and edge:
        parts[0] = {k: v[-edge:] for k, v in parts[0].items()}
    if nxt and edge:
        parts[-1] = {k: v[:edge] for k, v in parts[-1].items()}
    return {**cur, **{k: np.concatenate([p[k] for p in parts]) for k in KEYS}}


def names_context(samples: list, names_path: str, rng) -> list:
    by_label = {}
    for s in samples:
        if s["split"] == "train":
            by_label.setdefault(s["label"], []).append(s)
    names = [l.split("\t")[0].strip().lower() for l in open(names_path, encoding="utf-8") if l.strip() and not l.startswith("#")]
    out = []
    for name in names:
        letters = [c for c in name if c in by_label]
        for _ in range(CONTEXT_PER_NAME):
            pick = [by_label[c][rng.integers(len(by_label[c]))] for c in letters]
            for i, cur in enumerate(pick):
                out.append(in_name(pick[i - 1] if i else None, cur, pick[i + 1] if i + 1 < len(pick) else None, rng))
    return out


def main(real_path: str, synth_path: str, out: str, names_path: str | None = None) -> None:
    real = pickle.load(open(real_path, "rb"))["samples"]
    synth = pickle.load(open(synth_path, "rb"))
    samples = []
    for s in real:
        # One aspect for the whole cache: fold each photograph's own aspect into y.
        k = np.array([1.0, 1.0 / s["aspect"]], np.float32)
        samples.append({**s, "left": s["left"] * k, "right": s["right"] * k, "aspect": 1.0, "norm": "hands"})
    # Real photographs are few beside 400 synthetic poses a class, so they are repeated to carry weight.
    samples += [s for s in samples if s["split"] == "train"] * REPEAT
    synthetic = [{**s, "aspect": synth["aspect"], "norm": "hands"} for s in synth["samples"] if s["split"] == "train"]
    samples += synthetic
    if names_path:
        # Names are built from synthetic letters only: they hold every letter, all on one aspect.
        context = names_context(synthetic, names_path, np.random.default_rng(0))
        print("in names", len(context))
        samples += context
    with open(out, "wb") as f:
        pickle.dump({"aspect": 1.0, "norm": "hands", "samples": samples}, f)
    for split in ("train", "test"):
        print(split, sum(s["split"] == split for s in samples))


if __name__ == "__main__":
    main(*sys.argv[1:5])
