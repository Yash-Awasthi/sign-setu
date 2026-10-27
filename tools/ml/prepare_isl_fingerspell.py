"""Landmarks the ISL-Fingerspelling segments (ISH News, 1,308 clips, 3 signers, CC BY-NC 4.0) with the app's Holistic
model, one pickle per clip, then joins them with the letter annotations and the signer split into one cache.
Run from C:\\work with the signsetu-mp venv."""
import concurrent.futures
import csv
import json
import os
import pathlib
import pickle
import sys

from prepare_include_video import extract

# Training and evaluation data stay outside the repository.
DATA_ROOT = pathlib.Path(os.environ.get("SIGNSETU_DATA", "ml-data"))


def main(data: pathlib.Path, workers: int) -> None:
    src = data / "isl_fingerspell"
    out = src / "landmarks"
    out.mkdir(exist_ok=True)
    videos = sorted((src / "videos").glob("*.mp4"))
    with concurrent.futures.ProcessPoolExecutor(workers) as pool:
        futures = [pool.submit(extract, v, out / (v.stem + ".pkl")) for v in videos]
        for n, f in enumerate(concurrent.futures.as_completed(futures), 1):
            if err := f.result():
                print("failed", err, flush=True)
            if n % 100 == 0:
                print(f"{n}/{len(videos)} landmarked", flush=True)
    letters = json.loads((src / "letter_annotations.json").read_text(encoding="utf-8"))
    with open(src / "signer_split.csv", newline="") as f:
        signers = {r["uid"]: r for r in csv.DictReader(f)}
    samples = []
    for uid, marks in sorted(letters.items()):
        p = out / (uid + ".pkl")
        if p.exists() and uid in signers:
            # The letter annotations are the set's authoritative transcription.
            samples.append({**pickle.load(open(p, "rb")), "uid": uid, "text": "".join(m["letter"] for m in marks),
                            "letters": marks, "signer": signers[uid]["signer"], "split": signers[uid]["split"]})
    with open(src / "isl_fingerspell.pkl", "wb") as f:
        pickle.dump(samples, f)
    print(len(samples), "segments")


if __name__ == "__main__":
    main(DATA_ROOT, int(sys.argv[1]) if len(sys.argv) > 1 else 6)
