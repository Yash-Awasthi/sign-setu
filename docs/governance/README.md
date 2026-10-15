# Governance Pack

Documents for the department, the legal adviser and the CERT-In empanelled auditor. They are not needed to write code.

Most of them were written against the earlier online design (gateway, camera recognition, skeleton avatar). Before the phase 2 exit, refresh each against the accepted offline design ([decisions](../07-decisions.md) D-12 to D-19):

| Document | Refresh needed |
| --- | --- |
| [07-threat-model.md](07-threat-model.md) | Re-run STRIDE on the [system design](../system-design.html) diagrams; network threats move to release 1.1 |
| [08-privacy-dpia.md](08-privacy-dpia.md) | Data inventory without camera, audio or processors; record the interpreter video call through Sandes or WhatsApp; DPDP Rule 8(3) opinion |
| [11-devsecops-release.md](11-devsecops-release.md) | Add the build-expiry setting and the no-INTERNET manifest check |
| [12-operations-incident-response.md](12-operations-incident-response.md) | Gateway sections apply from release 1.1; lost personal phone has no remote wipe |
| [13-risk-register.md](13-risk-register.md) | Close camera and gateway risks for release 1 |
| [14-compliance-matrix.md](14-compliance-matrix.md) | Re-map controls to the new SEC list in [security](../04-security.md) |
| [17-uat-scripts.md](17-uat-scripts.md) | Replace voice and sign-recognition scripts with gesture lookup, language finder and interpreter call |
| [18-user-guide.md](18-user-guide.md) | Officer and Show states, profiles, gesture lookup |
| [20-audit-scope.md](20-audit-scope.md) | Release 1 audit covers the app only; no gateway |
| [policies/privacy-notice.md](policies/privacy-notice.md) | Remove camera, voice and processor statements for release 1 |

Unchanged: [19-training-plan.md](19-training-plan.md), [21-accessibility-conformance-report.md](21-accessibility-conformance-report.md), the remaining [policies](policies/), [agreements](agreements/) and [forms](forms/).
