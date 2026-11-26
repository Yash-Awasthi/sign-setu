"""Freeze the ISL sign-reader evaluation set as landmark arrays plus a manifest, reproducibly.

Takes the held-out rows of isl_more/include_kaggle.pkl (INCLUDE signer "include", split "test";
Kaggle signer "kaggle_*", split "kaggle_test") and writes manifest.tsv with one row per sequence
plus include_/kaggle_ pose, left, right and flags .npy files. --check re-verifies a written copy
without touching the corpus.
    py -3 tools/ml/freeze_eval.py [--data DIR] [--out DIR] [--force] [--check]
"""
import argparse
import hashlib
import os
import pathlib
import pickle
import sys

import numpy as np

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "tools" / "ml" / "data"
SOURCE = "isl_more/include_kaggle.pkl"
CORPORA = ("INCLUDE", "Kaggle ISL")
HEADER = ("seq", "corpus", "source_file", "source_index", "session", "signer", "word", "frames", "aspect", "sha256")
FILES = ("pose", "left", "right", "flags")


def held_out(s):
    if s["signer"] == "include":
        return s["split"] == "test"
    return s["signer"].startswith("kaggle") and s["split"] == "kaggle_test"


def digest(pose, left, right, flags):
    h = hashlib.sha256()
    for a in (pose, left, right):
        h.update(np.ascontiguousarray(a.astype("<f2")).tobytes())
    h.update(np.ascontiguousarray(flags.astype("u1")).tobytes())
    return h.hexdigest()


def arrays(samples):
    for s in samples:
        n = len(s["pose"])
        if any(len(s[k]) != n for k in ("left", "right", "has_pose", "has_left", "has_right")):
            raise ValueError(f"{s['label']}: landmark parts differ in length")
    pose = np.concatenate([s["pose"] for s in samples]).astype(np.float16)
    left = np.concatenate([s["left"] for s in samples]).astype(np.float16)
    right = np.concatenate([s["right"] for s in samples]).astype(np.float16)
    flags = np.concatenate([np.stack([s["has_pose"], s["has_left"], s["has_right"]], 1) for s in samples]).astype(np.uint8)
    return pose, left, right, flags


def freeze(data, out):
    with open(data / SOURCE, "rb") as f:
        cache = pickle.load(f)  # nosemgrep
    rows = [(0 if s["signer"] == "include" else 1, i, s) for i, s in enumerate(cache["samples"]) if held_out(s)]
    rows.sort(key=lambda r: (r[0], r[1]))
    lines = ["\t".join(HEADER)]
    for corpus_rank, i, s in rows:
        corpus = CORPORA[corpus_rank]
        a = s.get("aspect", cache["aspect"])
        flags = np.stack([s["has_pose"], s["has_left"], s["has_right"]], 1)
        lines.append("\t".join((f"{corpus.split()[0].lower()}-{i}", corpus, SOURCE, str(i), f"include_kaggle/{i}",
                                s["signer"], s["label"], str(len(s["pose"])), repr(float(a)),
                                digest(s["pose"], s["left"], s["right"], flags))))
    out.mkdir(parents=True, exist_ok=True)
    (out / "manifest.tsv").write_text("\n".join(lines) + "\n", encoding="ascii", newline="\n")
    for corpus_rank, corpus in enumerate(CORPORA):
        name = corpus.split()[0].lower()
        group = [s for rank, _, s in rows if rank == corpus_rank]
        for key, a in zip(FILES, arrays(group)):
            np.save(out / f"{name}_{key}.npy", a)
    return rows


def summary(n, words, corpora):
    return f"{n} sequences, {len(words)} words, {len(corpora)} corpora"


def check(out):
    errors = []
    path = out / "manifest.tsv"
    if not path.exists():
        print(f"missing manifest: {path}")
        return 1
    lines = path.read_text(encoding="ascii").splitlines()
    if not lines or tuple(lines[0].split("\t")) != HEADER:
        print(f"header mismatch in {path}: {lines[0] if lines else '<empty>'}")
        return 1
    rows = []
    for n, line in enumerate(lines[1:], 2):
        fields = line.split("\t")
        if len(fields) != len(HEADER):
            errors.append(f"line {n}: {len(fields)} fields, expected {len(HEADER)}")
            continue
        rows.append(dict(zip(HEADER, fields), line=n))
    data = {}
    for corpus in CORPORA:
        name = corpus.split()[0].lower()
        data[name] = {}
        for key in FILES:
            p = out / f"{name}_{key}.npy"
            if not p.exists():
                errors.append(f"missing {p.name}")
                continue
            data[name][key] = np.load(p)
    seen = set()
    for r in rows:
        if not r["word"]:
            errors.append(f"line {r['line']}: empty word")
        if not r["signer"]:
            errors.append(f"line {r['line']}: empty signer")
        if r["seq"] in seen:
            errors.append(f"line {r['line']}: duplicate seq {r['seq']}")
        seen.add(r["seq"])
    corpora = {r["corpus"] for r in rows}
    if corpora != set(CORPORA):
        errors.append(f"corpus set {sorted(corpora)}, expected {list(CORPORA)}")
    for corpus in CORPORA:
        name = corpus.split()[0].lower()
        group = [r for r in rows if r["corpus"] == corpus]
        try:
            total = sum(int(r["frames"]) for r in group)
        except ValueError as e:
            errors.append(f"{corpus}: bad frames value ({e})")
            continue
        a = data[name]
        if len(a) == len(FILES):
            for key in FILES:
                if a[key].shape[0] != total:
                    errors.append(f"{corpus}: frame sum {total}, {key} rows {a[key].shape[0]}")
        start = 0
        for r in group:
            try:
                frames = int(r["frames"])
            except ValueError:
                continue
            got = digest(*(a[k][start:start + frames] for k in FILES)) if len(a) == len(FILES) else None
            if got is not None and got != r["sha256"]:
                errors.append(f"line {r['line']}: sha256 {r['sha256']} != {got}")
            start += frames
    if errors:
        for e in errors:
            print(e)
        return 1
    print(f"frozen set intact: {summary(len(rows), {r['word'] for r in rows}, corpora)}")
    return 0


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("--data", default=os.environ.get("SIGNSETU_DATA", "C:/ml-data"))
    p.add_argument("--out", default=str(OUT))
    p.add_argument("--force", action="store_true")
    p.add_argument("--check", action="store_true")
    args = p.parse_args(argv)
    out = pathlib.Path(args.out)
    if args.check:
        return check(out)
    if (out / "manifest.tsv").exists() and not args.force:
        print(f"refusing to overwrite {out / 'manifest.tsv'} (use --force)")
        return 2
    rows = freeze(pathlib.Path(args.data), out)
    print(f"frozen set written: {summary(len(rows), {s['label'] for _, _, s in rows}, {CORPORA[r] for r, _, _ in rows})} -> {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
