"""Calibration, conformal prediction sets and the confusion table for the shipped word reader (docs/10-gloss-to-text.md).

Held-out data: ISLRTC dictionary clips (one signer, not in training) and the held-out Kaggle signer. The ISLRTC signer
is the only truly new signer on disk, so it is split by word into a fitting half and a reporting half; every number
printed for "report" comes from words the fit never saw. Writes the temperature and conformal threshold into
sign_reader.json and the confusion table to assets/content/models/sign_confusion.json.
    python tools/ml/calibrate.py
"""
import json
import pathlib
import pickle
import random

import numpy as np
import torch
import torch.nn.functional as F

from train import SignNet, batch

ROOT = pathlib.Path(__file__).resolve().parents[2]
MODELS = ROOT / "app" / "src" / "main" / "assets" / "content" / "models"
DATA = pathlib.Path("C:/work/ml-data")
# Shipped levels: on a new signer even 50% needs about 16 words per set (EVAL.md), so the app uses the 50% set as a
# sign's candidates and marks the sign unsure when it is larger than the app's cap; 80% is kept for reports.
COVERAGES = (0.5, 0.8)
# RAPS regularisation: sets larger than K_REG pay LAMBDA per extra word, which keeps them small without losing coverage.
K_REG, LAMBDA = 3, 0.02


def logits_for(model, samples, aspect):
    with torch.no_grad():
        return model(batch(samples, aspect)).double()


def fit_temperature(logits, labels) -> float:
    t = torch.nn.Parameter(torch.ones(1, dtype=torch.float64))
    opt = torch.optim.LBFGS([t], lr=0.1, max_iter=200)

    def closure():
        opt.zero_grad()
        loss = F.cross_entropy(logits / t, labels)
        loss.backward()
        return loss
    opt.step(closure)
    return float(t.item())


def ece(probs, labels, bins: int = 10) -> float:
    conf, pred = probs.max(1)
    right = (pred == labels).double()
    e = 0.0
    for lo in np.linspace(0, 1, bins, endpoint=False):
        m = (conf > lo) & (conf <= lo + 1 / bins)
        if m.any():
            e += m.double().mean().item() * abs(right[m].mean().item() - conf[m].mean().item())
    return e


def raps_scores(probs, labels):
    """Conformity score of the true label: probability mass ranked above it, plus the size penalty, randomised."""
    order = probs.argsort(1, descending=True)
    sorted_p = probs.gather(1, order)
    rank = (order == labels[:, None]).double().argmax(1).long()
    cum = sorted_p.cumsum(1)
    u = torch.rand(len(labels), dtype=torch.float64)
    true_p = sorted_p.gather(1, rank[:, None]).squeeze(1)
    return cum.gather(1, rank[:, None]).squeeze(1) - u * true_p + LAMBDA * (rank + 1 - K_REG).clamp(min=0)


def raps_sets(probs, qhat):
    order = probs.argsort(1, descending=True)
    sorted_p = probs.gather(1, order)
    cum = sorted_p.cumsum(1) - sorted_p  # mass strictly above each rank, the deterministic form used on the phone
    pen = LAMBDA * (torch.arange(probs.shape[1]) + 1 - K_REG).clamp(min=0)
    keep = (cum + pen) <= qhat
    keep[:, 0] = True
    return [order[i][keep[i]].tolist() for i in range(len(probs))]


def report(name, probs, labels, qhat):
    sets = raps_sets(probs, qhat)
    cover = np.mean([int(l) in s for s, l in zip(sets, labels.tolist())])
    sizes = np.array([len(s) for s in sets])
    top1 = (probs.argmax(1) == labels).double().mean().item()
    nll = F.nll_loss(probs.clamp_min(1e-12).log(), labels).item()
    print(f"  {name:28s} n={len(labels):4d} top1 {top1:.3f}  ECE {ece(probs, labels):.3f}  NLL {nll:.2f}  "
          f"set coverage {cover:.3f}  set size median {int(np.median(sizes))} mean {sizes.mean():.1f}")
    return cover, sizes


def risk_coverage(probs, labels):
    conf, pred = probs.max(1)
    order = conf.argsort(descending=True)
    right = (pred == labels)[order].double()
    out = []
    for c in (0.1, 0.2, 0.3, 0.5, 0.7, 1.0):
        k = max(1, int(c * len(right)))
        out.append(f"{c:.0%}:{1 - right[:k].mean().item():.2f}")
    return " ".join(out)


def risk_curve(probs, labels, score):
    """Error rate of the most confident share of signs, for every share, ranked by [score] (higher is surer)."""
    order = score.argsort(descending=True)
    right = (probs.argmax(1) == labels)[order].double()
    n = len(right)
    return [(k / n, 1 - right[:k].mean().item()) for k in range(max(1, n // 50), n + 1, max(1, n // 50))]


def svg_plot(curves, path, title):
    """Risk against coverage as a small standalone SVG (no plotting library on the machine)."""
    w, h, m = 520, 340, 46
    colours = ["#2a6fdb", "#d1495b", "#3c9d5d"]
    x = lambda c: m + c * (w - 2 * m)  # noqa: E731
    y = lambda r: h - m - r * (h - 2 * m)  # noqa: E731
    parts = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" font-family="sans-serif" font-size="12">',
             f'<rect width="{w}" height="{h}" fill="white"/><text x="{w / 2}" y="20" text-anchor="middle" font-size="14">{title}</text>',
             f'<line x1="{m}" y1="{h - m}" x2="{w - m}" y2="{h - m}" stroke="#444"/><line x1="{m}" y1="{m}" x2="{m}" y2="{h - m}" stroke="#444"/>',
             f'<text x="{w / 2}" y="{h - 10}" text-anchor="middle">coverage (share of signs answered)</text>',
             f'<text x="14" y="{h / 2}" transform="rotate(-90 14 {h / 2})" text-anchor="middle">error rate</text>']
    for t in (0, 0.25, 0.5, 0.75, 1):
        parts.append(f'<text x="{x(t)}" y="{h - m + 16}" text-anchor="middle">{t:.0%}</text><text x="{m - 6}" y="{y(t) + 4}" text-anchor="end">{t:.0%}</text>')
    for i, (name, pts) in enumerate(curves):
        d = " ".join(f"{x(c):.1f},{y(r):.1f}" for c, r in pts)
        parts.append(f'<polyline fill="none" stroke="{colours[i % 3]}" stroke-width="2" points="{d}"/>')
        parts.append(f'<text x="{w - m - 4}" y="{m + 16 * i}" text-anchor="end" fill="{colours[i % 3]}">{name}</text>')
    path.write_text("".join(parts) + "</svg>\n", encoding="utf-8")


def main() -> None:
    torch.manual_seed(7)
    random.seed(7)
    spec_path = MODELS / "sign_reader.json"
    ckpt = torch.load(DATA / "isl_more" / "include_kaggle.pt", weights_only=True)  # nosemgrep: trailofbits.python.pickles-in-pytorch.pickles-in-pytorch
    labels = ckpt["labels"]
    index = {l: i for i, l in enumerate(labels)}
    model = SignNet(len(labels), hidden=ckpt["state"]["bn2.weight"].shape[0])
    model.load_state_dict(ckpt["state"])
    model.eval()

    rtc = pickle.load(open(DATA / "islrtc" / "islrtc_words.pkl", "rb"))  # nosemgrep
    rtc_s = [s for s in rtc["samples"] if s["label"] in index]
    words = sorted({s["label"] for s in rtc_s})
    random.shuffle(words)
    fit_words = set(words[: len(words) // 2])
    fit = [s for s in rtc_s if s["label"] in fit_words]
    rep = [s for s in rtc_s if s["label"] not in fit_words]
    kag = pickle.load(open(DATA / "isl_more" / "include_kaggle.pkl", "rb"))  # nosemgrep
    kag_s = [s for s in kag["samples"] if s["split"] == "test" and s["label"] in index]

    lf, yf = logits_for(model, fit, rtc["aspect"]), torch.tensor([index[s["label"]] for s in fit])
    lr, yr = logits_for(model, rep, rtc["aspect"]), torch.tensor([index[s["label"]] for s in rep])
    lk, yk = logits_for(model, kag_s, kag["aspect"]), torch.tensor([index[s["label"]] for s in kag_s])

    t = fit_temperature(lf, yf)
    n = len(yf)
    scores = raps_scores((lf / t).softmax(1), yf)
    qhats = {c: float(torch.quantile(scores, min(1.0, np.ceil((n + 1) * c) / n))) for c in COVERAGES}
    print(f"temperature {t:.3f} (fit on {len(fit_words)} ISLRTC words, n={n})")
    for c, qhat in qhats.items():
        print(f" RAPS {c:.0%}: threshold {qhat:.3f}")
        for name, lg, y in (("ISLRTC report half, raw", lr, yr), ("ISLRTC report half, T", lr / t, yr),
                            ("Kaggle held-out signer, T", lk / t, yk)):
            report(name, lg.softmax(1), y, qhat)
    print("  risk at coverage (ISLRTC report, T):", risk_coverage((lr / t).softmax(1), yr))
    pr = (lr / t).softmax(1)
    sizes = torch.tensor([len(x) for x in raps_sets(pr, qhats[COVERAGES[0]])], dtype=torch.float64)
    plots = pathlib.Path(__file__).resolve().parent / "plots"
    plots.mkdir(exist_ok=True)
    svg_plot([("top probability", risk_curve(pr, yr, pr.max(1).values)),
              ("small 50% conformal set", risk_curve(pr, yr, -sizes + pr.max(1).values * 1e-3))],
             plots / "risk_coverage.svg", "Sign reader on a new signer (ISLRTC report half)")
    print(f"  wrote {plots / 'risk_coverage.svg'}")

    # The confusion table: every held-out reading, true word to what was read first, with counts.
    allp = torch.cat([(lf / t).softmax(1), (lr / t).softmax(1), (lk / t).softmax(1)])
    ally = torch.cat([yf, yr, yk])
    table = {}
    for p, y in zip(allp.argmax(1).tolist(), ally.tolist()):
        row = table.setdefault(labels[y], {})
        row[labels[p]] = row.get(labels[p], 0) + 1
    (MODELS / "sign_confusion.json").write_text(json.dumps({
        "note": "Held-out readings of the shipped sign_reader (ISLRTC dictionary signer, held-out Kaggle signer): true "
                "word to first guess, counts. Written by tools/ml/calibrate.py; edit the script, not this file.",
        "temperature": round(t, 4), "counts": table}, ensure_ascii=False, sort_keys=True), encoding="utf-8")

    spec = json.loads(spec_path.read_text(encoding="utf-8"))
    spec["temperature"] = round(t, 4)
    spec["conformal"] = {"method": "RAPS", "k_reg": K_REG, "lambda": LAMBDA,
                         "thresholds": {f"{c:.2f}": round(q, 4) for c, q in qhats.items()},
                         "fit": f"ISLRTC dictionary signer, {len(fit_words)} of {len(words)} words"}
    spec_path.write_text(json.dumps(spec, indent=1) + "\n", encoding="utf-8", newline="\n")
    print("wrote temperature and conformal threshold to sign_reader.json, confusion to sign_confusion.json")


if __name__ == "__main__":
    main()
