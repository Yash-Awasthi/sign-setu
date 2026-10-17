# Testing

Plans and results: `docs/06-test-plan.md`, `docs/10-test-report.md`.

## 1. Inventory

| Type | Location | Runner | Files |
| --- | --- | --- | --- |
| JVM unit | `app/src/test/java/app/signsetu/` | JUnit 4, kotlinx-coroutines-test; `unitTests.isReturnDefaultValues = true` | AutoPilotTest, AvatarTest, camera/AnalysisRateTest, camera/HolisticMappingTest, HandoverTest, InterviewTest, IslLexiconTest, LessonTest, LogicTest, MainPageTest, PairTest, PolicyTest, RigFixtureTest, RoleTest, SegmentTest, SignIngestionTest, SignReaderTest, StageLogicTest, SunModeTest |
| JVM fixture | `app/src/test/resources/sign_reader_fixture.json` | | Python-to-Kotlin logits parity, written by `tools/ml/export.py` |
| Instrumented | `app/src/androidTest/java/app/signsetu/` | AndroidJUnitRunner, Compose UI test, Accessibility Test Framework | OverlayTouchTest, SessionMigrationTest, StageAccessibilityTest |
| Portal | `portal/src/*.test.ts` | Vitest through vite-plus (`vp test`) | flags.test.ts, missing.test.ts, rig.test.ts (`rig-fixtures.json` shared with RigFixtureTest) |
| Python | `tools/isl/test_retarget.py`, `tools/content/test_review_record.py` | Plain `python` | Retarget geometry; content review record |

## 2. Commands

| Purpose | Command |
| --- | --- |
| JVM unit tests | `./gradlew testStandardDebugUnitTest` |
| One test class | `./gradlew testStandardDebugUnitTest --tests app.signsetu.SignReaderTest` |
| Instrumented (device or emulator, Android 14+) | `./gradlew connectedStandardDebugAndroidTest` |
| Lint | `./gradlew lintStandardDebug` |
| Nearby flavor | `./gradlew testNearbyDebugUnitTest lintNearbyDebug` |
| Permission gate only | `./gradlew checkStandardReleasePermissions checkNearbyReleasePermissions` |
| Portal | `cd portal && npx vp check && npx vp test run` |
| Python | `C:/Users/Yash/dev/venvs/signsetu-ml/Scripts/python tools/isl/test_retarget.py`; `python tools/content/test_review_record.py` |
| Dependency scan | `osv-scanner scan source --lockfile app/gradle.lockfile` |

## 3. Device test procedure

| Step | Action |
| --- | --- |
| 1 | Set a secure screen lock; the app refuses to store sessions without one (SEC-20) |
| 2 | `./gradlew installStandardDebug` (the permission gate runs first) |
| 3 | `./gradlew connectedStandardDebugAndroidTest` |
| 4 | Without a model or camera: `adb shell run-as in.signsetu touch files/demo_hands` swaps in the waving stand-in tracker (debug builds only) |
| 5 | Camera path: start a session, open a sign window, check overlay, capture and reader guesses; on a weak GPU confirm the CPU fallback keeps tracking |
| 6 | Second screen: bond two phones in system settings, pair, check code and check number, drop the link and resume within 120 s |
| 7 | Security spot checks from `docs/06-test-plan.md` section 5, for example ST-01 (`adb backup` holds no app data) and ST-17 (`run-as ls -R` on the stage shows only the settings store) |
| 8 | Accessibility: TalkBack pass over the stage states (AT-07) |
| 9 | Record results in `docs/10-test-report.md` against the device list in `docs/06-test-plan.md` section 8 |

## 4. Gaps

| Gap | Note |
| --- | --- |
| Few instrumented tests | Three classes; no automated end-to-end session, export or unlock flow |
| No camera or MediaPipe test on a device | `HolisticMappingTest` covers mapping only; the tracker is checked by hand |
| Reader accuracy in the field | Only the INCLUDE test split; no officer-phone or child-signer data |
| Pairing over real Bluetooth | `PairTest` covers crypto and framing on the JVM; the radio path is manual |
| Nearby flavor | No dedicated tests |
| Python tools | No runner wiring; scripts run by hand |
| Native assistant (`app/src/main/cpp`) | No unit tests |
| Security tests ST-01 to ST-17 | Manual, per release |
