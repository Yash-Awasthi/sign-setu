# 13. Data protection impact assessment

| Field | Value |
| --- | --- |
| Document | Sign Setu data protection impact assessment |
| Scope | The Android app (both flavors), the optional department gateway (`gateway/`) and the reviewer portal (`portal/`) |
| Controller | The police department that deploys the app **[confirm]** |
| Audience | Department legal officer and Data Protection Officer; not legal advice |

## 1. Processing described

### 1.1 Purpose

The app helps an officer communicate with a person who is Deaf, hard of hearing, mute, a child or otherwise unable to use speech. It shows questions as pictures, captions, speech and signs, and it records the person's answers. The app does not identify anyone, and it does not replace an interpreter, a special educator or a statutory recording (section 3).

### 1.2 Data subjects and categories

| Category | Examples | Held in | Retention |
| --- | --- | --- | --- |
| Answers | Taps, drawings, typed text, confirmed signs, lesson answers (name, address, school), and the officer's name for a saved session | Encrypted session database | 24 h idle by default, a set period of 4 to 72 h, or until the officer deletes it when the department chooses |
| Landmarks | Hand, pose and face points of signed answers | Memory only | Until the session ends or the app locks |
| Camera frames | The live picture while the stage camera shows, tracked for the overlay and gesture proposals | Memory only, one reused buffer | Not stored |
| Skeleton snapshot | A drawing of the pose for one answer, when the officer turns it on | Encrypted session database | As answers |
| Officer settings | Language, interpreter contact | DataStore | Until cleared |
| Device enrolment | Device token (hashed on the gateway), audit rows without subject data | Gateway database | Until revoked **[confirm]** |
| Sign contributions | Debug builds only (Record signs): a volunteer signer's landmarks for prompted words and sentences, the gloss and their corrections, under a random local signer id; never video or audio. Kept on the phone, and sent to the gateway (`POST /v1/contrib`) only while the signer's opt-in stands. Release builds have no entry point | Phone files; gateway disk as JSON lines | Until the signer deletes them (`DELETE /v1/contrib/{signer}`) **[confirm]** |

Children, victims of sexual offences and persons with disabilities are expected data subjects. Landmarks and camera frames are biometric in nature, even though they are never stored or used for identification.

### 1.3 Data flow

Camera, then MediaPipe landmark models on the phone, then the on-device sign reader, then a row holding the app's reading; the person's own answer row only when the officer asks them to choose from pictures. Nothing in this chain leaves the phone. The optional paths are:

- The second screen over bonded Bluetooth. It is encrypted (P-256 ECDH, AES-GCM) and carries no landmarks.
- The online assistant through the department gateway, or a custom API endpoint the officer sets up instead. It carries masked text only, and session text only after the officer allows it for the session.
- Speech of the officer's typed text through the gateway (`/v1/speak`), under the same switch and session permission: up to 500 characters of the officer's own words, unmasked, go through the gateway to the speech provider (ElevenLabs or Cartesia) and come back as audio. Nothing the person said or signed is sent.
- The encrypted PDF export, on an officer's action only.

## 2. Lawful basis

| Processing | Basis | Notes |
| --- | --- | --- |
| Core session | DPDP Act 2023 s.7 (legitimate uses: State functions, compliance with law) and the s.17(1)(c) exemption for the prevention, detection, investigation or prosecution of offences | s.17(1)(c) does not lift s.8(1) and s.8(5). Accuracy and reasonable security safeguards still bind the controller. Consent is not the basis, so the app does not ask the subject to "consent" to the interview **[confirm]** |
| Children in the core session | As above | s.9 (verifiable parental consent) falls under Chapter II, which s.17(1)(c) exempts for investigation. JJ Act s.74 and POCSO ss.24, 26 still apply |
| Sign contributions | Consent under s.6 | Not an investigation purpose, so the exemption does not apply. A consent card states what leaves the phone; withdrawing stops the queue at once. A child's contribution needs verifiable parental consent under s.9 and the 2025 Rules. Debug builds only; off by default |
| Clip upload | None | Session landmarks stay in memory and are never uploaded; no video is recorded or sent |

## 3. Statutory recording and interpretation

The app is an aid to communication. It is not the record the law requires in these cases:

| Provision | Requirement | Consequence for the app |
| --- | --- | --- |
| BNSS s.173(1), provisos (formerly CrPC s.154(1)) | Information about certain offences from a woman with a temporary or permanent mental or physical disability is recorded at a place of her choice, with an interpreter or special educator, and the recording is videographed | The app stores no video, so it cannot be that recording. The officer arranges the interpreter and the videography |
| BNSS s.183 (formerly CrPC s.164(5A)) | A statement before a magistrate by a person with a disability is recorded with an interpreter or special educator and videographed | As above. The report export says it is not a statement |
| BSA 2023 s.63 | An electronic record offered in evidence needs a certificate | The export carries a SHA-256 hash chain over the session timeline, a PDF digest and a draft s.63 certificate (`EvidenceIntegrity.kt`), which the department completes and signs **[confirm with the department]** |
| RPwD Act 2016 s.12 | Access to justice for persons with disabilities, in accessible formats | This is the purpose the app serves. It supports, and does not replace, the interpreter the Act's scheme expects |
| Constitution Art. 20(3) | No compelled self-incrimination | Rights notices are shown as statements. A Yes is never offered under a right |

## 4. Risks

Likelihood (L) and severity (S) are each rated 1 (low) to 3 (high), before and after controls.

| Risk | L×S before | Controls | L×S after |
| --- | --- | --- | --- |
| A lost, stolen or seized phone exposes session records | 3×3 | SQLCipher with a Keystore-wrapped key usable only while the phone is unlocked; FLAG_SECURE in the final release build; no backup. Sessions are purged after 24 hours by default (checked at every unlock, and the whole store is destroyed by a background job once even the newest session is past the period); keeping them until deleted is a choice that leaves more on a lost phone. A session's name is free text, stored in the encrypted row, shown on the start screen to whoever holds the unlocked phone, and deleted with the session; officers are told to use a case number. Rooted phones keep no history | 2×3 |
| A wrong machine reading is recorded as the person's answer | 3×3 | Readings are logged as the app's or the officer's, never as the person's answer, and are printed in their own section. The officer can ask the person to choose from pictures; only that pick is Confirmed. "Sign slower" asks again when a reading is unsure | 1×3 |
| Speech or text sent to a cloud service is retained or reused | 2×3 | No voice. Masked text only, through the department gateway, which stores none, or a custom endpoint an officer sets up, whose retention the department must check before allowing it. Masking is pattern based, not anonymisation: it replaces the names and details the subject gave, emails, links, PAN-style ids and runs of six or more digits, and lets through any name, place or description it was not told about, so an officer's own typed words can still identify someone. Off unless the officer turns it on and the device is enrolled. The AI counsellor sends the masked session text each turn, under the same controls | 1×2 |
| Camera pictures or landmarks leave the phone | 2×3 | No upload path exists for a session: its landmarks never reach disk or the Bluetooth link. Only a volunteer's Record signs landmarks in a debug build can be sent, with opt-in. The INTERNET permission is used only by the online assistant client and that opt-in queue | 1×3 |
| A child is identified through the record or export | 2×3 | No photo or face is stored. A name is stored only as the person's own written answer to a name question or in the officer's name for a saved session, encrypted and deleted with the session. Export only on an officer's action, encrypted with a passphrase shown once (JJ s.74, POCSO ss.24, 26) | 1×3 |
| A tampered model or content file changes what the person is shown | 2×3 | SHA-256 manifest checked before every load. APK signing. An imported assistant model is accepted only with a known hash | 1×3 |
| The app is treated as the statutory recording or as the interpreter | 2×3 | Notices in the session and on every exported page (section 3); officer training **[confirm]** | 1×2 |
| The second screen is spoofed or overheard | 2×2 | Bonded devices only. Six-digit comparison code, ECDH and AES-GCM. The stage device never opens the database | 1×2 |
| A contribution reveals who the signer is | 1×3 | Landmarks only, a random signer id, no names; opt-in per signer; the gateway checks the schema and a 2 MB size limit; the signer can delete everything by id | 1×2 |
| Reader accuracy is lower for some signers (regional variants, children, left-handed signers) | 3×2 | Readings kept apart from answers, picture choices when the subject is asked, and fingerspelling; evaluation on unseen signers in `tools/ml/EVAL.md` | 2×2 |
| Biometric points are held in memory for the length of a session | 2×2 | Memory only; cleared on lock and session end; face points only for grammar cues, never for identity | 1×2 |
| Gateway compromise exposes device tokens and audit rows | 2×2 | Tokens stored only as hashes and revocable; no subject data on the gateway; OIDC realm roles for admin and reviewer routes | 1×2 |
| The AI counsellor asks a leading question, or misses distress | 2×3 | Non-leading prompt; each line shown 2 s on the officer's phone before it goes, with Send now, Another line and Take over; risk or Stop hands back; only for victims and witnesses; every line marked in the record | 1×3 |

## 5. Data principal rights

Under s.17(1)(c), the core session is exempt from Chapter III. For the consent-based optional flows, a request to correct or erase data is answered by the department through the gateway admin **[confirm process]**. Records on the phone are erased by the retention period, by deleting the session, or by "Clear all data".

## 6. Breach handling

A breach of the gateway or a lost device with records still in retention is notified to the Data Protection Board and to affected persons as the 2025 Rules require. CERT-In is notified within six hours under the CERT-In Directions of 28 April 2022.
