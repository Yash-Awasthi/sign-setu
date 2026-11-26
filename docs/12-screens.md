# 12. Screen Catalogue

Every screen of the application, captured from the 0.8.5 `standard` debug build on an Android 14 emulator (Pixel 9 profile, 1080 by 2424 pixels, scaled to 720 wide), with Hindi as the subject language unless stated; the number pad, writing, sign window and ASL screens use English. Only "A signed answer" is still the 0.8.1 capture: the emulator camera cannot sign. The emulator camera shows its test scene. The final release build blocks screenshots (`secureScreen`, [security](04-security.md) §2.5).

The same images are kept byte for byte in docs/screens/before/ as the baseline a later capture is compared with, and new captures go to docs/screens/after/.

## 1. Before a session

| Screen | File | Notes |
| --- | --- | --- |
| First run, step 1 | [01-first-run-1.png](screens/01-first-run-1.png) | Aid, not an interpreter; app language |
| First run, step 2 | [02-first-run-2.png](screens/02-first-run-2.png) | Sessions are deleted after the retention time |
| First run, step 3 | [03-first-run-3.png](screens/03-first-run-3.png) | Records stay on this phone; what goes online |
| Home | [04-home.png](screens/04-home.png) | Take the tour, their language, Start session; who the person is and their role are quick notes inside the session |
| Their language | [05-home-language.png](screens/05-home-language.png) | Seventeen languages in their own scripts, or "Do not know" to find theirs on the stage |
| Home, saved sessions | [06-home-saved-sessions.png](screens/06-home-saved-sessions.png) | A finished session with its date; tap to open, long-press to choose and delete, download to keep a copy |
| Practice | [07-practice-type-and-sign.png](screens/07-practice-type-and-sign.png) | Settings, Type and sign: any sentence signed outside a session, nothing recorded |
| Take the tour | [44-take-the-tour.png](screens/44-take-the-tour.png) | The last first-run step and the Start screen offer Take the tour, which opens the guided walkthrough |
| Sign to Type | [45-sign-to-type.png](screens/45-sign-to-type.png) | Settings, Practice: signing to the camera builds a sentence in English and Hindi from read words and held letters; a word's other guesses are a tap away; nothing is recorded |

## 2. The stage

The stage first shows only the character, the caption and the Stop octagon in the corner; a tap shows the rest for 5 s ([UX](03-ux.md) §2). These captures were taken with the buttons shown.

The captures predate one change to the answer row: Water and Food are no longer drawn there as well as in the pinned needs row, which now holds all six needs, each still one tap and reachable by a key or a switch. The row is now the primary answer, No and More, then the needs; the express board opens from the tool bar's emoji button.

| Screen | File | Notes |
| --- | --- | --- |
| Introduction | [08-stage-introduction.png](screens/08-stage-introduction.png) | The video call: character, camera window, caption, Stop and Next, Water, Food and Help |
| Ground rule | [09-stage-ground-rule.png](screens/09-stage-ground-rule.png) | Pictured and signed; only Next and Stop |
| Last ground rule | [10-stage-practice.png](screens/10-stage-practice.png) | "I did not see what happened": the last step of the opening, then the stage waits for the officer |
| Question | [11-stage-question.png](screens/11-stage-question.png) | Picture held up beside the character; answer by tap, nod, shake or signing |
| Typed text | [12-stage-typed-text.png](screens/12-stage-typed-text.png) | "I am not hungry" signed word by word, the head shaking for the negation |
| Drawing | [13-stage-drawing.png](screens/13-stage-drawing.png) | Five ink colours, Undo, Start again, Done, under the character |
| Writing | [14-stage-writing.png](screens/14-stage-writing.png) | The keyboard opens under the caption |
| Number pad | [15-stage-number-pad.png](screens/15-stage-number-pad.png) | Lesson-sheet input with finger-count shortcuts and the camera button |
| Read back | [16-stage-read-back.png](screens/16-stage-read-back.png) | "Is this right?": the answer becomes the person's own only on their Yes |
| Urgent | [17-stage-phrase-board.png](screens/17-stage-phrase-board.png) | The picture board's Urgent tab: pain, sick, doctor, hospital, medicine, toilet, cold, hot, a short rest |
| Help | [18-stage-help-board.png](screens/18-stage-help-board.png) | The full picture board by category |
| Pause | [19-stage-pause.png](screens/19-stage-pause.png) | Breathing circle (in 4 s, hold 2 s, out 6 s) and bubbles; only "I'm ready" ends it |
| Body map | [46-stage-body-map.png](screens/46-stage-body-map.png) | Help, Body: front and back figure; a touch answers as pain with its exact place. The picture board, and this map, turn with the stage when it is rotated; Back closes the board |
| Sign window | [20-stage-signing.png](screens/20-stage-signing.png) | "Signing…" on the camera window and Done; the hands are read until they rest |
| Language finder | [21-stage-language-finder.png](screens/21-stage-language-finder.png) | Seventeen languages in their own scripts |

## 3. The officer page

| Screen | File | Notes |
| --- | --- | --- |
| Transcribe | [22-officer-transcribe.png](screens/22-officer-transcribe.png) | Conversation with grades and trails; Ask next, quick questions, follow-ups, composer |
| Ready to show | [23-officer-ready-to-show.png](screens/23-officer-ready-to-show.png) | A prepared question with its media marks; the Stop banner |
| Typed text with Hindi | [24-officer-typed-hindi.png](screens/24-officer-typed-hindi.png) | Instant on-phone Hindi caption, marked unchecked; Signs, Gestures, Spell and Pictures toggles |
| A signed answer | [25-officer-signed-answer.png](screens/25-officer-signed-answer.png) | The app's reading with its draft sentence, logged apart from the person's answers |
| More | [26-officer-more.png](screens/26-officer-more.png) | Manual or Auto, search, categories, other ways to ask, session tools |
| Question group | [27-officer-question-groups.png](screens/27-officer-question-groups.png) | Personal details questions in English and Hindi |
| They gestured | [28-officer-they-gestured.png](screens/28-officer-they-gestured.png) | Where the hand went and what it did |
| Picture board | [29-officer-picture-board.png](screens/29-officer-picture-board.png) | Categories, search and favourites |
| ISL dictionary | [30-officer-isl-dictionary.png](screens/30-officer-isl-dictionary.png) | Recorded and drawn signs, searchable in English and Hindi |
| ASL dictionary | [31-officer-asl-dictionary.png](screens/31-officer-asl-dictionary.png) | Shown when the session signs in ASL |
| ASL notice | [32-officer-asl-advisory.png](screens/32-officer-asl-advisory.png) | The officer is told how well ASL is read |
| Session menu | [34-officer-menu.png](screens/34-officer-menu.png) | Sign language, sitting opposite, skeleton on camera, Help. Finishing is the header's End button |
| In-call Help | [47-officer-help.png](screens/47-officer-help.png) | Menu, Help: the call topic of the in-app guide, full screen |
| Quick notes | [35-officer-change-profile.png](screens/35-officer-change-profile.png) | Age, victim or accused, sign language seen, speech, hearing, home, injuries, gender: one tap each |
| Second screen | [36-officer-second-screen.png](screens/36-officer-second-screen.png) | Pairing a department device; Find a nearby phone pairs one from inside the app on Android 12 and later |
| Record their voice | [48-officer-record-voice.png](screens/48-officer-record-voice.png) | The microphone in the dock: consent first, then tap to start, the person speaks, tap to stop; the recording joins the record |
| Finish | [37-officer-finish.png](screens/37-officer-finish.png) | Summary, name, and three ways out: Thank them on the stage, Save and finish, Delete without saving (§5) |
| Report ready | [38-officer-export.png](screens/38-officer-export.png) | Share, Done; the PDF has no password |

## 4. Settings

| Screen | File | Notes |
| --- | --- | --- |
| Settings, top | [39-settings-1.png](screens/39-settings-1.png) | Help and walkthrough, Type and sign, Sign to Type, Record signs, interpreter call |
| Settings, security and stage | [40-settings-2.png](screens/40-settings-2.png) | Pin the app, block screenshots, fingerprint; camera on the stage, gesture speed |
| Settings, online | [41-settings-3.png](screens/41-settings-3.png) | ISL order, 3D character; online assistant, station computer and this phone's link ID |
| Settings, offline and records | [42-settings-4.png](screens/42-settings-4.png) | On-device model and sentence helper; retention, Check a report, Delete all data |
| Settings, about | [49-settings-5.png](screens/49-settings-5.png), [50-settings-6.png](screens/50-settings-6.png) | Appearance, app language, licences, version; what ISL and ASL read and sign, and how well |
| Help and walkthrough | [52-help-walkthrough.png](screens/52-help-walkthrough.png) | Searchable topic list: tour, call, chat, settings, records, practice, station computer, privacy; the tour has Next, Back, Done and step dots. Text in [19-walkthrough.md](19-walkthrough.md) |
| Appearance | [43-settings-appearance.png](screens/43-settings-appearance.png) | Light or dark and the ten colour themes |

## 5. Ways out of a session

The owner's rule: no screen offers more than three divergent paths, where a divergent path is a control that ends, leaves
or discards the session. "Keep going" is on the finish screen but diverges nowhere: it puts the officer back where they
were. Measured with `tools/ux/count_paths.py` (the static inventory) and `WrapUpPathsDeviceTest` (what each state really
shows, on the emulator):

| Screen and state | Ways out | Which |
| --- | --- | --- |
| Finish, a session kept in history | 3 | Thank them on the stage, Save and finish, Delete without saving |
| Finish, a rooted phone that keeps no history | 3 | Thank them on the stage, Export report, Delete without saving |
| Finish, a saved session opened again | 3 | Thank them on the stage, Save and finish, Discard unsaved conversation |
| The session menu | 1 | Finish |

Export left the finish screen for a kept session: the same sealed PDF is written to `Documents/SignSetu` from the saved
session's download on the home screen, so offering it twice was one route too many. A rooted phone has no history to
download from, and there the export stays. Deleting still takes two taps.

## 6. Not captured

| Screen | Reason |
| --- | --- |
| Unlock prompt | The system credential window is secure and captures blank |
| Stage device, pairing code | Needs two devices |
| AI counsellor | Needs the online assistant enrolled with a department code |
