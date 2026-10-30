"""Research tagger (not in the app): a small encoder reads a gloss lattice and the question on screen and names the
intent and, per token, the pattern item or slot it fills ("O" for an extra sign). A ranker beside the phrasebook
engine, never a writer. Data: tagger_data.py; training answers also get the reader's confusions from
sign_confusion.json, since their sign tokens come from a signer the reader nearly always reads right.

    python tagger.py OUT_DIR [--epochs 30] [--seed 1]

The epoch is picked on tune.jsonl; val.jsonl is read once at the end, split into answer shapes seen in training and
shapes held out of it. Writes OUT_DIR/tagger.pt, spec.json ("enabled": false: nothing reads it until a real-signer
gate passes), metrics.json and val_right.json (intent right or not per val answer, for phrasebook_eval.py).
"""
import json
import pathlib
import random
import sys

import torch
from torch import nn

from guard import DATA

CONTENT = pathlib.Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "assets" / "content"
TYPES = ["SIGN", "SPELL", "NUM", "POINT", "UNKNOWN"]
CONFUSE = 0.5  # share of training sign tokens whose first guess is redrawn from the confusion table
NONE_SHARE = 0.1  # answers of random signs that match no intent
MAX_TOK = 12


class Tagger(nn.Module):
    def __init__(self, words, intents, tags, questions, d=128, layers=2):
        super().__init__()
        self.word = nn.Embedding(words + 1, d)  # the last row: a spelled or numeric token's text, not a word
        self.kind = nn.Embedding(len(TYPES), d)
        self.head_moves = nn.Linear(4, d)
        self.pos = nn.Embedding(MAX_TOK + 1, d)
        self.question = nn.Embedding(questions + 1, d)
        self.enc = nn.TransformerEncoder(nn.TransformerEncoderLayer(d, 4, 4 * d, 0.1, batch_first=True), layers)
        self.intent = nn.Linear(d, intents)
        self.tag = nn.Linear(d, tags)

    def forward(self, ids, probs, types, nm, q, mask):
        # A token is the probability-weighted sum of its candidates' word vectors.
        tok = (self.word(ids) * probs[..., None]).sum(2) + self.kind(types) + self.head_moves(nm)
        x = torch.cat([self.question(q)[:, None], tok], 1) + self.pos.weight[: tok.shape[1] + 1]
        pad = torch.cat([torch.zeros_like(mask[:, :1]), ~mask], 1)
        h = self.enc(x, src_key_padding_mask=pad)
        return self.intent(h[:, 0]), self.tag(h[:, 1:])


def load(path):
    return [json.loads(l) for l in open(path, encoding="utf-8")]


def confuse(ans, table, labels, rng):
    for t in ans["tokens"]:
        if t["type"] == "SIGN" and rng.random() < CONFUSE and t["candidates"][0]["label"] in table:
            row = table[t["candidates"][0]["label"]]
            pick = rng.choices(list(row), weights=list(row.values()))[0]
            p = rng.uniform(0.2, 0.7)
            rest = [c for c in t["candidates"] if c["label"] != pick][:9]
            s = sum(c["p"] for c in rest) or 1.0
            t["candidates"] = [{"label": pick, "p": p}] + [{"label": c["label"], "p": c["p"] / s * (1 - p)} for c in rest]
    return ans


def none_answer(labels, rng):
    n = rng.randint(1, 4)
    toks = [{"type": "SIGN", "candidates": [{"label": rng.choice(labels), "p": rng.uniform(0.2, 0.9)}], "nonManual": {}} for _ in range(n)]
    return {"question": None, "tokens": toks, "intent": "none", "tags": ["O"] * n}


def encode(batch, word, intent, tag, qid):
    n = len(batch)
    ids = torch.full((n, MAX_TOK, 10), len(word), dtype=torch.long)
    probs = torch.zeros(n, MAX_TOK, 10)
    types = torch.zeros(n, MAX_TOK, dtype=torch.long)
    nm = torch.zeros(n, MAX_TOK, 4)
    mask = torch.zeros(n, MAX_TOK, dtype=torch.bool)
    y_tag = torch.full((n, MAX_TOK), -100, dtype=torch.long)
    for i, a in enumerate(batch):
        for j, t in enumerate(a["tokens"][:MAX_TOK]):
            for k, c in enumerate(t["candidates"][:10]):
                ids[i, j, k] = word.get(c["label"], len(word))
                probs[i, j, k] = c["p"]
            types[i, j] = TYPES.index(t["type"])
            m = t.get("nonManual", {})
            nm[i, j] = torch.tensor([m.get(k, 0.0) for k in ("browsRaised", "headShake", "headNod", "headTilt")])
            mask[i, j] = True
            y_tag[i, j] = tag[a["tags"][j]]
    q = torch.tensor([qid.get(a["question"], len(qid)) for a in batch])
    y_int = torch.tensor([intent[a["intent"]] for a in batch])
    return (ids, probs, types, nm, q, mask), y_int, y_tag


def main(out, epochs=30, seed=1):
    torch.manual_seed(seed)
    rng = random.Random(seed)
    src = DATA / "tagger"
    train, tune, val = load(src / "train.jsonl"), load(src / "tune.jsonl"), load(src / "val.jsonl")
    labels = json.loads((CONTENT / "models" / "sign_reader.json").read_text(encoding="utf-8"))["labels"]
    table = json.loads((CONTENT / "models" / "sign_confusion.json").read_text(encoding="utf-8"))["counts"]
    phrase = json.loads((CONTENT / "intents.json").read_text(encoding="utf-8"))
    questions = [q["id"] for q in json.loads((CONTENT / "triage.json").read_text(encoding="utf-8"))["questions"]]
    word = {w: i for i, w in enumerate(labels)}
    intent = {k: i for i, k in enumerate([x["id"] for x in phrase["intents"]] + ["none"])}
    tag = {k: i for i, k in enumerate(sorted({t for a in train + tune + val for t in a["tags"]} | {"O"}))}
    qid = {k: i for i, k in enumerate(questions)}
    tune = tune + [none_answer(labels, random.Random(98)) for _ in range(int(NONE_SHARE * len(tune)))]
    n_val = len(val)
    val = val + [none_answer(labels, random.Random(99)) for _ in range(int(NONE_SHARE * len(val)))]
    dev = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    if dev.type == "cuda":
        torch.cuda.set_per_process_memory_fraction(0.2)
    model = Tagger(len(word), len(intent), len(tag), len(qid)).to(dev)
    opt = torch.optim.AdamW(model.parameters(), 1e-3, weight_decay=0.01)
    ce = nn.CrossEntropyLoss(ignore_index=-100)
    tx, ti, tt = encode(tune, word, intent, tag, qid)
    best, out = None, pathlib.Path(out)
    out.mkdir(parents=True, exist_ok=True)
    for ep in range(epochs):
        model.train()
        data = [confuse(json.loads(json.dumps(a)), table, labels, rng) for a in train]
        data += [none_answer(labels, rng) for _ in range(int(NONE_SHARE * len(train)))]
        rng.shuffle(data)
        for b in range(0, len(data), 256):
            x, yi, yt = encode(data[b:b + 256], word, intent, tag, qid)
            li, lt = model(*[t.to(dev) for t in x])
            loss = ce(li, yi.to(dev)) + ce(lt.reshape(-1, lt.shape[-1]), yt.to(dev).reshape(-1))
            opt.zero_grad()
            loss.backward()
            opt.step()
        m = evaluate(model, tx, ti, tt, tag)
        print(f"epoch {ep + 1}: loss {loss.item():.3f} tune intent {m['intent']:.3f} slot {m['slot']:.3f} exact {m['exact']:.3f}", flush=True)
        if best is None or m["exact"] > best["exact"]:
            best = {**m, "epoch": ep + 1}
            torch.save(model.state_dict(), out / "tagger.pt")  # nosemgrep: trailofbits.python.pickles-in-pytorch.pickles-in-pytorch
    (out / "spec.json").write_text(json.dumps({"enabled": False, "intents": list(intent), "tags": list(tag), "questions": questions,
                                               "words": labels, "max_tokens": MAX_TOK}, indent=1), encoding="utf-8")
    model.load_state_dict(torch.load(out / "tagger.pt", weights_only=True))  # nosemgrep: trailofbits.python.pickles-in-pytorch.pickles-in-pytorch
    vx, vi, vt = encode(val, word, intent, tag, qid)
    unseen = torch.tensor([bool(a.get("unseen")) for a in val])
    real = torch.arange(len(val)) < n_val
    report = {"tune": best}
    for name, sel in (("val_all", real), ("val_seen", real & ~unseen), ("val_unseen", real & unseen), ("val_none", ~real)):
        report[name] = evaluate(model, vx, vi, vt, tag, sel)
    (out / "metrics.json").write_text(json.dumps(report, indent=1), encoding="utf-8")
    (out / "val_right.json").write_text(json.dumps(right(model, vx, vi)[:n_val]), encoding="utf-8")
    r = report
    print(f"JOB tagger ok=1 best_epoch={best['epoch']} val_intent={r['val_all']['intent']:.3f} seen={r['val_seen']['intent']:.3f} "
          f"unseen={r['val_unseen']['intent']:.3f} slot={r['val_all']['slot']:.3f} slot_unseen={r['val_unseen']['slot']:.3f} "
          f"exact={r['val_all']['exact']:.3f} exact_unseen={r['val_unseen']['exact']:.3f} none_recall={r['val_none']['intent']:.3f} status=done")


@torch.no_grad()
def predict(model, vx):
    model.eval()
    dev = next(model.parameters()).device
    outs = [model(*[t[b:b + 1024].to(dev) for t in vx]) for b in range(0, len(vx[0]), 1024)]
    return torch.cat([li.argmax(1).cpu() for li, _ in outs]), torch.cat([lt.argmax(2).cpu() for _, lt in outs])


def right(model, vx, vi):
    return (predict(model, vx)[0] == vi).tolist()


def evaluate(model, vx, vi, vt, tag, sel=None):
    pi, pt = predict(model, vx)
    if sel is not None:
        pi, pt, vi, vt = pi[sel], pt[sel], vi[sel], vt[sel]
    on = vt != -100
    slot_tags = torch.tensor([k.isupper() and k != "O" for k in tag])
    slot_tok = on & slot_tags[vt.clamp(min=0)]
    tok_ok = ((pt == vt) | ~on).all(1)
    return {"intent": float((pi == vi).float().mean()), "slot": float((pt[slot_tok] == vt[slot_tok]).float().mean()),
            "tags": float((pt[on] == vt[on]).float().mean()), "exact": float(((pi == vi) & tok_ok).float().mean()),
            "n": int(len(vi))}


if __name__ == "__main__":
    a = sys.argv[1:]
    opt = lambda k, d: type(d)(a[a.index(k) + 1]) if k in a else d  # noqa: E731
    main(a[0], opt("--epochs", 30), opt("--seed", 1))
