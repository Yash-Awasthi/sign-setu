"""Uniform weight soup of seeds fine-tuned from one init (the app reader), written as seed 99 of the row so exp.py
--eval and union.py score it like any run.

    python soup.py WF 1 2 3 [--as 98]   (written as seed 99 unless --as says otherwise)
"""
import sys

import torch

from exp import RUNS


def main(row, seeds, out_seed=99):
    states = [torch.load(RUNS / f"{row}_s{s}" / "best.pt", weights_only=True, map_location="cpu") for s in seeds]  # nosemgrep: trailofbits.python.pickles-in-pytorch.pickles-in-pytorch
    soup = {k: (sum(st[k].float() for st in states) / len(states)).to(states[0][k].dtype) for k in states[0]}
    out = RUNS / f"{row}_s{out_seed}"
    out.mkdir(parents=True, exist_ok=True)
    torch.save(soup, out / "best.pt")  # nosemgrep: trailofbits.python.pickles-in-pytorch.pickles-in-pytorch
    print(f"soup of {row} seeds {seeds} -> {out / 'best.pt'}")


if __name__ == "__main__":
    a = sys.argv[1:]
    out_seed = int(a[a.index("--as") + 1]) if "--as" in a else 99
    seeds = [x for i, x in enumerate(a[1:], 1) if x != "--as" and a[i - 1] != "--as"]
    main(a[0], seeds, out_seed)
