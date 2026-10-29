"""The app's sign reader reads the character's own signs: rendered joint tracks of two sets of word signs (the render
page's per-sign JSON), scored on the words both hold and the reader knows, with a paired McNemar between them.

    torch venv: python -s tools/isl/read_back.py BEFORE_DIR AFTER_DIR [OUT.json]
"""
import json
import pathlib
import re
import sys

import numpy as np
import torch

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "ml"))
import exp  # noqa: E402
import features  # noqa: E402
import models  # noqa: E402

FINGERS = (("Thumb", ("Metacarpal", "Proximal", "Distal")),) + tuple(
    (f, ("Proximal", "Intermediate", "Distal")) for f in ("Index", "Middle", "Ring", "Little"))


def hand(frame, bones, side):
    """MediaPipe's 21 hand points from the rig's bones, the fingertip carried on from the last two joints."""
    pts = [frame[bones[side + "Hand"]]]
    for finger, joints in FINGERS:
        p = [frame[bones[side + finger + j]] for j in joints]
        pts += p + [p[2] + (p[2] - p[1])]
    return np.array(pts)


def frames(path):
    r = json.loads(path.read_text())
    b = {n: i for i, n in enumerate(r["bones"])}
    world = np.array(r["world"]).reshape(len(r["world"]), -1, 3)[:, :, :2] * [1, -1]  # image axes, y down
    n = len(world)
    if n < 2:
        return None
    pose = np.zeros((n, 33, 2))
    pose[:, 11:17] = world[:, [b[k] for k in ("leftUpperArm", "rightUpperArm", "leftLowerArm", "rightLowerArm", "leftHand", "rightHand")]]
    raw = np.zeros((n, models.RAW), np.float32)
    raw[:, models.L] = np.stack([hand(f, b, "left") for f in world]).reshape(n, -1)
    raw[:, models.R] = np.stack([hand(f, b, "right") for f in world]).reshape(n, -1)
    raw[:, models.P] = pose.reshape(n, -1)
    raw[:, models.PRES] = [1, 1, 1, 0]
    return raw[features.resample_index(n, features.FRAMES)]


def ranks(folder, model, labels, app):
    cols = [labels.index(w) for w in app]
    rows = []
    for p in sorted(pathlib.Path(folder).glob("isl.*.json")):
        word = re.sub(r"\s*\[.*?\]|\(.*?\)", "", p.stem[4:].replace("_", " ")).strip().lower()
        y = next((i for i, w in enumerate(app) if word == w or word in w.split()), None)
        x = frames(p)
        if y is not None and x is not None:
            rows.append((app[y], y, x))
    with torch.no_grad():
        lg = model(torch.from_numpy(np.stack([x for *_, x in rows])), torch.ones(len(rows), 1)).double()[:, cols].numpy()
    return {w: int((l > l[y]).sum()) for (w, y, _), l in zip(rows, lg)}


def main(before, after, out=None):
    model, labels, _ = exp.app_model()
    model.eval()
    app = json.loads((exp.APP_MODELS / "sign_reader.json").read_text(encoding="utf-8"))["labels"]
    a, b = ranks(before, model, labels, app), ranks(after, model, labels, app)
    shared = sorted(set(a) & set(b))
    ra, rb = np.array([a[w] for w in shared]), np.array([b[w] for w in shared])
    only_a, only_b = int(((ra == 0) & (rb != 0)).sum()), int(((rb == 0) & (ra != 0)).sum())
    summary = {"words": len(shared),
               "before": {"top1": round(float((ra == 0).mean()), 3), "top5": round(float((ra < 5).mean()), 3)},
               "after": {"top1": round(float((rb == 0).mean()), 3), "top5": round(float((rb < 5).mean()), 3)},
               "before_only": only_a, "after_only": only_b, "p": round(exp.mcnemar(ra == 0, rb == 0), 4)}
    print(json.dumps(summary, indent=1))
    if out:
        pathlib.Path(out).write_text(json.dumps({"summary": summary, "before": a, "after": b}, indent=1))


if __name__ == "__main__":
    main(*sys.argv[1:4])
