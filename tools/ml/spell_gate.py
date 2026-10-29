"""Sign-or-spell gate odds for a word reader, tuned two-fold on ISL-Fingerspelling's held-out signer: each annotated
letter is one window for the letter reader (isl_spell); INCLUDE test words are the windows that must stay words. The
gate reads a window as spelled when the letter reader's top probability beats odds times the word reader's, both after
their temperatures (CombinedReader in SignReaders.kt).

    python spell_gate.py [--ref C:/work/ml-data/staging/APP_head]   (the word reader is the installed one, or
    READER_DIR_SIGN_READER)
"""
import os
import pickle
import sys

import numpy as np

import runtime
from eval_isl import INCLUDE, SPELL_ODDS, featured, held_out, probs
from exp import bootstrap
from guard import DATA

FS = str(DATA / "isl_fingerspell" / "isl_fingerspell.pkl")
GRID = np.round(np.arange(0.5, 4.01, 0.1), 2)
WORDS_LOST_MAX = 0.005


def letters(spell_spec):
    """(letter, window, aspect, name id) for each annotated letter of the held-out signer's names."""
    index = {l: i for i, l in enumerate(spell_spec["labels"])}
    out = []
    for s in pickle.load(open(FS, "rb")):
        if s["split"] != "test":
            continue
        for a in s["letters"]:
            ch, i, j = a["letter"], a["start_frame"], a["end_frame"]
            if ch in index and j - i >= 3:
                part = {k: s[k][i:j] for k in ("left", "right", "pose", "has_left", "has_right", "has_pose")}
                out.append((ch, part, s["aspect"], s["uid"].rsplit("_seg", 1)[0]))
    return out, index


def rates(word_dir):
    os.environ.pop("READER_DIR_SIGN_READER", None)
    if word_dir:
        os.environ["READER_DIR_SIGN_READER"] = word_dir
    (ws, wl), (ss, sl) = runtime.reader("sign_reader"), runtime.reader("isl_spell")
    segs, index = letters(ss)
    segs = [g for g in segs if featured(g[1], g[2], ss.get("norm")) is not None and featured(g[1], g[2], ws.get("norm")) is not None]
    xs = np.stack([featured(s, a, ss.get("norm")) for _, s, a, _ in segs]).astype(np.float64)
    xw = np.stack([featured(s, a, ws.get("norm")) for _, s, a, _ in segs]).astype(np.float64)
    y = np.array([index[l] for l, *_ in segs])
    video = np.array([v for *_, v in segs])
    ps, pw = probs(sl, xs, ss.get("temperature", 1.0)), probs(wl, xw, ws.get("temperature", 1.0))
    iw, isw, iy = held_out(INCLUDE, ws["labels"], norms=(ws.get("norm"), ss.get("norm")))
    pww, psw = probs(wl, iw, ws.get("temperature", 1.0)), probs(sl, isw, ss.get("temperature", 1.0))
    ratio, right = ps.max(1) / pw.max(1), ps.argmax(1) == y
    word_ratio, word_right = psw.max(1) / pww.max(1), pww.argmax(1) == iy
    return ratio, right, video, word_ratio, word_right, ws.get("temperature", 1.0)


def at(odds, ratio, right, word_ratio, word_right, rows=slice(None)):
    spelled = ratio[rows] > odds
    return spelled.mean(), (spelled & right[rows]).mean(), (word_right & (word_ratio > odds)).mean()


def main(a):
    ref = a[a.index("--ref") + 1] if "--ref" in a else str(DATA / "staging" / "APP_head")
    r_ratio, r_right, r_video, r_wr, r_wright, r_t = rates(ref)
    ratio, right, video, wr, wright, t = rates(os.environ.get("READER_DIR_SIGN_READER_TUNE"))
    vids = np.unique(video)
    fold = np.isin(video, vids[np.random.default_rng(7).permutation(len(vids))[: len(vids) // 2]])
    print(f"letter windows n={len(ratio)} from {len(vids)} videos; INCLUDE test words n={len(wr)}")
    picks = []
    for fit, use in ((fold, ~fold), (~fold, fold)):
        # The lowest odds (most letters through) that loses at most WORDS_LOST_MAX of INCLUDE words.
        ok = [o for o in GRID if at(o, ratio, right, wr, wright, fit)[2] <= WORDS_LOST_MAX]
        o = min(ok) if ok else GRID[-1]
        s, c, lost = at(o, ratio, right, wr, wright, use)
        rs, rc, rlost = at(SPELL_ODDS, r_ratio, r_right, r_wr, r_wright, use)
        picks.append(o)
        print(f"fold: odds {o:.1f} -> spelled {s:.3f}, correct {c:.3f}, words lost {lost:.4f}; reference reader at "
              f"{SPELL_ODDS}: spelled {rs:.3f}, correct {rc:.3f}, words lost {rlost:.4f}")
    odds = float(np.mean(picks))
    s, c, lost = at(odds, ratio, right, wr, wright)
    rs, rc, rlost = at(SPELL_ODDS, r_ratio, r_right, r_wr, r_wright)
    lo, hi = bootstrap(r_ratio > SPELL_ODDS, r_video)
    passed = bool(s >= rs - (rs - lo) and lost <= rlost + WORDS_LOST_MAX)
    print(f"GATE odds {odds:.2f} (T {t}): spelled {s:.3f}, correct {c:.3f}, words lost {lost:.4f}; reference (T {r_t}) at "
          f"{SPELL_ODDS}: spelled {rs:.3f} [{lo:.3f}, {hi:.3f}], correct {rc:.3f}, words lost {rlost:.4f}; pass {passed}")
    for o in (SPELL_ODDS, 1.5, 1.0):
        print(f"  odds {o}: spelled {at(o, ratio, right, wr, wright)[0]:.3f}, correct {at(o, ratio, right, wr, wright)[1]:.3f}, "
              f"words lost {at(o, ratio, right, wr, wright)[2]:.4f}")
    print(f"JOB spell_gate ok=1 failed=0 quarantined=0 status=done odds={odds:.2f} pass={passed}")


if __name__ == "__main__":
    main(sys.argv[1:])
