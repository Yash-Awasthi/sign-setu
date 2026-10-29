"""C 3: NameResolver.kt knobs (LAMBDA, the unlisted prior mu, MARGIN) and the speller's temperature, on
ISL-Fingerspelling's held-out signer. Two-fold over the segments: knobs chosen on one half (wrong snaps under 2%,
then the most names right), measured on the other, then swapped.

    python name_tune.py <isl_fingerspell.pkl> <model.pt>

Lexicon per segment, as in a session: the spelled name is a confirmed session name (prior 1) for half the segments
and missing for the other half; every other name in the set is listed at 0.5.
"""
import itertools
import json
import sys

import numpy as np
import torch
import torch.nn.functional as F

from exp import EVAL, ece
from isl_spell_ctc import ALPHABET, IslSpellNet, greedy, load, plain, target
from spell_ctc import edit_distance

NEG, BEAM, PRUNE = -1e30, 10, -12.0


def log_add(a, b):
    if a <= NEG:
        return b
    if b <= NEG:
        return a
    m = max(a, b)
    return m + np.log(np.exp(a - m) + np.exp(b - m))


def prefix_beam(logp, width=BEAM):
    beams = {"": (0.0, NEG)}
    for row in logp:
        nxt = {}

        def add(p, b, nb):
            ob, onb = nxt.get(p, (NEG, NEG))
            nxt[p] = (log_add(ob, b), log_add(onb, nb))
        for prefix, (pb, pnb) in beams.items():
            add(prefix, log_add(pb, pnb) + row[0], NEG)
            last = prefix[-1] if prefix else None
            for c in np.flatnonzero(row[1:] >= PRUNE) + 1:
                ch, lp = ALPHABET[c - 1], float(row[c])
                if ch == last:
                    add(prefix, NEG, pnb + lp)
                    add(prefix + ch, NEG, pb + lp)
                else:
                    add(prefix + ch, NEG, log_add(pb, pnb) + lp)
        beams = dict(sorted(nxt.items(), key=lambda kv: -log_add(*kv[1]))[:width])
    return sorted(((p, log_add(*v)) for p, v in beams.items()), key=lambda x: -x[1])


def ctc_logprob(logp, text):
    labels = [ALPHABET.index(c) + 1 for c in text if c in ALPHABET]
    if len(labels) != len(text) or len(labels) > len(logp):
        return None
    s = 2 * len(labels) + 1
    lab = lambda i: 0 if i % 2 == 0 else labels[i // 2]  # noqa: E731
    alpha = np.full(s, NEG)
    alpha[0] = logp[0][0]
    if s > 1:
        alpha[1] = logp[0][lab(1)]
    for t in range(1, len(logp)):
        nxt = np.full(s, NEG)
        for i in range(s):
            a = alpha[i]
            if i >= 1:
                a = log_add(a, alpha[i - 1])
            if i >= 2 and lab(i) != 0 and lab(i) != lab(i - 2):
                a = log_add(a, alpha[i - 2])
            nxt[i] = a + logp[t][lab(i)]
        alpha = nxt
    return log_add(alpha[s - 1], alpha[s - 2]) if s > 1 else alpha[0]


def candidates(logp, names):
    """Beam strings and listed names within 2 edits of one, each with its exact CTC log probability."""
    beams = [b for b in prefix_beam(logp) if b[0]]
    if not beams:
        return None, {}
    near = {n for n in names if any(abs(len(n) - len(b)) <= 2 and edit_distance(n, b) <= 2 for b, _ in beams)}
    out = {}
    for c in {b for b, _ in beams} | near:
        lp = ctc_logprob(logp, c)
        if lp is not None:
            out[c] = lp
    return beams[0][0], out


def resolve(raw, cands, prior, lam, mu, margin):
    scored = sorted(((c, lp + lam * np.log(prior.get(c, mu))) for c, lp in cands.items()), key=lambda x: -x[1])
    best = scored[0]
    gap = best[1] - (scored[1][1] if len(scored) > 1 else -np.inf)
    sure = best[0] in prior and gap >= margin
    return (best[0] if sure else raw), sure


def main(pkl, model_path):
    _, te = load(pkl)
    dev = torch.device("cuda")
    model = IslSpellNet()
    model.load_state_dict(torch.load(model_path, weights_only=True)["state"])
    model.to(dev).eval()
    with torch.no_grad():
        logits = [model(torch.tensor(plain(s)[None]).to(dev))[0].float().cpu() for s in te]
    want = [target(s).replace(" ", "") for s in te]
    names = sorted(set(want))
    fold = np.arange(len(te)) % 2

    # Speller temperature: CTC loss of the true spelling, fitted per fold; confidence = P(greedy reading).
    def stats(rows, temp):
        nll, conf, right = [], [], []
        for i in rows:
            lp = F.log_softmax(logits[i] / temp, -1)
            tgt = torch.tensor([ALPHABET.index(c) + 1 for c in want[i]])
            nll.append(float(F.ctc_loss(lp[:, None], tgt[None], [len(lp)], [len(tgt)], reduction="sum")))
            got = greedy(lp.numpy()).replace(" ", "")
            p = ctc_logprob(lp.numpy(), got) if got else None
            conf.append(float(np.exp(p)) if p is not None else 0.0)
            right.append(got == want[i])
        return np.mean(nll), np.array(conf), np.array(right)
    temps, eces, ece1, nlls = [], [], [], []
    for k in (0, 1):
        fit, use = np.flatnonzero(fold == k), np.flatnonzero(fold != k)
        grid = np.linspace(0.5, 3, 26)
        t = float(grid[int(np.argmin([stats(fit, g)[0] for g in grid]))])
        n, c, r = stats(use, t)
        _, c1, r1 = stats(use, 1.0)
        temps.append(t), nlls.append(n), eces.append(ece(c, r)), ece1.append(ece(c1, r1))
    cer = sum(edit_distance(greedy(F.log_softmax(lg, -1).numpy()).replace(" ", ""), w) for lg, w in zip(logits, want)) / sum(map(len, want))

    # Name knobs at the app's temperature (1): the resolver sees the raw speller output.
    pre = []
    for i, lg in enumerate(logits):
        logp = F.log_softmax(lg, -1).numpy()
        listed = i % 2 == 0
        prior = {n: 0.5 for n in names if n != want[i]}
        if listed:
            prior[want[i]] = 1.0
        raw, cands = candidates(logp, set(prior))
        pre.append((raw, cands, prior, listed))
    grid = list(itertools.product([0.25, 0.5, 1, 1.5, 2, 3], [0.001, 0.003, 0.01, 0.03, 0.1], np.arange(0, 8.5, 0.5)))

    def run(rows, knobs):
        wrong = right = 0
        for i in rows:
            raw, cands, prior, _ = pre[i]
            if raw is None:
                continue
            text, sure = resolve(raw, cands, prior, *knobs)
            wrong += sure and text != want[i]
            right += text == want[i]
        return wrong / len(rows), right / len(rows)
    out = []
    for k in (0, 1):
        fit, use = np.flatnonzero(fold == k), np.flatnonzero(fold != k)
        ok = [(run(fit, g), g) for g in grid]
        ok = [x for x in ok if x[0][0] < 0.02] or ok
        best = max(ok, key=lambda x: (x[0][1], -x[0][0]))[1]
        out.append((best, run(use, best), run(use, (1.0, 0.01, 2.0))))
    res = {"cer": cer, "temperature": temps, "nll_t": float(np.mean(nlls)), "ece_t1": float(np.mean(ece1)),
           "ece_t": float(np.mean(eces)), "knobs": [o[0] for o in out],
           "wrong_snap": float(np.mean([o[1][0] for o in out])), "names_right": float(np.mean([o[1][1] for o in out])),
           "current_wrong_snap": float(np.mean([o[2][0] for o in out])), "current_names_right": float(np.mean([o[2][1] for o in out]))}
    print(json.dumps(res, indent=1, default=float), flush=True)
    with open(EVAL, "a", encoding="utf-8") as f:
        f.write(f"\n### C 3. Name margin and speller calibration (ISL-Fingerspelling held-out signer, n={len(te)}, two-fold)\n\n"
                f"- Speller CER {cer:.3f}. Temperature {temps[0]:.2f} / {temps[1]:.2f}; NLL after {res['nll_t']:.2f}; "
                f"confidence ECE {res['ece_t1']:.3f} at T=1, {res['ece_t']:.3f} after.\n"
                f"- NameResolver knobs (LAMBDA, unlisted prior, MARGIN) per fold: {out[0][0]} / {out[1][0]}. Wrong snaps "
                f"{res['wrong_snap']:.3f}, names right {res['names_right']:.3f}; current knobs (1, 0.01, 2): "
                f"{res['current_wrong_snap']:.3f} wrong, {res['current_names_right']:.3f} right.\n")


if __name__ == "__main__":
    main(*sys.argv[1:3])
