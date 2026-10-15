# 06. Security Architecture

Target: OWASP MASVS v2 with the **MAS-L2** testing profile (apps handling sensitive data), plus the CERT-In requirements for government applications. The app processes data about victims and children, so every control below is mandatory unless marked otherwise.

## 1. Security objectives

1. Subject data cannot be read from a lost, stolen or seized phone without the officer's device credential.
2. Subject data does not leak through backups, screenshots, logs, notifications, clipboard or other apps.
3. Network traffic cannot be read or altered, and only the genuine app can use the backend.
4. Partner services receive the minimum data needed and do not keep it.
5. Every release is built from reviewed code, scanned, and traceable to an audited build.

## 2. Control catalogue

Each control has an ID used in the [threat model](../governance/07-threat-model.md), [test plan](10-test-plan.md) and [compliance matrix](../governance/14-compliance-matrix.md).

### 2.1 Storage (MASVS-STORAGE)

| ID | Control | Source |
| --- | --- | --- |
| SEC-01 | Session database encrypted with SQLCipher; 256-bit random key generated on first use, wrapped by an AES key in Android Keystore (hardware-backed where available). | CERT-In 6.2.3, 6.2.4; MASVS-STORAGE-1 |
| SEC-02 | Drawings and exports stored only inside the encrypted database or as encrypted files in app-private storage; nothing on shared storage. | MASVS-STORAGE-1 |
| SEC-03 | `android:allowBackup="false"`; `dataExtractionRules` exclude all domains for cloud backup and device transfer. | MASVS-STORAGE-2 |
| SEC-04 | No personal data in Logcat; release builds strip `Log` calls with R8 rules; no crash SDK. | CERT-In 6.2.5; MASVS-STORAGE-2 |
| SEC-05 | Text fields for notes use `KeyboardOptions(autoCorrect = false)` and no-personalised-learning IME flag to limit keyboard caching. | MASVS-STORAGE-2 |
| SEC-06 | Clipboard not used for subject data; copy disabled on log entries. | MASVS-STORAGE-2 |
| SEC-07 | Settings in DataStore hold no personal data. EncryptedSharedPreferences is not used (deprecated). | MASVS-STORAGE-1 |
| SEC-08 | Sessions auto-deleted after the configured period by WorkManager and at next app open, whichever is first. Database file rewritten (VACUUM) after deletion. | DPDP Rule 8; retention policy |

### 2.2 Cryptography (MASVS-CRYPTO)

| ID | Control |
| --- | --- |
| SEC-10 | Only platform and SQLCipher cryptography; no custom algorithms. AES-256-GCM for file encryption. |
| SEC-11 | Keys never hard-coded or stored in plain text; Keystore keys are non-exportable. |
| SEC-12 | Export files encrypted with AES-256-GCM under a key derived from a one-time passphrase (Argon2id or PBKDF2-HMAC-SHA256 with at least 600,000 iterations) shown to the officer once and shared out of band. |

### 2.3 Authentication and app access (MASVS-AUTH)

| ID | Control |
| --- | --- |
| SEC-20 | App requires a secure device lock screen; refuses to store sessions if none is set. |
| SEC-21 | Opening the app and viewing past sessions requires BiometricPrompt: `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` on API 30 and later; on API 29, where that combination is unsupported, `BIOMETRIC_WEAK or DEVICE_CREDENTIAL`, with `KeyguardManager.isDeviceSecure()` for the lock-screen check. Re-lock after 5 minutes in background. |
| SEC-22 | Database key use is bound to user authentication so the key is unusable while the app is locked: Keystore `setUserAuthenticationParameters` (time-bound, credential or strong biometric) on API 30 and later; `setUserAuthenticationValidityDurationSeconds` on API 29. [confirm usability in phase 1] |

### 2.4 Network (MASVS-NETWORK)

| ID | Control | Source |
| --- | --- | --- |
| SEC-30 | Network Security Config: cleartext disabled, user-added CAs not trusted in release. | CERT-In 6.2.7 |
| SEC-31 | Certificate pinning to the gateway's public key (current and backup pin), via Network Security Config `pin-set` with an expiry and OkHttp `CertificatePinner`. | CERT-In 6.2.2 |
| SEC-32 | TLS 1.2 minimum, TLS 1.3 preferred, on gateway and all partner links. | CERT-In 6.1.7 |
| SEC-33 | Gateway validates every request server-side: schema, size, rate. | CERT-In 6.1.6, 6.1.10 |
| SEC-34 | Partner API keys only in the gateway's secret manager; rotated at least every 90 days. | CERT-In app guidelines 4.15 |

### 2.5 Platform interaction (MASVS-PLATFORM)

| ID | Control | Source |
| --- | --- | --- |
| SEC-40 | `FLAG_SECURE` on the main activity: blocks screenshots, screen recording and recents thumbnails. | NFR-32 |
| SEC-41 | No exported activities, services, receivers or providers except the launcher activity; export via `FileProvider` with temporary URI grant only. | MASVS-PLATFORM-1 |
| SEC-42 | Permissions limited to CAMERA, RECORD_AUDIO, INTERNET, ACCESS_NETWORK_STATE; camera and microphone requested at first use with explanation. | CERT-In 6.2.6 |
| SEC-43 | No WebView. | MASVS-PLATFORM-2 |
| SEC-44 | No notifications containing subject data. | NFR-37 |
| SEC-45 | Tapjacking protection: `filterTouchesWhenObscured` on confirm, export and clear actions. | MASVS-PLATFORM-3 |

### 2.6 Code quality and supply chain (MASVS-CODE)

| ID | Control | Source |
| --- | --- | --- |
| SEC-50 | Dependencies pinned with Gradle dependency verification (checksums) and locking. | CERT-In app guidelines 4.15 |
| SEC-51 | SBOM (CycloneDX) generated for every release build and stored with the release. | CERT-In govt guidelines 8.5 |
| SEC-52 | SAST on every pull request (Android Lint security checks, plus a static analyser such as Semgrep or MobSF static scan); dependency vulnerability scan (OWASP Dependency-Check or OSV-Scanner). | CERT-In app guidelines 4.8 |
| SEC-53 | Release build contains no test code, debug flags or debug logs; `debuggable=false`. | CERT-In 6.2.5 |
| SEC-54 | Input from partner services treated as untrusted: keypoint arrays bounds-checked, text length-limited, no rendering of HTML. | CERT-In app guidelines 4.16 |
| SEC-55 | minSdk 29, targetSdk the latest required by Play; security patch releases within 30 days of a relevant advisory. | MASVS-CODE-1 |

### 2.7 Resilience (MASVS-RESILIENCE)

| ID | Control | Source |
| --- | --- | --- |
| SEC-60 | R8 full-mode obfuscation and shrinking. | CERT-In 6.2.5 |
| SEC-61 | Android Key Attestation: the app generates a hardware-backed key with a gateway challenge; the gateway verifies the chain to either Google attestation root (the RSA root and the ECDSA P-384 root used by remotely provisioned devices since April 2026), the revocation status list, the app signing certificate digest and verified boot state, then issues a 1-hour token. | CERT-In 6.1.12 |
| SEC-62 | Release signing key generated offline, held encrypted by the release manager with a second custodian and an offline backup in department custody; APK Signature Scheme v3 for key rotation. | |
| SEC-63 | Rooted-device handling: warn the officer and disable session history (not block the app in an emergency) [confirm with department]. | |
| SEC-64 | Build expiry: a build stops starting new sessions 180 days after its build date and shows "update required"; existing sessions stay exportable. Needed because personal phones have no MDM and an offline build has no gateway minimum version (D-19). | |

### 2.8 Privacy (MASVS-PRIVACY)

| ID | Control |
| --- | --- |
| SEC-70 | Camera frames processed in memory and discarded; only landmarks leave the phone, only while recognition is on. |
| SEC-71 | Audio recorded only while the button is held; buffer cleared after upload. |
| SEC-72 | No third-party analytics, ads or tracking SDKs; no advertising ID. |
| SEC-73 | Visible camera and microphone indicators in the app in addition to the system indicators. |
| SEC-74 | Gateway logs contain a salted install hash, never names, text, audio or landmarks. |

## 3. Key management

| Key | Where | Rotation | Loss impact |
| --- | --- | --- | --- |
| Database key (DEK) | Generated on phone, wrapped by Keystore KEK | On "clear all data" | Data unrecoverable (acceptable: data is short-lived) |
| Keystore KEK | Android Keystore, non-exportable, auth-bound | On reinstall | Same as above |
| Export passphrase | Shown once to officer | Per export | Export unreadable |
| App upload key | Release manager, two custodians | On compromise | Play upload key reset |
| Gateway TLS key | Cloud certificate manager | Before pin expiry; backup pin pre-shipped | App cannot connect until update |
| Partner API keys | Gateway secret manager | 90 days | Service outage until rotated |

## 4. Backend controls (gateway and partner services)

- Hosted in an India region of a MeitY-empanelled cloud before production (D-07).
- Least-privilege cloud roles; MFA for all administrators; no shared accounts.
- Logs enabled for all components, retained for a rolling 180 days in India, time-synced to NIC NTP (CERT-In Directions 2022).
- Logs reviewed continuously with alerts for anomalies (CERT-In 6.1.11).
- Separate staging environment; no production data in staging (none exists server-side by design).
- Vulnerability assessment and penetration test at least annually and on every significant change (CERT-In 6.1.9).
- Request bodies never stored; partner services confirm the same in writing [confirm].

## 5. Security review points

| When | Review |
| --- | --- |
| Every pull request | Code review by someone other than the author; SAST and dependency scan pass |
| End of phase 1 | MASVS self-check using the security skills checklist |
| End of phase 2 | Full internal MASVS MAS-L2 review; threat model updated |
| Phase 3 | CERT-In empanelled audit (source code and black box) |
| Every 6 months | Internal audit |
| Every year and on major change | Third-party audit |
