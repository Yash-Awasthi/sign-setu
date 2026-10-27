# Recorded sentence clips

The character plays recorded ISL sentences when a typed sentence or an asked question matches one (case and
punctuation ignored); anything else is signed word by word. The clips come from the ISL-CSLTR corpus (Elakkiya R. and
Natarajan B., Mendeley Data, doi:10.17632/kcmpdxky7p.1, CC BY 4.0) and the team's recordings of twelve of the app's
own questions (`ml-data\text2isl\prediction_isl(sent_over_whatsapp).js`, kept outside the repository).

Needs pandas and NumPy, plus MediaPipe and OpenCV for video processing. Data stays outside the repository
(`$SIGNSETU_DATA\text2isl`); `get-data.ps1` downloads the corpus and the MediaPipe models there.

| Script | What it does | Run |
| --- | --- | --- |
| `build_labels.py` | `labels.csv` from the corpus's sentence folders | `build_labels.py` |
| `process_sign_videos.py` | Each video through MediaPipe Face, Pose and Hand Landmarker to a `frames x 196` `.npy` (52 face blendshapes, then 48 points x y z: shoulders, elbows, wrists, 21 points per hand) | `process_sign_videos.py --input_dir <videos> --output_dir processed_data --models_dir <tasks>` |
| `pick_clips.py` | The most typical clip of every sentence (smallest summed DTW distance to its other clips), as `medoids.json` | `pick_clips.py` |
| `inspect_ground_truth.py` | The processed corpus clip of every sentence, as `prediction.js` | `inspect_ground_truth.py` |
| `export_clips.py` | Prediction files to the app's `content/clips.json`, each track steadied (tracking slips replaced by the local median, then smoothed, depth more than x and y); a later file replaces an earlier one's sentence | `export_clips.py medoids.json prediction.js "..\..\ml-data\text2isl\prediction_isl(sent_over_whatsapp).js"`, then `tools/content/build_content.py --manifest-only` |

Tests: `python -m unittest discover -s tools/text2isl` (exporter) and `python -m pytest -q tools/text2isl/tests` (clip
picker; `test_process_sign_videos.py` needs MediaPipe).
