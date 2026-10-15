# 15. Phased Delivery Plan

The work runs in six phases. Each phase has an exit gate: the next phase starts only when every exit criterion is met or explicitly waived by the project lead, with the waiver recorded in the [decision log](../07-decisions.md).

| Phase | Name | Duration | Outcome |
| --- | --- | --- | --- |
| 0 | Foundations | 2-3 days, before coding | Documents, decisions, accounts and partner contracts in place |
| 1 | Minimum viable product | 15 days | Working app with mocks, then real services |
| 2 | Hardening | 3-4 weeks | All security, privacy and accessibility controls complete and self-tested |
| 3 | Audit and certification | 4-6 weeks | CERT-In empanelled audit passed, safe-to-host certificate |
| 4 | Pilot | 4-8 weeks | Real use by trained officers in one district, measured |
| 5 | Rollout and operations | Ongoing | Mobile Seva and MDM release, periodic audits, updates |

Durations for phases 2-5 depend on the department and auditor; they are estimates.

## Phase 0: Foundations

**Work**

1. Review and agree these documents within the team.
2. Settle the open decisions in the [decision log](../07-decisions.md): app platform (D-01), ISL clip recording (D-03).
3. Collect sample payloads from the ISL and ML teams and complete [integration contracts](09-api-contracts.md).
4. Apply for Bhashini API access.
5. Request a legal adviser from the department to review the [DPIA](../governance/08-privacy-dpia.md) and [compliance matrix](../governance/14-compliance-matrix.md).
6. Create the source repository with branch protection (see [document 11](../governance/11-devsecops-release.md)).
7. Identify test phones: the oldest supported model (Android 10, 3 GB RAM) plus one mid-range phone.
8. Agree the triage question set and picture-card list with a child-welfare contact.

**Exit criteria**

- [ ] D-01 (platform) and D-03 (ISL clips) decided
- [ ] Sample ISL keypoint payload received
- [ ] Repository created, protected main branch
- [ ] Test phones available
- [ ] Legal review requested (sign-off is a phase 2 exit criterion)

## Phase 1: Minimum viable product (15 days)

| Days | Work | Output |
| --- | --- | --- |
| 1 | Project setup (Kotlin, Compose, minSdk 29), split view with flip and focus mode, mock services, CI build | App runs on the oldest phone with the correct layout |
| 2-3 | Triage flow with recorded ISL clips, captions, curated picture board | Officer can ask preset questions offline |
| 4-5 | Skeleton avatar on Canvas from sample keypoints; frame-rate test on the oldest phone | Free text plays as a sign; performance measured |
| 6-7 | Voice input via Bhashini with officer confirmation, typed input, language switching | Officer can ask by voice in Hindi and English |
| 8-9 | CameraX and MediaPipe landmarks, landmark upload to the recognition mock, confidence display | Subject can sign back; officer sees result and confidence |
| 10 | Drawing canvas, notes, session log | Subject can draw; officer keeps notes |
| 11 | Encrypted storage, app lock, screenshot blocking, backup exclusion, auto-expiry, clear session | Baseline security controls in place |
| 12-13 | Connect real ISL, recognition and Bhashini services; timeouts, retries, fallbacks | End to end with real services |
| 14 | Device testing, accessibility pass, MASVS self-check | Defect list |
| 15 | Fixes, demo build, phase 2 handover notes | Demo build |

**Cut order if time runs short:** regional languages beyond Hindi and English, then drawing canvas, then skeleton avatar for free text. Never cut: picture board, fallbacks, encryption, clear session.

**Exit criteria**

- [ ] All MUST functional requirements in the [SRS](02-requirements-srs.md) work on the oldest test phone
- [ ] Cold start under 2 seconds and avatar at 24 fps or more on the oldest phone (NFR-01, NFR-03)
- [ ] Every external service has a working fallback (FR-40 to FR-43)
- [ ] No critical defects open

## Phase 2: Hardening

**Work**

1. Complete every control in the [security architecture](06-security-architecture.md) and close every high threat in the [threat model](../governance/07-threat-model.md).
2. Add SAST, dependency scanning and SBOM generation to CI.
3. Internal MASVS review against the MAS-L2 profile, using the [test plan](10-test-plan.md) security section.
4. Accessibility review against GIGW 3.0 and IS 17802 for the officer interface.
5. Content review: triage questions and translations by fluent speakers; ISL clips by a qualified ISL reviewer; wording and pictures by a child-welfare or counselling professional (see [content policy](../governance/policies/content-policy.md)).
6. Move backend services to an India-region, MeitY-empanelled cloud; configure logging, NTP and monitoring per [document 12](../governance/12-operations-incident-response.md).
7. Legal sign-off of the DPIA and compliance matrix.
8. Write the officer user guide and a one-page quick card.
9. Usability test with at least five people new to the app, and with at least one ISL user.

**Exit criteria**

- [ ] No open critical or high findings from internal review or SAST
- [ ] SBOM generated for the release candidate
- [ ] DPIA and compliance matrix signed by the legal adviser
- [ ] Content approved per the content policy
- [ ] Backend hosted in India with logs retained 180 days
- [ ] Code freeze for audit: release candidate tagged

## Phase 3: Audit and certification

**Work**

1. Engage a CERT-In empanelled auditor for source code review and black-box testing of the app and its APIs, against MASVS/MASTG, OWASP API Security and the CERT-In audit baseline.
2. Fix findings within the timelines set in the audit report; critical findings immediately.
3. Follow-up audit to confirm closure.
4. Obtain the safe-to-host / audit clearance certificate.
5. Accessibility conformance report ([document 21](../governance/21-accessibility-conformance-report.md)) for the audited build.

**Rule:** after the audit, no code or configuration change reaches production without a new audit decision (CERT-In secure application guidelines, phase IV). Any change goes through change control in [document 11](../governance/11-devsecops-release.md).

**Exit criteria**

- [ ] Audit clearance certificate received
- [ ] Audited build hash recorded in the release record

## Phase 4: Pilot

**Work**

1. Select one district and a small group of officers, ideally including SJPU officers.
2. Train officers with the [officer operating procedure](../governance/policies/officer-sop.md) and user guide.
3. Distribute through department device management or direct signed APK to the pilot group.
4. Collect feedback weekly; measure the success criteria in the [charter](01-project-charter.md).
5. Run one incident-response drill and one lost-phone drill.

**Exit criteria**

- [ ] Success criteria met or gaps understood
- [ ] No privacy incident during the pilot
- [ ] Department approval to roll out

## Phase 5: Rollout and operations

- Production release on the Mobile Seva AppStore and department MDM, with APK hashes published on a gov.in page.
- Internal security audit every 6 months; third-party audit at least once a year and after every significant change (CERT-In government guidelines 3.4 and 6.1.9).
- Dependency and vulnerability monitoring continuously; patch releases per [document 11](../governance/11-devsecops-release.md).
- Annual review of the DPIA, threat model and all policies.
- Later features, only when there is evidence of need: more regional languages, on-device speech recognition, integration with the case system.
