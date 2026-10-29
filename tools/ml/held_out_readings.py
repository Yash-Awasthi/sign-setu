"""Real reader outputs on held-out signers, for building simulated lattices (docs/15-gloss-to-text.md, design 3).

Every held-out clip (ISLRTC dictionary signer, held-out Kaggle signer) is read by the shipped sign_reader with its
fitted temperature; the top 10 readings, in the app's words, are written per true word. The evaluation in
SentenceEvalTest samples a real reading for each sign of a sentence instead of inventing reader noise.
    python tools/ml/held_out_readings.py [out.json]
"""
import json
import pathlib
import pickle
import sys

import torch

from calibrate import DATA, MODELS
from train import SignNet, batch

OUT = pathlib.Path(__file__).resolve().parents[2] / "app" / "src" / "test" / "resources" / "held_out_readings.json"


def main(out: pathlib.Path) -> None:
    ckpt = torch.load(DATA / "isl_more" / "include_kaggle.pt", weights_only=True)  # nosemgrep: trailofbits.python.pickles-in-pytorch.pickles-in-pytorch
    labels = ckpt["labels"]
    index = {l: i for i, l in enumerate(labels)}
    model = SignNet(len(labels), hidden=ckpt["state"]["bn2.weight"].shape[0])
    model.load_state_dict(ckpt["state"])
    model.eval()
    t = json.loads((MODELS / "sign_reader.json").read_text(encoding="utf-8"))["temperature"]
    words = json.loads((MODELS.parent / "sign_words.json").read_text(encoding="utf-8"))["words"]
    app = lambda l: words.get(l, l)  # noqa: E731
    readings = {"islrtc": {}, "kaggle": {}}
    for source, path, split in (("islrtc", DATA / "islrtc" / "islrtc_words.pkl", None), ("kaggle", DATA / "isl_more" / "include_kaggle.pkl", "test")):
        cache = pickle.load(open(path, "rb"))  # nosemgrep
        samples = [s for s in cache["samples"] if s["label"] in index and (split is None or s["split"] == split)]
        with torch.no_grad():
            p = (model(batch(samples, cache["aspect"])) / t).softmax(1)
        top = p.topk(10, 1)
        for s, vals, ids in zip(samples, top.values.tolist(), top.indices.tolist()):
            seen, row = set(), []
            for v, i in zip(vals, ids):
                w = app(labels[i])
                if w not in seen:
                    seen.add(w)
                    row.append([w, round(v, 4)])
            readings[source].setdefault(app(s["label"]), []).append(row)
    out.write_text(json.dumps({"note": "Written by tools/ml/held_out_readings.py: top-10 calibrated readings of held-out "
                                       "clips, by source (ISLRTC new signer, held-out Kaggle signer) and true word (app words).", "readings": readings}, ensure_ascii=False), encoding="utf-8")
    for k, v in readings.items():
        print(f"{k}: {sum(map(len, v.values()))} readings of {len(v)} words")


if __name__ == "__main__":
    main(pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else OUT)
