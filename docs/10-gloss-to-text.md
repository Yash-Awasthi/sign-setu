# Gloss to text: plan

This builds on "Sign-Setu: gloss-to-text implementation plan" (C:\work\The_bare_minmums_plan.pdf, 2026-10-28), which
is the floor. Everything it asks for is kept: the gloss lattice, calibrated readers with a reject path, the name
resolver with a margin rule, a reviewed English and Hindi phrasebook, the faithfulness checker, the four-rung output
ladder, the evaluation harness and the release gates. This file records where the build goes further, and why.

The rule from the floor stays the first rule: when the evidence is weak the app says less. No sentence may hold a word
the person did not sign.

## What changes against the floor

### 1. Held-out data is split three ways, by signer (Phases 0, 2)

The floor fits the temperature on held-out logits and then reports calibration on the same logits. That flatters the
result. Here every held-out set is split by signer into a fitting part and a reporting part, and the numbers in
`tools/ml/EVAL.md` come only from the reporting part. Where a set has one signer (ISLRTC, and CISLR, which copies the
ISLRTC signer), it is split by word instead and the report says so.

### 2. Conformal prediction sets instead of three hand-set reject thresholds (Phase 2)

The floor rejects a sign with three thresholds (top probability, top-two gap, entropy) tuned by eye on a plot. Here
each sign gets a split-conformal prediction set (adaptive prediction sets with regularisation, RAPS) fitted on the
fitting signers. The set holds the true word with a chosen probability (90%) on new signers drawn like the fitting
ones, a guarantee that does not depend on the model being right. The set is the token's candidate list; a set larger
than the cutoff, or one that holds the background class, makes the token UNKNOWN. The risk against coverage curve
is still drawn and kept.

### 3. Realistic lattices from real held-out readings, before any sentence is recorded (Phases 0, 5, 6)

The floor cannot tune the ladder until Deaf signers record sentences (Phase 6). Here the harness also builds
simulated lattices: for every sentence the engine can say, each sign is replaced by a real reader output vector for
that word from the held-out signers (sampled, not invented), and deletions and insertions are drawn at the measured
segmentation rates. This gives thousands of realistic lattices today. They are labelled simulated everywhere and
never replace the recorded test set at a release gate.

### 4. The question asked is evidence (Phases 4, 5)

The floor's 20 intents are generic. This app is an interview: the officer's question is known when the answer is
signed. Each question carries the intents that answer it (a name question expects a name; "How did you come here?"
expects a way of travel), and the matcher uses that as a prior over intents, so the answer space shrinks from every
intent to a handful. The intent list starts from the app's own questions (triage.json) and the floor's generic list.

### 5. Lattice decoding instead of top-1 matching (Phase 4)

The matcher aligns each intent pattern to the whole lattice with a small dynamic program: substitution costs come
from the calibrated candidate probabilities, and deletion and insertion costs from `confusion.json`. An intent can
therefore match through a missed sign or an extra one, and its score is a proper log probability that the checker
and the ladder can calibrate.

### 6. Lexicon-constrained CTC decoding for names (Phase 3)

Besides the floor's beam plus edit distance, the speller runs a CTC prefix beam search restricted to a trie of the
name lexicon, and a noisy-channel score built from the speller's own letter confusion replaces plain edit distance (an
M read as N costs less than an M read as A). The margin rule and its 2% wrong-correction target stay.

### 7. The neural part is a tagger, never a writer (Phase 7)

A sequence-to-sequence model that writes sentences can invent words; that was tried and taken out of the app. The
neural part is a small encoder that reads the lattice and the question and outputs an intent and a slot label per
token, trained on template data with reader noise drawn from `confusion.json` and real probability vectors. Its
scores are combined with the engine's; the checker has the last word.

### 8. Checker tested by fuzzing (Phase 5)

Besides the floor's corrupted-output tests, a property test generates thousands of random lattices and random
outputs and checks that no output with a content word outside the lattice ever passes.

## Order of work

1. Phase 0: baseline numbers file; debug recorder in `SignCapture.kt` (landmarks and top-5 logits, never video).
2. Phase 2 on data already on disk: held-out logits, temperature, conformal sets, `confusion.json`, ECE before and
   after, risk against coverage.
3. Phase 1: `GlossLattice.kt` with JSON round trip; the reader's output becomes lattice tokens.
4. Phase 3: name lexicon and resolver.
5. Phases 4 and 5: intents (English and Hindi, from the app's questions), lattice matcher, checker, ladder.
6. Harness with simulated lattices; then recorded sentences when signers are available.
7. Phase 7 only after Gate B.

## Needs people, not code

Recorded test sentences from at least 5 signers not in any training data, Hindi review of every template, and Deaf
review of meaning. Until then every number from simulated lattices is a development number, not a release one.

## Status (2026-10-28)

| Plan step | State |
|---|---|
| 0.1 baseline | Done: tag `baseline-glosstotext-v0`, `tools/ml/BASELINE.md` |
| 0.2 debug recorder, 5.6 decision log | Done in debug builds: `gloss/DebugRecorder.kt`, landmarks only |
| 0.3, 0.4, 6.1 to 6.4 recorded test set | Needs signers: not started |
| 0.5, 6.5 to 6.7 one-command evaluation and report | Done: `tools/eval/run_eval.py`; per-signer views wait for recordings |
| 1 lattice | Done: `gloss/GlossLattice.kt` with JSON; POINT tokens from a sideways point (`sign/Pointing.kt`) when the reader is unsure; "I" and "you" stay with the reader (no depth to tell a point at the camera from a fist) |
| 2.1 to 2.3, 2.6 calibration, conformal sets, risk curve, confusion | Done: `tools/ml/calibrate.py`, `tools/ml/plots/risk_coverage.svg` |
| 2.4 background class | Waiting for recorded non-sign motion (the recorder collects it) |
| 2.5 segmentation error rate | Waiting for recorded sentences |
| 2.7 better reader | Ongoing ML work (section 2 of `C:\work\newses.md`) |
| 3 name resolver | Done: CTC-scored, margin rule, 425-entry `names.txt`, names confirmed in a session; margin needs tuning on held-out spelling |
| 4 phrasebook | Done: 44 intents (every asking question has one, enforced by `check_content.py`), English and Hindi, question prior, lattice matcher; review sheet `docs/review/intents_review.csv` |
| 4.8 review | Needs a Hindi speaker and a Deaf ISL reviewer |
| 5 checker and ladder | Done: checker fuzz-tested, four rungs, alternatives, risky intents flagged |
| Speaker gender setting (4.6) | Done: start card sets it (logged as a system row); Hindi shows one form, both while unset |
| 7 neural tagger, 8 continuous reader | Held for Gate B (real sentence data) |
| Faster tracking (on-device roadmap) | Built, not yet measured on a phone: `camera/SplitTracker.kt` runs hands every frame and body and face on alternate frames (GPU, own CPU fallback, Holistic if hands cannot open); nods and shakes from the face model's head rotation; 19 mouth blendshapes per frame into the recorder. Offline the word reader barely depends on frame rate (ISLRTC top-1 0.342 at 25 fps, 0.316 at 8 fps); the gain is for head movement, mouth, spelling and segmentation |
| Mouthing (item 5) | Features recorded; a model waits for videos with faces (full ISLRTC download) |
| Sentence helper (item 6) | Built: its own Settings import (`ModelStore` "sentence"), separate from the automation agents' model; only reorders checked sentences when the ladder is unsure. Accepts Qwen2.5 0.5B Instruct Q4_K_M (491 MB, Apache-2.0, hash in `KNOWN_SENTENCE_MODELS`); the department copies `C:\work\ml-data\sentence_helper\qwen2.5-0.5b-instruct-q4_k_m.gguf` to the phone and imports it |
| Camera reads (all screens) | Nods, shakes, thumbs, points at the body, held fingers, palm, shrug and courtesies go to the officer as "Camera read" on both the phone and a paired second screen; the person is never asked to confirm |

