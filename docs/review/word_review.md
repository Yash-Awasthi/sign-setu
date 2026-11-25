# Word review sheet

How a sign enters the app, who checks it, and how often. Every sign and every
signed sentence ships as a draft until a Deaf reviewer signs it off; this sheet is
the record of that review. Written 2026-11-26.

The content it covers: `app/src/main/assets/content/isl_signs.json`,
`asl_signs.json`, the authored clips from `tools/text2isl`, and the words the
readers recognise (`tools/ml/EVAL.md`). The intents and their wording have their
own sheet, `docs/review/intents_review.csv`.

## 1. Who reviews

| Role | Does |
| --- | --- |
| Deaf reviewer, one per language | Watches the sign on the character, reads the gloss and the sense, gives a verdict. ISL needs a Deaf ISL signer; ASL a Deaf ASL signer. A hearing interpreter may advise but does not sign off |
| Second reviewer | Checks again when the first verdict is "disputed", or when a sign is marked hold; two reviewers agreeing is the bar for a sign that has a regional or handshape dispute |
| Maintainer | Prepares the batch, records the verdicts, promotes or demotes the sign, and keeps this sheet current |

A reviewer is named on the department's side; the named contact and the sitting
fee are the department's to fill in and are marked **[confirm]** here.

## 2. The sheet

One row per word per review. The columns are fixed so verdicts can be counted:

| Column | Holds |
| --- | --- |
| `id` | the content id, `isl.<word>`, `asl.<word>`, `asl.letter.<c>` or `gesture.<name>` |
| `gloss` | the word as the officer types it and the sense used in the sentence |
| `language` | ISL or ASL |
| `source` | INCLUDE, ISLRTC, Kaggle, PopSign, authored, or fingerspelling |
| `signers` | how many different signers the recording or drawing is based on |
| `draft_since` | the version the sign first shipped in |
| `reviewer` | initials of the Deaf reviewer |
| `date` | the day of the review |
| `verdict` | `ok`, `fix` (a generator or clip change, retest), `replace` (another clip is better), `hold` (do not use until confirmed), `drop` (fall back to spelling or another sign) |
| `second` | the second reviewer's initials for `hold` and disputed verdicts, or blank |
| `notes` | the reason in one line, in the reviewer's words |

Example row, comma-separated in the same column order:

    isl.thank_you,thank you,ISL,INCLUDE,4,0.8.40,RS,2026-11-26,ok,,both hands, clear on the character

The filled sheet lives beside this page as `word_review.csv`, one row per word.
Nothing in the app reads it: it is the reviewer's record, and the maintainer
applies a verdict to the bundle by hand in the next build.

A `hold` verdict means the sign is not shown as something the app has read; the
officer falls back to another sign or to spelling until a second reviewer clears
it.

## 3. The loop

1. The maintainer prepares a batch (section 4) as a list of ids with the clip and
   the character's rendering beside it, at the officer's phone size.
2. The reviewer watches each sign, reads the gloss, and gives a verdict from the
   table above, in the reviewer's own words for the note.
3. The maintainer records the rows in `word_review.csv` and, for `fix` or
   `replace`, changes the generator or clip and adds the word to the next batch
   for a second look.
4. A `drop` removes the sign from the bundle in the next build; the word falls
   back to another sign or to spelling.
5. Every verdict is dated and tied to a release, so a regression can be traced to
   the review that let it in.

## 4. Cadence

- **A batch a fortnight** while words are being added, on the department's
  working day; a batch is at most 40 words so a session stays under an hour.
- **Before a release** that promotes any sign from draft, the batch holding those
  words is reviewed; the release notes name the version the sign was reviewed in.
- **A new word** ships as a draft, marked as such in the dictionary sheet, until
  its first review.
- **A disputed or held sign** gets a second reviewer within the same fortnight;
  until then it stays `hold`.
- **A reported misread** (support, `docs/21-support.md`) puts that word in the
  next batch, ahead of new words.
- The sheet is reviewed with the department once a quarter: how many words are
  waiting, how many are held, and whether the cadence still matches how fast
  words are added.

## 5. What is not yet true

No reviewer is named and no verdict has been recorded: the sheet, the columns and
the cadence above are ready for the department to run, and every sign in the
bundle stays a draft until the first batch comes back.
