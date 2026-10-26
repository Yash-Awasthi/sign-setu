import json
from pathlib import Path
import numpy as np
import pandas as pd


def export_all_ground_truth(
    labels_csv: str = "labels.csv",
    npy_dir: str = "processed_data",
    output_file: str = "prediction.js",
):
  npy_path = Path(npy_dir)
  labels_path = Path(labels_csv)

  if not labels_path.exists():
    print(f"Error: {labels_csv} not found. Run make_labels.py first.")
    return

  df = pd.read_csv(labels_path)
  sentences = df["sentence"].dropna().unique().tolist()
  print(f"Packaging ground truth for {len(sentences)} sentences...")

  master_ground_truth = {}

  for sentence in sentences:
    sample_rows = df[df["sentence"] == sentence]
    target_npy = None

    for _, row in sample_rows.iterrows():
      candidate = npy_path / f"{row['filename']}.npy"
      if candidate.exists():
        target_npy = candidate
        break

    if target_npy is None:
      print(f"Warning: No .npy found for sentence '{sentence}'. Skipping.")
      continue

    raw_matrix = np.load(target_npy)
    frames_list = []

    for frame_idx, frame_data in enumerate(raw_matrix):
      frame_list = [float(v) for v in frame_data]
      frames_list.append({
          "frame": int(frame_idx),
          "blendshapes": frame_list[:52],
          "coordinates": frame_list[52:],
          "quaternions": frame_list[52:],
      })

    master_ground_truth[sentence] = {
        "sentence": f"GROUND TRUTH: {sentence} ({target_npy.stem})",
        "total_frames": int(raw_matrix.shape[0]),
        "frames": frames_list,
    }
    print(
        f"  Added: '{sentence}' from {target_npy.name} (Frames:"
        f" {raw_matrix.shape[0]})"
    )

  if not master_ground_truth:
    print("Error: No valid .npy files were found to package.")
    return

  first_key = list(master_ground_truth.keys())[0]

  with open(output_file, "w", encoding="utf-8") as f:
    f.write(
        "window.predictionDatabase = "
        + json.dumps(master_ground_truth, indent=2)
        + ";\n"
    )
    f.write(f"window.predictionData = window.predictionDatabase['{first_key}'];")

  print(
      f"\nSaved multi-sentence ground truth to {output_file}. Refresh"
      " index.html to view!"
  )


if __name__ == "__main__":
  export_all_ground_truth()