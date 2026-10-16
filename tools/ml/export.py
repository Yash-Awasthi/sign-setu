"""Exports the trained reader to the app's content bundle and writes the Kotlin parity fixture.

    python tools/ml/export.py [data_dir]
Then rebuild the manifest: python tools/content/build_content.py --manifest-only
"""
import json
import pathlib
import pickle
import sys

import numpy as np
import torch

from features import FEATURES, FRAMES
from train import HIDDEN, KERNEL, SignNet, features

ROOT = pathlib.Path(__file__).resolve().parents[2]
MODELS = ROOT / "app" / "src" / "main" / "assets" / "content" / "models"
FIXTURE = ROOT / "app" / "src" / "test" / "resources" / "sign_reader_fixture.json"


def folded(conv, bn):
    k = bn.weight / torch.sqrt(bn.running_var + bn.eps)
    return conv.weight * k[:, None, None], (conv.bias - bn.running_mean) * k + bn.bias


def rounded(s):
    """A sample as the app sees it: rounded normalised coordinates, missing parts dropped."""
    r = {key: np.round(s[key].astype(np.float64), 5) for key in ("left", "right", "pose")}
    return {**s, **r}


def frames_json(s):
    def pts(a, ok):
        return [round(float(v), 5) for v in a.reshape(-1)] if ok else None
    return [{"left": pts(s["left"][i], s["has_left"][i]), "right": pts(s["right"][i], s["has_right"][i]),
             "pose": pts(s["pose"][i, :17], s["has_pose"][i])} for i in range(len(s["pose"]))]


def main(data: pathlib.Path) -> None:
    ckpt = torch.load(data / "sign_reader.pt")
    labels = ckpt["labels"]
    model = SignNet(len(labels))
    model.load_state_dict(ckpt["state"])
    model.eval()
    with torch.no_grad():
        c1w, c1b = folded(model.conv1, model.bn1)
        c2w, c2b = folded(model.conv2, model.bn2)
        tensors = [model.embed.weight, model.embed.bias, c1w, c1b, c2w, c2b, model.head.weight, model.head.bias]
        blob = b"".join(t.detach().numpy().astype("<f4").tobytes() for t in tensors)
    MODELS.mkdir(parents=True, exist_ok=True)
    (MODELS / "sign_reader.bin").write_bytes(blob)
    spec = {
        "format": 1,
        "architecture": "per-frame linear+relu; conv1d+relu x2 (same padding, batch norm folded); "
                        "global average and max pooling concatenated; linear to logits",
        "frames": FRAMES, "features": FEATURES, "hidden": HIDDEN, "kernel": KERNEL, "classes": len(labels),
        "tensors": [{"shape": list(t.shape)} for t in tensors],
        "weights": "float32 little-endian, tensors in the order above, row-major; conv weight is [out][in][kernel]",
        "feature_spec": "MediaPipe Holistic normalised image points, unmirrored; y scaled by height/width; "
                        "centre = mean shoulder midpoint (pose 11, 12) over the segment, scale = mean shoulder width; "
                        "per frame: left hand 21x(x,y), right hand 21x(x,y), pose 11..16 (x,y), left and right "
                        "presence flags; a missing part is zeros; segment resampled to `frames` by nearest index "
                        "(i*(n-1)*2 + frames-1) div (2*(frames-1))",
        "dataset": "INCLUDE (AI4Bharat), OpenHands pose release, zenodo.org/records/6674324, CC BY 4.0",
        "test_top1": round(ckpt["top1"], 4), "test_top3": round(ckpt["top3"], 4),
        "labels": labels,
    }
    (MODELS / "sign_reader.json").write_text(json.dumps(spec, indent=1) + "\n", encoding="utf-8", newline="\n")
    print(f"weights {len(blob)} bytes, {len(labels)} classes")

    with open(data / "include.pkl", "rb") as f:
        cache = pickle.load(f)
    aspect = cache["aspect"]
    test = [s for s in cache["samples"] if s["split"] == "test"]
    one_hand = dict(test[3], has_left=np.zeros_like(test[3]["has_left"]))
    short = {k: (v[:7] if isinstance(v, np.ndarray) else v) for k, v in test[5].items()}
    cases = []
    for s in (test[0], test[200], test[400], one_hand, short):
        s = rounded(s)
        x = torch.tensor(features(s, aspect)[None])
        with torch.no_grad():
            logits = model(x)[0].double().numpy()
        cases.append({"word": s["label"], "frames": frames_json(s), "logits": [round(float(v), 6) for v in logits]})
    FIXTURE.parent.mkdir(parents=True, exist_ok=True)
    FIXTURE.write_text(json.dumps({"aspect": aspect, "cases": cases}), encoding="utf-8")


if __name__ == "__main__":
    main(pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "C:/Users/Yash/dev/ml-data/include_pose"))
