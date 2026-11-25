# 22. Screen inventory

Every screen the app can open, with its purpose, its three primary actions and its exit. A screen
without a purpose is removed; this review found none: the debug-only Record signs screen is kept
because it is how a Deaf contributor records a consented sign, and the stage device is kept because
it is the second screen a department pairs.

The navigation in `app/src/main/java/app/signsetu/ui/AppRoot.kt` is the source of truth. Each
`entry<...Key>` there needs a row below, so a screen added or deleted without this table fails the
check: `python tools/ux/check_inventory.py`.

## Before the navigation

| Screen | Purpose | Three primary actions | Exit |
| --- | --- | --- | --- |
| First run | Three steps before the first session: what the app is for, what is kept, and what goes online | Choose the app language; Read what is deleted and when; Read what goes online | Done opens Home; Take the tour opens the walkthrough |
| Unlock | The officer's own screen lock, before any record is opened | Unlock with the device credential; Use this phone as a stage; Wait for a tap on a stage device | Unlock opens Home |
| Stage device | The paired second screen the person watches, with no records on it | Show the caption; Answer with the picture board; Stop | Back returns to the pairing code |

## Routes

| Screen | Purpose | Three primary actions | Exit |
| --- | --- | --- | --- |
| `StartKey` | The officer's home: who is here, in their language, and what to do next | Start session; Their language; Take the tour | Back closes the app; a session opens over Home and its own finish screen returns here |
| `SettingsKey` | Officer-only options, kept off the person's screens | Type and sign; Help and walkthrough; Appearance | Back returns to Home |
| `PracticeKey` | Rehearse a sentence, signed or typed, with nothing recorded | Type a sentence; Sign it; Hear it | Back returns to Settings |
| `SignToTypeKey` | Sign to the camera and read the sentence back in English and Hindi | Start or stop reading; Clear; Say it in English | Back returns to Settings |
| `RecordSignsKey` | Record a consented contribution clip for the sign lexicon; debug builds only | Start recording; Redo; Save and queue | Back returns to Settings |
| `HelpKey` | The searchable guide, including the first-run tour | Search topics; Open a topic; Next or Done | Back returns to the screen Help was opened from |
| `AppearanceKey` | Light or dark, the ten colour themes and less motion | Pick light, dark or device default; Pick a colour theme; Turn on less motion | Back returns to Settings |
| `SessionKey` | The interview: the person on the stage, the officer's page beside it | Ask next; Show on the stage; Finish | The finish screen offers its three ways out; Back asks for the officer's unlock |

## Inside a session

`SessionKey` holds two faces of one screen: the stage the person sees and the officer page. Their
internal panels (question groups, picture board, dictionaries, body map, quick notes, summary) are
opened as sheets inside the session, not as routes, and each closes back to the face that opened it.

## Not in this inventory

| Screen | Reason |
| --- | --- |
| System credential window | Owned by the phone, not the app |
| System file picker and share sheet | Owned by the phone, not the app |
