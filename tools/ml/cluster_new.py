"""Clusters the SFace embeddings of the new dictionary clips (faces_new.py) and matches the clusters to the signers
q3.py already found, so a person gets the same signer id in cache_v6b and the rebuilt cache.

    python cluster_new.py [--threshold 0.55] [--min-size 3] [--match 0.5] [--sheets]

Writes SIGN_FACES/clusters.csv (one row per clip), signers.csv (clip,signer) for build_cache_v7.py --signers,
summary.json, and -- with --sheets -- a PNG per matched signer: new clips' crops over the matched q3 cluster's crops.
Signer ids: c<q3 cluster> for a matched existing signer, n<k> for a new one, and u:<clip> for a clip that stands alone
(no face, or a cluster below min-size) so nothing silently claims an identity.
"""

import json
import os
import pathlib
import sys

import numpy as np
import pandas as pd

from guard import DATA

Q3 = DATA / "q3"
NEW = pathlib.Path(os.environ.get("SIGN_FACES", "C:/work/faces_new"))


def unit(e: np.ndarray) -> np.ndarray:
    return e / np.maximum(np.linalg.norm(e, axis=-1, keepdims=True), 1e-6)


def centroids(emb: np.ndarray) -> np.ndarray:
    """Per-clip unit mean of the frames that had a face; zero for a clip with none."""
    seen = np.linalg.norm(emb, axis=-1) > 0
    v = np.where(seen[..., None], unit(emb), 0).sum(1)
    return v / np.maximum(np.linalg.norm(v, axis=1, keepdims=True), 1e-6)


def sheets(df: pd.DataFrame, matched: dict[int, int]) -> None:
    """One PNG per matched signer: the new clips' faces on top, the q3 cluster's own faces underneath."""
    import cv2
    q3 = pd.read_csv(Q3 / "clusters.csv", keep_default_na=False)
    q3["cluster"] = q3["cluster"].astype(int)
    qc = np.load(Q3 / "face_crops.npy", mmap_mode="r")
    qf = np.load(Q3 / "faces.npy", mmap_mode="r")
    nc = np.load(NEW / "crops.npy", mmap_mode="r")
    nf = np.load(NEW / "faces.npy", mmap_mode="r")
    out = NEW / "sheets"
    out.mkdir(parents=True, exist_ok=True)
    rng = np.random.default_rng(7)
    for signer, q in sorted(matched.items()):
        pid = f"c{q}"
        mine = df[df["signer"] == pid].index.to_numpy()
        theirs = q3.index[q3["cluster"] == q].to_numpy()
        rows = []
        for pool, crops, faces, label in ((mine, nc, nf, f"new {pid}"), (theirs, qc, qf, f"q3 c{q}")):
            pick = rng.choice(pool, min(8, len(pool)), replace=False) if len(pool) else []
            tiles = []
            for i in pick:
                j = int(np.flatnonzero(np.linalg.norm(faces[i], axis=1) > 0)[0])
                tiles.append(np.asarray(crops[i, j]))
            while len(tiles) < 8:
                tiles.append(np.full((112, 112, 3), 255, np.uint8))
            row = np.hstack(tiles)
            cv2.putText(row, f"{label} ({len(pool)})", (4, 108), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 0, 255), 1)
            rows.append(row)
        cv2.imwrite(str(out / f"{pid}.png"), np.vstack(rows))


def main() -> None:
    a = sys.argv[1:]
    get = lambda k, d: float(a[a.index(k) + 1]) if k in a else d
    threshold, min_size, match = get("--threshold", 0.55), int(get("--min-size", 3)), get("--match", 0.5)

    names = (NEW / "clips.txt").read_text(encoding="utf-8").splitlines()
    emb = np.load(NEW / "faces.npy").astype(np.float64)
    v = centroids(emb)
    ok = np.linalg.norm(v, axis=1) > 0
    print(f"{len(names)} clips, {ok.sum()} with a face, {len(names) - ok.sum()} without", flush=True)

    from sklearn.cluster import AgglomerativeClustering
    lab = np.full(len(names), -1)
    lab[ok] = AgglomerativeClustering(n_clusters=None, metric="cosine", linkage="average",
                                      distance_threshold=threshold).fit_predict(v[ok])
    sizes = pd.Series(lab).value_counts()
    keep = {l for l, n in sizes.items() if l >= 0 and n >= min_size}
    # Renumber by size, largest first, as q3.clusters does.
    order = {l: i for i, l in enumerate(sorted(keep, key=lambda l: (-sizes[l], l)))}
    cluster = np.array([order.get(l, -1) for l in lab])
    print(f"{len(keep)} clusters of >= {min_size} clips; {int((cluster >= 0).sum())} clips in them, "
          f"{int((cluster < 0).sum())} alone", flush=True)

    # q3's own signer clusters, in the order their faces were written.
    q3 = pd.read_csv(Q3 / "clusters.csv", keep_default_na=False)
    q3["cluster"] = q3["cluster"].astype(int)
    q3v = centroids(np.load(Q3 / "faces.npy").astype(np.float64))
    cent, q3_sizes = {}, q3["cluster"].value_counts()
    for c in q3["cluster"].unique():
        if c < 0:
            continue
        m = (q3["cluster"] == c).to_numpy()
        z = q3v[m].sum(0)
        cent[int(c)] = z / max(np.linalg.norm(z), 1e-6)
    print(f"{len(cent)} signers already known to q3 (its largest has {int(q3_sizes[q3_sizes.index >= 0].max())} clips)",
          flush=True)

    ids = sorted(cent)
    mat = np.stack([cent[c] for c in ids]) if ids else np.zeros((0, 128))
    signer, matched = [], {}
    for c in sorted(keep, key=lambda l: order[l]):
        m = cluster == order[c]
        z = v[m].sum(0)
        z = z / max(np.linalg.norm(z), 1e-6)
        s = mat @ z if len(ids) else np.zeros(0)
        best, sim = (int(np.argmax(s)), float(s.max())) if len(ids) else (-1, 0.0)
        second = float(np.partition(s, -2)[-2]) if len(s) > 1 else 0.0
        hit = len(ids) and sim >= match
        who = f"c{ids[best]}" if hit else f"n{order[c]}"
        if hit:
            matched[order[c]] = ids[best]
        signer.append((order[c], who, ids[best] if hit else -1, sim, second, int(m.sum())))
    by_cluster = {c: (who, q, sim, second, n) for c, who, q, sim, second, n in signer}

    rows = []
    for i, (clip, c) in enumerate(zip(names, cluster)):
        who, q, sim, second, n = by_cluster.get(int(c), ("", -1, 0.0, 0.0, 0)) if c >= 0 else (f"u:{clip}", -1, 0.0, 0.0, 1)
        rows.append({"clip": clip, "row": i, "faces": int((np.linalg.norm(emb[i], axis=1) > 0).sum()),
                     "cluster": int(c), "cluster_size": n, "signer": who,
                     "q3_cluster": q, "similarity": round(sim, 4), "second": round(second, 4)})
    df = pd.DataFrame(rows)
    df.to_csv(NEW / "clusters.csv", index=False)
    df[["clip", "signer"]].to_csv(NEW / "signers.csv", index=False)

    real = df[df["signer"].str.startswith(("c", "n"))]
    hit = real[real["signer"].str.startswith("c")]
    summary = {
        "clips": len(df), "clips_with_a_face": int(ok.sum()),
        "clusters": len(keep), "clips_in_clusters": int((df["cluster"] >= 0).sum()),
        "clips_alone": int((df["cluster"] < 0).sum()),
        "signers": int(real["signer"].nunique()),
        "signers_matched_to_q3": int(hit["signer"].nunique()),
        "clips_matched_to_q3": len(hit),
        "clips_new_signer": int(real["signer"].str.startswith("n").sum()),
        "largest_cluster": int(sizes.max()) if len(sizes) else 0,
        "clips_per_signer_median": float(real.groupby("signer").size().median()) if len(real) else 0.0,
        "match_similarity_min": float(min(r[3] for r in signer)) if signer else 0.0,
        "margin_min": float(min(r[3] - r[4] for r in signer)) if signer else 0.0,
    }
    (NEW / "summary.json").write_text(json.dumps(summary, indent=1), encoding="utf-8")
    print(json.dumps(summary, indent=1), flush=True)
    print("best match per cluster (cosine to the known signer, then the runner-up):", flush=True)
    for c, who, q, sim, second, n in sorted(signer, key=lambda r: -r[3]):
        print(f"  n{c:3d} -> {who:5s} q3 {q:3d} cosine {sim:.3f} (next {second:.3f}, margin {sim - second:+.3f})  {n} clips",
              flush=True)
    if "--sheets" in a:
        sheets(df, matched)
        print(f"contact sheets in {NEW / 'sheets'}", flush=True)
    print(f"JOB cluster_new clusters={len(keep)} clips={len(df)} status=done", flush=True)


if __name__ == "__main__":
    main()
