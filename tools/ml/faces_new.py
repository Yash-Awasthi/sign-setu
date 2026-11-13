"""SFace embeddings for ISLRTC dictionary clips that q3's catalogue never saw, so a rebuilt cache can name their signers.

The same YuNet detector, SFace recogniser and three-frames-inside-the-sign-span rule q3.py faces uses, so these rows can
be compared with q3/clusters.csv directly. Reads the landmarks and the videos through the working set (SIGNSETU_DATA,
D: through the junction) and writes only outside it (SIGN_FACES, C:/work/faces_new by default).

    python faces_new.py --workers 8            # shards inside, one per process
    python faces_new.py --shard k --of K       # one shard, as q3.py runs them
    python faces_new.py --workers 8 --no-crops # embeddings only, no face crops

faces.npy: (N, 3, 128) float32 in the order of clips.txt; a frame with no face is zeros.
crops.npy: (N, 3, 112, 112, 3) uint8, the aligned faces the embeddings came from (cluster_new.py --sheets shows them).
"""

import json
import os
import pathlib
import subprocess
import sys

import numpy as np

from guard import DATA
from q3 import span, word_span

LM = pathlib.Path(os.environ.get("SIGN_LANDMARKS", "C:/work/landmarks_islrtc"))
OUT = pathlib.Path(os.environ.get("SIGN_FACES", "C:/work/faces_new"))
FACE_MODELS = DATA / "face_models"
CROPS = (3, 112, 112, 3)


def clip_of(path: pathlib.Path) -> str:
    rel = str(path.relative_to(LM))[:-4].replace("/", "\\")
    return "islrtc_dict\\" + rel


def clips() -> list[str]:
    return [clip_of(p) for p in sorted(LM.rglob("*.npz"))]


def prepare(names: list[str], crops: bool) -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    order = OUT / "clips.txt"
    if not order.exists():
        order.write_text("\n".join(names), encoding="utf-8")
    elif order.read_text(encoding="utf-8").splitlines() != names:
        sys.exit("clips.txt exists with a different list; delete SIGN_FACES and rerun")
    for name, shape, dt in (("faces.npy", (3, 128), np.float32), ("crops.npy", CROPS, np.uint8)):
        if (name == "crops.npy" and not crops) or (OUT / name).exists():
            continue
        np.lib.format.open_memmap(OUT / name, "w+", dt, (len(names), *shape)).flush()


def faces(shard: int, of: int, crops: bool) -> None:
    import cv2
    cv2.setNumThreads(1)  # one thread per shard; OpenCV's own pool oversubscribes the CPU
    det = cv2.FaceDetectorYN.create(str(FACE_MODELS / "face_detection_yunet_2023mar.onnx"), "", (320, 320), 0.7)
    rec = cv2.FaceRecognizerSF.create(str(FACE_MODELS / "face_recognition_sface_2021dec.onnx"), "")
    names = clips()
    prepare(names, crops)
    emb = np.load(OUT / "faces.npy", mmap_mode="r+")
    cr = np.load(OUT / "crops.npy", mmap_mode="r+") if crops else None
    rows = []
    for i, clip in enumerate(names):
        if i % of != shard:
            continue
        c = dict(np.load(LM / (clip[len("islrtc_dict\\"):] + ".npz")))
        segs, moving = span(c)
        if segs:
            a, b, _cap = word_span(c["t"], segs, moving)
            t0, t1 = int(c["t"][a]), int(c["t"][b - 1])
        else:
            t0, t1 = int(c["t"][0]), int(c["t"][-1])
        cap = cv2.VideoCapture(str(DATA / clip))
        got = 0
        for j, q in enumerate((0.25, 0.5, 0.75)):
            cap.set(cv2.CAP_PROP_POS_MSEC, t0 + (t1 - t0) * q)
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
            if cr is not None:
                cr[i, j] = crop
            got += 1
        cap.release()
        emb.flush()
        if cr is not None:
            cr.flush()
        rows.append({"clip": clip, "row": i, "faces": got, "status": "ok" if got else "quarantined"})
        if len(rows) % 200 == 0:
            print(f"shard {shard}: {len(rows)} clips, last {clip} faces={got}", flush=True)
    (OUT / f"manifest.{shard}.jsonl").write_text("\n".join(json.dumps(r) for r in rows), encoding="utf-8")
    print(f"shard {shard}: {len(rows)} clips written", flush=True)


def main() -> None:
    a = sys.argv[1:]
    crops = "--no-crops" not in a
    if "--shard" in a:
        faces(int(a[a.index("--shard") + 1]), int(a[a.index("--of") + 1]), crops)
        return
    workers = int(a[a.index("--workers") + 1]) if "--workers" in a else max(1, (os.cpu_count() or 4) // 2)
    names = clips()
    prepare(names, crops)
    args = [sys.executable, "-s", __file__, "--of", str(workers)] + ([] if crops else ["--no-crops"])
    procs = [subprocess.Popen(args + ["--shard", str(k)]) for k in range(workers)]
    codes = [p.wait() for p in procs]
    print(f"shards exit={codes}", flush=True)
    if any(codes):
        sys.exit(1)
    rows = []
    for i in range(workers):
        p = OUT / f"manifest.{i}.jsonl"
        if p.exists():
            rows += [json.loads(line) for line in p.read_text(encoding="utf-8").splitlines() if line.strip()]
    rows.sort(key=lambda r: r["row"])
    print(f"{len(rows)} clips, {sum(r['faces'] for r in rows)} faces, "
          f"{sum(r['status'] == 'quarantined' for r in rows)} without a face", flush=True)
    (OUT / "manifest.jsonl").write_text("\n".join(json.dumps(r) for r in rows), encoding="utf-8")
    print(f"JOB faces_new ok={sum(r['status'] == 'ok' for r in rows)} "
          f"quarantined={sum(r['status'] == 'quarantined' for r in rows)} status=done", flush=True)


if __name__ == "__main__":
    main()
