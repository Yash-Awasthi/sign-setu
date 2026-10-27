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
