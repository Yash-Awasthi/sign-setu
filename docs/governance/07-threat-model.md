# 07. Threat Model

Method: STRIDE per element of the [data flow diagrams](../archive/04-data-flow-diagrams.md), plus abuse cases specific to field use. Required by the CERT-In guidelines for secure application development (4.9). Review at the end of each phase and on every architecture change.

Risk = likelihood x impact, each rated Low (1), Medium (2), High (3). Score 6-9 High, 3-4 Medium, 1-2 Low.

## 1. Assets

| Asset | Why it matters |
| --- | --- |
| A1 Subject answers, notes, drawings | Identify and describe a victim, often a child; disclosure is an offence under JJ Act section 74 |
| A2 Voice audio, landmarks in transit | Personal and biometric-like data |
| A3 Gateway and partner availability | Officers depend on it in the field |
| A4 Partner API keys | Abuse, cost, impersonation |
| A5 App signing key | Fake updates |
| A6 Correctness of what the subject sees | Wrong sign or picture can mislead or distress a child |

## 2. Attackers and situations

| Actor | Capability |
| --- | --- |
| Finder or thief of a lost phone | Physical access, no credential |
| Person with the officer's unlocked phone (colleague, family, suspect during a struggle) | Brief physical access while unlocked |
| Bystander near the officer | Can see the screen |
| Network attacker (public Wi-Fi, rogue tower) | Intercept or alter traffic |
| Malicious app on the same phone | Normal app permissions |
| Attacker with a repackaged app | Calls the gateway directly |
| Insider in a partner team or cloud admin | Access to services and logs |
| Malicious dependency or build compromise | Code in the app |

## 3. STRIDE by element

| ID | Element | STRIDE | Threat | L | I | Risk | Controls | Residual |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T-01 | D1 Session store | I | Lost or seized phone; data read from storage | 2 | 3 | High | SEC-01, SEC-20, SEC-21, SEC-22, SEC-08 | Low |
| T-02 | D1 | I | Data copied through cloud backup or device transfer | 2 | 3 | High | SEC-03 | Low |
| T-03 | P2/P5 | I | Screenshot, screen recording or recents thumbnail exposes subject | 2 | 3 | High | SEC-40 | Low |
| T-04 | P5 | I | Personal data in logs or crash reports | 2 | 3 | High | SEC-04, SEC-72 | Low |
| T-05 | P5 F17 | I | Export file shared to wrong person or app | 2 | 3 | High | SEC-12, officer SOP, export confirmation | Medium |
| T-06 | P1 | I | Keyboard learning or cloud keyboards capture notes | 2 | 2 | Medium | SEC-05; SOP advises the department keyboard | Medium |
| T-07 | F10 | I, T | Traffic intercepted or altered on hostile network | 2 | 3 | High | SEC-30, SEC-31, SEC-32 | Low |
| T-08 | G1 | S | Repackaged app or script calls gateway, abuses partners | 3 | 2 | High | SEC-61, rate limits, SEC-33 | Low |
| T-09 | D6 | I | Partner API keys stolen from app | 2 | 2 | Medium | SEC-34 (keys never in app) | Low |
| T-10 | G1 | D | Flood of requests takes gateway down | 2 | 2 | Medium | Rate limits, cloud DDoS protection, offline core (FR-40) | Low |
| T-11 | S1-S4 | I | Partner or Bhashini stores audio, text or landmarks | 2 | 3 | High | Minimised data (SEC-70, SEC-71), written no-retention terms [confirm], DPIA | Medium |
| T-12 | D5 | I | Gateway logs reveal who was helped | 1 | 3 | Medium | SEC-74 | Low |
| T-13 | P6 F11 | T | Malformed or malicious partner response crashes app or shows wrong content | 2 | 2 | Medium | SEC-54, fallbacks | Low |
| T-14 | Build | T | Compromised dependency or build pipeline | 1 | 3 | Medium | SEC-50, SEC-51, SEC-52, protected branch | Low |
| T-15 | A5 | S | Signing key stolen; malicious update | 1 | 3 | Medium | SEC-62, two custodians, offline backup | Low |
| T-16 | P4 | I | Camera left on; subject or bystanders captured | 1 | 2 | Low | SEC-70, SEC-73, auto-off after 60 seconds of no hands | Low |
| T-17 | Other apps | I | Exported component or intent leaks data | 1 | 3 | Medium | SEC-41 | Low |
| T-18 | Overlay app | T | Tapjacking tricks officer into export or clear | 1 | 2 | Low | SEC-45 | Low |
| T-19 | Rooted phone | E | Root bypasses sandbox and Keystore | 1 | 3 | Medium | SEC-61, SEC-63 | Medium |
| T-20 | P5 | R | Officer disputes what was asked or answered | 2 | 2 | Medium | Timestamped event log (FR-30); export | Low |

## 4. Abuse and harm cases

These are not classic security attacks but can cause serious harm; they are treated with the same rigour.

| ID | Case | Impact | Controls |
| --- | --- | --- | --- |
| T-30 | Wrong sign recognition taken as fact (subject signs "no", app says "yes") | Wrong decision about a child | Confidence shown; low confidence flagged; "ask again"; SOP: never act on a single recognition; cards as primary answer path |
| T-31 | Wrong or poor ISL from avatar misleads subject | Confusion, distress | Recorded clips for triage; captions and pictures always shown; ISL review before release |
| T-32 | Distressing picture or wording shown to a child | Re-traumatisation | Curated cards only; content policy review; no AI images (D-05) |
| T-33 | App used to take a formal statement | Evidence inadmissible; child harmed by improper process | SOP and in-app notice; no "statement" feature; export labelled as communication notes |
| T-34 | Officer uses the app on a suspect or for surveillance | Misuse of tool | Terms of use; no identification features; audit of usage counts only |
| T-35 | Subject sees officer notes | Distress, contamination of account | Notes only in officer panel and sheets; focus mode hides officer panel |
| T-36 | Bystanders see subject's answers on screen | Privacy | SOP: position, focus mode; minimal text on subject panel |

## 5. Open items

- Confirm partner and Bhashini retention terms (T-11).
- Decide rooted-device policy with the department (T-19).
- Validate auth-bound database key usability under field conditions (T-01, SEC-22).
