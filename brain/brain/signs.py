"""The station's sign readers: isolated-sign models run on the station computer, several at once.

A model is a file in the --isl folder with a spec file of the same name beside it (`name.json`):
  .json + .bin   format 1, as tools/ml/export.py writes it (weights run here in numpy)
  .tflite        LiteRT, as the phone's sign_reader_v3
  .onnx          onnxruntime
A .pt or other training checkpoint is refused: export it to ONNX first.

The spec names what the model needs: `frames`, `features`, `labels`, `temperature` and `input`, the builder that turns
a sign's landmarks into the model's input (shoulder98, raw218). `input` defaults to shoulder98 for format 1 and raw218
for format 3. An unknown builder, or a spec that does not fit the model, is refused with the reason, never read wrong.

A frame is {"left": 42 numbers or null, "right": 42 or null, "pose": at least 34 or null, "face": optional}: MediaPipe
Holistic normalised image points (x, y), unmirrored; [aspect] is the frame's width / height. raw218 uses the pose's 33
points and the spec's `face_points` of the face when the frame has a face.

Files are watched: a new or changed one loads with no restart; a file that fails leaves the reader it would have
replaced running. Several readers are merged as on the phone: the first by priority gives the words, and its top is
marked disputed when another reader is fairly sure of a different word.
"""
import json
import threading
import time
from pathlib import Path

import numpy as np

MAX_FRAMES = 600  # 20 s at 30 frames a second, the phone's sign window
OTHER_SURE = 0.3  # app Kinetics.kt: another reader's top this sure of a different word disputes the first
MODEL_SUFFIXES = (".bin", ".tflite", ".onnx")
TRAINING_SUFFIXES = (".pt", ".pth", ".ckpt", ".safetensors")


# ---- input builders -------------------------------------------------------------------------------------------

def _points(flat, n: int, at_least: bool = False) -> np.ndarray | None:
    if not flat:
        return None
    a = np.asarray(flat, dtype=np.float64).reshape(-1, 2)
    if len(a) < n or (len(a) != n and not at_least):
        return None
    return a


def _pick(frames: int, count: int, j: int) -> int:
    """The source frame for output frame j: nearest index, as the app and tools/ml/features.py do."""
    last = count - 1
    return (j * last * 2 + (frames - 1)) // (2 * (frames - 1))


def shoulder98(spec: dict, frames: list[dict], aspect: float) -> np.ndarray | None:
    """98 features per frame, centred and scaled on the shoulders (or the hands, spec norm "hands")."""
    if not frames:
        return None
    ky = 1.0 / aspect
    left = [_points(f.get("left"), 21) for f in frames]
    right = [_points(f.get("right"), 21) for f in frames]
    pose = [_points(f.get("pose"), 17, at_least=True) for f in frames]
    if spec.get("norm", "shoulders") == "hands":
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
    t = spec["frames"]
    out = np.zeros((t, 98), np.float32)
    centre = np.array([cx, cy])
    for j in range(t):
        i = _pick(t, len(frames), j)
        if left[i] is not None:
            out[j, 0:42] = ((left[i] * [1.0, ky] - centre) / scale).reshape(-1)
            out[j, 96] = 1
        if right[i] is not None:
            out[j, 42:84] = ((right[i] * [1.0, ky] - centre) / scale).reshape(-1)
            out[j, 97] = 1
        if with_pose and pose[i] is not None:
            out[j, 84:96] = ((pose[i][11:17] * [1.0, ky] - centre) / scale).reshape(-1)
    return out


def raw218(spec: dict, frames: list[dict], aspect: float) -> np.ndarray | None:
    """The phone's raw layout (LiteRtSignReader.rawFrames): hands, 33 pose points, the kept face points, four presence
    flags. The graph computes its features inside. None when no frame shows the shoulders."""
    if not any(len(f.get("pose") or []) >= 34 for f in frames):
        return None
    face_points = spec["face_points"]
    width = 42 + 42 + 66 + 2 * len(face_points) + 4
    t = spec["frames"]
    out = np.zeros((t, width), np.float32)
    for j in range(t):
        f = frames[_pick(t, len(frames), j)]
        left, right = _points(f.get("left"), 21), _points(f.get("right"), 21)
        pose = _points(f.get("pose"), 33, at_least=True)
        face = _points(f.get("face"), max(face_points) + 1, at_least=True)
        if left is not None:
            out[j, 0:42] = left.reshape(-1)
            out[j, width - 4] = 1
        if right is not None:
            out[j, 42:84] = right.reshape(-1)
            out[j, width - 3] = 1
        if pose is not None:
            out[j, 84:150] = pose[:33].reshape(-1)
            out[j, width - 2] = 1
        if face is not None:
            out[j, 150:width - 4] = face[face_points].reshape(-1)
            out[j, width - 1] = 1
    return out


BUILDERS = {
    "shoulder98": (lambda spec: 98, shoulder98),
    "raw218": (lambda spec: 42 + 42 + 66 + 2 * len(spec.get("face_points") or []) + 4, raw218),
}


# ---- spec -----------------------------------------------------------------------------------------------------

class Refused(Exception):
    """A model that is not used, with the reason shown in the status."""


class Spec:
    def __init__(self, path: Path):
        try:
            s = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, ValueError) as e:
            raise Refused(f"spec {path.name} unreadable ({e})")
        if not isinstance(s, dict):
            raise Refused(f"spec {path.name} is not an object")
        self.raw = s
        self.format = s.get("format", 1)
        labels = s.get("labels")
        if not isinstance(labels, list) or not labels or not all(isinstance(x, str) for x in labels):
            raise Refused("spec has no labels list")
        self.labels: list[str] = labels
        self.frames = s.get("frames")
        if not isinstance(self.frames, int) or self.frames < 2 or self.frames > MAX_FRAMES:
            raise Refused("spec frames must be a whole number from 2 to 600")
        try:
            self.temperature = float(s.get("temperature", 1.0))
        except (TypeError, ValueError):
            raise Refused("spec temperature is not a number")
        if not self.temperature > 0:
            raise Refused("spec temperature must be above 0")
        self.input = s.get("input") or {1: "shoulder98", 3: "raw218"}.get(self.format)
        if self.input is None:
            raise Refused(f"spec format {self.format} names no input builder (set \"input\")")
        if self.input not in BUILDERS:
            raise Refused(f"unknown input builder {self.input!r} (known: {', '.join(BUILDERS)})")
        if self.input == "raw218":
            fp = s.get("face_points")
            if not isinstance(fp, list) or not fp or not all(isinstance(i, int) and 0 <= i < 468 for i in fp):
                raise Refused("input raw218 needs face_points, a list of face landmark numbers")
        width, self.build = BUILDERS[self.input]
        self.features = width(s)
        declared = s.get("features", s.get("raw"))
        if declared is not None and declared != self.features:
            raise Refused(f"spec says {declared} features per frame, builder {self.input} gives {self.features}")
        self.priority = s.get("priority", 0)
        if not isinstance(self.priority, (int, float)):
            raise Refused("spec priority is not a number")


# ---- backends -------------------------------------------------------------------------------------------------

class Reader:
    """One model: its spec and a way to turn the built input into class scores."""

    def __init__(self, spec: Spec, name: str, file: str):
        self.spec, self.name, self.file = spec, name, file
        self.labels = spec.labels
        self.format = spec.format
        self.lock = threading.Lock()

    def logits(self, x: np.ndarray, aspect: float) -> np.ndarray:  # pragma: no cover - set by each backend
        raise NotImplementedError

    def close(self) -> None:
        pass

    def read(self, frames: list[dict], aspect: float, top: int = 10) -> list[dict]:
        """The [top] likeliest words with their probabilities; none when the frames cannot be read (no shoulders)."""
        x = _build(self.spec, frames, aspect)
        if x is None:
            return []
        with self.lock:
            z = np.asarray(self.logits(x, aspect), dtype=np.float64).reshape(-1)
        e = np.exp((z - z.max()) / self.spec.temperature)
        p = e / e.sum()
        order = np.argsort(-p)[:top]
        return [{"word": self.labels[i], "score": round(float(p[i]), 5)} for i in order]

    def check(self) -> None:
        """Runs the model once on zeros so a broken one is refused now, not on a child's first sign."""
        z = self.logits(np.zeros((self.spec.frames, self.spec.features), np.float32), 1.0)
        if np.asarray(z).size != len(self.labels):
            raise Refused(f"model gives {np.asarray(z).size} scores, spec lists {len(self.labels)} labels")


def _build(spec: Spec, frames: list[dict], aspect: float):
    """The model's input, or None when the frames cannot be read. Bad client data is always a ValueError, so it is a
    422 for that request and never takes the reader out of service."""
    try:
        x = spec.build({**spec.raw, "frames": spec.frames}, frames, aspect)
    except (TypeError, AttributeError, IndexError, KeyError) as e:
        raise ValueError(f"frames not in the expected shape ({type(e).__name__})")
    if x is not None and not np.isfinite(x).all():
        raise ValueError("landmarks hold a value that is not a number")
    return x


class Format1(Reader):
    """The app's format 1: per-frame linear + relu, two same-padded convolutions + relu, average and max pooling,
    a linear head (tools/ml/export.py). Needs the 98-feature builder."""

    def __init__(self, spec: Spec, name: str, file: str, weights: Path):
        super().__init__(spec, name, file)
        s = spec.raw
        if spec.format != 1 or spec.input != "shoulder98":
            raise Refused("a .bin is format 1 and reads the shoulder98 input")
        try:
            h, k = int(s["hidden"]), int(s["kernel"])
        except (KeyError, TypeError, ValueError):
            raise Refused("format 1 spec needs hidden and kernel")
        if not (0 < h <= 4096 and 0 < k <= 31):
            raise Refused("format 1 hidden must be 1 to 4096 and kernel 1 to 31")
        n, f = len(spec.labels), spec.features
        raw = weights.read_bytes()
        flat = np.frombuffer(raw, "<f2" if s.get("dtype", "float32") == "float16" else "<f4").astype(np.float32)
        sizes = [h * f, h, h * h * k, h, h * h * k, h, n * 2 * h, n]
        if sum(sizes) != flat.size:
            raise Refused("weights do not match the spec")
        parts, at = [], 0
        for size in sizes:
            parts.append(flat[at:at + size])
            at += size
        self.embed_w, self.embed_b = parts[0].reshape(h, f), parts[1]
        self.conv1_w, self.conv1_b = parts[2].reshape(h, h, k), parts[3]
        self.conv2_w, self.conv2_b = parts[4].reshape(h, h, k), parts[5]
        self.head_w, self.head_b = parts[6].reshape(n, 2 * h), parts[7]

    def logits(self, x, aspect):
        a = np.maximum(x @ self.embed_w.T + self.embed_b, 0).T  # (hidden, frames)
        a = _conv_relu(a, self.conv1_w, self.conv1_b)
        a = _conv_relu(a, self.conv2_w, self.conv2_b)
        pooled = np.concatenate([a.mean(axis=1), a.max(axis=1)])
        return self.head_w @ pooled + self.head_b


def _conv_relu(x: np.ndarray, w: np.ndarray, b: np.ndarray) -> np.ndarray:
    """Same-padded 1-D convolution over time, channel-major, then relu; w is [out][in][kernel]."""
    h, t = x.shape
    k = w.shape[2]
    pad = np.pad(x, ((0, 0), (k // 2, k // 2)))
    cols = np.stack([pad[:, q:q + t] for q in range(k)], axis=2)  # (in, t, k)
    return np.maximum(np.einsum("oik,itk->ot", w, cols) + b[:, None], 0)


class _Graph(Reader):
    """A model with one input of (1, frames, features), and optionally a second of one number, the aspect."""

    def _fit(self, shapes: list[list], out_shape: list) -> None:
        spec = self.spec
        main = [i for i, s in enumerate(shapes) if len(s) == 3]
        if len(main) != 1 or len(shapes) - 1 != len([s for s in shapes if len(s) != 3]) or len(shapes) > 2:
            raise Refused(f"model inputs {shapes} are not one (1, frames, features) and at most one aspect")
        self.main = main[0]
        self.has_aspect = len(shapes) == 2
        _, t, f = shapes[self.main]
        if isinstance(f, int) and f != spec.features:
            raise Refused(f"model takes {f} features per frame, builder {spec.input} gives {spec.features}")
        if isinstance(t, int) and t != spec.frames:
            raise Refused(f"model takes {t} frames, spec says {spec.frames}")
        if self.has_aspect is False and spec.input == "raw218":
            raise Refused("builder raw218 needs the model's aspect input")
        n = out_shape[-1]
        if isinstance(n, int) and n != len(spec.labels):
            raise Refused(f"model gives {n} scores, spec lists {len(spec.labels)} labels")


class LiteRt(_Graph):
    def __init__(self, spec: Spec, name: str, file: str, path: Path):
        super().__init__(spec, name, file)
        try:
            try:
                from ai_edge_litert.interpreter import Interpreter
            except ImportError:
                from tflite_runtime.interpreter import Interpreter  # the older package name
        except ImportError:
            raise Refused("the LiteRT package (ai-edge-litert) is not installed on this station")
        try:
            self.interp = Interpreter(model_path=str(path))
            self.interp.allocate_tensors()
        except Exception as e:
            raise Refused(f"LiteRT cannot load the file ({type(e).__name__}: {e})")
        self.ins = self.interp.get_input_details()
        self.out = self.interp.get_output_details()
        self._fit([[int(d) for d in i["shape"]] for i in self.ins], [int(d) for d in self.out[0]["shape"]])

    def logits(self, x, aspect):
        for i, d in enumerate(self.ins):
            v = x[None] if i == self.main else np.array([[aspect]], np.float32)
            self.interp.set_tensor(d["index"], v.astype(d["dtype"]))
        self.interp.invoke()
        return self.interp.get_tensor(self.out[0]["index"])


class Onnx(_Graph):
    def __init__(self, spec: Spec, name: str, file: str, path: Path):
        super().__init__(spec, name, file)
        try:
            import onnxruntime as ort
        except ImportError:
            raise Refused("onnxruntime is not installed on this station")
        try:
            self.session = ort.InferenceSession(str(path), providers=["CPUExecutionProvider"])
        except Exception as e:
            raise Refused(f"onnxruntime cannot load the file ({type(e).__name__}: {e})")
        ins, outs = self.session.get_inputs(), self.session.get_outputs()
        self.names = [i.name for i in ins]
        self._fit([list(i.shape) for i in ins], list(outs[0].shape))

    def logits(self, x, aspect):
        feed = {}
        for i, name in enumerate(self.names):
            feed[name] = x[None].astype(np.float32) if i == self.main else np.array([[aspect]], np.float32)
        return self.session.run(None, feed)[0]


def load_reader(model: Path) -> Reader:
    """Opens one model file with its spec, or raises Refused with the reason."""
    suffix = model.suffix.lower()
    if suffix in TRAINING_SUFFIXES:
        raise Refused(f"{suffix} is a training checkpoint: export it to ONNX (.onnx) with its spec file")
    spec_path = model.with_suffix(".json")
    if not spec_path.exists():
        raise Refused(f"no spec file {spec_path.name} beside it")
    spec = Spec(spec_path)
    name = model.stem
    if suffix == ".bin":
        reader = Format1(spec, name, model.name, model)
    elif suffix == ".tflite":
        reader = LiteRt(spec, name, model.name, model)
    elif suffix == ".onnx":
        reader = Onnx(spec, name, model.name, model)
    else:
        raise Refused(f"unknown file type {suffix}")
    try:
        reader.check()
    except Refused:
        reader.close()
        raise
    except Exception as e:
        reader.close()
        raise Refused(f"the model fails on a test input ({type(e).__name__}: {e})")
    return reader


# ---- the set of readers ---------------------------------------------------------------------------------------

def load_word_map(path: Path) -> set[str] | None:
    """The labels the app links to its own words or picture cards (sign_words.json); None when there is no map."""
    try:
        d = json.loads(path.read_text(encoding="utf-8"))
        return set(d.get("words", {})) | set(d.get("cards", {}))
    except (OSError, ValueError, AttributeError):
        return None


class Readers:
    """Every reader in a folder, merged. [scan] is one pass over the folder; [start] runs it every few seconds."""

    SETTLE = 1.5  # a file changed this recently may still be copying: looked at on the next pass

    def __init__(self, folder: Path, words: Path | None = None, log=print):
        self.folder = Path(folder)
        self.words_path = words
        self.log = log
        self._lock = threading.Lock()
        self._loaded: dict[str, tuple[tuple, Reader]] = {}
        self._refused: dict[str, tuple[tuple, str]] = {}
        self._order: list[Reader] = []
        self.folder.mkdir(parents=True, exist_ok=True)

    def __bool__(self) -> bool:
        return bool(self._order)

    @property
    def names(self) -> list[str]:
        return [r.name for r in self._order]

    def start(self, every: float = 2.0) -> None:
        self.scan()

        def loop():
            while True:
                time.sleep(every)
                try:
                    self.scan()
                except Exception as e:  # a bad folder pass must not end the watcher
                    self.log(f"Sign readers: scan failed ({type(e).__name__}: {e})")

        threading.Thread(target=loop, name="isl-watch", daemon=True).start()

    def scan(self) -> None:
        # A folder that was removed holds no models: its readers end with it, and the folder is made again.
        self.folder.mkdir(parents=True, exist_ok=True)
        files = {p.name: p for p in self.folder.iterdir()
                 if p.is_file() and p.suffix.lower() in MODEL_SUFFIXES + TRAINING_SUFFIXES}
        now = time.time()
        changed = False
        for name in list(self._loaded):
            if name not in files:
                with self._lock:
                    _, gone = self._loaded.pop(name)
                gone.close()
                self.log(f"Sign reader {gone.name} removed; its service ends with it")
                changed = True
        for name in list(self._refused):
            if name not in files:
                del self._refused[name]
        for name, path in sorted(files.items()):
            spec = path.with_suffix(".json")
            try:
                st, ss = path.stat(), (spec.stat() if spec.exists() else None)
            except OSError:
                continue
            sig = (st.st_mtime_ns, st.st_size, ss.st_mtime_ns if ss else 0, ss.st_size if ss else 0)
            if now - max(st.st_mtime, ss.st_mtime if ss else 0) < self.SETTLE:
                continue
            if (name in self._loaded and self._loaded[name][0] == sig) or (name in self._refused and self._refused[name][0] == sig):
                continue
            try:
                reader = load_reader(path)
            except Refused as e:
                self._refused[name] = (sig, str(e))
                kept = " (the earlier file keeps running)" if name in self._loaded else ""
                self.log(f"Sign reader {name} refused: {e}{kept}")
                continue
            except Exception as e:
                self._refused[name] = (sig, f"{type(e).__name__}: {e}")
                self.log(f"Sign reader {name} refused: {type(e).__name__}: {e}")
                continue
            with self._lock:
                old = self._loaded.get(name)
                self._loaded[name] = (sig, reader)
            if old:
                old[1].close()
            self._refused.pop(name, None)
            missing = self._missing(reader)
            reader.missing = None if missing is None else len(missing)
            note = "" if missing is None else (f"; {len(missing)} words not in the app's word map" if missing else "; all words in the app's word map")
            self.log(f"Sign reader {reader.name} loaded: {len(reader.labels)} words{note}")
            changed = True
        if changed:
            self._rebuild()

    def _broke(self, reader: Reader, why: str) -> None:
        """Takes a reader that failed on a real input out of use until its file changes."""
        with self._lock:
            for name, (sig, r) in list(self._loaded.items()):
                if r is reader:
                    del self._loaded[name]
                    self._refused[name] = (sig, f"failed while reading: {why}")
        reader.close()
        self.log(f"Sign reader {reader.name} failed while reading ({why}); not used until its file changes")
        self._rebuild()

    def _rebuild(self) -> None:
        with self._lock:
            order = sorted((r for _, r in self._loaded.values()), key=lambda r: (-r.spec.priority, r.name))
            self._order = order

    def _missing(self, reader: Reader) -> list[str] | None:
        known = load_word_map(self.words_path) if self.words_path else None
        if known is None:
            return None
        return [w for w in reader.labels if w not in known]

    def read(self, frames: list[dict], aspect: float, top: int = 10) -> dict:
        order = self._order
        if not order:
            raise LookupError("no sign reader")
        guesses, first, seconds = None, None, []
        for r in order:
            try:
                got = r.read(frames, aspect, top if first is None else 1)
            except ValueError:
                if first is None:
                    raise  # the client's frames, refused by the reader that answers
                continue  # a later reader that cannot use these frames adds no dispute
            except Exception as e:
                # A model that breaks is passed over; the next one answers. The status shows it.
                self._broke(r, f"{type(e).__name__}: {e}")
                continue
            if first is None:
                guesses, first = got, r
            elif got:
                seconds.append((r.name, got[0]))
        if first is None:
            raise LookupError("every sign reader failed")
        out = {"guesses": guesses, "model": first.name, "models": [r.name for r in order]}
        if guesses:
            disputing = [(n, g) for n, g in seconds if g["word"] != guesses[0]["word"] and g["score"] >= OTHER_SURE]
            if disputing:
                guesses[0]["disputed"] = True
                out["disputed_by"] = [{"model": n, **g} for n, g in disputing]
        return out

    def status(self) -> dict:
        models = []
        for r in self._order:
            models.append({"name": r.name, "file": r.file, "format": r.format, "input": r.spec.input, "words": len(r.labels),
                           "missing_from_app": getattr(r, "missing", None), "priority": r.spec.priority})
        refused = [{"file": f, "reason": why} for f, (_, why) in sorted(self._refused.items())]
        return {"models": models, "refused": refused}
