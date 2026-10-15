# 01. Requirements

## 1. Purpose

Police officers find lost children and adults, and rescue people from kidnapping or trafficking, who cannot communicate in words: they are deaf, non-verbal, too traumatised to speak, or speak a language the officer does not know. Sign Setu lets the officer ask simple questions and understand the answers on one shared phone while formal support is arranged.

The app is a communication aid. It does not interpret, it does not replace an ISL interpreter, special educator or counsellor, and it never records statements.

## 2. Users

| User | Description |
| --- | --- |
| Officer | Holds the phone and controls the app. Under time pressure, possibly outdoors, may have limited English. Uses a personal phone. |
| Subject | The person being helped, often a child. May be frightened, deaf, unable to read, or speak another language. Sees only the subject panel. |

The officer chooses a **subject profile** for each session; it decides what the subject sees and hears.

| Profile | Subject sees and hears |
| --- | --- |
| Young child | Picture cards, child character performing gestures, recorded audio in the subject language |
| ISL user | Picture cards, captions, human ISL clips, adult character phrases, interpreter call |
| Hearing, other language | Language finder, picture cards, recorded audio in the chosen language |
| Not sure | Picture cards, Yes and No, drawing |

## 3. Constraints

| Constraint | Detail |
| --- | --- |
| Devices | Android 10 (API 29) and later, 3 GB RAM or more, screens 5 to 6.9 inches, portrait. Officers' personal phones; no device management. |
| Network | Release 1 works fully offline and declares no INTERNET permission (D-15). |
| Cost | Free and open-source components and free government services only (D-10). |
| Platform | Native Android, Kotlin, Jetpack Compose (D-01). |
| Distribution | Signed APK through the Mobile Seva AppStore or a department download page. |
| Law | DPDP Act 2023 and Rules 2025; Juvenile Justice Act 2015 section 74; POCSO Act sections 24 and 26; RPwD Act with IS 17802. Details in [governance](governance/). |
| Assurance | CERT-In empanelled security audit before production. |

## 4. Functional requirements

Priority: **MUST** (release 1 cannot ship without it), **SHOULD** (release 1 if possible), **LATER**.

### 4.1 Session and screen

| ID | Requirement | Priority |
| --- | --- | --- |
| FR-02 | The officer can rotate the Show state 180 degrees for a subject sitting opposite. | MUST |
| FR-03 | The app has two states: **Officer** (officer panel full screen) and **Show** (subject panel full screen, facing the subject). One tap switches between them. | MUST |
| FR-05 | Opening the app resumes the unfinished session or starts a new one. | MUST |
| FR-06 | Ending a session offers export, then purges all session data. | MUST |
| FR-07 | The officer sets the subject language per session (Hindi and English at minimum). | MUST |
| FR-60 | The officer sets a subject profile per session and can change it at any time. | MUST |
| FR-61 | From the first Show state until the session ends, the app stays pinned (screen pinning) when the device allows it; first run guides the officer to turn on app pinning. Returning from the Show state to the Officer state requires the officer's biometric or device credential. | MUST |

### 4.2 Asking

| ID | Requirement | Priority |
| --- | --- | --- |
| FR-10 | Preset triage questions are grouped: safety, health, needs, identity, family, location. | MUST |
| FR-11 | A preset question shows in the Show state with its picture cards and caption, plus by profile the gesture card or clip, human ISL clip, or recorded audio. No network is used. | MUST |
| FR-12 | Sensitive questions ("what happened", "who hurt you") sit behind a deliberate second tap and never appear on the first screen. | MUST |
| FR-13 | The officer can type a free-text question. It is shown as a caption with officer-chosen picture cards and is never signed. | MUST |
| FR-15 | Nothing reaches the Show state until the officer confirms it. | MUST |
| FR-17 | When a gesture, clip or audio item is missing, the picture and caption are shown alone and the officer sees "not available". | MUST |
| FR-18 | Clips and audio can be replayed and played at slow speed. | MUST |
| FR-19 | The officer can show any card from the full picture board, with categories, search and favourites. | MUST |
| FR-62 | Phrase bank: reviewed sentences with slots ("Do you want [item]?") play as a sequence of lexicon clips. | SHOULD |
| FR-63 | Language finder: tiles that each play a recorded greeting in one language; the subject's choice sets the subject language. | MUST |
| FR-64 | Call an interpreter opens a video call in the configured messaging app (Sandes or WhatsApp) to the state's contact. The button is hidden when no contact is configured. No session data is passed. | MUST |

### 4.3 Answers

| ID | Requirement | Priority |
| --- | --- | --- |
| FR-20 | Yes and No are always available as large cards during a question. | MUST |
| FR-21 | When the subject taps a card, the officer sees the card and its label in the officer language. | MUST |
| FR-24 | The subject can draw on a canvas with pen, eraser and clear; the officer can save the drawing to notes. | MUST |
| FR-65 | Gesture lookup: the officer records where the hand went (body location) and what it did (hand action); the app lists matching lexicon entries with picture and clip. | MUST |
| FR-66 | Show-back: the officer sends a candidate meaning to the Show state with "Is this it?" and Yes/No. Only Yes logs the meaning; anything else is logged as unclear. | MUST |

### 4.4 Records

| ID | Requirement | Priority |
| --- | --- | --- |
| FR-30 | Every question shown and answer received is logged with a timestamp. | MUST |
| FR-31 | The officer can add free-text notes. | MUST |
| FR-32 | Export produces a PDF encrypted with AES-256 under a one-time passphrase shown once to the officer, shared through the Android share sheet. | MUST |
| FR-33 | Unexported sessions are purged after the retention period (default 24 hours, configurable 4 to 72 hours). | MUST |
| FR-34 | Every event is saved before the interface updates, so a crash or restart loses nothing. | MUST |

### 4.5 Settings and first run

| ID | Requirement | Priority |
| --- | --- | --- |
| FR-50 | First run explains the app in three screens (an aid, not an interpreter), sets the officer language, requires a secure device lock, and guides the officer to enable app pinning. | MUST |
| FR-51 | Settings: officer language, text size, clip speed, retention period within department limits, clear all data, about and open-source licences. | MUST |
| FR-52 | The "aid, not an interpreter" notice appears on first run and in About. | MUST |
| FR-67 | A build stops starting new sessions 180 days after its build date and shows "Update required"; existing sessions can still be exported and cleared. | MUST |

### 4.6 Later releases

| ID | Requirement | Release |
| --- | --- | --- |
| FR-14 | Voice question with editable transcript (Bhashini through the project gateway). | 1.1 |
| FR-68 | Machine translation of free-text captions, marked as unreviewed. | 1.1 |
| FR-22 | On-device ISL sign recognition for ISL users, rear camera, always followed by show-back. | After pilot |

Withdrawn from earlier drafts: FR-01 (split screen, replaced by FR-03), FR-04 (divider), FR-16 (free text signed by a skeleton avatar), FR-23 (camera confidence display), FR-25 (echo, replaced by FR-66), FR-40 to FR-43 (network and permission fallbacks; release 1 uses neither).

## 5. Non-functional requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| NFR-01 | Cold start to usable Officer state under 2 seconds on the oldest test device. | MUST |
| NFR-02 | A preset question appears in the Show state within 300 ms of confirmation. | MUST |
| NFR-03 | Clips play at their source frame rate without visible stutter on the oldest test device. | MUST |
| NFR-05 | APK under 30 MB for the base content set. | MUST |
| NFR-06 | Memory under 300 MB during a session. | SHOULD |
| NFR-10 | Android 10 (API 29) and later, 3 GB RAM or more. | MUST |
| NFR-11 | Portrait, screens 5 to 6.9 inches. | MUST |
| NFR-20 | Touch targets at least 48 dp; subject cards at least 96 dp. | MUST |
| NFR-21 | Text contrast at least 4.5:1; meaning never carried by colour alone. | MUST |
| NFR-22 | Officer panel usable at 200% system font scale. | MUST |
| NFR-23 | Officer panel works with TalkBack. | SHOULD |
| NFR-24 | Subject panel: no timers, flashing or pop-ups; sound only on officer action or by profile. | MUST |
| NFR-25 | All text from resources; Devanagari and the supported regional scripts render correctly. | MUST |
| NFR-30 | Session data encrypted at rest under a key protected by Android Keystore. | MUST |
| NFR-31 | Opening the app and past sessions require device credential or strong biometric. | MUST |
| NFR-32 | Screenshots, screen recording and recents thumbnails blocked. | MUST |
| NFR-33 | No session data in backups or device transfer. | MUST |
| NFR-34 | Release 1 declares no INTERNET, CAMERA or RECORD_AUDIO permission. | MUST |
| NFR-36 | No third-party analytics, advertising or crash-reporting SDKs. | MUST |
| NFR-37 | No personal data in logs, crash traces or notifications. | MUST |
| NFR-40 | Under 1 crash per 100 sessions in the pilot. | MUST |

## 6. Traceability

| Requirements | Design | Tests |
| --- | --- | --- |
| FR-02 to FR-07, FR-60, FR-61 | [UX](03-ux.md) §2-3, [architecture](02-architecture.md) §4 | [test plan](06-test-plan.md) §2 |
| FR-10 to FR-19, FR-62 to FR-64 | [UX](03-ux.md) §4, [avatar and gestures](05-avatar-and-gestures.md) | [test plan](06-test-plan.md) §2 |
| FR-20 to FR-24, FR-65, FR-66 | [UX](03-ux.md) §4, [system design](system-design.html) Figure 3 | [test plan](06-test-plan.md) §2 |
| FR-30 to FR-34, FR-67 | [architecture](02-architecture.md) §6-8, [security](04-security.md) | [test plan](06-test-plan.md) §2, §5 |
| NFR-01 to NFR-11 | [architecture](02-architecture.md) §10 | [test plan](06-test-plan.md) §3 |
| NFR-20 to NFR-25 | [UX](03-ux.md) §5 | [test plan](06-test-plan.md) §6 |
| NFR-30 to NFR-37 | [security](04-security.md) | [test plan](06-test-plan.md) §5 |
