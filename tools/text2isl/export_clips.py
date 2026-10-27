"""Writes app/src/main/assets/content/clips.json from a prediction file (pick_clips.py, inspect_ground_truth.py or evaluate.py).

Each clip keeps the 144 coordinates of every frame (48 points, x y z) at three decimals; blendshapes are dropped
because the character takes its face from the rig. Run from the repository root, then refresh the manifest:

    python tools/text2isl/export_clips.py medoids.json more.js ...
    python tools/content/build_content.py --manifest-only

With several files, a sentence in a later file replaces the same sentence from an earlier one.
"""
import json
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


def clips(predictions: dict) -> list:
    out = []
    for sentence, entry in sorted(predictions.items()):
        frames = [[round(v, 3) for v in f["coordinates"][:144]] for f in entry["frames"]]
        if frames and all(len(f) == 144 for f in frames):
            out.append({"sentence": sentence, "fps": 25, "frames": frames})
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
