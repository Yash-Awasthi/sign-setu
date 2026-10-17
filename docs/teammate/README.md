# Sign Setu engineering handbook

Offline Android app (Kotlin, Jetpack Compose) for communication between police officers and Deaf or hard-of-hearing people, plus a content review portal (`portal/`, Vite+ React).

## 1. Index

| File | Contents |
| --- | --- |
| [srs.md](srs.md) | Software requirements specification |
| [dfd.md](dfd.md) | Data flow diagrams |
| [architecture.md](architecture.md) | Modules, layers, runtime structure |
| [data-model.md](data-model.md) | Room and SQLCipher schema, content JSON |
| [api.md](api.md) | Internal interfaces and link protocol messages |
| [sequences.md](sequences.md) | Sequence diagrams for key flows |
| [state-machines.md](state-machines.md) | Session, stage and link states |
| [avatar-rig.md](avatar-rig.md) | 2D avatar rig and sign playback |
| [ml.md](ml.md) | Sign reader, MediaPipe Holistic, retarget pipeline |
| [security.md](security.md) | Threats, SEC controls, keys, permissions |
| [testing.md](testing.md) | Test inventory, commands, gaps |
| [ops.md](ops.md) | Release, versioning, content, lockfiles, portal build |

## 2. Quick start

| Task | Command |
| --- | --- |
| Prerequisites | JDK 21 (builds Java 17 bytecode), Android SDK 37, NDK 30.0.16248370, CMake 4.1.1; `local.properties` holds `sdk.dir` |
| Debug build | `./gradlew assembleStandardDebug` |
| Install | `./gradlew installStandardDebug` (the device needs a secure screen lock) |
| Unit tests | `./gradlew testStandardDebugUnitTest` |
| Instrumented tests | `./gradlew connectedStandardDebugAndroidTest` |
| Lint | `./gradlew lintStandardDebug` |
| Portal | `cd portal && npm ci && npx vp check && npx vp test run` |
| Content manifest | `python tools/content/build_content.py --manifest-only` |
| Demo tracker (debug) | `adb shell run-as in.signsetu touch files/demo_hands` |

## 3. Repository map

| Path | Contents |
| --- | --- |
| `app/src/main/java/app/signsetu/` | `MainActivity`, `SignSetuApp`, `Policy` |
| `.../assistant` | On-device assistant bridge; native code in `app/src/main/cpp` |
| `.../camera` | CameraX analysis, `HolisticTracker`, landmark mapping |
| `.../data` | Room and SQLCipher session store (`data/session`), content loading (`data/content`) |
| `.../export` | Encrypted PDF session report |
| `.../pair` | Second-screen link: `PairCrypto`, `StageLink`, `StageBridge` |
| `.../sign` | `LandmarkSignReader`, `SignCapture`, `SignChoices`, `SkeletonPlayer` |
| `.../ui` | Compose screens, stage, lock screen |
| `app/src/main/java/com/google/android/datatransport/cct/` | Telemetry stub (SEC-72) |
| `app/src/main/assets/content/` | Cards, audio, JSON lexicons, `isl_signs.json`, `models/`, `manifest.json` |
| `app/src/{debug,release,standard,nearby}/` | Source set overrides per build type and flavor |
| `app/src/test`, `app/src/androidTest` | JVM and instrumented tests |
| `app/schemas/` | Room schema exports |
| `content/symbols/` | SVG sources for picture cards |
| `portal/` | Content review portal |
| `tools/content/` | Card rendering, audio, signs, manifest, review record |
| `tools/isl/` | INCLUDE-to-rig retargeting |
| `tools/ml/` | Sign reader training and export |
| `tools/security/` | SBOM generation |
| `gradle/` | Version catalog, verification metadata |
| `docs/` | Requirements, architecture, UX, security, decisions (`07-decisions.md`, D-xx), test plan and report, governance |
