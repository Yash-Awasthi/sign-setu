# 08. Delivery Plan

Work proceeds in gated phases. A phase starts when the previous phase meets its exit criteria, or when the project lead waives a criterion and records the waiver in the [decision log](07-decisions.md).

| Phase | Objective | Deliverables | Exit criteria |
| --- | --- | --- | --- |
| 0. Foundations | Settle the design and remove external blockers | Approved design (D-12 to D-19); repository with protected main branch; emulator and test phone; legal adviser, ISL reviewer and child-welfare reviewer requested; volunteer signer and voice recorders requested | Decisions recorded; repository and devices ready; legal review requested |
| 1. Build | Working offline application | Subject profiles; the stage (code-drawn characters with the gesture lexicon, camera mirror, Yes, No, "I don't know", "Stop") and the officer panel, with pinning; interface for the partner hand model; triage questions with cards, captions and audio; picture board; gesture lookup with show-back; language finder; drawing and notes; encrypted session store and app lock; retention purge; PDF export; interpreter hand-off; build expiry | All MUST requirements in [requirements](01-requirements.md) pass on the API 29 emulator; security self-check against [security](04-security.md) |
| 2. Hardening | Complete controls and content | Full MAS-L2 control set; IS 17802 accessibility; content reviewed (language, ISL, child welfare); human ISL clips and animated gesture clips; threat model, DPIA and compliance matrix refreshed and signed | No open critical or high findings; release candidate frozen and tagged |
| 3. Security audit | Independent assurance | CERT-In empanelled audit of source and binary; remediation; re-test | Safe-to-host certificate; audited build hash recorded |
| 4. Pilot | Field validation | One district with trained officers, including the Special Juvenile Police Unit; incident and lost-phone drills; structured feedback | Success criteria met; no privacy incident; department approval |
| 5. Rollout | Scale and sustain | State-wise release through the Mobile Seva AppStore; release 1.1 language services on pilot evidence; periodic internal and external audits | Continuing |

## Cut order

If phase 1 runs short, cut in this order: phrase bank (FR-62), animated clips (keep static gesture cards), regional languages beyond Hindi and English. Never cut: picture board, Yes/No, encryption, app lock, pinning, purge.

## Phase 0 status

| Item | State |
| --- | --- |
| Design decisions D-12 to D-19 | Accepted |
| Repository | Local git with a private GitHub remote |
| Test devices | API 29 emulator being installed; real Android 10 phone later |
| Legal adviser, reviewers, volunteers | Not yet requested |
| Department `applicationId` and signing custody | Not yet confirmed |

## Success criteria for the pilot

| Measure | Target |
| --- | --- |
| Time from opening the app to the first question shown | Under 15 seconds |
| Officers completing the triage flow unaided after a short briefing | 90% |
| Answers understood correctly (checked later with an interpreter or family) | 90% |
| Crashes per 100 sessions | Under 1 |
| Privacy incidents | Zero |
