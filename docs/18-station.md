# The station

The station is one computer at the police station that runs the brain (`brain/`). It does, on the department's
own hardware and for every officer paired with it, what a phone alone does less well or cannot do:

| The station | The phone alone | The gateway (optional, online) |
| --- | --- | --- |
| Speech to text, whole takes and live ([17](17-live-speech.md)) | Records the voice; no text | None |
| ISL to text: its own sign reader, read first | Its own reader, always running beside the station's | None |
| Sentence help and AI help: its local model (Ollama) for captions, sentence ranking, the counsellor and the report summary | Its imported models: captions and sentence ranking | The same help, through the department gateway or a custom endpoint |
| Takes whole conversations into its inbox | Keeps them until deleted or expired | None |

Phones reach the station only over iroh ([16](16-relay.md)): directly on the station network, through the
department's relay from anywhere else. Without the station link a phone works on its own.

## 1. The station link on the phone

The link is off by default and stays off until the officer turns it on: an open connection and its heartbeat keep
the phone's radio awake, which costs battery for nothing when no station is needed.

- The switch is the Station computer row in Settings, Local AI brain, like the online assistant's. Turned on with no
  station paired, it opens the pairing first; on Android 17 it asks for access to devices on this network, without
  which Android drops every connection to a LAN address. While on, the row says Connecting or Connected, and the
  station's services are listed under it.
- The link runs only while the app is in front. Sent behind another app, the phone closes the connection; it opens
  it again when the officer comes back.
- Turning it off closes the iroh endpoint at once. A change of network does the same, and the phone dials again when
  it next needs the station.
- The phone's iroh identity is kept for the install and survives restarts ([16](16-relay.md), section 5). It uses the
  relay its pairing ticket names; there is no relay setting on the phone. To pair, paste the ticket (line breaks are
  fine) and the token, or the station network's `https://` address, token and pin. Replies larger than 8 MB are refused
  on both paths.

## 2. The heartbeat: nothing is asked for that the station has not offered

A phone treats the station as offering nothing until the station says otherwise. With the link on, the phone opens
one stream, `GET /v1/beat`, and the station writes its status on it every 5 seconds (`brain/link.py`). Each beat
is a fresh `/v1/status`, so the token is checked every time; a wrong token gets one 401 and the stream ends.

```json
{"caps": {"asr": true, "live": true, "isl": true, "llm": false, "gloss": true, "archive": true}, "isl_model": "sign_reader", ...}
```

| Capability | Offered when | What the phone may then send |
| --- | --- | --- |
| `asr` (and `live`) | The speech model (`--asr`) has loaded | Speech to text, one service on the phone: the words shown live while the person speaks, and the whole take's text kept after |
| `isl` | A sign reader is in the `--isl` folder | Each sign's landmarks, as it is read |
| `llm` | Ollama answers with the model named by `--llm` | Captions, sentence ranking, counsellor, report summary |
| `gloss` | Always: rules, refined by the model when it answers | Sign order for typed text, asked only while the phone's "Sign in ISL order" is on; it has no switch of its own |
| `archive` | Always (the inbox is `--inbox`, or Sign Setu in Documents) | Whole conversations |

Every feature on the phone asks `can(feature)` before it sends anything (`BrainClient.kt`), and it is true only when
all four hold: the station link is on, the latest heartbeat offers the feature, the officer has not turned it off on
this phone, and it has not failed since the last heartbeat. Otherwise the phone does without the station, with no wait.
A station that is stopped or loses its network sends no goodbye: with no beat for 12 seconds the phone counts it gone,
says Connecting, and tries again after 2 seconds, then twice as long each time, up to 10 seconds.

When a service breaks on the station (a model that stops loading or answering, Ollama gone, the inbox's disk full),
the station answers that request with 503 and `{"unavailable": "<service>"}`, logs it, and leaves the service out of
its heartbeat for 60 seconds. Bad input is not a broken service: it gets 422 and the service stays offered. On the
phone, one failed call (a 503, any other refusal, or no reply in time) stops that service at once until the next
heartbeat, which then says whether it is back. So a service that breaks mid-conversation costs one request, and the
phone does that job alone from the next one.

Settings, Local AI brain, lists the station's services (the in-app Help, under Station computer, walks through pairing). Those the station has are on, with a switch to keep one on
this phone instead; those it does not have, or all of them while the link is off, are dimmed and off, and a tap says
why.

## 3. Reading signs: the station first, the phone beside it

When a sign ends, the phone sends its landmarks to the station's reader (`POST /v1/sign`) and runs its own reader at
the same time. The station's answer is used when it comes back within 2.5 seconds; otherwise, or when it fails, the
phone's own reading stands. The phone waits only when `can(ISL)` holds (section 2); otherwise it reads alone, with no
wait.
The one wait the officer notices is on the last sign, after the hands drop; the earlier signs are read while the person
is still signing. Only ISL signs go
to the station. The phone's conformal thresholds belong to the phone's reader, so a station reading is ranked
without them.

The station's reader is any model in the phone's format 1, a spec `.json` beside its `.bin` weights as
`tools/ml/export.py` writes them, placed in the `--isl` folder by the department. Who trains it and which one is used is
the department's choice; the brain only runs it. Its features and forward pass (`brain/brain/signs.py`) match the
phone's exactly: on the five cases of `app/src/test/resources/sign_reader_fixture.json` the logits agree with the
phone's to within 0.00001.

`POST /v1/sign` takes `{"aspect": width/height, "frames": [...], "top": 20}` and returns
`{"guesses": [{"word", "score"}], "model"}`. A frame is the same as in an exported conversation (section 5).

## 4. AI help: station, then phone, then gateway

| Help | First | Then | Last |
| --- | --- | --- | --- |
| Caption for the officer's sentence, and its English check | Station model | Phone's assistant model | Gateway or custom endpoint |
| Ranking the checked sentences of a signed answer | Station model | Phone's sentence helper | None |
| Sentence suggestions from sign guesses | Station model | None | Gateway or custom endpoint |
| Counsellor, report summary | Station model | None | Gateway or custom endpoint |

The station is the department's own computer, reached end-to-end encrypted, so text goes to it unmasked. Text going to
the gateway is still masked, and the consent asked before session text leaves the phone for the gateway is unchanged.
The counsellor and the summary are offered when either the station's model or the gateway is there.

## 5. Sending conversations to the station

In the home screen's saved sessions, a tap opens a session; a long press starts choosing, with a box on each row.
While choosing, the header holds three buttons: delete the chosen ones (after the officer's screen lock), choose all
or none, and send to the station. Sending with the station not connected, or to a station that has not offered its
inbox, says so and sends nothing.

Each conversation goes as one zip (`export/StationExport.kt`), in pieces of 2 MB (`POST /v1/archive`, then
`/v1/archive/{id}?at=offset` for each piece, then `/v1/archive/{id}/done`). A piece whose reply was lost is sent
again and taken once. The station checks the whole file's SHA-256 before it keeps it, and saves it as it came, as
`<title> <first 8 characters of the session id>.zip` (spaces and marks other than letters, digits, dots and
dashes become underscores; a title in any script stays readable), in its inbox folder. The phone keeps the session; it is the
officer's to delete.

In the history each saved session has two buttons: download saves `Documents/SignSetu/<name>/` on the phone with the
zip and the encrypted PDF report (its passphrase is shown once), and upload sends the zip to the station; upload is shown
only while the station is connected and takes conversations. A small "i" on a button means a copy made at the session's
current seal exists; tapping it shows the file and its full path, on the phone or on the station computer, and offers
to do it again. A long press chooses sessions to delete.

A saved session opened again by mistake loses nothing: ending it without saving ("Discard unsaved conversation")
removes only the rows added since it was opened and seals it as it was, under its title.

Sending a session again is safe. A session's record only grows (a reopened session adds rows, never changes them), so
when the rows of an earlier zip for the same session, by their hash-chain links, are where the new zip starts, the new
one replaces it, under the session's current title. The same rows sent twice are kept once. A zip whose rows differ
from the start is kept beside the other, as `<name>-1.zip`, for a person to look at.

| File in the zip | What it holds |
| --- | --- |
| `README.txt` | What every file is, and how to check the hash chain |
| `session.json` | Title, times, language, role, and the seal: Merkle root, signed line, signature, device key |
| `events.json` | Every row in the order written, with its hash and the file it holds |
| `drawings/<id>.png` | Drawings, and the stick figures kept with signed answers |
| `audio/<id>.wav` | The person's recorded voice, 16 kHz mono 16-bit |
| `signs/<id>.json.gz` | The raw landmarks each signed answer was read from, gzipped JSON |

A file in `signs/` holds the word read for each sign and its frames. Android reads no database value over 2 MB, so a
long window (Tell me runs to three minutes) keeps every second frame, then every fourth, until it fits, and the file's
`every` says which. A frame is `{"t", "left", "right", "pose"}`, plus `browsUp`, `mouthOpen`, `yaw`, `pitch` and `mouth` when
the face model gave them: hands as 21 (x, y) and the pose as 33 (x, y), MediaPipe normalised image points, flat, null
when missing. The face mesh is never kept, only those cues, and no camera picture is ever kept.

The inbox is a plain folder. Protecting it (disk encryption, who may open it, how long files stay) is the
department's, as for any case file on that computer.

## 6. Raw landmarks on the phone

Raw landmarks used to live in memory only. Now the landmarks the app read a signed answer from are kept with that
row: encrypted with the session database, sealed in the hash chain (the row's seal gains `|signs` and the file's
SHA-256, as audio does), and deleted with the session, by the officer or by retention. They are what the station
needs to read the signs again with a better model. Landmarks outside a sign window are still never kept.

## 7. Setting up a station

On the station computer, once, from `brain/` (see [brain/README.md](../brain/README.md) for the rest):

```powershell
.\setup.ps1 -Asr <model> -Relay https://relay.example.gov.in -Service -Firewall      # Windows, as administrator
```
```sh
./setup.sh --asr <model> --relay https://relay.example.gov.in --service              # Linux
```

The only thing left to add is the sign reader, when the department has one: its `.json` and `.bin` go in `brain/isl/`
(or the folder `-Isl` names), then `stop` and `start -Service`. Without one the station offers no sign reading and
phones read alone. `-Inbox` defaults to `Sign Setu` in `OneDrive\Documents` when OneDrive is set up, else in `Documents` (Linux: `~/Documents/Sign Setu`); OneDrive syncs it to Microsoft, so a department that must keep it in-house sets `-Inbox`. `status` shows whether the brain runs, what it
offers, how to pair a phone and the end of its log; `stop`, `start -Service` and `remove` do what they say, on both
systems. A model for AI help is the Ollama model `-Llm` names; without Ollama, AI help goes to
the phone and the gateway. On start the brain prints what it found:

```
Sign reader: sign_reader, 314 words
Conversations from phones are saved in D:\signsetu\inbox
Pair a phone from anywhere: ticket endpoint…  token …
```

The station only needs to be on while officers use it; phones find it again by the heartbeat when it comes back.

## 8. How it was tested

On one computer with the iroh relay in `--dev` mode and the brain behind it, a client dialing the ticket through the
relay only:

- Heartbeat: the first beat at once, then one every 5.0 seconds; a wrong token got 401 and the stream ended.
- `/v1/sign` with the fixture cases gave the same words and probabilities as the phone's reader.
- A 9 MB conversation sent in 4 MB pieces, the first piece sent twice, arrived whole and was saved under its
  title; a file whose hash did not match was refused and deleted.
- The app builds and its unit tests pass. The phone's side has not been run on a phone.

## 9. Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| The row says Connecting and stays there | The station is off or unreachable, the token or ticket is wrong, or the phone is on another network and the brain has no relay ([16](16-relay.md)). Run `status` on the station; check that `Brain:` says running |
| Connected, but a service is dimmed | The station does not offer it: no `--isl` reader (`isl`), Ollama not running or lacking the `--llm` model (`llm`), or the speech model still loading (`asr`, `live`). A tap on the row says why |
| A service switched itself off | It failed once (503, refusal, timeout) and stays off until the next heartbeat, within 5 seconds; a broken service is left out of the heartbeat for 60 seconds |
| Sending a conversation says the station is not connected or takes no conversations | The link is off, or the station's inbox is not offered; check `status` |
| A conversation sent twice shows as two zips | The second differs from the first from its start, so it is kept as `<name>-1.zip`; sending the same rows again replaces nothing |
| Sign reading is slower than usual | The station answers after the 2.5 second limit; the phone's own reading then stands. Check the station's load |
| `status` shows a Reader line "none" | Put the reader's `.json` and `.bin` in `brain/isl/` and restart the brain |
| Nothing pairs after a rotated token | Every phone must enter the new token from `status` |
| Android drops LAN connections | Allow access to devices on this network when the phone asks (Android 17) |
