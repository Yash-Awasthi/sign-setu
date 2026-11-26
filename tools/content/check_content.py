"""Checks the bundle's references, unique ids, card images, 17-language labels, bundle identity and
the GGUF allow-list; exits 1 with the errors."""
import hashlib
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "content"
SYMBOLS = ROOT / "content" / "symbols"
LOCAL_MODEL = ROOT / "app" / "src" / "main" / "java" / "app" / "signsetu" / "assistant" / "LocalModel.kt"
ASSISTANT_DOC = ROOT / "docs" / "10-on-device-assistant.md"
SENTENCE_DOC = ROOT / "docs" / "15-gloss-to-text.md"
HASH = re.compile(r"^[0-9a-f]{64}$")
# Languages with no recording yet (build_audio.py's UNVOICED): the finder lights their tile silently.
SILENT = {"gon", "hlb"}


def load(assets: pathlib.Path, name: str) -> dict:
    return json.loads((assets / name).read_text(encoding="utf-8"))


def check_bundle_identity(assets: pathlib.Path = ASSETS, symbols: pathlib.Path = SYMBOLS) -> list[str]:
    """The shipped bundle against its sources: every card has a symbol, every symbol a card, hashes current."""
    errors: list[str] = []
    mapping = json.loads((symbols / "map.json").read_text(encoding="utf-8"))
    ids = [card["id"] for card in load(assets, "cards.json")["cards"]]
    for card in ids:
        if card not in mapping:
            errors.append(f"card {card}: no entry in content/symbols/map.json")
        elif not (symbols / mapping[card]).is_file():
            errors.append(f"card {card}: map.json names content/symbols/{mapping[card]}, which is absent")
    referenced = set(mapping.values())
    for svg in sorted(symbols.rglob("*.svg")):
        name = svg.relative_to(symbols).as_posix()
        if name not in referenced:
            errors.append(f"content/symbols/{name}: orphan, no card in map.json uses it")

    files = load(assets, "manifest.json")["files"]
    on_disk = {p.relative_to(assets).as_posix() for p in assets.rglob("*")
               if p.is_file() and p.relative_to(assets).as_posix() != "manifest.json"}
    for name, expected in sorted(files.items()):
        path = assets / name
        if not path.is_file():
            errors.append(f"manifest.json: lists {name}, which is absent from the bundle")
            continue
        actual = hashlib.sha256(path.read_bytes()).hexdigest()
        if actual != expected:
            errors.append(f"{name}: sha256 {actual}, manifest.json records {expected}")
    for name in sorted(on_disk - set(files)):
        errors.append(f"{name}: present in the bundle, not listed in manifest.json")
    return errors


def kotlin_hashes(source: str, name: str) -> list[str] | None:
    """The string literals inside `val <name> = setOf(...)`, or None when the set is not declared."""
    lines = source.splitlines()
    for start, line in enumerate(lines):
        if not re.search(rf"\b{name}\s*=\s*setOf\s*\(", line.split("//")[0]):
            continue
        depth, body = 0, []
        for part in (current.split("//")[0] for current in lines[start:]):
            body.append(part)
            depth += part.count("(") - part.count(")")
            if depth <= 0:
                return re.findall(r'"([^"]*)"', "\n".join(body))
        return None
    return None


def check_allow_list(model: pathlib.Path = LOCAL_MODEL,
                     docs: dict[str, pathlib.Path] | None = None) -> list[str]:
    """Each allowed GGUF hash is a well-formed SHA-256, unique, and stated in the doc that ships it."""
    errors: list[str] = []
    docs = docs or {"KNOWN_MODELS": ASSISTANT_DOC, "KNOWN_SENTENCE_MODELS": SENTENCE_DOC}
    source = model.read_text(encoding="utf-8")
    seen: dict[str, str] = {}
    for name, doc in docs.items():
        hashes = kotlin_hashes(source, name)
        if hashes is None:
            errors.append(f"LocalModel.kt: no {name} = setOf(...) declaration")
            continue
        if not hashes:
            errors.append(f"LocalModel.kt: {name} is empty, so no model could be imported")
        text = doc.read_text(encoding="utf-8")
        for value in hashes:
            if not HASH.match(value):
                errors.append(f"LocalModel.kt: {name} hash {value!r} is not 64 lowercase hex characters")
                continue
            if value in seen:
                errors.append(f"LocalModel.kt: hash {value} appears in both {seen[value]} and {name}")
            else:
                seen[value] = name
            if value not in text:
                errors.append(f"LocalModel.kt: {name} hash {value} is not stated in {doc.name}")
    return errors


def check(assets: pathlib.Path = ASSETS, symbols: pathlib.Path = SYMBOLS,
          model: pathlib.Path = LOCAL_MODEL, docs: dict[str, pathlib.Path] | None = None) -> list[str]:
    errors: list[str] = []

    cards_file = load(assets, "cards.json")
    triage = load(assets, "triage.json")
    words_file = load(assets, "words.json")
    phrases_file = load(assets, "phrases.json")
    languages = [entry["code"] for entry in load(assets, "languages.json")["languages"]]
    subject_ui = load(assets, "subject_ui.json")
    motion = set()
    for name in ("gestures.json", "signs.json", "isl_signs.json"):
        motion |= {g["id"] for g in load(assets, name)["gestures"]}

    def unique(where: str, ids: list[str]) -> set[str]:
        seen, duplicated = set(), set()
        for i in ids:
            if i in seen:
                duplicated.add(i)
            seen.add(i)
        if duplicated:
            errors.append(f"{where}: duplicate ids {sorted(duplicated)}")
        return seen

    cards = unique("cards.json", [c["id"] for c in cards_file["cards"]])
    groups = unique("triage.json groups", [g["id"] for g in triage["groups"]])
    questions = unique("triage.json questions", [q["id"] for q in triage["questions"]])
    unique("words.json", [w["w"][0] for w in words_file["words"] if w.get("w")])
    seen_intents: set[str] = set()
    unique("isl_signs.json", [g["id"] for g in load(assets, "isl_signs.json")["gestures"]])

    def label(where: str, values, allowed: set[str]) -> None:
        missing = sorted(allowed - set(values))
        extra = sorted(set(values) - allowed)
        blank = sorted(k for k, v in values.items() if not str(v).strip())
        if missing:
            errors.append(f"{where}: missing languages {missing}")
        if extra:
            errors.append(f"{where}: unknown languages {extra}")
        if blank:
            errors.append(f"{where}: blank labels {blank}")

    allowed = set(languages)
    for card in cards_file["cards"]:
        label(f"card {card['id']}", card.get("label", {}), allowed)
        if not (assets / "cards" / f"{card['id']}.webp").is_file():
            errors.append(f"card {card['id']}: missing cards/{card['id']}.webp")
    for category in cards_file["categories"]:
        label(f"card category {category['id']}", category.get("label", {}), allowed)
    for group in triage["groups"]:
        label(f"group {group['id']}", group.get("label", {}), allowed)
    for question in triage["questions"]:
        label(f"question {question['id']}", question.get("text", {}), allowed)
        if question.get("group") not in groups:
            errors.append(f"question {question['id']}: unknown group {question.get('group')!r}")
        for card in question.get("cards", []):
            if card not in cards:
                errors.append(f"question {question['id']}: unknown card {card!r}")
        for key in ("gesture", "lexicon"):
            if question.get(key) and question[key] not in motion:
                errors.append(f"question {question['id']}: unknown {key} {question[key]!r}")
        if question.get("after"):
            # "s2:yes" or "s2:any": the parent and the answer that must hold before this is asked.
            parts = str(question["after"]).split(":")
            if len(parts) != 2 or not all(parts):
                errors.append(f"question {question['id']}: malformed after {question['after']!r}")
            elif parts[0] not in questions:
                errors.append(f"question {question['id']}: unknown after question {parts[0]!r}")
    for phrase in phrases_file["phrases"]:
        label(f"phrase {phrase['id']}", phrase.get("label", {}), allowed)
        for sign in phrase.get("signs", []):
            if sign != "$" and sign not in motion:
                errors.append(f"phrase {phrase['id']}: unknown sign {sign!r} (use $ for the slot)")
        for slot in phrase.get("slots", []):
            label(f"phrase {phrase['id']} slot {slot.get('card', '?')}", slot.get("text", {}), allowed)
            if slot.get("card") and slot["card"] not in cards:
                errors.append(f"phrase {phrase['id']}: unknown slot card {slot['card']!r}")
            if slot.get("sign") and slot["sign"] not in motion:
                errors.append(f"phrase {phrase['id']}: unknown slot sign {slot['sign']!r}")
    for word in words_file["words"]:
        if not word.get("w"):
            errors.append("word entry has no written forms")
            continue
        name = word["w"][0]
        for key in ("sign", "gesture"):
            if word.get(key) and word[key] not in motion:
                errors.append(f"word {name!r}: unknown {key} {word[key]!r}")
        if word.get("card") and word["card"] not in cards:
            errors.append(f"word {name!r}: unknown card {word['card']!r}")
    # A screen label is either a full language map or an error: a value that is not a map, or a map
    # with no language the bundle knows, would otherwise pass without any language being checked.
    for key, values in subject_ui.items():
        if not isinstance(values, dict):
            errors.append(f"subject_ui {key}: expected a language map, got {type(values).__name__}")
        elif not set(values) & allowed:
            errors.append(f"subject_ui {key}: no known language, keys {sorted(values)}")
        else:
            label(f"subject_ui {key}", values, allowed)

    def coverage(where: str, values, required: set[str]) -> None:
        """Every required language is written, every key is one the bundle knows, none is blank."""
        missing = sorted(required - set(values))
        extra = sorted(set(values) - allowed)
        blank = sorted(k for k, v in values.items() if not str(v).strip())
        if missing:
            errors.append(f"{where}: missing languages {missing}")
        if extra:
            errors.append(f"{where}: unknown languages {extra}")
        if blank:
            errors.append(f"{where}: blank labels {blank}")

    def exact(where: str, values, fallback: set[str]) -> None:
        """A map is either complete or exactly the declared fallback set, so a half-written language in
        the middle of a file cannot hide behind a fallback that was meant for a whole set."""
        present = set(values)
        if present not in (allowed, allowed - fallback):
            errors.append(f"{where}: covers {len(present & allowed)} of {len(allowed)} languages; "
                          f"write all of them or exactly the declared fallback set")
        unknown = sorted(fallback - allowed)
        if unknown:
            errors.append(f"{where}: fallback names unknown languages {unknown}")

    # The lexicon and the place list are opened by the officer today, so a language the file declares
    # as a fallback may be absent; every other language must be written or the sheet shows English or an id.
    lexicon = load(assets, "lexicon.json")
    fallback = set(lexicon.get("fallback", []))
    for entry in lexicon["entries"]:
        coverage(f"lexicon {entry['id']}", entry.get("label", {}), allowed - fallback)
        exact(f"lexicon {entry['id']}", entry.get("label", {}), fallback)
        coverage(f"lexicon {entry['id']} how", entry.get("how", {}), allowed & {"en", "hi"})
        exact(f"lexicon {entry['id']} how", entry.get("how", {}), allowed - {"en", "hi"})
    places = load(assets, "places.json")
    place_fallback = set(places.get("fallback", []))
    for state in places["states"]:
        coverage(f"place state {state['id']}", state.get("name", {}), allowed - place_fallback)
        exact(f"place state {state['id']}", state.get("name", {}), place_fallback)
        for district in state.get("districts", []):
            coverage(f"place district {district['id']}", district.get("name", {}), allowed - place_fallback)
            exact(f"place district {district['id']}", district.get("name", {}), place_fallback)

    # A language with a voice has a greeting and a recording for every question; a silent one is declared
    # in build_audio.py's UNVOICED and carries no recording on purpose.
    for code in languages:
        if code in SILENT:
            continue
        if not (assets / "audio" / "greet" / f"{code}.ogg").is_file():
            errors.append(f"audio/greet/{code}.ogg: missing greeting recording")
        missing = [q["id"] for q in triage["questions"] if not (assets / "audio" / code / f"{q['id']}.ogg").is_file()]
        if missing:
            errors.append(f"audio/{code}: {len(missing)} question recording(s) missing, e.g. {missing[:3]}")
    # Every word an intent waits for must be one the sign reader can actually give (docs/15-gloss-to-text.md).
    if (assets / "intents.json").exists():
        intents = load(assets, "intents.json")
        readable = set(load(assets, "models/sign_reader.json")["labels"]) | set(load(assets, "sign_words.json")["words"].values())
        for intent in intents["intents"]:
            if intent["id"] in seen_intents:
                errors.append(f"intent {intent['id']}: duplicate id")
            seen_intents.add(intent["id"])
            for item in intent["pattern"]:
                if item.startswith("$") and item.rstrip("?")[1:] not in set(intents["slots"]) | {"NAME", "NUM"}:
                    errors.append(f"intent {intent['id']}: unknown slot {item}")
                elif not item.startswith(("$", "@")):
                    errors += [f"intent {intent['id']}: the reader cannot sign {w!r}" for w in item.rstrip("?").split("|") if w not in readable]
            errors += [f"intent {intent['id']}: unknown question {q}" for q in intent["questions"] if q not in questions]
        # Every question that asks for an answer has at least one intent to read the answer with (docs/15-gloss-to-text.md).
        answered = {q for intent in intents["intents"] for q in intent["questions"]}
        errors += [f"question {q['id']}: no intent answers it" for q in triage["questions"]
                   if q.get("kind", "QUESTION") != "STATEMENT" and q["id"] not in answered]
        for slot, entries in intents["slots"].items():
            errors += [f"slot {slot}: the reader cannot sign {w!r}" for w in entries if w not in readable]
    errors += check_bundle_identity(assets, symbols)
    errors += check_allow_list(model, docs)
    return errors


if __name__ == "__main__":
    problems = check()
    if problems:
        print(f"{len(problems)} content error(s):", file=sys.stderr)
        for problem in problems:
            print(f"  {problem}", file=sys.stderr)
        sys.exit(1)
    print("content references, ids, card images and 17-language labels are consistent; "
          "bundle identity and the GGUF allow-list are consistent")
