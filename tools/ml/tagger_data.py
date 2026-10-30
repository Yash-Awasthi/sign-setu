"""Training data for the tagger (intent + slot per token): answers built from the phrasebook patterns, each sign token
given a candidate list the shipped reader really produced for that word on a held-out signer, so the noise is real.

    python tagger_data.py bank OUT_DIR      # reader candidate lists per word from held-out clips, and confusion.json
    python tagger_data.py make OUT_DIR [--per 400] [--seed 7]

bank: validation-signer clips only, their signers split in two groups (one feeds train.jsonl, the other val.jsonl), so
no signer's readings are in both; test signers stay closed.
make: per intent and question (or none), --per answers; optional items dropped half the time, a required sign missed
5% of the time, an extra sign inserted 15% of the time, a head gesture item sets every token's nonManual. Each line:
question, tokens (GlossToken fields), intent, tags (slot or pattern item per token, "O" for an extra sign), shape
and unseen. An answer's shape is the items it signs in order, with the word chosen for an "a|b" item; a fifth of the
shapes (by hash) never reach train.jsonl or tune.jsonl, so val.jsonl reports wordings the tagger never saw.
tune.jsonl (train signers' readings, another seed) picks epochs; val.jsonl only reports.
"""
import hashlib
import json
import pathlib
import random
import re
import sys

import numpy as np
import pandas as pd
import torch

from cache import frames_of
from exp import app_model
from features import resample_index
from guard import DATA

CONTENT = pathlib.Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "assets" / "content"
CACHE = DATA / "cache_v6b"
CANDIDATES = 10  # ReadPolicy.kt
DROP_OPTIONAL, MISS, INSERT = 0.5, 0.05, 0.15


@torch.no_grad()
def bank(out):
    m, labels, frames = app_model()
    m.eval()
    temp = json.loads((CONTENT / "models" / "sign_reader.json").read_text(encoding="utf-8"))["temperature"]
    index = pd.read_csv(CACHE / "index.csv", keep_default_na=False)
    raw = frames_of(CACHE, index)
    out = pathlib.Path(out)
    out.mkdir(parents=True, exist_ok=True)
    pool = index[(index["split"] == "val") & index["label"].isin(labels) & (index["source"] != "nonsign")]
    signers = sorted(set(pool["signer"]))
    random.Random(7).shuffle(signers)
    half = set(signers[: len(signers) // 2])
    for part, mine in (("train", True), ("val", False)):
        rows = pool.index[pool["signer"].isin(half) == mine].to_numpy()
        lists, confusion = {}, {}
        for b in range(0, len(rows), 256):
            rb = rows[b:b + 256]
            x = np.stack([raw(r)[s + resample_index(n, frames)] for r, s, n in
                          zip(rb, index["start"].to_numpy()[rb], index["length"].to_numpy()[rb])])
            p = torch.softmax(m(torch.from_numpy(x), torch.from_numpy(index["aspect"].to_numpy(np.float32)[rb])[:, None]) / temp, 1)
            top = p.topk(CANDIDATES)
            for r, v, i in zip(rb, top.values.tolist(), top.indices.tolist()):
                w = index.at[r, "label"]
                lists.setdefault(w, []).append([[labels[k], round(q, 4)] for k, q in zip(i, v)])
                c = confusion.setdefault(w, {})
                c[labels[i[0]]] = c.get(labels[i[0]], 0) + 1
        (out / f"bank_{part}.json").write_text(json.dumps(lists), encoding="utf-8")
        (out / f"confusion_{part}.json").write_text(json.dumps(confusion, indent=1), encoding="utf-8")
        right = sum(c.get(w, 0) for w, c in confusion.items()) / max(1, sum(map(sum, (c.values() for c in confusion.values()))))
        print(f"bank {part}: {len(rows)} clips, {len(set(pool.loc[rows, 'signer']))} signers, {len(lists)} words, top-1 {right:.3f}")


def make(out, per=400, seed=7):
    out = pathlib.Path(out)
    phrase = json.loads((CONTENT / "intents.json").read_text(encoding="utf-8"))
    slots = phrase["slots"]
    labels = json.loads((CONTENT / "models" / "sign_reader.json").read_text(encoding="utf-8"))["labels"]
    names = [l.split("\t")[0] for l in (CONTENT / "names.txt").read_text(encoding="utf-8").splitlines() if l and not l.startswith("#")]
    for part, bank_part, k in (("train", "train", 0), ("tune", "train", 2), ("val", "val", 1)):
        rng = random.Random(seed + k)
        lists = json.loads((out / f"bank_{bank_part}.json").read_text(encoding="utf-8"))

        def sign(word):
            # A word with no held-out reading keeps a clean one: the reader sure of it.
            got = lists.get(word)
            return ("SIGN", rng.choice(got) if got else [[word, round(rng.uniform(0.5, 1.0), 4)]])

        def item_token(item):
            name = item.removesuffix("?")
            if name == "$NAME":
                n = rng.choice(names)
                return ("SPELL", [[n, round(rng.uniform(0.4, 0.95), 4)]]), "NAME", "NAME"
            if name == "$NUM":
                return ("NUM", [[str(rng.randint(1, 99)), round(rng.uniform(0.4, 0.95), 4)]]), "NUM", "NUM"
            if name.startswith("$"):
                vals = [v for v in slots[name[1:]] if v in labels]
                return sign(rng.choice(vals)), name[1:], name[1:]
            w = rng.choice([w for w in name.split("|") if w in labels] or name.split("|"))
            return sign(w), name, w

        written, hidden = 0, 0
        with open(out / f"{part}.jsonl", "w", encoding="utf-8") as f:
            for intent in phrase["intents"]:
                for q in intent["questions"] + [None]:
                    for _ in range(per // (len(intent["questions"]) + 1) + 1):
                        toks, tags, head, shape = [], [], {}, []
                        for item in intent["pattern"]:
                            opt = item.endswith("?")
                            if item.startswith("@"):
                                if not opt or rng.random() > DROP_OPTIONAL:
                                    head["headNod" if "nod" in item else "headShake"] = 1.0
                                    shape.append(item.removesuffix("?"))
                                continue
                            if (opt and rng.random() < DROP_OPTIONAL) or (not opt and rng.random() < MISS):
                                continue
                            tok, tag, said = item_token(item)
                            shape.append(said)
                            toks.append(tok)
                            tags.append(tag)
                            if rng.random() < INSERT:
                                toks.append(sign(rng.choice(labels)))
                                tags.append("O")
                        if not toks:
                            continue
                        key = intent["id"] + ":" + " ".join(shape)
                        # An intent with one wording keeps it: holding it out would leave the intent untrained.
                        varied = any(i.endswith("?") or "|" in i for i in intent["pattern"])
                        unseen = varied and int(hashlib.sha1(key.encode()).hexdigest(), 16) % 5 == 0
                        if unseen and part != "val":
                            hidden += 1
                            continue
                        tokens = [{"id": i, "type": t, "candidates": [{"label": l, "p": p} for l, p in c],
                                   "nonManual": head} for i, (t, c) in enumerate(toks)]
                        f.write(json.dumps({"question": q, "tokens": tokens, "intent": intent["id"], "tags": tags, "shape": key,
                                            "unseen": unseen}, ensure_ascii=False) + "\n")
                        written += 1
        print(f"tagger {part}: {written} answers, {hidden} with unseen shapes left out, {len(phrase['intents'])} intents")


if __name__ == "__main__":
    a = sys.argv[1:]
    opt = lambda k, d: type(d)(a[a.index(k) + 1]) if k in a else d  # noqa: E731
    if a[0] == "bank":
        bank(a[1])
    else:
        make(a[1], opt("--per", 400), opt("--seed", 7))
