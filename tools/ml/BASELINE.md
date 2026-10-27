# Gloss-to-text baseline (docs/10-gloss-to-text.md, step 0.1)

Frozen at git tag `baseline-glosstotext-v0` (master 4a475dd, before any gloss-to-text work). No later change may make
these worse; each comes from `tools/ml/EVAL.md`.

| Reader | Set | Numbers |
|---|---|---|
| sign_reader | INCLUDE test | top-1 0.979, top-3 0.995 (n=816) |
| sign_reader | ISLRTC dictionary signer, new | top-1 0.342, top-3 0.442 (n=269) |
| sign_reader | held-out Kaggle signer | top-1 0.973, top-3 0.997 (n=300) |
| sign_reader | CISLR (copies the ISLRTC signer) | top-1 0.272, top-3 0.377 (n=610) |
| isl_spell_ctc | held-out signer, spelled names | CER 0.120, exact 0.289 (n=498) |
| isl_spell_ctc | ISLRTC acronyms | CER 0.75 (n=7) |

The current speller file is `isl_spell_ctc.json` (the plan's checklist asked to fix that name in the notes).
