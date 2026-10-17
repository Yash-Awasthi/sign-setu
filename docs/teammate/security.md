# Security

Sources: `docs/04-security.md` (controls), `docs/governance/07-threat-model.md` (STRIDE), `docs/governance/11-devsecops-release.md` (pipeline), `docs/governance/08-privacy-dpia.md`.

## 1. Threat summary

| Threat | IDs | Controls |
| --- | --- | --- |
| Lost, stolen or seized phone | T-01 | SEC-01, SEC-08, SEC-20 to SEC-22 |
| Backup or device transfer | T-02 | SEC-03 |
| Screenshot, recording, recents | T-03 | SEC-40 |
| Personal data in logs | T-04 | SEC-04, SEC-70 |
| Export to the wrong person | T-05 | SEC-12, SOP |
| Network threats | T-07 to T-12 | Out of scope in release 1: no INTERNET (SEC-30) |
| Tampered content bundle | T-13 | SEC-54, SEC-74 |
| Compromised dependency or build | T-14 | SEC-50 to SEC-52 |
| Signing key theft | T-15 | SEC-62 |
| Camera exposure | T-16 | SEC-47, SEC-40 |
| Exported components, overlays | T-17, T-18 | SEC-41, SEC-45 |
| Rooted phone | T-19 | SEC-63 |
| Subject leaves the app on the stage | T-21, T-43, T-47 | SEC-46, SEC-75 |
| Stale build | T-22 | SEC-64 |
| Landmarks kept or leaked | T-37 | SEC-71 |
| Rogue, relayed or wrong second screen | T-38 to T-41 | SEC-66 to SEC-68 |
| Lost stage device | T-42 | SEC-69 |
| Flavor scope creep | T-45 | SEC-73 |
| Library telemetry | T-46 | SEC-72, SEC-30 |
| Misread sign taken as fact | T-30 | Graded readings, subject pick, SOP |

## 2. Controls by area

| Area | IDs | Summary |
| --- | --- | --- |
| Storage | SEC-01 to SEC-08 | SQLCipher database; drawings in the database only; backup off; no logs in release (R8 strips `Log`); no clipboard; purge with `secure_delete` |
| Cryptography | SEC-10 to SEC-12 | Platform and SQLCipher only; non-exportable Keystore keys; export PDF AES-256 with an 80-bit one-time passphrase |
| Authentication | SEC-20 to SEC-22 | Secure lock required; BiometricPrompt (STRONG or DEVICE_CREDENTIAL on API 30+, WEAK on API 29); relock after 5 min; auth-bound KEK |
| Network | SEC-30 | No INTERNET; build gate |
| Platform | SEC-40 to SEC-47 | FLAG_SECURE; only the launcher exported; no WebView; `filterTouchesWhenObscured` on every window; screen pinning; Preview and ImageAnalysis only |
| Supply chain | SEC-50 to SEC-55 | Verification metadata and lockfiles; CycloneDX SBOM; Lint, Semgrep, OSV, gitleaks; manifest-hashed content; minSdk 29 |
| Resilience | SEC-60 to SEC-65 | R8 full mode; offline signing key, scheme v3; root warning; 180-day build expiry (`BUILD_EPOCH_DAY`); assistant model SHA-256 allow-list |
| Second screen | SEC-66 to SEC-70 | Bonded RFCOMM only; ECDH pairing; memory-only resume secret; nothing stored on the stage; scrubbed logs |
| Camera and flavors | SEC-71 to SEC-75 | Landmarks in memory only; telemetry stub; flavor rules; model verification; `stage_live` flag forces the full credential |

## 3. Key management

| Key | Algorithm and location | Code | Lifetime |
| --- | --- | --- | --- |
| DEK | Random 256-bit SQLCipher key, stored wrapped by the KEK | `app/src/main/java/app/signsetu/data/session/DatabaseKey.kt` | Until Clear all data |
| KEK | AES key in Android Keystore, non-exportable, hardware-backed where available, auth-bound (`setUserAuthenticationParameters` on API 30+, `setUserAuthenticationValidityDurationSeconds` on API 29); not invalidated by enrolling a fingerprint | `DatabaseKey.kt` | Until reinstall or lock removal; on loss the database is deleted, keys recreated and the officer told once |
| Silent unlock | Keystore key tried within its 30 s window after phone unlock (D-23) | | Disabled after a live stage (SEC-75) |
| Export passphrase | 16 characters, 80 bits, AES-256 PDF | `app/src/main/java/app/signsetu/export/SessionReport.kt` | Per export, shown once |
| Link direction keys | P-256 ECDH, HKDF-SHA256 with the transcript hash as salt | `app/src/main/java/app/signsetu/pair/PairCrypto.kt` | Per link and resume, memory only |
| Resume secret | HKDF output | `app/src/main/java/app/signsetu/pair/StageLink.kt` | 120 s reconnect window; zeroed on Bye, expiry, lock, end |
| Release signing key | Offline, two custodians, backup in department custody, APK Signature Scheme v3 | Not in the repository | Rotated on compromise |

## 4. Pairing protocol (SEC-67)

| Step | Detail |
| --- | --- |
| Transport | Secure RFCOMM to bonded devices; SDP name "Display", app-specific UUID; never scans, advertises or bonds |
| Key agreement | P-256 ECDH with commit then reveal; peer keys checked to lie on the curve |
| Derivation | HKDF-SHA256 to two direction keys, 6-digit code, 4-digit check number, resume key |
| Frames | AES-256-GCM; IV = direction byte, zeros, 64-bit counter; AAD = version and counter; strict next counter, so replay, reorder or skip closes the link |
| Limits | Plain handshake 4 KiB, encrypted 1 MiB, checked before allocation; connect 12 s plus one retry, handshake 15 s, code 90 s |
| Verification | Code shown on the stage, typed on the officer phone, compared in constant time, 3 misses close; matching check numbers before Match; version code and content manifest hash must match |
| Tests | `app/src/test/java/app/signsetu/PairTest.kt` |

## 5. Permissions per flavor

Gate: `check<Variant>Permissions` in `app/build.gradle.kts`, run before every `assemble`, `bundle`, `install` and `package` task of the variant.

| Permission | standard | nearby |
| --- | --- | --- |
| CAMERA (runtime, at session start) | yes | yes |
| BLUETOOTH_CONNECT (runtime, only when a second screen is set up) | yes | yes |
| VIBRATE (normal) | yes | yes |
| INTERNET, RECORD_AUDIO, ACCESS_BACKGROUND_LOCATION | build fails | build fails |
| ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, BLUETOOTH_SCAN, BLUETOOTH_ADMIN, BLUETOOTH_ADVERTISE, NEARBY_WIFI_DEVICES | build fails | allowed (Nearby Connections) |
| Play services | none | `play-services-nearby` |
| Application ID | `in.signsetu` | `in.signsetu.nearby` |

Manifests: `app/src/main/AndroidManifest.xml`, `app/src/nearby/AndroidManifest.xml`, `app/src/debug/AndroidManifest.xml`.

## 6. Telemetry stub (SEC-72)

| Item | Detail |
| --- | --- |
| Excluded | `com.google.android.datatransport:transport-backend-cct` from `mediapipe-tasks-vision` (`app/build.gradle.kts`) |
| Replacement | `app/src/main/java/com/google/android/datatransport/cct/CCTDestination.kt`, drops every event on the device |
| Backstop | No INTERNET in any variant (SEC-30) |

## 7. Rules for changes

| Change | Required |
| --- | --- |
| New dependency | Reason recorded; lockfile and verification metadata regenerated; OSV clean; licence in `docs/09-open-source.md` |
| New permission | Gate lists, `docs/04-security.md` and the DPIA updated |
| New stored field | Inside the SQLCipher database only; covered by purge |
| New log line | No subject data; pairing code logs exception class names only |
| New window | `filterTouchesWhenObscured` set |
| New model or content file | Added to the manifest and verified before load |
