"""The next interview step, from the items the phone allows and the person's last answer.

Two ways, by the size of the model. "words": a small model is poor at picking an item from a list but good at naming
which word of a closed list an answer means ("thirsty" is water, "mummy" is mother); the word must be one of the items'
own cue words, and the item is the first one in the phone's order that carries it. "write": a large model picks an item
or writes one short welfare sentence of its own; the phone checks that sentence (no sensitive or leading question,
every word signable) before anything reaches the person. Anything else is no pick, and the phone keeps the bank's order.

The person answers in their own language. When the station's translator covers it, the last answer is put into English
here before the model reads it, and a written sentence is put back into the person's language as its caption; the
phone never sees the English. Without a translator the model reads the answer as given.
"""
import json
import re

# The phone sends the same instructions to its own model and to the gateway (app: ui/session/Planner.kt).
PROMPT = (
    "A frightened child at a police station gave an answer. Which one word from the list is closest to what the "
    "answer says they need or feel? If none is close, reply none. Reply with JSON only: {\"word\": \"<one word from the list or none>\"}"
)


# The phone sends the same instructions to the gateway (app: ui/session/Planner.kt, PLAN_WRITE_PROMPT).
WRITE_PROMPT = (
    "You plan the next step of a welfare conversation between a police officer and a person who may be a frightened child. "
    "They answer with pictures, yes or no, writing or sign language. The conversation so far is a JSON array of turns: "
    "\"officer\" is what was shown to them, \"user\" is their answer; a signed answer is a machine's unconfirmed reading. "
    "Choose one: an item from the list, when one fits what they just showed; or one short new sentence about their comfort or needs "
    "(water, food, toilet, rest, cold, feeling unwell, a trusted person, a break). A new sentence has at most 10 simple everyday words "
    "and is never about what happened, violence, the body, touching, secrets or who did something; never asks why; never suggests "
    "a person, place, act or detail they have not given; never asks for names, numbers or addresses; never promises anything. "
    "Reply with JSON only: {\"id\": \"<item id>\", \"why\": \"<at most 12 words>\"} or {\"say\": \"<sentence>\", \"why\": \"<at most 12 words>\"}"
)
MAX_SAY_WORDS = 14
MAX_WHY = 120
NONE = {"id": None, "say": None, "why": None, "word": None, "caption": None}


def last_exchange(turns: list[dict]) -> tuple[str, str]:
    """The last thing shown to the person and everything they gave after it."""
    asked, answer = "", []
    for t in turns:
        if t["role"] == "user":
            answer.append(t["text"])
        else:
            asked, answer = t["text"], []
    return asked, " ".join(a for a in answer if a).strip()


def word_of(reply: str) -> str | None:
    m = re.search(r"\{.*\}", reply, re.S)
    try:
        w = json.loads(m.group(0)).get("word") if m else None
    except ValueError:
        return None
    return w.strip().lower() if isinstance(w, str) else None


def user_text(words: list[str], asked: str, answer: str) -> str:
    return f"List: {', '.join(words)}\nQuestion shown: {asked}\nAnswer: {answer}"


def cue_in(text: str, items: list[dict]) -> tuple[str, str] | None:
    """The first item, in the phone's order, whose cue word [text] says outright."""
    low = text.lower()
    for i in items:
        for c in i["cues"]:
            if re.search(r"(?<!\w)" + re.escape(c.lower()) + r"(?!\w)", low):
                return i["id"], c.lower()
    return None


def write(llm, turns: list[dict], items: list[dict], english: str | None = None) -> dict:
    listing = "\n".join(f"{i['id']}: {i['text']}" for i in items)
    extra = f"\nTheir last answer in English: {english}" if english else ""
    reply = llm.chat([{"role": "system", "content": WRITE_PROMPT},
                      {"role": "user", "content": f"Conversation: {json.dumps(turns, ensure_ascii=False)}\nItems:\n{listing}{extra}"}],
                     temperature=0.0, max_tokens=120, fmt="json")
    m = re.search(r"\{.*\}", reply, re.S)
    try:
        o = json.loads(m.group(0)) if m else {}
    except ValueError:
        return NONE
    why = " ".join(str(o.get("why") or "").split())[:MAX_WHY]
    pick, say = o.get("id"), o.get("say")
    if isinstance(pick, str) and pick.strip() in {i["id"] for i in items}:
        return {**NONE, "id": pick.strip(), "why": why}
    # The phone checks a sentence fully; this only drops what is plainly not one short line.
    if isinstance(say, str) and say.strip() and "\n" not in say.strip() and len(say.split()) <= MAX_SAY_WORDS:
        return {**NONE, "say": " ".join(say.split()), "why": why}
    return NONE


def plan(llm, turns: list[dict], items: list[dict], mode: str = "words", lang: str = "en", translate=None) -> dict:
    """[translate](text, src, tgt) gives the text in [tgt], or None when this station cannot."""
    asked, answer = last_exchange(turns)
    english = translate(answer, lang, "en") if translate and answer and lang != "en" else None
    if english:
        hit = cue_in(english, items)
        if hit:
            return {**NONE, "id": hit[0], "why": f"answer fits {hit[1]}", "word": hit[1]}
    if mode == "write":
        out = write(llm, turns, items, english)
        if out["say"] and translate and lang != "en":
            out["caption"] = translate(out["say"], "en", lang)
        return out
    words = list(dict.fromkeys(c.lower() for i in items for c in i["cues"]))
    # A small model reads Indian languages too poorly to trust ("प्यास", thirst, came back as food): English only.
    said = english if lang != "en" else answer
    if not said or not words:
        return NONE
    reply = llm.chat([{"role": "system", "content": PROMPT}, {"role": "user", "content": user_text(words, asked, said)}],
                     temperature=0.0, max_tokens=24, fmt="json")
    word = word_of(reply)
    pick = next((i["id"] for i in items if word in (c.lower() for c in i["cues"])), None)
    return {**NONE, "id": pick, "why": f"answer fits {word}" if pick else None, "word": word if pick else None}
