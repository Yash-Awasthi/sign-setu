# 06. Test Plan

## 1. Approach

| Level | What | Tools | When |
| --- | --- | --- | --- |
| Unit | Session logic, retention, content resolution, lexicon search, passphrase, build expiry, PDF encryption | JUnit, kotlinx-coroutines-test | Every change |
| UI | Officer and Show flows | Compose UI tests on the API 29 emulator | Every change |
| Device | Release build on real phones | Scripts below, Macrobenchmark | End of each phase |
| Security | MASVS L2 checks, static analysis, dependency and secret scans | Lint, Semgrep, OSV-Scanner, gitleaks, MobSF static | Every change (static); phase 2 (full) |
| Accessibility | IS 17802 / WCAG 2.1 AA on the officer panel | Accessibility Scanner, TalkBack | Phase 1 end, phase 2 |
| Content | Translations, clips, gestures, audio | Reviewers per the content policy | Phase 2 |
| Acceptance | Department testers | [governance/17-uat-scripts.md](governance/17-uat-scripts.md) | Phase 2 end |

Logic with branches (retention, show-back, build expiry, export) is written test-first: the failing test, then the code.

## 2. Functional scenarios

| ID | Scenario | Requirements |
| --- | --- | --- |
| TC-01 | Fresh install: first run, no device lock set; app refuses to store sessions until a lock exists | FR-50, SEC-20 |
| TC-02 | Choose each profile; Show state content matches §3 of the UX document | FR-60, FR-11 |
| TC-03 | Show and Officer states; rotate 180 degrees; return requires biometric or credential | FR-02, FR-03, FR-61 |
| TC-04 | App pinning on: subject cannot leave the app; pinning ends with the session | FR-61, SEC-46 |
| TC-05 | Every triage question shows within 300 ms with its cards, caption and profile content | FR-11, NFR-02 |
| TC-06 | Sensitive questions need a second deliberate tap | FR-12 |
| TC-07 | Free text with board cards: caption and cards shown; no sign or gesture played | FR-13 |
| TC-08 | Missing clip or audio: picture and caption only; officer sees "not available" | FR-17 |
| TC-09 | Phrase bank sentence with slot plays as one sequence | FR-62 |
| TC-10 | Language finder: each tile plays its greeting; choice sets subject language | FR-63 |
| TC-11 | Gesture lookup returns the expected entries for scripted location and action pairs | FR-65 |
| TC-12 | Show-back: Yes logs the meaning; No and no answer log "unclear" | FR-66 |
| TC-13 | Drawing saved to notes and present in the log | FR-24 |
| TC-14 | Kill the app mid-session; reopen; session restored exactly | FR-34 |
| TC-15 | Export: passphrase shown once; PDF opens in a standard reader only with it | FR-32, SEC-12 |
| TC-16 | Retention: move the clock past the period; session purged at next open and by the worker | FR-33, SEC-08 |
| TC-17 | Interpreter: contact configured opens the messaging app; none configured hides the button | FR-64 |
| TC-18 | Build expiry: clock past 180 days from build; new session refused, export of existing allowed | FR-67, SEC-64 |
| TC-19 | Camera mirror: permission asked of the officer at start; denied or off shows a placeholder; no files appear in app storage after a session | FR-69, SEC-47 |
| TC-20 | "I don't know" and "Stop" are logged; Stop shows the officer's banner until acknowledged or a new item is shown | FR-70 |
| TC-21 | Every gesture plays on both characters; the gesture file passes the rig checks (unit test) and reviewers sign off in the gallery | FR-11, D-21 |

## 3. Performance

Release build on the API 29 emulator with 3 GB RAM, then on a real Android 10 phone.

| ID | Measure | Target |
| --- | --- | --- |
| PT-01 | Cold start (Macrobenchmark `StartupTimingMetric`) | Under 2 s (NFR-01) |
| PT-02 | Confirm to Show state with clip playing | Under 300 ms (NFR-02) |
| PT-03 | Clip playback frame timing (`FrameTimingMetric`) | No janky frames at source rate (NFR-03) |
| PT-04 | Release APK size | Under 30 MB (NFR-05) |
| PT-05 | Memory over a 30-question session | Under 300 MB (NFR-06) |

## 4. Resilience

| ID | Drill | Expected |
| --- | --- | --- |
| RD-01 | Incoming call during Show state | Session preserved; pinning restored on return |
| RD-02 | Low storage during export | Clear message; session intact |
| RD-03 | Battery saver, dark mode, 200% font | App fully usable |
| RD-04 | Device lock removed while sessions exist | Sessions unreadable; app explains and offers Clear |
| RD-05 | Corrupted content file | Hash check fails; picture and caption fallback; officer told |

## 5. Security

| ID | Test | Controls |
| --- | --- | --- |
| ST-01 | `adb backup` and device-transfer extraction contain no app data | SEC-03 |
| ST-02 | Database file on a rooted test device is unreadable without the key | SEC-01 |
| ST-03 | Screenshot and recording blocked; recents thumbnail blank | SEC-40 |
| ST-04 | Logcat across a full session contains no personal data | SEC-04 |
| ST-05 | Merged manifest: no INTERNET, RECORD_AUDIO or LOCATION permission; CAMERA present only for the mirror | SEC-30, SEC-42 |
| ST-07 | `dumpsys package`: only the launcher activity exported | SEC-41 |
| ST-08 | Decompiled release APK: no keys, test code or debug flags; obfuscated | SEC-11, SEC-53, SEC-60 |
| ST-09 | Lock after 5 minutes in background; key unusable while locked; run on API 29 and API 30+ | SEC-21, SEC-22 |
| ST-10 | Export unreadable without passphrase; tampered file rejected by readers | SEC-12 |
| ST-12 | Tampered content bundle rejected | SEC-54 |
| ST-13 | SBOM complete; no known critical vulnerabilities; gitleaks clean | SEC-50 to SEC-52 |
| ST-14 | Overlay app over Show, export and clear actions: touches filtered | SEC-45 |

ST-06 and ST-11 (attestation, gateway fuzzing) apply from release 1.1.

## 6. Accessibility

| ID | Test | Requirement |
| --- | --- | --- |
| AT-01 | Accessibility Scanner on every screen: no target or contrast failures | NFR-20, NFR-21 |
| AT-02 | 200% font scale: officer panel usable | NFR-22 |
| AT-03 | TalkBack: complete triage flow as officer | NFR-23 |
| AT-04 | Every supported script renders without clipping | NFR-25 |
| AT-05 | Greyscale: Yes and No still distinguishable | NFR-21 |

## 7. Content validation and testing with people

- Content review and comprehension thresholds are in [avatar and gestures](05-avatar-and-gestures.md) §8.
- At least five people new to the app run scripted scenarios after a short briefing; record hesitation points.
- Any test with children needs approval from an institutional ethics committee, written consent from a parent or guardian, the child's own assent, a child-welfare professional present, and the right to stop at any time. Sessions take place in a familiar setting such as the child's school. Rescued or victimised children are never test participants. No video of child participants is kept after scoring.

## 8. Devices

| Device | Purpose |
| --- | --- |
| Emulator, API 29, 3 GB RAM | Performance floor and automated tests |
| Real Android 10 phone, 3 GB RAM | Final performance and pinning behaviour |
| Recent phone | Current platform behaviour |
| Emulator, latest API | Automated tests |

## 9. Exit criteria per release

- All MUST requirements pass on the device list.
- No open critical or high defects; ST-01 to ST-14 (release 1 set) pass.
- Performance targets met on the oldest device.
- Test report stored with the release record.
