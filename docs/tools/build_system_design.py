"""Writes docs/system-design.html: features, data flow diagrams, data dictionary, technology and readiness.

    python docs/tools/build_system_design.py docs/system-design.html

Every number and name here is taken from the code and content of this repository; update it with them.
"""
import html
import sys

OUT = sys.argv[1]
E = lambda s: html.escape(s, quote=True)


class Svg:
    """A Gane-Sarson data flow diagram drawn in SVG. Elements marked v are pending: to be connected or a later release."""

    def __init__(self, w, h, label, mid):
        self.w, self.h, self.aria, self.mid, self.p = w, h, label, mid, []

    def add(self, s):
        self.p.append(s)

    def text(self, x, y, s, cls, anchor="middle"):
        self.add(f'<text class="{cls}" x="{x}" y="{y}" text-anchor="{anchor}">{E(s)}</text>')

    def lines(self, cx, cy, lines, lh=15):
        y0 = cy - (len(lines) - 1) * lh / 2 + 4.5
        for i, (s, cls) in enumerate(lines):
            self.text(cx, round(y0 + i * lh, 1), s, cls)

    def ext(self, eid, x, y, w, h, name, sub=None, v=False):
        if not v:
            self.add(f'<rect class="shade" x="{x + 4}" y="{y + 4}" width="{w}" height="{h}"/>')
        self.add(f'<rect class="ent{" v" if v else ""}" x="{x}" y="{y}" width="{w}" height="{h}"/>')
        self.text(x + 7, y + 13, eid, "id", "start")
        ls = [(n, "nm" + (" v" if v else "")) for n in name] + ([(sub, "sub" + (" v" if v else ""))] if sub else [])
        self.lines(x + w / 2, y + h / 2 + 3, ls)

    def proc(self, pid, x, y, w, h, name, sub=None, v=False):
        r = 10
        k = " v" if v else ""
        self.add(f'<rect class="prc-bg" x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}"/>')
        self.add(f'<path class="band{k}" d="M{x},{y + 20} V{y + r} Q{x},{y} {x + r},{y} H{x + w - r} Q{x + w},{y} {x + w},{y + r} V{y + 20} Z"/>')
        self.add(f'<rect class="prc{k}" x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}"/>')
        self.add(f'<line class="sep{k}" x1="{x}" y1="{y + 20}" x2="{x + w}" y2="{y + 20}"/>')
        self.text(x + w / 2, y + 14, pid, "id")
        ls = [(n, "nm" + k) for n in name] + ([(sub, "sub" + k)] if sub else [])
        self.lines(x + w / 2, y + 20 + (h - 20) / 2, ls, 14)

    def store(self, sid, x, y, w, h, name, sub=None):
        self.add(f'<rect class="st-bg" x="{x}" y="{y}" width="{w}" height="{h}"/>')
        for x1, y1, x2, y2 in ((x, y, x + w, y), (x, y + h, x + w, y + h), (x, y, x, y + h), (x + 32, y, x + 32, y + h)):
            self.add(f'<line class="st" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}"/>')
        self.text(x + 16, y + h / 2 + 4, sid, "id")
        ls = [(name, "nm")] + ([(sub, "sub")] if sub else [])
        self.lines(x + 32 + (w - 32) / 2, y + h / 2, ls, 14)

    def flow(self, pts, v=False, both=False):
        m = f"{self.mid}-v" if v else self.mid
        p = " ".join(f"{a},{b}" for a, b in pts)
        start = f' marker-start="url(#{m})"' if both else ""
        self.add(f'<polyline class="f{" v" if v else ""}" points="{p}" marker-end="url(#{m})"{start}/>')

    def label(self, x, y, s, anchor="middle", v=False):
        for i, part in enumerate(s.split("|")):
            self.text(x, y + i * 12, part, "fl" + (" v" if v else ""), anchor)

    def tb(self, x, y, w, h, text, tx, ty):
        self.add(f'<rect class="tb" x="{x}" y="{y}" width="{w}" height="{h}" rx="14"/>')
        self.text(tx, ty, text, "tbl", "start")

    def render(self):
        mk = lambda i, c: (f'<marker id="{i}" viewBox="0 0 10 10" refX="10" refY="5" markerWidth="8" markerHeight="8" '
                           f'markerUnits="userSpaceOnUse" orient="auto-start-reverse"><path class="{c}" d="M0,1 L10,5 L0,9 z"/></marker>')
        return (f'<svg class="dfd" viewBox="0 0 {self.w} {self.h}" role="img" aria-label="{E(self.aria)}">'
                f'<defs>{mk(self.mid, "mk")}{mk(self.mid + "-v", "mk-v")}</defs>{"".join(self.p)}</svg>')


# ---------- Level 0 ----------
l0 = Svg(1000, 420, "Level 0 context diagram. The police officer exchanges officer input and output with the Sign Setu application. "
         "The subject receives the presentation and returns subject input, including camera frames. The application sends the session "
         "report to the records custodian and a contact link to the messaging app. The department supplies the assistant model file. "
         "An optional stage device, a paired second screen, receives the stage view and returns the subject's answers. "
         "Pending: language services are a later release.", "a0")
l0.proc("0", 410, 160, 180, 110, ["Sign Setu", "application"], "officer's phone")
l0.ext("E1", 40, 187, 170, 56, ["Police officer"])
l0.ext("E2", 790, 187, 170, 56, ["Subject"], "incl. camera frames")
l0.ext("E7", 40, 30, 170, 56, ["Department"], "model file, contacts")
l0.ext("E6", 790, 30, 170, 56, ["Stage device"], "optional second screen")
l0.ext("E5", 415, 24, 170, 56, ["Language services"], "later release", v=True)
l0.ext("E4", 40, 340, 170, 56, ["Messaging app"], "interpreter call")
l0.ext("E3", 790, 340, 170, 56, ["Records custodian"], "case file, CWC")
l0.flow([(210, 203), (409, 203)]); l0.label(310, 195, "officer input")
l0.flow([(410, 227), (211, 227)]); l0.label(310, 243, "officer output")
l0.flow([(590, 203), (789, 203)]); l0.label(690, 195, "presentation")
l0.flow([(790, 227), (591, 227)]); l0.label(690, 243, "subject input")
l0.flow([(210, 70), (300, 70), (300, 175), (409, 175)]); l0.label(306, 120, "assistant model", "start")
l0.flow([(560, 160), (560, 120), (789, 64)]); l0.label(690, 118, "stage view")
l0.flow([(790, 76), (620, 140), (591, 160)]); l0.label(712, 82, "answers")
l0.flow([(470, 160), (470, 81)], v=True); l0.label(462, 128, "language request", "end", v=True)
l0.flow([(500, 80), (500, 159)], v=True); l0.label(508, 145, "result", "start", v=True)
l0.flow([(572, 270), (789, 358)]); l0.label(700, 300, "session report")
l0.flow([(428, 270), (211, 358)]); l0.label(300, 300, "contact link")

# ---------- Level 1 ----------
l1 = Svg(1230, 790, "Level 1 diagram. Inside the device boundary: 1.0 authenticates the officer and releases the session key; "
         "2.0 handles officer requests using the catalogue in the content bundle; 3.0 presents content to the subject and returns the subject "
         "response; 4.0 maintains the encrypted session store and the session log; 5.0 builds the session report and the interpreter contact link; "
         "6.0 translates typed text with the assistant model; 7.0 links an optional stage device over bonded Bluetooth, sending the stage view "
         "and receiving answers. Pending: language services connect to 2.0 in a later release.", "a1")
l1.tb(196, 120, 820, 650, "TB1  DEVICE BOUNDARY (APP SANDBOX)", 214, 144)
l1.ext("E5", 240, 26, 170, 56, ["Language services"], "later release", v=True)
l1.ext("E7", 680, 26, 170, 56, ["Department"], "model file")
l1.ext("E1", 20, 270, 130, 344, ["Police", "officer"])
l1.ext("E6", 1060, 150, 150, 56, ["Stage device"], "second screen")
l1.ext("E2", 1060, 274, 150, 56, ["Subject"])
l1.ext("E3", 1060, 414, 150, 56, ["Records", "custodian"])
l1.ext("E4", 1060, 554, 150, 56, ["Messaging app"])
l1.store("D2", 470, 156, 170, 48, "Content bundle", "read-only, hashed")
l1.store("D5", 690, 124, 150, 42, "Assistant model", "optional, hashed")
l1.proc("6.0", 690, 178, 150, 68, ["Translate", "typed text"], "assistant")
l1.proc("7.0", 870, 144, 130, 70, ["Link second", "screen"], "RFCOMM")
l1.proc("2.0", 240, 270, 170, 64, ["Handle officer", "requests"])
l1.proc("3.0", 500, 270, 170, 64, ["Interact with", "subject"])
l1.proc("4.0", 240, 410, 170, 64, ["Maintain", "session record"])
l1.store("D1", 500, 418, 170, 48, "Session store", "encrypted")
l1.proc("5.0", 790, 410, 170, 64, ["Export and", "hand off"])
l1.proc("1.0", 240, 550, 170, 64, ["Authenticate", "officer"])
l1.store("D3", 500, 558, 170, 48, "Configuration")
l1.store("D4", 240, 690, 170, 48, "Key store", "Android Keystore")

l1.flow([(300, 270), (300, 83)], v=True); l1.label(292, 104, "language request", "end", v=True)
l1.flow([(350, 82), (350, 269)], v=True); l1.label(358, 104, "language result", "start", v=True)
l1.flow([(765, 82), (765, 123)]); l1.label(773, 104, "model file", "start")
l1.flow([(765, 166), (765, 177)]); l1.label(773, 175, "model", "start")
l1.flow([(1000, 170), (1059, 170)]); l1.label(1030, 162, "stage view")
l1.flow([(1060, 194), (1001, 194)]); l1.label(1030, 210, "answers")
l1.flow([(650, 270), (650, 258), (935, 258), (935, 215)], both=True); l1.label(860, 274, "items, answers")
l1.flow([(150, 294), (239, 294)]); l1.label(195, 274, "officer|request")
l1.flow([(240, 318), (151, 318)]); l1.label(195, 333, "officer view")
l1.flow([(470, 180), (395, 180), (395, 269)]); l1.label(432, 172, "catalogue")
l1.flow([(555, 204), (555, 269)]); l1.label(563, 222, "content assets", "start")
l1.flow([(372, 269), (372, 250), (670, 250), (670, 214), (689, 214)], both=True); l1.label(560, 242, "typed text, translation")
l1.flow([(410, 294), (499, 294)]); l1.label(455, 274, "subject|request")
l1.flow([(500, 318), (411, 318)]); l1.label(455, 333, "subject|response")
l1.flow([(670, 294), (1059, 294)]); l1.label(990, 286, "presentation")
l1.flow([(1060, 318), (671, 318)]); l1.label(990, 333, "subject input")
l1.flow([(325, 334), (325, 409)]); l1.label(317, 375, "session events", "end")
l1.flow([(395, 334), (395, 362), (860, 362), (860, 409)]); l1.label(620, 356, "export request")
l1.flow([(820, 410), (820, 382), (375, 382), (375, 335)]); l1.label(620, 397, "export result")
l1.flow([(240, 442), (151, 442)]); l1.label(195, 434, "session log")
l1.flow([(411, 442), (499, 442)], both=True); l1.label(455, 434, "records")
l1.flow([(670, 442), (789, 442)]); l1.label(730, 434, "records")
l1.flow([(960, 442), (1059, 442)]); l1.label(1010, 434, "session report")
l1.flow([(930, 474), (930, 582), (1059, 582)]); l1.label(995, 574, "contact link")
l1.flow([(510, 558), (406, 475)]); l1.label(472, 514, "retention period", "start")
l1.flow([(660, 558), (806, 475)]); l1.label(726, 551, "interpreter contact", "start")
l1.flow([(150, 582), (239, 582)]); l1.label(195, 574, "credential")
l1.flow([(325, 550), (325, 475)]); l1.label(317, 516, "session key", "end")
l1.flow([(325, 690), (325, 615)]); l1.label(333, 661, "key material", "start")

# ---------- Level 2 (process 3.0) ----------
l2 = Svg(1000, 470, "Level 2 diagram of process 3.0. 3.1 resolves the subject request against content assets into a render set; "
         "3.2 presents it on the subject panel, with the camera mirror; 3.5 tracks hands, face and pose in camera frames, only inside a sign "
         "window, and returns the hand-and-arm skeleton to 3.2; 3.6 splits the landmarks into signs, keeps them in D7 landmark memory for the "
         "session and sends the app's reading to 2.0 as a proposal; 3.3 captures subject input; 3.4 validates the raw input against the display "
         "context and returns the subject response to 2.0. Only the presentation, subject input and camera frames cross the subject panel boundary.", "a2")
l2.store("D2", 40, 18, 170, 44, "Content bundle")
l2.proc("2.0", 20, 98, 150, 346, ["Handle officer", "requests"])
l2.ext("E2", 830, 98, 140, 346, ["Subject"])
l2.proc("3.1", 250, 98, 170, 64, ["Resolve content"], "signs, gestures, spelling")
l2.proc("3.2", 520, 98, 170, 64, ["Present to", "subject"])
l2.proc("3.5", 520, 190, 170, 60, ["Track landmarks"], "MediaPipe, sign window")
l2.proc("3.6", 250, 190, 170, 60, ["Read signs"], "segment, read, choose")
l2.store("D7", 250, 290, 170, 44, "Landmark memory", "session only")
l2.proc("3.4", 250, 380, 170, 64, ["Validate", "response"])
l2.proc("3.3", 520, 380, 170, 64, ["Capture input"], "tap, draw, write")
l2.add('<line class="tb" x1="800" y1="84" x2="800" y2="460"/>')
l2.text(806, 92, "TB0", "tbl", "start")
l2.flow([(210, 40), (335, 40), (335, 97)]); l2.label(272, 33, "content assets")
l2.flow([(170, 130), (249, 130)]); l2.label(210, 110, "subject|request")
l2.flow([(420, 130), (519, 130)]); l2.label(470, 122, "render set")
l2.flow([(690, 130), (829, 130)]); l2.label(760, 122, "presentation")
l2.flow([(829, 220), (691, 220)]); l2.label(760, 212, "camera frames")
l2.flow([(640, 190), (640, 163)]); l2.label(648, 182, "skeleton", "start")
l2.flow([(519, 230), (421, 230)]); l2.label(450, 246, "landmarks")
l2.flow([(335, 250), (335, 289)], both=True); l2.label(343, 274, "signs, replay", "start")
l2.flow([(249, 210), (171, 210)]); l2.label(210, 202, "app reading")
l2.flow([(249, 312), (171, 312)]); l2.label(210, 304, "replay")
l2.flow([(829, 412), (691, 412)]); l2.label(760, 428, "subject input")
l2.flow([(519, 412), (421, 412)]); l2.label(470, 428, "raw input")
l2.flow([(519, 150), (494, 150), (494, 379)]); l2.label(502, 360, "display|context", "start")
l2.flow([(250, 412), (171, 412)]); l2.label(210, 428, "subject|response")


def mini(kind):
    s = Svg(36, 22, "", "lg" + kind)
    if kind == "ext":
        s.add('<rect class="shade" x="6" y="6" width="26" height="14"/><rect class="ent" x="3" y="3" width="26" height="14"/>')
    elif kind == "prc":
        s.add('<rect class="prc-bg" x="3" y="2" width="28" height="18" rx="4"/><path class="band" d="M3,8 V6 Q3,2 7,2 H27 Q31,2 31,6 V8 Z"/>'
              '<rect class="prc" x="3" y="2" width="28" height="18" rx="4"/><line class="sep" x1="3" y1="8" x2="31" y2="8"/>')
    elif kind == "st":
        s.add('<line class="st" x1="3" y1="4" x2="33" y2="4"/><line class="st" x1="3" y1="18" x2="33" y2="18"/>'
              '<line class="st" x1="3" y1="4" x2="3" y2="18"/><line class="st" x1="11" y1="4" x2="11" y2="18"/>')
    elif kind == "f":
        s.flow([(3, 11), (32, 11)])
    elif kind == "v":
        s.flow([(3, 11), (32, 11)], v=True)
    elif kind == "tb":
        s.add('<rect class="tb" x="3" y="3" width="30" height="16" rx="4"/>')
    return s.render().replace('role="img" aria-label=""', 'aria-hidden="true"')


legend = "".join(f"<li>{mini(k)}<span>{E(t)}</span></li>" for k, t in [
    ("ext", "Person or outside party"), ("prc", "Process (number in the band)"), ("st", "Data store"),
    ("f", "Data flow"), ("tb", "Trust boundary"), ("v", "A later release")])

code = lambda s: f"<code>{E(s)}</code>"
BUILT, PENDING, OPTIONAL, REVIEW = ("built", "Built"), ("pending", "To be connected"), ("optional", "Optional pack"), ("review", "Built, awaiting review")


def badge(state):
    return f'<span class="badge {state[0]}">{state[1]}</span>'


def table(cap, head, rows, cls=""):
    th = "".join(f'<th scope="col">{E(h)}</th>' for h in head)
    tr = "".join("<tr>" + "".join(f"<td>{c}</td>" for c in r) + "</tr>" for r in rows)
    cap = f"<caption>{cap}</caption>" if cap else ""
    return f'<div class="table-wrap"><table class="{cls}">{cap}<thead><tr>{th}</tr></thead><tbody>{tr}</tbody></table></div>'


def features(cap, rows):
    return table(cap, ["Feature", "What it does", "State"], [[f"<strong>{E(a)}</strong>", b, badge(c)] for a, b, c in rows], "feat")


officer = features("Officer side", [
    ("Who you are helping", "Four faces on the home screen: young child, signs, other language, not sure. The choice decides what the subject sees and hears, and which character appears. 17 languages in the language finder, including Chhattisgarhi, Gondi and Halbi.", BUILT),
    ("Role", "Not known yet (default), victim or witness, or accused or in custody. Sensitive and free-recall questions never for an accused; rights groups for an accused, with the child's rights for anyone who may be under 18. Rights are recorded as shown, not proof of understanding.", REVIEW),
    ("Conversation", "Two pages, Transcribe and More. What was shown on the right, the subject's answers on the left, each with its evidence grade (Said, Confirmed, Officer's reading, App's reading, Said no, Unclear) and its trail (time, changes, pauses).", BUILT),
    ("Prepared questions", "50 questions in 13 groups, including ground rules, breaks, closing, a victim-only sensitive group and four rights groups, plus a phrase bank of 10 sentences. Question runs put up to 5 safe questions on one handover.", REVIEW),
    ("Type anything", "The composer shows any typed text in one tap, with word completions tinted by sign, gesture or picture. The character signs, gestures or fingerspells it word by word in draft ISL order. 100 vocabulary entries cover 726 written forms.", REVIEW),
    ("Say it back", "The officer's reading of an answer is shown to the subject among the item's pictures; only the subject's pick makes it Confirmed.", BUILT),
    ("Read signs and Tell me", "Open a sign window: an answer of up to 20 s or an account of up to 3 minutes. The app's reading per sign, slow replay at quarter or half speed for an interpreter, and Ask them to choose.", BUILT),
    ("Manual and Auto", "Auto, when a department allows it, proposes the next welfare item from the reviewed bank in rule order; the officer approves each one. Never sensitive, free-recall or rights items.", BUILT),
    ("Assistant", "Translates typed text into the subject's language and back into English, on the phone, so the officer sees a wrong translation before Send. Gemma 3 1B through llama.cpp, imported once from a file.", OPTIONAL),
    ("They gestured", "The officer taps where the hand went and what it did; the app lists matching entries from the 33-entry gesture lexicon, and the subject confirms the meaning before anything is logged as theirs.", BUILT),
    ("Picture board", "64 picture cards in 9 categories, with search and favourites, shown as answer choices.", BUILT),
    ("Second screen", "A department phone or tablet paired in Bluetooth settings shows the stage; code, check number and Match; reconnect within 120 s; everything is logged on the officer's phone only.", BUILT),
    ("Notes, log and export", "Officer notes and the full session log; on ending, a PDF communication note, not a statement, encrypted with AES-256 under a one-time password shown once.", BUILT),
    ("Settings", "Camera mirror, large captions, slower gestures, screen lock to leave the stage, tilt to show, volume keys, allow automatic conversation, retention period (4 to 72 hours), app language, the assistant model, delete all data.", BUILT),
])

subject = features("Subject side: the stage", [
    ("Character", "A child and an adult character drawn in code, with jointed fingers, facial grammar and a living idle. 125 motions: 42 gestures, the 26-letter two-handed ISL alphabet, 10 digits and 47 draft word signs.", REVIEW),
    ("Opening", "The officer's introduction, four pictured and signed ground rules and a practice round, each moved on by the subject's Next.", REVIEW),
    ("Caption and pictures", "A speech bubble in the subject's language. Typed text lights the word being signed; tapping a word replays it slowly. Picture choices are shuffled and capped at four, plus Something else and I don't understand.", BUILT),
    ("Recorded speech", "88 placeholder recordings in Hindi and English, played by themselves for a young child or another language, never for a signer.", REVIEW),
    ("Answers", "Yes and No always; I don't know, I don't understand and That's wrong; Break and an octagonal Stop. Draw; Write for anyone but a young child.", BUILT),
    ("Sign an answer", "The hand button opens a 20-second sign window; one dot per sign, never the reader's guesses; a slower cue only after a failed read. The subject picks the meaning among two or three pictures.", BUILT),
    ("Camera mirror", "The subject sees their own hands, with a hand-and-arm skeleton during a sign window. Nothing is captured or stored.", BUILT),
    ("Pause, strip and buzz", "A calm pause after Stop or Break that only the subject ends; an upside-down strip for the officer; vibration codes for Yes, No and the other answers.", BUILT),
    ("Sun mode", "Black, white and yellow in glare, from the light sensor while the stage shows.", BUILT),
    ("Stays on the stage", "The app is pinned; Back and single volume presses do nothing; one tap on the small lock, or both volume keys held, returns to the officer.", BUILT),
])

safety = features("Safety and privacy", [
    ("Offline", "No INTERNET permission in any build; the standard build has no location, scanning or Play services; the build fails if a library adds them. MediaPipe's usage statistics are dropped on the phone.", BUILT),
    ("Encrypted record", "SQLCipher (AES-256) under a key wrapped by Android Keystore and released only after the phone's own lock; relock after 5 minutes away; full credential after a crash on the stage.", BUILT),
    ("Landmarks", "Face and hand points are biometric: analysed only inside a sign window, kept in memory for the session, wiped on end and lock, never stored or exported.", BUILT),
    ("Short-lived", "Sessions purge on end or after the retention period (default 24 hours); Delete all data destroys the key.", BUILT),
    ("Screen protection", "FLAG_SECURE blocks screenshots in release; taps through another app's overlay are ignored; no backup or device transfer.", BUILT),
    ("Second-screen link", "Bonded devices only; P-256 ECDH, HKDF-SHA256, AES-256-GCM with strict counters; the stage device stores nothing.", BUILT),
    ("Integrity", "Every content file and every model is checked by SHA-256 before use; a failed text file stops new sessions.", BUILT),
    ("Fresh builds", "A build refuses new sessions 180 days after it was made; rooted phones are warned.", BUILT),
])

elements = table("External entities and processes", ["ID", "Name", "Description"], [
    ["E1", "Police officer", "Operates the phone: authenticates, asks, types, reads signs, records notes, exports."],
    ["E2", "Subject", "The person being helped; sees the stage and answers by touch, drawing, writing and signing in front of the camera."],
    ["E3", "Records custodian", "Case file or Child Welfare Committee; receives the exported communication note."],
    ["E4", "Messaging app", "Sandes or WhatsApp on the same phone; receives the interpreter contact link only."],
    ["E5", "Language services (later)", "Speech recognition and translation through a project gateway; not in release 1."],
    ["E6", "Stage device", "An optional department phone or tablet, bonded in Bluetooth settings, that shows the stage and returns answers; stores nothing."],
    ["E7", "Department", "Hands out the assistant model file and sets the interpreter contact."],
    ["1.0", "Authenticate officer", "Tries the Keystore key silently after the phone unlock, else BiometricPrompt; always the full prompt after a crash on the stage; releases the session key."],
    ["2.0", "Handle officer requests", "Profile, role, question, run, phrase, typed text, picture board, gesture lookup, readings, say-back, Auto, notes and session commands; shows the conversation."],
    ["3.0", "Interact with subject", "Renders the stage for the profile, captures input, reads signs and binds every answer to what was shown (Figure 3)."],
    ["3.1", "Resolve content", "Selects caption, pictures, audio and motions; plans typed text into signs, gestures and spelling."],
    ["3.2", "Present to subject", "Draws the character, caption, pictures, mirror and skeleton on the pinned stage."],
    ["3.3", "Capture input", "Answer taps, drawing strokes and written answers."],
    ["3.4", "Validate response", "Binds input to what was on screen; a reading becomes the subject's only by their pick."],
    ["3.5", "Track landmarks", "MediaPipe Holistic on camera frames, only inside a sign window; frames are discarded after each call."],
    ["3.6", "Read signs", "Splits landmarks into signs at pauses, reads each on the phone, limits guesses to the item on screen, builds the subject's choices."],
    ["4.0", "Maintain session record", "Writes each event before the screen updates; serves the log; purges on end or retention expiry."],
    ["5.0", "Export and hand off", "Builds the encrypted PDF communication note; opens the interpreter contact."],
    ["6.0", "Translate typed text", "Runs the assistant model: caption in the subject's language and its back-translation, for the officer to accept."],
    ["7.0", "Link second screen", "Pairs with a bonded device, sends snapshots of what is on stage, receives and acknowledges answers after they are logged."],
], "ids")

stores = table("Data stores", ["ID", "Store", "Contents", "Protection", "Kept"], [
    ["D1", "Session store", "Session, role, events with item and answer links, \"Shown as\" records, notes, drawings, typed answers", "SQLCipher (AES-256); key wrapped by D4; excluded from backup", "Until session end or retention expiry"],
    ["D2", "Content bundle", "Questions, phrases, picture cards, gestures and signs, vocabulary, sign-word map, audio, languages, vision and sign-reader models", "Read-only in the signed APK; SHA-256 manifest", "Until app update"],
    ["D3", "Configuration", "App settings, retention period, interpreter contact, favourite cards, stage-device flag", "No personal data", "Until uninstall"],
    ["D4", "Key store", "Key-encryption key", "Android Keystore; non-exportable; bound to user authentication", "Until Delete all data or uninstall"],
    ["D5", "Assistant model", "GGUF model file", "App-private storage; accepted only by SHA-256 allow-list", "Until removed in Settings"],
    ["D7", "Landmark memory", "Landmark frames of each signed answer and account", "View-model memory only; never written to disk, logged, exported or sent", "Until the session ends, the app locks or the session screen closes"],
], "ids")

flows = table("Data flows", ["Flow", "From", "To", "Composition"], [
    ["credential", "E1", "1.0", "Device PIN, pattern, password or biometric, handled by the platform"],
    ["key material / session key", "D4, 1.0", "1.0, 4.0", "Wrapped database key, unwrapped inside Keystore and held in memory only"],
    ["officer request", "E1", "2.0", "Profile, role, question, run, phrase, typed text and toggles, picture choice, gesture description, sign window, reading, note, command"],
    ["officer view", "2.0", "E1", "Conversation with grades and trails, suggestions, readings, translation preview, export password"],
    ["catalogue / content assets", "D2", "2.0, 3.0", "Question sets, lexicon, vocabulary, pictures, motions, audio, models"],
    ["typed text, translation", "2.0, 6.0", "6.0, 2.0", "Officer's text; caption in the subject's language and its English back-translation"],
    ["model file / model", "E7, D5", "D5, 6.0", "GGUF file copied in once and checked by hash"],
    ["subject request", "2.0", "3.0", "Confirmed item with profile, toggles, accepted caption, or a sign window"],
    ["presentation", "3.0", "E2", "Character motion, caption, pictures, audio, mirror, skeleton"],
    ["subject input", "E2", "3.0", "Answer tap, drawing, written answer, camera frames inside a sign window"],
    ["landmarks / signs, replay", "3.5, 3.6, D7", "3.6, D7, 2.0", "Hand, face and pose points; sign segments; memory only"],
    ["app reading", "3.6", "2.0", "Top three guesses per sign, limited to the item on screen; a proposal, never shown to the subject automatically"],
    ["subject response", "3.0", "2.0", "Card, Yes or No, drawing, written text, picked meaning, or unclear"],
    ["items, answers / stage view", "3.0, 7.0", "7.0, E6", "Snapshot of what is on stage; answers back with item and number; encrypted with AES-256-GCM"],
    ["session events / records / session log", "2.0, 4.0", "4.0, D1, E1", "Timestamped events, appended and read back, purged on end"],
    ["session report", "5.0", "E3", "PDF encrypted with AES-256 under a one-time password shared separately"],
    ["contact link", "5.0", "E4", "Contact identifier only"],
    ["language request / result (later)", "2.0, E5", "E5, 2.0", "Officer audio or text; transcript or translation"],
], "flows")

boundaries = table("Trust boundaries", ["ID", "Boundary", "Crossing", "Controls"], [
    ["TB0", "The stage on the shared screen", "presentation, subject input, camera frames",
     "Stage full screen and pinned; notes, log, conversation and machine readings never drawn on it; nothing shown without the officer; camera analysed only inside a sign window; frames never stored"],
    ["TB1", "App sandbox on the officer's personal phone", "officer input and output, presentation, subject input, model file, report, contact link, stage view, answers",
     f"App lock (1.0); encrypted D1; {code('FLAG_SECURE')}; overlay taps filtered; no backup; only the launcher exported; no INTERNET permission; content and model hashes; build expiry"],
    ["TB2", "Bluetooth link to the stage device", "stage view, answers",
     "Bonded devices only; commit-then-reveal ECDH; typed code and matching check numbers; AES-256-GCM with strict counters; resume key in memory; stage stores nothing"],
], "ids")

stack = table(None, ["Layer", "Choice", "Why"], [
    ["Language, UI", "Kotlin, Jetpack Compose, Material 3", "Native, small, direct access to Keystore and platform security"],
    ["Platform", "Android 10 (API 29) to Android 17 (API 37)", "The oldest phones officers carry"],
    ["Characters", "Compose Canvas 2D rig; keyframes in " + code("gestures.json") + " and " + code("signs.json"), "No video files; one file plays on both characters"],
    ["Typed text", code("TextSigning.kt") + " with " + code("words.json"), "Rule-based word planning in draft ISL order; deterministic and reviewable"],
    ["Media", "Media3 ExoPlayer", "Recorded speech at normal and slow speed"],
    ["Camera and vision", "CameraX 1.6; MediaPipe Tasks Vision 1.0.0 (Holistic)", "Landmarks on the phone, only inside a sign window"],
    ["Sign reader", "262-word INCLUDE classifier in plain Kotlin", "No inference library; proposals only"],
    ["Second screen", "Bluetooth RFCOMM; Nearby Connections in the " + code("nearby") + " flavor only", "No network, scanning or location in the standard build"],
    ["Storage", "Room (version 3) with SQLCipher for Android 4.19; DataStore", "Encrypted at rest; settings hold no personal data"],
    ["Security", "BiometricPrompt, Android Keystore, screen pinning, FLAG_SECURE; P-256, HKDF, AES-GCM for the link", "Platform controls, no third-party security SDK"],
    ["Export", "Android PdfDocument, PdfBox-Android (AES-256)", "Readable in any PDF reader"],
    ["Assistant", "llama.cpp v0.5.0 (MIT) via NDK r30 and CMake 4.1.1; Gemma 3 1B GGUF", "Offline translation; optional"],
    ["Background", "WorkManager", "Retention purge"],
    ["Build", "Gradle with version catalog, lockfile and dependency verification; R8 full mode; flavors " + code("standard") + " and " + code("nearby"), "Small, fast to start, verifiable supply chain"],
    ["Tests", "JUnit unit tests; instrumented Accessibility Test Framework; Android Lint; portal tests", "120 unit tests pass for both flavors"],
    ["Reviewer portal", "Vite+ 1.0, React 19.3, TypeScript 7.0", "Static, offline content sign-off; not shipped in the app"],
    ["Content tools", "Python: content manifest, sign generator, review record, placeholder audio (Indic Parler-TTS), sign-reader training", "Content is data, reviewed outside the code"],
])

ready = table(None, ["Item", "State", "Needs"], [
    ["Application", badge(BUILT), "Built; 120 unit tests pass; 47.0 MB release APK; instrumented suite to run on the merged build"],
    ["Vision engine and sign reader", badge(BUILT), "Frame rate, memory and hand sides on a real 64-bit phone; accuracy with children; data for yes, no, water, pain, help, stop and don't know"],
    ["Second screen", badge(BUILT), "Two-phone and tablet acceptance script; department devices and SOP"],
    ["Gestures, signs, ISL order, alphabet, vocabulary, sign-word map, recordings", badge(REVIEW), "Sign-off by ISL, child-welfare and language reviewers through the reviewer portal and the content review record"],
    ["Rights wording", badge(REVIEW), "Legal adviser and a legal translator, English and Hindi"],
    ["Assistant model", badge(OPTIONAL), "The department hands out the file; speed checked on an arm64 phone; output spot-checked per language"],
    ["Field tests", badge(PENDING), "Timing and TalkBack on a real Android 10 phone; transfer test on two phones"],
    ["Security audit", badge(PENDING), "MASVS checklist, CERT-In empanelled audit, release signing key with two custodians"],
    ["Pilot and rollout", badge(PENDING), "One district with trained officers; then state-wise through the Mobile Seva AppStore"],
])

CSS = """
:root{--bg:#f4f6f9;--panel:#ffffff;--ink:#142033;--muted:#5b6678;--line:#d3d9e2;--accent:#1d4f91;--accent-soft:#e3ebf6;--later:#9a5a00;--later-soft:#fbefdc;--ok:#1f7a4d;--ok-soft:#e0f2e8;--shade:#cdd5df;
--font-display:"Archivo","Arial Narrow","Helvetica Neue",Arial,sans-serif;--font-body:"IBM Plex Sans","Segoe UI",Roboto,Arial,sans-serif;--font-mono:"IBM Plex Mono",ui-monospace,Consolas,monospace}
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){--bg:#0e131a;--panel:#151c26;--ink:#e5eaf1;--muted:#9ba6b6;--line:#2a3442;--accent:#86aeea;--accent-soft:#1b2a40;--later:#e3a645;--later-soft:#33270f;--ok:#6fd3a0;--ok-soft:#14301f;--shade:#2a3442;color-scheme:dark}}
:root[data-theme="dark"]{--bg:#0e131a;--panel:#151c26;--ink:#e5eaf1;--muted:#9ba6b6;--line:#2a3442;--accent:#86aeea;--accent-soft:#1b2a40;--later:#e3a645;--later-soft:#33270f;--ok:#6fd3a0;--ok-soft:#14301f;--shade:#2a3442;color-scheme:dark}
*{box-sizing:border-box}
html{-webkit-text-size-adjust:100%}
body{margin:0;background:var(--bg);color:var(--ink);font:400 16px/1.55 var(--font-body)}
.page{max-width:1120px;margin:0 auto;padding-left:max(16px,env(safe-area-inset-left,0px));padding-right:max(16px,env(safe-area-inset-right,0px));padding-block:40px 64px;display:grid;gap:56px}
header{display:grid;gap:12px}
.eyebrow{font:500 12px/1.4 var(--font-mono);letter-spacing:.08em;text-transform:uppercase;color:var(--muted);margin:0}
h1,h2{font-family:var(--font-display);font-stretch:85%;text-wrap:balance;margin:0;letter-spacing:-.01em}
h1{font-size:clamp(2.2rem,5vw,3.2rem);font-weight:750;line-height:1.05}
h2{font-size:1.6rem;font-weight:700;line-height:1.15}
h3{font:600 1.05rem/1.3 var(--font-body);margin:0}
p{margin:0;max-width:72ch}
.deck{font-size:1.08rem}
.muted{color:var(--muted)}
section{display:grid;gap:20px}
.sec-head{display:grid;gap:8px}
.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:12px}
.card{background:var(--panel);border:1px solid var(--line);border-radius:10px;padding:14px 16px;display:grid;gap:4px}
.card .k{font:500 11px/1.3 var(--font-mono);letter-spacing:.08em;text-transform:uppercase;color:var(--muted)}
.card .v{font-family:var(--font-display);font-stretch:85%;font-weight:700;font-size:1.7rem;line-height:1.1}
figure{margin:0;display:grid;gap:10px}
.diagram{overflow-x:auto;background:var(--panel);border:1px solid var(--line);border-radius:10px;padding:12px}
.diagram svg{display:block;width:100%;min-width:860px;height:auto}
figcaption{font-size:.92rem;color:var(--muted);max-width:80ch}
figcaption b{color:var(--ink);font-weight:600}
.legend{list-style:none;margin:0;padding:0;display:flex;flex-wrap:wrap;gap:10px 22px;font-size:.9rem;color:var(--muted)}
.legend li{display:flex;align-items:center;gap:8px}
.legend svg{width:36px;height:22px;flex:none}
.table-wrap{overflow-x:auto;background:var(--panel);border:1px solid var(--line);border-radius:10px}
table{width:100%;border-collapse:collapse;font-size:.95rem}
caption{text-align:left;font:600 1rem/1.3 var(--font-body);padding:14px 14px 4px}
th,td{text-align:left;vertical-align:top;padding:12px 14px;border-bottom:1px solid var(--line)}
tr:last-child td{border-bottom:0}
th{font:500 11px/1.3 var(--font-mono);letter-spacing:.08em;text-transform:uppercase;color:var(--muted)}
td strong{font-weight:600}
.feat td:first-child{width:22%}
.feat td:last-child,.ready td:nth-child(2){white-space:nowrap}
.ids td:first-child{font:500 13px/1.5 var(--font-mono);color:var(--accent);white-space:nowrap}
.flows td:nth-child(2),.flows td:nth-child(3){font:500 13px/1.5 var(--font-mono);white-space:nowrap}
code{font-family:var(--font-mono);font-size:.88em}
.badge{display:inline-block;font:500 11px/1.4 var(--font-mono);letter-spacing:.04em;padding:3px 8px;border-radius:999px;white-space:nowrap}
.badge.built{background:var(--ok-soft);color:var(--ok)}
.badge.review{background:var(--accent-soft);color:var(--accent)}
.badge.optional{background:var(--accent-soft);color:var(--accent);border:1px dashed var(--accent)}
.badge.pending{background:var(--later-soft);color:var(--later);border:1px dashed var(--later)}
.dfd text{font-family:var(--font-body);fill:var(--ink)}
.dfd .nm{font-size:13px;font-weight:600}
.dfd .nm.v,.dfd .sub.v{fill:var(--later)}
.dfd .sub{font-size:11px;fill:var(--muted)}
.dfd .id{font-family:var(--font-mono);font-size:10.5px;fill:var(--accent);letter-spacing:.04em}
.dfd .fl{font-size:11.5px;paint-order:stroke;stroke:var(--panel);stroke-width:5px;stroke-linejoin:round}
.dfd .fl.v{fill:var(--later)}
.dfd .tbl{font-family:var(--font-mono);font-size:10.5px;fill:var(--muted);letter-spacing:.06em}
.dfd .ent{fill:var(--panel);stroke:var(--ink);stroke-width:1.3}
.dfd .ent.v{stroke:var(--later);stroke-dasharray:6 4}
.dfd .shade{fill:var(--shade)}
.dfd .prc-bg{fill:var(--panel)}
.dfd .band{fill:var(--accent-soft)}
.dfd .band.v{fill:var(--later-soft)}
.dfd .prc{fill:none;stroke:var(--accent);stroke-width:1.5}
.dfd .prc.v{stroke:var(--later);stroke-dasharray:6 4}
.dfd .sep{stroke:var(--accent);stroke-width:1}
.dfd .sep.v{stroke:var(--later)}
.dfd .st-bg{fill:var(--panel)}
.dfd .st{stroke:var(--ink);stroke-width:1.3}
.dfd .tb{fill:none;stroke:var(--muted);stroke-width:1.2;stroke-dasharray:3 5}
.dfd .f{fill:none;stroke:var(--ink);stroke-width:1.3}
.dfd .f.v{stroke:var(--later);stroke-dasharray:6 4}
.dfd .mk{fill:var(--ink)}
.dfd .mk-v{fill:var(--later)}
footer{font-size:.88rem;color:var(--muted);border-top:1px solid var(--line);padding-top:16px}
@media (max-width:640px){.page{padding-block:28px 48px;gap:44px}.feat td:first-child{width:auto}}
@media print{body{background:#fff}.page{padding:0;max-width:none}.diagram,.table-wrap{border:0}figure,tr{break-inside:avoid}h2,h3{break-after:avoid}}
"""

stat = lambda k, v: f'<div class="card"><span class="k">{E(k)}</span><span class="v">{E(v)}</span></div>'

doc = f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<title>Sign Setu System Design</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Archivo:wdth,wght@62..125,500..800&family=IBM+Plex+Mono:wght@400;500&family=IBM+Plex+Sans:wght@400;500;600&display=swap">
<style>{CSS}</style>
</head>
<body>
<div class="page">
<header>
<p class="eyebrow">System design · Release 1</p>
<h1>Sign Setu</h1>
<p class="deck">An offline Android app that helps a police officer communicate with a lost, rescued or kidnapped person who cannot communicate in words: often a child who is deaf, non-verbal, traumatised, or speaks another language. Officer and subject share the officer's phone. The app is a communication aid, not an interpreter, and never records statements.</p>
</header>

<section aria-labelledby="glance">
<div class="sec-head"><h2 id="glance">At a glance</h2>
<p class="muted">Counts are taken from the code and content in this release.</p></div>
<div class="cards">
{stat("Network permission", "None")}
{stat("Languages", "17")}
{stat("Character motions", "125")}
{stat("Prepared questions", "50 + 10")}
{stat("Picture cards", "64")}
{stat("Release APK", "47.0 MB")}
{stat("Unit tests", "120")}
{stat("Sign reading", "On device")}
</div>
</section>

<section aria-labelledby="feat">
<div class="sec-head"><h2 id="feat">What it does</h2>
<p class="muted">Officer and subject take turns with one phone, or the subject uses a paired second screen. The officer prepares or types something and shows it; the subject sees it on the stage and answers by tapping, drawing, writing or signing; the officer takes the phone back with one tap and reads the answer, its grade and its trail.</p></div>
{officer}
{subject}
{safety}
</section>

<section aria-labelledby="dfd">
<div class="sec-head"><h2 id="dfd">Data flow</h2>
<p class="muted">Gane–Sarson notation. Everything runs inside the officer's phone; the only data that leaves is the report the officer chooses to export, the contact link of an interpreter call, and the encrypted stage view sent to a paired stage device. Amber marks what comes later.</p></div>
<ul class="legend" aria-label="Diagram key">{legend}</ul>
<figure><div class="diagram">{l0.render()}</div>
<figcaption><b>Figure 1. Level 0, context.</b> The app exchanges data with two people on one screen, with the records custodian on export, and with the messaging app on an interpreter call. The department supplies the optional assistant model as a file. An optional stage device shows the stage and returns answers; it stores nothing.</figcaption></figure>
<figure><div class="diagram">{l1.render()}</div>
<figcaption><b>Figure 2. Level 1, inside the phone.</b> Session data exists only in D1, encrypted under a key released by 1.0. The assistant (6.0) works only on the officer's own typed words and its output is shown to the officer before anything reaches the subject. The link (7.0) sends only what is already logged and on stage; answers are logged in D1 before they are acknowledged.</figcaption></figure>
<figure><div class="diagram">{l2.render()}</div>
<figcaption><b>Figure 3. Level 2, process 3.0 (the stage).</b> 3.4 binds each input to what was on screen, so a gesture or sign meaning becomes an answer only when the subject picks it. 3.5 analyses camera frames only inside a sign window and discards each frame after the call; 3.6 keeps landmarks in D7, memory only, for replay during the session, and its reading reaches the officer as a proposal.</figcaption></figure>
</section>

<section aria-labelledby="dict">
<div class="sec-head"><h2 id="dict">Data dictionary</h2>
<p class="muted">IDs are stable and used by the threat model, DPIA and compliance matrix.</p></div>
{elements}
{stores}
{flows}
{boundaries}
</section>

<section aria-labelledby="stack">
<div class="sec-head"><h2 id="stack">Technology</h2>
<p class="muted">One Android module, platform libraries first. No networking libraries, analytics, crash reporting, Google Play services or WebView.</p></div>
{stack}
</section>

<section aria-labelledby="ready">
<div class="sec-head"><h2 id="ready">Readiness</h2>
<p class="muted">What stands between this build and production. Each gate opens when the one before it closes.</p></div>
{ready}
</section>

<footer>Sources: requirements (01), architecture (02), UX (03), security (04), avatar and gestures (05), test plan and report (06, 10), decisions D-01 to D-34 (07), release path (08), vision engine and sign reader (11), on-device assistant (12), and the code. Generated by docs/tools/build_system_design.py.</footer>
</div>
</body>
</html>
"""

with open(OUT, "w", encoding="utf-8", newline="\n") as f:
    f.write(doc)
print("written", len(doc))
