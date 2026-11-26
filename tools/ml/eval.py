"""Accuracy report and regression gate for the shipped ISL word reader, on the frozen landmark set.

    python tools/ml/eval.py --check            # score tools/ml/data and compare with the recorded baseline
    python tools/ml/eval.py                    # score and print the report, no gate
    python tools/ml/eval.py --write-baseline   # record today's run as tools/ml/eval_baseline.json

The set is the frozen evaluation set: landmark clips with a word label, no video (`tools/ml/data/`, written by
`freeze_eval.py`). This tool only reads it. Scoring goes through runtime.py and features.py, so the numbers are the
reader the app ships, not a training-time model.

An "unclear" read is the app's own rule (Kinetics.kt, AMBIGUOUS_RATIO = 0.8): the runner-up's probability is at least
0.8 of the top's, after the spec's temperature. It measures how often the reader is unsure, next to how often it is
right; it is not folded into top-1.

The gate fails when top-1 drops more than 1 point or the unclear rate rises more than 2 points against the recorded
baseline. The baseline carries the frozen set's manifest sha256, so a number is only compared with a set it was
measured on; a changed set needs a newly recorded baseline.
"""
import argparse
import collections
import csv
import datetime
import hashlib
import json
import pathlib
import sys

import numpy as np

from features import featurize
from runtime import reader

ROOT = pathlib.Path(__file__).resolve().parents[2]
DATA = ROOT / "tools" / "ml" / "data"
BASELINE = ROOT / "tools" / "ml" / "eval_baseline.json"
MODEL = "sign_reader"
# More than a point of top-1 down, or more than two points of unclear reads up, fails the build.
MAX_TOP1_DROP = 0.01
MAX_UNCLEAR_RISE = 0.02
AMBIGUOUS = 0.8
TSV_FILES = ("pose", "left", "right", "flags")
TSV_COLUMNS = ("corpus", "word", "frames", "aspect", "sha256")


def rel(path):
    # Relative to the repo when inside it; absolute otherwise (fixtures live in a temp dir).
    try:
        return str(path.relative_to(ROOT))
    except ValueError:
        return str(path)


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def digest(a):
    """The per-sequence sha256 freeze_eval.py writes: pose, left, right as float16, then the flags as bytes."""
    h = hashlib.sha256()
    for key in ("pose", "left", "right"):
        h.update(np.ascontiguousarray(a[key].astype("<f2")).tobytes())
    h.update(np.ascontiguousarray(a["flags"].astype("u1")).tobytes())
    return h.hexdigest()


def tsv_clips(root, path):
    """One (word, arrays) per manifest.tsv row, cut from the per-corpus frame arrays and checked against its sha256."""
    with open(path, encoding="ascii", newline="") as f:
        rows = list(csv.DictReader(f, delimiter="\t"))
    if not rows:
        raise SystemExit(f"{path}: no rows")
    missing = [c for c in TSV_COLUMNS if c not in rows[0]]
    if missing:
        raise SystemExit(f"{path}: manifest.tsv lacks columns {missing}")
    arrays, cursor = {}, collections.Counter()
    for line, row in enumerate(rows, 2):
        name = row["corpus"].split()[0].lower()
        if name not in arrays:
            arrays[name] = {k: np.load(root / f"{name}_{k}.npy", mmap_mode="r") for k in TSV_FILES}
        start, frames = cursor[name], int(row["frames"])
        if start + frames > len(arrays[name]["pose"]):
            raise SystemExit(f"{path}:{line}: {frames} frames run past the end of {name}_pose.npy")
        a = {k: np.asarray(v[start:start + frames]) for k, v in arrays[name].items()}
        if digest(a) != row["sha256"]:
            raise SystemExit(f"{path}:{line}: sha256 does not match the arrays; this is not the frozen set")
        cursor[name] += frames
        flags = a["flags"]
        yield row["word"], dict(left=a["left"], right=a["right"], pose=a["pose"],
                                has_pose=flags[:, 0] > 0, has_left=flags[:, 1] > 0, has_right=flags[:, 2] > 0,
                                aspect=float(row["aspect"]), corpus=row["corpus"])
    for name, a in arrays.items():
        if cursor[name] != len(a["pose"]):
            raise SystemExit(f"{path}: {len(a['pose']) - cursor[name]} frames of {name}_pose.npy are in no row")


def json_manifest(root):
    """The clip list from manifest.json (a list, or an object holding clips/items/files) or manifest.jsonl."""
    path = root / "manifest.json"
    if path.exists():
        doc = json.loads(path.read_text(encoding="utf-8"))
        if isinstance(doc, list):
            return path, doc
        for key in ("clips", "items", "files", "entries"):
            if isinstance(doc.get(key), list):
                return path, doc[key]
        raise SystemExit(f"{path}: no clips list (expected a list under clips, items or files)")
    path = root / "manifest.jsonl"
    if path.exists():
        return path, [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    raise SystemExit(f"{root}: no manifest.tsv, manifest.json or manifest.jsonl; the frozen set is not here")


def presence(z, name, points):
    if name in z.files:
        return z[name].astype(bool)
    # No flag stored: a point counts as seen when it is finite and not the zero fill.
    return np.isfinite(z[points]).all((1, 2)) & (np.abs(z[points]).sum((1, 2)) > 0)


def npz_clip(root, entry):
    """One clip as featurize() inputs: an .npz/.npy clip, or a row of a raw.npy cache indexed by start/length."""
    path = entry.get("path") or entry.get("file")
    if path:
        z = np.load(root / path, allow_pickle=False)
        if not hasattr(z, "files"):
            v = np.asarray(z)
            if v.ndim != 2 or v.shape[1] < 150:
                raise SystemExit(f"{root / path}: expected an .npz clip or a (frames, >=150) array")
            return dict(left=v[:, 0:42].reshape(-1, 21, 2), right=v[:, 42:84].reshape(-1, 21, 2),
                        pose=v[:, 84:150].reshape(-1, 33, 2), has_left=v[:, -4] > 0.5,
                        has_right=v[:, -3] > 0.5, has_pose=v[:, -2] > 0.5,
                        aspect=float(entry.get("aspect", 1.0)), corpus=entry.get("corpus") or entry.get("source"))
        return dict(left=z["left"], right=z["right"], pose=z["pose"],
                    has_left=presence(z, "has_left", "left"), has_right=presence(z, "has_right", "right"),
                    has_pose=presence(z, "has_pose", "pose"),
                    aspect=float(z["aspect"]) if "aspect" in z.files else 1.0,
                    corpus=entry.get("corpus") or entry.get("source"))
    if entry.get("raw"):
        start, length = int(entry["start"]), int(entry["length"])
        v = np.load(root / entry["raw"], mmap_mode="r")[start:start + length]
        return dict(left=v[:, 0:42].reshape(-1, 21, 2), right=v[:, 42:84].reshape(-1, 21, 2),
                    pose=v[:, 84:150].reshape(-1, 33, 2), has_left=v[:, -4] > 0.5,
                    has_right=v[:, -3] > 0.5, has_pose=v[:, -2] > 0.5, aspect=float(entry["aspect"]),
                    corpus=entry.get("corpus") or entry.get("source"))
    raise SystemExit(f"manifest entry has no path and no raw row: {entry}")


def load_set(root):
    """The frozen clips as (manifest path, iterable of (word, arrays)). The TSV arrays are the committed format."""
    tsv = root / "manifest.tsv"
    if tsv.exists():
        return tsv, tsv_clips(root, tsv)
    path, items = json_manifest(root)
    return path, ((e.get("word") or e.get("label"), npz_clip(root, e)) for e in items)


def softmax(z):
    z = z - z.max(1, keepdims=True)
    e = np.exp(z)
    return e / e.sum(1, keepdims=True)


def reader_sha():
    src = pathlib.Path(reader.__globals__["MODELS"])
    h = hashlib.sha256()
    for name in (f"{MODEL}.json", f"{MODEL}.bin"):
        h.update((src / name).read_bytes())
    return h.hexdigest()


def evaluate(root):
    path, clips = load_set(root)
    spec, logits = reader(MODEL)
    labels = spec["labels"]
    index = {word: i for i, word in enumerate(labels)}
    temperature = float(spec.get("temperature", 1.0))

    rows, outside, skipped = [], 0, 0
    for word, a in clips:
        if word is None:
            raise SystemExit(f"{path}: a clip has no word")
        if word not in index:
            outside += 1
            continue
        k = np.array([1.0, 1.0 / a["aspect"]])
        f = featurize(a["left"] * k, a["right"] * k, a["pose"] * k,
                      a["has_left"], a["has_right"], a["has_pose"])
        if f is None:
            skipped += 1
            continue
        rows.append((word, f, a.get("corpus") or "all"))
    if not rows:
        raise SystemExit(f"{path}: no clip is readable by {MODEL}")

    p = softmax(logits(np.stack([f for _, f, _ in rows]).astype(np.float64)) / temperature)
    order = np.argsort(-p, 1)
    per, groups = collections.defaultdict(lambda: dict(n=0, top1=0, top5=0, unclear=0)), {}
    confusions = collections.Counter()
    for i, (word, _, corpus) in enumerate(rows):
        y = index[word]
        top1 = int(order[i, 0] == y)
        top5 = int(y in order[i, :5])
        unclear = int(p[i, order[i, 1]] >= AMBIGUOUS * p[i, order[i, 0]])
        for r in (per[word], groups.setdefault(corpus, dict(n=0, top1=0, top5=0, unclear=0))):
            r["n"] += 1
            r["top1"] += top1
            r["top5"] += top5
            r["unclear"] += unclear
        if not top1:
            confusions[(word, labels[int(order[i, 0])])] += 1
    n = len(rows)
    return dict(
        date=datetime.date.today().isoformat(), set=rel(path.parent), manifest=path.name,
        set_sha256=sha256(path), clips=n, words=len(per), outside=outside, skipped=skipped, reader=MODEL,
        reader_sha256=reader_sha(), top1=sum(r["top1"] for r in per.values()) / n,
        top5=sum(r["top5"] for r in per.values()) / n,
        unclear=sum(r["unclear"] for r in per.values()) / n,
        per_word={w: dict(n=r["n"], top1=r["top1"] / r["n"], top5=r["top5"] / r["n"], unclear=r["unclear"] / r["n"])
                  for w, r in sorted(per.items())},
        corpora={c: dict(n=r["n"], top1=r["top1"] / r["n"], top5=r["top5"] / r["n"], unclear=r["unclear"] / r["n"])
                 for c, r in sorted(groups.items())},
        confusions=[[t, q, c] for (t, q), c in confusions.most_common(10)],
    )


def report(run):
    lines = [
        f"frozen set  {run['set']} {run['manifest']}  sha256 {run['set_sha256'][:16]}  "
        f"{run['clips']:,} clips, {run['words']} words",
        f"reader      {run['reader']}  sha256 {run['reader_sha256'][:16]}",
        f"scored      {run['date']}  n={run['clips']:,}  top-1 {run['top1']:.4f}  top-5 {run['top5']:.4f}  "
        f"unclear {run['unclear']:.4f}",
    ]
    if len(run["corpora"]) > 1:
        lines.append("per corpus  " + "  ".join(
            f"{c} top-1 {r['top1']:.4f} top-5 {r['top5']:.4f} (n={r['n']:,})" for c, r in run["corpora"].items()))
    lines += [
        "",
        "| word | n | top-1 | top-5 | unclear |",
        "|---|---|---|---|---|",
    ]
    for word, r in sorted(run["per_word"].items(), key=lambda kv: (kv[1]["top1"], kv[0])):
        lines.append(f"| {word} | {r['n']} | {r['top1']:.2f} | {r['top5']:.2f} | {r['unclear']:.2f} |")
    lines += ["", "confusion pairs (true -> predicted, n)"]
    lines += [f"  {t} -> {q}  {c}" for t, q, c in run["confusions"]] or ["  none"]
    if run["outside"] or run["skipped"]:
        lines += ["", f"not scored: {run['outside']} clips of words outside the {MODEL} labels, "
                      f"{run['skipped']} clips with no pose"]
    return "\n".join(lines)


def gate(run, path):
    if not path.exists():
        print(f"no baseline at {rel(path)}; run with --write-baseline once the frozen set is final")
        return 2
    base = json.loads(path.read_text(encoding="utf-8"))
    if base.get("set_sha256") != run["set_sha256"]:
        print(f"REFUSING: {rel(path)} was recorded on a different frozen set "
              f"(sha256 {str(base.get('set_sha256'))[:16]} against {run['set_sha256'][:16]}); record a new baseline")
        return 2
    drop = run["top1"] - base["top1"]
    rise = run["unclear"] - base["unclear"]
    print("")
    print(f"gate  {rel(path)}, recorded {base['date']} on the same set")
    print(f"  top-1   {run['top1']:.4f}  baseline {base['top1']:.4f}  delta {drop:+.4f}  "
          f"(may not drop more than {MAX_TOP1_DROP:.2f})  {'ok' if drop >= -MAX_TOP1_DROP - 1e-9 else 'FAIL'}")
    print(f"  unclear {run['unclear']:.4f}  baseline {base['unclear']:.4f}  delta {rise:+.4f}  "
          f"(may not rise more than {MAX_UNCLEAR_RISE:.2f})  {'ok' if rise <= MAX_UNCLEAR_RISE + 1e-9 else 'FAIL'}")
    if drop < -MAX_TOP1_DROP - 1e-9 or rise > MAX_UNCLEAR_RISE + 1e-9:
        print("REGRESSION: the shipped reader is worse than the recorded baseline")
        return 1
    return 0


def main():
    ap = argparse.ArgumentParser(description="Score the shipped reader on the frozen landmark set.")
    ap.add_argument("--data", type=pathlib.Path, default=DATA, help="frozen set directory")
    ap.add_argument("--check", action="store_true", help="compare with the recorded baseline and fail on a regression")
    ap.add_argument("--baseline", type=pathlib.Path, default=BASELINE, help="baseline artefact to compare with")
    ap.add_argument("--write-baseline", action="store_true", help="record this run as the baseline")
    args = ap.parse_args()

    run = evaluate(args.data)
    print(report(run))
    if args.write_baseline:
        args.baseline.write_text(json.dumps(run, indent=2) + "\n", encoding="utf-8", newline="\n")
        print(f"\nwrote {rel(args.baseline)}: top-1 {run['top1']:.4f}, unclear {run['unclear']:.4f}, "
              f"set sha256 {run['set_sha256'][:16]}, {run['date']}")
    if args.check:
        return gate(run, args.baseline)
    return 0


if __name__ == "__main__":
    sys.exit(main())
