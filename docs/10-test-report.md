# 10. Phase 1 Test Report

Results of running the [test plan](06-test-plan.md) against the phase 1 build. Every result below was observed on the device listed; anything not yet run says so.

## 1. Build and devices

| Item | Value |
| --- | --- |
| Build | Debug and release (R8) builds of `in.signsetu` 0.1.0 |
| Release APK | 12.9 MB unsigned, ARM only (arm64-v8a, armeabi-v7a) |
| Devices | Android 10 (API 29) emulator, x86_64, 3 GB RAM; Android 14 (API 34) emulator, x86_64, 3 GB RAM, gesture navigation; release checked through a one-off x86_64 release build |
| Not yet used | Real Android 10 phone; recent phone. Android 17 (API 37) emulator images, standard and 16 KB page, crash the system compositor on the build machine, so Android 14 stands in as the recent-platform emulator; the native libraries were checked for 16 KB alignment on the APK itself |

## 2. Functional scenarios

| ID | Result | Evidence |
| --- | --- | --- |
| TC-01 | Partial | Fresh install on Android 14 with a screen lock: three first-run screens, unlock, home without scrolling. With no lock set, the lock screen offers security settings, and key loss after a lock reset recovers with a notice (Android 10); the no-lock path was not repeated on a fresh install |
| TC-02 | Pass | Each profile started: child character for young child and not sure, adult character for sign language, language finder first for another language |
| TC-03 | Pass | Stage shown; the officer handle needs the device credential; rotation option present (not re-photographed after the redesign) |
| TC-04 | Pass | Lock task state PINNED during the session and NONE after it ends; Android 10 shows its own "Screen is pinned" notice each time pinning resumes |
| TC-05 | Partial | Show to the stage's first frame on the debug build in the emulator: the database write takes 13 to 43 ms; the rest is drawing. Camera opening and pinning now wait for the first frame. Repeat shows took 283 to 388 ms, the first 672 ms, on an emulator that renders in software at about 48 ms a frame; repeat on a phone GPU |
| TC-06 | Pass | Sensitive group locked until confirmed |
| TC-07 | Pass | Typed question with one picture: the stage shows the typed caption and holds the picture; the character makes only its general asking pose, which carries no content |
| TC-08 | Pass | Officer tiles mark gesture and audio; the Show bar names caption, audio and gesture as present or "not available" |
| TC-09 | Pass (pending review) | "Do you want ...?" with water: the stage shows the full sentence and holds the picture while the character signs "you", "water" and the asking pose in one movement; logged as a phrase. Unit test checks every phrase's signs exist and chain in order. The sentences await content review |
| TC-10 | Pass | 14 tiles in their own scripts; tapping plays the greeting, selects, and the bubble greets back |
| TC-11 | Pass | Unit test for ranking; officer list shows the character's pose and picture for each match |
| TC-12 | Pass | "Is this it?" with the gesture and picture; Yes logs the meaning, otherwise unclear |
| TC-13 | Pass | Drawing logged as the answer; the subject stays on the stage until the officer re-authenticates |
| TC-14 | Pass | Force-stopped mid-session; reopening offered the unfinished session with its log |
| TC-15 | Pass | Release build export; PDF opened only with the password (AES-256, revision 6); share sheet offered |
| TC-16 | Pass | Clock moved 25 hours on the emulator: the forced retention job deleted the database and the wrapped key; reopening showed no session and no error. Decision also unit-tested (PolicyTest) |
| TC-17 | Pass | WhatsApp contact opens the `wa.me` link; with no contact the button is hidden |
| TC-18 | Pass | Clock moved 181 days: "Update required" shown and Start disabled even with a profile chosen. Decision also unit-tested (PolicyTest) |
| TC-19 | Pass | Permission asked of the officer at Start; mirror shown; with a stand-in hand model the 21-point overlay is drawn over the live preview |
| TC-20 | Pass | "Stop" logged and shown to the officer as a banner with guidance until acknowledged |
| TC-21 | Pass (pending review) | Rig unit tests pass for every gesture on both characters; reviewer sign-off in the gallery is outstanding |

## 3. Performance

| ID | Result |
| --- | --- |
| PT-01 | Emulator only: release cold start to first frame 0.79 to 1.03 s over four runs (`am start -W`); repeat on a real Android 10 phone |
| PT-02 | Emulator, debug build, software rendering: 283 to 388 ms after the first show (see TC-05) |
| PT-03 | Not meaningful on this emulator: it renders in software (SwiftShader), so every frame misses its deadline; measure on a phone GPU |
| PT-05 | Pass on the emulator: 139 MB total PSS on the stage with the camera running (limit 300 MB) |
| PT-04 | Pass: 12.9 MB, under 30 MB |

## 4. Security

| ID | Result | Evidence |
| --- | --- | --- |
| ST-01 | Pass | `adb backup`: Android reports the app "not eligible for backup"; the archive holds no app entries. Device-to-device transfer is excluded by the extraction rules but was not run |
| ST-02 | Pass | Database pulled as root from the emulator: no SQLite header, rejected by a standard SQLite library; neither it nor the settings file contains any session text |
| ST-03 | Pass | Screen capture refused on the release build |
| ST-05 | Pass | Merged manifest: CAMERA only among dangerous permissions; no INTERNET, RECORD_AUDIO or LOCATION |
| ST-09 | Pass | Android 10 and Android 14: four minutes in the background left the app open; just over five minutes asked for the device credential again ("Unlock Sign Setu"); relock on lock reset verified |
| ST-05 build gate | Pass | `check<Variant>Permissions` fails the build when INTERNET is added (tried and reverted) |
| ST-07 | Pass | Release manifest: only the launcher activity is exported, plus WorkManager's job service behind BIND_JOB_SERVICE (the SEC-41 exception) |
| ST-08 | Pass | Release dex: not debuggable; no gallery, stand-in hand model or timing log; no key or token patterns; only five entry-point classes keep their names, the rest is obfuscated |
| ST-10 | Pass | Wrong password rejected by a standard PDF library |
| ST-12 | Pass | A debug build with one byte added to `gestures.json` showed the officer a notice and played no gestures; with one byte added to `triage.json` it showed "Content check failed" and kept Start disabled. Also covers RD-05 |
| ST-13 | Partial | gitleaks clean on every commit; OSV over all 361 packages in `app/gradle.lockfile`: nothing on the release runtime classpath has a known vulnerability (BouncyCastle raised from 1.72 to 1.86); six packages with known issues sit only in Android Lint and the instrumented-test runtime, neither of which ships; SBOM produced |
| ST-04 | Pass | Release build (one-off x86_64 for the emulator): a typed question naming a person, a shown question, an answer and a note naming a place. The app's own log lines are rendering and memory messages only; no session text appears |
| ST-14 | Partial | The touch filter drops taps under Android's own "Screen pinned" notice, so obscured touches are filtered; a third-party overlay app over the stage and export was not run |

## 5. Accessibility

| ID | Result | Evidence |
| --- | --- | --- |
| AT-01 | Pass on emulator | The Accessibility Test Framework (the engine behind Accessibility Scanner) runs as instrumented tests on Android 14 over every stage state (greeting, held picture, choices, number pad, adult character, phrase, language finder, drawing) and the picture board, in both themes: 18 of 18 pass. It found the bottom-sheet drag handle 32 dp wide, now fixed. Contrast also computed for 32 text pairs, all at or above 4.5:1 |
| AT-02 | Pass | At 200% font scale the officer panel switches to one scrolling column with full labels |
| AT-03 | Partial | Every control on the stage and the officer panel exposes a spoken label in the accessibility tree (UI Automator dump); a spoken TalkBack walk-through is outstanding: neither the Google APIs nor the Google Play Android 10 emulator image includes TalkBack, so it needs a phone |
| AT-04 | Pass | All 14 language names and Hindi interface text render without clipping |
| AT-05 | Pass by design | Yes and No differ by icon (tick, cross), word and position as well as colour |

## 6. Defects found and fixed during the run

| Defect | Fix |
| --- | --- |
| Start crashed when asking for the camera permission (old fragment library from biometric 1.1.0) | Current fragment library declared |
| Content hashes taken over Windows line endings would reject every JSON file on a fresh clone | Content tool normalises to LF before hashing |
| Officer panel unusable at 200% text | Large-text layout |
| Text content files were used even when their check failed, and the officer was not told | Failed files are never parsed; a failed text file stops new sessions; the officer sees a notice |
| Dark theme: a chosen number's digit drawn white on light teal (1.6:1) | Paired text colour for the selection colour |
| Picture board favourite star: 40 dp target, and below 3:1 in both states | 48 dp toggle that announces its state; darker colours |
| Bottom-sheet drag handle 32 dp wide (found by the accessibility tests) | Every officer sheet uses one handle with a 48 dp touch area |
| Dark theme: wordmark and settings icon nearly invisible on the night stage | Stage title colour defined per theme |
| Dark theme: officer action labels drawn black on the dark panel | Panel drawn on a surface that sets the content colour |
| Dark theme: the symbols' dark outlines lost on dark tiles | Symbols always sit on a light well, in both themes |
| Touches during Android's "Screen pinned" toast are dropped by the overlay filter (SEC-45) | Known platform interaction; the toast lasts about two seconds |

## 7. Security controls self-check

Each control in [security](04-security.md) checked against the code and the release build.

| Controls | Status | Evidence |
| --- | --- | --- |
| SEC-01, SEC-10, SEC-11, SEC-22 | Met | SQLCipher database; 256-bit key wrapped by an auth-bound Keystore AES key; platform crypto only |
| SEC-02, SEC-08 | Met | Drawings only in the database; exports deleted on finish, clear and unlock; `secure_delete` and truncated journal |
| SEC-03, SEC-07 | Met | `allowBackup="false"` with exclusion rules; DataStore holds settings, card IDs and the department's interpreter contact only |
| SEC-04, SEC-53, SEC-60 | Met | R8 strips `Log` calls; release not debuggable; R8 full mode |
| SEC-05 | Partial | Note and question fields turn autocorrect off; Compose offers no switch for "no personalised learning" |
| SEC-06, SEC-44 | Met | Log text is not selectable; no notifications |
| SEC-12 | Met | AES-256 PDF, 80-bit one-time password, verified with a standard reader |
| SEC-20, SEC-21 | Met | Secure lock required; BiometricPrompt with API-level authenticators; five-minute relock |
| SEC-30, SEC-42 | Met | No INTERNET, RECORD_AUDIO or LOCATION; the build fails if one appears; CAMERA only |
| SEC-40, SEC-45 | Met | FLAG_SECURE in release; touches through overlays filtered |
| SEC-41 | Met with one exception | Only the launcher activity is exported, except WorkManager's job service, which Android requires and protects with BIND_JOB_SERVICE; two unused library receivers removed |
| SEC-46, SEC-47 | Met | Pinning from the first stage to the end; camera preview only, nothing stored, officer and department switches |
| SEC-50 | Met | `gradle/verification-metadata.xml` (SHA-256 for every artifact) and `app/gradle.lockfile` |
| SEC-51 | Met | `tools/security/sbom.py` writes a CycloneDX 1.7 SBOM of the 185 components on the release runtime classpath |
| SEC-52 | Met | Android Lint clean of errors; OSV-Scanner clean for shipped components; Semgrep (Kotlin and secrets rules) found nothing in 62 files; gitleaks on every commit |
| SEC-54 | Met | Every content file verified against the manifest before use; a failed file is never parsed or shown; LF hashing fixed |
| SEC-55, SEC-63, SEC-64 | Met | minSdk 29, target 37; rooted-phone warning; build expiry |
| SEC-62 | Open | Release signing key and custodians are a department decision |

## 8. Open items before the phase 1 exit

- Reviewer sign-off for every gesture and both characters (05 §8), and content review of the phrase-bank sentences.
- TC-05, PT-01, PT-02 and PT-03 on a real Android 10 phone with a GPU.
- Spoken TalkBack pass on a phone with TalkBack, and the formal MASVS review (`/masvs-checklist`).
- Partner hand model through `HandTrackers` in the release source set.
- ST-14 with a third-party overlay app, and device-to-device transfer for ST-01.
