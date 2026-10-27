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
    return [[cols[c][t] for c in range(len(cols))] for t in range(len(frames))]


def clips(predictions: dict) -> list:
    out = []
    for sentence, entry in sorted(predictions.items()):
        frames = [f["coordinates"][:144] for f in entry["frames"]]
        if not frames or any(len(f) != 144 for f in frames):
            continue
        frames = [[round(v, 3) for v in f] for f in steady(frames)]
        clip = {"sentence": sentence, "fps": 25, "frames": frames}
        face = [[round(v, 2) for v in b] if len(b := f.get("blendshapes", [])) == 52 and max(b) > 0 else [] for f in entry["frames"]]
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
