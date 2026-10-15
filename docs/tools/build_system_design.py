import html, sys

OUT = sys.argv[1]
E = lambda s: html.escape(s, quote=True)


class Svg:
    def __init__(self, w, h, label, mid):
        self.w, self.h, self.aria, self.mid, self.p = w, h, label, mid, []

    def add(self, s):
        self.p.append(s)

    def text(self, x, y, s, cls, anchor="middle"):
        self.add(f'<text class="{cls}" x="{x}" y="{y}" text-anchor="{anchor}">{E(s)}</text>')

    def lines(self, cx, cy, lines, lh=15):
        # lines: list of (text, cls); block is vertically centred on cy
        y0 = cy - (len(lines) - 1) * lh / 2 + 4.5
        for i, (s, cls) in enumerate(lines):
            self.text(cx, round(y0 + i * lh, 1), s, cls)

    def ext(self, eid, x, y, w, h, name, sub=None, v=False):
        if not v:
            self.add(f'<rect class="shade" x="{x+4}" y="{y+4}" width="{w}" height="{h}"/>')
        self.add(f'<rect class="ent{" v" if v else ""}" x="{x}" y="{y}" width="{w}" height="{h}"/>')
        self.text(x + 7, y + 13, eid, "id", "start")
        ls = [(n, "nm" + (" v" if v else "")) for n in name] + ([(sub, "sub")] if sub else [])
        self.lines(x + w / 2, y + h / 2 + 3, ls)

    def proc(self, pid, x, y, w, h, name, v=False):
        r = 8
        self.add(f'<rect class="prc-bg" x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}"/>')
        self.add(f'<path class="band" d="M{x},{y+20} V{y+r} Q{x},{y} {x+r},{y} H{x+w-r} Q{x+w},{y} {x+w},{y+r} V{y+20} Z"/>')
        self.add(f'<rect class="prc{" v" if v else ""}" x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}"/>')
        self.add(f'<line class="sep" x1="{x}" y1="{y+20}" x2="{x+w}" y2="{y+20}"/>')
        self.text(x + w / 2, y + 14, pid, "id")
        self.lines(x + w / 2, y + 20 + (h - 20) / 2, [(n, "nm") for n in name])

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
        self.add(f'<rect class="tb" x="{x}" y="{y}" width="{w}" height="{h}" rx="12"/>')
        self.text(tx, ty, text, "tbl", "start")

    def render(self):
        mk = lambda i, c: (f'<marker id="{i}" viewBox="0 0 10 10" refX="10" refY="5" markerWidth="8" markerHeight="8" '
                           f'markerUnits="userSpaceOnUse" orient="auto-start-reverse"><path class="{c}" d="M0,1 L10,5 L0,9 z"/></marker>')
        return (f'<svg class="dfd" viewBox="0 0 {self.w} {self.h}" role="img" aria-label="{E(self.aria)}">'
                f'<defs>{mk(self.mid, "mk")}{mk(self.mid + "-v", "mk-v")}</defs>{"".join(self.p)}</svg>')


# ---------- Level 0 ----------
l0 = Svg(1000, 400, "Level 0 context diagram. The police officer exchanges officer input and output with the Sign Setu application. "
         "The subject receives the presentation and returns subject input. The application sends the session report to the records custodian "
         "and a contact link to the messaging app. Language services exchange requests and results with the application in release 1.1.", "a0")
l0.proc("0", 410, 150, 180, 110, ["Sign Setu", "application"])
l0.ext("E1", 60, 177, 160, 56, ["Police officer"])
l0.ext("E2", 780, 177, 160, 56, ["Subject"])
l0.ext("E5", 410, 24, 180, 56, ["Language services"], "release 1.1", v=True)
l0.ext("E4", 60, 320, 180, 56, ["Messaging app"], "interpreter call")
l0.ext("E3", 760, 320, 180, 56, ["Records custodian"], "case file, CWC")
l0.flow([(220, 193), (409, 193)]); l0.label(315, 185, "officer input")
l0.flow([(410, 217), (221, 217)]); l0.label(315, 233, "officer output")
l0.flow([(590, 193), (779, 193)]); l0.label(685, 185, "presentation")
l0.flow([(780, 217), (591, 217)]); l0.label(685, 233, "subject input")
l0.flow([(480, 150), (480, 81)], v=True); l0.label(472, 120, "language request", "end", v=True)
l0.flow([(520, 80), (520, 149)], v=True); l0.label(528, 120, "language result", "start", v=True)
l0.flow([(572, 260), (759, 338)]); l0.label(690, 286, "session report")
l0.flow([(428, 260), (241, 338)]); l0.label(310, 286, "contact link")

# ---------- Level 1 ----------
l1 = Svg(1200, 770, "Level 1 diagram. Inside the device boundary: 1.0 authenticates the officer and releases the session key; "
         "2.0 handles officer requests using the catalogue in the content bundle; 3.0 presents content to the subject and returns the subject response; "
         "4.0 maintains the encrypted session store and the session log; 5.0 builds the session report and the interpreter contact link. "
         "Language services connect to 2.0 in release 1.1.", "a1")
l1.tb(196, 120, 784, 630, "TB1  DEVICE BOUNDARY (APP SANDBOX)", 700, 144)
l1.ext("E5", 240, 26, 170, 56, ["Language services"], "release 1.1, via gateway", v=True)
l1.ext("E1", 20, 270, 130, 344, ["Police", "officer"])
l1.ext("E2", 1030, 274, 140, 56, ["Subject"])
l1.ext("E3", 1030, 414, 140, 56, ["Records", "custodian"])
l1.ext("E4", 1030, 554, 140, 56, ["Messaging app"])
l1.store("D2", 500, 156, 170, 48, "Content bundle", "read-only")
l1.proc("2.0", 240, 270, 170, 64, ["Handle officer", "requests"])
l1.proc("3.0", 500, 270, 170, 64, ["Interact with", "subject"])
l1.proc("4.0", 240, 410, 170, 64, ["Maintain", "session record"])
l1.store("D1", 500, 418, 170, 48, "Session store", "encrypted")
l1.proc("5.0", 760, 410, 170, 64, ["Export and", "hand off"])
l1.proc("1.0", 240, 550, 170, 64, ["Authenticate", "officer"])
l1.store("D3", 500, 558, 170, 48, "Configuration")
l1.store("D4", 240, 680, 170, 48, "Key store", "Android Keystore")

l1.flow([(300, 270), (300, 83)], v=True); l1.label(292, 104, "language request", "end", v=True)
l1.flow([(350, 82), (350, 269)], v=True); l1.label(358, 104, "language result", "start", v=True)
l1.flow([(150, 294), (239, 294)]); l1.label(195, 274, "officer|request")
l1.flow([(240, 318), (151, 318)]); l1.label(195, 333, "officer view")
l1.flow([(500, 180), (395, 180), (395, 269)]); l1.label(447, 172, "catalogue")
l1.flow([(585, 204), (585, 269)]); l1.label(593, 242, "content assets", "start")
l1.flow([(410, 294), (499, 294)]); l1.label(455, 274, "subject|request")
l1.flow([(500, 318), (411, 318)]); l1.label(455, 333, "subject|response")
l1.flow([(670, 294), (1029, 294)]); l1.label(850, 286, "presentation")
l1.flow([(1030, 318), (671, 318)]); l1.label(850, 333, "subject input")
l1.flow([(325, 334), (325, 409)]); l1.label(317, 375, "session events", "end")
l1.flow([(395, 334), (395, 362), (830, 362), (830, 409)]); l1.label(600, 356, "export request")
l1.flow([(790, 410), (790, 382), (375, 382), (375, 335)]); l1.label(600, 397, "export result")
l1.flow([(240, 442), (151, 442)]); l1.label(195, 434, "session log")
l1.flow([(411, 442), (499, 442)], both=True); l1.label(455, 434, "records")
l1.flow([(670, 442), (759, 442)]); l1.label(715, 434, "records")
l1.flow([(930, 442), (1029, 442)]); l1.label(980, 434, "session report")
l1.flow([(900, 474), (900, 582), (1029, 582)]); l1.label(965, 574, "contact link")
l1.flow([(510, 558), (406, 475)]); l1.label(472, 514, "retention period", "start")
l1.flow([(660, 558), (776, 475)]); l1.label(706, 551, "interpreter contact", "start")
l1.flow([(150, 582), (239, 582)]); l1.label(195, 574, "credential")
l1.flow([(325, 550), (325, 475)]); l1.label(317, 516, "session key", "end")
l1.flow([(325, 680), (325, 615)]); l1.label(333, 651, "key material", "start")

# ---------- Level 2 (process 3.0) ----------
l2 = Svg(1000, 360, "Level 2 diagram of process 3.0. 3.1 resolves the subject request against content assets into a render set; "
         "3.2 presents it on the subject panel; 3.3 captures subject input; 3.4 validates the raw input against the display context "
         "and returns the subject response to 2.0. Only the presentation and subject input cross the subject panel boundary.", "a2")
l2.store("D2", 40, 18, 170, 44, "Content bundle")
l2.proc("2.0", 20, 98, 150, 238, ["Handle officer", "requests"])
l2.ext("E2", 830, 98, 140, 238, ["Subject"])
l2.proc("3.1", 250, 98, 170, 64, ["Resolve content"])
l2.proc("3.2", 520, 98, 170, 64, ["Present to", "subject"])
l2.proc("3.4", 250, 272, 170, 64, ["Validate", "response"])
l2.proc("3.3", 520, 272, 170, 64, ["Capture input"])
l2.add('<line class="tb" x1="800" y1="84" x2="800" y2="350"/>')
l2.text(806, 92, "TB0", "tbl", "start")
l2.flow([(210, 40), (335, 40), (335, 97)]); l2.label(272, 33, "content assets")
l2.flow([(170, 130), (249, 130)]); l2.label(210, 110, "subject|request")
l2.flow([(420, 130), (519, 130)]); l2.label(470, 122, "render set")
l2.flow([(690, 130), (829, 130)]); l2.label(745, 122, "presentation")
l2.flow([(830, 304), (691, 304)]); l2.label(745, 320, "subject input")
l2.flow([(520, 304), (421, 304)]); l2.label(470, 320, "raw input")
l2.flow([(540, 162), (402, 271)]); l2.label(488, 222, "display context", "start")
l2.flow([(250, 304), (171, 304)]); l2.label(210, 320, "subject|response")


# ---------- legend ----------
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
    ("ext", "External entity"), ("prc", "Process (identifier in the band)"), ("st", "Data store"),
    ("f", "Data flow"), ("tb", "Trust boundary"), ("v", "Release 1.1 element or flow")])


def table(cap, head, rows, cls=""):
    th = "".join(f'<th scope="col">{E(h)}</th>' for h in head)
    tr = "".join("<tr>" + "".join(f"<td>{c}</td>" for c in r) + "</tr>" for r in rows)
    return (f'<figure class="tbl {cls}"><figcaption>{cap}</figcaption><div class="scroll"><table>'
            f"<thead><tr>{th}</tr></thead><tbody>{tr}</tbody></table></div></figure>")


code = lambda s: f"<code>{E(s)}</code>"

scope = table("Table 1. Release scope", ["Capability", "Release"], [
    ["Subject profiles: young child, ISL user, other language, not sure", "1"],
    ["Triage questions with pictograms, captions and recorded audio", "1"],
    ["Gesture cards, officer gesture lookup and show-back confirmation", "1"],
    ["Language finder with recorded greetings", "1"],
    ["Human ISL clips for the triage set; animated gesture clips", "1"],
    ["Drawing, officer notes, encrypted session record", "1"],
    ["Password-protected PDF export; interpreter hand-off", "1"],
    ["Voice input and caption translation through the project gateway", "1.1"],
    ["On-device sign recognition (ISL users only)", "After pilot"],
    ["Statement recording, identity fields, face or emotion analysis", "Excluded"],
])

principles = table("Table 2. Design principles", ["Principle", "How the design applies it"], [
    ["Offline first", "The complete v1 flow runs without a network; the v1 build declares no INTERNET permission."],
    ["Data minimisation", "No identity fields. No camera or audio capture in v1. Only session events, notes and drawings are stored."],
    ["Device-local storage", "Session data is encrypted at rest (SQLCipher, AES-256) under a key wrapped by Android Keystore and excluded from backup and device transfer."],
    ["Officer in the loop", "Nothing reaches the subject panel without officer confirmation. A gesture meaning is logged only after the subject confirms it by show-back."],
    ["Least privilege", "Only the launcher activity is exported. No analytics, advertising or crash-reporting SDKs. Permissions are limited to those in use."],
    ["Bounded retention", "A session is purged when it ends or when the retention period expires (default 24 h, configurable 4–72 h)."],
    ["Graceful degradation", "Every output channel has a fallback, from interpreter call to pictograms; the failure of one channel never stops the session."],
    ["Separated views", "Officer notes and logs never render on the subject panel; the subject panel is full screen and pinned while facing the subject."],
])

elements = table("Table 3. External entities and processes", ["ID", "Name", "Description"], [
    ["E1", "Police officer", "Operates the device: authenticates, asks, records notes, exports."],
    ["E2", "Subject", "Person being helped; sees the subject panel and answers by touch."],
    ["E3", "Records custodian", "Case file or Child Welfare Committee; receives the exported report."],
    ["E4", "Messaging app", "Sandes or WhatsApp on the same device; receives the interpreter contact link only."],
    ["E5", "Language services (1.1)", "Speech recognition and translation through the project gateway (Bhashini)."],
    ["1.0", "Authenticate officer", "Device credential or strong biometric through BiometricPrompt; authorises the Keystore key that unwraps the session key."],
    ["2.0", "Handle officer requests", "Subject profile, question choice or free text, confirmation, gesture description, notes and session commands; shows responses."],
    ["3.0", "Interact with subject", "Renders content for the subject profile, captures input and confirms gestures by show-back (Figure 3)."],
    ["3.1", "Resolve content", "Selects pictograms, caption, clip and audio for the request and profile; for a gesture description, lists matching lexicon entries."],
    ["3.2", "Present to subject", "Renders the set full screen on the pinned subject panel and records what is displayed."],
    ["3.3", "Capture input", "Accepts card taps, drawing strokes and Yes or No."],
    ["3.4", "Validate response", "Binds input to the displayed item. A gesture candidate counts only after Yes; otherwise the response is logged as unclear."],
    ["4.0", "Maintain session record", "Writes each event before the interface updates; serves the session log; purges on session end or retention expiry."],
    ["5.0", "Export and hand off", "Builds the password-protected PDF report; opens the configured interpreter contact in the messaging app."],
], "w-desc ids")

stores = table("Table 4. Data stores", ["ID", "Store", "Contents", "Protection", "Retention"], [
    ["D1", "Session store", "Session events, notes, drawings", "SQLCipher (AES-256); key wrapped by D4; excluded from backup", "Until session end or retention expiry"],
    ["D2", "Content bundle", "Question sets, pictograms, captions, gesture and ISL clips, audio, gesture lexicon", "Read-only; shipped in the signed package", "Until app update"],
    ["D3", "Configuration", "Language defaults, retention period, interpreter contacts", "No personal data", "Until uninstall"],
    ["D4", "Key store", "Key-encryption key", "Android Keystore; non-exportable; bound to user authentication", "Until Clear all data or uninstall"],
], "ids")

flows = table("Table 5. Data flows", ["Flow", "From", "To", "Composition"], [
    ["officer input", "E1", "0", "credential + officer request"],
    ["officer output", "0", "E1", "officer view + session log"],
    ["credential", "E1", "1.0", "Device PIN, pattern, password or biometric, handled by the platform"],
    ["key material", "D4", "1.0", "Wrapped session key, unwrapped inside Keystore"],
    ["session key", "1.0", "4.0", "Database key, held in memory only"],
    ["officer request", "E1", "2.0", "Subject profile, question choice or free text, confirmation, gesture description, note, session command"],
    ["officer view", "2.0", "E1", "Subject response, export passphrase, status"],
    ["catalogue", "D2", "2.0", "Question sets and gesture lexicon index"],
    ["subject request", "2.0", "3.0", "Confirmed question or gesture description, with subject profile"],
    ["content assets", "D2", "3.0, 3.1", "Pictograms, captions, clips, audio, lexicon entries"],
    ["render set", "3.1", "3.2", "Items selected for display, including show-back candidates"],
    ["presentation", "3.0, 3.2", "E2", "Rendered question, cards, clip, audio, show-back candidate"],
    ["subject input", "E2", "3.0, 3.3", "Card tap, drawing stroke, Yes or No"],
    ["raw input", "3.3", "3.4", "Input event with position and time"],
    ["display context", "3.2", "3.4", "Items on screen when the input occurred"],
    ["subject response", "3.0, 3.4", "2.0", "Selected card, drawing, confirmed gesture meaning, or unclear"],
    ["session events", "2.0", "4.0", "Question shown, response, note or command, each with a timestamp"],
    ["session log", "4.0", "E1", "Ordered events of the current session"],
    ["records", "4.0, D1", "D1, 4.0, 5.0", "Read, append and purge of session events"],
    ["retention period", "D3", "4.0", "Hours before automatic purge"],
    ["export request", "2.0", "5.0", "Export command or interpreter request"],
    ["export result", "5.0", "2.0", "One-time passphrase and status"],
    ["interpreter contact", "D3", "5.0", "Messaging app and contact configured for the state"],
    ["session report", "5.0", "E3", "PDF encrypted with AES-256 under the one-time passphrase, shared out of band"],
    ["contact link", "5.0", "E4", "Contact identifier only; no session data"],
    ["language request (1.1)", "2.0", "E5", "Officer audio or question text"],
    ["language result (1.1)", "E5", "2.0", "Transcript or translation"],
], "w-flows ids")

boundaries = table("Table 6. Trust boundaries", ["ID", "Boundary", "Flows crossing", "Controls"], [
    ["TB0", "Subject panel on the shared screen", "presentation, subject input",
     "Subject panel full screen and pinned while facing the subject; notes and log never rendered on it; nothing shown without officer confirmation"],
    ["TB1", "Device: app sandbox on the officer's personal phone", "officer input and output, presentation, subject input, session report, contact link",
     f"App lock (1.0); encrypted D1; {code('FLAG_SECURE')}; backup and device transfer excluded; only the launcher activity exported; no INTERNET permission in v1; build expiry forces updates"],
    ["TB2", "Network to the project gateway (1.1)", "language request, language result",
     "TLS 1.2 or later with certificate pinning; key attestation; partner keys held only by the gateway; request bodies not stored"],
], "ids")

plan = table("Table 7. Delivery phases", ["Phase", "Objective", "Deliverables", "Exit criteria"], [
    ["0. Foundations", "Settle the design and remove external blockers",
     "Design decisions D-12 to D-19 accepted; legal adviser, ISL and child-welfare reviewers nominated; test devices; repository with protected main branch",
     "Decisions recorded; legal review requested"],
    ["1. Build", "Working offline application",
     "Subject profiles; triage questions; gesture cards and lookup; language finder; drawing and notes; encrypted session store; app lock; PDF export; interpreter hand-off",
     "All mandatory requirements pass on the oldest supported device"],
    ["2. Hardening", "Complete controls and content",
     "OWASP MASVS L2 control set; IS 17802 accessibility; language, ISL and child-welfare content review; animated gesture clips; signed DPIA",
     "No open critical or high findings; release candidate frozen"],
    ["3. Security audit", "Independent assurance",
     "CERT-In empanelled audit of source and binary; remediation; re-test",
     "Safe-to-host certificate; audited build hash recorded"],
    ["4. Pilot", "Field validation",
     "One district with trained officers, including the Special Juvenile Police Unit; incident and lost-phone drills; structured feedback",
     "Success criteria met; no privacy incident; department approval"],
    ["5. Rollout", "Scale and sustain",
     "State-wise release through the Mobile Seva AppStore; release 1.1 language services on pilot evidence; periodic internal and external audits",
     "Continuing"],
], "w-plan")

CSS = """
:root{--bg:#eef0f3;--paper:#ffffff;--ink:#1b2330;--muted:#5e6a78;--rule:#d8dde3;--band:#eef1f5;--shade:#c9cfd7;--accent:#2a5590;
--sans:"IBM Plex Sans","Segoe UI",Roboto,Arial,sans-serif;--mono:"IBM Plex Mono",Consolas,"Courier New",monospace}
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){--bg:#0d1117;--paper:#141a22;--ink:#e3e8ee;--muted:#98a3b2;--rule:#2a3340;--band:#1c2430;--shade:#2a3340;--accent:#86a8dc;color-scheme:dark}}
:root[data-theme="dark"]{--bg:#0d1117;--paper:#141a22;--ink:#e3e8ee;--muted:#98a3b2;--rule:#2a3340;--band:#1c2430;--shade:#2a3340;--accent:#86a8dc;color-scheme:dark}
*{box-sizing:border-box}
html{-webkit-text-size-adjust:100%}
body{margin:0;background:var(--bg);color:var(--ink);font:15px/1.6 var(--sans)}
.doc{max-width:1120px;margin:32px auto;background:var(--paper);border:1px solid var(--rule);
padding-block:44px 56px;padding-left:max(48px,env(safe-area-inset-left,0px));padding-right:max(48px,env(safe-area-inset-right,0px));display:grid;gap:44px}
h1,h2,h3{margin:0;font-weight:600;line-height:1.25;text-wrap:balance}
h1{font-size:1.9rem;letter-spacing:-.01em}
h2{font-size:1.25rem;padding-bottom:8px;border-bottom:1px solid var(--rule);display:flex;gap:14px}
h3{font-size:1rem;display:flex;gap:12px}
.n{font-family:var(--mono);font-weight:500;color:var(--accent);font-variant-numeric:tabular-nums}
p{margin:0;max-width:72ch}
.sub{color:var(--muted);font-size:1.05rem;margin-top:4px}
section{display:grid;gap:24px}
.block{display:grid;gap:14px}
code{font-family:var(--mono);font-size:.88em}
.control{border-collapse:collapse;font-size:.88rem;margin-top:20px;width:100%;max-width:720px}
.control th,.control td{border:1px solid var(--rule);padding:6px 10px;text-align:left;vertical-align:top}
.control th{width:150px;font-weight:500;color:var(--muted);background:var(--band)}
figure{margin:0;display:grid;gap:8px}
figure>figcaption{font-size:.85rem;color:var(--muted)}
figure.tbl>figcaption{font-weight:600;color:var(--ink);font-size:.88rem}
.fig figcaption b{color:var(--ink);font-weight:600}
.diagram{overflow-x:auto;border:1px solid var(--rule);padding:14px}
.diagram svg{display:block;width:100%;min-width:860px;height:auto}
.scroll{overflow-x:auto}
table{border-collapse:collapse;width:100%;font-size:.88rem}
thead th{text-align:left;font-weight:600;border-bottom:1.5px solid var(--ink);padding:7px 10px;white-space:nowrap}
tbody td{border-bottom:1px solid var(--rule);padding:7px 10px;vertical-align:top}
.ids td:first-child{font-family:var(--mono);font-size:.84rem;white-space:nowrap}
.w-desc td:nth-child(2),.w-plan td:first-child{white-space:nowrap}
.w-plan td:first-child{font-family:var(--sans);font-weight:600;font-size:.88rem}
.w-flows td:nth-child(2),.w-flows td:nth-child(3){font-family:var(--mono);font-size:.84rem;white-space:nowrap}
.legend{list-style:none;margin:0;padding:0;display:flex;flex-wrap:wrap;gap:8px 26px;font-size:.85rem;color:var(--muted)}
.legend li{display:flex;align-items:center;gap:8px}
.legend svg{width:36px;height:22px;flex:none}
.dfd text{font-family:var(--sans);fill:var(--ink)}
.dfd .nm{font-size:13px;font-weight:500}
.dfd .nm.v{fill:var(--muted)}
.dfd .sub{font-size:11px;fill:var(--muted)}
.dfd .id{font-family:var(--mono);font-size:10.5px;fill:var(--muted)}
.dfd .fl{font-size:11.5px;paint-order:stroke;stroke:var(--paper);stroke-width:4px;stroke-linejoin:round}
.dfd .fl.v{fill:var(--muted);font-style:italic}
.dfd .tbl{font-family:var(--mono);font-size:10.5px;fill:var(--accent);letter-spacing:.06em}
.dfd .ent{fill:var(--paper);stroke:var(--ink);stroke-width:1.25}
.dfd .ent.v{stroke:var(--muted);stroke-dasharray:5 4}
.dfd .shade{fill:var(--shade)}
.dfd .prc-bg{fill:var(--paper)}
.dfd .band{fill:var(--band)}
.dfd .prc{fill:none;stroke:var(--ink);stroke-width:1.25}
.dfd .sep{stroke:var(--ink);stroke-width:1}
.dfd .st-bg{fill:var(--paper)}
.dfd .st{stroke:var(--ink);stroke-width:1.25}
.dfd .tb{fill:none;stroke:var(--accent);stroke-width:1.2;stroke-dasharray:8 5}
.dfd .f{fill:none;stroke:var(--ink);stroke-width:1.15}
.dfd .f.v{stroke:var(--muted);stroke-dasharray:5 4}
.dfd .mk{fill:var(--ink)}
.dfd .mk-v{fill:var(--muted)}
footer{font-size:.82rem;color:var(--muted);border-top:1px solid var(--rule);padding-top:14px}
@media (max-width:720px){.doc{margin:0;border:0;padding-block:28px 40px;padding-left:max(16px,env(safe-area-inset-left,0px));padding-right:max(16px,env(safe-area-inset-right,0px));gap:36px}h1{font-size:1.55rem}}
@media print{body{background:#fff}.doc{margin:0;border:0;padding:0;max-width:none}.diagram{border:0;padding:0}figure,tr{break-inside:avoid}h2,h3{break-after:avoid}}
"""

doc = f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<title>Sign Setu System Design</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;500&family=IBM+Plex+Sans:wght@400;500;600&display=swap">
<style>{CSS}</style>
</head>
<body>
<main class="doc">
<header>
<h1>Sign Setu</h1>
<p class="sub">System design: data flows and delivery plan</p>
<table class="control">
<tr><th scope="row">Document</th><td>SS-SD-01</td></tr>
<tr><th scope="row">Version</th><td>0.3</td></tr>
<tr><th scope="row">Status</th><td>Draft for review</td></tr>
<tr><th scope="row">Classification</th><td>Restricted</td></tr>
<tr><th scope="row">Basis</th><td>Requirements 01; decision log D-01 to D-19</td></tr>
</table>
</header>

<section aria-labelledby="s1">
<h2 id="s1"><span class="n">1</span>Scope and design principles</h2>
<div class="block">
<h3><span class="n">1.1</span>Purpose</h3>
<p>Sign Setu is an Android application that lets a police officer communicate with a lost, rescued or kidnapped person who cannot communicate in words, often a child who is deaf, non-verbal, traumatised or speaks another language. Officer and subject share the officer's phone. The application is a communication aid: it does not interpret, and it never records statements.</p>
</div>
<div class="block">
<h3><span class="n">1.2</span>Release scope</h3>
{scope}
</div>
<div class="block">
<h3><span class="n">1.3</span>Design principles</h3>
{principles}
</div>
</section>

<section aria-labelledby="s2">
<h2 id="s2"><span class="n">2</span>Data flow</h2>
<div class="block">
<h3><span class="n">2.1</span>Notation</h3>
<p>Diagrams use Gane–Sarson notation. Processes are numbered by level, and each flow is named for the data it carries and defined in Table 5. Composite flows on Level 0 decompose into the named flows on Level 1.</p>
<ul class="legend">{legend}</ul>
</div>
<div class="block">
<h3><span class="n">2.2</span>Level 0: context</h3>
<figure class="fig"><div class="diagram">{l0.render()}</div>
<figcaption><b>Figure 1.</b> The application exchanges data with two people on one screen, with the records custodian when the officer exports, and with the messaging app when the officer calls an interpreter. Language services are added in release 1.1.</figcaption></figure>
</div>
<div class="block">
<h3><span class="n">2.3</span>Level 1: application</h3>
<figure class="fig"><div class="diagram">{l1.render()}</div>
<figcaption><b>Figure 2.</b> All v1 processing and storage sit inside the device boundary TB1. Session data exists only in D1, encrypted under a key released by 1.0 after the officer authenticates. The only v1 flows that leave the device are the session report and the contact link, both on officer action.</figcaption></figure>
</div>
<div class="block">
<h3><span class="n">2.4</span>Level 2: process 3.0, interact with subject</h3>
<figure class="fig"><div class="diagram">{l2.render()}</div>
<figcaption><b>Figure 3.</b> Only the presentation and the subject's input cross the subject panel boundary TB0. 3.4 checks each input against what was on screen, so a gesture meaning becomes a response only when the subject confirms the show-back candidate.</figcaption></figure>
</div>
<div class="block">
<h3><span class="n">2.5</span>Data dictionary</h3>
{elements}
{stores}
{flows}
</div>
<div class="block">
<h3><span class="n">2.6</span>Trust boundaries</h3>
{boundaries}
</div>
</section>

<section aria-labelledby="s3">
<h2 id="s3"><span class="n">3</span>Delivery plan</h2>
<p>Work proceeds in gated phases. Each phase starts when the previous phase meets its exit criteria.</p>
{plan}
</section>

<footer>Related documents: 01 Requirements, 02 Architecture, 04 Security, 05 Avatar and gestures, 07 Decisions, 08 Plan.</footer>
</main>
</body>
</html>
"""

with open(OUT, "w", encoding="utf-8", newline="\n") as f:
    f.write(doc)
print("written", len(doc))
