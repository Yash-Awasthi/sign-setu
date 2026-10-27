"""Landmarks the Kaggle ISL 60-word videos with the app's Holistic model, one pickle per clip.
Run from C:\\work with the signsetu-mp venv; build_isl_more.py turns the clips into a training cache."""
import concurrent.futures
import os
import pathlib
import pickle
import sys

from prepare_include_video import extract

# Training and evaluation data stay outside the repository.
DATA_ROOT = pathlib.Path(os.environ.get("SIGNSETU_DATA", "ml-data"))


def clips(data: pathlib.Path):
    # Kaggle: Video_Dataset/Video_Dataset/<Word>/WIN_<time>.mp4; the _tilt copies are rotated duplicates.
    for p in sorted((data / "isl_kaggle60" / "Video_Dataset" / "Video_Dataset").glob("*/*.mp4")):
        if "tilt" not in p.stem:
            yield p, f"kaggle__{p.parent.name}__{p.stem}"


def main(data: pathlib.Path, workers: int) -> None:
    out = data / "isl_more" / "landmarks"
    out.mkdir(parents=True, exist_ok=True)
    jobs = [(p, out / (name + ".pkl")) for p, name in clips(data)]
    print(f"{len(jobs)} clips", flush=True)
    with concurrent.futures.ProcessPoolExecutor(workers) as pool:
        futures = [pool.submit(extract, p, o) for p, o in jobs]
        for n, f in enumerate(concurrent.futures.as_completed(futures), 1):
            if err := f.result():
                print("failed", err, flush=True)
            if n % 100 == 0:
                print(f"{n}/{len(jobs)} landmarked", flush=True)


if __name__ == "__main__":
    main(DATA_ROOT, int(sys.argv[1]) if len(sys.argv) > 1 else 6)
