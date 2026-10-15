# 01. Project Charter

## Purpose

Police officers regularly find lost children and adults, and rescue people from kidnapping, who cannot communicate in words: they are deaf, mute, both, or too traumatised to speak. Today officers rely on gestures, written notes or waiting for an interpreter. Police Assist gives the officer a way to ask simple questions and understand answers immediately, on one shared phone, while waiting for formal support.

The app is an **aid to communication**. It does not replace a qualified ISL interpreter, special educator or counsellor, and it does not record formal statements.

## Objectives

1. An officer can ask the core safety questions (are you safe, hurt, hungry, thirsty, need a doctor) and understand the answer within one minute of opening the app.
2. A deaf subject sees questions in ISL; a hearing non-verbal subject sees captions and pictures; any subject can answer by pointing, signing or drawing.
3. The app runs on old Android phones, works without network for the core triage flow, and is simple enough to use under stress without training beyond a short briefing.
4. Personal data about subjects, many of them children, is protected to the standard expected of a government app handling victims' data.

## Scope

**In scope**

- Android app (Android 10 and later) for officers
- Preset triage questions with recorded ISL, captions and pictures
- Free-text and voice questions, shown as captions and an ISL skeleton avatar
- Subject answers by picture cards, sign recognition and drawing
- Notes and session log stored encrypted on the phone, with export and clear
- Backend adapters for the partner services (ISL generation, sign recognition, speech and translation)

**Out of scope for v1**

- Officer login against a department identity system
- Cloud storage or sync of sessions, case management, integration with CCTNS
- Formal statement recording under POCSO or the Juvenile Justice Act
- Emotion or distress detection from faces
- AI-generated images (see D-05 in the [decision log](../07-decisions.md))
- iOS

## Stakeholders

| Stakeholder | Interest |
| --- | --- |
| Police department (sponsor) | Faster, safer first contact; compliance; reputation |
| Field officers, SJPU officers | Simple, fast, reliable tool |
| Subjects and their families | Dignity, safety, privacy |
| Child Welfare Committee, DCPU | Correct handling of children; no harm |
| Deaf community, ISL experts (ISLRTC, Deaf associations) | Correct and respectful ISL |
| ISL generation team | Their service is integrated correctly |
| ML (sign recognition) team | Their model gets correct input |
| Department legal adviser | DPDP, POCSO, JJ Act compliance |
| CERT-In empanelled auditor | Auditable, secure app |

## Roles (RACI)

R = responsible, A = accountable, C = consulted, I = informed.

| Activity | Project lead | Tech lead | Security lead | Design lead | QA lead | Legal adviser | ISL/ML teams | Department |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Requirements | A | R | C | R | C | C | C | C |
| Architecture | I | A/R | C | I | I | I | C | I |
| Security controls | I | R | A | I | C | I | I | I |
| DPIA and compliance | A | C | R | I | I | R | I | C |
| UX and content | C | C | I | A/R | C | I | C | C |
| Integration | I | A/R | C | I | C | I | R | I |
| Testing | I | C | C | C | A/R | I | C | I |
| Audit | A | R | R | I | C | I | I | C |
| Release approval | R | C | C | I | C | C | I | A |

One person may hold several roles in a small team; the security lead should not also be the only reviewer of their own code.

## Success criteria (measured in the pilot)

| Measure | Target |
| --- | --- |
| Time from opening the app to the first triage question shown | Under 15 seconds |
| Officers who complete the triage flow without help after a 15-minute briefing | 90% |
| Triage answers understood correctly by the officer (checked against follow-up with interpreter) | 90% |
| Crashes per 100 sessions | Under 1 |
| Privacy incidents | Zero |
| App download size | Under 30 MB (excluding optional language packs) |

## Constraints

- Phase 1 must deliver a working app in 15 days.
- Oldest supported phone: Android 10, 3 GB RAM.
- Distributed as a signed APK through the Mobile Seva AppStore (MeitY) and department device management; zero licence or service cost (D-10).
- Data of minors and victims: DPDP Act, POCSO Act, Juvenile Justice Act apply.
- Government app: CERT-In security audit before production; GIGW 3.0 and IS 17802 accessibility.

## Assumptions

- The ISL team supplies keypoints for free text and a vocabulary list; the triage set can be recorded as video by a fluent signer [confirm].
- The ML team accepts landmark arrays rather than video [confirm].
- Bhashini access is granted for speech and translation [confirm].
- The department provides a legal adviser and a child-welfare reviewer [confirm].
