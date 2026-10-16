# 13. Product Overview

A single reference for explaining Sign Setu: what it does, how it works, what it is built with, and what remains before production. Diagrams are in the [system design](system-design.html); details are in the numbered documents cited in each section.

## 1. Purpose

Sign Setu is an offline Android application that helps a police officer communicate with a lost, rescued or kidnapped person who cannot communicate in words: often a child who is deaf, non-verbal, traumatised or speaks another language. Officer and subject share the officer's phone. The application is a communication aid, not an interpreter, and never records statements.

| Property | Value |
| --- | --- |
| Platform | Android 10 (API 29) and later |
| Network | None: the application declares no INTERNET permission and the build fails if one is added |
| Languages for the subject | 17, including Chhattisgarhi, Gondi and Halbi |
| Officer interface languages | English, Hindi |
| Release APK | 19.1 MB |
| Automated tests | 17 unit tests and 20 instrumented tests, all passing |

## 2. How a session works

1. The officer opens the app; it opens directly if the phone was unlocked in the last 30 seconds, otherwise it asks for the phone's fingerprint or PIN.
2. On Home the officer chooses who they are helping (young child, signs, other language, not sure) and the subject's language, then starts.
3. The officer prepares something on the officer panel (a prepared question, a phrase, typed text, pictures, a drawing request or the language finder) and taps send.
4. The phone turns to the subject. The stage shows the character, the caption, pictures and the camera mirror; the subject answers by tapping, drawing or writing.
5. The officer taps the small lock to take the phone back and reads the answer in the conversation.
6. At the end the officer exports an encrypted PDF report or deletes the session. Unended sessions are deleted after the retention period (24 hours by default).

## 3. Features

### 3.1 Officer side

| Feature | Description |
| --- | --- |
| Profiles | Young child (child character, automatic speech), signs (adult character, no sound), other language (language finder first), not sure |
| Conversation | A messaging-style view: items shown to the subject on the right, the subject's answers with pictures on the left, notes and steps in between |
| Prepared questions | 26 triage questions in seven groups (Safety, Health, Needs, Identity, Family, Location, Sensitive); the sensitive group is locked until the officer confirms |
| Phrase bank | 10 reviewed sentences built from patterns such as "Do you want [water]?" |
| Media marks | Each question card shows whether a gesture and a recording exist and whether the caption is missing in the subject's language; "Ready to show" lists what the subject will get |
| Type anything | Typed text is shown at once; the character plays it word by word under four toggles: Signs, Gestures, Spell, Pictures |
| Vocabulary | 69 entries covering 557 written forms in English, Hindi, Hindi in Latin letters and local words |
| Assistant (optional) | Translates typed text into the subject's language and back into English on the phone; the officer sees both before sending |
| They gestured | The officer marks where the subject's hand went and what it did; matching entries from the 33-entry gesture lexicon are shown back to the subject, and only a Yes records the meaning |
| Picture board | 60 picture cards in 9 categories, with search and favourites |
| Draw, language finder | A drawing pad for the subject; a grid of greetings in every language |
| Interpreter call | Opens the department's Sandes or WhatsApp contact; no session data is passed |
| Notes and log | Officer notes and the full session record |
| Export | A PDF report encrypted with AES-256 under a one-time 16-character password shown once |
| Settings | Interpreter contact, camera mirror, large captions, slower gestures, screen lock to leave the stage, assistant model, retention period (4 to 72 hours), app language, delete all data |

### 3.2 Subject side (the stage)

| Feature | Description |
| --- | --- |
| Characters | A child and an adult character with jointed fingers and facial expressions; 95 motions: 42 gestures, the 26-letter two-handed ISL alphabet, 10 digits and 17 word signs |
| Caption | The question in the subject's language in large type; during typed text the current word is lit, a spelled word shows its current letter, and a word's picture is held up |
| Speech | 88 recordings (placeholders until re-recorded); automatic for a young child or another language, never for a signer |
| Answers | Yes, No, "I don't know" and "Stop" always; picture choices or a number pad when the question has them; Draw; Write, which opens the keyboard for every profile except a young child |
| Camera mirror | The subject sees their own hands below the character; nothing is captured or stored |
| Replay | At full or half speed |
| Rotate | The stage turns 180 degrees for a subject sitting opposite |
| Staying on the stage | The app is pinned; Back does nothing; one tap on the small lock returns to the officer |

### 3.3 Safety and privacy

| Control | Description |
| --- | --- |
| Offline | No INTERNET, RECORD_AUDIO or location permission; enforced by a build gate |
| Encrypted record | SQLCipher (AES-256) under a random key wrapped by an Android Keystore key that works only after the phone's own lock |
| Relock | After five minutes away from the app |
| Retention | Sessions deleted on end or after the retention period; "Delete all data" destroys the keys |
| Screen | Screenshots blocked in release; taps through another app's overlay ignored; no backup or device transfer |
| Keyboard | Every text field asks the keyboard not to learn from what is typed |
| Integrity | Every content file and the assistant model are checked by SHA-256 before use |
| Expiry | A build refuses new sessions 180 days after it was made; rooted phones are warned |
| Excluded by design | Identity fields, statements, audio or video recording, identity recognition |

## 4. Mechanisms

### 4.1 Unlock and keys

A random 32-byte database key is stored only in wrapped form, encrypted by an AES-GCM key in Android Keystore that is usable for 30 seconds after the officer passes the phone lock. On launch the app first tries the key silently, which succeeds straight after a phone unlock; otherwise it shows the fingerprint or PIN prompt. If the phone's lock is removed or reset, Android destroys the Keystore key; the app then deletes the unreadable data and tells the officer once.

### 4.2 Write before show

Every event (item shown, answer, note) is written to the encrypted database before the screen changes. The officer panel and the stage are two modes of one session screen, driven by a single view-model state.

### 4.3 Content integrity

`assets/content/manifest.json` holds the SHA-256 of every content file and is checked each time content loads. A failed file is never parsed: a failed text file stops new sessions, because the subject could otherwise be shown wrong words; a failed picture, recording or motion file is left out. The officer sees a notice either way. Model files are checked against the same manifest when first used, not on every load; a failed model is not used.

### 4.4 Typed text into signs

Typed text is split into words (a possessive keeps its stem). Each word becomes, in order of preference: the ISL sign, then the everyday gesture (with Signs on); the everyday gesture (with Gestures on); fingerspelling (with Spell on; grammar words such as "is" and "का" are skipped); or a short pause that holds the word's picture (with Pictures only). Hindi is spelled in Latin letters (पानी to PANI), dropping the final silent vowel. A question mark adds the asking face. The stage follows the playback clock to light the current word, show the current letter and hold up the picture. This is word-by-word signing, not ISL grammar.

### 4.5 Show-back

A meaning is never inferred silently. When the subject gestures, the officer describes the place and action; candidates are ranked by both, then place, then action; the subject sees "Is this it?" and only their Yes records the meaning.

### 4.6 Assistant

The model file (Gemma 3 1B, 806 MB) is imported once in Settings. Its SHA-256 is computed while copying, and only allow-listed files are kept. It is offered only on 64-bit phones with about 3 GB of memory or more, loads on first use and is freed when the app locks. Each request starts from an empty context and uses greedy decoding, so the same input gives the same output. It translates the officer's text into the subject's language and then back into English; the officer sees both, marked "machine translation, not checked", and decides. The character still signs the officer's own words. The log keeps the officer's words and the caption shown.

### 4.7 Other mechanisms

| Mechanism | How |
| --- | --- |
| Leaving the stage | One tap on the lock; a setting adds the fingerprint or PIN; Back does nothing |
| Screen pinning | Starts at the first Show and ends with the session; Android locks the phone when pinning ends |
| Retention | WorkManager deletes the database and wrapped key after the retention period |
| Export | The PDF is drawn on the phone, encrypted with AES-256 under a 16-character password from 32 symbols without look-alikes (80 bits), and handed to Android's share sheet; temporary files are deleted afterwards |
| Overlay protection | The activity, every dialog, sheet and menu ignore taps that pass through another app's overlay |

## 5. The character in detail

The character is not a 3D model, a video or a machine-learning model. It is a parametric 2D puppet drawn in code with Compose Canvas and redrawn every frame from numbers.

| Part | Location | Content |
| --- | --- | --- |
| Drawing | `ui/avatar/Avatar.kt` | Head, hair, torso, arms, five three-joint fingers, brows, eyes and mouth as vector shapes with soft shading |
| Rig | `ui/avatar/Rig.kt` | A 200 by 200 unit space, named body points, a two-bone arm solver, handshapes, face presets, keyframe interpolation |
| Characters | `ChildBuild`, `AdultBuild`, `ChildLook`, `AdultLook` | One skeleton, two sets of proportions and colours |
| Motions | `assets/content/gestures.json`, `signs.json` | Keyframes: time, each hand's target point, handshape, palm roll and angle, face, head and body |

Each frame: the clock time selects two keyframes, which are blended with easing; each hand is placed at a named point (for example `mouth`, `chest`, or `index_opp`, the other hand's index fingertip); the elbow is solved from the shoulder and wrist; fingers bend by curl values; the face preset sets brows, eyes and mouth; breathing, blinking, sway and occasional glances are added; the result is painted as vectors. Because positions are named body points, one motion file plays on both characters.

| Strengths | Limits |
| --- | --- |
| Small (about 900 lines of code and a few hundred kilobytes of motion data); offline; a motion is corrected by editing numbers; motions chain smoothly; faces carry ISL facial grammar | Flat 2D front view with faked depth; motions are authored or generated, not captured from a signer, and the alphabet and signs await ISL review; no mouthing; cartoon style |

The planned upgrade is a 3D rig (VRoid, Blender, glTF or VRM) rendered with Filament, with motion captured from a Deaf signer's video and cleaned ([avatar and gestures](05-avatar-and-gestures.md) §5.4).

## 6. Camera and sign recognition

### 6.1 Current state

| Component | State |
| --- | --- |
| Camera mirror (CameraX Preview) | Built; nothing captured or stored |
| Frame analysis and overlay | Built; switches on only when a tracker is present; tested end to end with a debug stand-in hand |
| Tracker interface (`HandTracker`) | Built; one upright frame in, 21-point hand landmarks out, frame discarded after each call |
| Sign recognition | To be connected |

### 6.2 Available ISL recognition models

| Option | Scope | Assessment |
| --- | --- | --- |
| AI4Bharat INCLUDE | Isolated ISL words from MediaPipe keypoints; training code and pretrained models | Usable as a base; limited vocabulary |
| AI4Bharat OpenHands | Pose-based isolated sign recognition with checkpoints for six sign languages including Indian; Apache-2.0 | Usable as a base; no longer maintained |
| ISL-FS continuous fingerspelling | Dataset with character-level frame labels and research baselines | Research only |
| SignFlow, ISLTranslate | Continuous ISL sentences | Research only; no deployable model |
| Google SignGemma | On-device sign language translation | American Sign Language first; weights not public |

No open model reads fast, continuous ISL reliably. Isolated-sign recognition over a small, known answer vocabulary is achievable.

### 6.3 Direction

The application bundles MediaPipe Holistic (Apache-2.0), which returns per frame 21 points for each hand, 468 face points and 33 body points, all on the phone. Hands carry the sign; brows and mouth carry ISL grammar. On top of it:

1. **Sign-answer capture.** The subject taps "Sign your answer"; landmark points, not video, are recorded for a few seconds and stop when the hands rest. Points stay in memory for that answer only.
2. **Slow-motion replay.** The officer replays the answer as a stick figure of hands, face outline and arms at a quarter or half speed, which makes fast signing readable for the officer or an interpreter.
3. **Segmentation.** Pauses, where the hands drop to rest, divide the answer into word-sized chunks.
4. **Partner recogniser.** The partner team's isolated-sign model (LiteRT) turns each chunk into candidate signs for a small answer vocabulary (yes, no, numbers, family, places, needs); every candidate still passes through show-back.

Face points are used only for ISL grammar, kept in memory, and never used for identity or stored; this needs a recorded decision and an updated DPIA, because face analysis was excluded until now. MediaPipe adds about 10.5 MB of native code for 64-bit phones and 13.7 MB of model, so the release APK limit rises from 30 MB to about 45 MB, or the model is delivered as an import file like the assistant.

## 7. Exporting and the network

Export works without a network. The PDF is written on the phone and handed to Android's share sheet; only the application the officer chooses (for example WhatsApp or email) uses its own connection. Sign Setu never connects.

| Item | Exported | Reason |
| --- | --- | --- |
| Confirmed meanings, answers, notes, typed and written answers | Yes | Part of the session record |
| Assistant caption with the officer's own words | Yes | Both are logged |
| Recogniser suggestions not confirmed by the subject | Yes, marked unconfirmed | Not evidence |
| "Signed answer" summary (number of signs, duration) | Yes | Records that a signed reply was given |
| Stick-figure snapshots or landmark replay files | Only after DPIA approval | Face points are biometric-like data |
| Camera video or photographs | Never | The application records nothing |

## 8. Technology stack

| Layer | Technology |
| --- | --- |
| Language | Kotlin 2.4.21; C++ for the assistant bridge |
| Build | Android Gradle Plugin 9.4.1, Gradle 9.8.1, JDK 21, KSP 2.3.12, R8 full mode, version catalog, lockfile, SHA-256 dependency verification, CycloneDX SBOM, permission build gate |
| User interface | Jetpack Compose (BOM 2026.09.00), Material 3, Material Icons 1.7.8, Activity Compose 1.13.0, Navigation 3 (1.2.0), Lifecycle 2.11.0 |
| Graphics | Compose Canvas for the characters, overlays and drawing pad |
| Media | Media3 ExoPlayer 1.11.1 |
| Camera | CameraX 1.6.2 with `camera-compose` |
| Storage | Room 2.8.5, SQLCipher for Android 4.19.1, androidx.sqlite 2.7.1, DataStore 1.2.1 |
| Security | androidx.biometric 1.1.0, Android Keystore, screen pinning, FLAG_SECURE |
| Background | WorkManager 2.12.0 |
| Export | Android PdfDocument, PdfBox-Android 2.0.27.0, BouncyCastle 1.86 |
| Data | kotlinx.serialization 1.11.0, coroutines 1.11.0 |
| Assistant | llama.cpp v0.5.0 through NDK r30 and CMake 4.1.1; Gemma 3 1B GGUF model file |
| Fonts | Baloo 2 (SIL OFL) |
| Tests | JUnit 4.13.2; Accessibility Test Framework instrumented tests; Android Lint |
| Content tools | Python: content manifest, sign generator, review record, placeholder speech (Indic Parler-TTS) |
| Not used | Networking libraries, analytics, crash reporting, Firebase or Play services, WebView, microphone, camera recording |

## 9. Frontend architecture

### 9.1 Structure

```
MainActivity  window security, edge-to-edge, biometric prompt, pinning, app language
 └─ AppRoot  no-learning keyboard, theme, gate
     ├─ FirstRunScreen        until first run is done
     ├─ LockScreen            while the database is closed
     └─ NavDisplay
         ├─ StartKey          StartScreen (Home)
         ├─ SettingsKey       SettingsScreen
         └─ SessionKey(id)    SessionRoute
               ├─ Mode.OFFICER  OfficerPane: header, conversation, dock
               └─ Mode.SHOW     Stage under the stage theme
```

| Package | Content |
| --- | --- |
| `ui.start` | Home and first run |
| `ui.session` | Officer panel (conversation, suggestion cards, composer, tool tray), stage (character, caption, mirror, answers, drawing, writing), sheets (picture board, gesture lookup, phrases, log, profile), session view model |
| `ui.avatar` | Drawing, rig, small faces |
| `ui.settings` | Grouped settings cards |
| `ui.components` | Brand button, picture cards, speech player, overlay filter, guarded dialog |
| `ui.theme` | Colour schemes, shapes, type, composition locals |

### 9.2 State

The session view model combines the database's event flow with local state into one `SessionUi` value, collected with lifecycle awareness. The item prepared for the subject is a sealed `Pending` value (question, typed text, cards, show-back, phrase, language finder, drawing), which is the single input the stage renders. Screens send events up as lambdas; the view model writes the event before the screen changes. Settings come from DataStore; content is an immutable bundle loaded once; screen-local state such as composer text and toggles uses `rememberSaveable`. Wiring is manual through `AppContainer`, with no dependency-injection framework.

### 9.3 Rendering and theming

- The character's clock lives in state read only during drawing, so animation runs without recomposition; typed-text playback derives only the current word and letter, so only those changes recompose.
- Two colour schemes: slate and indigo with a gradient for the officer, warm peach and teal for the stage, so the two states are never confused. Shapes are 12, 14, 20 and 24 dp; headings use Baloo 2 and the stage uses a larger type scale.
- The officer interface uses Android resources in English and Hindi; everything the subject sees comes from content files keyed by language, so a new subject language needs no code change.
- Touch targets are at least 48 dp and answer cards 96 dp; layouts reflow above 1.3 font scale; instrumented accessibility tests cover every stage state in both themes and two screen sizes.

## 10. Readiness

| Item | State | Needed |
| --- | --- | --- |
| Application, release 1 | Built and tested on emulators | None |
| Content: gestures, alphabet, signs, vocabulary, local-language wording, recordings | Built, awaiting review | Sign-off by ISL, child-welfare and language reviewers on the [content review record](governance/forms/content-review-record.md) |
| Camera sign recognition | To be connected | MediaPipe Holistic layer, capture and replay; the partner team's recogniser |
| Assistant | Optional | Model file from the department; speed on an arm64 phone; language spot-checks |
| Field tests | Pending | Timing and TalkBack on a real Android 10 phone; transfer test on two phones |
| Security | Pending | MASVS checklist, CERT-In empanelled audit, release signing key with two custodians |
| Pilot and rollout | Pending | One district with trained officers, then state-wise through the Mobile Seva AppStore |
