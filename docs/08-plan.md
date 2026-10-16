# 08. Release Path

The application is built (release 1, including the on-device assistant of D-24, the vision engine and sign reader of D-25, the second screen of D-26 and the decisions up to D-34). It reaches production through four gates. A gate starts when the previous one meets its exit criteria, or when the project lead waives a criterion and records the waiver in the [decision log](07-decisions.md).

| Gate | Objective | Work | Exit criteria |
| --- | --- | --- | --- |
| 1. Hardening | Complete content and field evidence | Content review by ISL, child-welfare and language reviewers through the reviewer portal ([record](governance/forms/content-review-record.md)); legal review of the rights wording in English and Hindi; sign reading and the second screen checked on real phones; timing and TalkBack runs on a real Android 10 phone; MASVS checklist; threat model, DPIA and compliance matrix signed | No open critical or high findings; release candidate frozen and tagged |
| 2. Security audit | Independent assurance | CERT-In empanelled audit of source and binary; remediation; re-test | Safe-to-host certificate; audited build hash recorded |
| 3. Pilot | Field validation | One district with trained officers, including the Special Juvenile Police Unit; incident and lost-phone drills; structured feedback | Success criteria met; no privacy incident; department approval |
| 4. Rollout | Scale and sustain | State-wise release through the Mobile Seva AppStore; the partner team's model, if it outperforms the bundled reader, swapped in behind `SignReader` or `LandmarkTracker` ([contract](11-hand-model-integration.md)); periodic internal and external audits | Continuing |

## Success criteria for the pilot

| Measure | Target |
| --- | --- |
| Time from opening the app to the first question shown | Under 15 seconds |
| Officers completing the triage flow unaided after a short briefing | 90% |
| Answers understood correctly (checked later with an interpreter or family) | 90% |
| Crashes per 100 sessions | Under 1 |
| Privacy incidents | Zero |
