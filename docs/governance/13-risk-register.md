# 13. Risk Register

Likelihood (L) and impact (I): 1 low, 2 medium, 3 high. Score = L x I. Review weekly in phase 1, at each phase gate afterwards. Security threats in detail are in the [threat model](07-threat-model.md); privacy risks in the [DPIA](08-privacy-dpia.md).

| ID | Risk | Category | L | I | Score | Mitigation | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| R-01 | Team lacks Kotlin experience; phase 1 slips | Delivery | 2 | 3 | 6 | Decide D-01 in phase 0; pair on setup; cut order agreed | Tech lead | Open |
| R-02 | ISL payload late or different from sample | Dependency | 2 | 3 | 6 | Adapter layer; fakes; recorded clips cover triage | Tech lead | Open |
| R-03 | Sign recognition accuracy too low for use | Product | 3 | 2 | 6 | Small vocabulary; confidence; cards as main path | ML team | Open |
| R-04 | Skeleton avatar not understood by Deaf users | Product | 3 | 2 | 6 | Recorded clips for triage; ISL user testing; captions always | Design lead | Open |
| R-05 | Avatar or camera too slow on old phones | Technical | 2 | 2 | 4 | Test on oldest phone by day 5; reduce resolution and fps | Tech lead | Open |
| R-06 | Bhashini access delayed or quality poor for accents | Dependency | 2 | 2 | 4 | Typed input; officer confirms transcript; on-device ASR later | Tech lead | Open |
| R-07 | Legal position (DPDP exemptions) not confirmed before pilot | Legal | 2 | 3 | 6 | Request adviser in phase 0; DPIA as basis | Privacy lead | Open |
| R-08 | Partners or Bhashini retain data | Privacy | 2 | 3 | 6 | Written terms; minimise data; India hosting | Privacy lead | Open |
| R-09 | CERT-In audit finds critical issues late | Compliance | 2 | 3 | 6 | Build to MASVS L2 from day 1; internal review in phase 2 | Security lead | Open |
| R-10 | Wrong recognition leads to wrong decision about a child | Safety | 2 | 3 | 6 | SOP; confidence; never act on single recognition | Project lead | Open |
| R-11 | Distressing content shown to a child | Safety | 1 | 3 | 3 | Content policy; curated cards; no AI images | Design lead | Open |
| R-12 | App misused as statement-recording or surveillance tool | Misuse | 1 | 3 | 3 | Terms of use; SOP; training; no such features | Department | Open |
| R-13 | Translations of triage set inaccurate | Quality | 2 | 2 | 4 | Fluent reviewers; content policy | Content owner | Open |
| R-14 | Picture or symbol licences incompatible | Legal | 2 | 2 | 4 | Licence check per asset; record in licensing register | Design lead | Open |
| R-15 | Scope creep within 15 days | Delivery | 3 | 2 | 6 | Fixed cut order; decision log for any addition | Project lead | Open |
| R-16 | Officers do not adopt the app | Adoption | 2 | 3 | 6 | Pilot with SJPU; 15-minute briefing; quick card; feedback loop | Project lead | Open |
| R-17 | Signing key loss or compromise | Security | 1 | 3 | 3 | Two custodians; offline backup | Release manager | Open |
| R-18 | DPDP commencement dates changed by a new notification (none found as of 15 Oct 2026) | Legal | 1 | 2 | 2 | Design already meets safeguards; track MeitY notifications | Privacy lead | Open |
| R-19 | DPDP Rule 8(3) one-year minimum retention applies and conflicts with 24-hour deletion | Legal | 2 | 3 | 6 | Written opinion from the legal adviser before the pilot; retention period configurable (D-11) | Privacy lead | Open |
| R-20 | Subject does not know ISL (most deaf children, all hearing non-verbal children), so ISL output does not reach them | Product | 3 | 3 | 9 | Gesture lexicon, pictures, audio by subject profile (D-12, doc 23) | Design lead | Open |
| R-21 | Child character infringes or resembles a commercial cartoon | Legal | 2 | 3 | 6 | Original in-house design, VRoid preset items checked per licence, legal check before animation (doc 23 §4, §9) | Design lead | Open |
| R-22 | Subject leaves the app and sees the officer's other apps | Privacy | 2 | 2 | 4 | Screen pinning while the phone faces the subject; first run guides the officer to enable app pinning (D-19) | Tech lead | Open |
| R-23 | Family member who knows the personal phone's PIN opens a session | Privacy | 2 | 3 | 6 | End session after each conversation; auto-delete; SOP on unshared PIN; app-specific PIN if the pilot shows shared phones (D-19) | Privacy lead | Open |
| R-24 | No budget: volunteer performers, reviewers and interpreters not available | Delivery | 2 | 3 | 6 | Recruit through the department in phase 0; gesture cards and human triage clips first; interpreter button hidden where no contact exists | Project lead | Open |
| R-25 | Team lacks 3D skills; avatar clips slip | Delivery | 2 | 2 | 4 | VRoid Studio and Blender only; hand-keyframed gestures first; measure the first 20 clips (doc 23 §9) | Design lead | Open |
