# 10. Test Report

Results of running the [test plan](06-test-plan.md) against the release 1 build. Every result below was observed on the device listed; anything not yet run says so. Sections 2 to 6 cover the first build; section 7 covers the build with the vision engine, sign reader, roles, conversation modes, second screen and the stage and record changes (D-25 to D-34), as integrated and fixed.

## 1. Build and devices

| Item | Value |
| --- | --- |
| Build | Debug and release (R8) builds of `in.signsetu` 0.1.0 |
| Release APK | 19.1 MB unsigned, ARM only (arm64-v8a, armeabi-v7a); the assistant's native library ships for arm64-v8a only |
| Automated | 17 unit tests and 20 instrumented tests (Android 14 emulator) pass; Android Lint clean of errors |
| Devices | Android 10 (API 29) emulator, x86_64, 3 GB RAM; Android 14 (API 34) emulator, x86_64, 3 GB RAM, gesture navigation; release checked through a one-off x86_64 release build; a 5-inch screen (360 by 640 dp) simulated on the Android 14 emulator for NFR-11 |
| Not yet used | Real Android 10 phone; recent phone. Android 17 (API 37) emulator images, standard and 16 KB page, crash the system compositor on the build machine, so Android 14 stands in as the recent-platform emulator; the native libraries were checked for 16 KB alignment on the APK itself |

## 2. Functional scenarios

| ID | Result | Evidence |
| --- | --- | --- |
| TC-01 | Partial | Fresh install on Android 14 with a screen lock: three first-run screens, unlock, home without scrolling. With no lock set, the lock screen offers security settings, and key loss after a lock reset recovers with a notice (Android 10); the no-lock path was not repeated on a fresh install |
| TC-02 | Pass | Each profile started: child character for young child and not sure, adult character for sign language, language finder first for another language |
| TC-03 | Pass | Stage shown; Back stays on the stage; one tap on the handle returns to the officer panel; with the setting on, the credential is asked as well; rotation option present |
| TC-04 | Pass | Lock task state PINNED during the session and NONE after it ends; Android 10 shows its own "Screen is pinned" notice each time pinning resumes |
| TC-05 | Partial | Show to the stage's first frame on the debug build in the emulator: the database write takes 13 to 43 ms; the rest is drawing. Camera opening and pinning now wait for the first frame. Repeat shows took 283 to 388 ms, the first 672 ms, on an emulator that renders in software at about 48 ms a frame; repeat on a phone GPU |
| TC-06 | Pass | Sensitive group locked until confirmed |
| TC-07 | Pass (pending review) | "where is ravi mother?" with the signer defaults: the asking sign for "where", R-A-V-I spelled with each letter shown, the mother sign with its picture; each word lit in turn. Planning unit-tested (AvatarTest). The alphabet and signs await ISL review |
| TC-08 | Pass | Officer tiles mark gesture and audio; the Show bar names caption, audio and gesture as present or "not available" |
| TC-09 | Pass (pending review) | "Do you want ...?" with water: the stage shows the full sentence and holds the picture while the character signs "you", "water" and the asking pose in one movement; logged as a phrase; the sentence is spoken in the subject's language for hearing profiles. Unit test checks every phrase's signs exist and chain in order, and that each sentence has its recording. The sentences and their placeholder recordings await content review |
| TC-10 | Pass | 14 tiles in their own scripts; tapping plays the greeting, selects, and the bubble greets back |
| TC-11 | Pass | Unit test for ranking; officer list shows the character's pose and picture for each match |
| TC-12 | Pass | "Is this it?" with the gesture and picture; Yes logs the meaning, otherwise unclear |
| TC-13 | Pass | Drawing logged as the answer; the subject stays on the stage until the officer takes the phone back |
| TC-14 | Pass | Force-stopped mid-session; reopening offered the unfinished session with its log |
| TC-15 | Pass | Release build export; PDF opened only with the password (AES-256, revision 6); share sheet offered |
| TC-16 | Pass | Clock moved 25 hours on the emulator: the forced retention job deleted the database and the wrapped key; reopening showed no session and no error. Decision also unit-tested (PolicyTest) |
| TC-17 | Pass | WhatsApp contact opens the `wa.me` link; with no contact the button is hidden |
| TC-18 | Pass | Clock moved 181 days: "Update required" shown and Start disabled even with a profile chosen. Decision also unit-tested (PolicyTest) |
| TC-19 | Pass | Permission asked of the officer at Start; mirror shown; with a stand-in hand model the 21-point overlay is drawn over the live preview |
| TC-20 | Pass | "Stop" logged and shown to the officer as a banner with guidance until acknowledged |
| TC-21 | Pass (pending review) | Rig unit tests pass for every gesture, letter and sign on both characters, including fingertip-to-fingertip contact; reviewer sign-off in the gallery is outstanding |
| TC-22 | Pass | "my name is ravi" written on the stage of a signing subject; shown in the officer's conversation as a typed answer and in the log |
| TC-23 | Pass (pending review) | Gemma 3 1B imported and accepted by hash; a random file refused with nothing kept. "are you hungry? do you want water?" gave "आप भूखे हैं? क्या आप पानी चाहते हैं?" and back "Are you hungry? Do you want some water?" in 66 s on the emulator; shown as the caption and logged with the English. Speed on an arm64 phone and per-language quality await the phone and the language reviewers |
| TC-24 | Pass | The composer's editor info reports `imeOptions=0x3000004`, which includes `IME_FLAG_NO_PERSONALIZED_LEARNING` |

## 3. Performance

| ID | Result |
| --- | --- |
| PT-01 | Emulator only: release cold start to first frame 0.79 to 1.03 s over four runs (`am start -W`); repeat on a real Android 10 phone |
| PT-02 | Debug build, Android 14 emulator on a desktop GPU: 173 to 236 ms over four repeat shows, 510 ms for the first (target 300 ms). Software-rendered Android 10: 283 to 388 ms repeat, 672 ms first. Repeat on a real phone |
| PT-03 | Partial. Release build, Android 14 emulator on a desktop GPU, four shows of about 7 s each: 0 to 8 janky frames of about 420 (0 to 2%), all as the stage opens; gesture playback itself drops none. Repeat on a real phone |
| PT-05 | Pass on the emulator: 139 MB total PSS on the stage with the camera running (limit 300 MB) |
| PT-04 | Pass: 19.1 MB, under 30 MB |

## 4. Security

| ID | Result | Evidence |
| --- | --- | --- |
| ST-01 | Pass (transfer by configuration) | `adb backup`: Android reports the app "not eligible for backup"; the archive holds no app entries. Device-to-device transfer ignores `allowBackup` from Android 12; the extraction rules exclude every domain (root, files, databases, shared preferences, external) for both cloud backup and device transfer, and a copied database would stay unreadable because its key is wrapped by this phone's Keystore (SEC-01). A live transfer needs a Google account on the test phone and was not run |
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
| ST-14 | Pass | Android 10, a separate test app drawing a translucent overlay that lets touches through: "Show to them", a picture-board card, the officer menu, "Export report" and "Finish and delete session" all ignored the tap while the overlay was up and worked once it was removed. Instrumented test checks that dialog and sheet windows keep the filter |

## 5. Accessibility

| ID | Result | Evidence |
| --- | --- | --- |
| AT-01 | Pass on emulator | The Accessibility Test Framework (the engine behind Accessibility Scanner) runs as instrumented tests on Android 14 over every stage state (greeting, held picture, choices, number pad, adult character, phrase, language finder, drawing) and the picture board, in both themes, at the emulator's own size and at 5 inches: 18 of 18 pass each time. It found the bottom-sheet drag handle 32 dp wide, now fixed. Contrast also computed for 32 text pairs, all at or above 4.5:1 |
| AT-02 | Pass | At 200% font scale the officer panel switches to one scrolling column with full labels. The subject's stage, answer cards and home screen also stay whole at 200%; the lock screen now gives its character less room at large sizes so the unlock button and notice stay visible |
| AT-03 | Partial | Every control on the stage and the officer panel exposes a spoken label in the accessibility tree (UI Automator dump); a spoken TalkBack walk-through is outstanding: neither the Google APIs nor the Google Play Android 10 emulator image includes TalkBack, so it needs a phone |
| AT-04 | Pass | All 14 language names and Hindi interface text render without clipping |
| AT-05 | Pass by design | Yes and No differ by icon (tick, cross), word and position as well as colour |

## 6. Security controls self-check

Each control in [security](04-security.md) checked against the code and the release build.

| Controls | Status | Evidence |
| --- | --- | --- |
| SEC-01, SEC-10, SEC-11, SEC-22 | Met | SQLCipher database; 256-bit key wrapped by an auth-bound Keystore AES key; platform crypto only |
| SEC-02, SEC-08 | Met | Drawings only in the database; exports deleted on finish, clear and unlock; `secure_delete` and truncated journal |
| SEC-03, SEC-07 | Met | `allowBackup="false"` with exclusion rules; DataStore holds settings, card IDs and the department's interpreter contact only |
| SEC-04, SEC-53, SEC-60 | Met | R8 strips `Log` calls; release not debuggable; R8 full mode |
| SEC-05 | Met | Every text field asks the keyboard for no personalised learning through one input interceptor at the app root (TC-24) |
| SEC-06, SEC-44 | Met | Log text is not selectable; no notifications |
| SEC-12 | Met | AES-256 PDF, 80-bit one-time password, verified with a standard reader |
| SEC-20, SEC-21 | Met | Secure lock required; BiometricPrompt with API-level authenticators, skipped while the key is still valid from the phone unlock; five-minute relock |
| SEC-65 | Met | Model kept only if its SHA-256 is on the allow-list, computed while copying; refused file deleted before parsing (TC-23) |
| SEC-30, SEC-42 | Met | No INTERNET, RECORD_AUDIO or LOCATION; the build fails if one appears; CAMERA only |
| SEC-40, SEC-45 | Met | FLAG_SECURE in release; touches through another app's overlay filtered in the activity and in every dialog, sheet and menu (ST-14) |
| SEC-41 | Met with one exception | Only the launcher activity is exported, except WorkManager's job service, which Android requires and protects with BIND_JOB_SERVICE; two unused library receivers removed |
| SEC-46, SEC-47 | Met | Pinning from the first stage to the end; camera preview only, nothing stored, officer and department switches |
| SEC-50 | Met | `gradle/verification-metadata.xml` (SHA-256 for every artifact) and `app/gradle.lockfile` |
| SEC-51 | Met | `tools/security/sbom.py` writes a CycloneDX 1.7 SBOM of the 185 components on the release runtime classpath |
| SEC-52 | Met | Android Lint clean of errors; OSV-Scanner clean for shipped components; Semgrep (Kotlin and secrets rules) found nothing in 62 files; gitleaks on every commit |
| SEC-54 | Met | Every content file verified against the manifest before use; a failed file is never parsed or shown; LF hashing fixed |
| SEC-55, SEC-64 | Met | minSdk 29, target 37; build expiry |
| SEC-63 | Partial | Rooted-phone warning shown; disabling session history waits on the department's rooted-device decision (threat model T-19) |
| SEC-62 | Open | Release signing key and custodians are a department decision |

## 7. Integrated build: vision, sign reading, roles, modes, second screen

### 7.1 Build

| Item | Value |
| --- | --- |
| Variants | `standardDebug`, `standardRelease`, `nearbyDebug`, `nearbyRelease` of `in.signsetu` 0.1.0 (`in.signsetu.nearby` for the nearby flavor) |
| Release APK | 47.0 MB (`standard`) and 47.7 MB (`nearby`), unsigned, arm64-v8a and armeabi-v7a; MediaPipe's native library ships for arm64-v8a only; under the 50 MB limit of NFR-05 |
| Unit tests | 120 tests in 17 classes pass for both flavors (`testStandardDebugUnitTest`, `testNearbyDebugUnitTest`) |
| Instrumented tests | 19 tests (overlay 2, migration 2, accessibility 15) compile; their last full run on the Android 14 emulator predates the merge, so the run on this build is open |
| Device | Android 14 (API 34) emulator, x86_64, with the emulator's virtual camera and the debug stand-in hand |

### 7.2 Functional scenarios

| ID | Result | Evidence |
| --- | --- | --- |
| TC-25 | Pass | Unit test. The emulator smoke test found that no frame was ever analysed: the first-frame sentinel overflowed the interval check. Fixed, and covered by `AnalysisRateTest` |
| TC-26 | Pass | Unit tests (`SegmentTest` 5, `SignIngestionTest` segmentation cases) |
| TC-27 | Pass | Logits within 1e-3 of the Python reference on all five fixtures; a forward pass takes about 18 ms on a desktop JVM. Accuracy on phone cameras with children is unmeasured |
| TC-28 | Pass | Unit tests; on the emulator the slower cue appeared, in Hindi with its picture, after a failed read of the stand-in hand |
| TC-29 | Pass on emulator | Read signs showed the hand overlay, one dot per sign and Done; the officer's sheet showed the app's reading, slow replay at ¼× and the guesses as chips; Ask them to choose showed picture cards and None of these on the stage. The smoke test also found app readings graded as the officer's and "Ask them to choose" offered with nothing to choose; both fixed. Landmark wiping is verified by code review; no memory dump was taken |
| TC-30 | Pass | Unit tests (`RoleTest` 4) against the real bank; on the emulator the rights group, the officer's interpreter card, the subject line on the stage and "shown, not proof of understanding" in the transcript and report |
| TC-31 | Pass | Unit tests (`InterviewTest` 17). The smoke test found Auto proposing ground rules the stage opening had already covered; fixed and tested |
| TC-32 | Pass | Unit tests (`AutoPilotTest` 10); on the emulator a suggestion was approved, shown and logged |
| TC-33 | Pass | Unit tests (`PairTest` 28), including the check number added after review |
| TC-34 | Pass | Unit tests (`MainPageTest` 8); on the emulator completions, a question run of two items and the report with grades and the not-a-statement notice on each page. The smoke test found a duplicate "I don't understand" tile; removed |
| TC-35 | Pass | Unit tests (`StageLogicTest` 6); on the emulator the ground rules, the practice round, the pause layer after Stop and the officer glance strip |
| TC-36 | Pass | Unit tests (`SunModeTest` 2); sun mode forced on from the menu on the emulator. The light sensor itself needs a phone |
| TC-37 | Pass | Unit tests (`HandoverTest` 6). Tilt and volume keys on a phone are open |
| TC-38 | Open | The process was killed while the stage showed on the emulator, but the restart was not captured; repeat and record. The `stage_live` flag logic is reviewed in code |
| TC-39 | Open | `SessionMigrationTest` compiles; it has not run on this build |
| TC-40 | Pass | `RigFixtureTest` passes; `vp test`: 756 tests in 3 files pass |
| TC-41 | Open | Needs two phones and a tablet (UAT-18); on the emulator only the officer's pairing sheet was checked |

### 7.3 Security

| ID | Result | Evidence |
| --- | --- | --- |
| ST-05, ST-15 | Pass | Merged release manifests. `standard`: CAMERA, BLUETOOTH_CONNECT and BLUETOOTH (up to API 30) among the permissions that matter, plus VIBRATE; no INTERNET, RECORD_AUDIO, location, scan, advertise or NEARBY_WIFI_DEVICES. `nearby`: adds BLUETOOTH_SCAN, BLUETOOTH_ADVERTISE, BLUETOOTH_ADMIN (up to API 30), NEARBY_WIFI_DEVICES, ACCESS_FINE_LOCATION (up to API 32), and Wi-Fi state (up to API 31); no INTERNET. Both release variants assemble through `check<Variant>Permissions` |
| ST-16 | Pass | `transport-backend-cct` is excluded in the build script and the stub destination is in the APK; the merged manifest has no INTERNET |
| ST-17 | Open | Needs a stage device |
| ST-18 | Partial | Pairing code logs exception class names only (code review); a logcat capture during a sign window on a phone is open |
| ST-19 | Pass by design | Both models are listed in the content manifest and checked before loading (`HolisticTracker`, `SignReaders`); a tampered-model run is open |

### 7.4 Security controls self-check

| Controls | Status | Evidence |
| --- | --- | --- |
| SEC-42 | Met | Runtime permissions CAMERA and BLUETOOTH_CONNECT only (ST-15) |
| SEC-66, SEC-73 | Met | Bonded RFCOMM, no scan or bond calls; per-variant permission gate |
| SEC-67, SEC-68 | Met | Handshake, frame and resume behaviour covered by `PairTest` |
| SEC-69, SEC-70 | Met in code | Stage stores only the `stageDevice` flag; exception class names only; ST-17 on a device is open |
| SEC-71 | Met in code | Analysis gated by the sign window; landmarks in view-model memory, wiped on end, lock and clear; narratives drop faces |
| SEC-72 | Met | ST-16 |
| SEC-74 | Met | Manifest hash check before each model loads |
| SEC-75 | Met in code | TC-38 open |

## 8. Open items before production

- Reviewer sign-off for every gesture and both characters (05 §8), the phrase-bank sentences, the placeholder recordings, the draft ISL word order and sign-word map, the rights wording in English and Hindi, and the reworded x2 and x3, on the [content review record](governance/forms/content-review-record.md).
- TC-05, PT-01, PT-02 and PT-03 on a real Android 10 phone with a GPU; the vision engine's frame rate, memory and left-right hand assignment on a real 64-bit phone.
- The instrumented suite (TC-39, AT-06) on the merged build; the two-phone and tablet script (TC-41, UAT-18) and ST-17.
- Spoken TalkBack pass on a phone with TalkBack, and the formal MASVS review.
- Sign reader accuracy with phone cameras and children; data for the core answer words the reader cannot read (yes, no, water, pain, help, stop, don't know).
- Assistant speed on an arm64 phone (TC-23), and language reviewers' spot-check of its output.
- A live device-to-device transfer (ST-01) on two phones with a Google account.
