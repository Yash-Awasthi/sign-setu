# 10. Phase 1 Test Report

Results of running the [test plan](06-test-plan.md) against the phase 1 build. Every result below was observed on the device listed; anything not yet run says so.

## 1. Build and devices

| Item | Value |
| --- | --- |
| Build | Debug and release (R8) builds of `in.signsetu` 0.1.0 |
| Release APK | 16.96 MB, ARM only (arm64-v8a, armeabi-v7a) |
| Device | Android 10 (API 29) emulator, x86_64, 3 GB RAM; release checked through a one-off x86_64 release build |
| Not yet used | Real Android 10 phone; recent phone; latest-API emulator |

## 2. Functional scenarios

| ID | Result | Evidence |
| --- | --- | --- |
| TC-01 | Partial | Lock screen offers security settings when no lock is set; key loss after a lock reset recovers with a notice. Fresh-install run not repeated after the redesign |
| TC-02 | Pass | Each profile started: child character for young child and not sure, adult character for sign language, language finder first for another language |
| TC-03 | Pass | Stage shown; the officer handle needs the device credential; rotation option present (not re-photographed after the redesign) |
| TC-04 | Pass | Lock task state PINNED during the session and NONE after it ends; Android 10 shows its own "Screen is pinned" notice each time pinning resumes |
| TC-05 | Partial | Show to the stage's first frame on the debug build in the emulator: the database write takes 13 to 43 ms; the rest is drawing. Camera opening and pinning now wait for the first frame. Repeat shows took 283 to 388 ms, the first 672 ms, on an emulator that renders in software at about 48 ms a frame; repeat on a phone GPU |
| TC-06 | Pass | Sensitive group locked until confirmed |
| TC-07 | Not re-run | Free text moved into a sheet; flow unchanged in the view model |
| TC-08 | Pass | Officer tiles mark gesture and audio; the Show bar names caption, audio and gesture as present or "not available" |
| TC-09 | Not built | Phrase bank (SHOULD), first in the cut order |
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
| PT-04 | Pass: 16.96 MB, under 30 MB |

## 4. Security

| ID | Result | Evidence |
| --- | --- | --- |
| ST-03 | Pass | Screen capture refused on the release build |
| ST-05 | Pass | Merged manifest: CAMERA only among dangerous permissions; no INTERNET, RECORD_AUDIO or LOCATION |
| ST-09 | Partial | Relock on lock-reset verified; five-minute background relock not timed |
| ST-05 build gate | Pass | `check<Variant>Permissions` fails the build when INTERNET is added (tried and reverted) |
| ST-10 | Pass | Wrong password rejected by a standard PDF library |
| ST-13 | Partial | gitleaks clean on every commit; OSV-Scanner over `app/gradle.lockfile`: no known vulnerability in anything shipped (BouncyCastle raised from 1.72 to 1.86); six remain in Android Lint's own tooling, which is not shipped; the shipped set alone scans clean; SBOM produced |
| Others | Not run | Planned for the phase 1 security self-check |

## 5. Accessibility

| ID | Result | Evidence |
| --- | --- | --- |
| AT-01 | Partial | Contrast computed for the main pairs; all at or above 4.5:1 after darkening the Yes green; Accessibility Scanner not run |
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
| SEC-54 | Met | Content verified against manifest hashes; LF hashing fixed |
| SEC-55, SEC-63, SEC-64 | Met | minSdk 29, target 37; rooted-phone warning; build expiry |
| SEC-62 | Open | Release signing key and custodians are a department decision |

## 8. Open items before the phase 1 exit

- Reviewer sign-off for every gesture and both characters (05 §8).
- TC-05, PT-01, PT-02 and PT-03 on a real Android 10 phone with a GPU.
- Spoken TalkBack pass and Accessibility Scanner on a phone with TalkBack, and the formal MASVS review (`/masvs-checklist`).
- Partner hand model through `HandTrackers` in the release source set.
