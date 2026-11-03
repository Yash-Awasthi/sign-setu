"""The station's sign reader: an isolated-sign model in the phone's format 1 (a spec .json beside its .bin weights, as
tools/ml/export.py writes them), run on the station computer. The department places the model it chose in a folder
(--isl); without one the station reads no signs and says so in its status, so phones never ask.

Features and forward pass match tools/ml/features.py and the app's LandmarkSignReader.kt exactly; the parity fixture
the export writes (app/src/test/resources/sign_reader_fixture.json) checks it.

A frame is {"left": 42 numbers or null, "right": 42 or null, "pose": at least 34 or null}: MediaPipe Holistic
normalised image points (x, y), unmirrored, pose points 0 to 16 at least; [aspect] is the frame's width / height.
"""
import json
from pathlib import Path

import numpy as np

FEATURES = 98
MAX_FRAMES = 600  # 20 s at 30 frames a second, the phone's sign window


class IslReader:
    def __init__(self, folder: Path):
        specs = sorted(p for p in Path(folder).glob("*.json") if p.with_suffix(".bin").exists())
        if not specs:
            raise FileNotFoundError(f"no reader (a .json with its .bin) in {folder}")
        spec_path = specs[0]
        s = json.loads(spec_path.read_text(encoding="utf-8"))
        if s.get("format", 1) != 1 or s["features"] != FEATURES:
            raise ValueError(f"{spec_path.name}: not a format 1 sign reader")
        self.name = spec_path.stem
        self.labels: list[str] = s["labels"]
        self.frames, self.hidden, self.kernel = s["frames"], s["hidden"], s["kernel"]
        self.norm = s.get("norm", "shoulders")
        self.temperature = float(s.get("temperature", 1.0))
        # Both are divisors in read() and features(): a bad spec fails here at start, not on every sign.
        if self.frames < 2 or not self.temperature > 0:
            raise ValueError(f"{spec_path.name}: frames must be 2 or more and temperature above 0")
        raw = spec_path.with_suffix(".bin").read_bytes()
        flat = np.frombuffer(raw, "<f2" if s.get("dtype", "float32") == "float16" else "<f4").astype(np.float32)
        h, k, n = self.hidden, self.kernel, len(self.labels)
        sizes = [h * FEATURES, h, h * h * k, h, h * h * k, h, n * 2 * h, n]
        if sum(sizes) != flat.size:
            raise ValueError(f"{spec_path.name}: weights do not match the spec")
        parts, at = [], 0
        for size in sizes:
            parts.append(flat[at:at + size])
            at += size
        self.embed_w = parts[0].reshape(h, FEATURES)
        self.embed_b = parts[1]
        self.conv1_w, self.conv1_b = parts[2].reshape(h, h, k), parts[3]
        self.conv2_w, self.conv2_b = parts[4].reshape(h, h, k), parts[5]
        self.head_w, self.head_b = parts[6].reshape(n, 2 * h), parts[7]

    def read(self, frames: list[dict], aspect: float, top: int = 10) -> list[dict]:
        """The [top] likeliest words with their probabilities, or none when the frames show no shoulders (or, for a
        hands model, no hands)."""
        z = self.logits(frames, aspect)
        if z is None:
            return []
        e = np.exp((z - z.max()) / self.temperature)
        p = e / e.sum()
        order = np.argsort(-p)[:top]
        return [{"word": self.labels[i], "score": round(float(p[i]), 5)} for i in order]

    def logits(self, frames: list[dict], aspect: float) -> np.ndarray | None:
        x = self.features(frames, aspect)
        if x is None:
            return None
        a = np.maximum(x @ self.embed_w.T + self.embed_b, 0).T  # (hidden, frames)
        a = _conv_relu(a, self.conv1_w, self.conv1_b)
        a = _conv_relu(a, self.conv2_w, self.conv2_b)
        pooled = np.concatenate([a.mean(axis=1), a.max(axis=1)])
        return self.head_w @ pooled + self.head_b

    def features(self, frames: list[dict], aspect: float) -> np.ndarray | None:
        if not frames:
            return None
        ky = 1.0 / aspect
        left = [_points(f.get("left"), 21) for f in frames]
        right = [_points(f.get("right"), 21) for f in frames]
        pose = [_points(f.get("pose"), 17, at_least=True) for f in frames]
        if self.norm == "hands":
            hands = [h for h in left + right if h is not None]
            if not hands:
                return None
            pts = np.concatenate(hands) * [1.0, ky]
            cx, cy = pts.mean(axis=0)
            scale = float(np.mean([np.hypot(h[0, 0] - h[9, 0], (h[0, 1] - h[9, 1]) * ky) for h in hands]))
            with_pose = False
        else:
            shoulders = [p for p in pose if p is not None]
            if not shoulders:
                return None
            cx = float(np.mean([(p[11, 0] + p[12, 0]) / 2 for p in shoulders]))
            cy = float(np.mean([(p[11, 1] + p[12, 1]) / 2 * ky for p in shoulders]))
            scale = float(np.mean([np.hypot(p[11, 0] - p[12, 0], (p[11, 1] - p[12, 1]) * ky) for p in shoulders]))
            with_pose = True
        if scale < 1e-6:
            return None
        t, last = self.frames, len(frames) - 1
        out = np.zeros((t, FEATURES), np.float32)
        centre = np.array([cx, cy])
        for j in range(t):
            i = (j * last * 2 + (t - 1)) // (2 * (t - 1))
            if left[i] is not None:
                out[j, 0:42] = ((left[i] * [1.0, ky] - centre) / scale).reshape(-1)
                out[j, 96] = 1
            if right[i] is not None:
                out[j, 42:84] = ((right[i] * [1.0, ky] - centre) / scale).reshape(-1)
                out[j, 97] = 1
            if with_pose and pose[i] is not None:
                out[j, 84:96] = ((pose[i][11:17] * [1.0, ky] - centre) / scale).reshape(-1)
        return out


def _points(flat, n: int, at_least: bool = False) -> np.ndarray | None:
    if not flat:
        return None
    a = np.asarray(flat, dtype=np.float64).reshape(-1, 2)
    if len(a) < n or (len(a) != n and not at_least):
        return None
    return a


def _conv_relu(x: np.ndarray, w: np.ndarray, b: np.ndarray) -> np.ndarray:
    """Same-padded 1-D convolution over time, channel-major, then relu; w is [out][in][kernel]."""
    h, t = x.shape
    k = w.shape[2]
    pad = np.pad(x, ((0, 0), (k // 2, k // 2)))
    cols = np.stack([pad[:, q:q + t] for q in range(k)], axis=2)  # (in, t, k)
    return np.maximum(np.einsum("oik,itk->ot", w, cols) + b[:, None], 0)
