"""Stage Q3 over C:\\work\\ml-data: catalogue the ISLRTC dictionary, RKMVU and Kaggle ISL 60 videos, landmark them like the
phone, cut the sign span, embed the signer's face and cluster signers, with a contact sheet per cluster.

    python q3.py catalog | landmarks [--workers 12] | spans | faces | clusters
Outputs in C:\\work\\ml-data\\q3: catalog.csv, spans.csv, faces.npy + face_crops.npy, clusters.csv, sheets\\*.png
"""
import hashlib
import json
import os
import pathlib
import re
import subprocess
import sys

import numpy as np
import pandas as pd

import guard
from guard import DATA

OUT = DATA / "q3"
LM = DATA / "landmarks"
FACE_MODELS = DATA / "face_models"
SOURCES = {"islrtc": "islrtc_dict", "rkmvu": "rkmvu/videos", "kaggle60": "isl_kaggle60"}
VIDEO = {".mp4", ".mov", ".mkv", ".webm", ".avi"}


def word_of(source, path: pathlib.Path, rk_titles):
    if source == "rkmvu":
        r = rk_titles.get(path.stem, {})
        return str(r.get("word", "")).lower(), int(r.get("variant", 1) or 1), False
    stem = path.stem if source == "islrtc" else path.parent.name
    explanation = "(explanation)" in stem.lower()
    m = re.search(r"\(sign[ _]*(\d+)\)", stem, re.I)
    variant = int(m.group(1)) if m else 1
    w = re.sub(r"\((sign[ _]*\d+|explanation)\)", "", stem, flags=re.I)
    w = re.sub(r"^\d+\.\s*", "", w.replace("_-_", "-").replace("_", " "))
    return re.sub(r"\s+", " ", w).strip().lower(), variant, explanation


def head_hash(p: pathlib.Path) -> str:
    with open(p, "rb") as f:
        return hashlib.sha256(f.read(1 << 22)).hexdigest()[:20] + f"-{p.stat().st_size}"


def catalog():
    rk = {}
    man = DATA / "rkmvu" / "manifest.jsonl"
    if man.exists():
        for line in man.read_text(encoding="utf-8").splitlines():
            r = json.loads(line)
            rk[r["id"]] = r
    # Q2 was skipped (owner); clips it landed before it was stopped are kept on disk but left out.
    before = OUT / "islrtc_before_q2.txt"
    before = set(before.read_text(encoding="utf-8").splitlines()) if before.exists() else None
    rows = []
    for source, sub in SOURCES.items():
        root = DATA / sub
        for p in sorted(root.rglob("*")):
            # RKMVU is still downloading: only clips its manifest marks done, never part files or fragments.
            if p.suffix.lower() not in VIDEO or (source == "rkmvu" and rk.get(p.stem, {}).get("state") != "ok"):
                continue
            rel = p.relative_to(DATA)
            if source == "islrtc" and before is not None and str(rel) not in before:
                continue
            word, variant, expl = word_of(source, p, rk)
            folder = rel.parts[1] if len(rel.parts) > 2 else ""
            rows.append({"clip": str(rel), "source": source, "folder": folder, "word": word, "variant": variant,
                         "explanation": expl, "hash": head_hash(p)})
    df = pd.DataFrame(rows)
    # Language subfolders repeat clips byte for byte; only the first copy is kept for training.
    df["duplicate_of"] = df.groupby("hash")["clip"].transform("first")
    df.loc[df["duplicate_of"] == df["clip"], "duplicate_of"] = ""
    # RKMVU stories and sentences over 30 s are kept on disk but never landmarked or trained on (owner, 2026-10-28).
    df["skip_long"] = [int(s == "rkmvu" and float(rk.get(pathlib.PurePath(c).stem, {}).get("duration") or 0) > 30)
                       for s, c in zip(df["source"], df["clip"])]
    OUT.mkdir(parents=True, exist_ok=True)
    df.to_csv(OUT / "catalog.csv", index=False)
    print(df.groupby("source").agg(clips=("clip", "size"), words=("word", "nunique"),
                                   dupes=("duplicate_of", lambda s: (s != "").sum())).to_string(), flush=True)


def priority(df):
    """App words, and words likely from 2 or more signers (seen in both sources or as a second variant), come first,
    so the splits and B1 can start on a partial cache."""
    app = set(json.loads((pathlib.Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "assets" / "content" / "models"
                          / "sign_reader.json").read_text(encoding="utf-8"))["labels"])
    if (OUT / "clusters.csv").exists():
        # After the first clustering the real signer counts replace the guess.
        cl = pd.read_csv(OUT / "clusters.csv", keep_default_na=False)
        takes = cl[cl["cluster"] >= 0].groupby("word")["cluster"].nunique()
    else:
        takes = df.groupby("word").apply(lambda g: len(set(zip(g["source"], g["variant"]))), include_groups=False)
    multi = set(takes[takes >= 2].index)
    return df["word"].isin(app | multi)


def landmarks(workers, part="all"):
    # One landmark run at a time: a second one (another chain restarting Q3) leaves at once. The OS drops the lock
    # when this process exits, however it exits.
    import msvcrt
    lock = open(OUT / "landmarks.lock", "a+", encoding="utf-8")
    try:
        msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
    except OSError:
        print("landmarks already running elsewhere; leaving", flush=True)
        return 0
    df = pd.read_csv(OUT / "catalog.csv", keep_default_na=False)
    df = df[(df["duplicate_of"] == "") & (df["source"] != "kaggle60")]
    # RKMVU clips over 30 s are stories and sentences: kept out of S and B, landmarked last after every word clip.
    man = DATA / "rkmvu" / "manifest.jsonl"
    dur = {}
    for line in man.read_text(encoding="utf-8").splitlines() if man.exists() else []:
        r = json.loads(line)
        dur[r["id"]] = float(r.get("duration") or 0)
    stem = df["clip"].map(lambda c: pathlib.PurePath(c).stem)
    long = (df["source"] == "rkmvu") & stem.map(lambda v: dur.get(v, 0) > 30)
    (OUT / "later_long.txt").write_text("\n".join(df[long]["clip"]), encoding="utf-8")
    later = df[long]["clip"]
    df = df[~long]
    first = priority(df)
    # Already-landmarked clips are skipped through the manifest, so "rest" after "first" does only the remainder.
    # After the priority words: RKMVU's short clips, then ISLRTC words with one signer, which barely move app-view scores.
    rk = (df["source"] == "rkmvu").to_numpy()
    rest = pd.concat([df[~first & rk], df[~first & ~rk]])
    todo = pd.concat([df[first], rest])["clip"] if part == "all" else (df[first] if part == "first" else rest)["clip"]
    todo = pd.concat([todo, later]) if part != "first" else todo
    (OUT / "landmark_list.txt").write_text("\n".join(todo), encoding="utf-8")
    r = subprocess.run([sys.executable, "-s", str(pathlib.Path(__file__).with_name("landmark_split.py")), str(DATA),
                        str(LM), "--list", str(OUT / "landmark_list.txt"), "--workers", str(workers), "--name",
                        "Q3_landmarks"])
    return r.returncode


def span(c, card_s=1.0, pad=3):
    """Sign span from landmarks: segments are split where no wrist is up or moving for card_s (the explanation card),
    and trimmed to active frames; the first segment with 0.3 s of activity is the sign, later ones are continuous
    signing."""
    t = c["t"] / 1000.0
    k = np.array([1.0, 1.0 / float(c["aspect"])])
    hp, hl, hr = c["has_pose"], c["has_left"], c["has_right"]
    pose = c["pose"] * k
    sw = np.median(np.linalg.norm(pose[hp, 11] - pose[hp, 12], axis=1)) if hp.any() else 0.1
    sh_y = np.median((pose[hp, 11, 1] + pose[hp, 12, 1]) / 2) if hp.any() else 0.5
    up = np.zeros(len(t), bool)
    moving = np.zeros(len(t), bool)
    for hand, has in ((c["left"] * k, hl), (c["right"] * k, hr)):
        w = hand[:, 0]
        speed = np.r_[0, np.linalg.norm(np.diff(w, axis=0), axis=1) / np.maximum(np.diff(t), 1e-3)] / max(sw, 1e-3)
        up |= has & ((w[:, 1] < sh_y + 1.6 * sw) | (speed > 1.0))
        moving |= has & (speed > 0.3)
    # The signer stays in view while the card shows, hands at rest, so a long rest is the cut, not a missing body.
    card = ~up
    # Cuts: runs of rest frames lasting card_s or more.
    cuts, i = [], 0
    while i < len(t):
        if card[i]:
            j = i
            while j + 1 < len(t) and card[j + 1]:
                j += 1
            if t[j] - t[i] >= card_s or i == 0 or j == len(t) - 1:
                cuts.append((i, j + 1))
            i = j + 1
        else:
            i += 1
    bounds = [0] + [x for c in cuts for x in c] + [len(t)]
    segs = []
    for a, b in zip(bounds[::2], bounds[1::2]):
        act = np.flatnonzero(up[a:b])
        if len(act) and t[a + act[-1]] - t[a + act[0]] >= 0.3:
            segs.append((max(a, a + act[0] - pad), min(b, a + act[-1] + pad + 1)))
    return segs, moving


def word_span(t, segs, moving, long_s=6.0, cap_s=4.0, still_s=0.25):
    """The headword inside the first segment. Many letter-folder clips run straight from the sign into its
    explanation, so a first segment over long_s ends at its first stillness of still_s between 0.8 s and 2 * cap_s,
    else at cap_s."""
    a, b = segs[0]
    if (t[b - 1] - t[a]) / 1000 <= long_s:
        return a, b, "whole"
    i, run_from = a, None
    b = min(b, int(np.searchsorted(t, t[a] + 2 * cap_s * 1000)))
    while i < b:
        if not moving[i]:
            run_from = i if run_from is None else run_from
            if (t[i] - t[run_from]) / 1000 >= still_s and (t[run_from] - t[a]) / 1000 >= 0.8:
                return a, run_from, "pause"
        else:
            run_from = None
        i += 1
    return a, int(np.searchsorted(t, t[a] + cap_s * 1000)), "capped"


def spans():
    df = pd.read_csv(OUT / "catalog.csv", keep_default_na=False)
    rows = []
    for clip in df[(df["duplicate_of"] == "") & (df["source"] != "kaggle60")]["clip"]:
        f = LM / (clip + ".npz")
        if not f.exists():
            continue
        c = dict(np.load(f))
        segs, moving = span(c)
        t = c["t"]
        a, b, how = word_span(t, segs, moving) if segs else (-1, -1, "none")
        # The rest of the first segment after the headword counts as continuous signing too.
        rest = ([[b, segs[0][1]]] if segs and b < segs[0][1] else []) + [[int(x), int(y)] for x, y in segs[1:]]
        rows.append({"clip": clip, "frames": len(t), "seconds": round(t[-1] / 1000, 2), "segments": len(segs),
                     "sign_from": a, "sign_to": b, "cut": how,
                     "sign_ms_from": int(t[a]) if segs else -1, "sign_ms_to": int(t[b - 1]) if segs else -1,
                     "rest": json.dumps([[int(x), int(y)] for x, y in rest])})
    pd.DataFrame(rows).to_csv(OUT / "spans.csv", index=False)
    s = pd.DataFrame(rows)
    # Headwords cut at the fixed cap carry the most label noise; G3 and cleanlab look at them apart.
    capped = set(s.loc[s["cut"] == "capped", "clip"])
    cat = pd.read_csv(OUT / "catalog.csv", keep_default_na=False)
    cat["span_capped"] = cat["clip"].isin(capped).astype(int)
    cat.to_csv(OUT / "catalog.csv", index=False)
    print(f"spans: {len(s)} clips, {(s['segments'] > 0).mean():.3f} with a sign, "
          f"median sign {((s['sign_ms_to'] - s['sign_ms_from']) / 1000)[s['segments'] > 0].median():.2f} s, "
          f"{(s['segments'] > 1).sum()} with later signing", flush=True)


def face_arrays(clips):
    """faces.npy and face_crops.npy in catalog order; when the catalog changed, rows already done move to their clip's
    new row (faces_clips.txt keeps the order they were written in)."""
    order = OUT / "faces_clips.txt"
    n = len(clips)
    old = order.read_text(encoding="utf-8").splitlines() if order.exists() else None
    if old is not None and old != list(clips):
        pos = {c: i for i, c in enumerate(clips)}
        for name, shape, dt in (("faces.npy", (3, 128), np.float32), ("face_crops.npy", (3, 112, 112, 3), np.uint8)):
            prev = np.load(OUT / name, mmap_mode="r")
            new = np.lib.format.open_memmap(OUT / (name + ".tmp.npy"), "w+", dt, (n, *shape))
            for i, c in enumerate(old):
                if c in pos and i < len(prev):
                    new[pos[c]] = prev[i]
            new.flush()
            del new, prev
            os.replace(OUT / (name + ".tmp.npy"), OUT / name)
    if old is None or old != list(clips):
        for name, shape, dt in (("faces.npy", (3, 128), np.float32), ("face_crops.npy", (3, 112, 112, 3), np.uint8)):
            if not (OUT / name).exists():
                np.lib.format.open_memmap(OUT / name, "w+", dt, (n, *shape)).flush()
        guard.write_atomic(order, "\n".join(clips).encode("utf-8"))


def faces(shard=0, of=1):
    """SFace embedding of the largest face in three frames of the sign span (whole clip for Kaggle 60). Run as
    shard k of K processes, each on rows i % K == k; face_arrays must run once before the shards start."""
    import cv2
    cv2.setNumThreads(1)  # one thread per shard process; OpenCV's own pool oversubscribes the CPU 16-fold
    det = cv2.FaceDetectorYN.create(str(FACE_MODELS / "face_detection_yunet_2023mar.onnx"), "", (320, 320), 0.7)
    rec = cv2.FaceRecognizerSF.create(str(FACE_MODELS / "face_recognition_sface_2021dec.onnx"), "")
    df = pd.read_csv(OUT / "catalog.csv", keep_default_na=False)
    df = df[df["duplicate_of"] == ""]
    sp = pd.read_csv(OUT / "spans.csv").set_index("clip") if (OUT / "spans.csv").exists() else pd.DataFrame()
    if of == 1:
        face_arrays(list(df["clip"]))
    done = guard.Manifest(OUT / "faces_manifest.jsonl")
    man = done if of == 1 else guard.Manifest(OUT / f"faces_manifest.{shard}.jsonl")
    emb = np.load(OUT / "faces.npy", mmap_mode="r+")
    crops = np.load(OUT / "face_crops.npy", mmap_mode="r+")
    status = "done"
    for i, clip in enumerate(df["clip"]):
        if i % of != shard or done.done(clip) or man.done(clip):
            continue
        if i % 200 == 0 and (s := guard.check(OUT)):
            status = s
            break
        cap = cv2.VideoCapture(str(DATA / clip))
        dur = cap.get(cv2.CAP_PROP_FRAME_COUNT) / (cap.get(cv2.CAP_PROP_FPS) or 25) * 1000
        a, b = (sp.loc[clip, "sign_ms_from"], sp.loc[clip, "sign_ms_to"]) if clip in sp.index and sp.loc[clip, "sign_ms_from"] >= 0 else (0, dur)
        got = 0
        for j, q in enumerate((0.25, 0.5, 0.75)):
            cap.set(cv2.CAP_PROP_POS_MSEC, a + (b - a) * q)
            ok, img = cap.read()
            if not ok:
                continue
            h, w = img.shape[:2]
            s = 640 / max(h, w)
            img = cv2.resize(img, (round(w * s), round(h * s)))
            det.setInputSize((img.shape[1], img.shape[0]))
            _, f = det.detect(img)
            if f is None:
                emb[i, j] = 0
                continue
            f = f[np.argmax(f[:, 2] * f[:, 3])]
            crop = rec.alignCrop(img, f)
            emb[i, j] = rec.feature(crop)[0]
            crops[i, j] = crop
            got += 1
        cap.release()
        man.mark(clip, "ok" if got else "quarantined", "" if got else "no face", row=i, faces=got)
    emb.flush()
    crops.flush()
    guard.job_line("Q3_faces", man, status)


def clusters(threshold=0.55, min_size=5):
    import cv2
    from sklearn.cluster import AgglomerativeClustering
    df = pd.read_csv(OUT / "catalog.csv", keep_default_na=False)
    df = df[df["duplicate_of"] == ""].reset_index(drop=True)
    emb = np.load(OUT / "faces.npy", mmap_mode="r")
    crops = np.load(OUT / "face_crops.npy", mmap_mode="r")
    e = np.asarray(emb)
    seen = np.linalg.norm(e, axis=2) > 0
    mean = (e / np.maximum(np.linalg.norm(e, axis=2, keepdims=True), 1e-6)).sum(1)
    ok = seen.any(1)
    mean[ok] /= np.linalg.norm(mean[ok], axis=1, keepdims=True)
    lab = np.full(len(df), -1)
    model = AgglomerativeClustering(n_clusters=None, metric="cosine", linkage="average", distance_threshold=threshold)
    lab[ok] = model.fit_predict(mean[ok])
    sizes = pd.Series(lab[ok]).value_counts()
    small = set(sizes[sizes < min_size].index)
    lab = np.array([-1 if l in small else l for l in lab])
    # Renumber by size so cluster 0 is the largest; -1 is no face or a cluster too small to judge.
    order = {l: k for k, l in enumerate(pd.Series(lab[lab >= 0]).value_counts().index)}
    df["cluster"] = [order.get(l, -1) for l in lab]
    df.to_csv(OUT / "clusters.csv", index=False)
    sheets = OUT / "sheets"
    sheets.mkdir(exist_ok=True)
    rng = np.random.default_rng(7)
    for c, g in df[df["cluster"] >= 0].groupby("cluster"):
        pick = rng.choice(g.index.to_numpy(), min(24, len(g)), replace=False)
        tiles = []
        for i in pick:
            j = int(np.flatnonzero(seen[i])[0])
            tiles.append(np.asarray(crops[i, j]))
        while len(tiles) % 6:
            tiles.append(np.full((112, 112, 3), 255, np.uint8))
        grid = np.vstack([np.hstack(tiles[r:r + 6]) for r in range(0, len(tiles), 6)])
        cv2.imwrite(str(sheets / f"cluster_{c:03d}.png"), grid)
    summary = df.groupby("cluster").agg(clips=("clip", "size"), words=("word", "nunique"),
                                        sources=("source", lambda s: ",".join(sorted(set(s)))))
    summary.to_csv(OUT / "cluster_summary.csv")
    print(summary.head(40).to_string(), flush=True)


if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "catalog":
        catalog()
    elif cmd == "landmarks":
        w = int(sys.argv[sys.argv.index("--workers") + 1]) if "--workers" in sys.argv else 12
        sys.exit(landmarks(w, sys.argv[sys.argv.index("--part") + 1] if "--part" in sys.argv else "all"))
    elif cmd == "spans":
        spans()
    elif cmd == "faces" and "--shard" in sys.argv:
        faces(int(sys.argv[sys.argv.index("--shard") + 1]), int(sys.argv[sys.argv.index("--of") + 1]))
    elif cmd == "faces":
        # K shard processes on one memory-mapped array, then their manifests fold into the main one.
        k = int(sys.argv[sys.argv.index("--workers") + 1]) if "--workers" in sys.argv else 8
        cat = pd.read_csv(OUT / "catalog.csv", keep_default_na=False)
        face_arrays(list(cat.loc[cat["duplicate_of"] == "", "clip"]))
        procs = [subprocess.Popen([sys.executable, "-s", __file__, "faces", "--shard", str(i), "--of", str(k)]) for i in range(k)]
        codes = [q.wait() for q in procs]
        main = guard.Manifest(OUT / "faces_manifest.jsonl")
        for i in range(k):
            part = OUT / f"faces_manifest.{i}.jsonl"
            if part.exists():
                for line in part.read_text(encoding="utf-8").splitlines():
                    r = json.loads(line)
                    main.mark(r.pop("id"), r.pop("state"), r.pop("reason", ""), **{x: v for x, v in r.items() if x != "attempts"})
                os.replace(part, part.with_name(part.name + ".merged"))
        print(f"JOB Q3_faces shards={k} exit={codes}", flush=True)
    elif cmd == "clusters":
        clusters()
