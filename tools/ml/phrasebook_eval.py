"""The app's phrasebook matcher (Phrasebook.kt align and match, same costs and prior) in Python, scored on the tagger's
validation answers: the baseline a tagger must beat.

    python phrasebook_eval.py [VAL_JSONL] [--tagger RUN_DIR]   # with a tagger run: paired McNemar on all, seen, unseen
"""
import json
import math
import pathlib
import sys

from guard import DATA

CONTENT = pathlib.Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "assets" / "content"
INSERT, DELETE, UNEXPLAINED_SHAKE, ON_QUESTION = -2.3, -3.0, -1.6, 0.8


def fit(item, tok, slots):
    name = item.removesuffix("?")
    slot = name[1:] if name.startswith("$") else None
    first = tok["candidates"][0] if tok["candidates"] else None
    if slot == "NAME":
        return first["p"] if first and tok["type"] == "SPELL" else None
    if slot == "NUM":
        return first["p"] if first and tok["type"] == "NUM" else None
    if slot is not None:
        return next((c["p"] for c in tok["candidates"] if c["label"] in slots.get(slot, {})), None)
    if tok["type"] in ("SPELL", "NUM"):
        return None
    words = set(name.split("|"))
    return next((c["p"] for c in tok["candidates"] if c["label"] in words), None)


def align(intent, toks, slots):
    items = []
    for item in intent["pattern"]:
        if not item.startswith("@"):
            items.append(item)
            continue
        key = "headNod" if item.removesuffix("?") == "@nod" else "headShake"
        if not any(t.get("nonManual", {}).get(key, 0) > 0.5 for t in toks) and not item.endswith("?"):
            return None
    m, n = len(items), len(toks)
    dp = [[None] * (n + 1) for _ in range(m + 1)]
    dp[0][0] = (0.0, 0)
    for i in range(m + 1):
        for j in range(n + 1):
            if dp[i][j] is None:
                continue
            s, used = dp[i][j]

            def offer(ii, jj, v, u):
                if dp[ii][jj] is None or v > dp[ii][jj][0]:
                    dp[ii][jj] = (v, u)
            if j < n:
                offer(i, j + 1, s + INSERT, used)
            if i < m:
                offer(i + 1, j, s + (0.0 if items[i].endswith("?") else DELETE), used)
                if j < n:
                    p = fit(items[i], toks[j], slots)
                    if p is not None:
                        offer(i + 1, j + 1, s + math.log(max(p, 1e-4)), used + 1)
    end = dp[m][n]
    if end is None:
        return None
    if any(not it.endswith("?") for it in items) and end[1] == 0:
        return None
    shake = not any(it.startswith("@shake") for it in intent["pattern"]) and any(t.get("nonManual", {}).get("headShake", 0) > 0.5 for t in toks)
    return end[0] + (UNEXPLAINED_SHAKE if shake else 0.0)


def match(intents, toks, question, slots):
    answering = sum(question is not None and question in it["questions"] for it in intents)
    best, top = None, -math.inf
    for it in intents:
        prior = (1 / len(intents) if answering == 0 else ON_QUESTION / answering if question in it["questions"]
                 else (1 - ON_QUESTION) / (len(intents) - answering))
        s = align(it, toks, slots)
        if s is not None and s + math.log(prior) > top:
            best, top = it["id"], s + math.log(prior)
    return best or "none"


def main(path, tagger=None):
    phrase = json.loads((CONTENT / "intents.json").read_text(encoding="utf-8"))
    rows = [json.loads(l) for l in open(path, encoding="utf-8")]
    right = [match(phrase["intents"], r["tokens"], r["question"], phrase["slots"]) == r["intent"] for r in rows]
    print(f"JOB phrasebook_eval n={len(rows)} intent={sum(right) / len(rows):.3f}")
    if tagger:
        import numpy as np
        from exp import mcnemar
        b, a = np.array(right), np.array(json.loads((pathlib.Path(tagger) / "val_right.json").read_text()))
        unseen = np.array([bool(r.get("unseen")) for r in rows])
        for name, sel in (("all", np.ones(len(rows), bool)), ("seen", ~unseen), ("unseen", unseen)):
            x, y = a[sel], b[sel]
            print(f"  {name}: n={sel.sum()} tagger {x.mean():.3f} phrasebook {y.mean():.3f} "
                  f"tagger-only {int((x & ~y).sum())} phrasebook-only {int((y & ~x).sum())} p={mcnemar(x, y):.3g}")


if __name__ == "__main__":
    a = sys.argv[1:]
    t = a[a.index("--tagger") + 1] if "--tagger" in a else None
    pos = [x for x in a if x != "--tagger" and x != t]
    main(pos[0] if pos else DATA / "tagger" / "val.jsonl", t)
