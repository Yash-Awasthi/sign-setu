# Frozen ISL evaluation set

Frozen 2026-11-26. Landmark sequences only: no video, no images, no frames. The
set is the held-out ground truth for the sign-reader accuracy gate and does not
change after this freeze.

## What is here

| Corpus | Split (as recorded in the corpus file) | Sequences | Words | Signer | Licence |
| --- | --- | --- | --- | --- | --- |
| INCLUDE (AI4Bharat) | `test`, the official signer-disjoint test split | 816 | 262 | `include` | CC BY 4.0 |
| Kaggle ISL video dataset (Prasad Shet) | `kaggle_test`, the held-out signer | 300 | 60 | `kaggle_C` | MIT |

1116 sequences, 99,985 frames (52,738 INCLUDE, 47,247 Kaggle), 313 distinct word
labels. Nine words occur in both corpora. 313 of the shipped word reader's 314
labels have held-out clips here (`still` has none); no other corpus was mixed in.

Both splits and the signer tags are the ones already stored in
`isl_more/include_kaggle.pkl` by `prepare.py` / `prepare_isl_more.py` /
`build_isl_more.py`. Nothing was relabelled, resplit or invented: `include` is the
corpus file's own tag for the INCLUDE set, and `kaggle_C` is its held-out signer.

Excluded on purpose: iSign landmarks (file ids are hashes and the metadata CSVs are
not present, so a word or signer cannot be pinned), ISLRTC and RKMVU held-out clips
(their words are outside the reader's 314-word vocabulary), ISL-Fingerspelling
(letters, not words), and the `cache_v5`/`cache_v6b` feature caches (training
caches, not per-sequence labels).

## Files

Arrays are little-endian `float16` coordinates, one row per frame, frames
concatenated in manifest order; `flags` is `uint8` with columns `has_pose`,
`has_left`, `has_right`.

| File | Shape |
| --- | --- |
| `include_pose.npy`, `kaggle_pose.npy` | (frames, 33, 2) |
| `include_left.npy`, `kaggle_left.npy` | (frames, 21, 2) |
| `include_right.npy`, `kaggle_right.npy` | (frames, 21, 2) |
| `include_flags.npy`, `kaggle_flags.npy` | (frames, 3) |

`manifest.tsv` has one row per sequence, tab separated:

| Column | Meaning |
| --- | --- |
| `seq` | stable id, `include-<index>` or `kaggle-<index>` |
| `corpus` | `INCLUDE` or `Kaggle ISL` |
| `source_file` | corpus file the sequence came from, relative to the data root |
| `source_index` | index of the sample in that file's `samples` list |
| `session` | the same sample as the cache names it, `include_kaggle/<index>` |
| `signer` | the corpus file's own signer tag |
| `word` | the corpus file's own label, lowercase |
| `frames` | rows belonging to this sequence, from `frames:` in the arrays |
| `aspect` | the sample's own aspect if it carries one, else the file's |
| `sha256` | digest of this sequence's array bytes (below) |

Slice a row's sequence with the running sum of earlier `frames` in the same
corpus. Its `sha256` is SHA-256 over the C-order little-endian bytes of
`pose.astype("<f2")`, then `left`, then `right`, then `flags.astype("u1")` for
that sequence alone.

## Reproduce or verify

From the repository root, with the corpora at `C:\ml-data`:

    python tools/ml/freeze_eval.py --data C:/ml-data     # writes this directory
    python tools/ml/freeze_eval.py --check               # verifies it, writes nothing

A freeze refuses to overwrite an existing `manifest.tsv` unless `--force` is
passed, so the set is immutable from this date on. Freezing into two empty
directories gives byte-identical files; `--out DIR` writes a copy elsewhere for
that comparison. `--check` recomputes every digest and frame count and is run in
CI.
