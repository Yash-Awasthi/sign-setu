import numpy as np
import pandas as pd

import pick_clips


def clip(offset, frames=20):
    c = np.zeros((frames, 196), dtype=np.float32)
    c[:, 52:] = np.linspace(0, 1, frames)[:, None] + offset
    return c


def test_dtw_ignores_tempo():
    slow = clip(0, 40)
    fast = clip(0, 20)
    shifted = clip(0.5, 20)
    assert pick_clips.dtw(slow[:, 52:], fast[:, 52:]) * 10 < pick_clips.dtw(slow[:, 52:], shifted[:, 52:])


def test_medoid_skips_the_outlier():
    # Flat clips, since time warping can absorb a shifted ramp.
    flat = [np.full((20, 196), v, dtype=np.float32) for v in (3.0, 0.1, 0.0, -0.1, -0.2)]
    assert pick_clips.medoid(flat) == 2


def test_pick_writes_one_clip_per_sentence(tmp_path):
    for name, offset in [("a", 0.0), ("a (2)", 0.1), ("a (3)", 5.0), ("b", 0.0)]:
        np.save(tmp_path / f"{name}.npy", clip(offset))
    labels = pd.DataFrame({"filename": ["a", "a (2)", "a (3)", "b"], "sentence": ["hi ", "hi ", "hi ", "bye"]})
    out = pick_clips.pick(tmp_path, labels)
    assert set(out) == {"hi", "bye"}
    assert out["hi"]["source"] in {"a", "a (2)"}
    assert len(out["hi"]["frames"][0]["coordinates"]) == 144
