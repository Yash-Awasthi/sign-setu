# Reader evaluation

Output of `eval_isl.py` and `eval_asl.py` on the shipped models.

    ISL words    top-1 0.9792  top-3 0.9951  (n=816, INCLUDE test, sign_reader)
    ISL letters  top-1 0.3538  top-3 0.4615  (n=65, isl_spell)
    ISL digits   top-1 0.5000  top-3 0.5227  (n=44, isl_spell)
    ISL combined words   top-1 0.9767  word-only 0.9767  (n=816, gate costs 0.0000)
    ISL combined spelled read as spelling 0.2385, correctly 0.1743  (n=109)
    ISL real letters  top-1 0.1304  top-3 0.3043  (n=23, ISLRTC signers, isl_spell)
    ISL real digits   top-1 0.8000  top-3 1.0000  (n=10, ISLRTC signers, isl_spell)
    ISL real gate odds 1.5: spelled 0.6061, correctly 0.2727  (n=33); INCLUDE words lost 0.0000
    ISL real gate odds 2.0: spelled 0.4848, correctly 0.2424  (n=33); INCLUDE words lost 0.0000
    ISL real gate odds 3.0: spelled 0.3636, correctly 0.2121  (n=33); INCLUDE words lost 0.0000
    ISL real gate odds 4.0: spelled 0.2121, correctly 0.1212  (n=33); INCLUDE words lost 0.0000
    ISL words, new signer  top-1 0.3420  top-3 0.4424  (n=269, ISLRTC dictionary clips of the reader's words, sign_reader with Kaggle ISL; 314 words)
    ISL words, Kaggle held-out signer  top-1 0.9733  top-3 0.9967  (n=300)
    ISL spelled names, held-out signer  CER 0.1204, letters only 0.1108, exact 0.2892  (n=498, isl_spell_ctc)
    ISL spelled names, ISLRTC acronyms  CER 0.75, letters found 8 of 20  (n=7, isl_spell_ctc)
    ISL words, CISLR       top-1 0.2721  top-3 0.3770  (n=610, CISLR clips of INCLUDE words, the 262-word sign_reader; was 0.2393 / 0.3459)
    ASL words    top-1 0.4669  top-3 0.6329  (n=5385, PopSign test signers, asl_reader; was 0.4020 / 0.5428)
    ASL spelling CER 0.0418  (400 synthetic sequences, asl_spell)
    ASL spelling, real CER 0.3677  (413 sequences of 10 held-out Kaggle signers, asl_spell; synthetic-only was 0.6580 on all 2,892)
    ASL combined words   top-1 0.3935  word-only 0.4020  (n=5385, gate costs 0.0085)
    ASL combined spelled read as spelling 0.7325  (400 sequences)
    ASL gate peak 0.80: real PopSign words lost 0.0156, spelled sequences passed 0.7900
    ASL gate peak 0.90: real PopSign words lost 0.0085, spelled sequences passed 0.7325
    ASL gate peak 0.95: real PopSign words lost 0.0059, spelled sequences passed 0.6925

Notes:
- `sign_reader` is trained with `SIGN_HAND_DROP=0.3` (each hand's frames dropped at a random rate up to 0.3), as the
  phone loses a hand for a few frames at a time. Rates 0.15 to 0.7 score alike; pose face points (nose, eyes, mouth)
  lowered both new-signer sets (0.27 and 0.21 top-1), so the features are unchanged.
- INCLUDE landmarked from its videos with the app's own Holistic model (`prepare_include_video.py`) scores the same as
  the pose release (0.327 / 0.462 new signer, 0.277 / 0.382 CISLR), so the extractor is not what limits new signers.
- CISLR (`prepare_cislr.py`) is evaluation only: trained on half its words, ISLRTC on those words rose from 0.40 to
  0.87, so CISLR copies the ISLRTC dictionary signer, and the other half fell. Its licence is also unsettled.
- `asl_reader` trains on PopSign plus Google's Kaggle `asl-signs` (`prepare_asl_signs.py`; 21 signers, 4 held out,
  frame aspect estimated per signer from hand bone lengths): `SIGN_TMAX=48 SIGN_HIDDEN=320 SIGN_AUG=strong`, 100
  epochs. Kaggle held-out signers: 0.28 / 0.46 shipped before, 0.52 / 0.70 trained on Kaggle alone.
- Combined rows apply the app's sign-or-spell gate (`SignReaders.kt`): an ISL letter or digit wins only at twice
  the word reader's top score; an ASL spelling wins only with at least 3 letters, each at least 0.9 sure. Otherwise the
  word reader answers alone, so a weak read can still ask the subject to sign slower.
- ISL letters and digits are trained on CC BY photographs of four signers (Multimodal ISL alphabet dataset) plus
  synthetic landmarks posed from the app's keyframes, with hand-normalised features. The letters and digits rows are
  ISLAN signers held out of training; the real rows read ISLRTC dictionary signers (`prepare_islrtc.py`, evaluation
  only): the 0 to 9 clips, and the letters of 7 acronyms the dictionary fingerspells, each take split evenly among its
  letters. Synthetic-only training scored 0.04 and 0.10 there; this model scores 0.13 and 0.80.
- INCLUDE's pose release scales its points by a portrait 1080x1920 frame, but the videos are landscape. `prepare.py`
  first kept the portrait aspect, so every body was stretched about three times in height and live signers looked
  unlike the training data. With the landscape aspect, INCLUDE's own test split is unchanged and the ISLRTC signer,
  never trained on, rises from 0.11 to 0.25 top-1 (`eval_signers.py`).
- `SIGN_AUG=strong train.py` moves and resizes each hand against the body in training (other signers hold their hands
  higher, lower and wider); the ISLRTC signer rises from 0.25 to 0.28 top-1, INCLUDE unchanged. The shipped
  `sign_reader` is trained this way, 320 wide (`SIGN_HIDDEN=320`, also for `export.py`): 0.29 and 0.44 against 0.28
  and 0.42 at 160. The ISLRTC clips both picked and scored this variant, so the gain may be slightly high.
- Adding each hand's shape from its own wrist as extra features gave 0.30 top-1 but 0.43 top-3 on the unseen signer
  (shipped: 0.29 and 0.44); top-3 is what the subject chooses from, so the feature set stays as it is.
- Adding the ISLRTC signer's clips for half of the shared words does not help on the other
  half: 0.23 top-1 against 0.26 without them, so one more signer per word is not shipped.
- `SPELL_MAX_REACH` (1 shoulder width) keeps an ISL letter from winning over a moving segment: the farthest a wrist
  strays from its median position is over 1.06 for 95% of INCLUDE words and under 0.95 for 90% of real ISLRTC letter
  and digit segments. Path length was tried first, but camera jitter adds up over a long hold.
  On the device test the ISLRTC sign for "student" had read as the digit 5.
- `SPELL_ODDS` went from 3 to 2 with the real-trained reader: correct spelled reads of real signers rise from 0.09 to
  0.24 and no INCLUDE word is lost at either value.
- `SPELL_MIN_PEAK` is 0.8: with the real-trained speller, 0.8 loses 0.0017 of real PopSign words (0.9: 0.0009) and
  passes 0.115 of real held-out spelled phrases of 3+ letters (0.9: 0.052).
- `asl_spell` trains on Google's ASL Fingerspelling (`prepare_asl_spell.py`, 24,616 digit-free phrases, 94 signers,
  every tenth held out) for 75% of each batch and synthetic ASLNow! sequences for the rest (`SPELL_REAL=<cache>
  spell_ctc.py train`). Real held-out CER 0.368, against 0.658 for the synthetic-only reader; synthetic CER 0.042.
- ASL words: all 250 PopSign signs, trained on the train and validation signers (43,928 clips with the game fill-in);
  test signers are not in training. 0.40 is the release-1 ceiling on these landmarks. Round 4 (`train_asl.py`, 200
  epochs each): width 320 gave 0.3957, with mixup 0.3970, width 1024 0.3987, and the three as an ensemble 0.4046, so
  no distilled student of this format beats the shipped 0.4020. The clips hold about 5 source frames (median 13 at
  15 fps after interpolation) with a hand found in 16% (left) and 41% (right) of frames; 0.6 needs denser landmarks
  (Kaggle `asl-signs` at 30 fps, credentials) or a new reader format; neither is built.
- Calibration of the shipped `sign_reader` on the unseen ISLRTC signer (n=199): mean top score 0.26 against 0.29
  accuracy, expected calibration error 0.09, and temperature 1.0 already gives the lowest log loss (0.75: 0.146 ECE,
  1.5: 0.190). Reads at or above `READ_MIN_SCORE` 0.4 are 27% of reads and 51% right. No temperature is applied. A
  conformal choice set at 90% coverage would need far more than the three pictures the subject chooses from, because
  top-3 is 0.44, so the fixed top three stay.
- A transformer head (3 layers, width 192, same features and strong augmentation, 300 epochs) scored 0.276 top-1 and
  0.377 top-3 on the unseen ISLRTC signer, and 0.62 inside the lesson questions' answer cards (shipped: 0.73), so the
  convolution reader stays.
- Pretraining the trunk without labels (SimCLR on the training augmentation, 20,000 steps on the 6.8 hours of
  OpenHands Sign Library poses, zenodo.org/records/5558897; the script is not kept) then fine-tuning on INCLUDE gave 0.316 / 0.424 on all 269 ISLRTC clips of INCLUDE words
  (`prepare_islrtc_words.py`) against 0.312 / 0.428 from scratch, within noise; at 100 epochs 0.283 against 0.271.
  It reaches INCLUDE's own accuracy sooner but does not help new signers, so it is not shipped. Adding ISH News
  (51.6 hours with Sign Library, many presenters) gave 0.309 / 0.446, also within noise: more unlabelled signers do
  not move this reader; labelled clips from more signers are what would.
- OpenHands' released INCLUDE checkpoint (ST-GCN with DPC pretraining on 1,129 hours, 263 classes, 3.6M parameters)
  scores 0.75 on INCLUDE's test split here and 0.07 to 0.09 top-1 on the ISLRTC clips (shipped: 0.32); it reads only
  each clip's first 60 frames, and its preprocessing could not be matched beyond 0.75.
- Lesson-sheet answers, read among the question's own answer cards as the app does (`decideRead` with two or
  more cards): unseen ISLRTC signer top-1 0.73, top-3 0.88 (n=26); INCLUDE test signers 0.98 and 1.00 (n=111). The
  misses are male and female pairs (mother and father, grandmother and grandfather, wife and husband). Averaging over
  mirrored and rescaled copies at read time changed nothing (0.286 overall either way), so it is not used. More signers
  of the lesson words are what would raise the first number.
- Kaggle ISL video set (prasadshet, 60 words plus "still", 4 signers, MIT; `prepare_isl_more.py`, `build_isl_more.py`)
  added to INCLUDE (314 words, shipped settings, 400 epochs): INCLUDE test 0.979 (unchanged), ISLRTC 269 clips 0.342 /
  0.442 against 0.312 / 0.428 for INCLUDE alone (within noise, no drop), held-out Kaggle signer 0.973 / 0.997 (n=300;
  INCLUDE alone 0.42 / 0.44 on the 9 shared words, n=45). The set ships no signer ids: clips were grouped by face and
  clothing per recording day and checked by eye (`ml-data/isl_more/kaggle_signers.json`). All four signers share one
  room, camera and word list, so the held-out score is far easier than a new signer in the field. Shipped in 0.8.1.
- ISL emergency words (Mendeley 2vfdm42337: accident, call, doctor, help, hot, lose, pain, thief; 26 signers) are
  close-ups of the hands with no face or shoulders, so they cannot join the shoulder-centred word reader. A hand-only
  reader (script since removed; Hand Landmarker, `norm: hands`) scores 1.00 on 5 held-out signers (n=80) but
  0.30 / 0.70 on full-body ISLRTC dictionary clips of 6 of the words (n=10; chance 0.125 / 0.375; "call" has four
  dictionary signs). Trimming idle frames changed nothing. Not shipped.
- Kaggle asl-signs 1st place model ported to PyTorch (script since removed: x, y and 1 and 2 frame differences of hands
  and arms after per-clip nan-aware normalisation, 2 x (3 depthwise convolution blocks with ECA plus a transformer
  block), width 192, late dropout 0.8, its affine and time-mask augmentation plus ours; 64 resampled frames; AWP left
  out; 1.8M parameters, 300 epochs, 12 min) on INCLUDE plus Kaggle: INCLUDE test 0.978, ISLRTC 0.316 / 0.442, held-out
  Kaggle signer 1.000 / 1.000. Averaging its probabilities with the convolution reader gives ISLRTC 0.342 / 0.442 (the
  convolution reader alone: 0.342 / 0.442) and Kaggle 0.993 / 1.000. Below the 0.05 bar, so neither is shipped; a
  bigger model does not move the new-signer score either.
- ISLRTC dictionary onto the character: 55 more words from the official dictionary clips (`retarget_islrtc.py`, now
  keeping an unchanged clip's entry as it was), 208 ISLRTC signs in all. Includes yes, no, ok, pain, stop, wait,
  sorry, toilet, hungry, phone, lost, scared and the digits 1 to 9. Drafts until ISL review.
- The winner with AWP (weights perturbed by 0.2 x gradient / gradient norm from epoch 15, as in its source): INCLUDE
  test 0.965, ISLRTC 0.275 / 0.398, Kaggle signer 0.993 / 1.000, worse on new signers than without it. SL-GCN
  (SAM-SLR, script since removed, joint stream, 27 points, 120 epochs, 50 min): INCLUDE test 0.967, ISLRTC 0.253 / 0.383,
  Kaggle signer 0.990 / 1.000. No average of SL-GCN, either winner and the convolution reader beats the convolution
  reader alone on ISLRTC (best 0.342 / 0.446). None shipped; S2 closed.
- Adding a sign from one video (`oneshot.py`): the reader retrained without 60 words that have ISLRTC dictionary
  clips (shipped settings, 400 epochs); each held word then gets one reference clip, compared by cosine on the
  reader's pooled features (before the classifier). On INCLUDE test signers (n=190 clips of the 60 words): among the
  60 new words, one dictionary clip gives 0.332 / 0.484 and one INCLUDE signer's clip 0.711 / 0.889; among all 314
  words (old words by the mean of their training clips) 0.074 / 0.226 and 0.421 / 0.705, old words staying at 0.99.
  HomeSigns.kt's matcher (time warping on raw features) on the same clips, among the 60: 0.063 / 0.105 and 0.247 /
  0.347. Averaging 20 augmented copies of the one clip changed nothing (within 0.04). The shipped reader, which saw
  the words, gives the ceiling: 0.421 / 0.532 and 0.884 / 0.963 among the 60. So the reader's features are a far
  better matcher for HomeSigns than raw time warping, but a dictionary clip alone does not teach a word to other
  signers, and a new word seldom beats the old ones without a similarity bonus tuned on devices.
- Continuous ISL fingerspelling (`prepare_isl_fingerspell.py`, `isl_spell_ctc.py`): ISL-Fingerspelling (ISH News,
  1,308 segments, CC BY-NC 4.0, letter annotations as the transcription, a to z and space), its signer-independent
  split (signers 00 and 01 train, 810 segments; signer 02 test, 498). CTC over both hands (each hand's shape from its
  wrist, the right wrist from the left, both wrists from the chest, wrist steps; 96 features at 15 fps), the ASL
  speller's network at width 256 with dilations 1, 2, 4, 8, 1, 2; mirror, rotation, speed and hand-loss augmentation;
  200 epochs, 15 min. Held-out signer: CER 0.113 (letters only 0.108), whole segment exact 0.313 (best 0.109 at
  epoch 180). Examples: "amit shah", "harsh vardhan", "nirav modi" exact; "islamabad" read "isamabald". Studio news
  footage of trained interpreters, one test signer; phone video of untrained signers will score worse.
- That reader, trained only on spelling, read 4.9% of INCLUDE test word signs as a confident spelling (3+ letters, each
  at least 0.8 sure). Retrained with INCLUDE and Kaggle word clips as a quarter of each batch, taught as no letters
  (`ISPELL_NEGATIVES`): held-out signer CER 0.120 (letters only 0.111, exact 0.289); word signs read as spelling, by the
  least sure letter's floor: INCLUDE test 0.000 at every floor from 0.5 (n=816), unseen ISLRTC signers 0.034 at 0.5,
  0.015 at 0.6, 0.004 at 0.7 (n=269); spelled segments passing 0.91, 0.75, 0.63. Shipped as `isl_spell_ctc` with a
  floor of 0.7, beside `isl_spell` (kept for single letters and digits); a read that passes wins over the word reader.
- On the ISLRTC dictionary's fingerspelled acronyms (`prepare_islrtc.py`, the 7 clips behind the 0.13 letter row
  above): `isl_spell_ctc` reads "atm" exactly and finds 8 of 20 letters (CER 0.75; "bmw" read "b", "cd" and "url"
  nothing). Dictionary signers spell slower and more deliberately than news interpreters; more varied spelling data is
  what would close this.
- Home signs (`HomeSigns.kt`) now compare the word reader's pooled features by cosine when the reader is loaded,
  falling back to time warping without it. Threshold 0.65 from the held-out reader of `oneshot.py`: one clip of a new
  word from another signer keeps 74% of that word's clips by other signers above it and lets 0.66% of pairs of
  different words through (0.6: 83% and 1.7%; 0.7: 57% and 0.27%).

Text to sign, 2026-10-27:
- Sentence clips (`export_clips.py`) are now steadied: a point more than 0.05 and three deviations from its 7-frame
  median is a tracking slip and takes the median; then a Gaussian of 1 frame on x and y and 2.5 frames on z. Mean hand
  acceleration over the 112 clips: median 0.0127 to 0.0046. "where do you live" 0.021 to 0.006 (z step 0.052 to
  0.014, largest step 0.54 to 0.19); "what is your father's name" 0.070 to 0.006 (largest step 1.74 to 0.32);
  "who do you live with" 0.065 to 0.009; "how many brothers and sisters do you have" 0.058 to 0.004. Hand travel
  shrinks by up to a half on the worst clips, as the slips had inflated it.
- Joins between word signs: 378 of the then 470 `isl_signs.json` signs start and 404 end with both hands at the waist (rest,
  or the side anchor less than 15 units up). Inside a sentence those keys are now dropped (`TextSigning.kt`
  `trimmed`), so the hands go straight from one sign to the next: 0.55 s of the mean 1.39 s sign was waist time.
- Every word the reader knows now signs back on the character: the 47 Kaggle words that neither INCLUDE nor the
  ISLRTC dictionary covered are retargeted from the cleanest of 6 Kaggle takes each (`tools/isl/retarget_kaggle.py`),
  517 ISL signs in all.
- Negation: a typed sentence with no, not, never, nahi, mat or a negated auxiliary now shakes the character's head
  (turn 0.2 at 1.6 Hz) with slightly lowered brows through the sentence, beside the existing question brows.

## Sentence ranker (2026-10-28)

- Shipped word reader on 269 new-signer ISLRTC clips: top-1 0.342, top-3 0.442, top-5 0.480, top-10 0.524, top-20 0.595;
  mean top probability 0.20 (0.12 when wrong).
- `SentenceEvalTest`: 152 app sentences glossed in ISL order, each sign read by a simulated reader with that rank curve,
  ranked by `rankSentences` with a trigram model built without the sentence (written and ISL-ordered forms).
  Exact whole-sentence match, top-1 / top-5: 3 candidates, no model 0.026 / 0.033; 3 with model 0.020 / 0.053;
  10 candidates, no model 0.026 / 0.033; 10 with model 0.033 / 0.059. Shipped: 10 candidates with the model.
- The ceiling is the reader: with the right word in the top 10 for half the signs, a 3-sign sentence has every word on
  offer about 14% of the time. Ranking cannot pass that; reader recall on new signers and the question's own answer
  words (lifted by `expectedWords`, not in this simulation) are what move it.

## Calibration and conformal sets (2026-10-28, `calibrate.py`)

The only new signer on disk is the ISLRTC dictionary signer, so its 269 clips are split by word: 100 words fit, the
other 126 clips report. The held-out Kaggle signer is reported too.
- Temperature 0.857 (the reader is underconfident on a new signer). ECE on the report half 0.199 to 0.134, NLL 3.72 to
  3.64; Kaggle signer ECE 0.100 to 0.036, NLL 0.19 to 0.12. Ranking unchanged.
- RAPS sets (k_reg 3, lambda 0.02), coverage on the report half for each target: 50% gives 0.62 with a median of 17
  words; 60% 0.68 / 36; 70% 0.73 / 66; 80% 0.83 / 119; 90% 0.93 / 206. Coverage holds at every level. On the Kaggle
  signer the 50% set is one word (coverage 0.994): sets adapt to how sure the reader is.
- Risk at coverage (report half, error rate of the most confident share): 10% 0.17, 20% 0.20, 30% 0.24, 50% 0.37,
  100% 0.60. Even the surest tenth of a new signer's signs is wrong one time in six.
- Shipped: the temperature, and the 50% set as each sign's candidates; a set over 10 words marks the sign unsure.
  For a new signer no vocabulary-wide set is both small and safe; the question's answer words have to narrow it first.

## Gloss engine on simulated lattices (2026-10-28, `SentenceEvalTest`)

Every phrasebook intent (39) "signed" 40 times: each sign is a real held-out reading of that word
(`held_out_readings.py`: ISLRTC new signer 269 readings of 200 words; held-out Kaggle signer 816 of 261), names as
spelled tokens with a third unverified, 5% missed and 10% extra segments. Development numbers, not release ones.

| source, question on screen | n | intent sentence top-1 / top-2 | rungs 1 / 2 / 3 / 4 | wrong at rung 1 | right at rung 1 or 2 |
|---|---|---|---|---|---|
| ISLRTC, yes | 889 | 0.535 / 0.556 | 0.15 / 0.62 / 0.06 / 0.17 | 0.000 | 0.474 |
| ISLRTC, no | 889 | 0.336 / 0.445 | 0.06 / 0.39 / 0.25 / 0.29 | 0.018 | 0.242 |
| Kaggle, yes | 1151 | 0.945 / 0.953 | 0.64 / 0.20 / 0.16 / 0.00 | 0.019 | 0.809 |
| Kaggle, no | 1151 | 0.761 / 0.915 | 0.47 / 0.18 / 0.35 / 0.00 | 0.044 | 0.604 |

- The question on screen is the largest single gain: new-signer top-1 0.336 to 0.535.
- Before the "no sign vanishes" rule, rung 1 was wrong 5.7% of the time on the new signer: an unreadable sign was
  dropped as an extra segment and the sentence came out less specific ("a shirt" for "a pink shirt"). Unplaced signs
  now hold a sentence at rung 2.
- Remaining rung-1 errors are signs the segmenter missed outright, which no lattice evidence shows; the segmentation
  sanity term (docs/10, 2.5 and 5.2) needs recorded sentences.
- Rung 1 takes only signs whose 50% conformal set is one word (`RUNG1_DOUBT`); the table above is with that rule.
  Small samples: about 130 rung-1 sentences per new-signer row, so 0.000 means under about 3% at 95% confidence.
  `tools/eval/run_eval.py` reruns all of it and writes `tools/eval/report.html`.

### 44 intents (2026-10-28, after covering every asking question)

| source, question on screen | n | top-1 / top-2 | rungs 1 / 2 / 3 / 4 | wrong at rung 1 | right at rung 1 or 2 |
|---|---|---|---|---|---|
| ISLRTC, yes | 980 | 0.578 / 0.597 | 0.16 / 0.56 / 0.12 / 0.16 | 0.000 | 0.472 |
| ISLRTC, no | 980 | 0.295 / 0.407 | 0.06 / 0.33 / 0.32 / 0.29 | 0.036 | 0.209 |
| Kaggle, yes | 1354 | 0.945 / 0.958 | 0.65 / 0.20 / 0.15 / 0.00 | 0.022 | 0.810 |
| Kaggle, no | 1354 | 0.757 / 0.901 | 0.42 / 0.22 / 0.35 / 0.00 | 0.042 | 0.606 |

More intents help when the question is known and cost a little when it is not (more ways to be wrong); typed or
unknown questions are where rung 1 is least safe, so those answers lean on rungs 2 and 3.


## Reader plan v4

### P. Preflight (2026-10-28): all pass

| Check | Result |
|---|---|
| python jobs on islrtc_dict | none |
| junction write, free space through junction | 461 GB (D:) |
| landmark speed, 20 ISLRTC clips from D:, 848 px, split schedule, 6 processes | 68.4 fps total, 15.8 per process, 2.70 video s per wall s |
| memmap cache, 2,000 clips, 4 workers, 1 epoch | peak RAM 3.62 GB |
| LiteRT, in-graph features, random weights (via ONNX) | shipped 7.5e-8, B3 5.7e-7, B4 4.6e-7 max diff |
| yt-dlp, 5 RKMVU clips | ok, no bot check; channel tops out at 480p |
| ASL Citizen range request | 206 |
| YuNet, SFace | load, embed 128-d |

ETAs at measured speed: Q0 move 3.5 h (copy plus hash of 135 GB to D:), Q3 ISLRTC landmarks 36 h (15,350 clips, mean
23 s), RKMVU 6.4 h, Q4 ASL Citizen about 21 h. Q3 landmarks app words and multi-signer words first.

### Q. Overlapping jobs (2026-10-28)

All jobs share one guard: they stop at 20 GB of RAM used, and 12 landmark workers plus 4 loader workers leave cores free.

| job | resource | runs beside |
|---|---|---|
| Q chain 1: Q0 move (done), Q1 RKMVU, Q2 ISLRTC gaps | network, disk | everything below |
| Q3 priority words, then spans, faces, clusters | CPU, 12 workers | Q1, Q2, Q4 download, interim GPU |
| Q3 remainder: RKMVU and Q2 clips join the same pool as they land, then the full spans, faces and clusters | CPU, 12 workers | Q1, Q2, Q4 download, B rows |
| Q4 ASL Citizen download, 45.9 GB, capped at 8 MB/s | network | Q1, Q2, Q3 |
| Q4 landmarks | CPU, 12 workers | B rows (after Q3) |
| interim B0, B1 on INCLUDE and Kaggle only (no test signers), plumbing and timing only, no result row | GPU | Q1, Q2, Q3 priority words |
| S and B rows on the partial v4 cache | GPU | Q3 remainder, Q4 |
| B12 masked pose pretraining of the B4 encoder, train rows only (INCLUDE and Kaggle now; ISLRTC and RKMVU train clusters after S; ASL Citizen train after Q4); 20 s per epoch on 5,194 clips | GPU, pauses while a B row runs | Q1, Q2, Q3, Q4 |

### Resource log (every 10 min)

| time | CPU % | GPU %, VRAM GB | RAM used GB | landmark workers | jobs | action |
|---|---|---|---|---|---|---|
| 22:10 | 100 | 1, 0.8 | 16.3 | 18 | curl, fetch_rkmvu, landmark_split, to_c | under target |
| 22:20 | 100 | 6, 1.2 | 16.9 | 18 | curl, fetch_rkmvu, landmark_split, to_c | under target |
| 22:30 | 49 | 0, 1.2 | 10.6 | 17 | curl, exp B12, fetch_rkmvu | GPU under 70% during a B row: landmark workers to 17 |
| 22:40 | 42 | 0, 1.2 | 10.9 | 16 | curl, exp B12 | GPU under 70% during a B row: landmark workers to 16 |
| 22:50 | 42 | 24, 1.2 | 10.5 | 15 | curl, exp B12 | GPU under 70% during a B row: landmark workers to 15 |
| 23:00 | 43 | 53, 1.2 | 13.3 | 14 | curl, exp B4 | GPU under 70% during a B row: landmark workers to 14 |
| 23:10 | 100 | 0, 0.9 | 14.2 | 14 | curl | under target |

### C 3. Name margin and speller calibration (ISL-Fingerspelling held-out signer, n=498, two-fold)

- Speller CER 0.111. Temperature 2.00 / 2.00; NLL after 10.14; confidence ECE 0.232 at T=1, 0.258 after.
- NameResolver knobs (LAMBDA, unlisted prior, MARGIN) per fold: (3, 0.001, np.float64(0.0)) / (0.25, 0.001, np.float64(0.0)). Wrong snaps 0.004, names right 0.221; current knobs (1, 0.01, 2): 0.000 wrong, 0.235 right.
| 23:20 | 76 | 6, 0.7 | 8.0 | 14 | curl | under target |
| 23:30 | 75 | 7, 1.1 | 21.1 | 13 | curl, exp B0, exp B1, landmark_split | GPU under 70% during a B row: landmark workers to 13 |

### B. Experiments (new-signer val)

| ID | data | change | val: new-signer mean top-1 [95%]; all top-1 / top-5 | macro-F1 | worst signer | per source | NLL, ECE after T | RAPS size, coverage | non-sign FA | train top-1, gap | McNemar vs app model | old 269 | test | accepted |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| APP | INCLUDE + Kaggle (app as shipped) | the model in the app as shipped (format 1 weights, 32 frames, 314 words) | new signers 0.698 [0.305, 1.000] (n=59); all 0.852 [0.350, 1.000] / 0.922 | 0.691 | c1 0.397 | islrtc 0.397, kaggle 0.940, rkmvu 1.000 | 0.856, 0.061 | 11.4, 0.941 | nan | - | - | 0.343 | - | baseline |

APP top confused pairs (true -> predicted): elephant -> colour (4); busy -> photograph (2); crocodile -> city (2); drink -> friday (2); tea -> good (2); brinjal -> onion (1); busy -> soap (1); elephant -> plane (1); lemon -> young (1); lemon -> key (1)

| 23:40 | 86 | 0, 1.1 | 14.3 | 12 | exp B0, exp B1, landmark_split | GPU under 70% during a B row: landmark workers to 12 |
| B1 | include + islrtc + kaggle + rkmvu, 5866 clips, 94 s | shipped conv, + ISLRTC + RKMVU | new signers 0.375 [0.048, 0.833] (n=87); all 0.681 [0.067, 0.870] / 0.805 | 0.442 | c1 0.083 | islrtc 0.083, kaggle 0.849, rkmvu 0.667 | 1.848, 0.121 | 108.6, 0.925 | 1.000 | 0.338, -0.343 | 0.717 vs 0.852, p=4.47e-09 | 0.287 | - | baseline |

B1 top confused pairs (true -> predicted): man -> radish (5); switch -> drink (5); lemon -> radish (4); uncle -> drink (4); wrong -> karnataka (4); carrot -> radish (3); come -> pour (3); key -> radish (3); crocodile -> close (2); exam -> budget (2)

| B0 | include + kaggle, 5172 clips, 118 s | shipped design retrained on the shipped data, new splits | new signers 0.274 [0.115, 0.500] (n=87); all 0.810 [0.158, 0.987] / 0.842 | 0.548 | c1 0.214 | islrtc 0.214, kaggle 0.983, rkmvu 0.333 | 1.568, 0.086 | 83.0, 0.909 | 1.000 | 0.958, +0.148 | 0.874 vs 0.852, p=0.215 | 0.257 | - | baseline |

B0 top confused pairs (true -> predicted): switch -> uncle (3); area -> jump (2); switch -> wrong (1); switch -> drink (1); association -> president (1); audiology -> monsoon (1); baby -> jump (1); balance sheet -> car (1); box -> tool (1); brain -> lamp (1)

| 23:51 | 84 | 100, 7.7 | 17.4 | 12 | exp B3, exp B4, landmark_split |  |
| APP | INCLUDE + Kaggle (app as shipped) | the model in the app as shipped (format 1 weights, 32 frames, 314 words) | new signers 0.698 [0.305, 1.000] (n=59); all 0.852 [0.350, 1.000] / 0.922 | 0.691 | c1 0.397 | islrtc 0.397, kaggle 0.940, rkmvu 1.000 | 0.856, 0.061 | 11.4, 0.941 | nan | - | - | 0.343 | - | baseline |

APP top confused pairs (true -> predicted): elephant -> colour (4); busy -> photograph (2); crocodile -> city (2); drink -> friday (2); tea -> good (2); brinjal -> onion (1); busy -> soap (1); elephant -> plane (1); lemon -> young (1); lemon -> key (1)


The APP, B0 and B1 rows above on cache_v4 are superseded. Equal per-signer sampling starved INCLUDE (4,284 clips filed
as one signer, 1/57 of batches; B1 train top-1 on INCLUDE 0.14), and the vocabulary (542) left out most scored
new-signer words. Reruns below use cache_v4b (vocabulary 868 = every training word plus the app words, same frozen val
and test), square-root signer balance, and a 50% face drop in training for B3 and later.
| B0 | include + kaggle, 5172 clips, 102 s | shipped design retrained on the shipped data, new splits | new signers 0.068 [0.000, 0.113] (n=256); all 0.552 [0.047, 0.956] / 0.587 | 0.229 | c1 0.080 | islrtc 0.080, kaggle 0.960, rkmvu 0.056 | 3.468, 0.109 | 499.0, 0.903 | 1.000 | 0.970, +0.417 FLAG | 0.857 vs 0.852, p=0.864 | 0.269 | - | baseline |

B0 top confused pairs (true -> predicted): switch -> drink (5); crocodile -> temple (2); crocodile -> close (2); appointment -> location (2); area -> jump (2); blood pressure -> box (2); capital -> lamp (2); sandwich -> clean (2); brinjal -> onion (1); elephant -> deer (1)

| B1 | include + islrtc + kaggle + rkmvu, 6311 clips, 103 s | shipped conv, + ISLRTC + RKMVU (McNemar p=0.0708 vs B0) | new signers 0.087 [0.032, 0.194] (n=256); all 0.532 [0.049, 0.932] / 0.579 | 0.233 | c1 0.063 | islrtc 0.063, kaggle 0.933, rkmvu 0.111 | 3.560, 0.178 | 420.9, 0.899 | 1.000 | 0.882, +0.350 FLAG | 0.810 vs 0.852, p=0.0237 | 0.481 | - | unclear |

B1 top confused pairs (true -> predicted): switch -> carrot (5); come -> cook (3); uncle -> man (3); crocodile -> temple (2); drink -> uncle (2); lemon -> fedup (2); area -> island (2); brain -> shaheed bhagat singh (2); brinjal -> onion (1); drink -> carrot (1)

| 00:01 | 100 | 36, 1.7 | 17.2 | 17 | curl, exp B4, exp W, landmark_split | GPU under 70% during a B row: landmark workers to 17 |
| W | include + islrtc + kaggle + rkmvu, 6311 clips, 0 s | app reader fine-tuned: its weights and 314 head rows, 868-way head, 0.1x LR, frozen folded batch norm, 32 frames (McNemar p=0.832 vs APP) | new signers 0.072 [0.000, 0.125] (n=256); all 0.545 [0.056, 0.937] / 0.570 | 0.254 | c1 0.088 | islrtc 0.088, kaggle 0.940, rkmvu 0.056 | 3.741, 0.136 | 500.4, 0.906 | 1.000 | 0.839, +0.294 FLAG | 0.882 vs 0.852, p=0.00342 | 0.302 | - | no |

W top confused pairs (true -> predicted): crocodile -> air matress (4); elephant -> colour (4); switch -> format (3); appointment -> air matress (2); flood -> format (2); noun -> air matress (2); refund -> air matress (2); reservation -> air matress (2); sandwich -> format (2); sperm -> format (2)


W as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.716 [0.336, 1.000] (n=59), per source {'islrtc': 0.431, 'kaggle': 0.9698, 'rkmvu': 1.0}, against the app 0.882 vs 0.852, p=0.00342

| W | include + islrtc + kaggle + rkmvu, 6311 clips, 0 s | app reader fine-tuned: its weights and 314 head rows, 868-way head, 0.1x LR, frozen folded batch norm, 32 frames (McNemar p=0.832 vs APP) | new signers 0.086 [0.000, 0.125] (n=256); all 0.545 [0.056, 0.937] / 0.570 | 0.254 | c1 0.088 | islrtc 0.088, kaggle 0.940, rkmvu 0.056 | 3.741, 0.136 | 500.4, 0.906 | 1.000 | 0.839, +0.294 FLAG | 0.882 vs 0.852, p=0.00342 | 0.302 | - | no |

W top confused pairs (true -> predicted): crocodile -> air matress (4); elephant -> colour (4); switch -> format (3); appointment -> air matress (2); flood -> format (2); noun -> air matress (2); refund -> air matress (2); reservation -> air matress (2); sandwich -> format (2); sperm -> format (2)


W as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.441 [0.336, 1.000] (n=59), per source {'islrtc': 0.431, 'kaggle': 0.9698, 'rkmvu': 1.0}, against the app 0.882 vs 0.852, p=0.00342

| B4 | include + islrtc + kaggle + rkmvu, 6311 clips, 433 s | 1st-place Kaggle conv-transformer (McNemar p=2.74e-05 vs B1) | new signers 0.100 [0.051, 0.194] (n=256); all 0.572 [0.069, 0.984] / 0.610 | 0.273 | c1 0.088 | islrtc 0.088, kaggle 0.987, rkmvu 0.111 | 3.145, 0.086 | 492.0, 0.904 | 1.000 | 0.979, +0.406 FLAG | 0.866 vs 0.852, p=0.511 | 0.470 | - | unclear |

B4 top confused pairs (true -> predicted): bad -> onam (2); capital -> idea (2); crocodile -> temple (1); switch -> drink (1); turtle -> busy (1); uncle -> man (1); addicted dependent hooked -> red (1); address -> approval (1); africa -> board (panel) (1); agriculture -> advance (1)


Scoring note (2026-10-29 00:30): every exp.py score above was computed under bf16 autocast, which flips near-tie
predictions. In fp32, W and the app reader are equal on the new ISLRTC signer (0.431 both, n=58) and the Kaggle
validation signer (0.970 both, n=298); the bf16 run had the app at 0.397 and 0.940. W is not shipped, and the app keeps
its reader. The fp32 re-scores below replace the bf16 rows.
| APP | INCLUDE + Kaggle (app as shipped) | the model in the app as shipped (format 1 weights, 32 frames, 314 words) | new signers 0.407 [0.305, 1.000] (n=59); all 0.852 [0.350, 1.000] / 0.922 | 0.691 | c1 0.397 | islrtc 0.397, kaggle 0.940, rkmvu 1.000 | 0.854, 0.060 | 11.4, 0.941 | nan | - | - | 0.343 | - | baseline |

APP top confused pairs (true -> predicted): elephant -> colour (4); busy -> photograph (2); crocodile -> city (2); drink -> friday (2); tea -> good (2); brinjal -> onion (1); busy -> soap (1); elephant -> plane (1); lemon -> young (1); lemon -> key (1)


APP as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.407 [0.305, 1.000] (n=59), per source {'islrtc': 0.3966, 'kaggle': 0.9396, 'rkmvu': 1.0}, against the app -

| B0 | include + kaggle, 5172 clips, 0 s | shipped design retrained on the shipped data, new splits | new signers 0.078 [0.000, 0.113] (n=256); all 0.552 [0.047, 0.956] / 0.587 | 0.228 | c1 0.080 | islrtc 0.080, kaggle 0.960, rkmvu 0.056 | 3.469, 0.107 | 492.7, 0.899 | 1.000 | 0.970, +0.418 FLAG | 0.857 vs 0.852, p=0.864 | 0.269 | - | baseline |

B0 top confused pairs (true -> predicted): switch -> drink (5); crocodile -> temple (2); crocodile -> close (2); appointment -> location (2); area -> jump (2); blood pressure -> box (2); capital -> lamp (2); sandwich -> clean (2); brinjal -> onion (1); elephant -> deer (1)


B0 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.339 [0.237, 1.000] (n=59), per source {'islrtc': 0.3276, 'kaggle': 0.9597, 'rkmvu': 1.0}, against the app 0.857 vs 0.852, p=0.864

| APP | INCLUDE + Kaggle (app as shipped) | the model in the app as shipped (format 1 weights, 32 frames, 314 words) | new signers 0.407 [0.305, 1.000] (n=59); all 0.852 [0.350, 1.000] / 0.922 | 0.691 | c1 0.397 | islrtc 0.397, kaggle 0.940, rkmvu 1.000 | 0.854, 0.060 | 11.4, 0.941 | nan | - | - | 0.343 | - | baseline |

APP top confused pairs (true -> predicted): elephant -> colour (4); busy -> photograph (2); crocodile -> city (2); drink -> friday (2); tea -> good (2); brinjal -> onion (1); busy -> soap (1); elephant -> plane (1); lemon -> young (1); lemon -> key (1)


APP as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.407 [0.305, 1.000] (n=59), per source {'islrtc': 0.3966, 'kaggle': 0.9396, 'rkmvu': 1.0}, against the app -


Correction (2026-10-29 00:45): the scoring note above is wrong and is withdrawn. Its "app reader in fp32" check ran
after ship_w.py had already written W into the app's sign_reader.bin, so it compared W with itself. The real app reader
in fp32 (CPU and GPU agree) matches the bf16 numbers: on the ISLRTC val signer c1 (n=58), app 0.397 and W 0.431 (W alone
right on 2 clips, the app alone on 0, McNemar p=0.5); on the Kaggle val signer (n=298), app 0.940 and W 0.970 (10 against
1, p=0.012). ECE: c1 app 0.089 (T 0.8575), W 0.110 (T 1); Kaggle app 0.313, W 0.053. Scoring now runs in fp32
without TF32 anyway.
| B0 | include + kaggle, 5172 clips, 0 s | shipped design retrained on the shipped data, new splits | new signers 0.078 [0.000, 0.113] (n=256); all 0.552 [0.047, 0.956] / 0.587 | 0.228 | c1 0.080 | islrtc 0.080, kaggle 0.960, rkmvu 0.056 | 3.469, 0.107 | 492.7, 0.899 | 1.000 | 0.970, +0.418 FLAG | 0.857 vs 0.852, p=0.864 | 0.269 | - | baseline |

B0 top confused pairs (true -> predicted): switch -> drink (5); crocodile -> temple (2); crocodile -> close (2); appointment -> location (2); area -> jump (2); blood pressure -> box (2); capital -> lamp (2); sandwich -> clean (2); brinjal -> onion (1); elephant -> deer (1)


B0 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.339 [0.237, 1.000] (n=59), per source {'islrtc': 0.3276, 'kaggle': 0.9597, 'rkmvu': 1.0}, against the app 0.857 vs 0.852, p=0.864

| B1 | include + islrtc + kaggle + rkmvu, 6311 clips, 0 s | shipped conv, + ISLRTC + RKMVU (McNemar p=0.0708 vs B0) | new signers 0.066 [0.032, 0.194] (n=256); all 0.532 [0.049, 0.932] / 0.579 | 0.233 | c1 0.063 | islrtc 0.063, kaggle 0.933, rkmvu 0.111 | 3.560, 0.178 | 420.9, 0.903 | 1.000 | 0.882, +0.350 FLAG | 0.812 vs 0.852, p=0.0336 | 0.481 | - | no |

B1 top confused pairs (true -> predicted): switch -> carrot (5); come -> cook (3); uncle -> man (3); crocodile -> temple (2); drink -> uncle (2); lemon -> fedup (2); area -> island (2); brain -> shaheed bhagat singh (2); brinjal -> onion (1); drink -> carrot (1)


B1 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.203 [0.119, 1.000] (n=59), per source {'islrtc': 0.1897, 'kaggle': 0.9329, 'rkmvu': 1.0}, against the app 0.812 vs 0.852, p=0.0336

| B4 | include + islrtc + kaggle + rkmvu, 6311 clips, 0 s | 1st-place Kaggle conv-transformer (McNemar p=2.74e-05 vs B1) | new signers 0.090 [0.051, 0.194] (n=256); all 0.572 [0.069, 0.984] / 0.610 | 0.273 | c1 0.088 | islrtc 0.088, kaggle 0.987, rkmvu 0.111 | 3.145, 0.089 | 491.5, 0.906 | 1.000 | 0.979, +0.406 FLAG | 0.868 vs 0.852, p=0.405 | 0.474 | - | unclear |

B4 top confused pairs (true -> predicted): bad -> onam (2); capital -> idea (2); crocodile -> temple (1); switch -> drink (1); turtle -> busy (1); uncle -> man (1); addicted dependent hooked -> red (1); address -> approval (1); africa -> board (panel) (1); agriculture -> advance (1)


B4 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.271 [0.169, 1.000] (n=59), per source {'islrtc': 0.2586, 'kaggle': 0.9866, 'rkmvu': 1.0}, against the app 0.868 vs 0.852, p=0.405

| W | include + islrtc + kaggle + rkmvu, 6311 clips, 0 s | app reader fine-tuned: its weights and 314 head rows, 868-way head, 0.1x LR, frozen folded batch norm, 32 frames (McNemar p=0.832 vs APP) | new signers 0.086 [0.000, 0.125] (n=256); all 0.545 [0.056, 0.937] / 0.570 | 0.254 | c1 0.088 | islrtc 0.088, kaggle 0.940, rkmvu 0.056 | 3.742, 0.136 | 500.9, 0.906 | 1.000 | 0.839, +0.294 FLAG | 0.882 vs 0.852, p=0.00342 | 0.302 | - | no |

W top confused pairs (true -> predicted): crocodile -> air matress (4); elephant -> colour (4); switch -> format (3); appointment -> air matress (2); flood -> format (2); noun -> air matress (2); refund -> air matress (2); reservation -> air matress (2); sandwich -> format (2); sperm -> format (2)


W as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.441 [0.336, 1.000] (n=59), per source {'islrtc': 0.431, 'kaggle': 0.9698, 'rkmvu': 1.0}, against the app 0.882 vs 0.852, p=0.00342

| 00:11 | 100 | 15, 0.9 | 17.5 | 17 | curl, embed, landmark_split | under target |

### E. Embedding

| trunk | words | val clips | retrieval top-1 [95%] / top-5 | outside classifier: n, top-1 |
|---|---|---|---|---|
| W_s1 | 867 | 554 | 0.570 [0.056, 0.950] / 0.637 | 161, 0.081 |
| W7 | include + islrtc + kaggle + rkmvu, 6311 clips, 69 s | W + signer-adversarial head (gradient reversal, weight 0.1) (McNemar p=0.0156 vs W) | new signers 0.086 [0.000, 0.125] (n=256); all 0.558 [0.056, 0.961] / 0.578 | 0.255 | c1 0.088 | islrtc 0.088, kaggle 0.963, rkmvu 0.056 | 3.432, 0.107 | 476.3, 0.897 | 1.000 | 0.840, +0.282 FLAG | 0.882 vs 0.852, p=0.00342 | 0.336 | - | no |

W7 top confused pairs (true -> predicted): crocodile -> air matress (4); elephant -> colour (4); appointment -> air matress (2); flood -> format (2); mall -> format (2); refund -> air matress (2); reservation -> air matress (2); sandwich -> format (2); tripura -> format (2); brinjal -> onion (1)


W7 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.441 [0.336, 1.000] (n=59), per source {'islrtc': 0.431, 'kaggle': 0.9698, 'rkmvu': 1.0}, against the app 0.882 vs 0.852, p=0.00342


### Safety model shipped to the app (2026-10-29 00:18): row W

- What: the app reader plus a light, early-stopped fine-tune on cache_v4b (ISLRTC and RKMVU added), 314-word head.
- Against the app reader on validation, restricted to its words, fp32: new ISLRTC signer c1 0.431 vs 0.397 (n=58,
  2 clips to 0, p=0.5, not significant); Kaggle validation signer 0.970 vs 0.940 (n=298, 10 to 1, p=0.012). Small gain on
  the studio signer; no significant change on the new ISLRTC signer.
- Calibration, two-fold by word over pooled new-signer validation (n=59): T stays 1 (fold fits 1.34 and 1.21 did not lower
  ECE on their other folds); RAPS coverage per fold 0.59 / 0.56 at the 50% level and 0.81 / 0.88 at the 80% level.
- Float16 file against the float32 trunk: max logit difference 0.0059, top-1 agreement 1.00; Kotlin SignReaderTest passes.
- Kaggle held-out floor 0.973: 0.970, within its interval. INCLUDE test floor 0.979 not verified: that split was in W's
  training.
- APK: app/build/outputs/apk/standard/debug/app-standard-debug.apk.
| 00:21 | 100 | 0, 0.9 | 20.2 | 18 | curl, embed, landmark_split | under target |
| W_s1 | 867 | 554 | 0.569 [0.056, 0.946] / 0.641 | 161, 0.081 |

Fusion (W_s1 restricted to app words + E_W_s1 prototypes for other words, thresholds two-fold by word on val): all scored val n=554 fused 0.565, classifier alone 0.569, prototypes alone 0.569; one-clip words n=161 fused 0.019 vs classifier 0.000; per source {'islrtc': 0.109, 'kaggle': 0.96, 'rkmvu': 0.056}; thresholds [(0.292, 0.2), (0.675, 0.6)]
| B0F | include + kaggle, 5172 clips, 114 s | shipped recipe from scratch: hidden 320, 32 frames, strong hand augmentation, hand drop up to 0.3 (McNemar p=0.5 vs APP) | new signers 0.090 [0.000, 0.129] (n=256); all 0.558 [0.056, 0.955] / 0.596 | 0.243 | c1 0.092 | islrtc 0.092, kaggle 0.960, rkmvu 0.056 | 3.326, 0.078 | 483.8, 0.903 | 1.000 | 0.987, +0.429 FLAG | 0.866 vs 0.852, p=0.5 | 0.261 | - | no |

B0F top confused pairs (true -> predicted): exam -> interview (4); lemon -> man (2); switch -> drink (2); appointment -> location (2); land -> dog (2); lemon -> key (1); switch -> uncle (1); turtle -> busy (1); uncle -> man (1); addicted dependent hooked -> door (1)


B0F as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.390 [0.284, 1.000] (n=59), per source {'islrtc': 0.3793, 'kaggle': 0.9597, 'rkmvu': 1.0}, against the app 0.866 vs 0.852, p=0.5


Fusion (W_s1 restricted to app words + E_W_s1 prototypes for other words, thresholds two-fold by word on val): all scored val n=554 fused 0.565, classifier alone 0.569, prototypes alone 0.569; one-clip words n=161 fused 0.019 vs classifier 0.000; per source {'islrtc': 0.109, 'kaggle': 0.96, 'rkmvu': 0.056}; thresholds [(0.292, 0.2), (0.675, 0.6)]
| W8 | include + islrtc + kaggle + rkmvu, 6311 clips, 128 s | W + handshape, location, movement auxiliary heads (McNemar p=0.0127 vs W) | new signers 0.109 [0.000, 0.145] (n=256); all 0.565 [0.056, 0.954] / 0.599 | 0.263 | c1 0.113 | islrtc 0.113, kaggle 0.956, rkmvu 0.056 | 3.348, 0.086 | 414.6, 0.904 | 1.000 | 0.910, +0.345 FLAG | 0.874 vs 0.852, p=0.115 | 0.396 | - | unclear |

W8 top confused pairs (true -> predicted): elephant -> colour (4); crocodile -> city (3); appointment -> air matress (2); land -> bike suspension (2); mall -> format (2); brinjal -> onion (1); crocodile -> inform apprise notify (1); key -> man (1); switch -> man (1); switch -> drink (1)


W8 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.441 [0.336, 1.000] (n=59), per source {'islrtc': 0.431, 'kaggle': 0.9597, 'rkmvu': 1.0}, against the app 0.874 vs 0.852, p=0.115


Fusion (W_s1 restricted to app words + E_W_s1 prototypes for other words, thresholds two-fold by word on val): all scored val n=554 fused 0.565, classifier alone 0.569, prototypes alone 0.569; one-clip words n=161 fused 0.019 vs classifier 0.000; per source {'islrtc': 0.109, 'kaggle': 0.96, 'rkmvu': 0.056}; thresholds [(0.292, 0.2), (0.675, 0.6)]

Rule 1 (pre-registered 2026-10-29 ~01:00): new-signer val clips scored as the app sees them (restricted to app words
when the word is one, 868-way otherwise), paired McNemar against W; Kaggle C must stay within W's interval.
- W8 vs W: 0.109 vs 0.102 (n=256; 4 clips to 2, p=0.69); Kaggle C 0.960 vs 0.970 [0.946, 0.987]. Not accepted.
- B0F vs W: 0.090 vs 0.102 (4 to 7, p=0.55). Not accepted.
- Fusion (W + stage E prototypes) is held for the next run: all val 0.569 to 0.576, one-clip words 0.000 to 0.081, but
  app-word clips 315 to 305 (n=357), and the app needs a second network and text for ~550 non-app words.

Validation cannot resolve gains below about 8 points; more held-out signers are needed (FDMSE-ISL request, A1
collection).
| B1F | include + islrtc + kaggle + rkmvu, 6311 clips, 141 s | B0F recipe + ISLRTC + RKMVU (McNemar p=1 vs B0F) | new signers 0.082 [0.047, 0.194] (n=256); all 0.560 [0.065, 0.968] / 0.616 | 0.260 | c1 0.080 | islrtc 0.080, kaggle 0.970, rkmvu 0.111 | 3.240, 0.110 | 481.3, 0.910 | 1.000 | 0.947, +0.387 FLAG | 0.857 vs 0.852, p=0.86 | 0.511 | - | no |

B1F top confused pairs (true -> predicted): switch -> drink (3); lemon -> key (2); area -> location (2); capital -> impact (2); land -> australia (2); crocodile -> temple (1); switch -> uncle (1); switch -> carrot (1); uncle -> man (1); addicted dependent hooked -> red (1)


B1F as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.288 [0.198, 1.000] (n=59), per source {'islrtc': 0.2759, 'kaggle': 0.9698, 'rkmvu': 1.0}, against the app 0.857 vs 0.852, p=0.86

| W7 | include + islrtc + kaggle + rkmvu, 6311 clips, 87 s | W + signer-adversarial head (gradient reversal, weight 0.1) (McNemar p=0.00131 vs W) | new signers 0.113 [0.000, 0.148] (n=256); all 0.570 [0.056, 0.962] / 0.599 | 0.264 | c1 0.118 | islrtc 0.118, kaggle 0.963, rkmvu 0.056 | 3.402, 0.118 | 422.5, 0.904 | 1.000 | 0.906, +0.336 FLAG | 0.880 vs 0.852, p=0.0309 | 0.399 | - | unclear |

W7 top confused pairs (true -> predicted): elephant -> colour (4); crocodile -> city (3); appointment -> air matress (2); land -> bike suspension (2); mall -> format (2); brinjal -> onion (1); crocodile -> inform apprise notify (1); key -> man (1); switch -> uncle (1); addicted dependent hooked -> red (1)


W7 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.441 [0.336, 1.000] (n=59), per source {'islrtc': 0.431, 'kaggle': 0.9664, 'rkmvu': 1.0}, against the app 0.880 vs 0.852, p=0.0309

| 00:31 | 100 | 6, 0.7 | 16.3 | 18 | curl, landmark_split | under target |
| 00:41 | 100 | 88, 5.3 | 20.6 | 18 | curl, exp B12, exp B3, landmark_split |  |
| B12 | include + islrtc + kaggle + rkmvu, 6311 clips, 782 s | B4 from masked pose pretraining (train-side signers only) (McNemar p=0.481 vs B4) | new signers 0.082 [0.046, 0.194] (n=256); all 0.565 [0.063, 0.978] / 0.614 | 0.265 | c1 0.080 | islrtc 0.080, kaggle 0.980, rkmvu 0.111 | 3.163, 0.121 | 497.5, 0.906 | 1.000 | 0.974, +0.409 FLAG | 0.868 vs 0.852, p=0.392 | 0.537 | - | no |

B12 top confused pairs (true -> predicted): turtle -> busy (2); capital -> impact (2); exam -> writer (1); lemon -> key (1); switch -> drink (1); uncle -> man (1); addicted dependent hooked -> summer (1); address -> friend (1); africa -> board (panel) (1); agriculture -> divorcee (1)


B12 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.305 [0.203, 1.000] (n=59), per source {'islrtc': 0.2931, 'kaggle': 0.9799, 'rkmvu': 1.0}, against the app 0.868 vs 0.852, p=0.392

| B3 | include + islrtc + kaggle + rkmvu, 6311 clips, 1003 s | graph attention over hand, arm, face (HWGAT style) (McNemar p=5.73e-08 vs B1) | new signers 0.059 [0.000, 0.111] (n=256); all 0.466 [0.036, 0.815] / 0.552 | 0.204 | c1 0.059 | islrtc 0.059, kaggle 0.815, rkmvu 0.056 | 3.830, 0.165 | 495.5, 0.906 | 1.000 | 0.730, +0.264 FLAG | 0.711 vs 0.852, p=7.64e-11 | 0.235 | - | no |

B3 top confused pairs (true -> predicted): come -> thank you (4); crocodile -> temple (4); drink -> fever (4); maybe -> maths (4); tea -> radish (4); busy -> turtle (3); uncle -> fever (3); chilli -> man (2); cry -> thank you (2); exam -> break (2)


B3 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.186 [0.000, 0.259] (n=59), per source {'islrtc': 0.1897, 'kaggle': 0.8154, 'rkmvu': 0.0}, against the app 0.711 vs 0.852, p=7.64e-11

| 00:51 | 100 | 45, 2.0 | 20.8 | 17 | exp WR, landmark_split | GPU under 70% during a B row: landmark workers to 17 |
| WR | include + islrtc + kaggle + rkmvu, 6311 clips, 104 s | rehearsal of the final on cache_v4b (W recipe, 3 seeds) (McNemar p=0.481 vs APP) | new signers 0.090 [0.000, 0.129] (n=256); all 0.558 [0.056, 0.958] / 0.574 | 0.256 | c1 0.092 | islrtc 0.092, kaggle 0.960, rkmvu 0.056 | 3.650, 0.151 | 499.6, 0.894 | 1.000 | 0.830, +0.272 FLAG | 0.880 vs 0.852, p=0.0129 | 0.310 | - | no |

WR top confused pairs (true -> predicted): elephant -> colour (4); crocodile -> brand (2); brinjal -> onion (1); crocodile -> wholesale (1); crocodile -> biased (1); crocodile -> out for a duck (1); giraffe -> gas stove (1); switch -> mode (1); addicted dependent hooked -> red (1); address -> back date (1)


WR as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.424 [0.322, 1.000] (n=59), per source {'islrtc': 0.4138, 'kaggle': 0.9698, 'rkmvu': 1.0}, against the app 0.880 vs 0.852, p=0.0129

| WR | include + islrtc + kaggle + rkmvu, 6311 clips, 108 s | rehearsal of the final on cache_v4b (W recipe, 3 seeds) (McNemar p=0.648 vs APP) | new signers 0.086 [0.000, 0.125] (n=256); all 0.554 [0.056, 0.954] / 0.572 | 0.253 | c1 0.088 | islrtc 0.088, kaggle 0.956, rkmvu 0.056 | 3.645, 0.103 | 508.9, 0.906 | 1.000 | 0.831, +0.277 FLAG | 0.885 vs 0.852, p=0.000488 | 0.306 | - | no |

WR top confused pairs (true -> predicted): crocodile -> donkey (4); elephant -> colour (3); august -> share capital (2); brinjal -> taxable (1); brinjal -> onion (1); crocodile -> pasteurization (1); elephant -> area (1); giraffe -> area (1); switch -> uncle (1); addicted dependent hooked -> red (1)


WR as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.441 [0.336, 1.000] (n=59), per source {'islrtc': 0.431, 'kaggle': 0.9732, 'rkmvu': 1.0}, against the app 0.885 vs 0.852, p=0.000488

| WR | include + islrtc + kaggle + rkmvu, 6311 clips, 60 s | rehearsal of the final on cache_v4b (W recipe, 3 seeds) (McNemar p=0.629 vs APP) | new signers 0.086 [0.000, 0.125] (n=256); all 0.554 [0.056, 0.954] / 0.572 | 0.255 | c1 0.088 | islrtc 0.088, kaggle 0.956, rkmvu 0.056 | 3.659, 0.153 | 485.3, 0.901 | 1.000 | 0.830, +0.275 FLAG | 0.880 vs 0.852, p=0.00635 | 0.317 | - | no |

WR top confused pairs (true -> predicted): elephant -> colour (4); crocodile -> format (2); crocodile -> assassination (2); appointment -> holiday (2); area -> force (2); brinjal -> onion (1); crocodile -> island (1); drink -> man (1); giraffe -> accommodation endorsement (1); switch -> photosynthesis (1)


WR as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.441 [0.336, 1.000] (n=59), per source {'islrtc': 0.431, 'kaggle': 0.9664, 'rkmvu': 1.0}, against the app 0.880 vs 0.852, p=0.00635


I1 TTA (5 views: scale 0.95/1.05, rotation -/+0.05 rad): app-view new-signer 0.102 vs W_s1 0.102 (0 to 0, p=1), Kaggle C 0.970 (champion 0.970 [0.946, 0.987]); no

I2 ensemble W_s1 + B0F_s1: app-view new-signer 0.094 vs W_s1 0.102 (3 to 5, p=0.727), Kaggle C 0.973 (champion 0.970 [0.946, 0.987]); no

I2 ensemble W_s1 + B4_s1: app-view new-signer 0.113 vs W_s1 0.102 (11 to 8, p=0.648), Kaggle C 0.983 (champion 0.970 [0.946, 0.987]); no

I3 fusion v2 (prototype when W's app-word top probability < 0.5; app-word clips lost 5): app-view new-signer 0.129 vs W_s1 0.102 (9 to 2, p=0.0654), Kaggle C 0.960 (champion 0.970 [0.946, 0.987]); no
| OPT3 | include + islrtc + kaggle + rkmvu, 6311 clips, 78 s | W search trial 3: LR 0.040x, augmentation level 2, min epochs 15 (McNemar p=0.0391 vs W) | new signers 0.090 [0.000, 0.129] (n=256); all 0.558 [0.056, 0.958] / 0.578 | 0.254 | c1 0.092 | islrtc 0.092, kaggle 0.960, rkmvu 0.056 | 3.451, 0.090 | 479.6, 0.903 | 1.000 | 0.851, +0.293 FLAG | 0.880 vs 0.852, p=0.0129 | 0.347 | - | unclear |

OPT3 top confused pairs (true -> predicted): elephant -> colour (4); crocodile -> format (3); crocodile -> air matress (2); appointment -> air matress (2); flood -> format (2); mall -> format (2); reservation -> air matress (2); sperm -> format (2); tripura -> format (2); brinjal -> onion (1)


OPT3 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.441 [0.339, 1.000] (n=59), per source {'islrtc': 0.431, 'kaggle': 0.9664, 'rkmvu': 1.0}, against the app 0.880 vs 0.852, p=0.0129

| OPT2 | include + islrtc + kaggle + rkmvu, 6311 clips, 78 s | W search trial 2: LR 0.099x, augmentation level 1, min epochs 15 (McNemar p=0.00131 vs W) | new signers 0.113 [0.000, 0.148] (n=256); all 0.570 [0.056, 0.962] / 0.599 | 0.265 | c1 0.118 | islrtc 0.118, kaggle 0.963, rkmvu 0.056 | 3.377, 0.108 | 424.2, 0.904 | 1.000 | 0.907, +0.337 FLAG | 0.880 vs 0.852, p=0.0309 | 0.392 | - | unclear |

OPT2 top confused pairs (true -> predicted): elephant -> colour (4); crocodile -> city (3); appointment -> air matress (2); land -> bike suspension (2); mall -> format (2); brinjal -> onion (1); crocodile -> inform apprise notify (1); key -> man (1); switch -> uncle (1); addicted dependent hooked -> red (1)


OPT2 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.441 [0.336, 1.000] (n=59), per source {'islrtc': 0.431, 'kaggle': 0.9664, 'rkmvu': 1.0}, against the app 0.880 vs 0.852, p=0.0309

| OPT4 | include + islrtc + kaggle + rkmvu, 6311 clips, 99 s | W search trial 4: LR 0.074x, augmentation level 0, min epochs 15 (McNemar p=0.0072 vs W) | new signers 0.105 [0.000, 0.141] (n=256); all 0.569 [0.056, 0.963] / 0.601 | 0.265 | c1 0.109 | islrtc 0.109, kaggle 0.966, rkmvu 0.056 | 3.366, 0.100 | 416.6, 0.903 | 1.000 | 0.926, +0.357 FLAG | 0.880 vs 0.852, p=0.0309 | 0.414 | - | unclear |

OPT4 top confused pairs (true -> predicted): elephant -> colour (5); crocodile -> city (3); area -> zone (2); land -> bike suspension (2); mall -> format (2); sandwich -> zone (2); key -> man (1); switch -> uncle (1); addicted dependent hooked -> red (1); address -> share capital (1)


OPT4 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.441 [0.336, 1.000] (n=59), per source {'islrtc': 0.431, 'kaggle': 0.9664, 'rkmvu': 1.0}, against the app 0.880 vs 0.852, p=0.0309

| OPT1 | include + islrtc + kaggle + rkmvu, 6311 clips, 100 s | W search trial 1: LR 0.034x, augmentation level 1, min epochs 25 (McNemar p=0.00391 vs W) | new signers 0.094 [0.000, 0.133] (n=256); all 0.561 [0.056, 0.961] / 0.583 | 0.258 | c1 0.097 | islrtc 0.097, kaggle 0.963, rkmvu 0.056 | 3.458, 0.086 | 455.9, 0.899 | 1.000 | 0.864, +0.303 FLAG | 0.882 vs 0.852, p=0.00739 | 0.358 | - | unclear |

OPT1 top confused pairs (true -> predicted): crocodile -> air matress (4); elephant -> colour (4); appointment -> air matress (2); area -> air matress (2); bad -> child (2); mall -> format (2); refund -> air matress (2); rehabilitation -> air matress (2); reservation -> air matress (2); sperm -> format (2)


OPT1 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.441 [0.339, 1.000] (n=59), per source {'islrtc': 0.431, 'kaggle': 0.9698, 'rkmvu': 1.0}, against the app 0.882 vs 0.852, p=0.00739

| OPT5 | include + islrtc + kaggle + rkmvu, 6311 clips, 59 s | W search trial 5: LR 0.032x, augmentation level 0, min epochs 15 (McNemar p=0.00781 vs W) | new signers 0.090 [0.000, 0.129] (n=256); all 0.560 [0.056, 0.961] / 0.576 | 0.255 | c1 0.092 | islrtc 0.092, kaggle 0.963, rkmvu 0.056 | 3.443, 0.096 | 488.3, 0.899 | 1.000 | 0.845, +0.285 FLAG | 0.885 vs 0.852, p=0.00183 | 0.347 | - | unclear |

OPT5 top confused pairs (true -> predicted): elephant -> colour (4); crocodile -> format (3); crocodile -> air matress (2); appointment -> air matress (2); flood -> format (2); mall -> format (2); refund -> air matress (2); reservation -> air matress (2); reward -> format (2); sperm -> format (2)


OPT5 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.458 [0.345, 1.000] (n=59), per source {'islrtc': 0.4483, 'kaggle': 0.9698, 'rkmvu': 1.0}, against the app 0.885 vs 0.852, p=0.00183

| OPT6 | include + islrtc + kaggle + rkmvu, 6311 clips, 80 s | W search trial 6: LR 0.184x, augmentation level 0, min epochs 25 (McNemar p=0.000488 vs W) | new signers 0.105 [0.000, 0.141] (n=256); all 0.576 [0.056, 0.979] / 0.632 | 0.269 | c1 0.109 | islrtc 0.109, kaggle 0.980, rkmvu 0.056 | 3.257, 0.131 | 387.7, 0.910 | 1.000 | 0.983, +0.407 FLAG | 0.885 vs 0.852, p=0.0169 | 0.500 | - | unclear |

OPT6 top confused pairs (true -> predicted): crocodile -> city (3); area -> zone (2); blood pressure -> biology (2); clean -> cauliflower (1); elephant -> colour (1); switch -> drink (1); addicted dependent hooked -> red (1); address -> share capital (1); africa -> evening (1); agriculture -> zone (1)


OPT6 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.407 [0.302, 1.000] (n=59), per source {'islrtc': 0.3966, 'kaggle': 0.9799, 'rkmvu': 1.0}, against the app 0.885 vs 0.852, p=0.0169

| 01:01 | 100 | 0, 0.7 | 17.9 | 8 | landmark_split | under target |
| OPT1 | include + islrtc + kaggle + rkmvu, 6311 clips, 15 s | W search trial 1: LR 0.034x, augmentation level 1, min epochs 25 (McNemar p=0.00391 vs W) | new signers 0.094 [0.000, 0.133] (n=256); all 0.561 [0.056, 0.961] / 0.583 | 0.258 | c1 0.097 | islrtc 0.097, kaggle 0.963, rkmvu 0.056 | 3.458, 0.086 | 455.9, 0.899 | 1.000 | 0.864, +0.303 FLAG | 0.882 vs 0.852, p=0.00739 | 0.358 | - | unclear |

OPT2 top confused pairs (true -> predicted): elephant -> colour (4); crocodile -> city (3); appointment -> air matress (2); land -> bike suspension (2); mall -> format (2); brinjal -> onion (1); crocodile -> inform apprise notify (1); key -> man (1); switch -> uncle (1); addicted dependent hooked -> red (1)


OPT1 top confused pairs (true -> predicted): crocodile -> air matress (4); elephant -> colour (4); appointment -> air matress (2); area -> air matress (2); bad -> child (2); mall -> format (2); refund -> air matress (2); rehabilitation -> air matress (2); reservation -> air matress (2); sperm -> format (2)


OPT2 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.441 [0.336, 1.000] (n=59), per source {'islrtc': 0.431, 'kaggle': 0.9664, 'rkmvu': 1.0}, against the app 0.880 vs 0.852, p=0.0309


| 01:33 | 67 | 7, 0.7 | 11.0 | 8 |  | under target |
| OPT2 | include + islrtc + kaggle + rkmvu, 6311 clips, 12 s | W search trial 2: LR 0.099x, augmentation level 1, min epochs 15 (McNemar p=0.00131 vs W) | new signers 0.113 [0.000, 0.148] (n=256); all 0.570 [0.056, 0.962] / 0.599 | 0.265 | c1 0.118 | islrtc 0.118, kaggle 0.963, rkmvu 0.056 | 3.377, 0.108 | 424.2, 0.904 | 1.000 | 0.907, +0.337 FLAG | 0.880 vs 0.852, p=0.0309 | 0.392 | - | unclear |

OPT2 top confused pairs (true -> predicted): elephant -> colour (4); crocodile -> city (3); appointment -> air matress (2); land -> bike suspension (2); mall -> format (2); brinjal -> onion (1); crocodile -> inform apprise notify (1); key -> man (1); switch -> uncle (1); addicted dependent hooked -> red (1)


OPT2 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.441 [0.336, 1.000] (n=59), per source {'islrtc': 0.431, 'kaggle': 0.9664, 'rkmvu': 1.0}, against the app 0.880 vs 0.852, p=0.0309

| OPT1 | include + islrtc + kaggle + rkmvu, 6311 clips, 12 s | W search trial 1: LR 0.034x, augmentation level 1, min epochs 25 (McNemar p=0.00391 vs W) | new signers 0.094 [0.000, 0.133] (n=256); all 0.561 [0.056, 0.961] / 0.583 | 0.258 | c1 0.097 | islrtc 0.097, kaggle 0.963, rkmvu 0.056 | 3.458, 0.086 | 455.9, 0.899 | 1.000 | 0.864, +0.303 FLAG | 0.882 vs 0.852, p=0.00739 | 0.358 | - | unclear |

OPT1 top confused pairs (true -> predicted): crocodile -> air matress (4); elephant -> colour (4); appointment -> air matress (2); area -> air matress (2); bad -> child (2); mall -> format (2); refund -> air matress (2); rehabilitation -> air matress (2); reservation -> air matress (2); sperm -> format (2)


OPT1 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.441 [0.339, 1.000] (n=59), per source {'islrtc': 0.431, 'kaggle': 0.9698, 'rkmvu': 1.0}, against the app 0.882 vs 0.852, p=0.00739

| OPT3 | include + islrtc + kaggle + rkmvu, 6311 clips, 54 s | W search trial 3: LR 0.040x, augmentation level 2, min epochs 15 (McNemar p=0.0391 vs W) | new signers 0.090 [0.000, 0.129] (n=256); all 0.558 [0.056, 0.958] / 0.578 | 0.254 | c1 0.092 | islrtc 0.092, kaggle 0.960, rkmvu 0.056 | 3.451, 0.090 | 479.6, 0.903 | 1.000 | 0.851, +0.293 FLAG | 0.880 vs 0.852, p=0.0129 | 0.347 | - | unclear |

OPT3 top confused pairs (true -> predicted): elephant -> colour (4); crocodile -> format (3); crocodile -> air matress (2); appointment -> air matress (2); flood -> format (2); mall -> format (2); reservation -> air matress (2); sperm -> format (2); tripura -> format (2); brinjal -> onion (1)


OPT3 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.441 [0.339, 1.000] (n=59), per source {'islrtc': 0.431, 'kaggle': 0.9664, 'rkmvu': 1.0}, against the app 0.880 vs 0.852, p=0.0129

| OPT4 | include + islrtc + kaggle + rkmvu, 6311 clips, 69 s | W search trial 4: LR 0.074x, augmentation level 0, min epochs 15 (McNemar p=0.0072 vs W) | new signers 0.105 [0.000, 0.141] (n=256); all 0.569 [0.056, 0.963] / 0.601 | 0.265 | c1 0.109 | islrtc 0.109, kaggle 0.966, rkmvu 0.056 | 3.366, 0.100 | 416.6, 0.903 | 1.000 | 0.926, +0.357 FLAG | 0.880 vs 0.852, p=0.0309 | 0.414 | - | unclear |

OPT4 top confused pairs (true -> predicted): elephant -> colour (5); crocodile -> city (3); area -> zone (2); land -> bike suspension (2); mall -> format (2); sandwich -> zone (2); key -> man (1); switch -> uncle (1); addicted dependent hooked -> red (1); address -> share capital (1)


OPT4 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.441 [0.336, 1.000] (n=59), per source {'islrtc': 0.431, 'kaggle': 0.9664, 'rkmvu': 1.0}, against the app 0.880 vs 0.852, p=0.0309

| OPT5 | include + islrtc + kaggle + rkmvu, 6311 clips, 53 s | W search trial 5: LR 0.032x, augmentation level 0, min epochs 15 (McNemar p=0.00781 vs W) | new signers 0.090 [0.000, 0.129] (n=256); all 0.560 [0.056, 0.961] / 0.576 | 0.255 | c1 0.092 | islrtc 0.092, kaggle 0.963, rkmvu 0.056 | 3.443, 0.096 | 488.3, 0.899 | 1.000 | 0.845, +0.285 FLAG | 0.885 vs 0.852, p=0.00183 | 0.347 | - | unclear |

OPT5 top confused pairs (true -> predicted): elephant -> colour (4); crocodile -> format (3); crocodile -> air matress (2); appointment -> air matress (2); flood -> format (2); mall -> format (2); refund -> air matress (2); reservation -> air matress (2); reward -> format (2); sperm -> format (2)


OPT5 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.458 [0.345, 1.000] (n=59), per source {'islrtc': 0.4483, 'kaggle': 0.9698, 'rkmvu': 1.0}, against the app 0.885 vs 0.852, p=0.00183

| OPT6 | include + islrtc + kaggle + rkmvu, 6311 clips, 75 s | W search trial 6: LR 0.184x, augmentation level 0, min epochs 25 (McNemar p=0.000488 vs W) | new signers 0.105 [0.000, 0.141] (n=256); all 0.576 [0.056, 0.979] / 0.632 | 0.269 | c1 0.109 | islrtc 0.109, kaggle 0.980, rkmvu 0.056 | 3.257, 0.131 | 387.7, 0.910 | 1.000 | 0.983, +0.407 FLAG | 0.885 vs 0.852, p=0.0169 | 0.500 | - | unclear |

OPT6 top confused pairs (true -> predicted): crocodile -> city (3); area -> zone (2); blood pressure -> biology (2); clean -> cauliflower (1); elephant -> colour (1); switch -> drink (1); addicted dependent hooked -> red (1); address -> share capital (1); africa -> evening (1); agriculture -> zone (1)


OPT6 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.407 [0.302, 1.000] (n=59), per source {'islrtc': 0.3966, 'kaggle': 0.9799, 'rkmvu': 1.0}, against the app 0.885 vs 0.852, p=0.0169

| OPT1 | include + islrtc + kaggle + rkmvu, 6311 clips, 8 s | W search trial 1: LR 0.034x, augmentation level 1, min epochs 25 (McNemar p=0.00391 vs W) | new signers 0.094 [0.000, 0.133] (n=256); all 0.561 [0.056, 0.961] / 0.583 | 0.258 | c1 0.097 | islrtc 0.097, kaggle 0.963, rkmvu 0.056 | 3.458, 0.086 | 455.9, 0.899 | 1.000 | 0.864, +0.303 FLAG | 0.882 vs 0.852, p=0.00739 | 0.358 | - | unclear |

OPT1 top confused pairs (true -> predicted): crocodile -> air matress (4); elephant -> colour (4); appointment -> air matress (2); area -> air matress (2); bad -> child (2); mall -> format (2); refund -> air matress (2); rehabilitation -> air matress (2); reservation -> air matress (2); sperm -> format (2)


OPT1 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.441 [0.339, 1.000] (n=59), per source {'islrtc': 0.431, 'kaggle': 0.9698, 'rkmvu': 1.0}, against the app 0.882 vs 0.852, p=0.00739

| OPT2 | include + islrtc + kaggle + rkmvu, 6311 clips, 8 s | W search trial 2: LR 0.099x, augmentation level 1, min epochs 15 (McNemar p=0.00131 vs W) | new signers 0.113 [0.000, 0.148] (n=256); all 0.570 [0.056, 0.962] / 0.599 | 0.265 | c1 0.118 | islrtc 0.118, kaggle 0.963, rkmvu 0.056 | 3.377, 0.108 | 424.2, 0.904 | 1.000 | 0.907, +0.337 FLAG | 0.880 vs 0.852, p=0.0309 | 0.392 | - | unclear |

OPT2 top confused pairs (true -> predicted): elephant -> colour (4); crocodile -> city (3); appointment -> air matress (2); land -> bike suspension (2); mall -> format (2); brinjal -> onion (1); crocodile -> inform apprise notify (1); key -> man (1); switch -> uncle (1); addicted dependent hooked -> red (1)


OPT2 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.441 [0.336, 1.000] (n=59), per source {'islrtc': 0.431, 'kaggle': 0.9664, 'rkmvu': 1.0}, against the app 0.880 vs 0.852, p=0.0309

| OPT9 | include + islrtc + kaggle + rkmvu, 6311 clips, 70 s | W9: W + look-alike and same-meaning label smoothing (B9 as a fine-tune) (McNemar p=0.00235 vs W) | new signers 0.109 [0.000, 0.145] (n=256); all 0.569 [0.056, 0.961] / 0.599 | 0.261 | c1 0.113 | islrtc 0.113, kaggle 0.963, rkmvu 0.056 | 3.350, 0.093 | 390.1, 0.890 | 1.000 | 0.909, +0.341 FLAG | 0.877 vs 0.852, p=0.0636 | 0.403 | - | unclear |

OPT9 top confused pairs (true -> predicted): elephant -> colour (4); crocodile -> city (3); switch -> drink (2); appointment -> air matress (2); land -> bike suspension (2); mall -> format (2); brinjal -> onion (1); crocodile -> inform apprise notify (1); addicted dependent hooked -> red (1); address -> share capital (1)


OPT9 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.424 [0.322, 1.000] (n=59), per source {'islrtc': 0.4138, 'kaggle': 0.9664, 'rkmvu': 1.0}, against the app 0.877 vs 0.852, p=0.0636

| 01:43 | 93 | 0, 0.7 | 13.4 | 12 | landmark_split | under target |

Val and test grown at final1 (cache_v5, --grow-held): new clips of the frozen held-out signers, never trained on, join
their splits. c1 (val) +505 ISLRTC clips (val ISLRTC 524 to 1,029; scored 247 to 269), c10 (test) +91 (test ISLRTC 290
to 381; scored 163 to 165). Most new clips are words no other training signer has, so they are not scored; the
scored gain is small. INCLUDE's test split (816) is held out of training from cache_v5 on.

APP INCLUDE test floor (0.979): 0.978 [0.968, 0.987] (n=816)
| APP | INCLUDE + Kaggle (app as shipped) | the model in the app as shipped (format 1 weights, 32 frames, 314 words) | new signers 0.441 [0.336, 1.000] (n=59); all 0.882 [0.373, 1.000] / 0.930 | 0.721 | c1 0.431 | islrtc 0.431, kaggle 0.970, rkmvu 1.000 | 0.712, 0.040 | 9.9, 0.944 | nan | - | - | 0.351 | - | baseline |

APP top confused pairs (true -> predicted): elephant -> colour (4); crocodile -> city (3); bad -> child (2); brinjal -> onion (1); switch -> uncle (1); baby -> slow (1); box -> wide (1); bus -> spring (1); come -> new (1); deaf -> cry (1)


APP as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.441 [0.336, 1.000] (n=59), per source {'islrtc': 0.431, 'kaggle': 0.9698, 'rkmvu': 1.0}, against the app -


W INCLUDE test floor (0.979): 0.978 [0.968, 0.987] (n=816)
| W | include + islrtc + kaggle + rkmvu, 8449 clips, 0 s | app reader fine-tuned: its weights and 314 head rows, 868-way head, 0.1x LR, frozen folded batch norm, 32 frames (McNemar p=nan vs APP) | new signers 0.077 [0.000, 0.111] (n=287); all 0.516 [0.052, 0.934] / 0.540 | 0.227 | c1 0.078 | islrtc 0.078, kaggle 0.940, rkmvu 0.056 | 3.742, 0.136 | 500.9, 0.906 | 1.000 | - | - | 0.302 | - | no |

W top confused pairs (true -> predicted): crocodile -> air matress (4); elephant -> colour (4); switch -> format (3); appointment -> air matress (2); flood -> format (2); noun -> air matress (2); refund -> air matress (2); reservation -> air matress (2); sandwich -> format (2); sperm -> format (2)


W as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.441 [0.336, 1.000] (n=59), per source {'islrtc': 0.431, 'kaggle': 0.9698, 'rkmvu': 1.0}, against the app -


OPT1 INCLUDE test floor (0.979): 0.977 [0.966, 0.987] (n=816)
| OPT1 | include + islrtc + kaggle + rkmvu, 8449 clips, 0 s | W search trial 1: LR 0.034x, augmentation level 1, min epochs 25 (McNemar p=nan vs W) | new signers 0.084 [0.000, 0.115] (n=287); all 0.532 [0.056, 0.957] / 0.552 | 0.230 | c1 0.086 | islrtc 0.086, kaggle 0.963, rkmvu 0.056 | 3.458, 0.086 | 455.9, 0.899 | 1.000 | - | - | 0.358 | - | no |

OPT1 top confused pairs (true -> predicted): crocodile -> air matress (4); elephant -> colour (4); appointment -> air matress (2); area -> air matress (2); bad -> child (2); mall -> format (2); refund -> air matress (2); rehabilitation -> air matress (2); reservation -> air matress (2); sperm -> format (2)


OPT1 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.441 [0.339, 1.000] (n=59), per source {'islrtc': 0.431, 'kaggle': 0.9698, 'rkmvu': 1.0}, against the app -


OPT2 INCLUDE test floor (0.979): 0.980 [0.971, 0.989] (n=816)
| OPT2 | include + islrtc + kaggle + rkmvu, 8449 clips, 0 s | W search trial 2: LR 0.099x, augmentation level 1, min epochs 15 (McNemar p=nan vs W) | new signers 0.101 [0.000, 0.132] (n=287); all 0.540 [0.056, 0.957] / 0.568 | 0.236 | c1 0.104 | islrtc 0.104, kaggle 0.963, rkmvu 0.056 | 3.377, 0.108 | 424.2, 0.904 | 1.000 | - | - | 0.392 | - | unclear |

OPT2 top confused pairs (true -> predicted): elephant -> colour (4); crocodile -> city (3); appointment -> air matress (2); land -> bike suspension (2); mall -> format (2); brinjal -> onion (1); crocodile -> inform apprise notify (1); key -> man (1); switch -> uncle (1); addicted dependent hooked -> red (1)


OPT2 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.441 [0.336, 1.000] (n=59), per source {'islrtc': 0.431, 'kaggle': 0.9664, 'rkmvu': 1.0}, against the app -


WF INCLUDE test floor (0.979): 0.978 [0.968, 0.988] (n=816)
| WF | include + islrtc + kaggle + rkmvu, 8449 clips, 164 s | final: W recipe on the T+7 re-split (INCLUDE test held out), 3 seeds (McNemar p=nan vs APP) | new signers 0.118 [0.000, 0.152] (n=287); all 0.556 [0.056, 0.972] / 0.605 | 0.261 | c1 0.123 | islrtc 0.123, kaggle 0.977, rkmvu 0.056 | 3.788, 0.082 | 1643.2, 0.896 | 1.000 | 0.750, +0.195 | - | 0.369 | - | no |

WF top confused pairs (true -> predicted): elephant -> colour (4); switch -> uncle (2); land -> australia (2); mall -> format (2); range -> winter (2); tripura -> asymmetrical design (2); busy -> mahavir jayanti (1); addicted dependent hooked -> red (1); address -> acknowledgement (1); afforestation -> bharat (1)


WF as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.492 [0.373, 1.000] (n=59), per source {'islrtc': 0.4828, 'kaggle': 0.9799, 'rkmvu': 1.0}, against the app -


OPT3 INCLUDE test floor (0.979): 0.975 [0.964, 0.985] (n=816)
| OPT3 | include + islrtc + kaggle + rkmvu, 8449 clips, 0 s | W search trial 3: LR 0.040x, augmentation level 2, min epochs 15 (McNemar p=nan vs W) | new signers 0.080 [0.000, 0.112] (n=287); all 0.528 [0.056, 0.953] / 0.547 | 0.227 | c1 0.082 | islrtc 0.082, kaggle 0.960, rkmvu 0.056 | 3.451, 0.090 | 479.6, 0.903 | 1.000 | - | - | 0.347 | - | no |

OPT3 top confused pairs (true -> predicted): elephant -> colour (4); crocodile -> format (3); crocodile -> air matress (2); appointment -> air matress (2); flood -> format (2); mall -> format (2); reservation -> air matress (2); sperm -> format (2); tripura -> format (2); brinjal -> onion (1)


OPT3 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.441 [0.339, 1.000] (n=59), per source {'islrtc': 0.431, 'kaggle': 0.9664, 'rkmvu': 1.0}, against the app -


OPT4 INCLUDE test floor (0.979): 0.984 [0.975, 0.993] (n=816)
| OPT4 | include + islrtc + kaggle + rkmvu, 8449 clips, 0 s | W search trial 4: LR 0.074x, augmentation level 0, min epochs 15 (McNemar p=nan vs W) | new signers 0.094 [0.000, 0.125] (n=287); all 0.538 [0.056, 0.962] / 0.569 | 0.237 | c1 0.097 | islrtc 0.097, kaggle 0.966, rkmvu 0.056 | 3.366, 0.100 | 416.6, 0.903 | 1.000 | - | - | 0.414 | - | unclear |

OPT4 top confused pairs (true -> predicted): elephant -> colour (5); crocodile -> city (3); area -> zone (2); land -> bike suspension (2); mall -> format (2); sandwich -> zone (2); key -> man (1); switch -> uncle (1); addicted dependent hooked -> red (1); address -> share capital (1)


OPT4 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.441 [0.336, 1.000] (n=59), per source {'islrtc': 0.431, 'kaggle': 0.9664, 'rkmvu': 1.0}, against the app -


OPT5 INCLUDE test floor (0.979): 0.975 [0.964, 0.985] (n=816)
| OPT5 | include + islrtc + kaggle + rkmvu, 8449 clips, 0 s | W search trial 5: LR 0.032x, augmentation level 0, min epochs 15 (McNemar p=nan vs W) | new signers 0.080 [0.000, 0.112] (n=287); all 0.530 [0.056, 0.957] / 0.545 | 0.227 | c1 0.082 | islrtc 0.082, kaggle 0.963, rkmvu 0.056 | 3.443, 0.096 | 488.3, 0.899 | 1.000 | - | - | 0.347 | - | no |

OPT5 top confused pairs (true -> predicted): elephant -> colour (4); crocodile -> format (3); crocodile -> air matress (2); appointment -> air matress (2); flood -> format (2); mall -> format (2); refund -> air matress (2); reservation -> air matress (2); reward -> format (2); sperm -> format (2)


OPT5 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.458 [0.345, 1.000] (n=59), per source {'islrtc': 0.4483, 'kaggle': 0.9698, 'rkmvu': 1.0}, against the app -

| 01:53 | 79 | 32, 1.5 | 16.4 | 17 | exp OPT6, exp WF, landmark_split | GPU under 70% during a B row: landmark workers to 17 |

OPT6 INCLUDE test floor (0.979): 0.988 [0.979, 0.995] (n=816)
| OPT6 | include + islrtc + kaggle + rkmvu, 8449 clips, 0 s | W search trial 6: LR 0.184x, augmentation level 0, min epochs 25 (McNemar p=nan vs W) | new signers 0.094 [0.000, 0.125] (n=287); all 0.545 [0.056, 0.973] / 0.598 | 0.240 | c1 0.097 | islrtc 0.097, kaggle 0.980, rkmvu 0.056 | 3.257, 0.131 | 387.7, 0.910 | 1.000 | - | - | 0.500 | - | unclear |

OPT6 top confused pairs (true -> predicted): crocodile -> city (3); area -> zone (2); blood pressure -> biology (2); clean -> cauliflower (1); elephant -> colour (1); switch -> drink (1); addicted dependent hooked -> red (1); address -> share capital (1); afforestation -> lion (1); africa -> evening (1)


OPT6 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.407 [0.302, 1.000] (n=59), per source {'islrtc': 0.3966, 'kaggle': 0.9799, 'rkmvu': 1.0}, against the app -


OPT9 INCLUDE test floor (0.979): 0.982 [0.973, 0.990] (n=816)
| OPT9 | include + islrtc + kaggle + rkmvu, 8449 clips, 0 s | W9: W + look-alike and same-meaning label smoothing (B9 as a fine-tune) (McNemar p=nan vs W) | new signers 0.098 [0.000, 0.129] (n=287); all 0.538 [0.056, 0.959] / 0.568 | 0.233 | c1 0.100 | islrtc 0.100, kaggle 0.963, rkmvu 0.056 | 3.350, 0.093 | 390.1, 0.890 | 1.000 | - | - | 0.403 | - | unclear |

OPT9 top confused pairs (true -> predicted): elephant -> colour (4); crocodile -> city (3); switch -> drink (2); appointment -> air matress (2); land -> bike suspension (2); mall -> format (2); brinjal -> onion (1); crocodile -> inform apprise notify (1); addicted dependent hooked -> red (1); address -> share capital (1)


OPT9 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.424 [0.322, 1.000] (n=59), per source {'islrtc': 0.4138, 'kaggle': 0.9664, 'rkmvu': 1.0}, against the app -


WF INCLUDE test floor (0.979): 0.982 [0.972, 0.990] (n=816)
| WF | include + islrtc + kaggle + rkmvu, 8449 clips, 231 s | final: W recipe on the T+7 re-split (INCLUDE test held out), 3 seeds (McNemar p=nan vs APP) | new signers 0.139 [0.000, 0.174] (n=287); all 0.574 [0.056, 0.991] / 0.622 | 0.283 | c1 0.145 | islrtc 0.145, kaggle 0.993, rkmvu 0.056 | 3.797, 0.116 | 1708.7, 0.903 | 1.000 | 0.915, +0.341 FLAG | - | 0.433 | - | no |

WF top confused pairs (true -> predicted): area -> zone (2); land -> australia (2); tripura -> temperature (2); elephant -> colour (1); switch -> uncle (1); addicted dependent hooked -> red (1); address -> acknowledgement (1); afforestation -> global economy (1); africa -> evening (1); agriculture -> zone (1)


WF as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.559 [0.441, 1.000] (n=59), per source {'islrtc': 0.5517, 'kaggle': 0.9933, 'rkmvu': 1.0}, against the app -


W7 INCLUDE test floor (0.979): 0.980 [0.972, 0.989] (n=816)
| W7 | include + islrtc + kaggle + rkmvu, 8449 clips, 0 s | W + signer-adversarial head (gradient reversal, weight 0.1) (McNemar p=nan vs W) | new signers 0.101 [0.000, 0.132] (n=287); all 0.540 [0.056, 0.957] / 0.568 | 0.236 | c1 0.104 | islrtc 0.104, kaggle 0.963, rkmvu 0.056 | 3.402, 0.118 | 422.5, 0.904 | 1.000 | - | - | 0.399 | - | unclear |

W7 top confused pairs (true -> predicted): elephant -> colour (4); crocodile -> city (3); appointment -> air matress (2); land -> bike suspension (2); mall -> format (2); brinjal -> onion (1); crocodile -> inform apprise notify (1); key -> man (1); switch -> uncle (1); addicted dependent hooked -> red (1)


W7 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.441 [0.336, 1.000] (n=59), per source {'islrtc': 0.431, 'kaggle': 0.9664, 'rkmvu': 1.0}, against the app -


W8 INCLUDE test floor (0.979): 0.983 [0.974, 0.991] (n=816)
| W8 | include + islrtc + kaggle + rkmvu, 8449 clips, 0 s | W + handshape, location, movement auxiliary heads (McNemar p=nan vs W) | new signers 0.098 [0.000, 0.129] (n=287); all 0.535 [0.056, 0.951] / 0.568 | 0.235 | c1 0.100 | islrtc 0.100, kaggle 0.956, rkmvu 0.056 | 3.348, 0.086 | 414.6, 0.904 | 1.000 | - | - | 0.396 | - | unclear |

W8 top confused pairs (true -> predicted): elephant -> colour (4); crocodile -> city (3); appointment -> air matress (2); land -> bike suspension (2); mall -> format (2); brinjal -> onion (1); crocodile -> inform apprise notify (1); key -> man (1); switch -> man (1); switch -> drink (1)


W8 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.441 [0.336, 1.000] (n=59), per source {'islrtc': 0.431, 'kaggle': 0.9597, 'rkmvu': 1.0}, against the app -


APP INCLUDE test floor (0.979): 0.980 [0.971, 0.989] (n=816)
| APP | INCLUDE + Kaggle (app as shipped) | the model in the app as shipped (format 1 weights, 32 frames, 314 words) | new signers 0.407 [0.305, 1.000] (n=59); all 0.852 [0.350, 1.000] / 0.922 | 0.691 | c1 0.397 | islrtc 0.397, kaggle 0.940, rkmvu 1.000 | 0.854, 0.060 | 11.4, 0.941 | nan | - | - | 0.343 | - | baseline |

APP top confused pairs (true -> predicted): elephant -> colour (4); busy -> photograph (2); crocodile -> city (2); drink -> friday (2); tea -> good (2); brinjal -> onion (1); busy -> soap (1); elephant -> plane (1); lemon -> young (1); lemon -> key (1)


APP as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.407 [0.305, 1.000] (n=59), per source {'islrtc': 0.3966, 'kaggle': 0.9396, 'rkmvu': 1.0}, against the app -


WF INCLUDE test floor (0.979): 0.980 [0.971, 0.989] (n=816)
| WF | include + islrtc + kaggle + rkmvu, 8449 clips, 180 s | final: W recipe on the T+7 re-split (INCLUDE test held out), 3 seeds (McNemar p=nan vs APP) | new signers 0.118 [0.000, 0.152] (n=287); all 0.556 [0.056, 0.971] / 0.609 | 0.257 | c1 0.123 | islrtc 0.123, kaggle 0.977, rkmvu 0.056 | 3.826, 0.086 | 1820.3, 0.901 | 1.000 | 0.809, +0.253 FLAG | - | 0.388 | - | no |

WF top confused pairs (true -> predicted): elephant -> colour (4); area -> island (2); land -> australia (2); mall -> format (2); range -> winter (2); busy -> working hours (1); crocodile -> city (1); switch -> uncle (1); addicted dependent hooked -> red (1); address -> acknowledgement (1)


WF as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.508 [0.390, 1.000] (n=59), per source {'islrtc': 0.5, 'kaggle': 0.9799, 'rkmvu': 1.0}, against the app -


WF INCLUDE test floor (0.979): 0.979 [0.969, 0.988] (n=816)
| WF | include + islrtc + kaggle + rkmvu, 8449 clips, 0 s | final: W recipe on the T+7 re-split (INCLUDE test held out), 3 seeds (McNemar p=nan vs APP) | new signers 0.132 [0.000, 0.167] (n=287); all 0.566 [0.056, 0.979] / 0.615 | 0.270 | c1 0.138 | islrtc 0.138, kaggle 0.983, rkmvu 0.056 | 3.669, 0.084 | 1644.2, 0.897 | 1.000 | 0.842, +0.276 FLAG | - | 0.396 | - | no |

WF top confused pairs (true -> predicted): elephant -> colour (4); appointment -> list (2); land -> australia (2); mall -> format (2); range -> winter (2); switch -> uncle (1); addicted dependent hooked -> red (1); address -> acknowledgement (1); afforestation -> bharat (1); africa -> evening (1)


WF as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.492 [0.373, 1.000] (n=59), per source {'islrtc': 0.4828, 'kaggle': 0.9832, 'rkmvu': 1.0}, against the app -


### final1 shipped (2026-10-29 02:03): row WF, seed 1 (the W recipe on cache_v5)

- Seeds on the grown val, app words only, new signers (n=59) / Kaggle C: s1 0.559 / 0.993, s2 0.492 / 0.980,
  s3 0.508 / 0.980, uniform soup 0.492 / 0.983. Spread between seeds is up to 4 clips of 59 (s1 vs s2 9 to 1).
- Research union against W (new-signer clips whose word both heads know, app view, n=256): s1 0.176 vs 0.102 (20 to 1,
  p<0.001), soup 0.152 (15 to 2, p=0.002), s2 0.145 (p=0.007), s3 0.141 (p=0.013). The recipe gain holds across seeds,
  so picking s1 on the same val adds only a small optimism.
- Ship gate, app words only: against W 0.559 vs 0.441 (8 to 1, p=0.039), Kaggle C 0.993 vs 0.970; against the app reader
  before W 0.559 vs 0.407 (10 to 1, p=0.012), Kaggle C 0.993 vs 0.940.
- Floors: INCLUDE test (held out of training from cache_v5) 0.982 [0.972, 0.990] against 0.979; Kaggle held-out 0.993.
- Calibration (two-fold by word over pooled new-signer val): T 1.98 (both folds lowered ECE); RAPS per-fold coverage
  0.52 / 0.75 at 50% and 0.81 / 0.91 at 80%. Float16 file: max logit difference 0.0097, top-1 agreement 1.00.
- SignReaderTest passes; APK app/build/outputs/apk/standard/debug/app-standard-debug.apk.
| 02:03 | 47 | 16, 0.7 | 13.0 | 12 | landmark_split | under target |

final1 selection note: s1 was the best of 3 seeds on this same val, so its own numbers (0.559; p=0.039 against W) are
selected on val and optimistic. Headline numbers are the seed mean and the soup: new signers, app words, n=59: seed
mean 0.520 (0.559 / 0.492 / 0.508), soup 0.492; Kaggle C seed mean 0.984, soup 0.983. The evidence for the recipe is
that all 3 seeds and the soup beat W on the research union (p = <0.001, 0.007, 0.013 and 0.002).

### Test, read once (2026-10-29): cache_v5 test split, app words only, n=62 (c10 61, c25 1)

| reader | top-1 [95%] | per signer | ECE at its T | RAPS coverage (50%, 80%) | McNemar vs shipped |
|---|---|---|---|---|---|
| WF seed 1 (shipped) | 0.403 [0.000, 0.500] | {'c10': 0.41, 'c25': 0.0} | 0.168 (T 1.98) | 0.452, 0.742 | - |
| W | 0.387 [0.000, 0.484] | {'c10': 0.393, 'c25': 0.0} | 0.150 (T 1.00) | 0.484, 0.790 | 4 to 3, p=1 |
| app reader before W | 0.371 [0.000, 0.476] | {'c10': 0.377, 'c25': 0.0} | 0.098 (T 0.86) | 0.597, 0.823 | 5 to 3, p=0.727 |

Test is closed for choices from here on.


Stitched sentence proxy (test, WF_s1, optimistic: no coarticulation), 12 intents x 4 samplings, 1 signer; CI over intents: gloss WER 0.444 [0.196, 0.648]; sentence exact 0.571 [0.347, 0.804]; segment insertion 0.130 [0.025, 0.220]; segment deletion 0.000 [0.000, 0.000]. Indicative only; no claim for the gloss-to-text gate.

WF INCLUDE test floor (0.979): 0.979 [0.969, 0.988] (n=816)

WF INCLUDE test floor (0.979): 0.975 [0.964, 0.985] (n=816)
| WF | include + islrtc + kaggle + rkmvu, 8449 clips, 98 s | final: W recipe on the T+7 re-split (INCLUDE test held out), 3 seeds (McNemar p=nan vs APP) | new signers 0.111 [0.056, 0.167] (n=287); all 0.559 [0.089, 0.988] / 0.585 | 0.258 | c1 0.112 | islrtc 0.112, kaggle 0.990, rkmvu 0.111 | 3.898, 0.109 | 1628.5, 0.904 | 1.000 | 0.656, +0.098 | - | 0.407 | - | no |

WF top confused pairs (true -> predicted): switch -> uncle (2); appointment -> list (2); brain -> grey (2); land -> australia (2); mall -> format (2); range -> winter (2); elephant -> colour (1); addicted dependent hooked -> red (1); address -> work (1); afforestation -> active absorption (1)


WF as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.559 [0.441, 1.000] (n=59), per source {'islrtc': 0.5517, 'kaggle': 0.9899, 'rkmvu': 1.0}, against the app -

| WF | include + islrtc + kaggle + rkmvu, 8449 clips, 98 s | final: W recipe on the T+7 re-split (INCLUDE test held out), 3 seeds (McNemar p=nan vs APP) | new signers 0.094 [0.000, 0.125] (n=287); all 0.547 [0.056, 0.980] / 0.586 | 0.245 | c1 0.097 | islrtc 0.097, kaggle 0.983, rkmvu 0.056 | 3.913, 0.092 | 1777.6, 0.913 | 1.000 | 0.666, +0.119 | - | 0.418 | - | no |

WF top confused pairs (true -> predicted): elephant -> colour (3); switch -> uncle (2); area -> zone (2); mall -> format (2); sandwich -> zone (2); addicted dependent hooked -> red (1); address -> zone (1); afforestation -> backup (1); africa -> evening (1); agriculture -> zone (1)


WF as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.525 [0.407, 1.000] (n=59), per source {'islrtc': 0.5172, 'kaggle': 0.9832, 'rkmvu': 1.0}, against the app -


Calibration method for the next run (WF seed 1; full val, new signers plus Kaggle C, app words, n=357; two-fold by word): per fold (T, ECE at T=1 on the other fold, ECE at T, RAPS (level, threshold, coverage, mean size), n): [(1.091, 0.049, 0.069, [(0.5, 0.499, 0.927, 1.2), (0.8, 0.84, 0.939, 2.1)], 179), (1.131, 0.034, 0.061, [(0.5, 0.545, 0.927, 1.2), (0.8, 0.74, 0.933, 1.6)], 178)]. Not applied to the installed reader in this run.
| 02:13 | 83 | 46, 1.8 | 16.5 | 12 | embed, landmark_split, pretrain | under target |
| WF_s1 | 3177 | 585 | 0.554 [0.000, 0.968] / 0.600 | 125, 0.024 |

WF INCLUDE test floor (0.979): 0.982 [0.972, 0.990] (n=816)
| WF | include + islrtc + kaggle + rkmvu, 8449 clips, 0 s | final: W recipe on the T+7 re-split (INCLUDE test held out), 3 seeds (McNemar p=nan vs APP) | new signers 0.129 [0.000, 0.164] (n=287); all 0.564 [0.056, 0.980] / 0.610 | 0.269 | c1 0.134 | islrtc 0.134, kaggle 0.983, rkmvu 0.056 | 3.673, 0.075 | 1501.4, 0.897 | 1.000 | 0.767, +0.203 | - | 0.418 | - | no |

WF top confused pairs (true -> predicted): elephant -> colour (3); switch -> uncle (2); appointment -> list (2); area -> zone (2); land -> australia (2); mall -> format (2); range -> winter (2); tripura -> asymmetrical design (2); addicted dependent hooked -> red (1); address -> acknowledgement (1)


WF as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.492 [0.373, 1.000] (n=59), per source {'islrtc': 0.4828, 'kaggle': 0.9832, 'rkmvu': 1.0}, against the app -


Shipped (debug APK only, 2026-10-29 02:22): soup of 5 seeds (WF seeds 1 to 5, uniform weights); s1 withdrawn
(val-lucky: on the union s1 beat every other seed, s4 15 to 1, p=0.0005).
- Rule 1 against W (research union, n=256): 0.148 vs 0.102, 14 to 2, p=0.004. App words only, new signers (n=59):
  0.492 vs W 0.441 (5 to 2, p=0.45) and vs the pre-W reader 0.407 (7 to 2, p=0.18); Kaggle C 0.983 (W 0.970, pre-W
  0.940). INCLUDE test floor 0.982 [0.972, 0.990].
- Calibration on the full val (all signers, app words, n=357, two-fold by word): T stays 1 (fold fits 1.36 and 1.46 did
  not lower ECE on their other folds); RAPS coverage 0.91 / 0.92 at 50% and 0.93 / 0.92 at 80% (Kaggle-heavy pool:
  sets are wide enough). Float16 file: max logit difference 0.0118, top-1 agreement 1.00. SignReaderTest passes.
- Test was read once earlier with s1; it is closed and was not used for this change.

### Test, read once (2026-10-29): cache_v5 test split, app words only, n=62 (c10 61, c25 1)

| reader | top-1 [95%] | per signer | ECE at its T | RAPS coverage (50%, 80%) | McNemar vs shipped |
|---|---|---|---|---|---|
| WF soup of 5 seeds (shipped) | 0.387 [0.000, 0.484] | {'c10': 0.393, 'c25': 0.0} | 0.415 (T 1.00) | 0.419, 0.419 | - |
| app reader before W | 0.371 [0.000, 0.476] | {'c10': 0.377, 'c25': 0.0} | 0.098 (T 0.86) | 0.597, 0.823 | 5 to 4, p=1 |

Test is closed for choices from here on.

| 02:23 | 87 | 3, 0.9 | 15.7 | 14 | embed, landmark_split | under target |

Soup recalibrated (debug APK 02:25): calibration refit on new-signer val after the test read-out showed miscalibration;
test ECE/coverage for the refit are not unbiased, and the refit is not scored on test. Fit: new-signer val, app words,
n=59, two-fold by word: T 2.23 (folds 2.23 and 2.22; lowered ECE on both); RAPS coverage 0.56 / 0.72 at 50% and
0.78 / 0.88 at 80%. Float16 top-1 agreement 1.00; SignReaderTest passes.
Release recommendation: on test the soup and the previous reader are equally accurate (5 to 4, p=1) and the previous
reader is better calibrated, so a release build uses the previous reader (staging\APP_head); the soup is for field
trials in debug builds only. Owner to decide.
| WF_s1 | 3177 | 585 | 0.554 [0.000, 0.968] / 0.610 | 125, 0.024 |

Record correction: the 50% face dropout added for B3 and later rows has no effect on the W and WF rows. Their
architecture (the shipped reader) takes hands, arms and two hand-presence flags only, never the face.

WF INCLUDE test floor (0.979): 0.979 [0.969, 0.988] (n=816)
| WF | include + islrtc + kaggle + rkmvu, 8449 clips, 129 s | final: W recipe on the T+7 re-split (INCLUDE test held out), 3 seeds (McNemar p=nan vs APP) | new signers 0.118 [0.000, 0.151] (n=287); all 0.562 [0.056, 0.988] / 0.586 | 0.261 | c1 0.123 | islrtc 0.123, kaggle 0.990, rkmvu 0.056 | 3.914, 0.095 | 1608.5, 0.886 | 1.000 | 0.703, +0.140 | - | 0.422 | - | no |

WF top confused pairs (true -> predicted): switch -> uncle (2); appointment -> list (2); tripura -> asymmetrical design (2); busy -> fortify fortification (1); addicted dependent hooked -> red (1); address -> verification (1); afforestation -> land (1); africa -> evening (1); agriculture -> zone (1); allow -> owl (1)


WF as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.542 [0.424, 1.000] (n=59), per source {'islrtc': 0.5345, 'kaggle': 0.9933, 'rkmvu': 1.0}, against the app -


WF INCLUDE test floor (0.979): 0.979 [0.969, 0.988] (n=816)
| WF | include + islrtc + kaggle + rkmvu, 8449 clips, 127 s | final: W recipe on the T+7 re-split (INCLUDE test held out), 3 seeds (McNemar p=nan vs APP) | new signers 0.108 [0.056, 0.167] (n=287); all 0.556 [0.086, 0.982] / 0.576 | 0.253 | c1 0.108 | islrtc 0.108, kaggle 0.987, rkmvu 0.111 | 3.903, 0.092 | 1743.3, 0.908 | 1.000 | 0.649, +0.094 | - | 0.414 | - | no |

WF top confused pairs (true -> predicted): switch -> uncle (2); appointment -> address (2); capital -> black (2); land -> australia (2); mall -> format (2); range -> winter (2); elephant -> colour (1); key -> man (1); addicted dependent hooked -> red (1); address -> working hours (1)


WF as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.542 [0.424, 1.000] (n=59), per source {'islrtc': 0.5345, 'kaggle': 0.9866, 'rkmvu': 1.0}, against the app -

| 02:33 | 87 | 39, 1.6 | 15.7 | 13 | exp WF, landmark_split | GPU under 70% during a B row: landmark workers to 13 |

WF INCLUDE test floor (0.979): 0.975 [0.964, 0.985] (n=816)
| WF | include + islrtc + kaggle + rkmvu, 8449 clips, 121 s | final: W recipe on the T+7 re-split (INCLUDE test held out), 3 seeds (McNemar p=nan vs APP) | new signers 0.125 [0.000, 0.160] (n=287); all 0.562 [0.056, 0.980] / 0.590 | 0.264 | c1 0.130 | islrtc 0.130, kaggle 0.983, rkmvu 0.056 | 3.874, 0.100 | 1624.8, 0.899 | 1.000 | 0.679, +0.117 | - | 0.414 | - | no |

WF top confused pairs (true -> predicted): key -> man (2); switch -> uncle (2); mall -> format (2); busy -> क्रॉस कंट्री (1); addicted dependent hooked -> red (1); address -> zone (1); afforestation -> global economy (1); africa -> evening (1); agriculture -> zone (1); allow -> bharat (1)


WF as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.542 [0.424, 1.000] (n=59), per source {'islrtc': 0.5345, 'kaggle': 0.9866, 'rkmvu': 1.0}, against the app -


WF INCLUDE test floor (0.979): 0.980 [0.971, 0.989] (n=816)
| WF | include + islrtc + kaggle + rkmvu, 8449 clips, 0 s | final: W recipe on the T+7 re-split (INCLUDE test held out), 3 seeds (McNemar p=nan vs APP) | new signers 0.129 [0.000, 0.164] (n=287); all 0.564 [0.056, 0.980] / 0.605 | 0.269 | c1 0.134 | islrtc 0.134, kaggle 0.983, rkmvu 0.056 | 3.692, 0.064 | 1573.7, 0.903 | 1.000 | 0.727, +0.163 | - | 0.425 | - | no |

WF top confused pairs (true -> predicted): elephant -> colour (3); switch -> uncle (2); appointment -> list (2); area -> zone (2); land -> australia (2); mall -> format (2); range -> winter (2); tripura -> asymmetrical design (2); addicted dependent hooked -> red (1); address -> acknowledgement (1)


WF as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.508 [0.390, 1.000] (n=59), per source {'islrtc': 0.5, 'kaggle': 0.9832, 'rkmvu': 1.0}, against the app -


Soup size (I9b): WF seeds 6, 7, 8 each 0.542 on new signers, app words (n=59); Kaggle C 0.993 / 0.987 / 0.987. Eight
seeds: mean 0.533, range 0.492 to 0.559. Soup of 8 against soup of 5: union 0.139 vs 0.136 (1 clip to 0, p=1), app words
0.508 vs 0.492 (1 to 0), Kaggle C 0.983 both, INCLUDE test 0.980. Beyond 5 seeds the soup barely moves; the installed
soup of 5 stays.

I10 setup (HWGAT augmentation, sl-hwgat dataTransform.py and configs.py, MIT): WF already had mirroring with the hand
slots and arm points swapped, rotation (uniform +/-0.2 rad, std 0.115, close to HWGAT's 0.1), label smoothing 0.1, and
hand drop (each hand lost on a random share of frames up to 0.3, kept instead of HWGAT's 20% both-hands mask). Origin
jitter does nothing here: the reader centres every clip on the shoulders. Added: speed 0.5 to 1.5 (was 0.8 to 1.2) and
shear with std 0.1.

I10 INCLUDE test floor (0.979): 0.979 [0.968, 0.988] (n=816)
| I10 | include + islrtc + kaggle + rkmvu, 8449 clips, 139 s | WF + HWGAT augmentation: speed 0.5 to 1.5, shear 0.1 (McNemar p=0.109 vs WF) | new signers 0.125 [0.000, 0.160] (n=287); all 0.564 [0.056, 0.984] / 0.593 | 0.268 | c1 0.130 | islrtc 0.130, kaggle 0.987, rkmvu 0.056 | 3.821, 0.078 | 1634.9, 0.899 | 1.000 | 0.764, +0.200 | - | 0.429 | - | no |

I10 top confused pairs (true -> predicted): switch -> uncle (2); land -> australia (2); busy -> photograph (1); busy -> working hours (1); addicted dependent hooked -> red (1); address -> acknowledgement (1); afforestation -> global economy (1); africa -> evening (1); agriculture -> zone (1); allow -> banner (hyderabad) (1)


I10 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.525 [0.407, 1.000] (n=59), per source {'islrtc': 0.5172, 'kaggle': 0.9899, 'rkmvu': 1.0}, against the app -


I10 INCLUDE test floor (0.979): 0.979 [0.968, 0.988] (n=816)
| I10 | include + islrtc + kaggle + rkmvu, 8449 clips, 140 s | WF + HWGAT augmentation: speed 0.5 to 1.5, shear 0.1 (McNemar p=0.00635 vs WF) | new signers 0.122 [0.000, 0.153] (n=287); all 0.557 [0.056, 0.973] / 0.598 | 0.265 | c1 0.126 | islrtc 0.126, kaggle 0.977, rkmvu 0.056 | 3.830, 0.071 | 1750.1, 0.904 | 1.000 | 0.760, +0.203 | - | 0.440 | - | no |

I10 top confused pairs (true -> predicted): elephant -> colour (3); switch -> uncle (3); area -> island (2); land -> australia (2); mall -> format (2); tripura -> asymmetrical design (2); busy -> voluntarily (1); addicted dependent hooked -> red (1); address -> acknowledgement (1); afforestation -> bharat (1)


I10 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.525 [0.407, 1.000] (n=59), per source {'islrtc': 0.5172, 'kaggle': 0.9799, 'rkmvu': 1.0}, against the app -

| 02:43 | 85 | 34, 1.6 | 15.9 | 12 | exp I10, landmark_split | GPU under 70% during a B row: landmark workers to 12 |

I10 INCLUDE test floor (0.979): 0.973 [0.962, 0.983] (n=816)
| I10 | include + islrtc + kaggle + rkmvu, 8449 clips, 120 s | WF + HWGAT augmentation: speed 0.5 to 1.5, shear 0.1 (McNemar p=0.607 vs WF) | new signers 0.132 [0.056, 0.178] (n=287); all 0.569 [0.102, 0.988] / 0.605 | 0.271 | c1 0.134 | islrtc 0.134, kaggle 0.990, rkmvu 0.111 | 3.829, 0.100 | 1602.7, 0.901 | 1.000 | 0.763, +0.194 | - | 0.437 | - | no |

I10 top confused pairs (true -> predicted): switch -> uncle (2); area -> zone (2); land -> australia (2); tripura -> asymmetrical design (2); busy -> working hours (1); addicted dependent hooked -> red (1); address -> acknowledgement (1); afforestation -> betray (1); africa -> evening (1); agriculture -> zone (1)


I10 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.542 [0.424, 1.000] (n=59), per source {'islrtc': 0.5345, 'kaggle': 0.9933, 'rkmvu': 1.0}, against the app -


I10 INCLUDE test floor (0.979): 0.980 [0.971, 0.989] (n=816)
| I10 | include + islrtc + kaggle + rkmvu, 8449 clips, 120 s | WF + HWGAT augmentation: speed 0.5 to 1.5, shear 0.1 (McNemar p=0.0636 vs WF) | new signers 0.129 [0.000, 0.164] (n=287); all 0.559 [0.056, 0.969] / 0.591 | 0.267 | c1 0.134 | islrtc 0.134, kaggle 0.973, rkmvu 0.056 | 3.861, 0.097 | 1577.5, 0.906 | 1.000 | 0.702, +0.143 | - | 0.437 | - | no |

I10 top confused pairs (true -> predicted): elephant -> colour (4); land -> australia (2); mall -> format (2); busy -> photograph (1); busy -> ashoka (academic) (1); switch -> man (1); switch -> uncle (1); addicted dependent hooked -> red (1); address -> zone (1); afforestation -> global economy (1)


I10 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.542 [0.424, 1.000] (n=59), per source {'islrtc': 0.5345, 'kaggle': 0.9732, 'rkmvu': 1.0}, against the app -


I10 INCLUDE test floor (0.979): 0.975 [0.964, 0.985] (n=816)
| I10 | include + islrtc + kaggle + rkmvu, 8449 clips, 135 s | WF + HWGAT augmentation: speed 0.5 to 1.5, shear 0.1 (McNemar p=0.388 vs WF) | new signers 0.129 [0.056, 0.174] (n=287); all 0.568 [0.095, 0.988] / 0.597 | 0.277 | c1 0.130 | islrtc 0.130, kaggle 0.990, rkmvu 0.111 | 3.801, 0.092 | 1515.9, 0.884 | 1.000 | 0.785, +0.218 | - | 0.433 | - | no |

I10 top confused pairs (true -> predicted): switch -> uncle (2); area -> island (2); land -> australia (2); mall -> format (2); busy -> atom (science) (1); addicted dependent hooked -> red (1); address -> work (1); afforestation -> global economy (1); africa -> evening (1); agriculture -> zone (1)


I10 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.542 [0.424, 1.000] (n=59), per source {'islrtc': 0.5345, 'kaggle': 0.9933, 'rkmvu': 1.0}, against the app -


I10 INCLUDE test floor (0.979): 0.978 [0.968, 0.988] (n=816)
| I10 | include + islrtc + kaggle + rkmvu, 8449 clips, 0 s | WF + HWGAT augmentation: speed 0.5 to 1.5, shear 0.1 (McNemar p=0.754 vs WF) | new signers 0.132 [0.000, 0.167] (n=287); all 0.571 [0.056, 0.991] / 0.607 | 0.271 | c1 0.138 | islrtc 0.138, kaggle 0.993, rkmvu 0.056 | 3.723, 0.077 | 1494.0, 0.899 | 1.000 | 0.750, +0.180 | - | 0.440 | - | no |

I10 top confused pairs (true -> predicted): switch -> uncle (2); area -> island (2); land -> australia (2); mall -> format (2); tripura -> asymmetrical design (2); addicted dependent hooked -> red (1); address -> acknowledgement (1); afforestation -> global economy (1); africa -> evening (1); agriculture -> zone (1)


I10 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.542 [0.424, 1.000] (n=59), per source {'islrtc': 0.5345, 'kaggle': 0.9933, 'rkmvu': 1.0}, against the app -


I10 (WF + speed 0.5 to 1.5 + shear 0.1), 5 seeds: new signers, app words (n=59) 0.525 / 0.525 / 0.542 / 0.542 / 0.542
(mean 0.535; WF seeds 1 to 5 mean 0.529); Kaggle C 0.990 / 0.980 / 0.993 / 0.973 / 0.993. Soup of 5 against WF soup of
5: union 0.150 vs 0.136 (5 clips to 1, p=0.22), app words 0.542 vs 0.492 (3 to 0, p=0.25), Kaggle C 0.993 vs 0.983,
INCLUDE test 0.978. Direction positive, not significant (rule 1 needs p < 0.01): not shipped; candidate recipe for the
next run.
| 02:53 | 81 | 34, 1.6 | 14.9 | 11 | exp I11, landmark_split | GPU under 70% during a B row: landmark workers to 11 |

I11 INCLUDE test floor (0.979): 0.931 [0.913, 0.947] (n=816)
| I11 | include + islrtc + kaggle + rkmvu, 8449 clips, 101 s | WF on cache_v5_hc: missing hand frames filled (quadratic through nearest seen frames; wrist at the ends) (McNemar p=0.00936 vs WF) | new signers 0.108 [0.000, 0.139] (n=287); all 0.550 [0.056, 0.971] / 0.571 | 0.244 | c1 0.112 | islrtc 0.112, kaggle 0.977, rkmvu 0.056 | 3.896, 0.087 | 1625.8, 0.903 | 1.000 | 0.616, +0.066 | - | 0.407 | - | no |

I11 top confused pairs (true -> predicted): switch -> man (2); elephant -> colour (1); giraffe -> बेसलाइन (1); injury -> applicant (1); key -> man (1); switch -> uncle (1); addicted dependent hooked -> red (1); address -> reconciliation (1); afforestation -> bharat (1); africa -> evening (1)


I11 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.559 [0.441, 1.000] (n=59), per source {'islrtc': 0.5517, 'kaggle': 0.9765, 'rkmvu': 1.0}, against the app -


I11 INCLUDE test floor (0.979): 0.892 [0.869, 0.913] (n=816)
| I11 | include + islrtc + kaggle + rkmvu, 8449 clips, 86 s | WF on cache_v5_hc: missing hand frames filled (quadratic through nearest seen frames; wrist at the ends) (McNemar p=1.62e-09 vs WF) | new signers 0.084 [0.000, 0.115] (n=287); all 0.509 [0.056, 0.915] / 0.532 | 0.229 | c1 0.086 | islrtc 0.086, kaggle 0.919, rkmvu 0.056 | 5.231, 0.147 | 2636.1, 0.906 | 1.000 | 0.505, -0.004 | - | 0.351 | - | no |

I11 top confused pairs (true -> predicted): crocodile -> gratis free of charge (2); elephant -> colour (2); key -> lemon (2); brain -> grey (2); capital -> black (2); land -> ग्रैंड स्लैम (2); bear -> electrical load (1); brinjal -> onion (1); busy -> improve (1); busy -> foundation (organization) (1)


I11 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.525 [0.407, 1.000] (n=59), per source {'islrtc': 0.5172, 'kaggle': 0.9362, 'rkmvu': 1.0}, against the app -


I11 INCLUDE test floor (0.979): 0.892 [0.869, 0.913] (n=816)

I11 INCLUDE test floor (0.979): 0.931 [0.913, 0.947] (n=816)
| I11 | include + islrtc + kaggle + rkmvu, 8449 clips, 14 s | WF on cache_v5_hc: missing hand frames filled (quadratic through nearest seen frames; wrist at the ends) (McNemar p=1.62e-09 vs WF) | new signers 0.084 [0.000, 0.115] (n=287); all 0.509 [0.056, 0.915] / 0.532 | 0.229 | c1 0.086 | islrtc 0.086, kaggle 0.919, rkmvu 0.056 | 5.231, 0.147 | 2636.1, 0.906 | 1.000 | 0.505, -0.004 | - | 0.351 | - | no |

I11 top confused pairs (true -> predicted): crocodile -> gratis free of charge (2); elephant -> colour (2); key -> lemon (2); brain -> grey (2); capital -> black (2); land -> ग्रैंड स्लैम (2); bear -> electrical load (1); brinjal -> onion (1); busy -> improve (1); busy -> foundation (organization) (1)


I11 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.525 [0.407, 1.000] (n=59), per source {'islrtc': 0.5172, 'kaggle': 0.9362, 'rkmvu': 1.0}, against the app -

| I11 | include + islrtc + kaggle + rkmvu, 8449 clips, 14 s | WF on cache_v5_hc: missing hand frames filled (quadratic through nearest seen frames; wrist at the ends) (McNemar p=0.00936 vs WF) | new signers 0.108 [0.000, 0.139] (n=287); all 0.550 [0.056, 0.971] / 0.571 | 0.244 | c1 0.112 | islrtc 0.112, kaggle 0.977, rkmvu 0.056 | 3.896, 0.087 | 1625.8, 0.903 | 1.000 | 0.616, +0.066 | - | 0.407 | - | no |

I11 top confused pairs (true -> predicted): switch -> man (2); elephant -> colour (1); giraffe -> बेसलाइन (1); injury -> applicant (1); key -> man (1); switch -> uncle (1); addicted dependent hooked -> red (1); address -> reconciliation (1); afforestation -> bharat (1); africa -> evening (1)


I11 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.559 [0.441, 1.000] (n=59), per source {'islrtc': 0.5517, 'kaggle': 0.9765, 'rkmvu': 1.0}, against the app -


I11 INCLUDE test floor (0.979): 0.934 [0.915, 0.951] (n=816)
| I11 | include + islrtc + kaggle + rkmvu, 8449 clips, 125 s | WF on cache_v5_hc: missing hand frames filled (quadratic through nearest seen frames; wrist at the ends) (McNemar p=0.0169 vs WF) | new signers 0.108 [0.049, 0.167] (n=287); all 0.554 [0.085, 0.980] / 0.588 | 0.252 | c1 0.108 | islrtc 0.108, kaggle 0.983, rkmvu 0.111 | 3.895, 0.091 | 1700.4, 0.903 | 1.000 | 0.689, +0.135 | - | 0.414 | - | no |

I11 top confused pairs (true -> predicted): switch -> uncle (2); appointment -> buy ins (2); land -> accessories (2); mall -> format (2); tripura -> asymmetrical design (2); busy -> working hours (1); busy -> clean (1); giraffe -> hello (1); addicted dependent hooked -> red (1); address -> acknowledgement (1)


I11 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.525 [0.407, 1.000] (n=59), per source {'islrtc': 0.5172, 'kaggle': 0.9866, 'rkmvu': 1.0}, against the app -


I11 INCLUDE test floor (0.979): 0.935 [0.917, 0.952] (n=816)
| I11 | include + islrtc + kaggle + rkmvu, 8449 clips, 139 s | WF on cache_v5_hc: missing hand frames filled (quadratic through nearest seen frames; wrist at the ends) (McNemar p=0.115 vs WF) | new signers 0.122 [0.000, 0.157] (n=287); all 0.561 [0.056, 0.980] / 0.590 | 0.264 | c1 0.126 | islrtc 0.126, kaggle 0.983, rkmvu 0.056 | 3.810, 0.064 | 1630.3, 0.897 | 1.000 | 0.695, +0.134 | - | 0.410 | - | no |

I11 top confused pairs (true -> predicted): mall -> format (2); busy -> voluntarily (1); busy -> clean (1); giraffe -> hello (1); switch -> man (1); switch -> uncle (1); addicted dependent hooked -> red (1); address -> acknowledgement (1); afforestation -> graphics tablet (1); africa -> evening (1)


I11 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.525 [0.407, 1.000] (n=59), per source {'islrtc': 0.5172, 'kaggle': 0.9832, 'rkmvu': 1.0}, against the app -


I10 INCLUDE test floor (0.979): 0.979 [0.969, 0.988] (n=816)
| I10 | include + islrtc + kaggle + rkmvu, 8449 clips, 134 s | WF + HWGAT augmentation: speed 0.5 to 1.5, shear 0.1 (McNemar p=0.00443 vs WF) | new signers 0.101 [0.000, 0.136] (n=287); all 0.552 [0.056, 0.983] / 0.581 | 0.250 | c1 0.104 | islrtc 0.104, kaggle 0.987, rkmvu 0.056 | 3.873, 0.073 | 1739.6, 0.903 | 1.000 | 0.653, +0.101 | - | 0.410 | - | no |

I10 top confused pairs (true -> predicted): switch -> uncle (2); appointment -> address (2); tripura -> asymmetrical design (2); busy -> fortify fortification (1); elephant -> colour (1); addicted dependent hooked -> red (1); address -> working hours (1); afforestation -> global economy (1); africa -> evening (1); agriculture -> zone (1)


I10 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.542 [0.424, 1.000] (n=59), per source {'islrtc': 0.5345, 'kaggle': 0.9899, 'rkmvu': 1.0}, against the app -

| 03:03 | 98 | 0, 1.7 | 16.5 | 14 | exp I11, landmark_split | GPU under 70% during a B row: landmark workers to 14 |

I11 INCLUDE test floor (0.979): 0.939 [0.922, 0.955] (n=816)
| I11 | include + islrtc + kaggle + rkmvu, 8449 clips, 178 s | WF on cache_v5_hc: missing hand frames filled (quadratic through nearest seen frames; wrist at the ends) (McNemar p=0.263 vs WF) | new signers 0.136 [0.056, 0.181] (n=287); all 0.564 [0.105, 0.973] / 0.598 | 0.273 | c1 0.138 | islrtc 0.138, kaggle 0.977, rkmvu 0.111 | 3.770, 0.077 | 1717.9, 0.897 | 1.000 | 0.790, +0.226 | - | 0.440 | - | no |

I11 top confused pairs (true -> predicted): switch -> uncle (2); appointment -> list (2); capital -> impact (2); land -> australia (2); mall -> format (2); tripura -> asymmetrical design (2); busy -> voluntarily (1); busy -> clean (1); giraffe -> goat (1); hug -> umbrella (1)


I11 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.542 [0.424, 1.000] (n=59), per source {'islrtc': 0.5345, 'kaggle': 0.9765, 'rkmvu': 1.0}, against the app -


I11 INCLUDE test floor (0.979): 0.931 [0.913, 0.949] (n=816)
| I11 | include + islrtc + kaggle + rkmvu, 8449 clips, 0 s | WF on cache_v5_hc: missing hand frames filled (quadratic through nearest seen frames; wrist at the ends) (McNemar p=0.108 vs WF) | new signers 0.129 [0.056, 0.174] (n=287); all 0.559 [0.098, 0.969] / 0.590 | 0.262 | c1 0.130 | islrtc 0.130, kaggle 0.973, rkmvu 0.111 | 3.792, 0.096 | 1593.3, 0.897 | 1.000 | 0.678, +0.120 | - | 0.414 | - | no |

I11 top confused pairs (true -> predicted): switch -> uncle (2); land -> australia (2); mall -> format (2); tripura -> asymmetrical design (2); busy -> mahavir jayanti (1); busy -> clean (1); giraffe -> pant (1); hug -> umbrella (1); injury -> crocodile (1); key -> man (1)


I11 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.525 [0.407, 1.000] (n=59), per source {'islrtc': 0.5172, 'kaggle': 0.9765, 'rkmvu': 1.0}, against the app -


I10 INCLUDE test floor (0.979): 0.973 [0.961, 0.983] (n=816)
| I10 | include + islrtc + kaggle + rkmvu, 8449 clips, 188 s | WF + HWGAT augmentation: speed 0.5 to 1.5, shear 0.1 (McNemar p=0.388 vs WF) | new signers 0.132 [0.056, 0.178] (n=287); all 0.568 [0.098, 0.984] / 0.591 | 0.274 | c1 0.134 | islrtc 0.134, kaggle 0.987, rkmvu 0.111 | 3.828, 0.075 | 1611.5, 0.901 | 1.000 | 0.750, +0.182 | - | 0.422 | - | no |

I10 top confused pairs (true -> predicted): elephant -> colour (2); switch -> uncle (2); appointment -> address (2); land -> australia (2); mall -> format (2); addicted dependent hooked -> red (1); address -> acknowledgement (1); afforestation -> bharat (1); africa -> evening (1); agriculture -> हॉटस्पॉट (1)


I10 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.542 [0.424, 1.000] (n=59), per source {'islrtc': 0.5345, 'kaggle': 0.9866, 'rkmvu': 1.0}, against the app -

| 03:13 | 93 | 60, 3.0 | 18.0 | 14 | landmark_split, pretrain | under target |

I11 (hand fill on cache_v5_hc, WF recipe, 5 seeds): new signers, app words 0.525 / 0.559 / 0.525 / 0.525 / 0.542; soup
against WF soup5: union 0.129 vs 0.136 (4 clips to 6, p=0.75), app words 0.525 vs 0.492 (2 to 0), Kaggle C 0.977 vs
0.983, INCLUDE test 0.931 [0.913, 0.949], below the 0.979 floor. Not accepted; filling hands hurts INCLUDE, whose clips
are also filled. I10 seeds 6 and 7: 0.542 / 0.542 (Kaggle C 0.990 / 0.987), kept for a later soup.

I10 INCLUDE test floor (0.979): 0.982 [0.972, 0.990] (n=816)
| I10 | include + islrtc + kaggle + rkmvu, 8449 clips, 181 s | WF + HWGAT augmentation: speed 0.5 to 1.5, shear 0.1 (McNemar p=0.0768 vs WF) | new signers 0.125 [0.000, 0.158] (n=287); all 0.561 [0.056, 0.977] / 0.586 | 0.265 | c1 0.130 | islrtc 0.130, kaggle 0.980, rkmvu 0.056 | 3.837, 0.082 | 1585.8, 0.903 | 1.000 | 0.678, +0.117 | - | 0.422 | - | no |

I10 top confused pairs (true -> predicted): elephant -> colour (3); appointment -> list (2); area -> zone (2); capital -> black (2); land -> australia (2); mall -> format (2); reservation -> zone (2); tripura -> asymmetrical design (2); busy -> voluntarily (1); switch -> man (1)


I10 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.542 [0.424, 1.000] (n=59), per source {'islrtc': 0.5345, 'kaggle': 0.9832, 'rkmvu': 1.0}, against the app -


I10 INCLUDE test floor (0.979): 0.977 [0.966, 0.987] (n=816)
| I10 | include + islrtc + kaggle + rkmvu, 8449 clips, 162 s | WF + HWGAT augmentation: speed 0.5 to 1.5, shear 0.1 (McNemar p=0.146 vs WF) | new signers 0.136 [0.056, 0.185] (n=287); all 0.564 [0.102, 0.972] / 0.605 | 0.271 | c1 0.138 | islrtc 0.138, kaggle 0.977, rkmvu 0.111 | 3.828, 0.088 | 1692.4, 0.894 | 1.000 | 0.774, +0.210 | - | 0.418 | - | no |

I10 top confused pairs (true -> predicted): elephant -> colour (4); appointment -> list (2); area -> zone (2); mall -> format (2); sandwich -> zone (2); busy -> atom (science) (1); switch -> man (1); switch -> uncle (1); addicted dependent hooked -> red (1); address -> work (1)


I10 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.559 [0.441, 1.000] (n=59), per source {'islrtc': 0.5517, 'kaggle': 0.9799, 'rkmvu': 1.0}, against the app -

| 03:24 | 98 | 93, 3.8 | 20.1 | 12 | curl, exp I10, pretrain |  |

I10 INCLUDE test floor (0.979): 0.974 [0.963, 0.984] (n=816)
| I10 | include + islrtc + kaggle + rkmvu, 8449 clips, 200 s | WF + HWGAT augmentation: speed 0.5 to 1.5, shear 0.1 (McNemar p=0.332 vs WF) | new signers 0.132 [0.000, 0.171] (n=287); all 0.566 [0.056, 0.981] / 0.609 | 0.276 | c1 0.138 | islrtc 0.138, kaggle 0.983, rkmvu 0.056 | 3.808, 0.092 | 1424.0, 0.886 | 1.000 | 0.806, +0.241 | - | 0.425 | - | no |

I10 top confused pairs (true -> predicted): switch -> uncle (2); area -> island (2); land -> australia (2); busy -> photograph (1); busy -> voluntarily (1); elephant -> colour (1); addicted dependent hooked -> red (1); address -> acknowledgement (1); afforestation -> land (1); africa -> evening (1)


I10 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.542 [0.424, 1.000] (n=59), per source {'islrtc': 0.5345, 'kaggle': 0.9832, 'rkmvu': 1.0}, against the app -


I10 INCLUDE test floor (0.979): 0.980 [0.971, 0.989] (n=816)
| I10 | include + islrtc + kaggle + rkmvu, 8449 clips, 0 s | WF + HWGAT augmentation: speed 0.5 to 1.5, shear 0.1 (McNemar p=0.18 vs WF) | new signers 0.129 [0.000, 0.164] (n=287); all 0.566 [0.056, 0.984] / 0.603 | 0.271 | c1 0.134 | islrtc 0.134, kaggle 0.987, rkmvu 0.056 | 3.706, 0.069 | 1464.4, 0.897 | 1.000 | 0.736, +0.170 | - | 0.440 | - | no |

I10 top confused pairs (true -> predicted): elephant -> colour (2); switch -> uncle (2); appointment -> list (2); land -> australia (2); mall -> format (2); tripura -> asymmetrical design (2); addicted dependent hooked -> red (1); address -> acknowledgement (1); afforestation -> global economy (1); africa -> evening (1)


I10 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.542 [0.424, 1.000] (n=59), per source {'islrtc': 0.5345, 'kaggle': 0.9866, 'rkmvu': 1.0}, against the app -


WF INCLUDE test floor (0.979): 0.979 [0.969, 0.988] (n=816)
| WF | include + islrtc + kaggle + rkmvu, 8449 clips, 0 s | final: W recipe on the T+7 re-split (INCLUDE test held out), 3 seeds (McNemar p=nan vs APP) | new signers 0.132 [0.000, 0.167] (n=287); all 0.566 [0.056, 0.980] / 0.609 | 0.273 | c1 0.138 | islrtc 0.138, kaggle 0.983, rkmvu 0.056 | 3.681, 0.061 | 1455.8, 0.896 | 1.000 | 0.746, +0.180 | - | 0.433 | - | no |

WF top confused pairs (true -> predicted): elephant -> colour (3); switch -> uncle (2); appointment -> list (2); area -> zone (2); land -> australia (2); mall -> format (2); tripura -> asymmetrical design (2); addicted dependent hooked -> red (1); address -> acknowledgement (1); afforestation -> global economy (1)


WF as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.542 [0.424, 1.000] (n=59), per source {'islrtc': 0.5345, 'kaggle': 0.9832, 'rkmvu': 1.0}, against the app -

| 03:34 | 81 | 33, 1.9 | 17.5 | 11 | curl, exp I12, landmark_split | GPU under 70% during a B row: landmark workers to 11 |

I12 INCLUDE test floor (0.979): 0.892 [0.871, 0.914] (n=816)
| I12 | include + islrtc + kaggle + rkmvu, 8449 clips, 363 s | B12v2: b4 from the MS-MAE encoder trained to epoch 900 on cache_v5 train rows (McNemar p=0.0895 vs WF) | new signers 0.125 [0.000, 0.162] (n=287); all 0.556 [0.056, 0.965] / 0.622 | 0.278 | c1 0.130 | islrtc 0.130, kaggle 0.970, rkmvu 0.056 | 3.646, 0.120 | 1561.9, 0.897 | 1.000 | 0.962, +0.406 FLAG | - | 0.455 | - | no |

I12 top confused pairs (true -> predicted): brinjal -> onion (3); exam -> writer (2); switch -> drink (2); land -> australia (2); reservation -> location (2); volcano -> hat (2); exam -> break (1); turtle -> busy (1); addicted dependent hooked -> red (1); address -> weak (1)


I12 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.407 [0.305, 1.000] (n=59), per source {'islrtc': 0.3966, 'kaggle': 0.9698, 'rkmvu': 1.0}, against the app -

| 03:44 | 76 | 40, 1.9 | 16.3 | 10 | curl, exp I12, landmark_split | GPU under 70% during a B row: landmark workers to 10 |
| 03:46 | 76 | 8, 1.9 | 18.3 | 14 | curl, exp I12, landmark_split | under target |

I12 INCLUDE test floor (0.979): 0.931 [0.912, 0.947] (n=816)
| I12 | include + islrtc + kaggle + rkmvu, 8449 clips, 395 s | B12v2: b4 from the MS-MAE encoder trained to epoch 900 on cache_v5 train rows (McNemar p=0.0336 vs WF) | new signers 0.118 [0.083, 0.250] (n=287); all 0.550 [0.095, 0.961] / 0.612 | 0.266 | c1 0.115 | islrtc 0.115, kaggle 0.966, rkmvu 0.167 | 3.749, 0.131 | 1737.1, 0.903 | 1.000 | 0.981, +0.431 FLAG | - | 0.478 | - | no |

I12 top confused pairs (true -> predicted): brinjal -> onion (3); exam -> writer (3); clean -> wife (2); bad -> thin (2); sandwich -> fop (2); switch -> uncle (1); turtle -> busy (1); addicted dependent hooked -> red (1); address -> friend (1); afforestation -> global economy (1)


I12 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.373 [0.271, 1.000] (n=59), per source {'islrtc': 0.3621, 'kaggle': 0.9664, 'rkmvu': 1.0}, against the app -


I12 INCLUDE test floor (0.979): 0.725 [0.696, 0.756] (n=816)
| I12 | include + islrtc + kaggle + rkmvu, 8449 clips, 324 s | B12v2: b4 from the MS-MAE encoder trained to epoch 900 on cache_v5 train rows (McNemar p=0.000106 vs WF) | new signers 0.115 [0.056, 0.167] (n=287); all 0.530 [0.092, 0.927] / 0.603 | 0.250 | c1 0.115 | islrtc 0.115, kaggle 0.929, rkmvu 0.111 | 3.841, 0.139 | 1774.2, 0.906 | 1.000 | 0.780, +0.251 FLAG | - | 0.291 | - | no |

I12 top confused pairs (true -> predicted): exam -> writer (4); lemon -> key (4); switch -> come (3); turtle -> busy (3); sandwich -> fop (2); volcano -> alloy wheel (2); drink -> uncle (1); switch -> uncle (1); switch -> drink (1); uncle -> man (1)


I12 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.390 [0.288, 1.000] (n=59), per source {'islrtc': 0.3793, 'kaggle': 0.9295, 'rkmvu': 1.0}, against the app -


B12v2 (row I12: b4 from the MS-MAE encoder at epoch 900, cache_v5, 3 seeds): against WF soup5 on the union 0.143 / 0.132 /
0.129 vs 0.136 (p 0.86, 1.0, 0.84); app words, new signers 0.407 / 0.373 / 0.390 vs 0.492; Kaggle C 0.970 / 0.966 /
0.929; INCLUDE test 0.892 / 0.931 / 0.725, below the 0.979 floor. No gain over the warm-started W recipe on app words,
and unstable on INCLUDE. The union level (about 0.13) is above scratch B12 on cache_v4b (0.094), but the val sets
differ, so that comparison is indicative only. No soup: the seeds share only the encoder; each head starts fresh, so
averaging weights is not valid.
| 03:56 | 58 | 0, 0.8 | 14.1 | 9 | landmark_split | free RAM 11.4 GB: landmark workers back to 9 |

WF2 INCLUDE test floor (0.979): 0.974 [0.963, 0.984] (n=816)
| WF2 | include + islrtc + kaggle + rkmvu, 11921 clips, 126 s | final2: WF recipe on cache_v6 (all word clips landmarked), 5 seeds, soup (McNemar p=nan vs APP) | new signers 0.049 [0.000, 0.068] (n=546); all 0.380 [0.031, 0.987] / 0.386 | 0.132 | c22 0.031 | islrtc 0.051, kaggle 0.987, rkmvu 0.031 | 5.908, 0.073 | 4624.1, 0.902 | 1.000 | 0.400, +0.019 | - | 0.381 | - | no |

WF2 top confused pairs (true -> predicted): gujarat -> format (3); switch -> uncle (2); area -> bharat (2); assam -> format (2); bath -> india (2); flood -> format (2); goa -> format (2); jackfruit -> format (2); papaya -> richter scale (2); sperm -> format (2)


WF2 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.508 [0.390, 1.000] (n=59), per source {'islrtc': 0.5, 'kaggle': 0.9899, 'rkmvu': 1.0}, against the app -

| 04:07 | 94 | 35, 1.9 | 18.3 | 14 | exp WF2, landmark_split | under target |

WF2 INCLUDE test floor (0.979): 0.979 [0.968, 0.988] (n=816)
| WF2 | include + islrtc + kaggle + rkmvu, 11921 clips, 320 s | final2: WF recipe on cache_v6 (all word clips landmarked), 5 seeds, soup (McNemar p=nan vs APP) | new signers 0.073 [0.000, 0.093] (n=546); all 0.391 [0.031, 0.973] / 0.443 | 0.164 | c22 0.031 | islrtc 0.076, kaggle 0.973, rkmvu 0.031 | 5.377, 0.115 | 3353.5, 0.904 | 1.000 | 0.903, +0.513 FLAG | - | 0.410 | - | no |

WF2 top confused pairs (true -> predicted): elephant -> colour (4); busy -> kill (2); capital -> day before (2); busy -> photograph (1); switch -> uncle (1); acceleration -> photosynthesis (1); accept -> add (1); across -> bell (1); across -> atmosphere (1); action -> stay (1)


WF2 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.559 [0.441, 1.000] (n=59), per source {'islrtc': 0.5517, 'kaggle': 0.9765, 'rkmvu': 1.0}, against the app -


WF2 INCLUDE test floor (0.979): 0.974 [0.962, 0.984] (n=816)
| WF2 | include + islrtc + kaggle + rkmvu, 11921 clips, 178 s | final2: WF recipe on cache_v6 (all word clips landmarked), 5 seeds, soup (McNemar p=nan vs APP) | new signers 0.057 [0.000, 0.075] (n=546); all 0.383 [0.000, 0.981] / 0.405 | 0.143 | c22 0.000 | islrtc 0.060, kaggle 0.980, rkmvu 0.000 | 5.507, 0.093 | 3410.9, 0.903 | 1.000 | 0.598, +0.215 | - | 0.369 | - | no |

WF2 top confused pairs (true -> predicted): busy -> kill (3); elephant -> colour (2); capital -> day before (2); jackfruit -> atmosphere (2); land -> adjustement (2); switch -> uncle (1); acceleration -> deploy (1); accept -> temporary (1); across -> hippopotamus (1); across -> callous indifferent (1)


WF2 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.508 [0.390, 1.000] (n=59), per source {'islrtc': 0.5, 'kaggle': 0.9899, 'rkmvu': 1.0}, against the app -


WF2 INCLUDE test floor (0.979): 0.978 [0.967, 0.987] (n=816)
| WF2 | include + islrtc + kaggle + rkmvu, 11921 clips, 0 s | final2: WF recipe on cache_v6 (all word clips landmarked), 5 seeds, soup (McNemar p=nan vs APP) | new signers 0.064 [0.000, 0.084] (n=546); all 0.391 [0.031, 0.991] / 0.417 | 0.149 | c22 0.031 | islrtc 0.066, kaggle 0.990, rkmvu 0.031 | 5.291, 0.064 | 3324.0, 0.905 | 1.000 | 0.656, +0.266 FLAG | - | 0.418 | - | no |

WF2 top confused pairs (true -> predicted): switch -> uncle (2); across -> ऑफ साइड (2); assam -> format (2); bath -> india (2); gujarat -> verdict (2); land -> adjustement (2); elephant -> colour (1); acceleration -> photosynthesis (1); accept -> add (1); action -> accessories (1)


WF2 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.525 [0.407, 1.000] (n=59), per source {'islrtc': 0.5172, 'kaggle': 0.9899, 'rkmvu': 1.0}, against the app -


WF INCLUDE test floor (0.979): 0.982 [0.972, 0.990] (n=816)
| WF | include + islrtc + kaggle + rkmvu, 11921 clips, 0 s | final: W recipe on the T+7 re-split (INCLUDE test held out), 3 seeds (McNemar p=nan vs APP) | new signers 0.068 [0.000, 0.086] (n=546); all 0.391 [0.031, 0.983] / 0.423 | 0.147 | c22 0.031 | islrtc 0.070, kaggle 0.983, rkmvu 0.031 | 3.691, 0.075 | 1519.8, 0.896 | 1.000 | - | - | 0.418 | - | no |

WF top confused pairs (true -> predicted): elephant -> colour (3); switch -> uncle (2); appointment -> list (2); area -> zone (2); bath -> india (2); gujarat -> casual leave (cl) (2); jackfruit -> ऑफ साइड (2); land -> australia (2); mall -> format (2); range -> winter (2)


WF as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.492 [0.373, 1.000] (n=59), per source {'islrtc': 0.4828, 'kaggle': 0.9832, 'rkmvu': 1.0}, against the app -

| 04:17 | 72 | 85, 3.0 | 18.3 | 9 | exp WF2, landmark_split | free RAM 7.2 GB: landmark workers back to 9 |

WF2 INCLUDE test floor (0.979): 0.973 [0.962, 0.983] (n=816)
| WF2 | include + islrtc + kaggle + rkmvu, 11921 clips, 160 s | final2: WF recipe on cache_v6 (all word clips landmarked), 5 seeds, soup (McNemar p=nan vs APP) | new signers 0.057 [0.000, 0.075] (n=546); all 0.383 [0.000, 0.982] / 0.412 | 0.147 | c22 0.000 | islrtc 0.060, kaggle 0.980, rkmvu 0.000 | 5.535, 0.102 | 3612.3, 0.900 | 1.000 | 0.648, +0.266 FLAG | - | 0.369 | - | no |

WF2 top confused pairs (true -> predicted): busy -> anklets (2); switch -> uncle (2); area -> zone (2); bath -> bharat (2); gujarat -> annualised return (2); jackfruit -> ऑफ साइड (2); land -> adjustement (2); busy -> late (1); busy -> gupta rulers (academic) (1); acceleration -> photosynthesis (1)


WF2 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.508 [0.390, 1.000] (n=59), per source {'islrtc': 0.5, 'kaggle': 0.9866, 'rkmvu': 1.0}, against the app -


WF2 INCLUDE test floor (0.979): 0.978 [0.967, 0.987] (n=816)
| WF2 | include + islrtc + kaggle + rkmvu, 11921 clips, 199 s | final2: WF recipe on cache_v6 (all word clips landmarked), 5 seeds, soup (McNemar p=nan vs APP) | new signers 0.062 [0.000, 0.081] (n=546); all 0.385 [0.031, 0.979] / 0.429 | 0.152 | c22 0.031 | islrtc 0.064, kaggle 0.977, rkmvu 0.031 | 5.444, 0.114 | 3341.7, 0.902 | 1.000 | 0.811, +0.426 FLAG | - | 0.373 | - | no |

WF2 top confused pairs (true -> predicted): busy -> kill (4); switch -> uncle (2); mall -> format (2); lemon -> man (1); acceleration -> big bang theory (1); accept -> distance (1); across -> बेसलाइन (1); across -> atmosphere (1); action -> armed & dangerous (bihar) (1); addicted dependent hooked -> red (1)


WF2 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.525 [0.407, 1.000] (n=59), per source {'islrtc': 0.5172, 'kaggle': 0.9832, 'rkmvu': 1.0}, against the app -


WF2 INCLUDE test floor (0.979): 0.978 [0.968, 0.988] (n=816)
| WF2 | include + islrtc + kaggle + rkmvu, 11921 clips, 0 s | final2: WF recipe on cache_v6 (all word clips landmarked), 5 seeds, soup (McNemar p=nan vs APP) | new signers 0.070 [0.000, 0.090] (n=546); all 0.393 [0.031, 0.988] / 0.419 | 0.157 | c22 0.031 | islrtc 0.072, kaggle 0.987, rkmvu 0.031 | 5.251, 0.063 | 3231.9, 0.900 | 1.000 | 0.691, +0.298 FLAG | - | 0.425 | - | no |

WF2 top confused pairs (true -> predicted): switch -> uncle (2); area -> zone (2); assam -> format (2); bath -> india (2); jackfruit -> ऑफ साइड (2); land -> adjustement (2); busy -> kill (1); elephant -> colour (1); acceleration -> photosynthesis (1); accept -> add (1)


WF2 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.525 [0.407, 1.000] (n=59), per source {'islrtc': 0.5172, 'kaggle': 0.9899, 'rkmvu': 1.0}, against the app -


final2 (WF2: the WF recipe on cache_v6, 4,940 single-signer ISLRTC word clips still unlandmarked; val grown by the
frozen signers' new clips, union n=289 on words both heads know): 5 seeds, new signers app words 0.508 / 0.559 / 0.508
/ 0.525 / 0.508 (Kaggle C 0.990 / 0.977 / 0.990 / 0.983 / 0.987). Soup of 5 against the installed soup5, same val:
union 0.135 vs 0.135 (4 clips to 4, p=1), app words 0.525 vs 0.492 (2 to 0, p=0.5), Kaggle C 0.990 vs 0.983, INCLUDE
test 0.978. Fails rule 1; the installed soup5 stays. Within-run seed spread on the grown union is again large (seed 2
beat seed 1 by 20 clips to 0).

Cache versions. At 06:00 a leftover wait loop re-ran `final.sh final` on cache_v6; it re-cut the cache at 06:08 and
replaced the final2 WF2 runs (stopped at 06:20). Now:
- cache_v6 as used by final2 and I10V6 (gone): sha256 not recorded; train 11,921 clips; val ISLRTC 1,282 clips (514
  scored), Kaggle C 298, RKMVU 205 (32 scored); test counts not recorded (test closed). The results above stand on it.
- cache_v6b (the 06:08 re-cut, renamed from cache_v6): sha256 9bddf5aa...8141, 34,035 clips in raw; train 14,913
  clips; val ISLRTC 1,561 (514 scored), Kaggle C 298, RKMVU 205 (32 scored); test ISLRTC 606 (288 scored), RKMVU 152
  (17 scored).
- Runs WF2_s1 to s4 now hold the stray rerun on cache_v6b (s3, s4 partial): not used. The final2 WF2 weights are lost.
- The next comparison must score both sides on one cache version. final.sh now moves earlier runs of a row to
  runs/_old instead of deleting them.

## Run summary (reader plan v4, 2026-10-28 to 2026-10-29)

- Shipped to the debug APK (uncommitted): a uniform soup of 5 seeds of the W recipe (the app reader fine-tuned at 0.1x
  learning rate on INCLUDE, Kaggle signers A, B, D, ISLRTC and RKMVU word clips), temperature 2.23 and RAPS fitted on
  new-signer validation. Release recommendation: keep the previous reader (staging\APP_head) until field trials;
  owner to decide.
- Against the previous app reader on validation (app words, new signers, n=59): 0.492 vs 0.407; Kaggle validation
  signer 0.983 vs 0.940; against W on the research union 14 clips to 2 (p=0.004). INCLUDE test floor 0.982.
- Test, read once: seed 1 (since withdrawn) 0.403 vs the previous reader 0.371 (5 to 3, p=0.73); the soup, reported
  only, 0.387 (5 to 4, p=1). No significant test gain; the first soup calibration was overconfident on the new signer and
  was refit on new-signer val.
- Did not pass rule 1 (p < 0.01): I1 test-time augmentation, I2 ensembles, I3 fusion with prototypes (lost app-word
  clips), OPT1 to OPT6, W7, W8, W9, I10 (positive, p 0.22), I11 hand fill (INCLUDE floor drop), B12v2 (MS-MAE init),
  soups of 8 and 15 seeds, final2 (the W recipe on cache_v6).
- Seed spread is large: on the same val, one seed beat another by 15 to 1 and 20 to 0 clips. With one new signer per
  source in val, gains below about 8 points cannot be resolved; the next run needs more held-out signers (FDMSE-ISL
  request, A1 collection) before any model choice.
- Next run's candidate recipe: I10 (W + speed 0.5 to 1.5 + shear 0.1), shipped as a soup of 5, calibrated on new
  signers only; final.sh now takes SEEDS (default 5) and always ships the soup.
- Q3 landmarks done: 14,905 ok, 0 failed, wall 18,233 s. iSign videos: part_aa + part_ab downloaded to
  D:\ml-data\isign, sha256 match the repo's LFS hashes (JOB Q5_isign_videos ok=1); not yet joined or extracted. ASL Citizen zip is on both ml-data\asl_citizen and D:\ml-data\asl_citizen (45.9 GB each).
- Data state: 9,821 of 14,761 word clips landmarked when final2 was cut (the rest are single-signer ISLRTC words); the
  146 long RKMVU clips are kept for stage M and C; iSign videos are downloading to D:; ASL Citizen is on C: and D:.

I10V6 INCLUDE test floor (0.979): 0.978 [0.967, 0.987] (n=816)
| I10V6 | include + islrtc + kaggle + rkmvu, 11921 clips, 162 s | I10 recipe on cache_v6, stock for the next run (not shipped) (McNemar p=nan vs WF) | new signers 0.055 [0.000, 0.075] (n=546); all 0.382 [0.031, 0.980] / 0.399 | 0.138 | c22 0.031 | islrtc 0.056, kaggle 0.980, rkmvu 0.031 | 5.653, 0.102 | 3768.3, 0.902 | 1.000 | 0.506, +0.125 | - | 0.384 | - | no |

I10V6 top confused pairs (true -> predicted): elephant -> colour (3); switch -> uncle (2); area -> zone (2); assam -> format (2); bath -> india (2); capital -> black (2); ice cream -> burette (2); land -> baby cot (2); busy -> agenda (1); acceleration -> assassination (1)


I10V6 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.508 [0.390, 1.000] (n=59), per source {'islrtc': 0.5, 'kaggle': 0.9832, 'rkmvu': 1.0}, against the app -

| 04:25 | 92 | 29, 1.9 | 19.0 | 12 | exp I10V6, landmark_split | under target |

I10V6 INCLUDE test floor (0.979): 0.974 [0.963, 0.984] (n=816)
| I10V6 | include + islrtc + kaggle + rkmvu, 11921 clips, 230 s | I10 recipe on cache_v6, stock for the next run (not shipped) (McNemar p=nan vs WF) | new signers 0.071 [0.000, 0.093] (n=546); all 0.390 [0.031, 0.974] / 0.431 | 0.158 | c22 0.031 | islrtc 0.074, kaggle 0.973, rkmvu 0.031 | 5.407, 0.109 | 3138.5, 0.900 | 1.000 | 0.785, +0.396 FLAG | - | 0.388 | - | no |

I10V6 top confused pairs (true -> predicted): elephant -> colour (3); busy -> kill (2); switch -> uncle (2); capital -> day before (2); size -> anti corruption law (bihar) (2); busy -> anklets (1); acceleration -> photosynthesis (1); accept -> add (1); across -> parade (1); across -> ऑफ साइड (1)


I10V6 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.542 [0.424, 1.000] (n=59), per source {'islrtc': 0.5345, 'kaggle': 0.9799, 'rkmvu': 1.0}, against the app -


I10V6 INCLUDE test floor (0.979): 0.975 [0.964, 0.985] (n=816)
| I10V6 | include + islrtc + kaggle + rkmvu, 11921 clips, 265 s | I10 recipe on cache_v6, stock for the next run (not shipped) (McNemar p=nan vs WF) | new signers 0.081 [0.000, 0.103] (n=546); all 0.397 [0.000, 0.979] / 0.440 | 0.170 | c22 0.000 | islrtc 0.086, kaggle 0.977, rkmvu 0.000 | 5.385, 0.119 | 3349.8, 0.902 | 1.000 | 0.860, +0.463 FLAG | - | 0.399 | - | no |

I10V6 top confused pairs (true -> predicted): busy -> kill (4); elephant -> colour (2); amritsar -> play (2); area -> island (2); capital -> day before (2); sandwich -> zone (2); sperm -> unconditional (2); switch -> uncle (1); acceleration -> photosynthesis (1); accept -> foot (1)


I10V6 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.542 [0.431, 1.000] (n=59), per source {'islrtc': 0.5345, 'kaggle': 0.9866, 'rkmvu': 1.0}, against the app -

| 04:35 | 83 | 34, 1.9 | 18.5 | 12 | exp I10V6, landmark_split | under target |

I10V6 INCLUDE test floor (0.979): 0.978 [0.967, 0.988] (n=816)
| I10V6 | include + islrtc + kaggle + rkmvu, 11921 clips, 137 s | I10 recipe on cache_v6, stock for the next run (not shipped) (McNemar p=nan vs WF) | new signers 0.049 [0.000, 0.070] (n=546); all 0.374 [0.031, 0.972] / 0.402 | 0.135 | c22 0.031 | islrtc 0.051, kaggle 0.970, rkmvu 0.031 | 5.588, 0.088 | 3763.7, 0.910 | 1.000 | 0.564, +0.189 | - | 0.377 | - | no |

I10V6 top confused pairs (true -> predicted): busy -> kill (3); elephant -> colour (2); key -> man (2); switch -> uncle (2); bath -> india (2); ice cream -> burette (2); jackfruit -> ऑफ साइड (2); land -> adjustement (2); mall -> format (2); acceleration -> big bang theory (1)


I10V6 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.508 [0.390, 1.000] (n=59), per source {'islrtc': 0.5, 'kaggle': 0.9799, 'rkmvu': 1.0}, against the app -


I10V6 INCLUDE test floor (0.979): 0.977 [0.966, 0.987] (n=816)
| I10V6 | include + islrtc + kaggle + rkmvu, 11921 clips, 179 s | I10 recipe on cache_v6, stock for the next run (not shipped) (McNemar p=nan vs WF) | new signers 0.062 [0.000, 0.081] (n=546); all 0.389 [0.000, 0.988] / 0.421 | 0.153 | c22 0.000 | islrtc 0.066, kaggle 0.987, rkmvu 0.000 | 5.453, 0.117 | 3549.1, 0.899 | 1.000 | 0.714, +0.325 FLAG | - | 0.366 | - | no |

I10V6 top confused pairs (true -> predicted): busy -> kill (2); appointment -> list (2); area -> zone (2); jackfruit -> ऑफ साइड (2); land -> adjustement (2); refund -> accommodation endorsement (2); key -> man (1); switch -> uncle (1); acceleration -> photosynthesis (1); accept -> add (1)


I10V6 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.525 [0.407, 1.000] (n=59), per source {'islrtc': 0.5172, 'kaggle': 0.9899, 'rkmvu': 1.0}, against the app -


I10V6 INCLUDE test floor (0.979): 0.979 [0.968, 0.988] (n=816)
| I10V6 | include + islrtc + kaggle + rkmvu, 11921 clips, 0 s | I10 recipe on cache_v6, stock for the next run (not shipped) (McNemar p=nan vs WF) | new signers 0.071 [0.000, 0.092] (n=546); all 0.392 [0.031, 0.981] / 0.423 | 0.157 | c22 0.031 | islrtc 0.074, kaggle 0.980, rkmvu 0.031 | 5.280, 0.067 | 3186.1, 0.902 | 1.000 | 0.678, +0.286 FLAG | - | 0.407 | - | no |

I10V6 top confused pairs (true -> predicted): busy -> kill (2); elephant -> colour (2); switch -> uncle (2); area -> zone (2); bath -> india (2); jackfruit -> ऑफ साइड (2); land -> adjustement (2); mall -> format (2); acceleration -> anthrax (1); accept -> arrogate (1)


I10V6 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.508 [0.390, 1.000] (n=59), per source {'islrtc': 0.5, 'kaggle': 0.9866, 'rkmvu': 1.0}, against the app -


I10 on cache_v6 (row I10V6, 5 seeds, stock for the next run): new signers app words 0.508 / 0.542 / 0.542 / 0.508 /
0.525; soup against WF2's soup on the same val: union (n=546) 0.079 vs 0.079 (3 clips to 3, p=1), app words 0.508 vs
0.525 (0 to 1), Kaggle C 0.987 vs 0.990, INCLUDE 0.979. I10's small lead on cache_v5 does not hold on cache_v6, so it is
no longer a favoured candidate: the next run should treat the W recipe and I10 as equal until more held-out signers
exist.
| 04:45 | 78 | 0, 0.8 | 14.5 | 14 | landmark_split | under target |
| 04:55 | 87 | 0, 0.8 | 14.8 | 14 | landmark_split | under target |
| 05:05 | 84 | 0, 0.8 | 14.5 | 14 | landmark_split | under target |
| 05:15 | 85 | 17, 0.8 | 15.0 | 14 | landmark_split | under target |
| 05:25 | 88 | 0, 0.8 | 14.6 | 14 | landmark_split | under target |
| 05:35 | 86 | 0, 0.8 | 13.7 | 14 | landmark_split | under target |
| 05:45 | 83 | 2, 0.8 | 13.7 | 14 | landmark_split | under target |
| 05:55 | 83 | 0, 0.8 | 13.5 | 14 | landmark_split | under target |
| 06:05 | 53 | 0, 0.8 | 11.8 | 9 | landmark_split | free RAM 13.8 GB: landmark workers back to 9 |

WF2 INCLUDE test floor (0.979): 0.978 [0.967, 0.987] (n=816)
| WF2 | include + islrtc + kaggle + rkmvu, 14913 clips, 204 s | final2: WF recipe on cache_v6 (all word clips landmarked), 5 seeds, soup (McNemar p=nan vs APP) | new signers 0.051 [0.000, 0.070] (n=546); all 0.380 [0.031, 0.984] / 0.408 | 0.143 | c22 0.031 | islrtc 0.052, kaggle 0.983, rkmvu 0.031 | 5.707, 0.109 | 5095.4, 0.897 | 1.000 | 0.597, +0.217 | - | 0.366 | - | no |

WF2 top confused pairs (true -> predicted): appointment -> buy ins (2); area -> zone (2); jackfruit -> photosynthesis (2); sandwich -> zone (2); size -> anti corruption law (bihar) (2); sperm -> unconditional (2); busy -> मिडिया (1); busy -> pandyas (academic) (1); busy -> picket (1); busy -> love (1)


WF2 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.542 [0.424, 1.000] (n=59), per source {'islrtc': 0.5345, 'kaggle': 0.9933, 'rkmvu': 1.0}, against the app -


WF2 INCLUDE test floor (0.979): 0.979 [0.969, 0.989] (n=816)
| WF2 | include + islrtc + kaggle + rkmvu, 14913 clips, 205 s | final2: WF recipe on cache_v6 (all word clips landmarked), 5 seeds, soup (McNemar p=nan vs APP) | new signers 0.064 [0.000, 0.084] (n=546); all 0.386 [0.031, 0.980] / 0.411 | 0.151 | c22 0.031 | islrtc 0.066, kaggle 0.977, rkmvu 0.031 | 5.675, 0.109 | 5218.6, 0.903 | 1.000 | 0.613, +0.226 | - | 0.377 | - | no |

WF2 top confused pairs (true -> predicted): busy -> kill (3); switch -> uncle (2); appointment -> list (2); area -> island (2); land -> adjustement (2); busy -> allergy (1); elephant -> colour (1); acceleration -> anthrax (1); accept -> validate (1); across -> liquid (1)


WF2 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.525 [0.407, 1.000] (n=59), per source {'islrtc': 0.5172, 'kaggle': 0.9866, 'rkmvu': 1.0}, against the app -

| 06:15 | 95 | 82, 3.5 | 18.7 | 14 | exp WF2, landmark_split |  |
| 06:26 | 82 | 0, 0.8 | 13.7 | 14 | landmark_split | under target |
| 06:36 | 81 | 9, 0.8 | 13.6 | 14 | landmark_split | under target |
| 06:46 | 77 | 7, 0.8 | 13.0 | 14 | landmark_split | under target |
| 06:56 | 77 | 0, 0.8 | 12.4 | 14 | landmark_split | under target |
| 07:06 | 77 | 0, 0.8 | 12.2 | 14 | landmark_split | under target |
| 07:16 | 77 | 0, 0.8 | 12.4 | 14 | landmark_split | under target |
| 07:26 | 76 | 0, 0.8 | 12.1 | 14 | landmark_split | under target |
| 07:36 | 76 | 0, 0.8 | 12.4 | 14 | landmark_split | under target |
| 07:46 | 76 | 0, 0.8 | 12.3 | 14 | landmark_split | under target |
| 07:56 | 76 | 0, 0.8 | 12.0 | 14 | landmark_split | under target |
| 08:06 | 76 | 0, 0.8 | 12.3 | 14 | landmark_split | under target |
| 08:16 | 77 | 12, 0.8 | 12.3 | 14 | landmark_split | under target |
| 08:26 | 7 | 1, 0.8 | 11.8 | 14 | landmark_split | under target |
| 12:00 | 51 | 82, 4.3 | 17.7 | 10 | exp B10, landmark_split | under target |

Non-sign false-accept on val (cache_v6b, n=2437 rest and card-pause windows, a word shown at p >= 0.4 after T, app words): APP 0.139 [0.024, 0.816] (top label a word 1.000; new-signer app-word clips shown 0.475, shown right 0.322, n=59)

Non-sign false-accept on val (cache_v6b, n=2437 rest and card-pause windows, a word shown at p >= 0.4 after T, app words): WF_soup5 0.000 [0.000, 0.000] (top label a word 1.000; new-signer app-word clips shown 0.492, shown right 0.390, n=59)

B10 INCLUDE test floor (0.979): 0.979 [0.968, 0.988] (n=816)
| B10 | include + islrtc + kaggle + nonsign + rkmvu, 26319 clips, 239 s | WF recipe + non-sign class (rests before the headword, card pauses after it), cache_v6b, 5 seeds, soup (McNemar p=nan vs WF) | new signers 0.057 [0.000, 0.076] (n=546); all 0.383 [0.031, 0.981] / 0.402 | 0.143 | c22 0.031 | islrtc 0.058, kaggle 0.980, rkmvu 0.031 | 5.764, 0.091 | 5422.5, 0.906 | 0.001 | 0.724, +0.341 FLAG | - | 0.246 | - | no |

B10 top confused pairs (true -> predicted): busy -> allergy (2); elephant -> colour (2); across -> __nonsign__ (2); area -> __nonsign__ (2); capital -> day before (2); drought -> __nonsign__ (2); jackfruit -> __nonsign__ (2); mall -> format (2); rehabilitation -> __nonsign__ (2); busy -> mahavir jayanti (1)


B10 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.508 [0.390, 1.000] (n=59), per source {'islrtc': 0.5, 'kaggle': 0.9899, 'rkmvu': 1.0}, against the app -


int8 check (installed reader, per-channel symmetric weights, 200 val windows of cache_v6b): top-1 agreement 0.950, max logit difference 0.6283; float16 stays

B10 INCLUDE test floor (0.979): 0.980 [0.971, 0.989] (n=816)
| B10 | include + islrtc + kaggle + nonsign + rkmvu, 26319 clips, 299 s | WF recipe + non-sign class (rests before the headword, card pauses after it), cache_v6b, 5 seeds, soup (McNemar p=nan vs WF) | new signers 0.068 [0.000, 0.088] (n=546); all 0.392 [0.031, 0.988] / 0.432 | 0.159 | c22 0.031 | islrtc 0.070, kaggle 0.987, rkmvu 0.031 | 5.621, 0.106 | 4907.3, 0.900 | 0.001 | 0.871, +0.479 FLAG | - | 0.343 | - | no |

B10 top confused pairs (true -> predicted): busy -> kill (3); across -> __nonsign__ (2); area -> zone (2); capital -> day before (2); land -> adjustement (2); mall -> format (2); rehabilitation -> __nonsign__ (2); switch -> uncle (1); acceleration -> photosynthesis (1); accept -> m-rna (1)


B10 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.525 [0.407, 1.000] (n=59), per source {'islrtc': 0.5172, 'kaggle': 0.9966, 'rkmvu': 1.0}, against the app -


WF INCLUDE test floor (0.979): 0.982 [0.972, 0.990] (n=816)
| WF | include + islrtc + kaggle + rkmvu, 14913 clips, 0 s | final: W recipe on the T+7 re-split (INCLUDE test held out), 3 seeds (McNemar p=nan vs APP) | new signers 0.068 [0.000, 0.086] (n=546); all 0.391 [0.031, 0.983] / 0.423 | 0.147 | c22 0.031 | islrtc 0.070, kaggle 0.983, rkmvu 0.031 | 3.691, 0.075 | 1519.8, 0.896 | 1.000 | - | - | 0.418 | - | no |

WF top confused pairs (true -> predicted): elephant -> colour (3); switch -> uncle (2); appointment -> list (2); area -> zone (2); bath -> india (2); gujarat -> casual leave (cl) (2); jackfruit -> ऑफ साइड (2); land -> australia (2); mall -> format (2); range -> winter (2)


WF as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.492 [0.373, 1.000] (n=59), per source {'islrtc': 0.4828, 'kaggle': 0.9832, 'rkmvu': 1.0}, against the app -


B10 INCLUDE test floor (0.979): 0.982 [0.972, 0.990] (n=816)
| B10 | include + islrtc + kaggle + nonsign + rkmvu, 26319 clips, 0 s | WF recipe + non-sign class (rests before the headword, card pauses after it), cache_v6b, 5 seeds, soup (McNemar p=nan vs WF) | new signers 0.066 [0.000, 0.084] (n=546); all 0.390 [0.031, 0.985] / 0.416 | 0.150 | c22 0.031 | islrtc 0.068, kaggle 0.983, rkmvu 0.031 | 5.516, 0.067 | 5128.2, 0.904 | 0.001 | 0.780, +0.390 FLAG | - | 0.358 | - | no |

B10 top confused pairs (true -> predicted): busy -> kill (3); across -> __nonsign__ (2); allow -> __nonsign__ (2); area -> __nonsign__ (2); capital -> day before (2); drought -> __nonsign__ (2); flood -> __nonsign__ (2); hanuman -> __nonsign__ (2); jackfruit -> __nonsign__ (2); land -> adjustement (2)


B10 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.508 [0.390, 1.000] (n=59), per source {'islrtc': 0.5, 'kaggle': 0.9933, 'rkmvu': 1.0}, against the app -


Non-sign false-accept on val (cache_v6b, n=2437 rest and card-pause windows, a word shown at p >= 0.4 after T, app words): B10 0.000 [0.000, 0.000] (top label a word 0.001; new-signer app-word clips shown 0.407, shown right 0.322, n=59)

B10 (completion run, 2026-10-29): WF recipe plus a non-sign class (11,406 train windows: rests before the headword and
card pauses after it), cache_v6b, initialised from the installed WF soup5, seeds 1 and 2 (seeds 3 to 5 cut by the
one-hour rescope), uniform soup. Rule 1 against WF soup5 scored on the same cache_v6b val (row WF_s95, words both heads
know, n=289): 0.121 vs 0.135, 1 to 5, p=0.22; seeds 0.135 (6 to 6) and 0.114 (2 to 8). App words 0.508 vs 0.492 (n=59);
Kaggle C 0.993; INCLUDE test 0.982. Non-sign false-accept 0.000 for both, but B10 shows fewer real words at the 0.4
floor (0.407 vs 0.492; shown right 0.322 vs 0.390); APP 0.139. Not accepted; WF soup5 stays the reader. Staged only
(`staging\B10`, gate pass, T 2.26).

Stitched sentence proxy (val, WF_s99, optimistic: no coarticulation), 19 intents x 3 samplings, 1 signer; CI over intents: gloss WER 0.700 [0.575, 0.818]; sentence exact 0.169 [0.046, 0.333]; segment insertion 0.000 [0.000, 0.000]; segment deletion 0.040 [0.000, 0.080]. Indicative only; no claim for the gloss-to-text gate.
The line above is the shipped reader (WF soup5) on the val stitches; capture thresholds were tuned on these same val
stitches, and the test stitches stay closed, so it is not comparable with the earlier test line (WF_s1).
| 12:10 | 87 | 56, 4.0 | 21.4 | 13 | exp B11E, exp B13, landmark_split | RAM used 21.4 GB: landmark workers to 13 |

B11E INCLUDE test floor (0.979): 0.614 [0.580, 0.648] (n=816)
| B11E | include + islrtc + kaggle + rkmvu, 14913 clips, 0 s | WF soup5 scored with per-signer wrist offset from each signer's first 20 clips taken off (eval only) (McNemar p=nan vs WF) | new signers 0.046 [0.000, 0.062] (n=546); all 0.320 [0.031, 0.821] / 0.368 | 0.119 | c22 0.031 | islrtc 0.047, kaggle 0.822, rkmvu 0.031 | 4.584, 0.093 | 1885.9, 0.900 | 1.000 | - | - | 0.362 | - | no |

B11E top confused pairs (true -> predicted): jump -> accumulate (5); brinjal -> onion (4); budget -> elephant (4); knife -> shoes (4); switch -> bharat (4); deer -> plane (3); turtle -> दृष्टिकोण (3); busy -> accident (2); clean -> wife (2); elephant -> plane (2)


B11E as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.305 [0.203, 1.000] (n=59), per source {'islrtc': 0.2931, 'kaggle': 0.8423, 'rkmvu': 1.0}, against the app -


B11 eval-time (row B11E): WF soup5 scored with each signer's mean wrist offset (shoulder widths, first 20 clips) taken
off its hands, cache_v6b val. Rule 1 against the same soup unshifted (n=289): 0.093 vs 0.135, 4 to 16, p=0.012; app words
0.305 vs 0.492; Kaggle C 0.842; INCLUDE test 0.614 (floor 0.979). Eval-time normalisation hurts, so B11 is not
trained (plan: train only if eval-time helps).

B13 INCLUDE test floor (0.979): 0.983 [0.974, 0.991] (n=816)
| B13 | include + islrtc + kaggle + rkmvu, 14913 clips, 238 s | WF recipe + whole-clip one-hand drop (20% of clips) on top of the 50% mirror, cache_v6b, 5 seeds, soup (McNemar p=nan vs WF) | new signers 0.062 [0.000, 0.081] (n=546); all 0.387 [0.031, 0.984] / 0.428 | 0.153 | c22 0.031 | islrtc 0.064, kaggle 0.983, rkmvu 0.031 | 5.677, 0.121 | 5183.4, 0.898 | 1.000 | 0.733, +0.345 FLAG | - | 0.403 | - | no |

B13 top confused pairs (true -> predicted): switch -> uncle (2); capital -> day before (2); gujarat -> gas tractor (2); land -> adjustement (2); sandwich -> zone (2); busy -> kill (1); elephant -> colour (1); key -> man (1); acceleration -> photosynthesis (1); accept -> add (1)


B13 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.542 [0.424, 1.000] (n=59), per source {'islrtc': 0.5345, 'kaggle': 0.9866, 'rkmvu': 1.0}, against the app -


B13 INCLUDE test floor (0.979): 0.979 [0.968, 0.988] (n=816)
| B13 | include + islrtc + kaggle + rkmvu, 14913 clips, 332 s | WF recipe + whole-clip one-hand drop (20% of clips) on top of the 50% mirror, cache_v6b, 5 seeds, soup (McNemar p=nan vs WF) | new signers 0.077 [0.000, 0.097] (n=546); all 0.398 [0.031, 0.988] / 0.438 | 0.166 | c22 0.031 | islrtc 0.080, kaggle 0.987, rkmvu 0.031 | 5.676, 0.138 | 4692.6, 0.905 | 1.000 | 0.862, +0.463 FLAG | - | 0.381 | - | no |

B13 top confused pairs (true -> predicted): busy -> kill (3); area -> island (2); capital -> day before (2); switch -> uncle (1); acceleration -> act of war (1); accept -> add (1); across -> liquid (1); across -> cannibal (1); action -> stay (1); addicted dependent hooked -> red (1)


B13 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.559 [0.441, 1.000] (n=59), per source {'islrtc': 0.5517, 'kaggle': 0.9966, 'rkmvu': 1.0}, against the app -

| 12:20 | 96 | 77, 4.1 | 20.6 | 12 | exp B13, landmark_split | RAM used 20.6 GB: landmark workers to 12 |

B13 INCLUDE test floor (0.979): 0.983 [0.973, 0.991] (n=816)
| B13 | include + islrtc + kaggle + rkmvu, 14913 clips, 266 s | WF recipe + whole-clip one-hand drop (20% of clips) on top of the 50% mirror, cache_v6b, 5 seeds, soup (McNemar p=nan vs WF) | new signers 0.077 [0.000, 0.097] (n=546); all 0.398 [0.031, 0.988] / 0.421 | 0.165 | c22 0.031 | islrtc 0.080, kaggle 0.987, rkmvu 0.031 | 5.684, 0.134 | 5107.8, 0.899 | 1.000 | 0.761, +0.363 FLAG | - | 0.410 | - | no |

B13 top confused pairs (true -> predicted): busy -> kill (3); capital -> day before (2); switch -> uncle (1); acceleration -> bandh general strike (1); accept -> add (1); across -> parade (1); across -> atmosphere (1); action -> communicable diseases (1); addicted dependent hooked -> red (1); address -> essay (1)


B13 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.559 [0.441, 1.000] (n=59), per source {'islrtc': 0.5517, 'kaggle': 0.9966, 'rkmvu': 1.0}, against the app -


B13 INCLUDE test floor (0.979): 0.983 [0.973, 0.990] (n=816)
| B13 | include + islrtc + kaggle + rkmvu, 14913 clips, 315 s | WF recipe + whole-clip one-hand drop (20% of clips) on top of the 50% mirror, cache_v6b, 5 seeds, soup (McNemar p=nan vs WF) | new signers 0.068 [0.000, 0.088] (n=546); all 0.390 [0.031, 0.982] / 0.434 | 0.158 | c22 0.031 | islrtc 0.070, kaggle 0.980, rkmvu 0.031 | 5.710, 0.139 | 5204.3, 0.903 | 1.000 | 0.827, +0.437 FLAG | - | 0.392 | - | no |

B13 top confused pairs (true -> predicted): busy -> kill (3); switch -> uncle (2); amritsar -> sculpture (2); capital -> day before (2); land -> adjustement (2); mall -> format (2); key -> man (1); acceleration -> photosynthesis (1); accept -> प्लास्टिक (1); across -> squrriel (1)


B13 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.542 [0.424, 1.000] (n=59), per source {'islrtc': 0.5345, 'kaggle': 0.9899, 'rkmvu': 1.0}, against the app -


B13 INCLUDE test floor (0.979): 0.984 [0.974, 0.991] (n=816)
| B13 | include + islrtc + kaggle + rkmvu, 14913 clips, 218 s | WF recipe + whole-clip one-hand drop (20% of clips) on top of the 50% mirror, cache_v6b, 5 seeds, soup (McNemar p=nan vs WF) | new signers 0.060 [0.000, 0.081] (n=546); all 0.387 [0.031, 0.988] / 0.415 | 0.149 | c22 0.031 | islrtc 0.062, kaggle 0.987, rkmvu 0.031 | 5.706, 0.111 | 5464.1, 0.904 | 1.000 | 0.656, +0.269 FLAG | - | 0.373 | - | no |

B13 top confused pairs (true -> predicted): switch -> uncle (2); land -> adjustement (2); mall -> format (2); busy -> kill (1); key -> man (1); acceleration -> din noise racket (1); accept -> acknowledge (1); across -> parade (1); across -> atmosphere (1); action -> armed & dangerous (bihar) (1)


B13 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.542 [0.424, 1.000] (n=59), per source {'islrtc': 0.5345, 'kaggle': 0.9899, 'rkmvu': 1.0}, against the app -


B13 INCLUDE test floor (0.979): 0.984 [0.975, 0.991] (n=816)
| B13 | include + islrtc + kaggle + rkmvu, 14913 clips, 0 s | WF recipe + whole-clip one-hand drop (20% of clips) on top of the 50% mirror, cache_v6b, 5 seeds, soup (McNemar p=nan vs WF) | new signers 0.070 [0.000, 0.091] (n=546); all 0.395 [0.031, 0.991] / 0.434 | 0.159 | c22 0.031 | islrtc 0.072, kaggle 0.990, rkmvu 0.031 | 5.524, 0.109 | 4847.6, 0.903 | 1.000 | 0.798, +0.403 FLAG | - | 0.414 | - | no |

B13 top confused pairs (true -> predicted): switch -> uncle (2); area -> island (2); capital -> day before (2); mall -> format (2); busy -> kill (1); acceleration -> photosynthesis (1); accept -> add (1); across -> parade (1); across -> atmosphere (1); action -> armed & dangerous (bihar) (1)


B13 as the app shows it (logits masked to its 314 words, scored on those words): new-signer 0.525 [0.407, 1.000] (n=59), per source {'islrtc': 0.5172, 'kaggle': 0.9933, 'rkmvu': 1.0}, against the app -

| 12:30 | 95 | 21, 1.8 | 18.1 | 12 | landmark_split | under target |

eval_isl.py rerun (completion run, 2026-10-29) with `isl_spell.pkl` rebuilt (prepare_isl_real.py, synth_isl.py,
mix_isl_spell.py: train 39,625, test 329 ISLAN samples against 109 before) and the installed WF soup5 word reader:

    ISL words    top-1 0.9816  top-3 0.9951  (n=816, sign_reader)
    ISL letters  top-1 0.1965  top-3 0.2772  (n=285, isl_spell)
    ISL digits   top-1 0.5000  top-3 0.5227  (n=44, isl_spell)
    ISL combined words   top-1 0.9816  word-only 0.9816  (n=816, gate costs 0.0000)
    ISL combined spelled read as spelling 0.0213, correctly 0.0091  (n=329)
    ISL real letters  top-1 0.1304  top-3 0.3043  (n=23, ISLRTC signers, isl_spell)
    ISL real digits   top-1 0.8000  top-3 1.0000  (n=10, ISLRTC signers, isl_spell)
    ISL real gate odds 1.5: spelled 0.0303, correctly 0.0303  (n=33); INCLUDE words lost 0.0000
    ISL real gate odds 2.0: spelled 0.0000, correctly 0.0000  (n=33); INCLUDE words lost 0.0000

The letter set grew (ISLAN multi-signer photographs now included), so the letter rows are not comparable with the 0.354
above. The sign-or-spell gate changed with the word reader: at the shipped odds 2.0 the soup lets no real ISLRTC letter
or digit through as spelling (old reader 0.485 spelled, 0.242 correct), with no word lost either way; spelled names go
through the CTC reader, which this gate does not cover. The sentence demo assertion failed (synthetic "ram" read as
"6af" by the letter reader), so the script stopped after the rows above.

B13 slices (new-signer val, app words as the app shows them, n=59, naturally one-handed n=25): WF soup5: as recorded 0.492, mirrored 0.508, left hand dropped 0.458, right hand dropped 0.203, naturally one-handed 0.520; B13 soup5: as recorded 0.525, mirrored 0.508, left hand dropped 0.525, right hand dropped 0.237, naturally one-handed 0.600

B13 (completion run): WF recipe plus whole-clip one-hand drop (20% of clips) on top of the 50% mirror, cache_v6b,
initialised from the installed WF soup5, 5 seeds, uniform soup. Rule 1 against WF soup5 on the same val (words both
heads know, n=289): soup 0.135 vs 0.135, 4 to 4, p=1; seeds 0.132 / 0.142 / 0.163 / 0.128 / 0.132 (seed 3 9 to 1,
p=0.02, within the seed spread). App words 0.525 vs 0.492 (2 to 0, p=0.5); Kaggle C 0.993; INCLUDE test 0.984 to 0.979
by seed. Slices above: one hand dropped no longer costs the left-hand-drop case (0.525 vs 0.458), naturally one-handed
clips 0.600 vs 0.520, but n is small and right-hand loss stays 0.24. Not accepted; WF soup5 stays. Staged only
(`staging\B13`, gate pass, T 2.69).
| 12:40 | 97 | 19, 1.8 | 16.5 | 13 | landmark_split | RAM used 16.5 GB: landmark workers back to 13 |
| 12:50 | 91 | 4, 1.3 | 15.4 | 14 | landmark_split | RAM used 15.4 GB: landmark workers back to 14 |

Sign-or-spell gate after the soup (2026-10-29). The 0.000 above came from eval_isl.py scoring the gate at T 1; the app
compares probabilities after each reader's temperature, so eval_isl.py now does too. ISLRTC real letters and digits
(n=33), spelled / correct / INCLUDE words lost: previous reader (T 0.86) at odds 2.0 0.485 / 0.242 / 0.000; soup (T 2.23)
at 2.0 0.333 / 0.182 / 0.004. Odds refit two-fold on ISL-Fingerspelling's held-out signer (`spell_gate.py`, 5,122
letter windows, 199 videos; lowest odds losing at most 0.005 of INCLUDE words): 1.5 on both folds. Soup at 1.5:
spelled 0.367, correct 0.123, words lost 0.005 against the previous reader at 2.0 0.332 [0.312, 0.349], 0.097, 0.000;
ISLRTC real 0.394 / 0.212. Passed; `"spell_odds": 1.5` installed in sign_reader.json and sign_reader_v3.json (read by
the readers, SPELL_ODDS 2.0 stays the default). The sentence demo's "ram" read as "6af" with either word reader: the
letter reader on the rebuilt test photographs, not the word reader. SignReaderTest (10) and InterpreterCallTest (2) pass.

Ported read gate on WF soup5 (T 2.2276, app words; cache_v6b val, non-sign n=2437, new-signer app-word clips n=59; two reads per clip on its first and last 85%; prototypes from 4955 training clips): floor 0.4 alone: non-sign shown 0.000; signs shown 0.492, shown right 0.390. gate (defaults): non-sign shown 0.000; new-signer app-word clips shown 0.424, shown right 0.356; per-read reasons non-sign {'conf': 4874}, signs {'conf': 64, 'pass': 54} gate (calibrated values): non-sign shown 0.000; new-signer app-word clips shown 0.458, shown right 0.390; per-read reasons non-sign {'conf': 4874}, signs {'conf': 58, 'pass': 60}
Read: the gate rejects only by confidence here (margin, prototype and stillness never fire on these windows); it shows
fewer real signs than the floor (0.424 and 0.458 against 0.492) with no false accepts to remove. Not adopted.
Against the RKMVERI group's ported code: WF soup5
beats their HWGAT on 52 clips of 236 shared words (0.538 vs 0.308, 16 to 4, p=0.012); their read gate is not adopted
(above).
