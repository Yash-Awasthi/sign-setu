# Reader evaluation

Output of `eval_isl.py` and `eval_asl.py` on the shipped models.

    ISL words    top-1 0.9792  top-3 0.9951  (n=816, sign_reader)
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
    ISL words, new signer  top-1 0.3266  top-3 0.4673  (n=199, ISLRTC dictionary clips, sign_reader; was 0.2864 / 0.4422)
    ISL words, CISLR       top-1 0.2721  top-3 0.3770  (n=610, CISLR clips of INCLUDE words, sign_reader; was 0.2393 / 0.3459)
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
