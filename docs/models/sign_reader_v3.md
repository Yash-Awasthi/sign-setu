# Model card: sign_reader v3 (word reader, format 1)

## Release status

The app's word reader in 0.8.2, replaced after it by v4 ([model card](sign_reader_v4.md)). On test the soup and the previous reader are equally accurate (5 clips to 4,
p=1) and the previous reader is better calibrated; the soup is better on new-signer validation and shows no word for
rest and pause windows. Licensing: usable in this non-commercial project. The weights were fine-tuned on ISLRTC dictionary clips and RKMVU-CBE channel
clips, which are used for training in this non-commercial project; written permission is to be sought before any
commercial or department release (`docs/08-open-source.md`). The previous reader stays available as the fallback
(`sign_reader.bin` md5 8e995e24c1f300c476ec3ccb81eaccaa, listed in `content/models/registry.json`).

## What it is

- The previous app reader (per-frame linear layer, two temporal convolutions with batch norm folded, average and max
  pooling, linear head; 32 frames; hidden 320), fine-tuned at one tenth of the original learning rate, with the
  previous reader's weights and head rows as the starting point.
- 314 words, the app's vocabulary, unchanged. Format 1 file, float16 weights, same features as before.
- Shipped weights: a uniform soup of 5 seeds of `tools/ml/exp.py WF --seed 1..5` on `cache_v5`; seed 1 alone was
  withdrawn as val-lucky (on new-signer clips it beat every other seed, seed 4 by 15 clips to 1). Each seed was
  trained on INCLUDE, Kaggle ISL video signers A, B and D, ISLRTC dictionary clips of 50 signer clusters and RKMVU-CBE
  clips of 19 clusters, with early stopping on new-signer validation restricted to the app's words. Temperature 2.23 and RAPS thresholds fitted two-fold on new-signer
  validation (n=59), refit after the test read-out showed miscalibration; test ECE and coverage for the refit are not
  unbiased, and it was not scored on test.

## Data and signers

- Signer identity is the face cluster (SFace embeddings, agglomerative clustering) across all sources; one person is
  never on both sides of a split. Held-out signers are frozen in `q3/held_out.json`.
- Validation: one ISLRTC signer, one RKMVU signer, the Kaggle signer C (same studio as training; reported, never used to
  choose). Test: one ISLRTC signer and one RKMVU signer, read once.
- INCLUDE is one lumped signer (no faces available); its own test split is held out of training.
- Near-duplicate clips across a training and held-out pair are dropped from training (45 clips).

## Results

Validation (new signers, app words only, n=59; Kaggle signer C n=298):

| reader | new signers | Kaggle C |
|---|---|---|
| previous app reader | 0.407 | 0.940 |
| W (first fine-tune, previous build) | 0.441 | 0.970 |
| seed 1 alone (selected on validation, optimistic; withdrawn) | 0.559 | 0.993 |
| this recipe, mean of 5 seeds | 0.529 | 0.985 |
| this reader: weight soup of 5 seeds | 0.492 | 0.983 |

The 5-seed soup beats W on new-signer clips scored as the app shows them (n=256; 14 clips to 2, p=0.004), as did each
of the first three seeds and the 3-seed soup (McNemar p from <0.001 to 0.013).

Test (read once, with seed 1 before it was withdrawn; app words only; n=62, of which 61 from one ISLRTC signer;
test is closed, so the soup has no test number):

| reader | top-1 [95%] | ECE | RAPS coverage at 50% / 80% |
|---|---|---|---|
| soup of 5 seeds, first calibration (T 1, full val; replaced) | 0.387 [0.000, 0.484] | 0.415 (T 1) | 0.419 / 0.419 |
| seed 1 (withdrawn) | 0.403 [0.000, 0.500] | 0.168 (T 1.98) | 0.452 / 0.742 |
| W | 0.387 | 0.150 (T 1) | 0.484 / 0.790 |
| previous app reader | 0.371 | 0.098 (T 0.86) | 0.597 / 0.823 |

Seed 1's test gain over the previous reader was 5 clips to 3 (p=0.73): not significant. Its calibration on test was
worse than the previous reader's (temperature 1.98 fitted on a small new-signer set). The soup's test read-out
(5 clips to 4 against the previous reader, p=1) shows the opposite failure: calibrated on a validation pool that is
mostly the studio signer, it is overconfident on the new signer (ECE 0.415) and its RAPS sets cover only 42% at both
levels, so the app would rarely mark a sign unsure. The shipped soup is therefore refit on new-signer validation only
(T 2.23; RAPS coverage on validation folds 0.56 / 0.72 at 50%, 0.78 / 0.88 at 80%); that refit has no unbiased test
number. Calibration needs a larger new-signer set.

Floors (soup): INCLUDE test 0.982 [0.972, 0.990] (floor 0.979); Kaggle held-out signer 0.983 (floor 0.973).

Stitched sentence proxy (seed 1; one test signer, 12 intents, optimistic because it has no coarticulation): gloss WER 0.444
[0.196, 0.648], sentence exact 0.571. The soup on the validation stitches (19 intents, one signer, capture thresholds
tuned on the same stitches): gloss WER 0.700 [0.575, 0.818], sentence exact 0.169.

Non-sign windows (rests before a sign and card pauses after it, 2,437 validation windows): the soup shows a word at the
app's 0.4 floor for 0.000 of them (previous reader 0.139), while it shows 0.492 of new-signer app-word signs.

Weights stay float16: int8 weights agree with them on top-1 for 0.950 of 200 validation windows, under the 0.99 bar.
The same weights also ship in format 3 (`sign_reader_v3.tflite`, float32, 5.05 MB, LiteRT 1.4.2) with the features inside the graph; it is the default engine, and Settings, New sign reader engine, off goes back to format 1.
Hashes and metrics of every shipped model: `content/models/registry.json`.

## Known failures

- New signers: about 4 in 10 app-word signs right on a new ISLRTC signer; studio-like signers near 0.99.
- Validation and test each hold essentially one new signer per source; gains below about 8 points cannot be resolved.
- Seed-to-seed spread is large (up to 9 clips to 1 on the same validation set).
- Calibration overfits the small validation set: the fitted temperature helped on validation folds and hurt on test.
- Recipes cannot be told apart yet: the speed and shear augmentation led on cache_v5 but tied on cache_v6 (3 clips to 3).
- Words with a single training clip are not learned by the classifier (0 of 125 on validation); prototype retrieval
  reaches 0.08 there but costs app-word accuracy and is not shipped.

## Tried and not adopted

Each was compared with the shipped soup on new-signer validation (McNemar, p < 0.01 required): test-time augmentation,
probability ensembles, prototype fusion (lost app-word clips), a random search over learning rate and augmentation, the
signer-adversarial head, phonology auxiliary heads, look-alike label smoothing, HWGAT-style speed and shear
augmentation (positive but p 0.22), filling missing hand frames (lowered the INCLUDE floor to 0.931), the b4
encoder from masked pose pretraining, soups of 8 and 15 seeds, the same recipe on more landmarked clips (final2), and
a non-sign class (1 clip to 5, p 0.22; it showed fewer real signs at the 0.4 floor).

## Licences

- INCLUDE (CC BY 4.0), Kaggle Indian Sign Language video dataset (MIT): as listed in `docs/08-open-source.md`.
- ISLRTC dictionary: written permission required (see Release status).
- RKMVU-CBE channel: no licence stated (see Release status).
- MediaPipe landmarker models: Apache 2.0.
