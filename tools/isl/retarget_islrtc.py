"""Adds ISLRTC dictionary words the app could only fingerspell; only keyframes ship, never video.
Usage and licences are in tools/isl/README.md."""
import json
import os
import pathlib
import sys
import urllib.parse
import urllib.request

import numpy as np

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import retarget as rt  # noqa: E402

# Training and evaluation data stay outside the repository.
DATA_ROOT = pathlib.Path(os.environ.get("SIGNSETU_DATA", "ml-data"))

BASE = "https://huggingface.co/datasets/silentone0725/Indian_Sign_Language_Data.gov_Rencoded/resolve/main/"
MODEL = rt.ROOT / "app" / "src" / "main" / "assets" / "content" / "models" / "holistic_landmarker.task"
CATEGORY = "ISLRTC"
MIN_S, MAX_S = 0.3, 6.0
# Words whose dictionary clip reads better than their INCLUDE or Kaggle take (review.md verdict "replace"): the
# dictionary clip wins for these.
REPLACE = {
    "blue": "B/Blue.mp4", "boy": "B/Boy.mp4", "camera": "C/Camera.mp4", "city": "C/City.mp4", "door": "D/Door.mp4",
    "dry": "D/Dry.mp4", "exercise": "E/Exercise.mp4", "he": "H/He.mp4", "hot": "H/Hot.mp4", "india": "I/India.mp4",
    "lamp": "L/Lamp.mp4", "monsoon": "M/Monsoon.mp4", "month": "M/Month.mp4", "orange": "O/Orange.mp4",
    "queen": "Q/Queen.mp4", "science": "S/Science.mp4", "short": "S/Short.mp4", "sign": "S/Sign.mp4",
    "team": "T/Team.mp4", "window": "W/Window.mp4", "yesterday": "Y/Yesterday.mp4", "young": "Y/Young.mp4",
}
SOURCE = ("INCLUDE (AI4Bharat, Sridhar et al. 2020), OpenHands pose release, CC BY 4.0; ISLRTC Indian Sign Language "
          "Dictionary (Government of India), Government Open Data License - India; Indian Sign Language video dataset "
          "(Prasad Shet, Kaggle), MIT")


def landmarks(path: pathlib.Path) -> tuple[dict, float]:
    """Holistic in video mode; pose and hands in units of frame height, hand z in the same unit."""
    import cv2
    import mediapipe as mp
    from mediapipe.tasks.python import BaseOptions, vision
    opts = vision.HolisticLandmarkerOptions(base_options=BaseOptions(model_asset_path=str(MODEL)),
                                            running_mode=vision.RunningMode.VIDEO)
    cap = cv2.VideoCapture(str(path))
    fps = cap.get(cv2.CAP_PROP_FPS) or 25.0
    aspect = cap.get(cv2.CAP_PROP_FRAME_WIDTH) / cap.get(cv2.CAP_PROP_FRAME_HEIGHT)
    pose, hands, has = [], {"L": [], "R": []}, {"pose": [], "L": [], "R": []}
    with vision.HolisticLandmarker.create_from_options(opts) as lm:
        t = 0
        while True:
            ok, frame = cap.read()
            if not ok:
                break
            if frame.shape[1] > 640:  # landmarks are normalised, so a smaller frame only saves time
                frame = cv2.resize(frame, (640, round(frame.shape[0] * 640 / frame.shape[1])))
            r = lm.detect_for_video(mp.Image(image_format=mp.ImageFormat.SRGB, data=cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)),
                                    int(t * 1000 / fps))
            t += 1
            pose.append(np.array([(p.x * aspect, p.y) for p in r.pose_landmarks]) if r.pose_landmarks else np.zeros((33, 2)))
            has["pose"].append(bool(r.pose_landmarks))
            for side, ps in (("L", r.left_hand_landmarks), ("R", r.right_hand_landmarks)):
                hands[side].append(np.array([(p.x * aspect, p.y, p.z * aspect) for p in ps]) if ps else np.zeros((21, 3)))
                has[side].append(bool(ps))
    rec = {"pose": np.stack(pose), "has_pose": np.array(has["pose"])}
    for side in ("L", "R"):
        rec[side], rec[f"has_{side}"] = np.stack(hands[side]), np.array(has[side])
    return rec, fps


def main(targets: pathlib.Path, data: pathlib.Path, dictionary: pathlib.Path | None = None) -> None:
    """[dictionary], when given, is a local copy of the ISLRTC dictionary read in place before any download."""
    clips = data / "words"
    clips.mkdir(parents=True, exist_ok=True)
    signs = json.loads(rt.OUT.read_text(encoding="utf-8"))
    signs["gestures"] = [g for g in signs["gestures"] if g.get("rec", "").startswith("ISLRTC/") or g["word"] not in REPLACE]
    have = {g["word"] for g in signs["gestures"] if not g.get("rec", "").startswith("ISLRTC/")}  # INCLUDE and Kaggle win
    words = json.loads((rt.OUT.parent / "words.json").read_text(encoding="utf-8"))["words"]
    hindi = {e["w"][0]: next((w for w in e["w"] if any("ऀ" <= c <= "ॿ" for c in w)), "") for e in words}
    prior = {g["word"]: g for g in signs["gestures"] if g.get("rec", "").startswith("ISLRTC/")}
    made = {}
    for word, clip in sorted({**json.loads(targets.read_text(encoding="utf-8")), **REPLACE}.items()):
        if word in have:
            continue
        if prior.get(word, {}).get("rec") == f"ISLRTC/{clip}":
            made[word] = prior[word]
            continue
        path = clips / clip.replace("/", "__")
        cache = path.with_suffix(".npz")
        if not path.exists() and dictionary and (dictionary / clip).exists():
            path = dictionary / clip
        elif not path.exists():
            path.write_bytes(urllib.request.urlopen(BASE + urllib.parse.quote(clip)).read())
        if cache.exists():
            z = np.load(cache)
            rec, fps = {k: z[k] for k in z.files if k not in ("fps", "aspect")}, float(z["fps"])
        else:
            rec, fps = landmarks(path)
            np.savez_compressed(cache, fps=fps, aspect=1.0, **rec)
        if len(rec["pose"]) < 5 or rec["has_pose"].mean() < 0.8:
            print("skip", word, clip)
            continue
        seen = np.flatnonzero(rec["has_L"] | rec["has_R"])
        span = slice(seen[0], seen[-1] + 1) if len(seen) else slice(0, 0)
        # Hands at rest leave the frame here, so a hand counts as signing when it is seen in most of the visible span.
        hands = [h for h in ("R", "L") if len(seen) and rec[f"has_{h}"][span].mean() >= 0.5] or ["R"]
        # Visible hands mark the signing; failing that, the raised-hands cut used for INCLUDE. A clip that never pauses
        # holds an example sentence, and a blink of motion is a failed track: both stay spelled.
        keys = next((k for k in (rt.retarget(rec, hands, fps, first=True, visible=True), rt.retarget(*rt.pick([(clip, rec)])[1:], fps, first=True))
                     if MIN_S <= k[-1]["t"] <= MAX_S), None)
        if keys is None:
            print("skip", word)
            continue
        made[word] = {"id": f"isl.{word}", "word": word, "hi": hindi.get(word, ""), "category": CATEGORY, "rec": f"ISLRTC/{clip}", "keys": keys}
        print(word, len(made[word]["keys"]), "keys", flush=True)
    signs["gestures"] = [g for g in signs["gestures"] if not g.get("rec", "").startswith("ISLRTC/")] + list(made.values())
    signs["source"] = SOURCE
    signs["categories"][CATEGORY] = {"en": "More words (ISLRTC)", "hi": "और शब्द (ISLRTC)"}
    rt.write(signs)
    print(f"ISLRTC words: {len(made)}")


if __name__ == "__main__":
    a = sys.argv[1:]
    main(pathlib.Path(a[0]), pathlib.Path(a[1] if len(a) > 1 else str(DATA_ROOT / "islrtc")), pathlib.Path(a[2]) if len(a) > 2 else None)
