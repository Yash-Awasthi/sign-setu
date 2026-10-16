"""Writes the content review record: every gesture, phrase sentence and placeholder recording, ready for sign-off.

Regenerate after any content change, then commit the record with the change:
    python tools/content/review_record.py
"""
import collections
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "content"
OUT = ROOT / "docs" / "governance" / "forms" / "content-review-record.md"

# Gestures that carry no lexicon meaning of their own: their role on the stage.
ROLES = {
    "okay": "Acknowledgement after any answer: a nod with \"okay\"",
    "namaste": "Greeting on the home screen and at the start of a session",
    "wave": "Hello, with the language finder",
    "ask": "General asking pose (palms up, raised brows) for typed questions and pictures",
    "show_me": "\"Show me\"",
    "thumbs_up": "\"Good\"",
    "count": "Counting on the fingers (age question)",
    "sick.forehead": "Feeling unwell: hand to the forehead",
    "medicine.pill": "Medicine: taking a pill",
}


def load(name):
    return json.loads((ASSETS / name).read_text(encoding="utf-8"))


def cell(text):
    return text.replace("|", "\\|")


def main():
    gestures = [g["id"] for g in load("gestures.json")["gestures"]]
    lexicon = {e["id"]: e for e in load("lexicon.json")["entries"]}
    phrases = load("phrases.json")["phrases"]
    audio = collections.Counter(p.parent.name for p in (ASSETS / "audio").rglob("*.ogg"))

    out = ["# Content Review Record", "",
           "Sign-off sheet for the subject-facing content in this release, generated from the content files by "
           "`tools/content/review_record.py`. Reviewers follow the [content policy](../policies/content-policy.md); "
           "every item stays a candidate until it is approved here.", "",
           "Verdicts: **A** approved, **C** change needed (say what in the notes), **R** reject.", "",
           "## 1. Gestures", "",
           "Seen in the gesture gallery of a debug build, frozen at the moment of contact:", "",
           "```",
           "adb shell am start -n in.signsetu/app.signsetu.GalleryActivity --es only <ids, comma separated>",
           "adb shell am start -n in.signsetu/app.signsetu.GalleryActivity --es c adult --es only <ids>",
           "```", "",
           "Reviewers: child-welfare reviewer for every gesture; ISL reviewer as well for entries a Deaf subject may see.", "",
           "| Gesture | Meaning | How it is made | Child | Adult | Notes |",
           "| --- | --- | --- | --- | --- | --- |"]
    for gid in gestures:
        entry = lexicon.get(gid)
        meaning = entry["label"]["en"] if entry else ROLES.get(gid, "")
        how = entry["how"]["en"] if entry else ""
        out.append(f"| `{gid}` | {cell(meaning)} | {cell(how)} |  |  |  |")

    out += ["", "## 2. Phrase-bank sentences", "",
            "Each sentence is signed as the sequence shown, with its picture held up. Reviewers: language reviewer for "
            "each language, ISL reviewer for the sign sequence, child-welfare reviewer for the wording.", "",
            "| Sentence (English) | Sentence (Hindi) | Signs | English | Hindi | Signs | Notes |",
            "| --- | --- | --- | --- | --- | --- | --- |"]
    for p in phrases:
        for slot in p["slots"]:
            signs = ", ".join(f"`{slot['sign'] if s == '$' else s}`" for s in p["signs"])
            out.append(f"| {cell(slot['text'].get('en', ''))} | {cell(slot['text'].get('hi', ''))} | {signs} |  |  |  |  |")

    out += ["", "## 3. Placeholder recordings", "",
            "Every file under `assets/content/audio/` is machine-generated placeholder speech until a fluent speaker "
            "records it again or approves it. The language reviewer listens to each file in the folder for their language.", "",
            "| Language folder | Files | Approved by | Notes |",
            "| --- | --- | --- | --- |"]
    for lang, count in sorted(audio.items()):
        note = "One greeting per language-finder language; each goes to that language's reviewer" if lang == "greet" else ""
        out.append(f"| `{lang}` | {count} |  | {note} |")

    out += ["", "## 4. Sign-off", "",
            "| Role | Name | Organisation | Signature | Date |",
            "| --- | --- | --- | --- | --- |",
            "| Child-welfare reviewer |  |  |  |  |",
            "| ISL reviewer |  |  |  |  |",
            "| Language reviewer (Hindi) |  |  |  |  |",
            "| Language reviewer (English) |  |  |  |  |",
            "| Content owner |  |  |  |  |", ""]
    OUT.write_text("\n".join(out), encoding="utf-8", newline="\n")
    print(f"{OUT.relative_to(ROOT)}: {len(gestures)} gestures, "
          f"{sum(len(p['slots']) for p in phrases)} sentences, {sum(audio.values())} recordings")


if __name__ == "__main__":
    main()
