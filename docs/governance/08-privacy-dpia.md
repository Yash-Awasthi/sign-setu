# 08. Data Protection Impact Assessment

Status: draft for review by the department's legal adviser. Legal points are summaries of public sources, not legal advice. Items marked [confirm] need the adviser's decision.

## 1. Why a DPIA

The app processes personal data of people in a vulnerable situation, most often children, including images of their bodies (processed into landmarks), their voices and what they say about what happened to them. Even where the law may not strictly require a DPIA of this processing (see section 4), the risk justifies one. It is also the evidence auditors and the department will ask for.

## 2. Description of processing

| Item | Description |
| --- | --- |
| Controller (Data Fiduciary) | The police department operating the app [confirm legal entity] |
| Processors | Cloud provider hosting the gateway; ISL team service; ML team service; Bhashini (MeitY) |
| Data principals | Subjects (lost, rescued or kidnapped persons, often children, often persons with disabilities); officers |
| Purpose | Communicate with a subject who cannot communicate in words, to ensure their safety, meet immediate needs and identify their family, in the course of police duties |
| Not purposes | Formal statements, evidence collection, identification of suspects, profiling, analytics |
| Data | See [DFD data inventory](../archive/04-data-flow-diagrams.md#data-inventory) |
| Storage | Encrypted on the officer's phone only; nothing stored server-side except access metadata |
| Retention | Until exported or the auto-delete period ends (default 24 hours) |
| Recipients | Officer; export recipients chosen by the officer under the SOP (case file, CWC) |
| Transfers outside India | None intended; hosting in India before production [confirm Bhashini and cloud regions] |

## 3. Necessity and proportionality

| Data | Necessary? | Minimisation |
| --- | --- | --- |
| Questions and answers | Yes, core purpose | Short-lived; no identity fields required |
| Notes | Yes, officer must record what they learnt | Free text; SOP limits to what is needed |
| Drawings | Yes, alternative answer path | Saved only on officer action |
| Camera images | Needed for the stage mirror so the subject sees their own hands (D-20); later for the partner hand model | Shown live only; never stored, recorded or sent; analysed on the phone only when a hand model is installed; officer and department can turn the mirror off |
| Landmarks | Needed by recognition service | Sent only when hands are visible; partner must not store |
| Voice audio | Needed for voice questions | Officer's voice; only while button held; not stored |
| Name, address, photo of subject | Not needed by the app | No fields for them; SOP says record identity in official systems, not the app |

## 4. Legal basis

### 4.1 Digital Personal Data Protection Act 2023 and Rules 2025

- **Commencement.** Rules notified 13 November 2025 (G.S.R. 846(E)); most obligations commence 13-14 May 2027 (G.S.R. 843(E)). Rule 4 (Consent Managers) commences 13 November 2026. No notification advancing the other dates was found as of 15 October 2026; recheck before the pilot [confirm].
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
| Information Technology Act 2000 and CERT-In Directions 2022 | Incident reporting, log retention, time sync. |

## 5. Risks to data principals

| ID | Risk | L | I | Mitigation | Residual |
| --- | --- | --- | --- | --- | --- |
| P-01 | Child's identity or account disclosed from a lost phone | 2 | 3 | Encryption, app lock, auto-delete (SEC-01, 20, 21, 08) | Low |
| P-02 | Disclosure through export to the wrong person | 2 | 3 | Encrypted export, SOP, confirmation step | Medium |
| P-03 | Audio or landmarks retained by processors | 2 | 3 | Minimisation; written no-retention terms; India hosting | Medium until terms confirmed |
| P-04 | Wrong recognition leads to wrong decision about the child | 2 | 3 | Confidence display, ask again, SOP, cards preferred | Medium |
| P-05 | Distress from content shown | 2 | 3 | Content policy, curated cards, no AI images | Low |
| P-06 | Function creep: app used for surveillance or evidence | 1 | 3 | Terms of use, SOP, no such features, periodic review | Low |
| P-07 | Bystander sees subject's answers | 2 | 2 | Focus mode, SOP | Low |
| P-08 | Data kept longer than needed | 2 | 2 | Auto-delete, retention policy | Low |

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

Review this DPIA before the pilot, before rollout, annually, and whenever data flows change.
