"""Format 3 export: torch to ONNX (test2isl venv), then ONNX to LiteRT and a parity check (litert venv; LiteRT's own
torch converter has no Windows build).

    test2isl: python export_litert.py onnx OUT [arch ...] [--ckpt model.pt]   (random weights without --ckpt)
    litert:   python export_litert.py tflite OUT [arch ...]
"""
import json
import os
import pathlib
import subprocess
import sys

import numpy as np

T = int(os.environ.get("LITERT_FRAMES", 48))  # the shipped reader reads 32 frames
TOL = 1e-3


def sample_input(rng):
    from models import RAW
    raw = rng.uniform(0.2, 0.8, (1, T, RAW)).astype(np.float32)
    raw[..., RAW - 4:] = (rng.random((1, T, 4)) < 0.8).astype(np.float32)
    raw[0, :, RAW - 2] = 1  # pose seen throughout, as in a real sign window
    return raw, np.array([[848 / 480]], np.float32)


def to_onnx(out: pathlib.Path, archs, ckpt=None):
    import torch
    from models import ARCHS
    out.mkdir(parents=True, exist_ok=True)
    rng = np.random.default_rng(7)
    raw, aspect = sample_input(rng)
    np.save(out / "raw.npy", raw)
    np.save(out / "aspect.npy", aspect)
    for name in archs:
        torch.manual_seed(7)
        if ckpt:
            # A trained reader (ship_w.py's checkpoint) sets its own head and width.
            state = torch.load(ckpt, weights_only=True)["state"]  # nosemgrep: trailofbits.python.pickles-in-pytorch.pickles-in-pytorch
            model = ARCHS[name](classes=state["head.bias"].shape[0], hidden=state["embed.bias"].shape[0])
            model.load_state_dict(state)
        else:
            model = ARCHS[name](classes=300)
        model.eval()
        with torch.no_grad():
            ref = model(torch.from_numpy(raw), torch.from_numpy(aspect)).numpy()
        np.save(out / f"{name}_torch.npy", ref)
        torch.onnx.export(model, (torch.from_numpy(raw), torch.from_numpy(aspect)), out / f"{name}.onnx",
                          input_names=["raw", "aspect"], output_names=["logits"], opset_version=17, dynamo=False)
        print(f"{name}: onnx written", flush=True)


def to_tflite(out: pathlib.Path, archs):
    import tensorflow as tf
    raw, aspect = np.load(out / "raw.npy"), np.load(out / "aspect.npy")  # nosemgrep
    result = {}
    for name in archs:
        conv = out / f"{name}_tf"
        # Keep inputs as (B, T, RAW): onnx2tf otherwise treats the last axis as channels and transposes them.
        r = subprocess.run([sys.executable, "-m", "onnx2tf", "-i", str(out / f"{name}.onnx"), "-o", str(conv),
                            "-kat", "raw", "aspect", "-n"], capture_output=True, text=True)
        files = sorted(conv.glob("*_float32.tflite"))
        if r.returncode or not files:
            result[name] = {"pass": False, "error": (r.stderr or r.stdout)[-400:]}
            continue
        try:
            it = tf.lite.Interpreter(model_path=str(files[0]))
            it.allocate_tensors()
            for d in it.get_input_details():
                it.set_tensor(d["index"], raw if d["shape"][-1] == raw.shape[-1] else aspect)
            it.invoke()
            got = it.get_tensor(it.get_output_details()[0]["index"])
        except RuntimeError as e:
            result[name] = {"pass": False, "error": str(e)[:300]}
            continue
        diff = float(np.abs(got - np.load(out / f"{name}_torch.npy")).max())
        result[name] = {"pass": diff < TOL, "max_abs_diff": diff, "bytes": files[0].stat().st_size}
    print(json.dumps(result, indent=1))
    return all(v["pass"] for v in result.values())


if __name__ == "__main__":
    mode, out, rest = sys.argv[1], pathlib.Path(sys.argv[2]), sys.argv[3:]
    ckpt = rest[rest.index("--ckpt") + 1] if "--ckpt" in rest else None
    archs = [a for a in rest if a not in ("--ckpt", ckpt)] or ["shipped", "b3", "b4"]
    if mode == "onnx":
        to_onnx(out, archs, ckpt)
    else:
        sys.exit(0 if to_tflite(out, archs) else 1)
