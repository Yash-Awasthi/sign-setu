# 04. Security

Target: OWASP MASVS v2 with the MAS-L2 testing profile, and the CERT-In requirements for government mobile applications (Guidelines on Information Security Practices for Government Entities, 6.2.1 to 6.2.7). The app handles data about victims and children on officers' personal phones: NowSecure tier 3, government. Every control is mandatory unless marked otherwise. Architecture references are to [02-architecture.md](02-architecture.md).

## 1. Objectives

1. Subject data cannot be read from a lost, stolen or shared phone without the officer's credential.
2. Subject data does not leak through backups, screenshots (in the final release build), logs, notifications, the clipboard or other apps.
3. Data leaves the phone only as an encrypted report or a contact link on the officer's action, as encrypted frames to a bonded stage device, or as masked text to the assistant service after the officer switched it on and allowed it for the session, or to the department's own station over iroh while the officer has the station link on: audio, signs, text for its model, and conversations the officer sends ([18](18-station.md)). Debug builds add one more path: a volunteer's Record signs landmarks, sent to the gateway only with their opt-in.
4. Every release is built from reviewed code, scanned and traceable to an audited build.

## 2. Controls

### 2.1 Storage

| Control | Source |
| --- | --- |
| Session store encrypted with SQLCipher; a random 256-bit DEK wrapped by a non-exportable AES-256-GCM Keystore key usable only while the phone is unlocked (`DatabaseKey`). Without a secure lock screen the DEK is wrapped by a key not tied to unlocking, the officer is told once, and the DEK moves under the unlocked-only key at the first unlock after a lock is set. | CERT-In 6.2.3, 6.2.4; MASVS-STORAGE-1 |
| Drawings and skeleton snapshots only inside the encrypted store. Export files only in `cacheDir/exports`, deleted when the officer finishes, after 15 minutes, at every unlock and on Clear all data. | MASVS-STORAGE-1 |
| `allowBackup="false"`; `dataExtractionRules` and `fullBackupContent` exclude every domain. | MASVS-STORAGE-2 |
| No personal data in Logcat; release builds strip `android.util.Log` calls (`proguard-rules.pro`); the four remaining log lines carry exception classes or timings only; no crash SDK. | CERT-In 6.2.5 |
| Every text field asks the keyboard for no personalised learning (`AppRoot.NoPersonalisedLearning`); the enrolment code field is a password field. | MASVS-STORAGE-2 |
| Only the interpreter's contact ever reaches the clipboard, on the officer's tap. | MASVS-STORAGE-2 |
| DataStore `settings` and the plain preference files `locale` and `clock` hold settings only. The gateway token, the officer's custom endpoint key and the phone's iroh key are each wrapped by their own Keystore key (`KeystoreVault`) in `noBackupFilesDir`. A temporary Keystore error (typical just after boot) never deletes a stored file; a truncated one is caught explicitly. The wrapped database key is flushed to disk before it replaces the old file. | MASVS-STORAGE-1 |
| Keys and secrets typed in a dialog (the access key, the developer AI key) are held in memory only, never in saved instance state. The report's drawings are decoded downsampled, so a hostile drawing cannot exhaust memory. | MASVS-STORAGE-2 |
| Retention purge with `PRAGMA secure_delete` on and a truncated journal; while the app is locked the whole store is destroyed once even the newest session is past retention (the retention sweep, also run hourly by `RetentionJob`). | DPDP s.8(7) |

### 2.2 Cryptography

| Control | Source |
| --- | --- |
| Platform JCA, Android Keystore and SQLCipher only; no custom algorithms. AES-256-GCM, P-256 ECDH, HKDF-SHA256, SHA-256; `SecureRandom` for every key and nonce (`kotlin.random` is used only to shuffle pictures). | MASVS-CRYPTO-1 |
| No key in the source, the repository or any APK: the build has no field for an assistant key. The gateway enrols with a one-time department code; a custom API endpoint takes the officer's own key, kept in the Keystore. gitleaks runs on every push. | MASVS-CRYPTO-2 |
| Export: a PDF with no password, sealed rather than encrypted — every page carries the record digest and the hash-chain head, the certificate page the full chain head and its status, and the seal page the P-256 device signature over the line (`EvidenceIntegrity`). | MASVS-CRYPTO-1 |
| Event log sealed as a SHA-256 chain as rows are written (`EventSeal`); export re-walks it and reports the first broken row. | BSA s.63 |
| Two readers, one seal: the phone's own "Check a report" (`checkReport`) and the off-phone `tools/security/verify_seal.py` are diffed field by field over the same exported file by `tools/security/seal_agreement.py`, so the session, entry count, chain head, Merkle root, printed-rows digest, key fingerprint and the verdict itself cannot drift apart. Measured 0.8.52 on an API 34 emulator; a copy with one signature character flipped makes the two disagree. | BSA s.63 |

### 2.3 Authentication

| Control | Source |
| --- | --- |
| `OfficerAuth`: platform `BiometricPrompt` with `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` (API 30+), device credential on API 29, before the store opens, before a session is left, ended, exported or deleted, and before Clear all data. One confirmation covers the next 60 s; a lock forgets it. A phone with no secure lock skips the prompt. | MASVS-AUTH-2, AUTH-3 |
| Relock after 5 minutes in the background, 30 inside a session; a relock, crash or restart always asks again. | MASVS-AUTH-2 |
| The KEK needs no prompt but is created with `setUnlockedDeviceRequired(true)`; Android invalidating it (lock removed or reset) deletes the unreadable store and tells the officer once. | MASVS-AUTH-2 |
| A key file that is truncated or opens with no key counts as lost: the store is reset and the officer told once, rather than failing every unlock. The report's device signing key prefers StrongBox, then the TEE, then a TEE key without attestation, so reports are always sealed (`DeviceSeal`). | MASVS-AUTH-2 |
| Gateway: one-time enrolment codes (stored as SHA-256 on the server), per-address and global wrong-code limits, a random 256-bit bearer token (stored as SHA-256 on the server), optional token life (`TOKEN_TTL_DAYS`, 0 in production: tokens do not expire), admin revocation; admin and reviewer routes need an OIDC token with the right realm role (30 s clock leeway). The `Bearer` scheme is matched case-insensitively and any other scheme is refused with 401. Per-address rate limits treat an IPv6 /64 as one address. | MASVS-AUTH-1 |

### 2.4 Network

| Control | Source |
| --- | --- |
| INTERNET is used only by the online assistant client (`UrlHttp`): HTTPS only, cleartext refused by the network security config and by `UrlHttp`, system trust anchors only, validated networks only. Nothing connects unless the build has a gateway, or the officer has saved their own service, and the officer has switched the assistant on. | MASVS-NETWORK-1 |
| Session text goes only after the officer allows it once per session; names, details, emails, links, PAN-like ids and long numbers are masked first (`Pseudonymizer`). The gateway accepts only the app's own system prompts. Speaking typed text (`OnlineAssistant.speak`) sends the officer's own words, up to 500 characters and not masked, to the gateway's `/v1/speak` under the same switch and permission. | MASVS-NETWORK-1, PRIVACY-1 |
| Gateway replies carry `Cache-Control: no-store` and `X-Content-Type-Options: nosniff`; every request has a 70 s deadline and speech streams 60 s; the header limit is 64 KiB. Logs record the matched route pattern, never the raw path, so a signer id in `DELETE /v1/contrib/{signer}` is never logged. | MASVS-NETWORK-1 |
| Station computer, section 2.9: its own certificate is pinned by the phone, and iroh traffic is end-to-end encrypted to the brain's key. No relay other than the department's is ever contacted. | MASVS-NETWORK-1, NETWORK-2 |
| No certificate pinning for the gateway; the design depends on the production host and CA ([14](14-attestation-and-pinning.md)). | MASVS-NETWORK-2 (gap for the gateway) |

### 2.5 Platform interaction

| Control | Source |
| --- | --- |
| `FLAG_SECURE` on the activity window whenever the officer's Block screenshots setting is on. A release APK starts with that setting on, because the `release` build type forces `SECURE_SCREEN=true` and ignores the `secureScreen` property; only debug builds follow the property (off by default) so testers can capture. Dialog and sheet windows are separate windows and do not inherit the activity's flag: they are not protected, a known low-severity gap (F-11) that needs a device check. Settings > Security has three switches, each needing the officer to turn it off: Pin the app, Block screenshots (default from `SECURE_SCREEN`) and Ask for fingerprint. Debug builds made with `-PtestUnlocked=true` also skip pinning and the fingerprint so the app can be driven over adb. | MASVS-PLATFORM-3 |
| Exported components in the merged release manifest: the launcher activity. `RetentionJob` is not exported and is bound only by the platform scheduler (`BIND_JOB_SERVICE`). FileProvider not exported, one temporary read grant per share. Unused library receivers removed. No deep links, no PendingIntents. | MASVS-PLATFORM-1 |
| One WebView, the character's (`Avatar.kt`): it loads only the bundled avatar page, answers every other request with 404 and blocks navigation; file and content access are off; its JavaScript bridge exposes only `ready()` and `finished(token)`. | MASVS-PLATFORM-2 |
| `filterTouchesWhenObscured` on the activity and every dialog window (`GuardedAlertDialog`). | MASVS-PLATFORM-3 |
| Runtime permissions: CAMERA asked of the officer at session start, never on the stage; BLUETOOTH_CONNECT only when a second screen is set up; BLUETOOTH_SCAN (never for location) only when the officer taps Find a nearby phone. RECORD_AUDIO only after consent is logged in the voice sheet. Build gates fail any variant with background location, and `standard` with any discovery or location permission. | CERT-In 6.2.6 |
| Camera: Preview and ImageAnalysis only; frames tracked in memory while the stage camera shows, at most one per 30 ms and down to one per 250 ms when idle or when the phone reports severe heat; landmarks kept only inside a sign window; the officer and the department can turn the mirror off. | MASVS-PRIVACY-1, DPDP s.8 |
| Screen pinning (`startLockTask`) for the whole session on this phone, lifted only for the interpreter call or the share sheet and set again on return. The officer should turn on "ask for PIN before unpinning" in system settings; the app does not check it. | |

### 2.6 Code and supply chain

| Control | Source |
| --- | --- |
| Dependencies pinned, locked (`gradle.lockfile`) and checksum-verified (`verification-metadata.xml`); BouncyCastle forced to 1.86; llama.cpp vendored at a fixed commit. | CERT-In app guidelines 4.15; MASVS-CODE-3 |
| CycloneDX SBOM and OSV-Scanner record for every release build on `master`. | CERT-In 8.5 |
| Release lint with security checks as errors, semgrep, OSV-Scanner on the shipped runtime lockfile, gitleaks. | CERT-In app guidelines 4.8 |
| Input from outside the app is bounded before use: second-screen frames (4 KiB handshake, 1 MiB frames, strict counters, `ignoreUnknownKeys=false`), drawings re-encoded with bounded size, written answers capped at 2,000 characters, the gateway's body and field limits. Room queries are parameterised; the only raw SQL is fixed migration DDL. | MASVS-CODE-4 |
| Content and models verified against SHA-256 before use; the assistant model accepted only from an allow-list. | CERT-In app guidelines 4.16 |
| minSdk 29; compileSdk and targetSdk 37; a build refuses new sessions 180 days after its build day. | MASVS-CODE-1, CODE-2 |

### 2.7 Resilience

| Control | Source |
| --- | --- |
| R8 shrinking and obfuscation in release; not debuggable; logs stripped. | MASVS-RESILIENCE-3 |
| Release key generated offline; CI signs with it from encrypted repository secrets, and the release manager and a second custodian hold offline copies; APK Signature Scheme v3. | MASVS-RESILIENCE-2 |
| Rooted or test-key device: heuristic check (`Build.TAGS`, `su` paths), warn, keep no history, still usable in an emergency. | MASVS-RESILIENCE-1 (partial) |
| No debugger, hook or repackaging detection; Play Integrity is unavailable in `standard` (NOTE E-5). | MASVS-RESILIENCE-2, -4 (gap) |

### 2.8 Second screen

| Control | Source |
| --- | --- |
| Secure RFCOMM to bonded devices only; SDP name "Display" and an app-specific UUID. The app never advertises. It scans only on the officer's tap, on Android 12 and later, under BLUETOOTH_SCAN marked never for location, and bonds only with the phone the officer picks, through the system's confirmation on both phones; the six-digit code check of the link still follows. The build fails if the standard flavor's scan permission loses its never-for-location flag. | CERT-In 6.2.6 |
| P-256 ECDH with commit then reveal; HKDF-SHA256 over the transcript gives two direction keys, the 6-digit code, the 4-digit check number and the resume key. AES-256-GCM frames with a direction byte and counter as IV; the receiver requires the next counter. Peer keys must lie on P-256; version code and content hash must match; the code is compared in constant time; three misses close the link; timeouts connect 12 s with one retry, handshake 15 s, code 90 s. | MASVS-CRYPTO-1, NETWORK-1 |
| The resume secret is memory only and zeroed on Bye, window expiry, lock and end; the peer address is pinned for resume. | MASVS-CRYPTO-2 |
| The stage device stores nothing about a session, never opens the store, reads signs itself and sends words only; the officer phone re-encodes drawings. LINK rows hold the peer model, version, content hash prefix and 4 hex of the hashed address, never the Bluetooth name. | MASVS-STORAGE-1; JJ Act s.74 |

### 2.9 Station computer

| Control | Source |
| --- | --- |
| Off by default; the officer turns it on. Over iroh the phone and brain are authenticated by key and the traffic is QUIC, end-to-end encrypted to the brain's key. A relay sees the two addresses, the brain's public key, timing and sizes, never content ([16](16-relay.md)). | MASVS-NETWORK-1 |
| The brain uses only the department's relay (`--relay`); with none configured it uses no relay and no n0 service. The phone uses the relay named in the ticket and uses no relay when the ticket names an n0 relay (`*.iroh.link`, matched by host; an unparsable URL counts as n0). | MASVS-NETWORK-1, PRIVACY-2 |
| On the station network the brain serves HTTPS with a self-signed P-256 certificate; the phone trusts that certificate's SHA-256 pin and nothing else. Only private addresses are served (IPv4-mapped IPv6 addresses are judged by their IPv4 range). LAN pairing accepts IPv6 unique-local addresses. | MASVS-NETWORK-2 |
| Every request needs the bearer token, checked on each heartbeat. An empty or damaged token file gets a new token and an empty token is never accepted. Per-address wrong-token counting, and a per-phone rate limit over iroh (`x-signsetu-peer`). Rotating the token (`--rotate-token`) makes every phone pair again. | MASVS-AUTH-1 |
| The brain's certificate, token, `pairing.txt` and `iroh.key` are written owner-only (0600) from the start and replaced in one step, so a crash cannot leave a broken `iroh.key` (which would change the brain's identity). `status.sh` and `status.ps1` pass the token on stdin, not on a command line. | MASVS-STORAGE-1 |
| Input to the brain is bounded: request paths are decoded and rejected if they contain `..`, backslashes or control characters; chat messages carry only role and content (system, user or assistant; content up to 32 KB); WAV headers with zero channels or rate are refused; a stored file is read back at most 256 MB; inbox names that are Windows device names are prefixed. | MASVS-CODE-4 |
| The phone caps station replies at 8 MB on both paths, bounds every stream with timeouts and closes only the connection a failed call used. The phone's iroh key is made once per install in `noBackupFilesDir/iroh.bin` under the Keystore key `signsetu.iroh.key`; a relay `[access]` allowlist names its endpoint id. | MASVS-CODE-4, CRYPTO-2 |

### 2.10 What the seal proves, and what it does not

An exported report carries one seal line, `SS1|<session>|<sealed entries>|<chain head>|<Merkle root>` (`EventSeal.sealLine`),
signed with ECDSA P-256 by a key the Android Keystore holds and never releases (`DeviceSeal`). The signature covers those
four facts and nothing else; the key travels with the seal so any reader can check it without the phone.

| The seal proves | How the reader sees it |
| --- | --- |
| The four facts in the line are the ones the phone sealed: the session, how many entries it held, the chain head and the tree's Merkle root | The signature verifies under the key in the seal (`verify_seal.py`, `checkReport`) |
| Nobody changed the seal page after export: one character of the line or of the signature breaks it | `Signature invalid` from the off-phone tool; `SIGNATURE_INVALID` in the app |
| The key that signed is a P-256 key that was in a Keystore on the phone's account, since the private half never leaves it | By construction; the fingerprint on the certificate names that key |
| When the session is still on the phone, that the stored rows still rebuild the same head — no row, drawing, landmark or recorded answer was edited afterwards | The app re-walks every row (`verifyChain`) and answers `RECORD_ALTERED` when one does not match; the two readers are diffed over the same file by `seal_agreement.py` |

| The seal does not prove | Why |
| --- | --- |
| Who used the phone. No officer identity, login or PIN is signed | The officer's name, rank and station are blank lines on the paper for them to complete; `OfficerAuth` guards the app, not the key |
| That the record is true, or that the person said what is written | The seal is about bytes, not truth: readings are marked as readings, and a machine draft is marked not part of the record |
| When the seal was made, in a way an outside party can trust | The date on the certificate and the rows' timestamps come from the phone's clock; there is no trusted timestamp and no external time source |
| That the phone was genuine and unrooted | No key attestation is requested and the root check is a heuristic (F-4); a rooted phone with a rewritten store and its own key can produce a self-consistent seal, which is why the key fingerprint has to be compared with the one printed by a phone the department trusts |
| That the printed rows are the sealed rows, when checked away from the phone | The printed-rows digest sits in the seal outside the signed line (F-10), so only re-walking the chain on the phone ties pages to entries; off-phone the seal binds the chain head to the key, not the pages to the head |
| That the device signing key belongs to the officer who signed the paper | Any holder of the phone can produce a sealed report; the certificate's signature and department custody are what tie it to a person |

The seal is therefore a device-possession record, not an authorship record: it shows this phone produced this chain head,
not that a given officer wrote or reviewed it. The paper's signature, the station diary and the department's custody chain
carry authorship; the hash chain and the seal carry integrity.

## 3. MASVS v2 checklist

Status from code and configuration; dynamic testing by the empanelled auditor confirms it (§7).

| Control | Status | Evidence | MASTG |
| --- | --- | --- | --- |
| STORAGE-1 Secure storage | Implemented | `DatabaseKey`, `SessionDatabase`, `KeystoreVault` | 0001, 0200 |
| STORAGE-2 No leakage | Implemented | Backup rules, Log stripping, no-learning keyboard | 0003, 0011, 0201, 0202 |
| CRYPTO-1 Strong cryptography | Implemented | §2.2 inventory | 0013, 0208, 0221 |
| CRYPTO-2 Key management | Implemented | §4 | 0015, 0212 |
| AUTH-1 Remote authentication | Implemented on the gateway; pinning and attestation open | `gateway/auth.go` | 0017 |
| AUTH-2 Local authentication | Partial: the prompt gates actions; the DEK is not bound to the prompt by a CryptoObject (accepted: officers share no device credential, and the KEK still needs an unlocked phone) | `OfficerAuth`, `DatabaseKey` | 0018, 0326 |
| AUTH-3 Step-up for sensitive actions | Implemented for leave, end, export, delete, clear; switching the online assistant on is gated by a notice, not the prompt | `OfficerAuth` call sites | 0327 |
| NETWORK-1 Secure traffic | Implemented | `UrlHttp`, `network_security_config.xml` | 0019, 0217, 0233 |
| NETWORK-2 Identity pinning | Gap for the gateway until the production host is fixed; the station's certificate is pinned | [14](14-attestation-and-pinning.md) | 0020, 0021 |
| PLATFORM-1 IPC | Implemented | Merged manifest (§2.5) | 0007, 0250, 0251 |
| PLATFORM-2 WebViews | Implemented: bundled page only, no network, no file access, two-method bridge | `ui/avatar/Avatar.kt` | 0028 |
| PLATFORM-3 UI | Implemented in the final release build | FLAG_SECURE (`secureScreen`), overlay filter, pinning | 0035, 0289 |
| CODE-1 Platform version | Implemented | minSdk 29, targetSdk 37 | 0272 |
| CODE-2 Updates | Implemented by build expiry | `Policy.kt` | 0274 |
| CODE-3 Vulnerable components | Implemented | OSV-Scanner, lockfile, BouncyCastle pin | 0222 |
| CODE-4 Input validation | Implemented | §2.6 | 0025, 0245 |
| RESILIENCE-1 Platform integrity | Partial: heuristic root check | `AppContainer.deviceLooksRooted` | 0038 |
| RESILIENCE-2 Anti-tampering | Partial: signing only | | 0040, 0224 |
| RESILIENCE-3 Anti-static analysis | Implemented: R8 | `app/build.gradle.kts` | 0045 |
| RESILIENCE-4 Anti-dynamic analysis | Gap | NOTE E-5 | 0039, 0263 |
| PRIVACY-1 Minimisation | Implemented | Landmarks only from sign windows, no face mesh, deleted with the session; no identity columns; masked online text | 0254, 0255 |
| PRIVACY-2 No identification | Implemented | No identifiers; no analytics; MediaPipe statistics stubbed | 0318 |
| PRIVACY-3 Transparency | Implemented | First run, online notice, per-session consent, not-a-statement notice | 0256 |
| PRIVACY-4 Control | Implemented | Retention, delete, Clear all data | 0319 |

## 4. Key management

| Key | Where | Rotation | Loss impact |
| --- | --- | --- | --- |
| Database key (DEK) | Random, wrapped in `noBackupFilesDir/dek.bin` | On Clear all data, or when the KEK is lost | Sessions unrecoverable (short-lived data) |
| KEK `signsetu.kek.unlocked` | Android Keystore, non-exportable, unlocked-device only | On reinstall, or when Android drops it | Store deleted, new keys made, officer told once |
| KEK `signsetu.kek.open` | Keystore, only while the phone has no secure lock | Moved under the unlocked-only key at the first unlock with a lock | Same |
| Gateway token key `signsetu.gateway.token` | Keystore, wraps `gw.bin` | On Clear of the token (401) | Device enrols again with a new code |
| Phone iroh key `signsetu.iroh.key` | Keystore, wraps `iroh.bin` (the phone's station-link identity) | Never, unless the file is lost; reinstall or a backup restore makes a new one | The phone gets a new endpoint id; the relay allowlist, if any, is updated |
| Brain keys (`iroh.key`, certificate, token) | The brain's state folder, owner-only | `--rotate-token` for the token; delete `iroh.key` for a new identity | Every phone pairs again |
| Service key key `signsetu.assist.key` | Keystore, wraps `assist.bin` (custom API endpoint) | When the officer removes or replaces the service | Officer enters the key again |
| Export passphrase | Removed in 0.8.52: the report is one file with no password | — | Anything an officer shares can be read by whoever receives it; the seal still shows alteration |
| Link keys | ECDH and HKDF per link, memory only | Every link and resume | Link closes; pair again |
| Resume secret | Memory only | Every new link; zeroed on Bye, expiry, lock, end | Reconnect needs the code |
| Release signing key | Repository secrets for CI; offline copies with the release manager and a second custodian | On compromise (v3 rotation) | Updates need the backup |

## 5. Threat model (STRIDE)

Trust boundaries are TB0 to TB3 of the [data flow](README.md#33-trust-boundaries).

| Boundary | Threat | STRIDE | Controls | Residual |
| --- | --- | --- | --- | --- |
| Phone at rest | Lost, stolen or seized phone read | I | SQLCipher, unlocked-only KEK, relock, retention, wipe while locked | A phone with no lock screen; officer SOP |
| Phone at rest | Family member who knows the phone PIN | S, I | Retention, finish and delete, OfficerAuth | Shared PINs; department PIN if the pilot shows it |
| Stage | Subject leaves the app or reads officer data | E, I | Pinning, officer handle, no drafts or notes on the stage | Unpinning gesture if the device allows it |
| Stage | Machine reading taken as the subject's answer | R, T | Readings logged as the app's or officer's, never the subject's; the subject can be asked to choose from pictures; grades; readings in their own section | An officer who never asks the subject to choose |
| Record | Row edited after the fact | T, R | Hash chain, record digest, certificate | A full rewrite of the store with the DEK |
| OS and apps | Overlay tapjacking, screenshots, backups | T, I | Touch filter, FLAG_SECURE in the final build, backup exclusion | Screenshots in testing builds |
| OS and apps | Malicious intent or provider access | E | Nothing exported beyond the launcher and the job service | |
| Bluetooth | Rogue, relayed or wrong stage device | S, T | Bonded only, commit-then-reveal, code and check number, AEAD, strict counters | Officer misreads the check number |
| Network | Traffic read or altered | I, T | TLS, validated networks, masking | User-installed CA until pinning |
| Station link | Rogue or relayed brain, token guessing, a hostile relay | S, I | Key-authenticated end-to-end QUIC, certificate pin on the LAN, bearer token on every request, rate limits, the department's own relay only | A copied state folder impersonates the brain; keep it private |
| Network | Stolen enrolment code or token used elsewhere | S | Single-use codes, rate limits, revocation, optional token life | Attestation not built |
| Gateway | Prompt injection through the assist proxy | E | System prompts allow-listed; size limits | User text is still free text |
| Build | Compromised dependency or signing key | T | Lockfile, verification, scans, SBOM, key only in repository secrets and offline copies | A compromised GitHub account could sign a release |
| Library | Usage statistics sent by MediaPipe | I | Upload backend excluded and replaced by a stub | |
| Device | Rooted phone, hooking, repackaging | T, E | Root heuristic, R8, signing | No anti-debug or integrity attestation |
| Process | Denial of service by a hostile peer | D | Frame caps checked before allocation, timeouts | Bluetooth jamming |

## 6. Findings and accepted risks

| ID | Finding | Severity | Decision |
| --- | --- | --- | --- |
| F-1 | No certificate pinning | Medium | Open; depends on the production host (NOTE E-1) |
| F-2 | DEK not bound to the biometric prompt | Low | Accepted; documented in AUTH-2 |
| F-3 | No debugger or hook detection | Medium | Open; a department MASVS-R decision (NOTE E-5) |
| F-4 | Root check is a heuristic | Low | Warn-only by design, so an emergency is never blocked |
| F-5 | An assistant key compiled into an APK | Closed | The build no longer has a key field; keys live on the gateway or in the officer's Keystore |
| F-6 | The record digest joins fields without a separator | Low | The hash chain (length-prefixed fields) is the integrity control; the digest only matches re-exports. Left unchanged so digests on reports already handed out stay valid |
| F-7 | Testing builds allow screenshots | Closed for release builds | The `release` build type in `app/build.gradle.kts` forces `SECURE_SCREEN=true`, so every release APK sets `FLAG_SECURE` and the `secureScreen` property only decides debug builds, which stay open for testers |
| F-8 | Bundled BouncyCastle ships a trust-all `X509TrustManager` class (lint) | Info | Unused by the app; R8 keeps it only if referenced |
| F-9 | The event-log tree carries an odd leaf up to the next level; it is not RFC 6962's tree shape, though it uses its 0x00 and 0x01 prefixes | Info | Left unchanged: changing it would break existing reports; the verifier and the station use the same rule |
| F-10 | The printed-rows digest is in the seal but outside the signed line, so away from the phone the seal binds the chain head to the key and not the printed pages to the head | Low | Accepted for now: the digest is printed on every page so a re-export reproduces it, and the phone re-walks the chain; signing the digest as well would change the seal line and every report already issued (2.10) |
| F-11 | Dialog and sheet windows are not covered by the activity's `FLAG_SECURE`, so a screenshot taken while one is open can capture its content | Low | Open; needs a check on a device, then `FLAG_SECURE` set on the dialog windows where content warrants it |
| F-12 | A build that refuses `setUnlockedDeviceRequired` keeps the data key under the open key even on a phone with a screen lock, and the officer is not told | Low | Accepted for now: the only notice is the no-lock one, and showing it on a phone that has a lock would give wrong advice; a separate notice needs a sentence of its own in every officer language |
| F-13 | Lock zeroes the database key, but the officer's own service key and the station's pairing token stay in the process's memory until it exits | Low | Accepted for now: clearing the service key mid-process would change which service later requests target, since the key is loaded only at launch; a safe clear needs a reload path at unlock, and the values remain wrapped at rest |

## 7. Testing plan

| Area | Tests | How |
| --- | --- | --- |
| Storage | MASTG-TEST-0001, 0200, 0201 | Pull app data on a rooted test phone; confirm only ciphertext, no backup, no logs |
| Crypto | 0013, 0015, 0212 | Static review of §2.2; key aliases and flags with `adb shell dumpsys keystore` |
| Auth | 0018, 0326, 0327 | Relock timings; each gated action; behaviour with no lock screen |
| Network | 0019, 0020, 0233 | mitmproxy with a user CA against a gateway build; confirm masking on the wire |
| Platform | 0007, 0035, 0250 | Drozer for exported components; overlay test app; screenshot attempts |
| Second screen | Pairing protocol | [07-two-device-bluetooth-test.md](07-two-device-bluetooth-test.md); a modified peer for wrong version, bad curve point, replayed and oversized frames |
| Resilience | 0038, 0039 | Frida against root and relock behaviour |
| Privacy | 0254, 0256 | Permission review of each variant's merged manifest; first-run and consent texts |

## 8. Review points

| When | Review |
| --- | --- |
| Every change | Review by someone other than the author; unit tests, release lint, semgrep, OSV-Scanner and gitleaks pass |
| Gate 1, hardening | This checklist re-checked; internal MAS-L2 review; threat model refreshed |
| Gate 2, security audit | CERT-In empanelled audit, source and binary |

## 9. Incidents

Each class below has a different first move, because the seal constrains what can be recovered. Section references are to this document unless a file is linked.

| Incident | First action, first 15 minutes | Who is told | Why that is the first move |
| --- | --- | --- | --- |
| **Compromised device key.** The phone's report-signing P-256 key (`DeviceSeal`) is suspected: a rooted phone, or one that has left the department's hands. The key is chosen StrongBox, then TEE (section 2.3). | Stop exporting reports from that phone. Record the key fingerprint each issued report prints (section 2.10: the key travels with the seal) and treat every report carrying that fingerprint as device possession only; corroborate it with the paper signature and the station diary. | Department records custodian, the Data Protection Officer because personal data may be exposed, and the maintainer for the version record. Named contacts: **[confirm]**. | Section 2.10 already says a rooted phone with a rewritten store and its own key can produce a self-consistent seal, which is why the fingerprint must be compared with the one printed by a phone the department trusts. The seal proves possession, never authorship (section 2.10, close). |
| **Leaked report password, historical.** Only a report exported by a build before 0.8.52 has one. | Identify the file and the session it came from. Treat it as disclosed to whoever holds the password and do not re-share it. If the session is still on the phone within retention, export a fresh report, which has no password, for the case file, and keep the old file only as evidence of what was shared. | Records custodian and the Data Protection Officer. If the file holds personal data, the breach path in [13](13-dpia.md) applies, with CERT-In at six hours. Named contacts: **[confirm]**. | Section 4 records that the export passphrase was removed in 0.8.52 and the report is one file with no password; `tools/security/verify_seal.py` keeps `--passphrase` only for a report exported by an older build. The seal still shows alteration (section 2.10), so integrity stays checkable while confidentiality cannot be recovered. |
| **Bad model file.** The optional assistant GGUF imported through Settings, Offline, Import model file (see [README](README.md)). | Remove the imported model so no new load can happen. Keep the file and its hash for the report, and confirm the hash against the allow-list the department issued. | Department IT security cell, which issued the allow-list, and the maintainer. Named contacts: **[confirm]**. | An imported model is accepted only from an allow-list with a SHA-256 check (section 2.6; [13](13-dpia.md)), so the hash decides whether the file was ever legitimate. Assistant output is a draft marked not part of the record (section 2.10), so the session record stands unchanged. |
| **Signing regression.** Readings get worse after an update. | Officers stop accepting a reading. Use "Type what was signed" (`reading_correct`) and "Ask them to choose" (`signed_ask`), both in `app/src/main/res/values/strings.xml`, which is the app's existing route from a reading to the person's own answer (section 2.10). Collect the word, the profile and the version with its build day from Settings, where `settings_version` shows the version name and the build day (`app/src/main/java/app/signsetu/ui/settings/SettingsScreen.kt`). | The Deaf reviewer and the maintainer. The department IT security cell holds the previous APK, because a build refuses new sessions 180 days after its build day (section 2.6), which sets the rollback deadline. Named contacts: **[confirm]**. | A reading is never an answer: readings are logged as the app's or the officer's and printed in their own section (section 2.10), so a regression degrades help without corrupting the record. |

### 9.1 What every report carries

The certificate page of an export carries the session id and its start time, the device as manufacturer, model and Android release, and the producing app version with its version code (`EvidenceIntegrity.kt`). The rows carry their own timestamps and the exact on-screen message or word as it was shown. The build day is read in Settings. Nothing else leaves the phone by itself: INTERNET is used only by the assistant client, after the officer switches it on and allows the session (section 2.4).

### 9.2 Sequence when personal data may be out

When a leaked file, a lost phone still in retention or any other incident may have exposed personal data, the breach path of the DPIA is the one to follow: notify the Data Protection Board and the affected persons as the 2025 Rules require, and notify CERT-In within six hours under the CERT-In Directions of 28 April 2022 ([13](13-dpia.md)). Six hours is the hard clock; it starts when the officer first knows.
