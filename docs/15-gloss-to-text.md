# Gloss to text

The reader turns signs into glosses; this part turns glosses into an English or Hindi sentence the officer can rely
on. Its parts: the gloss lattice, calibrated readers with a reject path, the name resolver with a margin rule, a
reviewed English and Hindi phrasebook, the faithfulness checker, the four-rung output ladder and the evaluation harness.

The first rule: when the evidence is weak the app says less. No sentence may hold a word the person did not sign.

## Design

### 1. Held-out data is split three ways, by signer

Fitting a temperature and reporting calibration on the same logits flatters the result. Every held-out set is split
by signer into a fitting part and a reporting part, and the numbers in `tools/ml/EVAL.md` come only from the
reporting part. Where a set has one signer (ISLRTC, and CISLR, which copies the ISLRTC signer), it is split by word
instead and the report says so.

### 2. Conformal prediction sets instead of hand-set reject thresholds

Three thresholds (top probability, top-two gap, entropy) tuned by eye are replaced by a split-conformal prediction
set per sign (adaptive prediction sets with regularisation, RAPS) fitted on the fitting signers. The set holds the
true word with a chosen probability (90%) on new signers drawn like the fitting ones, a guarantee that does not
depend on the model being right. The set is the token's candidate list; a set larger than the cutoff makes the token
UNKNOWN. The risk against coverage curve is drawn and kept.

### 3. Realistic lattices from real held-out readings, before any sentence is recorded

Recorded sentences from Deaf signers are scarce, so the harness also builds simulated lattices: for every sentence the engine can say, each sign is replaced by a real reader output vector for
that word from the held-out signers (sampled, not invented), and deletions and insertions are drawn at the measured
segmentation rates. They are labelled simulated everywhere and are never reported as release numbers.

### 4. The question asked is evidence

This app is an interview: the officer's question is known when the answer is signed. Each question carries the
intents that answer it (a name question expects a name; "How did you come here?" expects a way of travel), and the
matcher uses that as a prior over intents, so the answer space shrinks from every intent to a handful. The intent
list comes from the app's own questions (triage.json) plus a generic list.

### 5. Lattice decoding instead of top-1 matching

The matcher aligns each intent pattern to the whole lattice with a small dynamic program: substitution costs come
from the calibrated candidate probabilities, and deletion and insertion costs from `confusion.json`. An intent can
therefore match through a missed sign or an extra one, and its score is a proper log probability that the checker
and the ladder can calibrate.

### 6. Lexicon-constrained CTC decoding for names

Besides beam search plus edit distance, the speller runs a CTC prefix beam search restricted to a trie of the name
lexicon, and a noisy-channel score built from the speller's own letter confusion replaces plain edit distance (an M
read as N costs less than an M read as A). The margin rule keeps wrong corrections at 2% or less.

### 7. Checker tested by fuzzing

Besides corrupted-output tests, a property test generates thousands of random lattices and random outputs and checks
that no output with a content word outside the lattice ever passes.

### 8. A learned tagger as a second ranker, kept out of the app

A small encoder (`tools/ml/tagger.py`) reads the lattice and the question and names the intent and, per token, the
pattern item or slot it fills. It ranks; it never writes, since a sequence-to-sequence writer was tried and invented
words. It trains on answers built from the phrasebook patterns, each sign token carrying a candidate list the shipped
reader produced for that word on a held-out signer, with further confusions drawn from `sign_confusion.json` and
answers that match no intent. A fifth of each varied intent's wordings (which optional signs appear, which of "a|b")
are held out of training, and epochs are picked on a separate tune set. On new-signer noise it beats the phrasebook
matcher on wordings it trained on (intent 0.903 against 0.875) but falls far behind on held-out wordings (0.444
against 0.801, 134 answers to 1,317) and loses overall (0.811 against 0.860); `tools/ml/phrasebook_eval.py` ports
the matcher and runs the paired test. It learns the templates, not the task, so the app does not use it: its spec
carries `"enabled": false`.

## Where each part lives

| Part | Code and data |
|---|---|
| Lattice | `gloss/GlossLattice.kt`, with a JSON form; POINT tokens from a sideways point (`sign/Pointing.kt`) when the reader is unsure. "I" and "you" stay with the reader: without depth a point at the camera cannot be told from a fist |
| Calibration, conformal sets, confusion | `tools/ml/ship_w.py` fits the word reader's temperature and RAPS thresholds on new-signer validation clips and writes them into its spec, with `sign_confusion.json`; `tools/ml/calibrate.py` did this for earlier readers and draws risk against coverage in `tools/ml/plots/risk_coverage.svg` |
| Tagger (research) | `tools/ml/tagger_data.py` builds its answers, `tools/ml/tagger.py` trains it, `tools/ml/phrasebook_eval.py` scores the matcher on the same answers; not in the app |
| Name resolver | `sign/NameResolver.kt`: CTC-scored candidates, the margin rule, `names.txt` (425 entries) and names confirmed earlier in the session |
| Phrasebook | `gloss/Phrasebook.kt` over `intents.json` (44 intents in English and Hindi, built by `tools/content/build_intents.py`); every asking question has at least one intent, enforced by `check_content.py`; review sheet `docs/review/intents_review.csv` |
| Checker and ladder | `gloss/Ladder.kt`: the faithfulness check, four rungs, alternatives, and a confirmation for risky intents |
| Speaker gender | Set on the start card and logged as a system row; Hindi shows one form, or both while unset |
| Sentence helper | `gloss/SentenceHelper.kt`: an optional model imported in Settings (`ModelStore` "sentence"), Qwen2.5 0.5B Instruct Q4_K_M (491 MB, Apache-2.0, hash in `KNOWN_SENTENCE_MODELS`); it only reorders sentences that already passed the checker, when the ladder is unsure |
| Camera reads | Nods, shakes, thumbs, points at the body, held fingers, palm, shrug and courtesies reach the officer as "Camera read" on the phone and a paired second screen; the person is never asked to confirm |
| Face and mouth | `camera/SplitTracker.kt` gives head rotation (nods and shakes) and 19 mouth blendshapes per frame to the recorder |
| Debug recorder | `gloss/DebugRecorder.kt`, debug builds only: each sign window as landmarks and top-5 logits, the decisions taken, and the agreement of the two reader formats; never video |
| Evaluation | `tools/eval/run_eval.py` replays recorded or simulated lattices and writes one report; the frozen baseline is `tools/ml/BASELINE.md` (git tag `baseline-glosstotext-v0`) |
