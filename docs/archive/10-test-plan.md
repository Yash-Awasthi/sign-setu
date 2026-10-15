# 10. Test Plan

## 1. Approach

| Level | What | Tools | When |
| --- | --- | --- | --- |
| Unit | Adapters, keypoint parsing, session logic, retention, crypto wrappers | JUnit, kotlinx-coroutines-test | Every commit |
| UI | Main flows on emulator | Compose UI test | Every pull request |
| Device | Real phones, release build | Manual scripts below, Macrobenchmark | Each phase exit |
| Integration | App against fakes, then staging gateway and partners | Fakes, staging | Phase 1 days 12-13, then each release |
| Security | MASVS MAS-L2 checks, SAST, dependency scan, DAST on gateway | Lint, Semgrep or MobSF, OSV-Scanner, OWASP ZAP | Every PR (static); phase 2 and 3 (full) |
| Accessibility | GIGW 3.0 / IS 17802 checks | Accessibility Scanner, TalkBack | Phase 1 day 14, phase 2 |
| Usability | People new to the app, ISL users | Observation scripts | Phase 2, pilot |
| Acceptance (UAT) | Department officers run scripted scenarios | UAT scripts | End of phase 2 |

Test-first for logic with branches (retention, adapters, confidence mapping, export encryption): write the failing test, then the code.

## 2. Functional test scenarios

| ID | Scenario | Requirements |
| --- | --- | --- |
| TC-01 | Fresh install, first run, choose Hindi, grant no permissions; triage flow works | FR-50, FR-40, FR-42 |
| TC-02 | Flip, focus, divider; rotate phone; return | FR-02 to FR-04 |
| TC-03 | Airplane mode: every triage question shows clip, caption, cards within 300 ms | FR-11, NFR-02 |
| TC-04 | Sensitive questions require second tap | FR-12 |
| TC-05 | Free text with network: caption first, avatar plays | FR-16 |
| TC-06 | Free text with unknown word: "sign not available" | FR-17 |
| TC-07 | Voice in Hindi: transcript editable, nothing shown until confirm | FR-14, FR-15 |
| TC-08 | Card answer appears for officer in officer language | FR-21 |
| TC-09 | Sign recognition: correct sign, wrong sign, no hands, low light | FR-22, FR-23 |
| TC-10 | Drawing saved to notes | FR-24 |
| TC-11 | Kill app mid-session; reopen; session restored | FR-34 |
| TC-12 | End session with export; open export with passphrase on another device | FR-06, FR-32 |
| TC-13 | Auto-delete: set clock forward past period; session gone at next open | FR-33 |

## 3. Performance tests

Run on the oldest test phone, release build.

| ID | Measure | Target |
| --- | --- | --- |
| PT-01 | Cold start (Macrobenchmark `StartupTimingMetric`) | < 2 s (NFR-01) |
| PT-02 | Avatar frame time over 10 s clip (`FrameTimingMetric`) | p95 < 41 ms, i.e. 24 fps (NFR-03) |
| PT-03 | Recognition analyser rate | >= 10 fps (NFR-04) |
| PT-04 | Memory with camera on for 5 minutes | < 300 MB (NFR-06) |
| PT-05 | Release APK size (arm64) | < 30 MB (NFR-05) |

## 4. Resilience drills

| ID | Drill | Expected |
| --- | --- | --- |
| RD-01 | Network off mid-request | Fallback message within 8 s; alternatives offered |
| RD-02 | Gateway returns 500, then malformed JSON, then 30 s delay | No crash; fallback; cancel available after 5 s |
| RD-03 | Revoke camera permission while recognising | Camera stops; explanation shown |
| RD-04 | Low battery saver, low storage | App keeps working; warns on export if storage low |
| RD-05 | Incoming call during session | Session preserved |

## 5. Security tests

Mapped to [security controls](06-security-architecture.md) and the MASTG.

| ID | Test | Controls |
| --- | --- | --- |
| ST-01 | Pull app data with `adb backup` and device-transfer; confirm nothing included | SEC-03 |
| ST-02 | Inspect `/data/data/<pkg>` on a test rooted device; database unreadable without key | SEC-01 |
| ST-03 | Screenshots and screen recording blocked; recents thumbnail blank | SEC-40 |
| ST-04 | Logcat during full session contains no personal data | SEC-04 |
| ST-05 | Proxy with user CA installed: requests fail; pin mismatch fails | SEC-30, SEC-31 |
| ST-06 | Repackaged APK and emulator: attestation fails, gateway rejects requests | SEC-61 |
| ST-07 | `adb shell dumpsys package`: only launcher activity exported | SEC-41 |
| ST-08 | Decompile release APK: no keys, URLs other than gateway, test code or debug flags; obfuscated | SEC-11, SEC-53, SEC-60 |
| ST-09 | App lock after 5 minutes in background; key unusable when locked | SEC-21, SEC-22 |
| ST-10 | Export file unreadable without passphrase; tampered file rejected | SEC-12 |
| ST-11 | Fuzz gateway endpoints (size, type, injection); DAST scan | SEC-33 |
| ST-12 | Malicious partner response (huge arrays, NaN, HTML) | SEC-54 |
| ST-13 | SBOM present and complete; no known critical vulnerabilities | SEC-51, SEC-52 |

## 6. Accessibility tests

| ID | Test | Requirement |
| --- | --- | --- |
| AT-01 | Accessibility Scanner on every screen: no target or contrast failures | NFR-20, NFR-21 |
| AT-02 | Font scale 200%: officer panel usable | NFR-22 |
| AT-03 | TalkBack: complete triage flow as officer | NFR-23 |
| AT-04 | Every supported script renders without clipping | NFR-25 |
| AT-05 | Grey-scale screen: Yes/No still distinguishable | NFR-21 |

## 7. Usability and content validation

- Five or more people new to the app run scripted scenarios after a 15-minute briefing; record hesitation points and time to first question.
- At least one fluent ISL user rates each triage clip and a sample of avatar sentences for understanding.
- A child-welfare or counselling professional reviews the full subject-facing content set.
- Any test with children (usability, gestures, avatar characters) needs approval from an institutional ethics committee, written consent from a parent or guardian, the child's own assent, a child-welfare professional present, and the right to stop at any time. Sessions take place in the child's school or another familiar place. Rescued or victimised children are never test participants. No video of child participants is kept after scoring.

## 8. Device matrix

| Device | Purpose |
| --- | --- |
| Oldest supported model (Android 10, 3 GB RAM) | Performance floor |
| Common mid-range model used by the department | Typical case |
| Recent phone (Android 15/16) | New platform behaviour |
| Emulator API 29 and latest | Automated tests |

## 9. Exit criteria per release

- All MUST requirements pass on the device matrix.
- No open critical or high defects; security tests ST-01 to ST-13 pass.
- Performance targets met on the oldest phone.
- Test report stored with the release record.
