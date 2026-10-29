"""Landmarks videos the way the phone does (SplitTracker.kt): frames scaled to the 848x480 analysis size, at most one
every 30 ms, hands on every frame, pose on even and face on odd frames (each reused for the frame between), hands
assigned to the nearer pose wrist. One uncompressed .npz per clip under OUT, mirrored on the clip's path under ROOT;
ROOT may be a .zip, whose video members are read one at a time.

    python landmark_split.py ROOT OUT [--list paths.txt] [--glob "**/*.mp4"] [--workers 6] [--limit N] [--name q3]
"""
import argparse
import concurrent.futures as cf
import io
import os
import pathlib
import sys
import time

# One thread per worker process: each library's own pool times 16 workers oversubscribes the CPU.
for _v in ("OMP_NUM_THREADS", "MKL_NUM_THREADS", "OPENBLAS_NUM_THREADS"):
    os.environ.setdefault(_v, "1")

import numpy as np  # noqa: E402

import guard  # noqa: E402
from guard import DATA  # noqa: E402

MODELS = pathlib.Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "assets" / "content" / "models"
# Phone schedule by default; LM_INTERVAL_MS, LM_LONG_SIDE and LM_MAX_S (only the first seconds) trade it for speed.
LONG_SIDE = int(os.environ.get("LM_LONG_SIDE", 848))
INTERVAL_MS = int(os.environ.get("LM_INTERVAL_MS", 30))
MAX_S = float(os.environ.get("LM_MAX_S", 0))
MAX_S_FOR = os.environ.get("LM_MAX_S_FOR", "")  # the cap applies only to paths containing this
# Face mesh points kept: lip contour, eye corners, brows, nose tip, chin, cheeks; enough for mouthing and non-manuals.
FACE_POINTS = [61, 146, 91, 181, 84, 17, 314, 405, 321, 375, 291, 185, 40, 39, 37, 0, 267, 269, 270, 409,
               33, 133, 362, 263, 70, 105, 336, 300, 1, 152, 234, 454]
MOUTH_SHAPES = ["jawOpen", "mouthClose", "mouthFunnel", "mouthPucker", "mouthSmileLeft", "mouthSmileRight",
                "mouthStretchLeft", "mouthStretchRight", "mouthRollLower", "mouthRollUpper", "mouthShrugLower",
                "mouthShrugUpper", "mouthPressLeft", "mouthPressRight", "mouthLowerDownLeft", "mouthLowerDownRight",
                "mouthUpperUpLeft", "mouthUpperUpRight", "cheekPuff"]

_tasks = None


def _open():
    import cv2
    cv2.setNumThreads(1)
    import mediapipe as mp
    from mediapipe.tasks.python import BaseOptions, vision
    video = vision.RunningMode.VIDEO
    base = lambda name: BaseOptions(model_asset_path=str(MODELS / name))  # noqa: E731
    hands = vision.HandLandmarker.create_from_options(vision.HandLandmarkerOptions(
        base_options=base("hand_landmarker.task"), running_mode=video, num_hands=2, min_hand_detection_confidence=0.3,
        min_hand_presence_confidence=0.3, min_tracking_confidence=0.3))
    pose = vision.PoseLandmarker.create_from_options(vision.PoseLandmarkerOptions(
        base_options=base("pose_landmarker_lite.task"), running_mode=video, num_poses=1,
        min_pose_detection_confidence=0.3, min_pose_presence_confidence=0.3, min_tracking_confidence=0.3))
    face = vision.FaceLandmarker.create_from_options(vision.FaceLandmarkerOptions(
        base_options=base("face_landmarker.task"), running_mode=video, num_faces=1, min_face_detection_confidence=0.3,
        min_face_presence_confidence=0.3, min_tracking_confidence=0.3, output_face_blendshapes=True,
        output_facial_transformation_matrixes=True))
    return mp, hands, pose, face


def assign_hands(found, labels, pose):
    """SplitTracker.kt assignHands: nearer pose wrist (15 subject's left, 16 right), else the flipped model label."""
    found = [h for h in found if len(h) == 21]
    if not found:
        return None, None
    if pose is not None:
        lw, rw = pose[15], pose[16]
        d = lambda h, w: float(np.hypot(*(h[0] - w)))  # noqa: E731
        if len(found) == 1:
            h = found[0]
            return (h, None) if d(h, lw) <= d(h, rw) else (None, h)
        a, b = found[0], found[1]
        return (a, b) if d(a, lw) + d(b, rw) <= d(a, rw) + d(b, lw) else (b, a)
    left = right = None
    for h, lab in zip(found, labels):
        if lab == "Left":
            right = right if right is not None else h
        else:
            left = left if left is not None else h
    return left, right


def extract(path: pathlib.Path) -> dict:
    import cv2
    global _tasks
    if _tasks is None:
        _tasks = _open()
    mp, hands, pose_t, face_t = _tasks
    cap = cv2.VideoCapture(str(path))
    if not cap.isOpened():
        raise OSError("cannot open")
    fps = cap.get(cv2.CAP_PROP_FPS) or 25.0
    w, h = cap.get(cv2.CAP_PROP_FRAME_WIDTH), cap.get(cv2.CAP_PROP_FRAME_HEIGHT)
    k = LONG_SIDE / max(w, h)
    size = (round(w * k), round(h * k))
    rows = {n: [] for n in ("t", "left", "right", "pose", "face", "has_left", "has_right", "has_pose", "has_face",
                            "yaw", "pitch", "mouth")}
    last_ms, frame_no, last_pose, last_face, i = None, 0, None, None, -1
    # Each graph keeps its own clock; a fresh offset per clip keeps timestamps rising across clips in one process.
    base_ms = getattr(extract, "clock", 0)
    cap_s = MAX_S if MAX_S and MAX_S_FOR in str(path).replace("\\", "/") else 0.0
    while cap.grab():
        i += 1
        ms = int(i * 1000 / fps)
        if last_ms is not None and ms - last_ms < INTERVAL_MS:
            continue
        if cap_s and ms > cap_s * 1000:
            break
        ok, frame = cap.retrieve()
        if not ok:
            break
        last_ms = ms
        img = mp.Image(image_format=mp.ImageFormat.SRGB,
                       data=cv2.cvtColor(cv2.resize(frame, size, interpolation=cv2.INTER_AREA), cv2.COLOR_BGR2RGB))
        ts = base_ms + ms
        frame_no += 1
        if frame_no % 2 == 0 or last_pose is None:
            r = pose_t.detect_for_video(img, ts)
            last_pose = np.array([(p.x, p.y) for p in r.pose_landmarks[0]], np.float32) if r.pose_landmarks else None
        if frame_no % 2 == 1 or last_face is None:
            r = face_t.detect_for_video(img, ts)
            if r.face_landmarks:
                pts = np.array([(p.x, p.y) for p in r.face_landmarks[0]], np.float32)[FACE_POINTS]
                # Column-major 4x4 as on the phone (headAngles); numpy hands it row-major, so transpose first.
                ms_ = r.facial_transformation_matrixes
                m = np.array(ms_[0]).T.reshape(-1) if ms_ else None
                yaw = float(np.degrees(np.arcsin(np.clip(-m[2], -1, 1)))) if m is not None else np.nan
                pitch = float(np.degrees(np.arctan2(m[6], m[10]))) if m is not None else np.nan
                s = {c.category_name: c.score for c in r.face_blendshapes[0]} if r.face_blendshapes else {}
                last_face = (pts, yaw, pitch, np.array([s.get(n, 0.0) for n in MOUTH_SHAPES], np.float32))
            else:
                last_face = None
        r = hands.detect_for_video(img, ts)
        found = [np.array([(p.x, p.y) for p in hl], np.float32) for hl in r.hand_landmarks]
        labels = [hd[0].category_name if hd else None for hd in r.handedness]
        left, right = assign_hands(found, labels, last_pose)
        rows["t"].append(ms)
        for key, v, n in (("left", left, 21), ("right", right, 21), ("pose", last_pose, 33)):
            rows[key].append(v if v is not None else np.zeros((n, 2), np.float32))
            rows["has_" + key].append(v is not None)
        f = last_face
        rows["face"].append(f[0] if f else np.zeros((len(FACE_POINTS), 2), np.float32))
        rows["has_face"].append(f is not None)
        rows["yaw"].append(f[1] if f else np.nan)
        rows["pitch"].append(f[2] if f else np.nan)
        rows["mouth"].append(f[3] if f else np.zeros(len(MOUTH_SHAPES), np.float32))
    cap.release()
    extract.clock = base_ms + (last_ms or 0) + 1000
    if not rows["t"]:
        raise OSError("no frames")
    out = {k: np.asarray(v) for k, v in rows.items()}
    out["t"] = out["t"].astype(np.int32)
    out["aspect"] = np.float32(size[0] / size[1])
    out["fps"] = np.float32(fps)
    out["interval_ms"], out["long_side"], out["max_s"] = np.int32(INTERVAL_MS), np.int32(LONG_SIDE), np.float32(cap_s)
    return out


def work(src: str, dst: str):
    t0 = time.perf_counter()
    if "::" in src:
        # A zip member: OpenCV needs a file, so it is unpacked next to its output for the length of one clip.
        import zipfile
        archive, member = src.split("::", 1)
        tmp = pathlib.Path(dst + ".video" + pathlib.PurePosixPath(member).suffix)
        tmp.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(archive) as z, z.open(member) as f, open(tmp, "wb") as out:
            out.write(f.read())
        try:
            c = extract(tmp)
        finally:
            tmp.unlink(missing_ok=True)
    else:
        c = extract(pathlib.Path(src))
    buf = io.BytesIO()
    np.savez(buf, **c)
    guard.write_atomic(dst, buf.getvalue())
    return len(c["t"]), c["t"][-1] / 1000, time.perf_counter() - t0


def near(root, rel):
    """While a folder is copied back to C: (to_c.py copy), clips already there are read from the copy, not from D:."""
    top, _, rest = rel.replace("\\", "/").partition("/")
    copy = root / (top + ".copy") / rest
    return copy if rest and copy.exists() else root / rel


CONTROL = DATA / "landmark_workers.txt"


def wanted(current):
    """Worker count set by monitor.py (GPU loaders come first); the current count when there is no file."""
    try:
        return max(1, int(CONTROL.read_text().strip()))
    except (OSError, ValueError):
        return current


def main():
    p = argparse.ArgumentParser()
    p.add_argument("root")
    p.add_argument("out")
    p.add_argument("--list")
    p.add_argument("--glob", default="**/*.mp4")
    p.add_argument("--workers", type=int, default=6)
    p.add_argument("--limit", type=int)
    p.add_argument("--name", default="landmarks")
    a = p.parse_args()
    root, out = pathlib.Path(a.root), pathlib.Path(a.out)
    if a.list:
        rels = [l.strip() for l in open(a.list, encoding="utf-8") if l.strip()]
    elif root.suffix.lower() == ".zip":
        import zipfile
        with zipfile.ZipFile(root) as z:
            rels = sorted(n for n in z.namelist() if n.lower().endswith((".mp4", ".mov", ".webm")))
    else:
        rels = sorted(str(q.relative_to(root)) for q in root.glob(a.glob))
    rels = rels[: a.limit] if a.limit else rels
    man = guard.Manifest(out / "manifest.jsonl")
    todo = [r for r in rels if not man.done(r)]
    workers, status = a.workers, "done"
    frames = seconds = busy = 0.0
    t0 = time.perf_counter()
    src = (lambda r: f"{root}::{r}") if root.suffix.lower() == ".zip" else (lambda r: str(near(root, r)))
    todo.reverse()
    broken = 0
    while todo and status == "done":
        # One long-lived pool, a few clips in flight per worker: a long clip never holds the other workers idle.
        # Low RAM drains the pool and restarts it with 2 workers fewer.
        shrink = dead = False
        with cf.ProcessPoolExecutor(workers) as pool:
            jobs = {}
            while ((todo and not shrink) or jobs) and not dead:
                while todo and not shrink and len(jobs) < workers * 2:
                    r = todo.pop()
                    try:
                        jobs[pool.submit(work, src(r), str(out / (r + ".npz")))] = r
                    except cf.process.BrokenProcessPool:
                        todo.append(r)
                        dead = True
                        break
                if dead:
                    break
                done, _ = cf.wait(jobs, return_when=cf.FIRST_COMPLETED)
                for j in done:
                    r = jobs.pop(j)
                    try:
                        n, sec, took = j.result()
                        frames, seconds, busy = frames + n, seconds + sec, busy + took
                        man.mark(r, "ok", frames=int(n), seconds=round(float(sec), 2))
                        broken = 0
                    except cf.process.BrokenProcessPool as e:
                        # A worker died (out of memory): every clip in flight fails once and goes back in the queue.
                        man.mark(r, "failed", repr(e))
                        todo.append(r)
                        dead = True
                    except Exception as e:
                        # A clip that fails to decode twice is set aside rather than retried forever.
                        man.mark(r, "quarantined" if man.attempts(r) >= 1 else "failed", repr(e))
                        if man.items[r]["state"] == "failed":
                            todo.insert(0, r)
                s = guard.check(out)
                want = wanted(workers)
                if s == "paused_disk":
                    status, shrink = s, True
                elif (s == "paused_ram" and workers > 1 or want != workers) and not shrink:
                    shrink = True
        if dead:
            for j, r in jobs.items():
                man.mark(r, "failed", "worker pool broke")
                todo.append(r)
            broken += 1
            print(f"worker pool broke ({broken} in a row); rebuilding with {workers} workers", flush=True)
            if broken >= 3:
                status = "failed_pool"
                print("STOP: the worker pool broke 3 times in a row", flush=True)
            continue
        if shrink and status == "done":
            if guard.check(out) == "paused_ram":
                workers = max(1, workers - 1)
                guard.write_atomic(CONTROL, str(workers).encode())
            else:
                workers = wanted(workers)
            print(f"workers now {workers}", flush=True)
    wall = time.perf_counter() - t0
    if frames:
        print(f"SPEED {frames:.0f} frames, {seconds:.0f} s of video, wall {wall:.0f} s: {frames / wall:.1f} fps total, "
              f"{frames / busy:.1f} fps per process, {seconds / wall:.2f} video s per wall s", flush=True)
    guard.job_line(a.name, man, status)


if __name__ == "__main__":
    sys.exit(main())
