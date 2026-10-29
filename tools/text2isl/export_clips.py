"""Writes app/src/main/assets/content/clips.json from a prediction file (pick_clips.py, inspect_ground_truth.py or evaluate.py).

Each clip keeps the 144 coordinates of every frame (48 points, x y z) at three decimals, steadied (see steady()), and,
when any frame has a tracked face, its 52 MediaPipe blendshapes at two decimals as "face" (an empty list for a frame with no face). Run from the repository root, then refresh the manifest:

    python tools/text2isl/export_clips.py medoids.json more.js ...
    python tools/content/build_content.py --manifest-only

With several files, a sentence in a later file replaces the same sentence from an earlier one.
"""
import json
import math
import statistics
import sys
from pathlib import Path

OUT = Path("app/src/main/assets/content/clips.json")
SOURCE = ("ISL-CSLTR sentence videos (Elakkiya R. and Natarajan B., Mendeley Data, doi:10.17632/kcmpdxky7p.1, "
          "CC BY 4.0) through process_sign_videos.py, plus team recordings of the app's questions")


def read_predictions(path: Path) -> dict:
    """The sentence map from a `window.predictionDatabase = {...};` file or plain JSON."""
    text = path.read_text(encoding="utf-8")
    start = text.index("{")
    end = text.rindex("}") + 1
    return json.JSONDecoder().raw_decode(text[start:end])[0]


GLITCH = 0.05  # a point this far from its 7-frame median, and 3 deviations out, is a tracking slip
SIGMA_XY, SIGMA_Z = 1.0, 2.5  # frames; single-camera depth shakes more than x and y
SIGMA_HAND = 2.0  # frames, finger shape about the wrist: finger flicker -38%, palm wobble -49%, shape moves 3.5 deg
HAND_WRISTS = (6, 27)  # first point of the left and right 21-point hands
REST_BELOW = 1.6  # shoulder widths below the shoulders: both wrists lower than this is resting, not signing
REST_MARGIN = 5  # frames kept either side of the signing


def _gauss(x: list, sigma: float) -> list:
    r = int(3 * sigma)
    w = [math.exp(-0.5 * (k / sigma) ** 2) for k in range(-r, r + 1)]
    out = []
    for t in range(len(x)):
        num = den = 0.0
        for k in range(-r, r + 1):
            if 0 <= t + k < len(x):
                num += w[k + r] * x[t + k]
                den += w[k + r]
        out.append(num / den)
    return out


def steady(frames: list) -> list:
    """Raw MediaPipe tracks shake on the character: each coordinate's slips (Hampel filter) become the local median,
    then x and y are smoothed lightly and z more."""
    cols = []
    for c in range(len(frames[0])):
        x = [f[c] for f in frames]
        y = list(x)
        for t in range(len(x)):
            w = x[max(0, t - 3):t + 4]
            med = statistics.median(w)
            mad = statistics.median(abs(v - med) for v in w)
            if abs(x[t] - med) > max(3 * 1.4826 * mad, GLITCH):
                y[t] = med
        cols.append(_gauss(y, SIGMA_Z if c % 3 == 2 else SIGMA_XY))
    # Finger points about their own wrist: the shape twitched on the character while the wrist path was steady.
    for wrist in HAND_WRISTS:
        for p in range(wrist + 1, wrist + 21):
            for a in range(3):
                c, w = 3 * p + a, 3 * wrist + a
                off = _gauss([cols[c][t] - cols[w][t] for t in range(len(frames))], SIGMA_HAND)
                cols[c] = [cols[w][t] + off[t] for t in range(len(frames))]
    return [[cols[c][t] for c in range(len(cols))] for t in range(len(frames))]


def signing_span(frames: list) -> tuple:
    """Frames from a little before the first raised hand to a little after the last: the team's recordings rest for
    up to 3 s at the ends ("where do you live" 0.6 s before, 3.1 s after), which the character played as a wait."""
    def raised(f):
        mid_y = (f[1] + f[4]) / 2
        width = math.dist(f[0:2], f[3:5]) or 1.0
        return max(f[13], f[16]) - mid_y > -REST_BELOW * width  # wrists 4 and 5 against the shoulders, y up
    up = [t for t, f in enumerate(frames) if raised(f)]
    if not up:
        return 0, len(frames)
    return max(0, up[0] - REST_MARGIN), min(len(frames), up[-1] + 1 + REST_MARGIN)


def clips(predictions: dict) -> list:
    out = []
    for sentence, entry in sorted(predictions.items()):
        frames = [f["coordinates"][:144] for f in entry["frames"]]
        if not frames or any(len(f) != 144 for f in frames):
            continue
        frames = steady(frames)
        start, end = signing_span(frames)
        frames = [[round(v, 3) for v in f] for f in frames[start:end]]
        clip = {"sentence": sentence, "fps": 25, "frames": frames}
        face = [[round(v, 2) for v in b] if len(b := f.get("blendshapes", [])) == 52 and max(b) > 0 else []
                for f in entry["frames"][start:end]]
        if any(face):
            clip["face"] = face
        out.append(clip)
    return out


def main(*paths: str) -> None:
    merged = {}
    for path in paths:
        merged.update({k.strip(): v for k, v in read_predictions(Path(path)).items()})
    data = {"note": "Written by tools/text2isl/export_clips.py; edit the script, not this file. Draft until Deaf ISL review.",
            "source": SOURCE, "clips": clips(merged)}
    OUT.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    print(f"{OUT}: {len(data['clips'])} clips")


if __name__ == "__main__":
    main(*(sys.argv[1:] or ["prediction.js"]))
