# 07. Threat Model

Method: STRIDE per element of the [system design](../system-design.html) data flow diagrams, plus abuse cases specific to field use. Release 1 has no network: rows marked *Release 1.1* apply once the gateway arrives and have no exposure until then (SEC-30). Required by the CERT-In guidelines for secure application development (4.9). Review at the end of each phase and on every architecture change.

Risk = likelihood x impact, each rated Low (1), Medium (2), High (3). Score 6-9 High, 3-4 Medium, 1-2 Low.

## 1. Assets

| Asset | Why it matters |
| --- | --- |
| A1 Subject answers, notes, drawings | Identify and describe a victim, often a child; disclosure is an offence under JJ Act section 74 |
| A2 Camera frames on the stage | Images of the subject and bystanders; shown live only |
| A2b Voice audio, landmarks in transit (Release 1.1) | Personal and biometric-like data |
| A3 Gateway and partner availability (Release 1.1) | Officers depend on it in the field |
| A4 Partner API keys (Release 1.1) | Abuse, cost, impersonation |
| A5 App signing key | Fake updates |
| A6 Correctness of what the subject sees | Wrong sign or picture can mislead or distress a child |

## 2. Attackers and situations

| Actor | Capability |
| --- | --- |
| Finder or thief of a lost phone | Physical access, no credential |
| Person with the officer's unlocked phone (colleague, family, suspect during a struggle) | Brief physical access while unlocked |
| Bystander near the officer | Can see the screen |
| Subject holding the phone on the stage | Can tap, swipe and press system buttons |
| Network attacker (public Wi-Fi, rogue tower) (Release 1.1) | Intercept or alter traffic |
| Malicious app on the same phone | Normal app permissions |
| Attacker with a repackaged app | Changes content or code; calls the gateway directly from release 1.1 |
| Insider in a partner team or cloud admin (Release 1.1) | Access to services and logs |
| Malicious dependency or build compromise | Code in the app |

## 3. STRIDE by element

| ID | Element | STRIDE | Threat | L | I | Risk | Controls | Residual |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T-01 | D1 Session store | I | Lost or seized phone; data read from storage | 2 | 3 | High | SEC-01, SEC-20, SEC-21, SEC-22, SEC-08 | Low |
| T-02 | D1 | I | Data copied through cloud backup or device transfer | 2 | 3 | High | SEC-03 | Low |
| T-03 | P2/P5 | I | Screenshot, screen recording or recents thumbnail exposes subject | 2 | 3 | High | SEC-40 | Low |
| T-04 | P5 | I | Personal data in logs or crash reports | 2 | 3 | High | SEC-04 (no crash SDK); verified ST-04 | Low |
| T-05 | P5 F17 | I | Export file shared to wrong person or app | 2 | 3 | High | SEC-12, officer SOP, export confirmation | Medium |
| T-06 | P1 | I | Keyboard learning or cloud keyboards capture notes | 2 | 2 | Medium | SEC-05; SOP advises the department keyboard | Medium |
| T-07 | F10 | I, T | Traffic intercepted or altered on hostile network (Release 1.1) | 2 | 3 | High | Release 1: no network (SEC-30); release 1.1: TLS and pinning | None in release 1 |
| T-08 | G1 | S | Repackaged app or script calls gateway, abuses partners (Release 1.1) | 3 | 2 | High | SEC-61, rate limits | None in release 1 |
| T-09 | D6 | I | Partner API keys stolen from app (Release 1.1) | 2 | 2 | Medium | Keys never in the app (SEC-11); verified ST-08 | None in release 1 |
| T-10 | G1 | D | Flood of requests takes gateway down (Release 1.1) | 2 | 2 | Medium | Rate limits, cloud DDoS protection, offline core | None in release 1 |
| T-11 | S1-S4 | I | Partner or Bhashini stores audio, text or landmarks (Release 1.1) | 2 | 3 | High | Minimised data, written no-retention terms [confirm], DPIA | None in release 1 |
| T-12 | D5 | I | Gateway logs reveal who was helped (Release 1.1) | 1 | 3 | Medium | Logs hold no names, text or landmarks | None in release 1 |
| T-13 | P6 | T | Tampered content bundle shows wrong words or pictures | 2 | 2 | Medium | SEC-54: failed files never parsed; a failed text file stops new sessions; verified ST-12 | Low |
| T-14 | Build | T | Compromised dependency or build pipeline | 1 | 3 | Medium | SEC-50, SEC-51, SEC-52, protected branch | Low |
| T-15 | A5 | S | Signing key stolen; malicious update | 1 | 3 | Medium | SEC-62, two custodians, offline backup | Low |
| T-16 | P4 | I | Camera mirror shows or keeps images of the subject or bystanders | 1 | 2 | Low | SEC-47 (preview only, nothing stored, runs only on the stage, officer and department can turn it off), SEC-40 | Low |
| T-17 | Other apps | I | Exported component or intent leaks data | 1 | 3 | Medium | SEC-41 | Low |
| T-18 | Overlay app | T | Tapjacking tricks officer into export or clear | 1 | 2 | Low | SEC-45 on every window the app opens; verified ST-14 | Low |
| T-19 | Rooted phone | E | Root bypasses sandbox and Keystore | 1 | 3 | Medium | SEC-63 (warning); key attestation from release 1.1 (SEC-61) | Medium |
| T-20 | P5 | R | Officer disputes what was asked or answered | 2 | 2 | Medium | Timestamped event log (FR-30); export | Low |
| T-21 | Stage | E | Subject leaves the app or opens the officer side | 2 | 3 | High | Screen pinning from the first Show (SEC-46); return only with the officer's credential (SEC-21) | Low |
| T-22 | Build | T | An old build with known flaws stays in use | 2 | 2 | Medium | Build expiry after 180 days (SEC-64) | Low |

## 4. Abuse and harm cases

These are not classic security attacks but can cause serious harm; they are treated with the same rigour.

| ID | Case | Impact | Controls |
| --- | --- | --- | --- |
| T-30 | A gesture is read wrongly and taken as fact | Wrong decision about a child | No automatic recognition in release 1; the officer's reading is shown back and only the subject's Yes records it (FR-66); cards and Yes/No as the main answer path; SOP |
| T-31 | A wrong or unclear gesture from the character misleads the subject | Confusion, distress | Only reviewed lexicon entries are played; captions and pictures always shown; sign-off on the [content review record](forms/content-review-record.md) |
| T-32 | Distressing picture or wording shown to a child | Re-traumatisation | Curated cards only; content policy review; no AI images (D-05) |
| T-33 | App used to take a formal statement | Evidence inadmissible; child harmed by improper process | SOP and in-app notice; no "statement" feature; export labelled as communication notes |
| T-34 | Officer uses the app on a suspect or for surveillance | Misuse of tool | Terms of use; no identification features; audit of usage counts only |
| T-35 | Subject sees officer notes | Distress, contamination of account | The stage never shows notes or the log; the officer side opens only with the officer's credential |
| T-36 | Bystanders see subject's answers on screen | Privacy | The stage shows only the current question; SOP on positioning |

## 5. Open items

- Confirm partner and Bhashini retention terms before release 1.1 (T-11).
- Decide rooted-device policy with the department (T-19).
- Validate auth-bound database key usability under field conditions (T-01, SEC-22).
