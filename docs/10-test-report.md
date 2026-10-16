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
| TC-05 | Not measured | Needs Macrobenchmark (PT-02) |
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
| TC-16 | Partial | Retention decision unit-tested at its boundary (PolicyTest); worker not exercised by moving the device clock |
| TC-17 | Pass | WhatsApp contact opens the `wa.me` link; with no contact the button is hidden |
| TC-18 | Partial | Expiry decision unit-tested at 180 and 181 days (PolicyTest); device clock not moved |
| TC-19 | Pass | Permission asked of the officer at Start; mirror shown; with a stand-in hand model the 21-point overlay is drawn over the live preview |
| TC-20 | Pass | "Stop" logged and shown to the officer as a banner with guidance until acknowledged |
| TC-21 | Pass (pending review) | Rig unit tests pass for every gesture on both characters; reviewer sign-off in the gallery is outstanding |

## 3. Performance

| ID | Result |
| --- | --- |
| PT-01 to PT-03, PT-05 | Not measured (Macrobenchmark and a real Android 10 phone needed) |
| PT-04 | Pass: 16.96 MB, under 30 MB |

## 4. Security

| ID | Result | Evidence |
| --- | --- | --- |
| ST-03 | Pass | Screen capture refused on the release build |
| ST-05 | Pass | Merged manifest: CAMERA only among dangerous permissions; no INTERNET, RECORD_AUDIO or LOCATION |
| ST-09 | Partial | Relock on lock-reset verified; five-minute background relock not timed |
| ST-10 | Pass | Wrong password rejected by a standard PDF library |
| ST-13 | Partial | gitleaks clean on every commit; SBOM and OSV scan not yet run |
| Others | Not run | Planned for the phase 1 security self-check |

## 5. Accessibility

| ID | Result | Evidence |
| --- | --- | --- |
| AT-01 | Partial | Contrast computed for the main pairs; all at or above 4.5:1 after darkening the Yes green; Accessibility Scanner not run |
| AT-02 | Pass | At 200% font scale the officer panel switches to one scrolling column with full labels |
| AT-03 | Partial | Every control on the stage and the officer panel exposes a spoken label in the accessibility tree (UI Automator dump); a spoken TalkBack walk-through is outstanding |
| AT-04 | Pass | All 14 language names and Hindi interface text render without clipping |
| AT-05 | Pass by design | Yes and No differ by icon (tick, cross), word and position as well as colour |

## 6. Defects found and fixed during the run

| Defect | Fix |
| --- | --- |
| Start crashed when asking for the camera permission (old fragment library from biometric 1.1.0) | Current fragment library declared |
| Content hashes taken over Windows line endings would reject every JSON file on a fresh clone | Content tool normalises to LF before hashing |
| Officer panel unusable at 200% text | Large-text layout |
| Touches during Android's "Screen pinned" toast are dropped by the overlay filter (SEC-45) | Known platform interaction; the toast lasts about two seconds |

## 7. Open items before the phase 1 exit

- Reviewer sign-off for every gesture and both characters (05 §8).
- TC-05, PT-01 to PT-05, and the TC-16 and TC-18 clock runs, on a real Android 10 phone.
- TalkBack pass, Accessibility Scanner, SBOM and OSV scan, MASVS self-check.
- Partner hand model through `HandTrackers` in the release source set.
