# 29. Shipped APK security posture

An audit of the posture of the phone build at HEAD `402339f`, package `in.signsetu`, version 0.8.52 (version code 24), read from the tree and from the `standardRelease` merged manifest under `app/build/`. Every row names the manifest or code line it came from. The gate in `tools/security/check_android_posture.py` repeats the audit and fails on the five rules below.

Run it:

```
python tools/security/check_android_posture.py
python -m unittest discover -s tools/security
```

Exit 0 means: no dangerous permission without a call site, no exported component without a permission or a documented reason, no cleartext traffic, no backup without rules, and no hardcoded key. The merged manifest is reported when build output exists but is not gated, because a clean checkout has no build output.

## 1. Application flags

| Flag | Value | Evidence | Verdict |
| --- | --- | --- | --- |
| `android:debuggable` | not set; release defaults to false | `app/src/main/AndroidManifest.xml` application element, lines 42-53 | ok |
| `android:allowBackup` | `false` | `app/src/main/AndroidManifest.xml:44` | ok |
| `android:fullBackupContent` | `@xml/backup_rules`; every domain excluded | `app/src/main/AndroidManifest.xml:46`, `app/src/main/res/xml/backup_rules.xml:3-7` | ok |
| `android:dataExtractionRules` | `@xml/data_extraction_rules`; cloud backup and device transfer exclude every domain | `app/src/main/AndroidManifest.xml:45`, `app/src/main/res/xml/data_extraction_rules.xml:3-16` | ok |
| `android:usesCleartextTraffic` | not set; target 37 defaults to false | application element | ok |
| `android:networkSecurityConfig` | `@xml/network_security_config`, base cleartext false, no domains | `app/src/main/AndroidManifest.xml:50`, `app/src/main/res/xml/network_security_config.xml:3` | ok |
| Deep-link target | none: the only filter is `MAIN`/`LAUNCHER`, no `VIEW` or `BROWSABLE` | `app/src/main/AndroidManifest.xml:65-68` | ok |

`allowBackup="false"` makes the two rule files belt and braces; they stay so a later flip of the flag cannot silently upload the record database.

## 2. Permissions

The standard build declares the first eleven. `WAKE_LOCK` is declared only to remove it from the merged manifest; the library that wants it does not keep the phone awake in this app.

| Permission | Declared at | Call site | Verdict |
| --- | --- | --- | --- |
| `CAMERA` | `AndroidManifest.xml:6` | `CameraMirror.kt:82`, `:123`; `SessionRoute.kt:150` | ok |
| `VIBRATE` | `AndroidManifest.xml:9` | `Buzz.kt:28` | ok |
| `USE_BIOMETRIC` | `AndroidManifest.xml:11` | `OfficerAuth.kt:30-37` | ok |
| `RECEIVE_BOOT_COMPLETED` | `AndroidManifest.xml:13` | `RetentionJob.kt:56` (`setPersisted(true)` keeps the hourly sweep across a restart) | ok |
| `INTERNET` | `AndroidManifest.xml:16` | `Http.kt:33`; the gateway client is off by default | ok |
| `ACCESS_NETWORK_STATE` | `AndroidManifest.xml:18` | `Http.kt:89` | ok |
| `ACCESS_LOCAL_NETWORK` | `AndroidManifest.xml:20` | `Permission.kt:82` | ok |
| `RECORD_AUDIO` | `AndroidManifest.xml:24` | `VoiceCapture.kt:29`, `:39`; `VoiceSheet.kt:68` | ok |
| `BLUETOOTH` (API 30 and older) | `AndroidManifest.xml:28` | `BluetoothBonding.kt:53`, `:60` | ok |
| `BLUETOOTH_CONNECT` | `AndroidManifest.xml:29` | `BluetoothBonding.kt:34` | ok |
| `BLUETOOTH_SCAN` (`neverForLocation`) | `AndroidManifest.xml:30-33` | `BluetoothBonding.kt:34`, `:53`, `:60` | ok |
| `WAKE_LOCK` | `AndroidManifest.xml:22`, `tools:node="remove"` | absent from the merged manifest | ok |

The nearby flavour adds these; none is read by app code, so each is an owner decision, not a call site.

| Permission | Declared at | Why it is there | Verdict |
| --- | --- | --- | --- |
| `ACCESS_WIFI_STATE` (API 31 and lower) | `app/src/nearby/AndroidManifest.xml:10` | Nearby Connections, library only | owner decision |
| `CHANGE_WIFI_STATE` (API 31 and lower) | `app/src/nearby/AndroidManifest.xml:11` | Nearby Connections, library only | owner decision |
| `BLUETOOTH_ADMIN` (API 30 and lower) | `app/src/nearby/AndroidManifest.xml:12` | Nearby Connections scanning, library only | owner decision |
| `ACCESS_FINE_LOCATION` (API 32 and lower) | `app/src/nearby/AndroidManifest.xml:13` | Nearby Connections requires it; the app never reads a location | owner decision |
| `ACCESS_COARSE_LOCATION` (API 32 and lower) | `app/src/nearby/AndroidManifest.xml:14` | Nearby Connections requires it; the app never reads a location | owner decision |
| `BLUETOOTH_ADVERTISE` | `app/src/nearby/AndroidManifest.xml:16` | Nearby Connections advertising, library only | owner decision |
| `NEARBY_WIFI_DEVICES` | `app/src/nearby/AndroidManifest.xml:17` | Nearby Connections connection, library only | owner decision |
| `BLUETOOTH_SCAN` without `neverForLocation` | `app/src/nearby/AndroidManifest.xml:15` | the flavour lets Nearby derive location from scans; the comment at line 15 says so | owner decision |

The standard build, the reference build, refuses location and advertising in the merged-manifest gate at `app/build.gradle.kts:143-175`.

## 3. Exported components

| Component | Exported | Intent filters | Permission or reason | Verdict |
| --- | --- | --- | --- | --- |
| `MainActivity` | `true` | `MAIN` / `LAUNCHER` | documented: the launcher entry; HOME starts it | ok |
| `RetentionJob` (service) | `false` | none | `android.permission.BIND_JOB_SERVICE` | ok |
| `FileProvider` | `false` | none | not exported; grants one read URI per export | ok |
| `ProfileInstallReceiver` | removed | none | `tools:node="remove"` at `AndroidManifest.xml:72-75` | ok |

The `standardRelease` merged manifest adds only library components, all `android:exported="false"`: `InitializationProvider`, `androidx.camera.core.impl.MetadataHolderService` (also disabled), `androidx.room.MultiInstanceInvalidationService`, the datatransport `JobInfoSchedulerService`, `AlarmManagerSchedulerBroadcastReceiver` and `TransportBackendDiscovery`. The nearby merged manifest adds `com.google.android.gms.common.api.GoogleApiActivity`, also `false`.

No component has a `VIEW`/`BROWSABLE` deep-link filter, so there is no exported deep-link target to review.

## 4. Network security and cleartext

- `app/src/main/res/xml/network_security_config.xml:3` sets `<base-config cleartextTrafficPermitted="false" />`; there is no `domain-config` and no domain is exempted.
- `app/src/main/AndroidManifest.xml` does not set `android:usesCleartextTraffic`.
- `Http.kt:30` refuses any URL that does not start with `https://`, and `Http.kt:53` turns redirects off so a bearer token cannot follow one.

Verdict: ok. No cleartext domain exists.

## 5. Logging of personal data

| Line | What it logs | Risk | Verdict |
| --- | --- | --- | --- |
| `SignPredictions.kt:117` | question id, rung and the gloss lattice | release: guarded by `BuildConfig.DEBUG`; debug builds only | ok |
| `SignPredictions.kt:156` | phrasebook load failure | no content | ok |
| `SignReading.kt:122` | head value and frame timestamp | debug builds only | ok |
| `StageLink.kt:60`, calls at `:268`, `:329` | frame size, exception class | no payload, no endpoint id | ok |
| `WavPlayer.kt:56`, `SplitTracker.kt:114`, `:159`, `:177`, `HolisticTracker.kt:45`, `SignSetuApp.kt:236` | failure and model path | asset path only, no name or answer | ok |
| `ShowTiming.kt:18` | show-to-stage milliseconds | no content | ok |

No unguarded `Log.*` or `println` carries a person's name, an answer, a token, an audio path or a transcript. The debug-only content logs are listed under owner decisions because a debug build on a shared phone would keep them.

## 6. Hardcoded keys and tokens

The gate checks `app/src` and `brain/` for known key shapes (private-key header, AWS, Google, `sk-`, GitHub, Slack), for assignments to `apiKey`/`secret`/`token`/`password`/`bearer`, and for high-entropy string literals of 32 characters or more. It skips vendored `app/src/main/cpp/third_party`, model and content assets, and Python bytecode, which are data rather than source.

Verdict: ok. No match in either tree. The repository `.gitleaks.toml` allowlists only the vendored C++ sources.

## 7. WebView

| Setting | Value | Evidence | Verdict |
| --- | --- | --- | --- |
| `javaScriptEnabled` | `true` | `Avatar.kt:163` | ok |
| `allowFileAccess` | `false` | `Avatar.kt:164` | ok |
| `allowContentAccess` | `false` | `Avatar.kt:165` | ok |
| `addJavascriptInterface` | one bridge, `Android`, methods `ready()` and `finished(done)` only | `Avatar.kt:168`, `:210-221` | ok |
| Resource loading | only `https://appassets.androidplatform.net/avatar/`, `..` rejected, everything else 404 | `Avatar.kt:170-181` | ok |
| Navigation | every URL load returns true (blocked) | `Avatar.kt:183` | ok |

Verdict: ok. The bridge exposes no session data or file API; the only page loaded is the packaged avatar page.

## 8. PendingIntent, receivers, clipboard and FileProvider

| Item | Finding | Verdict |
| --- | --- | --- |
| `PendingIntent` | no `PendingIntent` call in `app/src/main`; nothing to make mutable | ok |
| Dynamic receiver, battery | `StageDevice.kt:422` registers with `ContextCompat.RECEIVER_NOT_EXPORTED` | ok |
| Dynamic receiver, Bluetooth | `BluetoothBonding.kt:58` and `:80` register with `RECEIVER_EXPORTED` for adapter discovery and bond-state broadcasts | owner decision |
| Clipboard, interpreter contact | `InterpreterCall.kt:93` copies the configured contact only, and says so at line 91; never session or transcript data | ok |
| Clipboard, endpoint id | `BrainSettings.kt:170` copies this phone's iroh endpoint id, a pseudonym | ok |
| FileProvider paths | `export_paths.xml:3` exposes `cache-path` `exports/`; code writes `cacheDir/exports` (`SessionReport.kt:377`) | ok |
| FileProvider grant | `SessionRoute.kt:257-263` uses `FLAG_GRANT_READ_URI_PERMISSION` on a chooser; no write grant, no persistable grant | ok |

The two `RECEIVER_EXPORTED` registrations listen to `BluetoothAdapter` and `BluetoothDevice` broadcasts, which are protected system broadcasts sent by the Bluetooth process, not by other apps.

## 9. Owner decisions

1. Nearby flavour location and Wi-Fi permissions. `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `BLUETOOTH_ADMIN`, `BLUETOOTH_ADVERTISE` and `NEARBY_WIFI_DEVICES` are required by Nearby Connections, not read by app code. Keep them only if the nearby build ships.
2. Nearby `BLUETOOTH_SCAN` drops `neverForLocation` at `app/src/nearby/AndroidManifest.xml:15`. That is the library's position, not the standard build's; the standard build keeps the flag and its merged-manifest gate enforces it.
3. Debug-only content logs at `SignPredictions.kt:117` and `SignReading.kt:122` record a question id and a reading in debug builds. Release is a no-op. Decide whether debug builds may leave a phone.
4. The WebView JavaScript bridge (`Avatar.kt:168`) is a deliberate, local-only asset page. It is safe as long as the host, the asset path and the two bridge methods stay as they are.
5. `RECEIVER_EXPORTED` for the Bluetooth broadcasts is required to receive them from the Bluetooth process; moving either to `NOT_EXPORTED` would stop pairing feedback.

## 10. Fixes applied

None. The gate found no missing `android:exported`, no `allowBackup` without rules, no cleartext, no dangerous permission without a call site and no hardcoded secret, so there was nothing to fix without changing behaviour. The manifest already sets `android:exported="false"` on the service and provider, `android:allowBackup="false"` with both rule files, and a network security config with cleartext off.

Future findings are fixed only when the change cannot alter behaviour; anything that would change what the app may reach, or what it keeps, goes to the owner first.
