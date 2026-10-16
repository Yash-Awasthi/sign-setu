# 04. Security

Target: OWASP MASVS v2 with the MAS-L2 testing profile, plus the CERT-In requirements for government mobile applications (Guidelines on Information Security Practices for Government Entities, 6.2.1 to 6.2.7). The app handles data about victims and children on officers' personal phones, so every control below is mandatory for release 1 unless marked otherwise.

## 1. Objectives

1. Subject data cannot be read from a lost, stolen or shared phone without the officer's credential.
2. Subject data does not leak through backups, screenshots, logs, notifications, the clipboard or other apps.
3. The only data that leaves the phone is an encrypted report or a contact link, both on explicit officer action.
4. Every release is built from reviewed code, scanned, and traceable to an audited build.

## 2. Controls

### 2.1 Storage

| ID | Control | Source |
| --- | --- | --- |
| SEC-01 | Session database encrypted with SQLCipher; random 256-bit key wrapped by an AES key in Android Keystore (hardware-backed where available). | CERT-In 6.2.3, 6.2.4; MASVS-STORAGE-1 |
| SEC-02 | Drawings stored only inside the encrypted database; export files only in app-private cache, deleted when the officer finishes the session, on Clear all data, and at every unlock. | MASVS-STORAGE-1 |
| SEC-03 | `android:allowBackup="false"`; `dataExtractionRules` (API 31+) and `fullBackupContent` (API 29-30) exclude everything. | MASVS-STORAGE-2 |
| SEC-04 | No personal data in Logcat; release builds strip `Log` calls with R8; no crash SDK. | CERT-In 6.2.5 |
| SEC-05 | Note fields disable autocorrect and request no personalised learning where the keyboard supports it. | MASVS-STORAGE-2 |
| SEC-06 | Copy disabled on log entries; subject data never placed on the clipboard. | MASVS-STORAGE-2 |
| SEC-07 | DataStore holds settings only, no personal data. | MASVS-STORAGE-1 |
| SEC-08 | Sessions purged on end or retention expiry (WorkManager and at next open, whichever is first); `secure_delete` overwrites purged rows and the rollback journal is truncated, so no write-ahead log keeps old pages. | DPDP section 8(7) |

### 2.2 Cryptography

| ID | Control |
| --- | --- |
| SEC-10 | Platform and SQLCipher cryptography only; no custom algorithms. |
| SEC-11 | Keys never hard-coded or stored in plain text; Keystore keys non-exportable. |
| SEC-12 | Export is a PDF encrypted with AES-256 under a one-time 80-bit random passphrase (16 characters), shown once and shared out of band. |

### 2.3 Authentication

| ID | Control |
| --- | --- |
| SEC-20 | The app requires a secure device lock and refuses to store sessions without one (`KeyguardManager.isDeviceSecure()`). |
| SEC-21 | Opening the app and viewing past sessions require BiometricPrompt: `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` on API 30+; `BIOMETRIC_WEAK or DEVICE_CREDENTIAL` on API 29, where the strong combination is unsupported. Re-lock after 5 minutes in the background. The app first tries the Keystore key silently: within its 30-second window after the officer unlocks the phone it opens without a second prompt (D-23). |
| SEC-22 | The key-encryption key is bound to user authentication: `setUserAuthenticationParameters` (time-bound) on API 30+; `setUserAuthenticationValidityDurationSeconds` on API 29. |

### 2.4 Network

| ID | Control |
| --- | --- |
| SEC-30 | Release 1 declares no INTERNET permission; the build fails if the merged manifest contains it. |
| SEC-31 to SEC-34 | Release 1.1 only: TLS 1.2+, certificate pinning to the project gateway, server-side validation, partner keys only in the gateway. |

### 2.5 Platform interaction

| ID | Control | Source |
| --- | --- | --- |
| SEC-40 | `FLAG_SECURE` on the activity: blocks screenshots, screen recording and recents thumbnails. | NFR-32 |
| SEC-41 | Only the launcher activity exported, apart from WorkManager's job service, which Android requires and protects with BIND_JOB_SERVICE; unused library receivers are removed from the merged manifest; export through `FileProvider` with a temporary grant. | MASVS-PLATFORM-1 |
| SEC-42 | The only dangerous permission is CAMERA, for the stage mirror (D-20); no RECORD_AUDIO, LOCATION or INTERNET. | CERT-In 6.2.6 |
| SEC-43 | No WebView. | MASVS-PLATFORM-2 |
| SEC-44 | No notifications containing subject data. | NFR-37 |
| SEC-45 | `filterTouchesWhenObscured` on every window the app opens (the activity, and each dialog, sheet and menu), so Show, export and clear actions ignore touches that come through another app's overlay. | MASVS-PLATFORM-3 |
| SEC-47 | Camera: only Preview is bound, plus ImageAnalysis when a hand model is installed; no ImageCapture or VideoCapture; frames are never written anywhere and the analysis bitmap is recycled after each frame; the camera runs only while the stage is on screen; CAMERA is requested from the officer at session start, never on the stage; the officer and the department can turn the mirror off. | MASVS-PLATFORM-1, DPDP section 8 |
| SEC-46 | Screen pinning (`startLockTask`) from the first Show state until the session ends; first run guides the officer to turn on app pinning with "ask for PIN before unpinning". Leaving the Show state for the Officer state is one tap on the officer handle, with BiometricPrompt as well when the department turns it on (D-23). | FR-61 |

### 2.6 Code and supply chain

| ID | Control | Source |
| --- | --- | --- |
| SEC-50 | Dependencies pinned with Gradle dependency verification and locking. | CERT-In app guidelines 4.15 |
| SEC-51 | CycloneDX SBOM for every release build. | CERT-In 8.5 |
| SEC-52 | Android Lint security checks, Semgrep and OSV-Scanner on every change; gitleaks on every commit. | CERT-In app guidelines 4.8 |
| SEC-53 | Release build has no test code, debug flags or logs; `debuggable=false`. | CERT-In 6.2.5 |
| SEC-54 | Content bundle verified against the manifest hashes before use. | CERT-In app guidelines 4.16 |
| SEC-55 | minSdk 29; target the latest stable SDK; security fixes released within 30 days of a relevant advisory. | MASVS-CODE-1 |

### 2.7 Resilience

| ID | Control |
| --- | --- |
| SEC-60 | R8 full-mode shrinking and obfuscation. |
| SEC-61 | Release 1.1 only: Android key attestation verified by the gateway (both Google roots). |
| SEC-62 | Release signing key generated offline, held by the release manager with a second custodian and an offline backup in department custody; APK Signature Scheme v3. |
| SEC-63 | Rooted or test-key device: warn the officer and disable session history; do not block the app in an emergency [confirm with department]. |
| SEC-64 | Build expiry: new sessions refused 180 days after the build date (FR-67), because personal phones have no device management. |
| SEC-65 | The assistant model is imported from a file, never downloaded; it is kept only if its SHA-256 is on the app's allow-list, computed while copying, so no other file reaches the native parser. Each request starts from an empty context and nothing typed is stored by the assistant. | D-24 |

## 3. Key management

| Key | Where | Rotation | Loss impact |
| --- | --- | --- | --- |
| Database key (DEK) | Generated on the phone, wrapped by the KEK | On Clear all data | Sessions unrecoverable (acceptable: short-lived data) |
| Key-encryption key (KEK) | Android Keystore, non-exportable, auth-bound; not invalidated by enrolling a fingerprint | On reinstall, or when Android drops it because the screen lock was removed or reset | Same; the app then deletes the unreadable database, makes new keys and tells the officer once, instead of refusing to unlock |
| Export passphrase | Shown once to the officer | Per export | Report unreadable |
| Release signing key | Release manager and second custodian, offline | On compromise (APK Signature Scheme v3 rotation) | Installed apps cannot be updated without the backup |

## 4. Threats in scope for release 1

The full STRIDE analysis is in [governance/07-threat-model.md](governance/07-threat-model.md); it predates the offline design and must be refreshed before the audit. Release 1 removes the network threats (T-07 to T-13); camera exposure (T-16) returns with the mirror and is controlled by SEC-47.

| Threat | Main controls |
| --- | --- |
| Lost, stolen or seized phone | SEC-01, SEC-08, SEC-20 to SEC-22 |
| Family member who knows the phone PIN | End session after each conversation, SEC-08 retention, officer SOP; app-specific PIN if the pilot shows shared phones |
| Subject leaves the app on the officer's phone | SEC-46 |
| Backup or device transfer copies data | SEC-03 |
| Screenshot, recording, recents thumbnail | SEC-40 |
| Personal data in logs | SEC-04 |
| Report sent to the wrong person | SEC-12, passphrase out of band, officer SOP |
| Other apps, intents, overlays | SEC-41, SEC-45 |
| Rooted phone | SEC-63 |
| Camera shows or keeps images of the subject or bystanders | SEC-47, FLAG_SECURE (SEC-40), mirror off by officer or department |
| Compromised dependency or signing key | SEC-50 to SEC-52, SEC-62 |
| Gesture misread and acted on | Show-back confirmation (FR-66); "unclear" logged otherwise |
| Distressing content shown to a child | Content policy review; curated cards; no generated images |
| App used to take a statement | No statement feature; in-app notice; terms of use |

## 5. Review points

| When | Review |
| --- | --- |
| Every change | Review by someone other than the author; Lint, Semgrep, OSV-Scanner and gitleaks pass |
| End of phase 1 | MASVS self-check against this document |
| End of phase 2 | Full internal MAS-L2 review; threat model refreshed |
| Phase 3 | CERT-In empanelled audit (source and binary) |
