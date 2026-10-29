"""Stage S: one landmark cache over every source, split by signer, never by clip.

Train: INCLUDE, Kaggle signers but the held-out one, ISLRTC and RKMVU signer clusters but the held-out ones,
contributors not held out. Validation (decisions, tuning, early stop): one ISLRTC cluster, one RKMVU cluster, the
held-out Kaggle signer. Test (milestones only): the other held-out clusters, plus FDMSE test signers and phone clips
when they exist. The old 269-clip ISLRTC set is kept as its own report for continuity.

    python splits.py [--out C:/work/ml-data/cache_v4] [--contrib DIR ...]
"""
import json
import pathlib
import pickle
import sys

import numpy as np
import pandas as pd

import cache
from guard import DATA

Q3 = DATA / "q3"
LM = DATA / "landmarks"
OLD = DATA
APP = pathlib.Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "assets" / "content"


NONSIGN = "__nonsign__"
HOLD_INCLUDE_TEST = "--hold-include-test" in sys.argv


def app_words():
    return set(json.loads((APP / "models" / "sign_reader.json").read_text(encoding="utf-8"))["labels"])


def face_means():
    """Per clip in clusters.csv, the unit mean of its three face embeddings, and whether any face was seen."""
    e = np.asarray(np.load(Q3 / "faces.npy", mmap_mode="r"), np.float32)
    e = e / np.maximum(np.linalg.norm(e, axis=2, keepdims=True), 1e-6)
    m = e.sum(1)
    ok = np.abs(e).sum((1, 2)) > 0
    m[ok] /= np.linalg.norm(m[ok], axis=1, keepdims=True)
    return m, ok


def kaggle_clusters(cl):
    """Kaggle signer letter to the face cluster most of its clips fall in."""
    letters = json.loads((OLD / "isl_more" / "kaggle_signers.json").read_text(encoding="utf-8"))
    k = cl[cl["source"] == "kaggle60"].copy()
    k["key"] = k["clip"].map(lambda c: "/".join(pathlib.PurePath(c).parts[-2:]))
    k["letter"] = k["key"].map(letters)
    k = k[k["letter"].notna() & (k["cluster"] >= 0)]
    return {f"kaggle_{a}": int(g["cluster"].mode()[0]) for a, g in k.groupby("letter")}


def held_out(cl, words, kag):
    """Signer identity is the joint face cluster over ISLRTC, RKMVU and Kaggle, so one person never sits on both sides.
    Validation: the held-out Kaggle signer's cluster, plus per source the cluster after the largest with the most app
    words; test: the next one. Clusters holding a Kaggle training signer stay in training. Once written,
    held_out.json freezes the choice: later splits map their clusters onto it by face, so val and test keep their
    signers and only training grows."""
    frozen = Q3 / "held_out.json"
    m, ok = face_means()
    ids = cl["cluster"].to_numpy()
    centre = lambda c: m[ok & (ids == c)].mean(0)  # noqa: E731
    if frozen.exists():
        f = json.loads(frozen.read_text())
        pick = {"val": [], "test": []}
        for role in ("val", "test"):
            for v in f[role]:
                v = np.asarray(v) / np.linalg.norm(v)
                for c in set(ids[ids >= 0]):
                    z = centre(c)
                    if np.isfinite(z).all() and z @ v / np.linalg.norm(z) > 0.6:
                        pick[role].append(int(c))
        return pick
    train_kag = {c for name, c in kag.items() if name != "kaggle_C"}
    pick = {"val": [kag["kaggle_C"]] if "kaggle_C" in kag else [], "test": []}
    for src in ("islrtc", "rkmvu"):
        g = cl[(cl["source"] == src) & (cl["cluster"] >= 0) & ~cl["cluster"].isin(train_kag | set(pick["val"]))]
        stats = g.groupby("cluster").agg(n=("clip", "size"), app=("word", lambda w: len(set(w) & words)))
        if len(stats) < 2:
            continue
        rest = stats.drop(stats["n"].idxmax()).sort_values(["app", "n"], ascending=False)
        pick["val"].append(int(rest.index[0]))
        pick["test"].append(int(rest.index[1]))
    frozen.write_text(json.dumps({role: [centre(c).tolist() for c in v] for role, v in pick.items()}), encoding="utf-8")
    return pick


def from_q3(words):
    cl = pd.read_csv(Q3 / "clusters.csv", keep_default_na=False)
    sp = pd.read_csv(Q3 / "spans.csv").set_index("clip")
    kag = kaggle_clusters(cl)
    pick = held_out(cl, words, kag)
    val, test = set(pick["val"]), set(pick["test"])
    long = Q3 / "later_long.txt"
    long = set(long.read_text(encoding="utf-8").splitlines()) if long.exists() else set()
    out = []
    for r in cl.itertuples():
        if r.source == "kaggle60" or r.explanation in (True, "True") or r.clip not in sp.index or r.clip in long:
            continue
        s = sp.loc[r.clip]
        if s["sign_from"] < 0:
            continue
        c = dict(np.load(LM / (r.clip + ".npz")))
        a, b = int(s["sign_from"]), int(s["sign_to"])
        split = "val" if r.cluster in val else "test" if r.cluster in test else "train" if r.cluster >= 0 else "unassigned"
        part = lambda a, b: {k: c[k][a:b] for k in ("left", "right", "pose", "face", "has_left", "has_right", "has_pose",  # noqa: E731
                                                     "has_face")}
        meta = {"aspect": float(c["aspect"]), "signer": f"c{r.cluster}" if r.cluster >= 0 else f"u:{r.clip}", "split": split,
                "span_capped": int(r.span_capped)}
        out.append(part(a, b) | meta | {"label": r.word, "source": r.source, "clip": r.clip})
        # B10 non-sign windows: the rest before the headword and the card pause after it, 0.5 s or longer.
        t = c["t"]
        nxt = json.loads(s["rest"])[0][0] if isinstance(s.get("rest"), str) and s["rest"] != "[]" else len(t)
        for x, y, tag in ((0, a, "before"), (b, nxt, "after")):
            if y - x >= 2 and t[y - 1] - t[x] >= 500:
                out.append(part(x, y) | meta | {"label": NONSIGN, "source": "nonsign", "clip": f"{r.clip}#{tag}"})
    return out, pick, kag


def leakage(pick, near=0.6, share=0.01):
    """G2: a held-out cluster leaks when more than [share] of its clips have a face within cosine [near] of some
    training clip's face (SFace's same-person threshold is 0.363, so 0.6 is a near-certain match)."""
    cl = pd.read_csv(Q3 / "clusters.csv", keep_default_na=False)
    e = np.load(Q3 / "faces.npy", mmap_mode="r")
    e = np.asarray(e) / np.maximum(np.linalg.norm(e, axis=2, keepdims=True), 1e-6)
    seen = np.abs(e).sum(2) > 0
    m = e.sum(1)
    ok = seen.any(1)
    m[ok] /= np.linalg.norm(m[ok], axis=1, keepdims=True)
    held = {c for v in pick.values() for c in v}
    # The Kaggle validation signer is held out by name too; its cluster is checked like the others.
    tr = np.flatnonzero(ok & (cl["cluster"] >= 0).to_numpy() & ~cl["cluster"].isin(held).to_numpy())
    leaks = {}
    for c in held:
        rows = np.flatnonzero(ok & (cl["cluster"] == c).to_numpy())
        if not len(rows):
            continue
        sim = (m[rows] @ m[tr].T).max(1)
        frac = float((sim > near).mean())
        if frac > share:
            leaks[int(c)] = round(frac, 3)
    return {"clean": not leaks, "leaks": leaks, "near": near}


def near_duplicates(samples, rms=0.05):
    """The same recording re-encoded or re-uploaded in another source: hand and arm paths (16 frames, shoulder-centred,
    shoulder-width units) within [rms]. Across a train / held-out pair the train copy goes, so no held-out clip is
    seen in training; inside one split the later copy goes."""
    import torch
    rank = {"val": 0, "test": 1, "old269": 2, "train": 3}
    idx = [i for i, s in enumerate(samples) if s["source"] != "nonsign" and s["split"] in rank]
    sig = np.zeros((len(idx), 16 * 48 * 2), np.float32)
    for n, i in enumerate(idx):
        s = samples[i]
        t = len(s["left"])
        f = np.linspace(0, t - 1, 16).round().astype(int)
        pose = np.asarray(s["pose"], np.float32)[f]
        hp = np.asarray(s["has_pose"])[f] > 0
        if not hp.any():
            continue
        centre = ((pose[hp, 11] + pose[hp, 12]) / 2).mean(0)
        scale = max(float(np.linalg.norm(pose[hp, 11] - pose[hp, 12], axis=1).mean()), 1e-3)
        pts = np.concatenate([np.asarray(s["left"], np.float32)[f] * (np.asarray(s["has_left"])[f] > 0)[:, None, None],
                              np.asarray(s["right"], np.float32)[f] * (np.asarray(s["has_right"])[f] > 0)[:, None, None],
                              pose[:, 11:17]], 1)
        sig[n] = ((pts - centre) / scale).reshape(-1)
    dev = "cuda" if torch.cuda.is_available() else "cpu"
    z = torch.from_numpy(sig).to(dev)
    drop, pairs = set(), 0
    for a in range(0, len(z), 2048):
        d = torch.cdist(z[a:a + 2048], z) / np.sqrt(z.shape[1])
        for u, v in (d < rms).nonzero().tolist():
            i, j = idx[a + u], idx[v]
            if i >= j or i in drop or j in drop:
                continue
            pairs += 1
            ri, rj = rank[samples[i]["split"]], rank[samples[j]["split"]]
            drop.add(j if rj >= ri else i)
    kept = [s for i, s in enumerate(samples) if i not in drop]
    by = pd.Series([samples[i]["split"] for i in drop]).value_counts().to_dict()
    return kept, {"pairs": pairs, "dropped": len(drop), "dropped_by_split": by, "rms": rms}


def from_include(kag=None, pick=None):
    """Kaggle signers take their face cluster as identity and that cluster's split; INCLUDE (no videos here, so no
    faces) stays one training signer."""
    d = pickle.load(open(OLD / "isl_more" / "include_kaggle.pkl", "rb"))  # nosemgrep
    kag, pick = kag or {}, pick or {"val": [], "test": []}
    out = []
    for i, s in enumerate(d["samples"]):
        c = kag.get(s["signer"])
        split = "val" if s["split"] == "kaggle_test" or c in pick["val"] else "test" if c in pick["test"] else "train"
        # INCLUDE's own test split stays out of training so the shipped floor (0.979) can be measured; same signers as
        # its train split, so it is a floor check, not a new-signer score.
        if split == "train" and s["signer"] == "include" and s["split"] == "test" and HOLD_INCLUDE_TEST:
            split = "include_test"
        out.append({**s, "aspect": d["aspect"], "source": "include" if s["signer"] == "include" else "kaggle",
                    "signer": f"c{c}" if c is not None else s["signer"], "split": split, "clip": f"include_kaggle/{i}"})
    return out


def from_old_islrtc():
    d = pickle.load(open(OLD / "islrtc" / "islrtc_words.pkl", "rb"))  # nosemgrep
    return [{**s, "aspect": s.get("aspect", d["aspect"]), "signer": "islrtc_old", "source": "islrtc_old",
             "split": "old269", "clip": f"islrtc_old/{i}"} for i, s in enumerate(d["samples"])]


def from_contrib(dirs):
    """Contributed clips; one contributor in five, by a stable hash of the signer id, goes to test, the rest train."""
    import hashlib
    import import_contrib
    out = []
    for d in dirs:
        for signer, r in import_contrib.records(pathlib.Path(d)):
            w = r["window"]
            gloss = (r.get("correction") or " ".join(r["gloss"])).lower().split()
            signs = w.get("signs", [])
            if len(signs) != len(gloss):
                continue
            test = int(hashlib.sha256(signer.encode()).hexdigest(), 16) % 5 == 0
            for k, (s, label) in enumerate(zip(signs, gloss)):
                if len(s["frames"]) >= 3:
                    out.append({**import_contrib.sample(s["frames"], float(w.get("aspect", 480 / 848))), "label": label,
                                "signer": f"contrib:{signer}", "source": "contrib", "split": "test" if test else "train",
                                "clip": f"{signer}/{w.get('eventId', 0)}/{k}"})
    return out


def vocabulary(samples, words):
    """Every word with a training clip, plus the app words: set by training data alone, never by val or test."""
    signers = {}
    for s in samples:
        if s["split"] == "train":
            signers.setdefault(s["label"], set()).add(s["signer"])
    return sorted(words | set(signers)), signers


def main(out: pathlib.Path, contrib=(), interim=False):
    words = app_words()
    # Interim: INCLUDE and Kaggle only, which never hold test signers, so the GPU can start before stage Q ends.
    q3, pick, kag = ([], {}, {}) if interim else from_q3(words)
    samples = from_include() if interim else from_include(kag, pick) + q3 + from_old_islrtc() + from_contrib(contrib)
    dupes = {}
    if not interim:
        # Two signers doing one word can come within 0.05; if that drops over 2% of training, 0.02 keeps only copies.
        n_train = sum(s["split"] == "train" for s in samples)
        kept, dupes = near_duplicates(samples, 0.05)
        if dupes["dropped_by_split"].get("train", 0) > 0.02 * n_train:
            kept, dupes = near_duplicates(samples, 0.02)
        dupes["train_share"] = round(dupes["dropped_by_split"].get("train", 0) / max(n_train, 1), 4)
        samples = kept
    # Val and test stay exactly as first fixed: later clips of held-out signers are set aside, never trained on.
    fixed = Q3 / "held_clips.json"
    # --grow-held (final1 onward): new clips of the frozen held-out signers join val and test; never trained on.
    if not interim and fixed.exists() and "--grow-held" not in sys.argv:
        keep = set(json.loads(fixed.read_text(encoding="utf-8")))
        for s in samples:
            if s["split"] in ("val", "test") and s["clip"] not in keep:
                s["split"] = "held_new"
    elif not interim:
        fixed.write_text(json.dumps(sorted(s["clip"] for s in samples if s["split"] in ("val", "test"))), encoding="utf-8")
    vocab, signers = vocabulary(samples, words)
    vset = set(vocab)
    for s in samples:
        s["in_vocab"] = s["label"] in vset
        # Closed-set scoring: a held-out word counts only if training holds it from another signer.
        s["scored"] = (s["split"] in ("val", "test", "old269", "include_test") and s["label"] != NONSIGN
                       and len(signers.get(s["label"], set()) - {s["signer"]}) > 0)
    g2 = {"clean": True, "leaks": {}, "interim": True} if interim else leakage(pick)
    cache.build(out, samples, meta_keys=("label", "split", "signer", "source", "clip", "in_vocab", "scored", "span_capped"))
    (out / "vocab.json").write_text(json.dumps(vocab, ensure_ascii=False), encoding="utf-8")
    import hashlib
    test = sorted(s["clip"] for s in samples if s["split"] == "test")
    (out / "test_hash.txt").write_text(hashlib.sha256("\n".join(test).encode()).hexdigest(), encoding="utf-8")
    (out / "g2.json").write_text(json.dumps(g2, indent=1), encoding="utf-8")
    if not g2["clean"]:
        sys.exit(f"STOP G2: held-out signer near training clips {g2['leaks']}")
    df = pd.read_csv(out / "index.csv", keep_default_na=False)
    report = df.groupby(["split", "source"]).agg(clips=("clip", "size"), signers=("signer", "nunique"),
                                                   words=("label", "nunique"), scored=("scored", lambda x: (x == True).sum()))
    head = f"held out {pick}\nkaggle clusters {kag}\nnear duplicates {dupes}\nvocabulary {len(vocab)}\n"
    (out / "split_report.txt").write_text(head + report.to_string() + "\n", encoding="utf-8")
    print(head + report.to_string(), flush=True)


if __name__ == "__main__":
    a = sys.argv[1:]
    out = pathlib.Path(a[a.index("--out") + 1]) if "--out" in a else DATA / "cache_v4"
    main(out, a[a.index("--contrib") + 1:] if "--contrib" in a else (), "--interim" in a)
