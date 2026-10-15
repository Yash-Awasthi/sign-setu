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
| Database | Room with SQLCipher for Android 4.18 | Encrypted at rest; Room 3 supported |
| Settings | Jetpack DataStore | No personal data |
| Background work | WorkManager | Retention purge |
| App lock | androidx.biometric BiometricPrompt, KeyguardManager | Device credential or strong biometric |
| PDF export | Android PdfDocument to compose, PdfBox-Android to encrypt (AES-256) | Readable in any PDF reader |
| Build | Gradle wrapper, version catalog, R8 full mode, Baseline Profile, dependency verification | Size, startup, supply chain |

Not used: networking libraries, dependency-injection frameworks, Firebase or any Google Play services, analytics or crash SDKs, WebView, camera or microphone APIs. Exact versions are pinned in the version catalog when the project is created.

## 4. Packages

One Gradle module, organised by feature. Process numbers refer to the [system design](system-design.html) Figure 2.

| Package | Responsibility | DFD | Requirements |
| --- | --- | --- | --- |
| `ui.shell` | Officer and Show states, rotation, screen pinning, navigation between sheets | 2.0, 3.2 | FR-02, FR-03, FR-61 |
| `ui.officer` | Profile and language, triage groups, free text, picture board, gesture lookup, notes, session log | 2.0 | FR-07, FR-10 to FR-19, FR-31, FR-60, FR-65 |
| `ui.subject` | Render set, cards, Yes/No, clip and audio playback, drawing canvas, show-back, language finder | 3.1 to 3.4 | FR-11, FR-20, FR-21, FR-24, FR-63, FR-66 |
| `content` | Loads and validates the content bundle; resolves a request and profile into a render set; lexicon search for gesture lookup | 3.1, D2 | FR-11, FR-17, FR-62, FR-65 |
| `session` | Session state, event log, end and purge, retention worker | 4.0 | FR-05, FR-06, FR-30, FR-33, FR-34 |
| `export` | PDF report, passphrase, share sheet, interpreter hand-off | 5.0 | FR-32, FR-64 |
| `storage` | Room database, SQLCipher key handling, DataStore | D1, D3, D4 | NFR-30 |
| `security` | App lock, FLAG_SECURE, build expiry, root warning | 1.0 | FR-67, NFR-31, NFR-32 |

## 5. Content bundle

```
assets/content/
  manifest.json        content version, languages, SHA-256 of every file
  triage.json          questions: id, group, sensitive, captions{lang}, cards[], lexicon_id?, isl_clip?, audio{lang}?
  lexicon.json         gesture and ISL entries (schema in 05-avatar-and-gestures.md §5.2)
  phrases.json         phrase-bank templates with typed slots
  greetings.json       language finder: language, label in its own script, audio file
  cards/<id>.webp      picture cards
  clips/<set>/<id>.mp4 480x480 H.264, silent; sets: child, adult, isl
  audio/<lang>/<id>.ogg Opus, 16 kHz mono
```

- The app checks the manifest hashes at first start after install or update and refuses to show content that fails, falling back to the picture card and caption.
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

- `applicationId` is chosen at project creation; the department confirms the final identifier before the first signed release, because it cannot change afterwards.
- `BuildConfig.BUILD_EPOCH_DAY` is set at build time for the build-expiry check (FR-67).
- Release builds: R8 full mode, Baseline Profile for cold start and the first question, `debuggable=false`, no logging.
- Signing, SBOM, scanning and release steps are in [governance/11-devsecops-release.md](governance/11-devsecops-release.md).

## 10. Performance

| Target | Approach |
| --- | --- |
| Cold start under 2 s (NFR-01) | No work on startup beyond unlocking; Baseline Profile; content manifest checked once per install |
| Preset under 300 ms (NFR-02) | First-screen cards preloaded; one ExoPlayer instance reused; clips stored uncompressed in the APK so they play without extraction |
| Smooth clips (NFR-03) | 480x480 H.264 at source frame rate; hardware decode |
| APK under 30 MB (NFR-05) | WebP cards, Opus audio, short clips; size checked in CI |
| Memory under 300 MB (NFR-06) | No camera, single player, cards loaded lazily |

## 11. Release 1.1 (not built in release 1)

Voice questions and caption translation add the INTERNET permission, a project-owned gateway in an India-region cloud, Android key attestation, certificate pinning and Bhashini behind the gateway. The superseded contract draft is in [archive/09-api-contracts.md](archive/09-api-contracts.md). Adding release 1.1 requires an updated threat model, DPIA and audit scope.
