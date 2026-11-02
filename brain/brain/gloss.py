"""Sentence to draft ISL gloss: the words in signing order, limited to signs the phone can play.

Rules first, so the result is the same with or without the language model: time words first, then topic and
comment as said, negation after the verb, the question word last; articles, copulas and most function words drop.
The language model, when Ollama serves one, may only reorder and replace words with others from the vocabulary;
any word it invents is dropped again, and a reply that fails the check falls back to the rules.
"""
import json
import re

TIME = {"today", "tomorrow", "yesterday", "now", "morning", "evening", "night", "afternoon", "later", "before", "after", "week", "month", "year", "day"}
QUESTION = {"what", "where", "when", "who", "why", "how", "which", "whose", "how-many", "how-much"}
NEGATION = {"not", "no", "never", "nothing", "none"}
DROP = {"a", "an", "the", "is", "am", "are", "was", "were", "be", "been", "being", "do", "does", "did", "to", "of",
        "will", "shall", "would", "should", "can", "could", "please", "very", "just", "that", "this", "it", "there"}
PAIRS = {("how", "many"): "how-many", ("how", "much"): "how-much", ("do", "not"): "not", ("don't",): "not",
         ("didn't",): "not", ("doesn't",): "not", ("can't",): "not", ("won't",): "not", ("isn't",): "not", ("aren't",): "not"}


def tokens(text: str) -> list[str]:
    words = re.findall(r"[a-z]+(?:'[a-z]+)?", text.lower())
    out, i = [], 0
    while i < len(words):
        for size in (2, 1):
            key = tuple(words[i:i + size])
            if key in PAIRS:
                out.append(PAIRS[key])
                i += size
                break
        else:
            out.append(words[i])
            i += 1
    return out


def by_rules(text: str, vocabulary: set[str] | None = None) -> list[str]:
    ws = [w for w in tokens(text) if w not in DROP]
    time = [w for w in ws if w in TIME]
    question = [w for w in ws if w in QUESTION]
    negation = [w for w in ws if w in NEGATION]
    rest = [w for w in ws if w not in TIME and w not in QUESTION and w not in NEGATION]
    gloss = time + rest + negation[:1] + question[:1]
    if vocabulary:
        # A word with no sign stays only when written as a name (capitalised, not first), for fingerspelling.
        names = {w.lower() for w in re.findall(r"(?<=\s)[A-Z][a-z]+", text)}
        gloss = [w for w in gloss if w in vocabulary or w in names]
    return gloss


PROMPT = """You turn English sentences into Indian Sign Language gloss for a signing character.
Rules: time words first; then the topic and the comment; negation after the verb; a question word last.
Drop articles, "be" verbs and helper verbs. Use only words from VOCABULARY; if a needed word is missing, use the
closest word from VOCABULARY or leave it out. Reply with a JSON list of lower-case words and nothing else.
VOCABULARY: {vocab}
SENTENCE: {text}"""


def by_model(llm, text: str, vocabulary: set[str]) -> list[str] | None:
    try:
        reply = llm.chat([{"role": "user", "content": PROMPT.format(vocab=", ".join(sorted(vocabulary)[:1500]), text=text)}], temperature=0.0, max_tokens=80)
    except Exception:  # an unreachable or failing model is the rules' case
        return None
    m = re.search(r"\[.*?\]", reply, re.S)
    if not m:
        return None
    try:
        words = [str(w).lower().strip() for w in json.loads(m.group(0))]
    except (json.JSONDecodeError, TypeError):
        return None
    kept = [w for w in words if w in vocabulary]
    return kept or None


def expand(text: str, vocabulary: list[str] | None, llm=None) -> dict:
    vocab = {v.lower() for v in vocabulary or []}
    rules = by_rules(text, vocab or None)
    if llm is not None and vocab:
        model = by_model(llm, text, vocab)
        if model:
            return {"gloss": model, "method": "model", "rules": rules}
    return {"gloss": rules, "method": "rules"}
