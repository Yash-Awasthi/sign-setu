# 14. Compliance Matrix

Maps each external obligation to the control that meets it and the evidence an auditor will see. Controls are the SEC list in [security](../04-security.md); evidence IDs are results in the [test report](../10-test-report.md). Status: Planned, Implemented (in the build, not yet independently verified), Verified (passed a test in the report), N/A for release 1 (applies with the release 1.1 gateway). Items marked [confirm] need the legal adviser or department.

## 1. Law

| Source | Obligation | Control / document | Evidence | Status |
| --- | --- | --- | --- | --- |
| DPDP Act s8(5) | Reasonable security safeguards | All SEC controls | Security self-check (test report §7); audit certificate | Implemented |
| DPDP Act s8(7), Rule 8 | Erase data when purpose served | SEC-08, [retention policy](policies/data-retention-policy.md) | TC-16: retention job deletes the database and key | Verified |
| DPDP Rule 8(3), Seventh Schedule | Minimum one-year retention of personal data, traffic data and logs, if applicable | [DPIA](08-privacy-dpia.md) §4.1; R-19 | Legal opinion | Planned [confirm] |
| RPwD (Amendment) Rules 2026, draft of 20 July 2026 | Accessibility Conformance Report, if notified | [21](21-accessibility-conformance-report.md) per release | Completed report | Planned [confirm] |
| DPDP Act s9, Rule 12, Fourth Schedule Part B | Children: consent or exemption; no tracking or monitoring | No network, analytics or advertising (SEC-30, NFR-36); exemption analysis in [DPIA](08-privacy-dpia.md) §4 | ST-05; signed DPIA | Implemented [confirm] |
| DPDP Act s17(1) | Investigation exemption scope | DPIA §4 | Legal opinion | Planned [confirm] |
| DPDP Rule 7 | Breach notification to Board and data principals | [12](12-operations-incident-response.md) §4 | IR procedure, drill record | Planned |
| JJ Act 2015 s74 | No disclosure of child's identity | No identity fields; SEC-04, SEC-40, SEC-44; password-protected export (SEC-12) | ST-03, ST-04, ST-10 | Verified |
| POCSO Act s24, s26 | Statement procedure with interpreter / special educator; video recording | App not used for statements; [officer SOP](policies/officer-sop.md); in-app notice FR-52 | SOP, training record | Planned |
| RPwD Act 2016 s40, Rule 15 | ICT accessibility per IS 17802 | [UX](../03-ux.md) §5; NFR-20 to NFR-25 | AT-01 to AT-05; accessibility tests in CI | Implemented |
| IT Act 2000 s70B, CERT-In Directions 2022 | Report incidents in 6 hours; point of contact; logs 180 days in India; NTP sync | [12](12-operations-incident-response.md); release 1 keeps no server logs | Contact sheet, IR procedure | Planned (logs and NTP: N/A for release 1) |

## 2. Government standards and guidelines

| Source | Obligation | Control / document | Evidence | Status |
| --- | --- | --- | --- | --- |
| CERT-In govt guidelines 3.4 | Internal audit every 6 months; third-party yearly | [12](12-operations-incident-response.md) §8 | Audit reports | Planned |
| CERT-In govt guidelines 6.1.1 | Security in every SDLC phase | [11](11-devsecops-release.md) | CI checks: permission gate, lint, Semgrep, OSV, dependency verification, instrumented tests | Implemented |
| CERT-In govt guidelines 6.1.3 | Application inventory with custodian, version, criticality | Release record | Inventory entry | Planned |
| CERT-In govt guidelines 6.1.6, 6.1.10 | Server-side validation; API security tested | Release 1.1 gateway | DAST report | N/A for release 1 |
| CERT-In govt guidelines 6.1.7 | HTTPS with valid certificate | No network in release 1 (SEC-30) | ST-05 | N/A for release 1 |
| CERT-In govt guidelines 6.1.8 | Safe error handling | Plain messages, no stack traces to users; no API in release 1 | Code review | Implemented |
| CERT-In govt guidelines 6.1.9 | VA/PT yearly and on change | [11](11-devsecops-release.md) §7 | Audit reports | Planned |
| CERT-In govt guidelines 6.1.11 | Continuous log monitoring with alerts | Release 1.1 gateway | Alert configuration | N/A for release 1 |
| CERT-In govt guidelines 6.1.12 | Integrity checks on binaries | Signed APK with published SHA-256 (SEC-62); content hash check (SEC-54); key attestation from release 1.1 (SEC-61) | ST-12 | Implemented |
| CERT-In govt guidelines 6.2.1 | Address OWASP Mobile Top 10 | MASVS L2 controls | Security self-check; audit report | Implemented |
| CERT-In govt guidelines 6.2.2 | SSL pinning | No network in release 1 (SEC-30) | ST-05 | N/A for release 1 |
| CERT-In govt guidelines 6.2.3 | No unencrypted secret keys in app storage | SEC-11, SEC-22 | ST-08 | Verified |
| CERT-In govt guidelines 6.2.4 | No plain-text user data on device | SEC-01, SEC-02 | ST-02 | Verified |
| CERT-In govt guidelines 6.2.5 | No test code or debug logs; obfuscation | SEC-04, SEC-53, SEC-60 | ST-04, ST-08 | Verified |
| CERT-In govt guidelines 6.2.6 | Minimum permissions | SEC-30, SEC-42; build gate | ST-05 | Verified |
| CERT-In govt guidelines 6.2.7 | Sensitive data only over TLS | No network in release 1 (SEC-30) | ST-05 | N/A for release 1 |
| CERT-In govt guidelines 7.4, 7.10 | Backup policy | App data deliberately not backed up or transferred (SEC-03); gateway backups from release 1.1 | ST-01 | Verified |
| CERT-In govt guidelines 8.5 | SBOM for all components | SEC-51 | SBOM in release record | Implemented |
| CERT-In govt guidelines 9 | Secure cloud configuration | Release 1.1 gateway | Cloud config review | N/A for release 1 |
| CERT-In govt guidelines 10.1.13 | NIC or NPL NTP | Release 1.1 gateway | Server config | N/A for release 1 |
| CERT-In secure app guidelines 4.8 | SAST before production, DAST on live app | [11](11-devsecops-release.md) §3: Semgrep and Android Lint; DAST from release 1.1 | Scan reports | Implemented |
| CERT-In secure app guidelines 4.9 | Threat modelling with DFD | [system design](../system-design.html), [07](07-threat-model.md) | Documents | Implemented (draft) |
| CERT-In secure app guidelines 4.13 | Remove commented code; safe errors | Code review checklist | Review records | Planned |
| CERT-In secure app guidelines 4.15 | Evaluate third-party components; SBOM; key rotation | [11](11-devsecops-release.md) §4; SEC-50, SEC-51 | Pull request records, SBOM | Implemented |
| CERT-In secure app guidelines 5.2, 5.5 | Empanelled audit; secure design is precondition | [20](20-audit-scope.md) | Audit certificate | Planned |
| CERT-In secure app guidelines 6.1 | No change after audit without control | [11](11-devsecops-release.md) §6-7 | Release record | Planned |
| GIGW 3.0 | Accessibility, quality, security, lifecycle policies for government apps | [UX](../03-ux.md); [policies](policies/) | Policy set, accessibility report | Planned |
| IS 17802 (Parts 1 and 2) | ICT accessibility | [UX](../03-ux.md) §5; [21](21-accessibility-conformance-report.md) | AT-01 to AT-05 | Implemented |
| Policy on Adoption of Open Source Software (2015) | Prefer open source | [open source](../09-open-source.md) | SBOM licences | Implemented |
| Policy on Open APIs | Backward-compatible versioned APIs | Release 1.1 gateway | API docs | N/A for release 1 |

## 3. Industry standards

| Source | Obligation | Control / document | Status |
| --- | --- | --- | --- |
| OWASP MASVS v2, MAS-L2 profile | Mobile security verification | [security](../04-security.md) | Implemented; formal review before the audit |
| OWASP MASTG | Test methods | [test plan](../06-test-plan.md) §5 | Implemented |
| OWASP API Security Top 10 | Gateway API security | Release 1.1 gateway | N/A for release 1 |
| WCAG 2.1 AA | Accessibility (referenced by GIGW and IS 17802) | [UX](../03-ux.md) §5 | Implemented |

## 4. Platform

| Source | Obligation | Control | Status |
| --- | --- | --- | --- |
| Mobile Seva AppStore hosting requirements | Signed, verified APK | [11](11-devsecops-release.md) §8 | Planned |
| Store listing accuracy | Data disclosures match DPIA and privacy notice | Listing text | Planned |
| Android platform | Current target SDK, APK Signature Scheme v3 | SEC-55 (target 37), SEC-62 (signing key custody) | Implemented (target); Planned (signing key) |
