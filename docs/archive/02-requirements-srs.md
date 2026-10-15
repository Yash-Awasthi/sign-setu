# 02. Software Requirements Specification

Priority: **MUST** (v1 cannot ship without it), **SHOULD** (v1 if time allows), **LATER** (after the pilot).

Each requirement has a stable ID. Tests in the [test plan](10-test-plan.md) and controls in the [security architecture](06-security-architecture.md) refer to these IDs.

## 1. Users

| User | Description |
| --- | --- |
| Officer | Holds and controls the phone. Under time pressure, possibly outdoors, may have limited English. |
| Subject | Faces the subject half of the screen. May be a frightened child, deaf, mute, unable to read, or unfamiliar with phones. |

## 2. Functional requirements

### 2.1 Session and layout

| ID | Requirement | Priority |
| --- | --- | --- |
| FR-01 | The main screen is split: officer panel on top, subject panel at the bottom. | MUST |
| FR-02 | The officer can rotate the subject panel 180 degrees so a subject facing the officer reads it upright. | MUST |
| FR-03 | The officer can switch the subject panel to full screen ("focus subject") and back. | MUST |
| FR-04 | The officer can drag the divider to resize the two panels. | SHOULD |
| FR-05 | Starting the app opens a new session or resumes the last unfinished session. | MUST |
| FR-06 | The officer can end a session; ending offers export, then clears all session data from the phone. | MUST |
| FR-07 | The officer chooses the subject-facing language (Hindi, English at minimum) per session. | MUST |

### 2.2 Officer asks

| ID | Requirement | Priority |
| --- | --- | --- |
| FR-10 | The officer can tap a preset triage question, grouped as safety, health, needs, identity, family, location. | MUST |
| FR-11 | A preset question shows, on the subject panel, its recorded ISL clip, caption in the chosen language and matching picture cards, with no network. | MUST |
| FR-12 | Sensitive questions (what happened, who hurt you) are not on the first screen; they sit behind a deliberate second tap. | MUST |
| FR-13 | The officer can type a free-text question. | MUST |
| FR-14 | The officer can hold a button to record a voice question; the transcript appears for the officer to edit and confirm. | MUST |
| FR-15 | Nothing is shown to the subject until the officer confirms it. | MUST |
| FR-16 | A confirmed free-text question is shown as a caption immediately and played by the skeleton avatar when sign data arrives. | MUST |
| FR-17 | When a word or sentence has no sign data, the subject panel shows the caption and pictures and the officer sees "sign not available". | MUST |
| FR-18 | The officer can replay the sign, and play it at slow speed. | MUST |
| FR-19 | The officer can show any picture card from the full board, searchable, with favourites. | MUST |

### 2.3 Subject answers

| ID | Requirement | Priority |
| --- | --- | --- |
| FR-20 | Yes and No are always available as large cards during a question. | MUST |
| FR-21 | The subject can tap a picture card; the officer sees the card and its label in the officer's language. | MUST |
| FR-22 | The officer can turn on sign recognition; a live camera indicator is shown while the camera is on. | MUST |
| FR-23 | Recognised signs appear to the officer as text with a confidence level (high, medium, low) and a "ask to repeat" action. | MUST |
| FR-24 | The subject can draw or write on a canvas; the officer sees it and can save it to notes. | SHOULD |
| FR-25 | A subject's answer can be echoed back as a sign or caption for confirmation. | SHOULD |

### 2.4 Notes and records

| ID | Requirement | Priority |
| --- | --- | --- |
| FR-30 | Every question shown and answer received is logged with a time in the session log. | MUST |
| FR-31 | The officer can add free-text notes to the session. | MUST |
| FR-32 | The officer can export a session as an encrypted file through the Android share sheet. | MUST |
| FR-33 | Sessions not exported are deleted automatically after a configured period (default 24 hours). | MUST |
| FR-34 | The session is saved after every event so a crash or restart loses nothing. | MUST |

### 2.5 Fallbacks

| ID | Requirement | Priority |
| --- | --- | --- |
| FR-40 | Without network, the triage set, picture board, drawing and typed captions keep working. | MUST |
| FR-41 | If a service call fails or exceeds its timeout, the officer sees a plain message and the alternative path. | MUST |
| FR-42 | If camera or microphone permission is refused, the related button explains why and points to the alternative. | MUST |
| FR-43 | No loading indicator runs longer than 5 seconds without a cancel action. | MUST |

### 2.6 Settings and first run

| ID | Requirement | Priority |
| --- | --- | --- |
| FR-50 | First run explains the app in three short screens, sets the officer language and explains permissions in plain words. | MUST |
| FR-51 | Settings: officer language, text size, avatar speed, auto-delete period, clear all data, about and legal notices. | MUST |
| FR-52 | The app shows that it is an aid, not a replacement for an interpreter or counsellor, on first run and in About. | MUST |

## 3. Non-functional requirements

### 3.1 Performance and size

| ID | Requirement | Priority |
| --- | --- | --- |
| NFR-01 | Cold start to usable split view under 2 seconds on the oldest test phone. | MUST |
| NFR-02 | Tapping a preset question shows the clip, caption and pictures within 300 ms. | MUST |
| NFR-03 | Skeleton avatar plays at 24 fps or more on the oldest test phone. | MUST |
| NFR-04 | Sign recognition processes at least 10 frames per second on the oldest test phone. | MUST |
| NFR-05 | App download size under 30 MB; optional language and model packs delivered separately. | MUST |
| NFR-06 | Memory use stays under 300 MB during a session with camera on. | SHOULD |

### 3.2 Compatibility

| ID | Requirement | Priority |
| --- | --- | --- |
| NFR-10 | Supports Android 10 (API 29) and later, phones with 3 GB RAM or more. | MUST |
| NFR-11 | Works in portrait on screens from 5 to 6.9 inches. | MUST |

### 3.3 Usability and accessibility

| ID | Requirement | Priority |
| --- | --- | --- |
| NFR-20 | Touch targets at least 48 x 48 dp; subject cards at least 96 dp. | MUST |
| NFR-21 | Text contrast at least 4.5:1; meaning never conveyed by colour alone. | MUST |
| NFR-22 | Text scales with system font size up to 200% without loss of function in the officer panel. | MUST |
| NFR-23 | Officer interface works with TalkBack. | SHOULD |
| NFR-24 | Subject panel: no timers, no flashing, no sudden sound, no pop-ups. | MUST |
| NFR-25 | All interface text comes from translation files; Devanagari and selected regional scripts render correctly. | MUST |
| NFR-26 | Core triage flow usable one-handed by the officer. | SHOULD |

### 3.4 Security and privacy

Detailed controls are in the [security architecture](06-security-architecture.md). Summary requirements:

| ID | Requirement | Priority |
| --- | --- | --- |
| NFR-30 | All session data encrypted at rest with a key protected by Android Keystore. | MUST |
| NFR-31 | App opening and session history protected by device credential or biometrics. | MUST |
| NFR-32 | Screenshots and screen recording blocked on screens with subject data. | MUST |
| NFR-33 | No session data included in Android backups or device transfer. | MUST |
| NFR-34 | All network traffic over TLS 1.2 or later with certificate pinning to project backends. | MUST |
| NFR-35 | No video or audio of the subject stored by the app; camera frames never leave the phone. | MUST |
| NFR-36 | No third-party analytics, advertising or tracking SDKs. | MUST |
| NFR-37 | No personal data in logs, crash reports or notifications. | MUST |

### 3.5 Reliability

| ID | Requirement | Priority |
| --- | --- | --- |
| NFR-40 | Crash rate under 1 per 100 sessions in the pilot. | MUST |
| NFR-41 | Every service call has a timeout (default 8 seconds) and at most one automatic retry. | MUST |

## 4. Constraints

- Native Android, Kotlin, Jetpack Compose (D-01).
- Backends and partner services hosted in India before production (D-07).
- Open-source components preferred, in line with the Government of India policy on adoption of open source software.

## 5. Traceability

| Requirement group | Design | Tests |
| --- | --- | --- |
| FR-01 to FR-07 | [05](05-design-ux.md) §2 | [10](10-test-plan.md) §2 |
| FR-10 to FR-19 | [03](03-architecture.md) §4, [09](09-api-contracts.md) §2-3 | [10](10-test-plan.md) §2 |
| FR-20 to FR-25 | [03](03-architecture.md) §4, [09](09-api-contracts.md) §4 | [10](10-test-plan.md) §2 |
| FR-30 to FR-34 | [06](06-security-architecture.md) §3, [policies/data-retention-policy](../governance/policies/data-retention-policy.md) | [10](10-test-plan.md) §2, §5 |
| FR-40 to FR-43 | [03](03-architecture.md) §6 | [10](10-test-plan.md) §4 |
| NFR-01 to NFR-06 | [03](03-architecture.md) §7 | [10](10-test-plan.md) §3 |
| NFR-20 to NFR-26 | [05](05-design-ux.md) §5 | [10](10-test-plan.md) §6 |
| NFR-30 to NFR-37 | [06](06-security-architecture.md) | [10](10-test-plan.md) §5 |
