"""Mines word windows from iSign sentences: every window of a landmarked sentence clip is embedded by the shipped
reader (its pooled features) and compared by cosine with each app word's prototype, the mean over that word's training
clips. A word's best window counts only above a per-word threshold, set where clips whose sentence lacks the word
would leave the mined set at least 95% right. Later rounds fold the mined windows into the prototypes.

    python mine_isign.py mine OUT_DIR [--round 1] [--limit N] [--prev mined_r1.jsonl]
    python mine_isign.py sheet OUT_DIR mined_rN.jsonl [--n 60]   # source frames of a random sample, numbered
    python mine_isign.py check OUT_DIR mined_rN.jsonl WRONG.txt  # WRONG.txt: the sheet numbers judged wrong
    python mine_isign.py proxy OUT_DIR mined_rN.jsonl [--judge WF_s2] [--cache C:/work/ml-data/cache_v5]
    python mine_isign.py cache mined_rN_proxy.jsonl CACHE_OUT   # kept windows as a training cache (source "isign")

cache first drops every iSign recording whose signer may be a held-out validation or test signer: SFace embeddings of
three frames per recording against the held-out signer centroids (q3/held_out.json, embeddings only), dropped within
the 0.55 cosine distance that clustered signers, and dropped when no face is found.

With no one to judge sheets, proxy keeps a window when another reader, independent of the miner (by default WF seed
2, from before the installed reader), has the word in its top 5 ISL words: on validation signers it names a wrong
word 0.000 of the time against 0.55 for the right one on the new signer (n=11). DTW rank against the word's ISLRTC
dictionary clip (training rows only) is logged, not gated: on the new signer only 18% of true signs fall in its top 5%.
Calibration reads validation signers only, never the test split. Unverified: mined windows go into training only,
never into validation or test; they are used only if a reader trained with them beats one without (McNemar p<0.01).
"""
import csv
import json
import os
import pathlib
import random
import re
import sys

# Two threads: landmarking keeps the rest of the cores (maths libraries otherwise take all of them).
for _v in ("OMP_NUM_THREADS", "MKL_NUM_THREADS", "OPENBLAS_NUM_THREADS"):
    os.environ.setdefault(_v, "2")

import numpy as np  # noqa: E402
import pandas as pd  # noqa: E402
import torch  # noqa: E402

torch.set_num_threads(int(os.environ["OMP_NUM_THREADS"]))

from cache import frames_of, raw_frames
from exp import app_model
from features import resample_index
from guard import DATA
from models import Shipped, shipped_features

LM = DATA / "landmarks_isign"
TABLE = DATA / "isign" / "iSign_v1.1.csv"
VIDEOS = pathlib.Path("D:/ml-data/isign/iSign-videos_v1.1")
TRAIN_CACHE = DATA / "cache_v6b"
FRAME_S = 0.03
WINDOWS = (20, 27, 36, 48)  # frames at 30 ms: 0.6 to 1.44 s, the span of one sign
STRIDE = 4
PRECISION = 0.95
MIN_KEEP = 3  # a word with fewer mined windows above its threshold keeps none
JUDGE_TOP = 5
DTW_SHARE = 0.05


def pooled(m, raw, aspect):
    """The reader's features just before its head: mean and max over time, L2-normalised (on the model's device,
    returned on the CPU)."""
    dev = next(m.parameters()).device
    nodes, _, pres = m.points(raw.to(dev), aspect.to(dev))
    h = torch.relu(m.embed(shipped_features(nodes, pres))).transpose(1, 2)
    h = torch.relu(m.bn1(m.conv1(h)))
    h = torch.relu(m.bn2(m.conv2(h)))
    return torch.nn.functional.normalize(torch.cat([h.mean(2), h.amax(2)], 1), dim=1).cpu()


def forms(label):
    """Text forms of an app label to look for in a sentence: '(...)' notes dropped, 'x or y' split."""
    base = re.sub(r"\(.*?\)", "", label.lower()).strip()
    return [f.strip() for f in base.split(" or ") if f.strip()]


def words_in(text, labels):
    t = " " + " ".join(re.findall(r"[a-z']+", text.lower())) + " "
    return [w for w in labels if any(f" {f} " in t for f in forms(w))]


@torch.no_grad()
def prototypes(m, labels, frames, extra=None):
    index = pd.read_csv(TRAIN_CACHE / "index.csv", keep_default_na=False)
    rows = index.index[(index["split"] == "train") & index["label"].isin(labels)].to_numpy()
    raw = frames_of(TRAIN_CACHE, index)
    pos = {w: i for i, w in enumerate(labels)}
    sums, counts = np.zeros((len(labels), m.head.in_features), np.float32), np.zeros(len(labels))
    for b in range(0, len(rows), 256):
        rb = rows[b:b + 256]
        x = np.stack([raw(r)[s + resample_index(n, frames)] for r, s, n in
                      zip(rb, index["start"].to_numpy()[rb], index["length"].to_numpy()[rb])])
        f = pooled(m, torch.from_numpy(x), torch.from_numpy(index["aspect"].to_numpy(np.float32)[rb])[:, None]).numpy()
        for r, v in zip(rb, f):
            sums[pos[index.at[r, "label"]]] += v
            counts[pos[index.at[r, "label"]]] += 1
    for w, v in (extra or []):
        sums[pos[w]] += v
        counts[pos[w]] += 1
    p = sums / np.maximum(counts, 1)[:, None]
    return p / np.maximum(np.linalg.norm(p, axis=1, keepdims=True), 1e-6), counts


@torch.no_grad()
def windows(m, clip, frames):
    """Every window of every length in WINDOWS, stepped by STRIDE, with a hand seen in at least half its frames (hands
    at rest below the frame otherwise match some word): (start, end) frame pairs and their features."""
    c = np.load(LM / (clip + ".npz"))
    raw = raw_frames({k: c[k] for k in c.files})
    n = len(raw)
    hand = np.r_[0, np.cumsum(c["has_left"] | c["has_right"])]
    spans = [(a, a + L) for L in WINDOWS if L <= n for a in range(0, n - L + 1, STRIDE)] or [(0, n)]
    spans = [(a, b) for a, b in spans if hand[b] - hand[a] >= (b - a) / 2]
    if not spans:
        return [], np.zeros((0, m.head.in_features), np.float32)
    x = np.stack([raw[a + resample_index(b - a, frames)] for a, b in spans])
    asp = torch.full((len(spans), 1), float(c["aspect"]))
    return spans, pooled(m, torch.from_numpy(x), asp).numpy()


def window_raw(clip, a, b, frames):
    c = np.load(LM / (clip + ".npz"))
    return raw_frames({k: c[k] for k in c.files})[a + resample_index(b - a, frames)], float(c["aspect"])


@torch.no_grad()
def inputs(m, raw, aspect):
    """The reader's per-frame input features (normalised hands and arms), for DTW."""
    nodes, _, pres = m.points(torch.from_numpy(raw), torch.as_tensor(aspect, dtype=torch.float32)[:, None])
    return shipped_features(nodes, pres).numpy()


def dtw(a, refs):
    """DTW cost of one sequence (T, D) against many (K, T, D), mean over the path length bound; vectorised over K."""
    c = np.linalg.norm(a[None, :, None, :] - refs[:, None, :, :], axis=-1)
    k, n, m = c.shape
    d = np.full((k, n + 1, m + 1), np.inf, np.float32)
    d[:, 0, 0] = 0
    for i in range(1, n + 1):
        for j in range(1, m + 1):
            d[:, i, j] = c[:, i - 1, j - 1] + np.minimum(np.minimum(d[:, i - 1, j], d[:, i, j - 1]), d[:, i - 1, j - 1])
    return d[:, n, m] / (n + m)


def proxy(out, mined, judge="WF_s2", cache=str(DATA / "cache_v5")):
    from exp import RUNS
    app, labels, frames = app_model()
    vocab = json.loads((pathlib.Path(cache) / "vocab.json").read_text(encoding="utf-8"))
    sd = torch.load(RUNS / judge / "best.pt", weights_only=True, map_location="cpu")  # nosemgrep: trailofbits.python.pickles-in-pytorch.pickles-in-pytorch
    jm = Shipped(len(vocab), hidden=sd["embed.weight"].shape[0], kernel=sd["conv1.weight"].shape[2])
    jm.load_state_dict(sd)
    jm.eval()
    isl = torch.tensor([not w.startswith("asl:") for w in vocab])
    at = {w: i for i, w in enumerate(vocab)}
    # One ISLRTC dictionary clip per app word, resampled like a window.
    index = pd.read_csv(TRAIN_CACHE / "index.csv", keep_default_na=False)
    raw = frames_of(TRAIN_CACHE, index)
    first = index[(index["source"] == "islrtc") & (index["split"] == "train") & index["label"].isin(labels)].groupby("label").head(1)
    dict_words = first["label"].tolist()
    ref_raw = np.stack([raw(r)[s + resample_index(n, frames)] for r, s, n in zip(first.index, first["start"], first["length"])])
    refs = inputs(app, ref_raw, first["aspect"].to_numpy(np.float32))
    rows = [json.loads(l) for l in open(mined, encoding="utf-8")]
    kept = []
    for d in rows:
        x, aspect = window_raw(d["clip"], *d["frames"], frames)
        with torch.no_grad():
            logit = jm(torch.from_numpy(x[None]), torch.tensor([[aspect]]))[0].masked_fill(~isl, -1e9)
        top = logit.topk(JUDGE_TOP).indices.tolist()
        d["judge_top5"] = d["word"] in at and at[d["word"]] in top
        if d["word"] in dict_words:
            cost = dtw(inputs(app, x[None], np.array([aspect], np.float32))[0], refs)
            rank = int((cost < cost[dict_words.index(d["word"])]).sum())
            d["dtw_rank"] = rank
            d["dtw_near"] = rank < max(1, int(DTW_SHARE * len(dict_words)))
        else:
            d["dtw_rank"], d["dtw_near"] = None, False
        d["proxy"] = bool(d["judge_top5"])
        kept.append(d)
    dest = pathlib.Path(out) / (pathlib.Path(mined).stem + "_proxy.jsonl")
    with open(dest, "w", encoding="utf-8") as f:
        f.writelines(json.dumps(d) + "\n" for d in kept)
    n = len(kept)
    j, t, b = (sum(d[k] for d in kept) for k in ("judge_top5", "dtw_near", "proxy"))
    print(f"JOB mine proxy {pathlib.Path(mined).stem} n={n} judge_top5={j / max(n, 1):.3f} dtw_near={t / max(n, 1):.3f} "
          f"kept={b} judge={judge} status=unverified")


HELD_OUT = DATA / "q3" / "held_out.json"
FACE_MODELS = DATA / "face_models"
SAME_SIGNER = 0.55


def unsafe_recordings(clips):
    """Recordings (uid before "-") that may show a held-out signer, or show no face; checked on one clip each."""
    import cv2
    det = cv2.FaceDetectorYN.create(str(FACE_MODELS / "face_detection_yunet_2023mar.onnx"), "", (320, 320), 0.7)
    rec = cv2.FaceRecognizerSF.create(str(FACE_MODELS / "face_recognition_sface_2021dec.onnx"), "")
    held = np.array([v for vs in json.loads(HELD_OUT.read_text()).values() for v in vs], np.float32)
    held /= np.linalg.norm(held, axis=1, keepdims=True)
    first = {}
    for c in clips:
        first.setdefault(c.rsplit("-", 1)[0], c)
    bad = {}
    for r, clip in first.items():
        cap = cv2.VideoCapture(str(VIDEOS / clip))
        n = cap.get(cv2.CAP_PROP_FRAME_COUNT)
        near, seen = False, 0
        for q in (0.25, 0.5, 0.75):
            cap.set(cv2.CAP_PROP_POS_FRAMES, int(n * q))
            ok, img = cap.read()
            if not ok:
                continue
            det.setInputSize((img.shape[1], img.shape[0]))
            _, f = det.detect(img)
            if f is None:
                continue
            f = f[np.argmax(f[:, 2] * f[:, 3])]
            e = rec.feature(rec.alignCrop(img, f))[0]
            e = e / np.linalg.norm(e)
            seen += 1
            near |= bool((1 - held @ e).min() < SAME_SIGNER)
        cap.release()
        if near or not seen:
            bad[r] = "held-out signer" if near else "no face"
    return bad, len(first)


def to_cache(proxied, out):
    """Windows the judge kept, as training rows: label the word, signer the iSign recording (the uid before "-")."""
    import cache
    kept = [d for d in map(json.loads, open(proxied, encoding="utf-8")) if d.get("proxy")]
    bad, recs = unsafe_recordings([d["clip"] for d in kept])
    reasons = list(bad.values())
    print(f"JOB mine faces recordings={recs} dropped_held_out={reasons.count('held-out signer')} "
          f"dropped_no_face={reasons.count('no face')} windows_dropped={sum(d['clip'].rsplit('-', 1)[0] in bad for d in kept)} "
          f"of {len(kept)}", flush=True)

    def samples():
        for d in kept:
            if d["clip"].rsplit("-", 1)[0] in bad:
                continue
            c = np.load(LM / (d["clip"] + ".npz"))
            a, b = d["frames"]
            yield {k: c[k][a:b] for k in ("left", "right", "pose", "face", "has_left", "has_right", "has_pose", "has_face")} | {
                "aspect": float(c["aspect"]), "label": d["word"], "split": "train", "source": "isign",
                "signer": "isign:" + d["clip"].rsplit("-", 1)[0], "clip": f"{d['clip']}#{a}-{b}"}
    cache.build(pathlib.Path(out), samples())


def threshold(pos, neg):
    """Lowest score that keeps estimated precision at PRECISION: a clip without the word clears it at the negatives'
    rate, so that share of the positive clips above it is counted as wrong."""
    pos, neg = np.sort(pos)[::-1], np.asarray(neg)
    for k in range(len(pos), 0, -1):
        t = pos[k - 1]
        wrong = (neg >= t).mean() * len(pos) if len(neg) else 0.0
        if k >= MIN_KEEP and 1 - wrong / k >= PRECISION:
            return float(t)
    return None


def mine(out, rnd=1, limit=0, prev=None):
    m, labels, frames = app_model()
    m = m.eval().to("cuda" if torch.cuda.is_available() else "cpu")
    extra = []
    if prev:
        for d in map(json.loads, open(prev, encoding="utf-8")):
            spans, f = windows(m, d["clip"], frames)
            extra.append((d["word"], f[spans.index(tuple(d["frames"]))]))
    proto, counts = prototypes(m, labels, frames, extra)
    text = {r["uid"]: r["text"] for r in csv.DictReader(open(TABLE, encoding="utf-8"))}
    done = [json.loads(l) for l in open(LM / "manifest.jsonl", encoding="utf-8")]
    clips = sorted({d["id"] for d in done if d["state"] == "ok"})
    clips = clips[:limit] if limit else clips
    best, hits = [], []
    for i, clip in enumerate(clips):
        spans, f = windows(m, clip, frames)
        s = f @ proto.T if len(spans) else np.full((1, len(labels)), -1.0)  # windows x words
        j = s.argmax(0)
        best.append((clip, s.max(0), j, spans))
        hits.append(set(words_in(text.get(clip[:-4], ""), labels)))
        if i % 500 == 0:
            print(f"mine r{rnd}: {i}/{len(clips)}", flush=True)
    found, cuts = [], {}
    for w, word in enumerate(labels):
        pos = [(b[1][w], b) for b, h in zip(best, hits) if word in h]
        if not pos or counts[w] == 0:
            continue
        t = threshold([p for p, _ in pos], [b[1][w] for b, h in zip(best, hits) if word not in h])
        cuts[word] = {"threshold": t, "sentences": len(pos), "kept": 0 if t is None else int(sum(p >= t for p, _ in pos))}
        if t is None:
            continue
        for p, (clip, _, j, spans) in pos:
            if p >= t:
                a, e = spans[j[w]]
                found.append({"clip": clip, "word": word, "frames": [int(a), int(e)],
                              "seconds": [round(a * FRAME_S, 2), round(e * FRAME_S, 2)], "score": round(float(p), 4)})
    out = pathlib.Path(out)
    out.mkdir(parents=True, exist_ok=True)
    with open(out / f"mined_r{rnd}.jsonl", "w", encoding="utf-8") as f:
        f.writelines(json.dumps(d) + "\n" for d in found)
    (out / f"thresholds_r{rnd}.json").write_text(json.dumps(cuts, indent=1), encoding="utf-8")
    words = sum(v["kept"] > 0 for v in cuts.values())
    print(f"JOB mine r{rnd} clips={len(clips)} windows={len(found)} words={words}/{len(cuts)}")


def sheet(out, mined, n=60):
    """Four source frames across each sampled window, numbered, for judging by eye."""
    import cv2
    rows = [json.loads(l) for l in open(mined, encoding="utf-8")]
    pick = random.Random(7).sample(range(len(rows)), min(n, len(rows)))
    tiles = []
    for k, i in enumerate(pick):
        d = rows[i]
        cap = cv2.VideoCapture(str(VIDEOS / d["clip"]))
        a, b = d["seconds"]
        strip = []
        for q in np.linspace(a, b, 4):
            cap.set(cv2.CAP_PROP_POS_MSEC, q * 1000)
            ok, img = cap.read()
            strip.append(cv2.resize(img, (160, 120)) if ok else np.zeros((120, 160, 3), np.uint8))
        img = np.hstack(strip)
        cv2.putText(img, f"{k} {d['word']}", (4, 16), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 0, 255), 1)
        tiles.append(img)
    pathlib.Path(out).mkdir(parents=True, exist_ok=True)
    for p in range(0, len(tiles), 15):
        cv2.imwrite(str(pathlib.Path(out) / f"{pathlib.Path(mined).stem}_sheet{p // 15}.jpg"), np.vstack(tiles[p:p + 15]))
    (pathlib.Path(out) / f"{pathlib.Path(mined).stem}_sample.json").write_text(json.dumps([rows[i] for i in pick]))


def check(out, mined, wrong):
    sample = json.loads((pathlib.Path(out) / f"{pathlib.Path(mined).stem}_sample.json").read_text())
    bad = {int(x) for x in open(wrong).read().split()}
    p = 1 - len(bad) / len(sample)
    print(f"JOB mine check {pathlib.Path(mined).stem} precision={p:.3f} n={len(sample)} wrong={len(bad)} pass={p >= PRECISION}")


if __name__ == "__main__":
    a = sys.argv[1:]
    opt = lambda k, d: type(d)(a[a.index(k) + 1]) if k in a else d  # noqa: E731
    if a[0] == "mine":
        mine(a[1], opt("--round", 1), opt("--limit", 0), opt("--prev", ""))
    elif a[0] == "sheet":
        sheet(a[1], a[2], opt("--n", 60))
    elif a[0] == "cache":
        to_cache(a[1], a[2])
    elif a[0] == "proxy":
        proxy(a[1], a[2], opt("--judge", "WF_s2"), opt("--cache", str(DATA / "cache_v5")))
    else:
        check(a[1], a[2], a[3])
