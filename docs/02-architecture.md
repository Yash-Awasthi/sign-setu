# 02. Architecture

## 1. Overview

Release 1 is a single Android application that runs entirely on the officer's phone. It has no server, makes no network calls and declares no INTERNET permission. All content (questions, picture cards, gesture and ISL clips, audio) ships inside the signed APK; all session data stays in an encrypted database on the device until the officer exports or clears it.

The data flow diagrams, element catalogue and trust boundaries are in the [system design](system-design.html). This document covers how the application is built.

## 2. Principles

1. **Offline only.** Nothing in release 1 depends on a network. Online features arrive in release 1.1 behind the project gateway (§11).
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
| Camera mirror | CameraX 1.6: Preview, and ImageAnalysis only when a hand model is present (D-20) | Nothing captured or stored |
| On-device assistant | llama.cpp v0.5.0 built with the NDK and CMake into `libassistant.so` (arm64-v8a; x86_64 for the emulator); an optional GGUF model file imported in Settings (D-24) | Offline translation with a back-translation; no network |
| Type | Baloo 2 (SIL OFL), bundled, for headings and subject captions in Latin and Devanagari | Friendly, legible at arm's length; other scripts use the system Noto fonts |
| Build | Gradle wrapper, version catalog, R8 full mode, dependency verification; CMake fetches llama.cpp pinned by SHA-256 | Size, startup, supply chain |

Not used: networking libraries, dependency-injection frameworks, Firebase or any Google Play services, analytics or crash SDKs, WebView, microphone APIs, and camera capture or recording (the camera is a mirror only, D-20). Exact versions are pinned in the version catalog.

## 4. Packages

One Gradle module under `app.signsetu`, organised by feature. Process numbers refer to the [system design](system-design.html) Figure 2.

| Package | Responsibility | DFD | Requirements |
| --- | --- | --- | --- |
| (root) | `MainActivity` (FLAG_SECURE, overlay touch filter, BiometricPrompt, screen pinning); `AppContainer` wiring, app lock and relock | 1.0 | FR-61, FR-67, NFR-31 |
| `ui` | Navigation, lock screen, session route (officer panel or stage), app-wide no-learning keyboard | 1.0, 2.0, 3.0 | FR-02, FR-03, SEC-05 |
| `ui.start` | Home: greeting stage, unfinished session, profile faces, language picker; first run | 2.0 | FR-05, FR-07, FR-60 |
| `ui.session` | Officer panel (conversation, suggestions, composer, tools), stage (character, caption, mirror, answers, drawing, writing), sheets (picture board, gesture lookup, phrases, log, change profile), session view model | 2.0, 3.0 | FR-10 to FR-21, FR-31, FR-60 to FR-66, FR-70, FR-71 |
| `ui.avatar` | 2D rig, the two characters, keyframe playback, fingerspelling contact points | 3.1 | FR-11, FR-18 |
| `ui.settings`, `ui.theme`, `ui.components` | Settings; officer and stage colour schemes, shapes and type; shared cards and buttons | 2.0 | FR-51 |
| `data.content` | Loads and verifies the content bundle; resolves questions, phrases and lexicon lookup; plans typed text into signs, gestures and spelling | 3.1, D2 | FR-11, FR-13, FR-17, FR-62, FR-65 |
| `data.session` | Encrypted Room database, wrapped database key, event log, retention worker | 4.0, D1, D4 | FR-05, FR-06, FR-30, FR-33, FR-34 |
| `data.settings` | DataStore settings and the officer's app language | D3 | FR-51 |
| `export` | Password-protected PDF report | 5.0 | FR-32 |
| `camera` | Camera mirror; `HandTracker` interface for the partner model ([contract](11-hand-model-integration.md)), created by `HandTrackers` per build type (release: none until the model is connected; debug: a stand-in hand) | 3.3 | FR-69, FR-22 |
| `assistant` | Model import with hash check, llama.cpp bridge, translation and back-translation | 6.0, D5 | FR-68, SEC-65 |

## 5. Content bundle

```
assets/content/
  manifest.json          content version and the SHA-256 of every file
  triage.json            groups (sensitive flag) and questions: id, group, text{lang}, cards[], lexicon?, gesture?
  cards.json             picture board categories and cards: id, category, label{lang}
  lexicon.json           gesture and ISL entries (schema in 05-avatar-and-gestures.md §5.2)
  gestures.json          character keyframes per gesture, written against named contact points (D-21)
  signs.json             fingerspelling alphabet, digits and draft word signs, generated by tools/content/build_signs.py (D-22)
  words.json             typed-word vocabulary: written forms to sign, gesture and picture (D-22)
  languages.json         language finder: code, name in its own script, English name, greeting
  subject_ui.json        words the stage shows the subject (Yes, No, Stop, draw), per language
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

One `SessionViewModel` holds `SessionState` as a `StateFlow`. Every user action becomes an event written to the database first; the state updates from the write.

```
Session    id, started_at, ended_at?, profile, subject_lang, officer_lang
Event      id, session_id, ts, type, source, lang, text?, card_id?, lexicon_id?, drawing_id?
           type:   QUESTION_SHOWN | ANSWER_CARD | ANSWER_DRAWING | ANSWER_GESTURE | ANSWER_UNCLEAR | NOTE | SYSTEM
           source: PRESET | TEXT | PHRASE | BOARD | SUBJECT | OFFICER
Drawing    id, session_id, png
```

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
- Release builds: R8 full mode, `debuggable=false`, no logging. A Baseline Profile is not yet generated; it is the next step if cold start on the oldest phone misses NFR-01.
- Signing, SBOM, scanning and release steps are in [governance/11-devsecops-release.md](governance/11-devsecops-release.md).

## 10. Performance

| Target | Approach |
| --- | --- |
| Cold start under 2 s (NFR-01) | No work on startup beyond unlocking; content manifest checked once per install |
| Preset under 300 ms (NFR-02) | First-screen cards preloaded; one ExoPlayer instance reused; clips stored uncompressed in the APK so they play without extraction |
| Smooth clips (NFR-03) | 480x480 H.264 at source frame rate; hardware decode |
| APK under 30 MB (NFR-05) | WebP cards, Opus audio, short clips; size checked in CI |
| Memory under 300 MB (NFR-06) | Camera preview only (no analysis without a hand model), single player, cards loaded lazily |

## 11. Release 1.1 (not built in release 1)

Voice questions and caption translation add the INTERNET permission, a project-owned gateway in an India-region cloud, Android key attestation, certificate pinning and Bhashini behind the gateway. Adding release 1.1 requires an updated threat model, DPIA and audit scope.
