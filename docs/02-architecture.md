# 02. Architecture

## 1. Overview

Release 1 is a single Android application that runs entirely on the officer's phone. It has no server, makes no network calls and declares no INTERNET permission. All content (questions, picture cards, gesture and ISL clips, audio) ships inside the signed APK; all session data stays in an encrypted database on the device until the officer exports or clears it. An optional second phone or tablet can show the subject's stage over Bluetooth (§11); it stores nothing. The reviewer portal (§12) is a separate static web page for content sign-off and is not shipped in the app.

The data flow diagrams, element catalogue and trust boundaries are in the [system design](system-design.html). This document covers how the application is built.

## 2. Principles

1. **Offline only.** Nothing in release 1 depends on a network. Online features arrive in release 1.1 behind the project gateway (§13).
2. **Content is data.** Questions, cards, lexicon entries and translations live in versioned asset files reviewed under the content policy, not in code.
3. **Write before show.** Every event is persisted before the interface reflects it (FR-34).
4. **Small and boring.** One app module, platform libraries first, no frameworks the audit has to justify.

## 3. Technology

| Layer | Choice | Reason |
| --- | --- | --- |
| Language, UI | Kotlin, Jetpack Compose, Material 3 | Native, smallest build, direct access to Keystore and platform security APIs |
| SDK levels | minSdk 29; compile and target the latest stable SDK installed (37) | Android 10 floor |
| Media | Media3 ExoPlayer | Clips and audio, gapless playlists for the phrase bank, variable speed |
| Database | Room 2.8 with SQLCipher for Android 4.19 | Encrypted at rest |
| Settings | Jetpack DataStore | No personal data |
| Background work | WorkManager | Retention purge |
| App lock | androidx.biometric BiometricPrompt, KeyguardManager | Device credential or strong biometric |
| PDF export | Android PdfDocument to compose, PdfBox-Android to encrypt (AES-256) | Readable in any PDF reader |
| Characters | Compose Canvas, 2D rig, keyframes in `gestures.json` (D-21) | No video files; one gesture file plays on both characters |
| Camera mirror | CameraX 1.6: Preview, and ImageAnalysis whose frames reach the tracker only inside a sign window (D-20, D-25) | Nothing captured or stored |
| Vision engine | MediaPipe Tasks Vision 1.0.0: the Holistic landmarker on the CPU in video mode, model in the APK (D-25) | Hands, face and pose on the phone; 64-bit only |
| Sign reader | `LandmarkSignReader`: a 262-word isolated-sign classifier in plain Kotlin, weights in the APK (D-25) | No inference library to ship or audit |
| Second screen | Bluetooth RFCOMM to bonded devices (`standard`); Google Nearby Connections in the optional `nearby` flavor (D-26) | No network, scanning or location in the reference build |
| On-device assistant | llama.cpp v0.5.0 built with the NDK and CMake into `libassistant.so` (arm64-v8a; x86_64 for the emulator); an optional GGUF model file imported in Settings (D-24) | Offline translation with a back-translation; no network |
| Type | Baloo 2 (SIL OFL), bundled, for headings and subject captions in Latin and Devanagari | Friendly, legible at arm's length; other scripts use the system Noto fonts |
| Build | Gradle wrapper, version catalog, R8 full mode, dependency verification; CMake fetches llama.cpp pinned by SHA-256 | Size, startup, supply chain |

Not used: networking libraries, dependency-injection frameworks, Firebase, Google Play services (except Nearby Connections in the `nearby` flavor), analytics or crash SDKs, WebView, microphone APIs, and camera capture or recording (the camera is a mirror only, D-20). Exact versions are pinned in the version catalog.

## 4. Packages

One Gradle module under `app.signsetu`, organised by feature. Process numbers refer to the [system design](system-design.html) Figure 2.

| Package | Responsibility | DFD | Requirements |
| --- | --- | --- | --- |
| (root) | `MainActivity` (FLAG_SECURE, overlay touch filter, BiometricPrompt, screen pinning); `AppContainer` wiring, app lock and relock | 1.0 | FR-61, FR-67, NFR-31 |
| `ui` | Navigation, lock screen, session route (officer panel or stage), app-wide no-learning keyboard | 1.0, 2.0, 3.0 | FR-02, FR-03, SEC-05 |
| `ui.start` | Home: greeting stage, unfinished session, profile faces, language picker; first run | 2.0 | FR-05, FR-07, FR-60 |
| `ui.session` | Officer panel in two pages, Transcribe and More (conversation, completions, question runs, answer trail and grades, composer, tools); stage (character, caption, mirror, answers, drawing, writing, ground rules, pause layer, glance strip, sign panels); handover (tilt and volume keys); auto mode (`Interview` sequencer, `AutoPilot`, `AutoPanel`); the record (`PromptRecord`, `EvidenceGrade`, `RecordText`); sheets; session view model | 2.0, 3.0 | FR-10 to FR-21, FR-31, FR-60 to FR-66, FR-70, FR-71, FR-77 to FR-101 |
| `ui.pair` | `StageDevice` (the stage device's screens), `SecondScreenSheet` (officer pairing), `PairedBar`, `LinkText` | 2.0, 3.0 | FR-72 to FR-76 |
| `ui.avatar` | 2D rig, the two characters, keyframe playback, fingerspelling contact points | 3.1 | FR-11, FR-18 |
| `ui.settings`, `ui.theme`, `ui.components` | Settings; officer, stage and sun-mode colour schemes and the ambient-light switch; shapes and type; shared cards and buttons | 2.0 | FR-51, FR-99 |
| `data.content` | Loads and verifies the content bundle; resolves questions, phrases and lexicon lookup; plans typed text into signs, gestures and spelling | 3.1, D2 | FR-11, FR-13, FR-17, FR-62, FR-65 |
| `data.session` | Encrypted Room database, wrapped database key, event log, retention worker | 4.0, D1, D4 | FR-05, FR-06, FR-30, FR-33, FR-34 |
| `data.settings` | DataStore settings and the officer's app language | D3 | FR-51 |
| `export` | Password-protected PDF report | 5.0 | FR-32 |
| `camera` | Camera mirror; `LandmarkTracker` interface and `LandmarkFrame`; `HolisticTracker` (MediaPipe), created by `LandmarkTrackers` per build type (release: Holistic; debug: Holistic, or a stand-in hand for tests) | 3.5 | FR-69, FR-84 |
| `sign` | `SignCapture` and `SignWindow` (windows and pause segmentation); `SignReader`, `LandmarkSignReader` and `SignReaders` (the bundled reader); `SignChoices` (context limit, alternatives for the subject, the slower cue); `SkeletonPlayer` (slow replay) | 3.5, 3.6 | FR-84 to FR-87 |
| `pair` | `PairCrypto` (handshake, keys, frames), `StageWire` (messages and the stage view), `StageTransport` and `BluetoothTransport`, `StageLink` (link state machine), `StageBridge` (officer-side bridge to the view model), `Drawings` (bounded re-encoding) | 7.0 | FR-72 to FR-76, SEC-66 to SEC-70 |
| Flavor source sets | `src/standard` and `src/nearby`, each with `pair/StageTransports`; `NearbyStageTransport` only in `src/nearby` | 7.0 | SEC-73 |
| `assistant` | Model import with hash check, llama.cpp bridge, translation and back-translation | 6.0, D5 | FR-68, SEC-65 |

## 5. Content bundle

```
assets/content/
  manifest.json          content version and the SHA-256 of every file
  triage.json            groups (sensitive, rights, roles[], child?) and questions: id, group, phase, kind, after?, text{lang}, cards[], lexicon?, gesture?
  cards.json             picture board categories and cards: id, category, label{lang}
  lexicon.json           gesture and ISL entries (schema in 05-avatar-and-gestures.md §5.2)
  gestures.json          character keyframes per gesture, written against named contact points (D-21)
  signs.json             fingerspelling alphabet, digits and draft word signs, generated by tools/content/build_signs.py (D-22)
  words.json             typed-word vocabulary: written forms to sign, gesture and picture (D-22)
  languages.json         language finder: code, name in its own script, English name, greeting
  subject_ui.json        words the stage shows the subject (Yes, No, Stop, ground rules, pause), per language
  sign_words.json        sign-reader labels to vocabulary words (draft, D-25)
  models/                holistic_landmarker.task (MediaPipe), sign_reader.bin and sign_reader.json (D-25)
  phrases.json           phrase bank: sentence patterns, their signs, and per slot the picture, its sign and the full sentence
  cards/<id>.webp        picture cards
  audio/<lang>/<id>.ogg  question audio, Opus, mono; phrase sentences as phrase.<phrase>.<card>.ogg
  audio/greet/<lang>.ogg language finder greetings
```

Later releases add `clips/<set>/<id>.mp4` (480x480 H.264, silent; sets child, adult and ISL).

- The app checks every file against the manifest hashes each time it loads the content, and never parses or shows a file that fails. A failed picture, recording or gesture file is left out and the rest still works; a failed text file stops new sessions, because the subject could otherwise be shown wrong words. Either way the officer sees a notice on the home screen (SEC-54).
- All approved languages ship in the APK. Recorded audio is about 6 KB per question, so 22 languages fit within the size budget; video clips are the main cost (estimates in [avatar and gestures](05-avatar-and-gestures.md) §5.5).
- Interface strings use Android resources; subject-facing content uses the bundle, so content changes do not touch code.

## 6. State and data model

One `SessionViewModel` holds `SessionUi` as a `StateFlow`. Every user action becomes an event written to the database first; the state updates from the write. What is on the stage (`onStage`, with its logged row `itemId`) is kept apart from the officer's draft (`pending`), so a second screen never shows a draft. Landmark frames of signed answers are held only in view-model memory, keyed by event (SEC-71).

```
Session    id, startedAt, endedAt?, profile, subjectLang, role (NOT_KNOWN | VICTIM | ACCUSED)
Event      id, sessionId, ts, type, source, lang?, text?, cardId?, lexiconId?, drawingId?,
           itemId?, reason?, shownId?, via?
           type:   QUESTION_SHOWN | ANSWER_CARD | ANSWER_DRAWING | ANSWER_GESTURE | ANSWER_UNCLEAR | ANSWER_TEXT |
                   ANSWER_SIGNED | LANGUAGE_CHOSEN | NOTE | SYSTEM | SUGGESTED | APPROVED | SKIPPED |
                   AUTO_ADVANCED | LINK | PROMPT
           source: PRESET | TEXT | PHRASE | BOARD | SHOW_BACK | SUBJECT | OFFICER | RULE | MACHINE
Drawing    id, sessionId, png
```

- `itemId` names the bank item of a shown or suggested row; `reason` holds the auto-mode reason, `rights_shown`, `caption_missing` or `opening`; `shownId` links every answer to the QUESTION_SHOWN it answers; `via` is `stage` or `stage_late` for answers from a second screen.
- PROMPT rows hold the "Shown as" record (caption, motions, pictures in order, content version). A say-back carries `lexiconId` `ref:<answer id>`. SYSTEM rows use machine keys (for example `run_ended:k/n`, `break_given`, `jj_notice`, `juvenile`, `juvenile_off`, `read_signs`, `tell_me`, `mode_auto`), shown in the officer's language.
- Database version 1 to 2 adds the session role; 2 to 3 adds `itemId`, `reason`, `shownId` and `via`. A downgrade drops the tables, because sessions live a day at most.

No table has a field for a name, address, phone number or photograph of the subject.

## 7. Storage and keys

- A random 256-bit database key (DEK) is generated on first use and stored wrapped by an AES key-encryption key (KEK) in Android Keystore, hardware-backed where available, non-exportable and bound to user authentication.
- Unlocking the app (1.0) authorises the KEK; the unwrapped DEK lives in memory only while the app is unlocked.
- Drawings are stored inside the encrypted database, never on shared storage.
- Purge deletes the session rows and runs `VACUUM`.

API-level details for authentication are in [security](04-security.md) SEC-21 and SEC-22.

## 8. Export and hand-off

**Session report.** The report is composed with Android `PdfDocument`: header (session start, profile, languages), the event log, notes, and drawings as images. It is then encrypted with PdfBox-Android using AES-256. The passphrase is 16 characters from an unambiguous 32-symbol alphabet (80 bits, `SecureRandom`), shown once in four groups of four, and shared by the officer separately from the file. The file is written to app-private cache and shared through `FileProvider` with a temporary read grant, then deleted.

**Interpreter hand-off.** The configured contact (D3) is opened in the configured messaging app: for WhatsApp, `https://wa.me/<number>` opens the chat, and the officer starts the video call; for Sandes, the app is launched by package name and the contact is shown on screen for the officer to select [confirm Sandes deep link]. No session data is passed.

## 9. Build and release

- The `applicationId` is `in.signsetu`. The code namespace is `app.signsetu`, because `in` is a Kotlin keyword. The department confirms the identifier before the first signed release, because it cannot change afterwards.
- `BuildConfig.BUILD_EPOCH_DAY` is set at build time for the build-expiry check (FR-67).
- Two product flavors on the `transport` dimension: `standard` (default, the reference build) and `nearby` (application ID suffix `.nearby`). Tasks are named per variant, for example `assembleStandardRelease`.
- Release builds: R8 full mode, `debuggable=false`, no logging. MediaPipe's armeabi-v7a native library is excluded; 32-bit phones keep a plain mirror. A Baseline Profile is not yet generated; it is the next step if cold start on the oldest phone misses NFR-01.
- Signing, SBOM, scanning and release steps are in [governance/11-devsecops-release.md](governance/11-devsecops-release.md).

## 10. Performance

| Target | Approach |
| --- | --- |
| Cold start under 2 s (NFR-01) | No work on startup beyond unlocking; content manifest checked once per install |
| Preset under 300 ms (NFR-02) | First-screen cards preloaded; one ExoPlayer instance reused; clips stored uncompressed in the APK so they play without extraction |
| Smooth clips (NFR-03) | 480x480 H.264 at source frame rate; hardware decode |
| APK under 50 MB (NFR-05) | WebP cards, Opus audio; the vision model (13.7 MB) and MediaPipe's 64-bit native code are the main cost; release APK 47.0 MB (`standard`) |
| Memory under 300 MB (NFR-06) | Analysis only inside a sign window; the vision model mapped, not copied; a narrative keeps no face points (about 20 MB for 3 minutes); single player, cards loaded lazily |

## 11. Second screen

`AppContainer.link` is a `StateFlow<StageLink?>`: at most one link, this phone as officer or this device as the stage, held in memory only. All link logic runs on one single-threaded scope; blocking reads and writes go to the IO dispatcher. `AppRoot` puts the stage-device branch before `LockScreen`, so a stage device never opens the session database or the navigation.

```
Officer phone                              Stage device
  Hello1 (identity, commitment)    ->
                                   <-      Hello2 (identity, public key, nonce)
  Reveal (public key, nonce)       ->
  [both derive direction keys, code, check number, resume key]
                                           shows the 6-digit code
  officer types the code, sees the check number
  Confirm                          ->      shows the same check number
                                   <-      Confirm (Match tapped)
  Show (rev, itemId, view)         ->
                                   <-      Displayed (itemId)
                                   <-      Answer (itemId, n, body)
  Ack (itemId, n) after commit     ->
  Ping every 10 s                  <->     Ping; lost after 30 s; resume within 120 s
  Bye (end, fallback, exit)        ->      hand-back screen, pinned
```

The officer view model logs each item before `Show` and each answer before `Ack`. Answers are de-duplicated on (item, n) per link. If the link fails, the officer can reconnect or move the stage back to this phone ("Use this phone"), which logs `LINK fallback`.

## 12. Reviewer portal

`portal/` is a static site built with Vite+ (`vp`), React 19.3 and TypeScript 7.0 (D-31). It imports the app's content files at build time, draws motions with a TypeScript port of the rig (`src/rig.ts`, checked against `Rig.kt` through `src/rig-fixtures.json`), keeps verdicts in the browser's local storage and exports them as JSON for `tools/content/review_record.py --reviews`. It has no backend and makes no network calls.

## 13. Release 1.1 (not built in release 1)

Voice questions and caption translation add the INTERNET permission, a project-owned gateway in an India-region cloud, Android key attestation, certificate pinning and Bhashini behind the gateway. Adding release 1.1 requires an updated threat model, DPIA and audit scope.
