# Operations: build, release and content

## 1. Variants

| Dimension | Values |
| --- | --- |
| Flavor (`transport`) | `standard` (default, reference build, Bluetooth); `nearby` (Play services, `.nearby` application ID suffix, `-nearby` version name suffix) |
| Build type | `debug` (adds `GalleryActivity` and the demo tracker; arm64-v8a and x86_64); `release` (arm64-v8a and armeabi-v7a, R8 full mode, resource shrinking, `app/proguard-rules.pro`) |
| SDK | compileSdk 37, targetSdk 37, minSdk 29, Java 17 |
| Native | NDK 30.0.16248370, CMake 4.1.1, `app/src/main/cpp/CMakeLists.txt` |

## 2. Release build and signing

| Step | Command or rule |
| --- | --- |
| AAB | `./gradlew bundleStandardRelease` |
| APK | `./gradlew assembleStandardRelease` |
| Permission gate | `checkStandardReleasePermissions` runs before packaging |
| Signing | No signing config in `app/build.gradle.kts`; the release manager signs with the offline key (two custodians, department backup), APK Signature Scheme v3 (SEC-62). Signing credentials never reach pull-request builds |
| SBOM | `python tools/security/sbom.py <path to syft>` (CycloneDX, SEC-51) |
| Release record | SHA-256 of AAB and APKs, SBOM, test report, scan results (`docs/governance/11-devsecops-release.md` section 6) |
| Language splits | Disabled (`bundle { language { enableSplit = false } }`); every language ships |

## 3. Versioning

| Field | Location | Rule |
| --- | --- | --- |
| `versionCode` | `app/build.gradle.kts`, `defaultConfig` | Increment every release; must match on both paired devices |
| `versionName` | Same | `MAJOR.MINOR.PATCH` |
| `BUILD_EPOCH_DAY` | BuildConfig, set at build time | New sessions refused 180 days after the build (SEC-64); rebuild to renew |
| Content hash | `app/src/main/assets/content/manifest.json` | Must match on both paired devices |

## 4. Content pipeline

| Asset | Generator | Input |
| --- | --- | --- |
| Picture cards `assets/content/cards/*.webp` | `python tools/content/build_content.py [--only a,b]` | `content/symbols/map.json`, SVG symbols, `cards.json`; 320 px WebP quality 88 (resvg-py, Pillow) |
| Audio `assets/content/audio/` | `tools/content/build_audio.py` (GPU environment) | Phrase JSON |
| Signs `signs.json` | `tools/content/build_signs.py` | Authored signs |
| Recorded ISL `isl_signs.json` | `tools/isl/retarget.py` | INCLUDE pose release |
| Sign reader `models/sign_reader.{bin,json}` | `tools/ml/export.py` | `sign_reader.pt` |
| Review record | `tools/content/review_record.py` | Content JSON; output `docs/governance/forms/content-review-record.md` |
| Manifest | `python tools/content/build_content.py --manifest-only` | SHA-256 of every file under `assets/content` |

Rule: after any change under `app/src/main/assets/content`, regenerate the manifest. The app verifies hashes before use (SEC-54, SEC-74): a mismatched model is skipped, a mismatched text file stops new sessions.

## 5. ISL retarget regeneration

```
C:/Users/Yash/dev/venvs/signsetu-ml/Scripts/python tools/isl/retarget.py
C:/Users/Yash/dev/venvs/signsetu-ml/Scripts/python tools/isl/test_retarget.py
python tools/content/build_content.py --manifest-only
./gradlew testStandardDebugUnitTest --tests app.signsetu.IslLexiconTest
```

Data default: `C:/Users/Yash/dev/ml-data/include_pose`. Every regenerated sign is a draft until Deaf review; regenerate and re-sign the content review record.

## 6. Lockfiles and verification metadata (SEC-50)

| File | Purpose |
| --- | --- |
| `app/gradle.lockfile` | Resolved versions for all configurations (`dependencyLocking { lockAllConfigurations() }`) |
| `settings-gradle.lockfile` | Settings and plugin classpath |
| `gradle/verification-metadata.xml` | SHA-256 of every artifact |
| `gradle/libs.versions.toml` | Version catalog |
| `portal/package-lock.json` | Portal npm lock |

After a deliberate dependency change:

```
./gradlew --write-verification-metadata sha256 --write-locks testStandardDebugUnitTest assembleStandardRelease assembleNearbyRelease
osv-scanner scan source --lockfile app/gradle.lockfile
```

Review the diff of all three Gradle files and update `docs/09-open-source.md`. Pinned overrides: BouncyCastle 1.86 constraint (PdfBox-Android brings 1.72); full protobuf 4.26.1 in place of javalite.

## 7. Portal (`portal/`: review portal, Vite+ with React 19, TypeScript 7)

| Task | Command |
| --- | --- |
| Install | `cd portal && npm ci` |
| Dev server | `npx vp dev` |
| Check (types, lint) | `npx vp check` |
| Tests | `npx vp test run` |
| Build | `npx vp build` (output `portal/dist`) |
| Preview | `npx vp preview` |

The portal reads app content (`portal/src/content.ts`) and shares rig math with the app through `portal/src/rig-fixtures.json`, checked by `RigFixtureTest` and `rig.test.ts`.
