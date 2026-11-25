"""Per-word accuracy of the shipped ISL and ASL word readers, and the words held for confirmation.

Scores the exported readers through runtime.py, so the numbers are what ships, on clips the readers were not
trained on: INCLUDE's official test split (ISL), the ISLRTC dictionary signer (ISL, one signer never trained on)
and ASL Citizen (ASL, not in the ASL reader's PopSign/Kaggle training sets). Writes docs/models/per_word.md.

    SIGNSETU_DATA=<dir> python tools/ml/per_word.py            # print the tables and the flagged counts
    SIGNSETU_DATA=<dir> python tools/ml/per_word.py --write    # write docs/models/per_word.md
"""
import argparse
import collections
import csv
import json
import os
import pathlib
import pickle

import numpy as np

from features import featurize
from runtime import reader

ROOT = pathlib.Path(__file__).resolve().parents[2]
DATA = pathlib.Path(os.environ.get("SIGNSETU_DATA", "ml-data"))
OUT = ROOT / "docs" / "models" / "per_word.md"
MODELS = ROOT / "app" / "src" / "main" / "assets" / "content" / "models"

# A word read right less than three times in five is held to confirmation (the app never records a read without
# the subject picking it, so the hold is a note for the reviewer and the next recording round, not a code path).
THRESHOLD = 0.60
# One clip is 0 or 1, which says nothing about a word: only words with at least this many clips are judged.
MIN_N = 2


def features_of(cache, samples):
    k = np.array([1.0, 1.0 / cache["aspect"]])
    return [featurize(s["left"] * k, s["right"] * k, s["pose"] * k, s["has_left"], s["has_right"], s["has_pose"])
            for s in samples]


def score(model, pairs):
    """(word, n, right, top3, most read word when wrong) per word for (features, label) pairs the model can read."""
    spec, logits = reader(model)
    index = {label: i for i, label in enumerate(spec["labels"])}
    keep = [(f, label) for f, label in pairs if f is not None and label in index]
    x = np.stack([f for f, _ in keep]).astype(np.float64)
    p = logits(x)
    y = np.array([index[label] for _, label in keep])
    top = np.argsort(-p, 1)[:, :3]
    per = collections.defaultdict(lambda: {"n": 0, "right": 0, "top3": 0, "wrong": collections.Counter()})
    for i, (_, label) in enumerate(keep):
        row = per[label]
        row["n"] += 1
        row["right"] += int(top[i, 0] == y[i])
        row["top3"] += int(y[i] in top[i])
        if top[i, 0] != y[i]:
            row["wrong"][spec["labels"][int(top[i, 0])]] += 1
    return per


def asl_citizen():
    """The app's 250 ASL words as they appear in ASL Citizen: one held-out clip set, landmarked by the app's model."""
    root = DATA / "cache_asl_full"
    spec = json.loads((MODELS / "asl_reader.json").read_text(encoding="utf-8"))
    app = {label.replace(" ", "").replace("_", "").lower(): label for label in spec["labels"]}
    raw = np.load(root / "raw.npy", mmap_mode="r")
    pairs = []
    with open(root / "index.csv", encoding="utf-8") as f:
        for r in csv.DictReader(f):
            key = r["label"].replace("asl:", "").replace(" ", "").replace("_", "").lower()
            if key not in app:
                continue
            a, n = int(r["start"]), int(r["length"])
            v = np.array(raw[a:a + n])
            left, right, pose = v[:, 0:42].reshape(n, 21, 2), v[:, 42:84].reshape(n, 21, 2), v[:, 84:150].reshape(n, 33, 2)
            k = np.array([1.0, 1.0 / float(r["aspect"])])
            has = (v[:, -4] > 0.5, v[:, -3] > 0.5, v[:, -2] > 0.5)
            pairs.append((featurize(left * k, right * k, pose * k, *has), app[key]))
    return pairs


def load(name):
    with open(DATA / name, "rb") as f:
        return pickle.load(f)  # nosemgrep: trailofbits.python.pickles-in-pytorch.pickles-in-pytorch


def rows_markdown(per):
    """Worst first, so every held word is at the top of its table."""
    out = []
    for word, r in sorted(per.items(), key=lambda kv: (kv[1]["right"] / kv[1]["n"], kv[0])):
        score = r["right"] / r["n"]
        verdict = "hold to confirm" if r["n"] >= MIN_N and score < THRESHOLD else "ok"
        if r["n"] < MIN_N:
            verdict = "not judged"
        wrong = r["wrong"].most_common(1)
        read_as = wrong[0][0] if wrong else ""
        out.append(f"| `{word}` | {r['n']} | {score:.2f} | {r['top3'] / r['n']:.2f} | {read_as} | {verdict} |")
    return out


def table(title, source, per):
    judged = [r for r in per.values() if r["n"] >= MIN_N]
    held = [r for r in judged if r["right"] / r["n"] < THRESHOLD]
    clips = sum(r["n"] for r in per.values())
    top1 = sum(r["right"] for r in per.values()) / clips if clips else 0.0
    head = [f"### {title}", "",
            f"{source} Clips {clips}, words {len(per)}, top-1 {top1:.3f}. "
            f"Judged words (n >= {MIN_N}) {len(judged)}, held {len(held)}.", ""]
    return head + ["| Word | n | Top-1 | Top-3 | Read as | Verdict |", "|---|---|---|---|---|---|"] + rows_markdown(per)


def markdown(tables, single, absent_isl, absent_asl):
    lines = [
        "# Per-word accuracy",
        "",
        "Written 2026-11-26 by `tools/ml/per_word.py` over the shipped readers (`tools/ml/runtime.py`, the same NumPy",
        "forward pass the app's Kotlin mirrors). Clips the readers were not trained on: INCLUDE's official test split",
        "(ISL, 262 words), the ISLRTC dictionary signer (ISL, one signer never trained on), and ASL Citizen (ASL, not",
        "in the ASL reader's PopSign and Kaggle training sets). Re-run the tool after any retrain; do not edit this",
        "file by hand.",
        "",
        "A word is **held to confirm** when its top-1 is under 0.60 over at least two clips: at that point two or",
        "more reads in five are wrong, so the word is a poor first suggestion to put in front of the subject. A word",
        "with one clip is shown but not judged, because 0 or 1 says nothing about it.",
        "",
        "On ASL, ASL Citizen sometimes signs a gloss in a variant PopSign does not: a low row there can mean a different",
        "citation form rather than a reader that cannot see the sign. It is still a word to hold, because the phone will",
        "meet the same variation. On ISL, the ISLRTC signer is the only new signer on disk, and a low row there is the",
        "reader meeting a signer unlike its training signers.",
        "",
        "Holding a word costs nothing in the app's flow: no read is recorded until the subject confirms it, picking",
        "among up to three picture choices (`SignReader.kt`, `SignConfirm` in `sign/SignChoices.kt`), and the item's",
        "own answer cards are offered whether or not the reader named them. The hold is a note for the Deaf reviewer",
        "and the recording list; a held word is improved by re-recording it or by adding signers to the training",
        "cache, not by a code change.",
        "",
    ]
    for block in tables:
        lines += block + [""]
    lines += [
        "## Coverage",
        "",
        f"INCLUDE's test split has no clips for the {absent_isl} words that came from the Kaggle set, and ASL Citizen has",
        f"no clips of {absent_asl} of the app's 250 ASL words; a word absent from a table is unmeasured, not perfect. The",
        f"ISLRTC signer's clips cover 200 words, {single} of them with a single clip, so its table judges fewer words than",
        "it lists.",
        "",
    ]
    return "\n".join(lines)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--write", action="store_true", help="write docs/models/per_word.md")
    args = ap.parse_args()

    inc = load("include_pose/include.pkl")
    inc_samples = [s for s in inc["samples"] if s["split"] == "test"]
    isl = score("sign_reader", list(zip(features_of(inc, inc_samples), [s["label"] for s in inc_samples])))

    rtc = load("islrtc/islrtc_words.pkl")
    new = score("sign_reader", list(zip(features_of(rtc, rtc["samples"]), [s["label"] for s in rtc["samples"]])))

    asl = score("asl_reader", asl_citizen())

    tables = [
        table("ISL, INCLUDE test split", "The reader's own signers, held out by INCLUDE.", isl),
        table("ISL, ISLRTC dictionary signer", "One signer never in training, the hardest real set on disk.", new),
        table("ASL, ASL Citizen", "Signers of a different ASL corpus; 171 of the app's 250 words appear.", asl),
    ]
    spec_isl = json.loads((MODELS / "sign_reader.json").read_text(encoding="utf-8"))["labels"]
    spec_asl = json.loads((MODELS / "asl_reader.json").read_text(encoding="utf-8"))["labels"]
    text = markdown(
        tables,
        single=sum(1 for r in new.values() if r["n"] < MIN_N),
        absent_isl=len(spec_isl) - len(isl),
        absent_asl=len(spec_asl) - len(asl),
    )
    for block in tables:
        print("\n".join(block[:3]))
    if args.write:
        OUT.write_text(text + "\n", encoding="utf-8", newline="\n")
        print(f"wrote {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
