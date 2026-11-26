# Baseline screenshots

These are the 0.8.5 `standard` debug captures on an Android 14 emulator, taken from the
emulator's own screen buffer rather than a photograph of a phone. They were copied from
`docs/screens/` unchanged at this commit: same filenames, same bytes.

The canonical images the catalogue shows stay in `docs/screens/`; this directory is the
reference a later capture is compared with, so do not edit a file here to match a change.
New captures go to `docs/screens/after/`.

`py -3 tools/ux/check_baselines.py` fails if a file here is missing or no longer matches
the one `docs/12-screens.md` links.