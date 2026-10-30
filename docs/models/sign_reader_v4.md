# Model card: sign_reader v4 (word reader, format 1 and format 3)

## Release status

The app's word reader after 0.8.2, replacing v3 ([model card](sign_reader_v3.md)). On new-signer validation it reads
more signs right than v3 (22 clips to 6, p=0.0037) and holds the INCLUDE and Kaggle floors; the test split is closed,
so it has no test number. Licensing: its training adds ASL Citizen landmarks to the v3 data, and the ASL Citizen
dataset terms are not yet checked; they must be before any release. ISLRTC dictionary and RKMVU-CBE clips need written permission before any commercial or department
release (`docs/08-open-source.md`). v3 stays listed in `content/models/registry.json` as the previous reader.

## What it is

- The v3 architecture and features, unchanged: per-frame linear layer, two temporal convolutions with batch norm
  folded, average and max pooling, linear head; 32 frames; hidden 320; 314 app words.
- Trained from the app reader's weights at one tenth of the original learning rate on the v3 sources plus the ASL
  Citizen train split (40,154 clips), with one softmax head per language: an ASL clip is scored only over ASL words,
  an ISL clip only over ISL words, and ASL clips fill half of each batch (row B5A50F in `tools/ml/experiments.json`).
- Only the 314 ISL head rows of the app's words ship; the ASL head stays out of the app.
- Shipped weights: a uniform soup of 5 seeds (`tools/ml/exp.py B5A50F --seed 1..5`, `tools/ml/soup.py`), staged and
  calibrated by `tools/ml/ship_w.py`. Format 1: float16 (`sign_reader.bin`, md5 29cf0db90e17cb1c0ae635c896d86bcb).
  Format 3: float32 LiteRT (`sign_reader_v3.tflite`, md5 002ee8991bbaf6538567ec94a818a822), the default engine.

## Results

Validation, new signers (one ISLRTC signer, one RKMVU signer):

| reader | all words (n=546) | app words, as the app shows them (n=59) | Kaggle C |
|---|---|---|---|
| v3 (WF soup) | 0.071 | 0.492 | 0.983 |
| this reader | 0.101 | 0.525 | 0.993 |

McNemar against v3 on all new-signer clips: 22 clips to 6, p=0.0037. Each seed alone: 0.47 to 0.53 on app words.

Floors: INCLUDE test 0.979 [0.968, 0.988] (floor 0.979, at the floor); Kaggle held-out signer 0.993 (floor 0.973).

Non-sign windows (2,437 validation rests and card pauses): shown as a word at the app's 0.4 floor 0.000 of the time,
as v3; new-signer app-word signs shown and right at that floor 0.407 (v3 0.390).

Calibration: temperature 2.57 and RAPS thresholds fitted two-fold by word on new-signer validation (n=59); the
temperature lowered ECE on both folds. RAPS coverage on the folds: 0.56 / 0.72 at 50%, 0.89 / 0.78 at 80%.

Engines: format 3 against format 1 on 400 validation windows, top word agreement 1.000, largest logit difference
0.018; conversion parity 5.3e-05.

`sign_confusion.json` holds this reader's validation readings, true word to first guess.

## Known failures

- New signers remain hard: 0.10 over all 868 words and about half of app-word signs on validation, against 0.99
  on Kaggle held-out clips. Most words come from one ISLRTC signer each; more signers per word, not more clips of
  the same signers, is what moves this number.
- The INCLUDE floor is met exactly, with no margin.
- Validation holds essentially one new signer per source; gains below about 8 points cannot be resolved.
- Calibration rests on 59 clips.

## Licences

- INCLUDE (CC BY 4.0), Kaggle Indian Sign Language video dataset (MIT), ISLRTC dictionary, RKMVU-CBE channel: as in
  the v3 card and `docs/08-open-source.md`.
- ASL Citizen (Microsoft Research): training data; its dataset terms are not yet checked. No ASL Citizen video or
  landmark ships.
- MediaPipe landmarker models: Apache 2.0.
