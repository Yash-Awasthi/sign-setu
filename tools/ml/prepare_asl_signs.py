"""Builds the ASL landmark cache from Google's Isolated Sign Language Recognition set (Kaggle `asl-signs`, CC BY 4.0;
same 250 signs as PopSign). SRC is the unzipped folder or the Kaggle zip itself (read in place). Signer-independent:
TEST_SIGNERS never train; with --no-test their clips are not even read, so a closed test split stays closed."""
import csv
import json
import os
import pathlib
import pickle
import sys
from concurrent.futures import ProcessPoolExecutor

import numpy as np

# Training and evaluation data stay outside the repository.
DATA_ROOT = pathlib.Path(os.environ.get("SIGNSETU_DATA", "ml-data"))

LABELS = pathlib.Path(__file__).with_name("popsign_labels.json")
TEST_SIGNERS = 4  # the participants with the fewest clips


_ZIP = {}


def clip(path) -> dict:
    """path: a parquet file, or (zip path, member) for one read from the Kaggle zip."""
    import io
    import zipfile
    import pyarrow.parquet as pq
    if isinstance(path, tuple):
        z = _ZIP.get(path[0]) or _ZIP.setdefault(path[0], zipfile.ZipFile(path[0]))
        path = io.BytesIO(z.read(path[1]))
    t = pq.read_table(path, columns=["frame", "type", "landmark_index", "x", "y"])
    frame = t.column("frame").to_numpy()
    kind = np.array(t.column("type").to_pylist())
    idx = t.column("landmark_index").to_numpy()
    xy = np.stack([t.column(c).to_numpy(zero_copy_only=False) for c in ("x", "y")], 1).astype(np.float32)
    frames = np.unique(frame)
    row = np.searchsorted(frames, frame)
    out = {}
    for name, key, n in (("pose", "pose", 33), ("left_hand", "left", 21), ("right_hand", "right", 21)):
        a = np.full((len(frames), n, 2), np.nan, np.float32)
        m = kind == name
        a[row[m], idx[m]] = xy[m]
        ok = np.isfinite(a).all((1, 2))
        out[key], out["has_" + key] = np.nan_to_num(a), ok
    return out


def aspect(samples) -> float:
    """Frame width / height that keeps hand bone lengths steadiest as the hands turn; the set does not record it."""
    bones = [h[ok][:, [5, 9, 17]] - h[ok][:, [0]] for s in samples for h, ok in
             ((s["left"], s["has_left"]), (s["right"], s["has_right"])) if ok.any()]
    if not bones:
        return 1.0
    d = np.concatenate(bones).reshape(-1, 2)
    grid = np.linspace(0.4, 2.5, 211)
    return float(grid[np.argmin([np.log(d[:, 0] ** 2 + (d[:, 1] / a) ** 2 + 1e-12).var() for a in grid])])


def main(src: pathlib.Path, out: pathlib.Path, workers: int = 6, no_test: bool = False) -> None:
    import io
    import zipfile
    names = {l.lower(): l for l in json.loads(LABELS.read_text(encoding="utf-8"))["labels"]}
    zipped = src.suffix == ".zip"
    if zipped:
        with zipfile.ZipFile(src) as z:
            rows = list(csv.DictReader(io.TextIOWrapper(z.open("train.csv"), newline="")))
    else:
        rows = list(csv.DictReader(open(src / "train.csv", newline="")))
    counts = {}
    for r in rows:
        counts[r["participant_id"]] = counts.get(r["participant_id"], 0) + 1
    test = set(sorted(counts, key=counts.get)[:TEST_SIGNERS])
    if no_test:
        rows = [r for r in rows if r["participant_id"] not in test]
    with ProcessPoolExecutor(workers) as ex:
        clips = list(ex.map(clip, [(str(src), r["path"]) if zipped else src / r["path"] for r in rows], chunksize=64))
    by_signer = {}
    for r, c in zip(rows, clips):
        if c["has_pose"].any() and (c["has_left"].any() or c["has_right"].any()):
            s = {"split": "test" if r["participant_id"] in test else "train", "label": names[r["sign"].lower()],
                 "signer": r["participant_id"], **c}
            by_signer.setdefault(r["participant_id"], []).append(s)
    samples = []
    for signer, ss in by_signer.items():
        a = aspect(ss)
        print(f"signer {signer}: {len(ss)} clips, aspect {a:.2f}", flush=True)
        samples += [{**s, "aspect": a} for s in ss]
    with open(out, "wb") as f:
        pickle.dump({"aspect": 1.0, "fps": 30, "samples": samples}, f)
    print(f"{len(samples)} of {len(rows)} clips, test signers {sorted(test)} -> {out}")


if __name__ == "__main__":
    a = sys.argv[1:]
    no_test = "--no-test" in a
    a = [x for x in a if x != "--no-test"]
    main(pathlib.Path(a[0]) if a else DATA_ROOT / "asl_signs",
         pathlib.Path(a[1]) if len(a) > 1 else DATA_ROOT / "asl_signs" / "asl_signs.pkl",
         int(a[2]) if len(a) > 2 else 6, no_test)
