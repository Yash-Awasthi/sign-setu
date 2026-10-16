# 08. Data Protection Impact Assessment

Status: draft for review by the department's legal adviser. Legal points are summaries of public sources, not legal advice. Items marked [confirm] need the adviser's decision.

## 1. Why a DPIA

The app processes personal data of people in a vulnerable situation, most often children, including live images of their hands on the camera mirror, face and hand landmarks computed on the phone while they sign (biometric data, held in memory only), what they sign in an account of what happened, and what they answer about their safety and needs. Even where the law may not strictly require a DPIA of this processing (see section 4), the risk justifies one. It is also the evidence auditors and the department will ask for.

## 2. Description of processing

| Item | Description |
| --- | --- |
| Controller (Data Fiduciary) | The police department operating the app [confirm legal entity] |
| Processors | None in release 1: the app has no internet access. The vision engine and sign reader run on the phone. A paired second screen is a department device under the same controller and stores nothing. Release 1.1 adds the gateway, Bhashini and partner services, and this DPIA is reviewed then |
| Data principals | Subjects (lost, rescued or kidnapped persons, victims, witnesses and accused persons; often children, often persons with disabilities); officers |
| Purpose | Communicate with a subject who cannot communicate in words, to ensure their safety, meet immediate needs, identify their family and, for an accused, show the rights the law requires, in the course of police duties |
| Not purposes | Formal statements, evidence collection, identification of suspects, profiling, analytics |
| Data | See [system design](../system-design.html) data dictionary (Tables 4 and 5) |
| Storage | Encrypted on the officer's phone only; no server |
| Retention | Until exported or the auto-delete period ends (default 24 hours) |
| Recipients | Officer; export recipients chosen by the officer under the SOP (case file, CWC) |
| Transfers outside India | None in release 1. The interpreter video call takes place in the department's messaging app, outside Sign Setu (see P-10) |

## 3. Necessity and proportionality

| Data | Necessary? | Minimisation |
| --- | --- | --- |
| Questions and answers | Yes, core purpose | Short-lived; no identity fields required |
| Notes | Yes, officer must record what they learnt | Free text; SOP limits to what is needed |
| Drawings | Yes, alternative answer path | Saved only on officer action |
| Camera images | Needed for the stage mirror so the subject sees their own hands (D-20), and for sign reading inside a sign window (D-25) | Shown live only; never stored, recorded or sent; analysed only inside a window the officer or subject opens (20 s for an answer, 3 minutes for an account); officer and department can turn the mirror off |
| Face and hand landmarks (biometric) | Needed to read signed answers and to let an interpreter replay a fast account (D-25, D-27) | Computed on the phone; held in view-model memory for the session only and wiped on end, lock and clear; never written to disk, logged, exported or sent over the second-screen link; an account keeps hands and pose only; never used to identify anyone (SEC-71) |
| Signed account (Tell me) | Needed when a traumatised person can only narrate | Landmarks only, as above; the app's reading is recorded as a reading, not the person's words; never a statement |
| Role and juvenile mark | Needed to offer the right questions and rights (D-28) | Three values; "Not known yet" until set and then exported only when set; no offence details |
| Suggestion metadata (Auto) | Needed to show who chose each item | Item IDs, reasons and links between answers and items, in the encrypted session record under the same retention; no new personal data |
| Second-screen link records | Needed to show which answers came from a second screen | Device model, version code, the first 8 characters of the content hash and the last 4 hex of the SHA-256 of the peer address; never the Bluetooth friendly name, which may carry a child's name (JJ Act s.74). Answers are encrypted in transit and the stage stores nothing |
| Voice audio | Not processed in release 1 | No microphone permission; the build fails if one is added (SEC-30) |
| Interpreter video call | Needed when pictures and gestures are not enough (FR-64) | Started by the officer in the department's messaging app to a department contact; Sign Setu passes no session data |
| Name, address, photo of subject | Not needed by the app | No fields for them; SOP says record identity in official systems, not the app |

## 4. Legal basis

### 4.1 Digital Personal Data Protection Act 2023 and Rules 2025

- **Commencement.** Rules notified 13 November 2025 (G.S.R. 846(E)); most obligations commence 13-14 May 2027 (G.S.R. 843(E)). Rule 4 (Consent Managers) commences 13 November 2026. No notification advancing the other dates was found when this assessment was drafted; recheck before the pilot [confirm].
- **Rule 8(3) minimum retention.** A Data Fiduciary must retain personal data, associated traffic data and processing logs for at least one year for the purposes in the Seventh Schedule, which include State functions under law. Commentators disagree on whether this applies to all processing or only when the purpose falls in that Schedule. If it applies here, it conflicts with the 24-hour auto-delete (FR-33); if it does not, longer retention would breach section 8(7). The adviser must decide, in writing, before the pilot [confirm].
- **Section 7 legitimate uses** include processing by the State for functions under law and for responding to a medical emergency or threat to life or safety. The triage purpose fits these. [confirm]
- **Section 17(1)** exempts processing in the interest of prevention, detection, investigation or prosecution of offences from most of the Act. The duty to protect data with reasonable security safeguards (section 8(5)) still applies. Whether locating a lost child counts as an "offence" purpose depends on the case (kidnapping yes; a lost child possibly not) [confirm].
- **Children (section 9).** Processing a child's data needs verifiable parental consent, and tracking, behavioural monitoring and targeted advertising directed at children are prohibited. Rule 12 with the Fourth Schedule, Part B, item 1 exempts processing for exercising a power or performing a function or duty under law in the interest of a child, limited to what is necessary. Officers acting under the Juvenile Justice Act and missing-children procedure are expected to fall under this [confirm].
- **Persons with disabilities.** Rule 11 covers consent through a lawful guardian; the same exemptions are expected to apply [confirm].
- **Design consequence.** Regardless of which exemption applies, the app meets the security safeguard duty in full, keeps data for the shortest time, and never tracks or monitors the subject.

### 4.2 Child-protection and criminal law

| Law | Effect on the app |
| --- | --- |
| Juvenile Justice (Care and Protection of Children) Act 2015, section 74 | Identity of a child in need of care and protection must not be disclosed; penalties up to six months and two lakh rupees. No identity fields; no data in logs, notifications or crash reports; controlled export. |
| JJ Model Rules 2016, rule 92, and the 2016 SOP for missing and found children | Officers follow the procedure; the app supports first contact only. |
| POCSO Act 2012, sections 24 and 26 | Statements recorded with interpreter or special educator, audio-video where possible, at a place where the child feels safe. The app must not be used to record a statement. |
| Rights of Persons with Disabilities Act 2016, section 40 and Rule 15 | Accessibility per IS 17802 for ICT. |
| Rights of Persons with Disabilities Act 2016, section 7(4) | The disability-rights items shown to a victim with a disability (draft, legal review). |
| BNSS 2023 sections 38, 47, 48, 53, 58 and 187; Constitution Article 20(3) | Rights items shown to an accused, each recorded as shown, not proof of understanding, and to be explained through a qualified interpreter. |
| BNSS sections 173 and 183(6), BSA section 125 | Statements of a person who cannot speak are recorded with an interpreter and on video; the app's exports say "communication note, not a statement". |
| Juvenile Justice Act 2015 section 10 and Model Rules | A child in conflict with law: the child's rights items and the officer's JJ notice. |
| Information Technology Act 2000 and CERT-In Directions 2022 | Incident reporting, log retention, time sync. |

## 5. Risks to data principals

| ID | Risk | L | I | Mitigation | Residual |
| --- | --- | --- | --- | --- | --- |
| P-01 | Child's identity or account disclosed from a lost phone | 2 | 3 | Encryption, app lock, auto-delete (SEC-01, 20, 21, 08) | Low |
| P-02 | Disclosure through export to the wrong person | 2 | 3 | Encrypted export, SOP, confirmation step | Medium |
| P-03 | Audio or landmarks retained by processors | 1 | 3 | No processors and no network in release 1; landmarks stay on the phone | Low; reassess for release 1.1 |
| P-04 | A gesture or sign is read wrongly and a wrong decision follows | 2 | 3 | Readings are graded as the app's or the officer's and exported separately; only the subject's pick among alternatives makes a meaning theirs (FR-66, FR-86); cards and Yes/No preferred | Low |
| P-05 | Distress from content shown | 2 | 3 | Content policy, curated cards, no AI images | Low |
| P-06 | Function creep: app used for surveillance or evidence | 1 | 3 | Terms of use, SOP, no such features, periodic review | Low |
| P-07 | Bystander sees subject's answers | 2 | 2 | The stage shows only the current question; notes and log stay on the officer side; SOP | Low |
| P-08 | Data kept longer than needed | 2 | 2 | Auto-delete, retention policy | Low |
| P-09 | A child is upset by seeing themself on the camera mirror | 2 | 2 | Officer turns the mirror off for that person; department can turn it off for all; nothing recorded (SEC-47) | Low |
| P-10 | The interpreter call exposes the subject's face and words to a third-party platform | 2 | 3 | Call only to the department's contact; SOP prefers Sandes (government); Sign Setu passes no session data; the call is the department's process, outside the app | Medium |
| P-11 | Face and hand landmarks (biometric) are kept or misused | 1 | 3 | Analysis only inside a sign window; memory only, wiped on end, lock and clear; never stored, exported or sent; no identification; usage statistics of the library dropped on the device (SEC-71, SEC-72) | Low |
| P-12 | A signed account recorded by the app is treated as a statement | 2 | 3 | Readings marked as readings and printed apart from answers; not-a-statement notice on every export page; SOP; landmarks never exported | Low |
| P-13 | An accused is led into giving an account | 1 | 3 | Free-recall and sensitive groups never offered for an accused; Auto never proposes them; rights recorded as shown, not understood (D-28, D-30) | Low |
| P-14 | A paired stage device leaks or keeps session content | 1 | 3 | Encrypted link; stage stores nothing; pinned hand-back screen; department devices only; Bluetooth friendly names never recorded (SEC-66 to SEC-70) | Low |

## 6. Rights of data principals

Where the Act's rights apply, requests go to the department's grievance officer named in the [privacy notice](policies/privacy-notice.md). Because the app keeps data only briefly on one phone, most requests will be answered by confirming that the data has been deleted or handed to the case file, which follows its own rules.

## 7. Breach handling

See [operations and incident response](12-operations-incident-response.md): report to CERT-In within six hours of noticing a cyber incident; notify the Data Protection Board and affected persons as required by Rule 7 once in force; notify the CWC where a child's data is involved [confirm process].

## 8. Sign-off

| Role | Name | Decision | Date |
| --- | --- | --- | --- |
| Privacy lead | | | |
| Legal adviser | | | |
| Department approver | | | |

Review this DPIA before the pilot, before rollout, annually, and whenever data flows change. Paired mode is not for statement-taking (POCSO s.24 and s.26, BNSS).
