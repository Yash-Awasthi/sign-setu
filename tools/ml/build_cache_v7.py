"""Builds the second ISLRTC cache from the dictionary clips Phase 1 landmarked, then the joined cache over it and
cache_v6b: one index, each row naming the cache that holds its frames.

The new clips are the ones q3's catalogue left out (`q3/islrtc_before_q2.txt`), so their words never reached a cache.
Words are normalised with `q3.word_of`, the same function catalog.csv used, so a word already in the cache gets the
same label here.

Signers, with `--signers` (faces_new.py then cluster_new.py): each clip carries the signer its face matched — `c<q3
cluster>` for a person q3 already knew, `n<k>` for a new one — and takes that signer's split from cache_v6b, so a
held-out signer stays held out. Without it every clip is `u:<clip>`, split=train.

Reads the working set (SIGNSETU_DATA, D: through the junction) only; writes SIGN_CACHE_V7_DICT and SIGN_CACHE_V7,
C:/work by default.

    python build_cache_v7.py [--limit N] [--signers C:/work/faces_new/signers.csv]
"""
import json
import os
import pathlib
import sys

import numpy as np
import pandas as pd

import cache
from guard import DATA
from q3 import span, word_of, word_span

ROOT = pathlib.Path(__file__).resolve().parents[2]
V6B = DATA / "cache_v6b"
LM_NEW = pathlib.Path(os.environ.get("SIGN_LANDMARKS", "C:/work/landmarks_islrtc"))
OUT_DICT = pathlib.Path(os.environ.get("SIGN_CACHE_V7_DICT", "C:/work/cache_v7_dict"))
OUT = pathlib.Path(os.environ.get("SIGN_CACHE_V7", "C:/work/cache_v7"))
META = ("label", "split", "signer", "source", "clip", "in_vocab", "scored", "span_capped")
PARTS = ("left", "right", "pose", "face", "has_left", "has_right", "has_pose", "has_face")


def main():
    a = sys.argv[1:]
    limit = int(a[a.index("--limit") + 1]) if "--limit" in a else None
    signers = {}
    if "--signers" in a:
        signers = dict(pd.read_csv(a[a.index("--signers") + 1], keep_default_na=False)
                       .itertuples(index=False, name=None))
        print(f"{len(signers)} signer names", flush=True)
    cat = pd.read_csv(DATA / "q3" / "catalog.csv", keep_default_na=False).set_index("clip")
    vocab = set(json.loads((V6B / "vocab.json").read_text(encoding="utf-8")))

    paths = sorted(LM_NEW.rglob("*.npz"))
    if limit:
        paths = paths[:limit]
    print(f"{len(paths)} landmark files", flush=True)

    # A signer's split is a property of the signer, so a new clip follows the signer its face matched.
    known_split = {k: v.mode().iloc[0]
                   for k, v in pd.read_csv(V6B / "index.csv", keep_default_na=False).groupby("signer")["split"]
                   if k != "include"}
    samples, skipped, from_catalog = [], [], 0
    for n, p in enumerate(paths, 1):
        rel = str(p.relative_to(LM_NEW))[:-4].replace("\\", "/")
        clip = "islrtc_dict\\" + rel.replace("/", "\\")
        c = dict(np.load(p))
        segs, moving = span(c)
        if not segs:
            skipped.append((clip, "no sign segment"))
            continue
        a, b, _cut = word_span(c["t"], segs, moving)
        if b - a < 4:
            skipped.append((clip, "span too short"))
            continue
        if clip in cat.index:
            word, capped = str(cat.loc[clip, "word"]).lower(), int(cat.loc[clip, "span_capped"])
            from_catalog += 1
        else:
            word, _v, _e = word_of("islrtc", pathlib.Path(rel), {})
            capped = 0
        matched = signers.get(clip, "")
        samples.append({k: c[k][a:b] for k in PARTS}
                       | {"aspect": float(c["aspect"]), "label": word,
                          "split": known_split.get(matched, "train") if matched else "train",
                          "signer": matched or ("u:" + clip), "source": "islrtc", "clip": clip,
                          "in_vocab": word in vocab, "scored": False, "span_capped": capped})
        if n % 250 == 0:
            print(f"  {n}/{len(paths)} read, {len(samples)} kept", flush=True)

    if not samples:
        sys.exit("no samples; nothing written")
    cache.build(OUT_DICT, samples, meta_keys=META)

    old = pd.read_csv(V6B / "index.csv", keep_default_na=False)
    new = pd.read_csv(OUT_DICT / "index.csv", keep_default_na=False)
    old["cache"] = str(V6B)
    new["cache"] = str(OUT_DICT)
    joined = pd.concat([old, new], ignore_index=True)
    OUT.mkdir(parents=True, exist_ok=True)
    for name in ("vocab.json", "g2.json"):
        if (V6B / name).exists():
            (OUT / name).write_text((V6B / name).read_text(encoding="utf-8"), encoding="utf-8")
    joined.to_csv(OUT / "index.csv", index=False)

    app = set(json.loads((ROOT / "app" / "src" / "main" / "assets" / "content" / "models" / "sign_reader.json")
                         .read_text(encoding="utf-8"))["labels"])
    before = set(old.loc[old["label"] != "__nonsign__", "label"])
    new_words = set(new["label"])
    report = {
        "clips_landmarked": len(paths), "clips_in_cache": len(samples), "clips_skipped": len(skipped),
        "clips_taken_from_catalog": from_catalog,
        "frames": int(new["length"].sum()), "skipped_reasons": skipped[:20],
        "words": len(new_words),
        "words_already_in_cache": len(new_words & before),
        "words_with_a_first_landmark": len(new_words - before),
        "words_outside_cache_vocabulary": len(new_words - vocab),
        "app_words_among_new": sorted(new_words & app),
        "app_words_before": len(before & app), "app_words_after": len((before | new_words) & app),
        "signers_named": sum(1 for s in samples if not s["signer"].startswith("u:")),
        "signers_distinct": len({s["signer"] for s in samples}),
        "signers_matched_to_v6b": len({s["signer"] for s in samples} & set(known_split)),
        "clips_moved_off_train": sum(1 for s in samples if s["split"] != "train"),
        "cache_rows": len(joined), "cache_v6b_rows": len(old), "cache_v7_dict_rows": len(new),
    }
    (OUT_DICT / "coverage.json").write_text(json.dumps(report, indent=1))
    print(json.dumps(report, indent=1), flush=True)
    print(f"cache_v7/index.csv {len(joined):,} rows over {joined['cache'].nunique()} caches", flush=True)


if __name__ == "__main__":
    main()
