# Release policy

A release is a version an officer can install over the one already on the phone, with the sessions that build stored still readable. Three things make that true: the version number says what changed, the changelog says what an officer must do before updating, and the upgrade path is tested rather than assumed. This file is the rule for all three.

## 1. Version discipline

| Field | Place | Rule |
| --- | --- | --- |
| `versionName` | `android.defaultConfig` in `app/build.gradle.kts` | `MAJOR.MINOR.PATCH`, one value for the whole app |
| `versionCode` | the same block | A plain counter that only ever goes up; every APK that ships gets a new one, including a re-cut of the same `versionName` |

The bump is decided by what an installed phone can still do after the update, not by the size of the diff.

| Bump | When |
| --- | --- |
| **MAJOR** | The new build cannot read sessions written by the old one, or a report, key or database format breaks in a way an export cannot repair |
| **MINOR** | A new capability that keeps the same database schema and the same report format, so an old session opens unchanged |
| **PATCH** | Fixes only, with no new capability and no schema change |

The build file is the only place the version is written. `tools/docs/check_version.py` regenerates the README version line and the docs version row from it, and fails when either drifts. The `version` job in `.github/workflows/ci.yml` reads `versionName`, reports whether the tag `v<versionName>` already exists, and triggers the instrumented suite for a new version. The `release` job publishes `v<versionName>` from a green push to `master` in the private repository: a new version gets a new release, and the same version has its assets replaced. The `tools` job runs the version check and the documentation checks on every push.

No document, ticket or release note states a version from memory. If a number is needed, it is read from the build file or generated from it.

## 2. A changelog per release

`CHANGELOG.md` at the repository root carries one section per released version, newest first, in the form `## [MAJOR.MINOR.PATCH] - YYYY-MM-DD`. The newest section is the version in the build file; `tools/docs/check_release.py` fails when it is not, when a section is empty, or when the sections are out of order. A version bump and its changelog section are one change.

Each section answers two questions for an officer:

1. What changed that I will notice?
2. Does a session already on the phone survive the update, or must I export it first?

The `release` job writes the release page notes from the commit subjects since the previous tag and attaches them, so nothing merged is missing from the public record. The changelog is the curated copy of that record, and the release page is generated from the same commits. The one-line entries for releases before 0.8.52 are the tag annotations, which is what those releases published.

## 3. Supported-device matrix

The build is 64-bit only: `arm64-v8a` for the phone APK, and `arm64-v8a` plus `x86_64` for the universal APK (the `abi` handling in `app/build.gradle.kts`). One `app` module, two flavours, `standard` and `nearby`; the second screen is Bluetooth or Nearby Connections, never the network.

| API | Android release | Position | Automated evidence |
| --- | --- | --- | --- |
| 29 | 10 | Minimum (`minSdk`) | Installs; no per-API CI run |
| 30 | 11 | Supported | No per-API CI run |
| 31 | 12 | Supported | No per-API CI run |
| 32 | 12L | Supported | No per-API CI run |
| 33 | 13 | Supported | No per-API CI run |
| 34 | 14 | Supported and emulator-tested | The `instrumented` job in `.github/workflows/ci.yml` runs the listed classes on an API 34 `x86_64` emulator, in four shards |
| 35 | 15 | Supported | No per-API CI run |
| 36 | 16 | Supported | No per-API CI run |
| 37 | 17 | Target (`targetSdk`) and `compileSdk` | Built against; no per-API CI run |

"Supported" means the build installs and the app holds to the platform contracts it uses; it does not mean every API in that range was run in CI. The only automated per-device evidence is the API 34 emulator. The hardware baseline and its memory floors are in [06-hardware-baseline-protocol.md](docs/06-hardware-baseline-protocol.md); the camera, frame-time and second-screen classes need a physical phone and are named as device-only there and in the `instrumented` job's class list.

A new `minSdk` or `targetSdk` changes this table in the same change. `tools/docs/check_release.py` fails when a row for the build's `minSdk` or `targetSdk`, or any API between them, is missing.

## 4. Upgrade path that keeps sessions readable

### 4.1 Forward: the normal case

Sessions live in the encrypted Room database `session.db` (SQLCipher). The schema is at version 9, and `SessionDatabase` carries every migration from 1 to 9; each step adds a column or a table and keeps every row. `SessionMigrationTest` opens a database at the old version and walks the same migration list on the emulator. A schema change ships with its migration in the same change, and the database version is bumped only when that migration exists.

The database key does not change on an update: the DEK stays in `noBackupFilesDir/dek.bin` and the KEKs stay in the Android Keystore (section 4 of [04-security.md](docs/04-security.md)). Uninstalling, clearing all data or losing the Keystore drops the keys and with them the sessions; installing a newer APK over the old one does not.

Retention bounds the problem. A session is deleted after 4 to 72 hours of inactivity, 24 hours by default, so most sessions are gone before the next release. If a session must survive an update, export it first: the report is a PDF file outside the app and is unaffected by an update.

The 180-day rule is the other deadline. A build stops starting new sessions 180 days after its build day and shows "Update required"; sessions already stored can still be exported and cleared. An officer therefore has to update before that day, not merely choose to.

### 4.2 Downgrade: the case that loses sessions

An older build installed over a newer one drops every stored session. `SessionDatabase.open` uses `fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)` and tells the officer, because failing every unlock would be worse. That is the whole policy: do not downgrade a phone in the field.

When a release is bad, the fix is a newer build with a higher `versionCode`, not the previous APK. A previous APK is kept only for a phone that never installed the bad build. This is also why a rollback instruction in an incident note must say that stored sessions are lost, and why a session that matters is exported before any downgrade is attempted.

Reports survive either way. An exported report is one PDF with no password since 0.8.52; an older one may still ask for the passphrase shown when it was made, and `tools/security/verify_seal.py --passphrase` opens it. The seal still shows whether the file was altered.

### 4.3 Release checklist

1. Bump `versionName` and `versionCode` in `app/build.gradle.kts`; add the `CHANGELOG.md` section with the "does a stored session survive" answer.
2. Run `python tools/docs/check_version.py`, `python tools/docs/check_docs.py` and `python tools/docs/check_release.py`; run the `tools/docs` unit tests. Run the Gradle unit tests, lint and the instrumented suite when the schema, the export or the camera path changed.
3. Tag `v<versionName>` through the release path; the `release` job signs both APKs, writes `SHA256SUMS`, the SBOM and `PROVENANCE.json`, and publishes the notes. Check the assets and that `sha256sum -c SHA256SUMS` passes on a fresh download.
4. Tell officers whether an update keeps their sessions (it does), that a downgrade does not, and the build's 180-day deadline.

## 5. Release evidence

Every release has three files beside its APKs, written by `tools/security/release_manifest.py` and published with them:

| File | What it holds |
| --- | --- |
| `SHA256SUMS` | one `<sha256>  <name>` line per APK and for the SBOM, so `sha256sum -c SHA256SUMS` runs beside the download |
| `sign-setu-release.cdx.json` | a CycloneDX 1.5 SBOM of the release runtime classpath, from the resolved graph in `app/gradle.lockfile` (Syft reads the resolved files where it is installed) |
| `PROVENANCE.json` | the commit, version and build task; the Gradle distribution and its pinned SHA-256; each artefact's path, size, SHA-256 and signing status; the SHA-256 of `gradle/verification-metadata.xml`; the SHA-256, line count and shipped-component count of both lockfiles; the SBOM producer and component count |

The signing status is read from the APK's bytes, never taken from a label: an APK with no APK Signing Block and no `META-INF` certificate is `unsigned` and `shippable: false`. A release APK built without the release keystore, as on a machine that holds no key, is exactly that: unsigned, not installable as a release, and not to be described as shippable. It becomes shippable only after `zipalign` and `apksigner sign` with the release key, and the evidence then has to be cut again from the signed file.

The evidence committed under `release/<version>/` is the record of a build at a commit; `python tools/security/release_manifest.py release/<version> --check` recomputes every hash and exits 1 on one changed byte, on an APK or SBOM present but missing from `SHA256SUMS`, on a recorded signing status that the bytes contradict, and on a `verification-metadata.xml` digest that no longer matches the file. The `tools` job runs it, the `release` job runs the same check on `out/` before publishing, and a locally built pair of APKs is recorded with the same command.