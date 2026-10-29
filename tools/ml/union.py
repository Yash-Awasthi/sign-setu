"""Rule 1 selection: new-signer validation clips scored as the app would see them (restricted to the app's words when
the true word is one, 868-way otherwise), paired McNemar of a row against W, and the Kaggle validation signer.

    python union.py ROW [ROW ...] [--ref W] [--cache C:/work/ml-data/cache_v4b]
"""
import json
import pathlib
import sys

import numpy as np
import pandas as pd

from exp import APP_MODELS, RUNS, bootstrap, mcnemar
from guard import DATA


def app_view(row, index, val, app, app_only=False):
    run = RUNS / (row if "_s" in row else f"{row}_s1")
    full = np.load(run / "val_preds.npz")
    rest = np.load(run / "val_preds_app_words.npz")
    is_app = index["label"].to_numpy()[val].astype(object)
    is_app = np.isin(is_app, list(app))
    if app_only:
        # The ship gate: only clips of the app's own words, as the app shows them.
        return rest["right"], rest["scored"] & is_app
    right = np.where(is_app, rest["right"], full["right"])
    scored = np.where(is_app, rest["scored"], full["scored"])
    return right, scored


def main(a):
    root = pathlib.Path(a[a.index("--cache") + 1] if "--cache" in a else str(DATA / "cache_v4b"))
    ref = a[a.index("--ref") + 1] if "--ref" in a else "W"
    flagged = {a[i + 1] for i, x in enumerate(a[:-1]) if x.startswith("--")}
    rows = [x for x in a if not x.startswith("--") and x not in flagged]
    index = pd.read_csv(root / "index.csv", keep_default_na=False)
    val = np.flatnonzero(index["split"].to_numpy() == "val")
    src, sig = index["source"].to_numpy()[val], index["signer"].to_numpy()[val]
    app = set(json.loads((APP_MODELS / "sign_reader.json").read_text(encoding="utf-8"))["labels"])
    app_only = "--app-only" in a
    # --common VOCAB: only words both heads know (a 868-word head cannot name the rest of a 3,178-word head's words).
    common = None
    if "--common" in a:
        common = set(json.loads(pathlib.Path(a[a.index("--common") + 1]).read_text(encoding="utf-8")))
    labels = index["label"].to_numpy()[val]
    r0, s0 = app_view(ref, index, val, app, app_only)
    for row in rows:
        r1, s1 = app_view(row, index, val, app, app_only)
        both = s0 & s1 & (np.isin(labels, list(common)) if common is not None else True)
        extra = s1 & ~both & r1 & ~np.isin(src, ["kaggle", "include", "nonsign"])
        s0_, s1_ = both, both
        new = s0_ & s1_ & ~np.isin(src, ["kaggle", "include", "nonsign"])
        kag = s0_ & s1_ & (src == "kaggle")
        lo, hi = bootstrap(r0[kag], sig[kag])
        print(json.dumps({"row": row, "ref": ref, "new_signer_n": int(new.sum()),
                          "new_signer": [round(float(r1[new].mean()), 4), round(float(r0[new].mean()), 4)],
                          "row_only_right": int((r1 & ~r0)[new].sum()), "ref_only_right": int((r0 & ~r1)[new].sum()),
                          "p": round(mcnemar(r1[new], r0[new]), 4),
                          "kaggle_c": [round(float(r1[kag].mean()), 4), round(float(r0[kag].mean()), 4)],
                          "kaggle_c_ref_ci": [round(lo, 4), round(hi, 4)],
                          "row_only_words_right_outside_common": int(extra.sum())}), flush=True)


if __name__ == "__main__":
    main(sys.argv[1:])
