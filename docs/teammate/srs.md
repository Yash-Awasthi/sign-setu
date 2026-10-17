# Sign Setu: Software Requirements Specification

Structure follows ISO/IEC/IEEE 29148. Requirement IDs, wording and priorities are taken from [01-requirements.md](../01-requirements.md) and [04-security.md](../04-security.md); those files are normative and this document is the index into them.

## 1. Scope

| Item | Value |
| --- | --- |
| Product | Sign Setu, an offline communication aid between a police officer and a subject (deaf, non-literate, child, or other-language speaker). An aid, not an interpreter (FR-52). |
| Deliverables | Android app (one Gradle module `app/`, namespace `app.signsetu`, applicationId `in.signsetu`); reviewer portal (`portal/`, Vite+, React, TypeScript); content bundle (`app/src/main/assets/`, built by `tools/content/build_content.py`). |
| Platform | Kotlin, Jetpack Compose, Material 3; minSdk 29, targetSdk 37 (`app/build.gradle.kts`). |
| Flavors | `standard` (reference build, Bluetooth stage link); `nearby` (`.nearby` suffix, adds Nearby Connections discovery permissions). |
| Out of scope (release 1) | Network use of any kind, voice input (FR-14, release 1.1), statement taking, server, device management. |

## 2. Actors

| Actor | Role | Touches | Trust |
| --- | --- | --- | --- |
| Officer | Owns the phone, runs the session, confirms everything shown, reads proposals, exports. | Officer panel, settings, export, pairing. | Authenticated by BiometricPrompt or device credential (SEC-21). |
| Subject | Victim, witness or accused; child or adult. | Stage (Show state) or stage device only. | Untrusted input; never sees machine readings (FR-85). |
| Support person | Interpreter, guardian or special educator present at the session or on the interpreter call (FR-64). | Enters a word per sign on replay (FR-87); explains rights (FR-88). | Acts through the officer's device; no own login. |
| Reviewer | Content and sign reviewer. | `portal/` only, at build time; approves bank items, signs and motions that reach the content bundle. | Outside the app; output enters the app only through manifest-hashed assets (SEC-54). |

## 3. Functional requirements

Priority as in the source. Long requirements are summarised; the source text governs.

### 3.1 Session

| ID | Requirement | Priority |
| --- | --- | --- |
| FR-03 | Two states, Officer and Show; one tap switches. | MUST |
| FR-05 | Opening resumes the unfinished session or starts a new one. | MUST |
| FR-06 | Ending offers export, then purges all session data. | MUST |
| FR-07 | Subject language per session (Hindi, English minimum). | MUST |
| FR-60 | Subject profile per session, changeable any time. | MUST |
| FR-30 | Every question shown and answer received logged with timestamp. | MUST |
| FR-31 | Officer free-text notes. | MUST |
| FR-33 | Unexported sessions purged after retention (default 24 h, 4 to 72 h). | MUST |
| FR-34 | Every event saved before the UI updates. | MUST |
| FR-50 | First run: three screens, officer language, secure lock required, pinning guidance. | MUST |
| FR-51 | Settings: language, text size, clip speed, retention, clear all, about, licences. | MUST |
| FR-67 | Build stops new sessions 180 days after build date. | MUST |
| FR-91 | Answer trail and evidence grade under each answer. | MUST |
| FR-101 | After crash, kill or relock while staged, unlock needs full biometric or PIN. | MUST |

### 3.2 Stage

| ID | Requirement | Priority |
| --- | --- | --- |
| FR-02 | Show state rotates 180 degrees. | MUST |
| FR-61 | Screen pinning from first Show to end; officer handle to leave; Back inert. | MUST |
| FR-20 | Yes and No always available as large cards. | MUST |
| FR-24 | Finger drawing canvas, logged as answer. | MUST |
| FR-69 | Live hand mirror from front camera; nothing captured outside a sign window. | MUST |
| FR-70 | "I don't know", "I don't understand", "That's wrong", Stop (octagon) on every question. | MUST |
| FR-71 | Subject types own answer, logged verbatim, max 2,000 chars. | MUST |
| FR-82 | "I don't understand" always offered. | MUST |
| FR-93 | Picture choices shuffled, max four, plus "Something else" and "I don't understand". | MUST |
| FR-95 | Opening: introduction, four ground rules, practice round. | MUST |
| FR-96 | Pause layer after Stop; only "I'm ready" ends it. | MUST |
| FR-97 | Officer glance strip, upside down, never shows machine readings. | SHOULD |
| FR-98 | Vibration answer codes; never for young child. | SHOULD |
| FR-99 | Sun mode (on > 10,000 lux for 3 s, off < 2,000 lux for 10 s). | SHOULD |
| FR-100 | Tilt to show, Volume Up; both keys 1.5 s returns to officer. | SHOULD |
| FR-105 | Phrase board on stage; tap logged as subject's own. | SHOULD |
| FR-107 | Camera at 9:16 uncropped between two answer rails; Stop the only pause control. | MUST |

### 3.3 Text to sign and asking

| ID | Requirement | Priority |
| --- | --- | --- |
| FR-10 | Preset questions in groups (Safety, Health, ... Sensitive, rights). | MUST |
| FR-11 | Preset shows cards, caption, and by profile character gesture, ISL clip or audio; no network. | MUST |
| FR-12 | Sensitive questions behind a second tap. | MUST |
| FR-13 | Free text played word by word: recorded ISL, authored signs, gestures, fingerspelling, pictures. | MUST |
| FR-15 | Nothing reaches Show until the officer confirms. | MUST |
| FR-17 | Missing gesture, clip or audio: picture and caption alone, officer sees "not available". | MUST |
| FR-18 | Replay and slow speed. | MUST |
| FR-19 | Full picture board with categories, search, favourites. | MUST |
| FR-62 | Phrase bank with slots. | SHOULD |
| FR-63 | Language finder. | MUST |
| FR-64 | Call an interpreter via Sandes or WhatsApp; no session data passed. | MUST |
| FR-68 | On-device machine translation with back-translation, marked not checked. | SHOULD |
| FR-89 | Word completions tinted by what the character will do. | SHOULD |
| FR-90 | Question run of up to 5; sensitive never queued. | SHOULD |
| FR-106 | ISL dictionary search (English, Hindi), play, prepare for stage. | SHOULD |

### 3.4 Sign capture and reading

| ID | Requirement | Priority |
| --- | --- | --- |
| FR-84 | Frames analysed only inside an explicit sign window: answer up to 20 s, Tell me up to 3 min. | MUST |
| FR-85 | Split at pauses, read on device into ranked proposals; context matches first; officer only, "read by the app, not checked". | SHOULD |
| FR-86 | A reading becomes the answer only when the subject picks it among 2 or 3 pictures plus "None of these"; each set logged before shown. | MUST |
| FR-87 | Replay at 1/4x or 1/2x with sign markers; word per sign entry. | SHOULD |
| FR-65 | Gesture lookup by body location and hand action. | MUST |
| FR-66 | Show-back "Is this it?" with alternatives; unclear otherwise. | MUST |
| FR-92 | Say it back; only the subject's pick confirms. | MUST |

### 3.5 Lesson-sheet questions

| ID | Requirement | Priority |
| --- | --- | --- |
| FR-102 | Typed inputs: number pad, 10-digit mobile (6 to 9 start), name (letters, 60), text (80), class grid, state then district, multi-picture. Escape answers and Stop stay under every input. | MUST |
| FR-103 | Written or typed answer read back; subject's own only on Yes; else logged unconfirmed. | MUST |
| FR-104 | Report table "Personal details given". | MUST |

### 3.6 Roles and rights

| ID | Requirement | Priority |
| --- | --- | --- |
| FR-88 | Role: victim or witness, accused or in custody, "Not known yet" default. Sensitive and free recall never for accused; rights for accused (child rights under 18); each rights item marked "shown, not proof of understanding". | MUST |

### 3.7 Auto mode

| ID | Requirement | Priority |
| --- | --- | --- |
| FR-77 | Manual and Auto switch; Manual default; Auto only if allowed in Settings. | SHOULD |
| FR-78 | Auto proposes only reviewed rapport, ground-rule, welfare, practical, break, closing items, in order, by role, deterministically. Never sensitive, free recall, follow-up or rights. | MUST (with FR-77) |
| FR-79 | Every question needs one officer tap; statements may self-advance, max 3 in a row. | MUST (with FR-77) |
| FR-80 | Rules R1 to R8 of D-30 apply. | MUST (with FR-77) |
| FR-81 | Suggested, approved, skipped, auto-advanced logged with item ID, source, reason. | MUST |
| FR-83 | Stop pauses Auto; second Stop ends it. | MUST (with FR-77) |

### 3.8 Pairing (second screen)

| ID | Requirement | Priority |
| --- | --- | --- |
| FR-72 | Pair one already-bonded stage device; 6-digit code, 4-digit check number, Match; three wrong codes close. | SHOULD |
| FR-73 | Stage device stores only `stageDevice`; pinned while Live; pinned hand-back screen at end. | MUST (with FR-72) |
| FR-74 | Stage answers logged on officer phone, acked after commit, de-duplicated on (item, n). | MUST (with FR-72) |
| FR-75 | Ping 10 s, lost 30 s, resume within 120 s without code; Reconnect and "Use this phone". | MUST (with FR-72) |
| FR-76 | Stop and "I don't know" always on the stage device; break blocks Show until "Break given". | MUST (with FR-72) |

### 3.9 Export

| ID | Requirement | Priority |
| --- | --- | --- |
| FR-32 | AES-256 encrypted PDF under a one-time passphrase shown once; Android share sheet. | MUST |
| FR-94 | "Shown as" rows, not-a-statement notice top and bottom of every page, grade legend, readings in a separate section. | MUST |

## 4. Non-functional requirements

| Area | ID | Requirement | Verification |
| --- | --- | --- | --- |
| Performance | NFR-01 | Cold start under 2 s on the oldest test device. | Device timing |
| Performance | NFR-02 | Preset in Show within 300 ms of confirm. | Device timing |
| Performance | NFR-03 | Clips at source frame rate, no stutter. | Device test |
| Performance | NFR-06 | Memory under 300 MB per session (SHOULD). | Profiler |
| Size | NFR-05 | APK under 50 MB incl. vision and sign-reader models. Current unsigned release: 45.4 MiB `standard`, 45.5 MiB `nearby` ([02-architecture.md](../02-architecture.md) §10). | Build output |
| Platform | NFR-10, NFR-11 | API 29+, 3 GB RAM+, portrait, 5 to 6.9 inch. | Device matrix |
| Security | NFR-30, SEC-01 | SQLCipher DB; 256-bit DEK wrapped by Keystore KEK. | Code review, MASVS-STORAGE |
| Security | NFR-31, SEC-21 | Biometric or device credential to open app and past sessions. | UAT |
| Security | NFR-32 | FLAG_SECURE: no screenshots, recording, recents thumbnails. | Device test |
| Security | NFR-33 | No session data in backups or device transfer. | `adb backup`, manifest review |
| Security | NFR-36 | No analytics, ads or crash-reporting SDKs; MediaPipe telemetry stubbed (SEC-72). | Dependency review |
| Security | SEC-54, SEC-74 | Content and models verified against manifest SHA-256 before use. | Tamper test |
| Privacy | NFR-37 | No personal data in logs, crash traces, notifications; release strips `android.util.Log`. | Log review |
| Privacy | SEC-71 | Landmarks are biometric data: memory only, session-scoped, wiped at end. | Code review |
| Privacy | FR-33 | Retention purge 4 to 72 h, then `VACUUM`. | Test |
| Accessibility | NFR-20 | Touch targets 48 dp; subject cards 96 dp. | Layout audit |
| Accessibility | NFR-21 | Contrast 4.5:1; meaning not by colour alone. | Audit |
| Accessibility | NFR-22, NFR-23 | Officer panel at 200% font scale; TalkBack (SHOULD). | Device test |
| Accessibility | NFR-24 | Subject panel: no timers, flashing, pop-ups (except the 20 s sign window). | Review |
| Accessibility | NFR-25 | All text from resources; Devanagari and regional scripts render. | Device test |
| Offline | NFR-34 | Fully offline; no network permission. | Merged-manifest check |
| Reliability | NFR-40 | Under 1 crash per 100 pilot sessions. | Pilot data |

## 5. Constraints

| Constraint | Detail | Enforced by |
| --- | --- | --- |
| Forbidden permissions (all variants) | `INTERNET`, `RECORD_AUDIO`, `ACCESS_BACKGROUND_LOCATION` | Merged-manifest check, `app/build.gradle.kts` (fails the build) |
| Forbidden in `standard` only | `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `BLUETOOTH_SCAN`, `BLUETOOTH_ADMIN`, `BLUETOOTH_ADVERTISE`, `NEARBY_WIFI_DEVICES` | Same check |
| Declared permissions | `CAMERA`, `VIBRATE`, `BLUETOOTH` (maxSdk 30), `BLUETOOTH_CONNECT` | `app/src/main/AndroidManifest.xml` |
| No Play services in `standard` | Play services only in `nearby` (D-26) | `app/build.gradle.kts` |
| Exported components | Launcher activity and WorkManager job service only (SEC-41) | Manifest review |
| Cost | Free and open-source components only (D-10) | Licence review |
| Law | DPDP Act 2023 and Rules 2025, JJ Act s.74, POCSO ss.24 and 26, BNSS, BSA s.125, Art. 20(3), RPwD with IS 17802 | [governance/](../governance/) |
| Distribution | Signed APK via Mobile Seva AppStore or department page; CERT-In audit before production | [governance/11-devsecops-release.md](../governance/11-devsecops-release.md) |

## 6. External interfaces

| Interface | Type | Direction | Notes |
| --- | --- | --- | --- |
| Touchscreen, volume keys, accelerometer, ambient light | HW | In | FR-99, FR-100; light sensor read only while staged |
| Front camera (CameraX) | HW | In | Mirror always; analysis only inside a sign window (FR-84) |
| Vibrator | HW | Out | FR-98 |
| Android Keystore | OS | Both | KEK, non-exportable, auth-bound |
| BiometricPrompt | OS | In | SEC-21 |
| Screen pinning, FLAG_SECURE | OS | Out | FR-61, NFR-32 |
| Android share sheet via FileProvider `${applicationId}.exports` | OS | Out | Encrypted PDF only (FR-32) |
| Bluetooth RFCOMM to bonded device | Peer | Both | ECDH P-256, HKDF-SHA256, per-direction keys (SEC-67) |
| Nearby Connections | Peer | Both | `nearby` flavor only |
| Sandes or WhatsApp intent | App | Out | Contact only, no session data (FR-64) |
| MediaPipe Tasks Vision (Holistic) | Library | In-process | Model in APK, GPU then CPU |
| `LandmarkSignReader` | In-house | In-process | 262-word classifier, plain Kotlin |
| Content bundle `assets/` + `manifest.json` | Data | In | SHA-256 per file; manifest hash is the content version |
| Reviewer portal `portal/` | Build-time | Out to bundle | Imports app content files; no runtime link to the app |

## 7. Acceptance criteria

| # | Criterion | Requirements | Method |
| --- | --- | --- | --- |
| AC-01 | `assembleStandardRelease` and `assembleNearbyRelease` succeed; build fails if a forbidden permission is merged. | NFR-34, SEC-30 | CI |
| AC-02 | Release APK size under 50 MB for both flavors. | NFR-05 | CI |
| AC-03 | Kill the app mid-session; reopen; no event lost and unlock asks full credential. | FR-34, FR-101 | Device test |
| AC-04 | Nothing reaches Show without an officer confirm; sensitive needs a second tap. | FR-12, FR-15 | UI test |
| AC-05 | No camera analysis outside a sign window; landmarks absent after session end. | FR-84, SEC-71 | Code review, heap check |
| AC-06 | A sign reading never appears on the stage until the subject picks it from 2 or 3 alternatives plus "None of these". | FR-85, FR-86 | UI test |
| AC-07 | Accused role never offered sensitive or free-recall items; rights items carry the interpreter notice. | FR-88 | Unit test |
| AC-08 | Auto never proposes sensitive, free recall, follow-up or rights; max 3 statements in a row. | FR-78, FR-79 | Unit test |
| AC-09 | Pairing: wrong code three times closes; duplicate (item, n) logged once; lost after 30 s. | FR-72, FR-74, FR-75 | Instrumented test, UAT-18 |
| AC-10 | Export opens only with the shown passphrase; every page has the not-a-statement notice. | FR-32, FR-94 | Manual |
| AC-11 | Tampered asset file is not shown; tampered model is not loaded. | SEC-54, SEC-74 | Tamper test |
| AC-12 | Screenshots blocked; session absent from backup; no personal data in logcat. | NFR-32, NFR-33, NFR-37 | Device test |
| AC-13 | Retention purge removes rows after the configured period. | FR-33 | Instrumented test |
| AC-14 | Performance targets met on the oldest test device. | NFR-01 to NFR-03, NFR-06 | [06-test-plan.md](../06-test-plan.md) §3 |

Full test mapping: [01-requirements.md](../01-requirements.md) §6 and [06-test-plan.md](../06-test-plan.md).
