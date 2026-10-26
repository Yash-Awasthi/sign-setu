# Text to ISL

A text-to-sign model by Vaibhav Sharma: a DistilBERT encoder and a transformer decoder that turn an English
sentence into 100 frames of 196 features (52 face blendshapes, then 48 points x y z: shoulders, elbows, wrists and
21 points per hand). It trains on sentence videos from the ISL-CSLTR corpus (Elakkiya R. and Natarajan B., Mendeley
Data, doi:10.17632/kcmpdxky7p.1, CC BY 4.0). No trained weights ship yet; the app plays the corpus clips themselves.

Needs PyTorch, transformers, pandas and NumPy, plus MediaPipe and OpenCV for video processing. Data and weights stay
outside the repository (`processed_data/`, `*.pth`).

| Script | What it does | Run |
| --- | --- | --- |
| `build_labels.py` | `labels.csv` from the corpus's sentence folders | `build_labels.py` |
| `process_sign_videos.py` | Each video through MediaPipe Face, Pose and Hand Landmarker to a `frames x 196` `.npy` | `process_sign_videos.py --input_dir <videos> --output_dir processed_data --models_dir <tasks>` |
| `train.py` | Trains `isl_transformer_v1.pth` on `labels.csv` and `processed_data/` | `train.py` |
| `evaluate.py` | Model output for every sentence in `labels.csv`, as `prediction.js` | `evaluate.py` |
| `inspect_ground_truth.py` | The processed corpus clip of every sentence, as `prediction.js` | `inspect_ground_truth.py` |
| `export_clips.py` | `prediction.js` to the app's `content/clips.json` | `export_clips.py prediction.js`, then `tools/content/build_content.py --manifest-only` |

A typed sentence that matches a clip (case and punctuation ignored) plays the clip on the stage and the Type-to-Sign
screen; anything else is signed word by word. Tests: `python -m unittest discover -s tools/text2isl` (exporter), and `python -m pytest -q tools/text2isl/tests` with PyTorch (data loader).
