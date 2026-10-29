"""Writes assets/content/intents.json: the phrasebook that turns a gloss lattice into English and Hindi sentences
(docs/15-gloss-to-text.md). Edit this script, not the JSON. Every row is a draft until English, Hindi and
Deaf ISL review.

Pattern items: a word or w1|w2 the sign reader can output (as mapped by sign_words.json), $SLOT for a typed slot,
$NAME (fingerspelled), $NUM (spelled digits), @nod / @shake (head movement seen anywhere in the answer); a trailing ?
marks an item optional. "questions" are the triage.json ids the intent answers; the matcher favours those intents when
that question is on screen. In Hindi, "a/b" is a masculine/feminine pair chosen by the speaker's gender.
    python tools/content/build_intents.py
"""
import json
import pathlib

OUT = pathlib.Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "assets" / "content" / "intents.json"


def person(en, hi_my, hai="है", ref=None, hi_ne=None):
    """hi_my is "my X" with Hindi agreement; hai agrees with it (respect plural for elders); ref is the English
    reference; hi_ne is the doer with ने ("उसने" for he, not "वह ने")."""
    return {"en": en, "hi_my": hi_my, "hai": hai, "ref": ref or f"my {en}", "hi_ne": hi_ne or f"{hi_my} ने"}


SLOTS = {
    "PERSON": {
        "mother": person("mother", "मेरी माँ", "हैं"), "father": person("father", "मेरे पिता", "हैं"),
        "brother": person("brother", "मेरा भाई"), "sister": person("sister", "मेरी बहन"),
        "grandmother": person("grandmother", "मेरी दादी", "हैं"), "grandfather": person("grandfather", "मेरे दादा", "हैं"),
        "husband": person("husband", "मेरे पति", "हैं"), "wife": person("wife", "मेरी पत्नी", "हैं"),
        "son": person("son", "मेरा बेटा"), "daughter": person("daughter", "मेरी बेटी"),
        "uncle": person("uncle", "मेरे चाचा", "हैं"), "friend": person("friend", "मेरा दोस्त"),
        "neighbour": person("neighbour", "मेरा पड़ोसी"), "teacher": person("teacher", "मेरे शिक्षक", "हैं"),
        "man": person("man", "एक आदमी", ref="a man"), "woman": person("woman", "एक औरत", ref="a woman"),
        "boy": person("boy", "एक लड़का", ref="a boy"), "girl": person("girl", "एक लड़की", ref="a girl"),
        "police": person("police", "पुलिस", ref="the police"), "soldier": person("soldier", "एक सैनिक", ref="a soldier"),
        # From a sideways point (POINT tokens): who it was is shown, not named.
        "he": person("he", "वह", ref="he", hi_ne="उसने"), "she": person("she", "वह", ref="she", hi_ne="उसने"),
    },
    "PLACE": {k: {"en": e, "hi": h} for k, e, h in [
        ("house", "home", "घर"), ("school", "school", "स्कूल"), ("hospital", "hospital", "अस्पताल"),
        ("market", "market", "बाज़ार"), ("temple", "temple", "मंदिर"), ("park", "park", "पार्क"),
        ("office", "office", "दफ़्तर"), ("court", "court", "अदालत"), ("bank", "bank", "बैंक"), ("shop", "shop", "दुकान"),
        ("road", "road", "सड़क"), ("train station", "railway station", "रेलवे स्टेशन"),
        ("restaurant", "restaurant", "होटल"), ("library", "library", "पुस्तकालय"), ("university", "university", "विश्वविद्यालय")]},
    "TRANSPORT": {k: {"en": e, "hi": h} for k, e, h in [
        ("bus", "bus", "बस"), ("car", "car", "कार"), ("train", "train", "ट्रेन"), ("truck", "truck", "ट्रक"),
        ("bicycle", "bicycle", "साइकिल"), ("plane", "plane", "हवाई जहाज़"), ("boat", "boat", "नाव")]},
    "COLOUR": {k: {"en": k, "hi": h} for k, h in [
        ("red", "लाल"), ("blue", "नीला"), ("black", "काला"), ("white", "सफ़ेद"), ("green", "हरा"), ("yellow", "पीला"),
        ("brown", "भूरा"), ("pink", "गुलाबी"), ("grey", "स्लेटी"), ("orange", "नारंगी")]},
    "CLOTHES": {k: {"en": e, "bare": e.removeprefix("a ").removeprefix("an "), "hi": h} for k, e, h in [
        ("shirt", "a shirt", "कमीज़"), ("pant", "trousers", "पैंट"), ("t-shirt", "a T-shirt", "टी-शर्ट"),
        ("dress", "a dress", "ड्रेस"), ("skirt", "a skirt", "स्कर्ट"), ("suit", "a suit", "सूट"), ("hat", "a hat", "टोपी"),
        ("shoes", "shoes", "जूते")]},
    "OBJECT": {k: {"en": e, "hi": h} for k, e, h in [
        ("knife", "a knife", "चाकू"), ("gun", "a gun", "बंदूक"), ("telephone", "a phone", "फ़ोन"), ("cell phone", "a phone", "फ़ोन"),
        ("bag", "a bag", "थैला"), ("money", "money", "पैसे"), ("key", "a key", "चाबी"), ("book", "a book", "किताब"),
        ("box", "a box", "डिब्बा"), ("umbrella", "an umbrella", "छाता"), ("tool", "a tool", "औज़ार")]},
    # Whole sentences, since English and Hindi build these states differently ("I feel cold", "मुझे ठंड लग रही है").
    "FEELING": {k: {"en": e, "en_not": en_not, "hi": h, "hi_not": hn} for k, e, en_not, h, hn in [
        ("happy", "I am happy.", "I am not happy.", "मैं खुश हूँ।", "मैं खुश नहीं हूँ।"),
        ("sad", "I am sad.", "I am not sad.", "मैं दुखी हूँ।", "मैं दुखी नहीं हूँ।"),
        ("sick", "I feel sick.", "I do not feel sick.", "मैं बीमार हूँ।", "मैं बीमार नहीं हूँ।"),
        ("fedup", "I am upset.", "I am not upset.", "मैं परेशान हूँ।", "मैं परेशान नहीं हूँ।"),
        ("weak", "I feel weak.", "I do not feel weak.", "मुझे कमज़ोरी लग रही है।", "मुझे कमज़ोरी नहीं लग रही।"),
        ("healthy", "I am well.", "I am not well.", "मैं ठीक हूँ।", "मैं ठीक नहीं हूँ।"),
        ("good", "I am fine.", "I am not fine.", "मैं ठीक हूँ।", "मैं ठीक नहीं हूँ।"),
        ("bad", "I am not well.", "I am well.", "मेरी तबीयत ठीक नहीं है।", "मेरी तबीयत ठीक है।"),
        ("cold", "I feel cold.", "I do not feel cold.", "मुझे ठंड लग रही है।", "मुझे ठंड नहीं लग रही।"),
        ("hot", "I feel hot.", "I do not feel hot.", "मुझे गर्मी लग रही है।", "मुझे गर्मी नहीं लग रही।")]},
    "TIME": {k: {"en": e, "hi": h} for k, e, h in [
        ("yesterday", "Yesterday", "कल"), ("today", "Today", "आज"), ("morning", "In the morning", "सुबह"),
        ("evening", "In the evening", "शाम को"), ("night", "At night", "रात को"), ("afternoon", "In the afternoon", "दोपहर में")]},
    "SUBJECT": {k: {"en": e, "hi": h} for k, e, h in [("maths", "maths", "गणित"), ("science", "science", "विज्ञान"), ("computer", "computers", "कंप्यूटर")]},
    "SCHOOL_LIKE": {k: {"en": e, "hi": h} for k, e, h in [
        ("sport", "playing sports", "खेल"), ("friend", "my friends", "मेरे दोस्त"), ("book", "reading", "पढ़ाई"),
        ("paint", "drawing", "चित्र बनाना"), ("teacher", "my teacher", "मेरे शिक्षक")]},
}

YES_NO = ["s2", "s4", "h2", "h3", "h4", "n1", "n2", "n3", "n4", "n5", "i3", "f2", "f3", "l3", "k1", "k3", "x2", "x3", "c2"]


def intent(id, pattern, questions, en, hi, risk=False):
    return {"id": id, "pattern": pattern, "questions": questions, "en": en, "hi": hi, "risk": risk}


# Templates: {SLOT} is the slot's "en" (or "hi" in Hindi), {SLOT.field} another field; ".cap" capitalises the first
# letter; "{TIME, }" and "{TIME }" render the optional time with that separator, or nothing.
INTENTS = [
    intent("my_name", ["i?", "$NAME"], ["p1", "i1", "p3"], "My name is {NAME}.", "मेरा नाम {NAME} है।"),
    intent("relative_name", ["$PERSON", "$NAME"], ["f5", "f6", "sc3"], "{PERSON.ref.cap}'s name is {NAME}.", "{PERSON.hi_my} का नाम {NAME} है।"),
    intent("age", ["i?", "$NUM", "year?"], ["i2", "p3"], "I am {NUM} years old.", "मेरी उम्र {NUM} साल है।"),
    intent("from_place", ["i?", "$NAME"], ["a2", "a3", "a4"], "I am from {NAME}.", "मैं {NAME} से हूँ।"),
    intent("live_near", ["house?", "$PLACE"], ["a1", "l1", "a5"], "I live near the {PLACE}.", "मैं {PLACE.hi} के पास रहता/रहती हूँ।"),
    intent("family_count", ["family?", "$NUM"], ["f7"], "There are {NUM} people in my family.", "मेरे परिवार में {NUM} लोग हैं।"),
    intent("class", ["school?", "$NUM"], ["sc2"], "I am in class {NUM}.", "मैं कक्षा {NUM} में हूँ।"),
    intent("live_with", ["i?", "$PERSON"], ["f1"], "I live with {PERSON.ref}.", "मैं {PERSON.hi_my} के साथ रहता/रहती हूँ।"),
    intent("with_me", ["$PERSON"], ["s3"], "{PERSON.ref.cap} is with me.", "{PERSON.hi_my} मेरे साथ {PERSON.hai}।"),
    intent("person_at_place", ["$PERSON", "$PLACE"], ["f2", "f3", "s3"], "{PERSON.ref.cap} is at the {PLACE}.", "{PERSON.hi_my} {PLACE.hi} में {PERSON.hai}।"),
    intent("person_dead", ["$PERSON", "dead|death"], ["f2", "f3", "f5", "f6"], "{PERSON.ref.cap} has died.", "{PERSON.hi_my} की मृत्यु हो चुकी है।"),
    intent("came_by", ["i?", "$TRANSPORT", "come?"], ["l2"], "I came here by {TRANSPORT}.", "मैं {TRANSPORT.hi} से आया/आई।"),
    intent("came_by_with", ["$PERSON", "$TRANSPORT", "come?"], ["l2"], "I came here by {TRANSPORT} with {PERSON.ref}.", "मैं {PERSON.hi_my} के साथ {TRANSPORT.hi} से आया/आई।"),
    intent("wearing_colour", ["$COLOUR", "$CLOTHES"], ["d1"], "They were wearing {COLOUR} {CLOTHES.bare}.", "उन्होंने {COLOUR.hi} {CLOTHES.hi} पहना था।"),
    intent("wearing", ["$CLOTHES"], ["d1"], "They were wearing {CLOTHES}.", "उन्होंने {CLOTHES.hi} पहना था।"),
    intent("colour", ["$COLOUR"], ["d2"], "It was {COLOUR}.", "वह {COLOUR.hi} था।"),
    intent("held", ["$OBJECT"], ["d3"], "They had {OBJECT} in their hands.", "उनके हाथ में {OBJECT.hi} था।"),
    intent("attacked_by", ["$TIME?", "$PERSON", "attack"], ["x1", "x2", "c2", "x3"], "{TIME, }{PERSON.ref.cap} attacked me.", "{TIME }{PERSON.hi_ne} मुझ पर हमला किया।", True),
    intent("attacked_with", ["$TIME?", "$PERSON", "$OBJECT", "attack"], ["x1", "x2", "c2", "x3", "d3"], "{TIME, }{PERSON.ref.cap} attacked me with {OBJECT}.", "{TIME }{PERSON.hi_ne} मुझ पर {OBJECT.hi} से हमला किया।", True),
    intent("person_came", ["$TIME?", "$PERSON", "come"], ["x1", "x2", "c2"], "{TIME, }{PERSON.ref.cap} came.", "{TIME }{PERSON.hi_my} आए।"),
    intent("feel", ["i?", "$FEELING"], ["e2", "x3", "h3", "s4", "n4"], "{FEELING}", "{FEELING.hi}"),
    intent("not_feel", ["i?", "$FEELING", "@shake"], ["e2", "x3", "h3", "s4", "n4"], "{FEELING.en_not}", "{FEELING.hi_not}"),
    intent("fever", ["i?", "fever"], ["h3", "s2", "h1"], "I have a fever.", "मुझे बुखार है।", True),
    intent("injured", ["i?", "injury"], ["s2", "h1", "h3"], "I am hurt.", "मुझे चोट लगी है।", True),
    intent("need_doctor", ["i?", "doctor"], ["h2", "s2", "h3"], "I need a doctor.", "मुझे डॉक्टर चाहिए।", True),
    intent("take_medicine", ["i?", "medicine"], ["h4"], "I take medicine.", "मैं दवा लेता/लेती हूँ।"),
    intent("need_medicine", ["i?", "medicine"], ["h3", "h2", "c2"], "I need medicine.", "मुझे दवा चाहिए।", True),
    intent("want_drink", ["i?", "drink|water"], ["n1", "c2"], "I want something to drink.", "मुझे पानी चाहिए।"),
    intent("want_tea", ["i?", "tea"], ["n1", "n2", "c2"], "I would like some tea.", "मुझे चाय चाहिए।"),
    intent("go_home", ["i?", "house", "come?"], ["e8", "k1", "c2", "l3"], "I want to go home.", "मुझे घर जाना है।"),
    intent("go_to_school", ["i?", "school"], ["i3"], "Yes, I go to school.", "हाँ, मैं स्कूल जाता/जाती हूँ।"),
    intent("subject", ["$SUBJECT"], ["sc4"], "My favourite subject is {SUBJECT}.", "मेरा पसंदीदा विषय {SUBJECT.hi} है।"),
    intent("deaf", ["i?", "deaf"], [], "I am deaf.", "मैं सुन नहीं सकता/सकती।"),
    intent("phone_number", ["telephone|cell phone|phone?", "$NUM"], ["p2", "f4"], "The phone number is {NUM}.", "फ़ोन नंबर {NUM} है।"),
    intent("brothers", ["i?", "brother", "$NUM"], ["f8"], "I have {NUM} brothers.", "मेरे {NUM} भाई हैं।"),
    intent("sisters", ["i?", "sister", "$NUM"], ["f8"], "I have {NUM} sisters.", "मेरी {NUM} बहनें हैं।"),
    intent("school_name", ["school", "$NAME"], ["sc1"], "My school's name is {NAME}.", "मेरे स्कूल का नाम {NAME} है।"),
    intent("like_at_school", ["i?", "$SCHOOL_LIKE"], ["sc5"], "I like {SCHOOL_LIKE} at school.", "मुझे स्कूल में {SCHOOL_LIKE.hi} पसंद है।"),
    intent("yes", ["@nod"], YES_NO, "Yes.", "हाँ।"),
    intent("no", ["@shake"], YES_NO, "No.", "नहीं।"),
    intent("thank_you", ["thank you"], [], "Thank you.", "धन्यवाद।"),
    intent("hello", ["hello|good morning|good afternoon|good evening"], [], "Hello.", "नमस्ते।"),
    intent("how_are_you", ["how are you"], [], "How are you?", "आप कैसे हैं?"),
    intent("ask_name", ["what is your name"], [], "What is your name?", "आपका नाम क्या है?"),
]


def main() -> None:
    doc = {
        "status": "draft", "reviewed": False,
        "note": "Generated by tools/content/build_intents.py; edit the script, not this file. Needs English, Hindi and Deaf ISL review.",
        "slots": SLOTS, "intents": INTENTS,
    }
    OUT.write_text(json.dumps(doc, ensure_ascii=False, indent=1) + "\n", encoding="utf-8", newline="\n")
    print(f"{len(INTENTS)} intents, {sum(len(v) for v in SLOTS.values())} slot words -> {OUT.name}")


if __name__ == "__main__":
    main()
