# 14. Compliance Matrix

Maps each external obligation to the control that meets it and the evidence an auditor will see. Status: Planned, Implemented, Verified, N/A. Items marked [confirm] need the legal adviser or department.

## 1. Law

| Source | Obligation | Control / document | Evidence | Status |
| --- | --- | --- | --- | --- |
| DPDP Act s8(5) | Reasonable security safeguards | [06](../archive/06-security-architecture.md) all controls | Security test report, audit certificate | Planned |
| DPDP Act s8(7), Rule 8 | Erase data when purpose served | SEC-08, [retention policy](policies/data-retention-policy.md) | TC-13 result | Planned |
| DPDP Rule 8(3), Seventh Schedule | Minimum one-year retention of personal data, traffic data and logs, if applicable | [DPIA](08-privacy-dpia.md) §4.1; R-19 | Legal opinion | Planned [confirm] |
| RPwD (Amendment) Rules 2026, draft of 20 July 2026 | Accessibility Conformance Report, if notified | [21](21-accessibility-conformance-report.md) per release | Completed report | Planned [confirm] |
| DPDP Act s9, Rule 12, Fourth Schedule Part B | Children: consent or exemption; no tracking or monitoring | No tracking (SEC-72); exemption analysis in [DPIA](08-privacy-dpia.md) §4 | Signed DPIA | Planned [confirm] |
| DPDP Act s17(1) | Investigation exemption scope | DPIA §4 | Legal opinion | Planned [confirm] |
| DPDP Rule 7 | Breach notification to Board and data principals | [12](12-operations-incident-response.md) §4 | IR procedure, drill record | Planned |
| JJ Act 2015 s74 | No disclosure of child's identity | No identity fields; SEC-04, SEC-40, SEC-44; controlled export | Security tests ST-03, ST-04 | Planned |
| POCSO Act s24, s26 | Statement procedure with interpreter / special educator; video recording | App not used for statements; [officer SOP](policies/officer-sop.md); in-app notice FR-52 | SOP, training record | Planned |
| RPwD Act 2016 s40, Rule 15 | ICT accessibility per IS 17802 | [05](../archive/05-design-ux.md) §5; NFR-20 to NFR-26 | Accessibility test report | Planned |
| IT Act 2000 s70B, CERT-In Directions 2022 | Report incidents in 6 hours; logs 180 days in India; NTP sync; point of contact | [12](12-operations-incident-response.md) | Log config, contact sheet | Planned |

## 2. Government standards and guidelines

| Source | Obligation | Control / document | Evidence | Status |
| --- | --- | --- | --- | --- |
| CERT-In govt guidelines 3.4 | Internal audit every 6 months; third-party yearly | [12](12-operations-incident-response.md) §8 | Audit reports | Planned |
| CERT-In govt guidelines 6.1.1 | Security in every SDLC phase | [11](11-devsecops-release.md) | CI configuration, review records | Planned |
| CERT-In govt guidelines 6.1.3 | Application inventory with custodian, version, criticality | Release record | Inventory entry | Planned |
| CERT-In govt guidelines 6.1.6, 6.1.10 | Server-side validation; API security tested | SEC-33; ST-11 | DAST report | Planned |
| CERT-In govt guidelines 6.1.7 | HTTPS with valid certificate | SEC-30, SEC-32 | ST-05 | Planned |
| CERT-In govt guidelines 6.1.8 | Safe error handling | [09](../archive/09-api-contracts.md) §1 error format | ST-11 | Planned |
| CERT-In govt guidelines 6.1.9 | VA/PT yearly and on change | [11](11-devsecops-release.md) §7 | Audit reports | Planned |
| CERT-In govt guidelines 6.1.11 | Continuous log monitoring with alerts | [12](12-operations-incident-response.md) §2 | Alert configuration | Planned |
| CERT-In govt guidelines 6.1.12 | Integrity checks on binaries | SEC-61 | ST-06 | Planned |
| CERT-In govt guidelines 6.2.1 | Address OWASP Mobile Top 10 | MASVS L2 controls | Audit report | Planned |
| CERT-In govt guidelines 6.2.2 | SSL pinning | SEC-31 | ST-05 | Planned |
| CERT-In govt guidelines 6.2.3 | No unencrypted secret keys in app storage | SEC-11, SEC-34 | ST-08 | Planned |
| CERT-In govt guidelines 6.2.4 | No plain-text user data on device | SEC-01, SEC-02 | ST-02 | Planned |
| CERT-In govt guidelines 6.2.5 | No test code or debug logs; obfuscation | SEC-04, SEC-53, SEC-60 | ST-04, ST-08 | Planned |
| CERT-In govt guidelines 6.2.6 | Minimum permissions | SEC-42 | Manifest review | Planned |
| CERT-In govt guidelines 6.2.7 | Sensitive data only over TLS | SEC-30 | ST-05 | Planned |
| CERT-In govt guidelines 7.4, 7.10 | Backup policy | [12](12-operations-incident-response.md) §6 | Restore test | Planned |
| CERT-In govt guidelines 8.5 | SBOM for all components | SEC-51 | SBOM in release record | Planned |
| CERT-In govt guidelines 9 | Secure cloud configuration | [06](../archive/06-security-architecture.md) §4 | Cloud config review | Planned |
| CERT-In govt guidelines 10.1.13 | NIC or NPL NTP | [12](12-operations-incident-response.md) §2 | Server config | Planned |
| CERT-In secure app guidelines 4.8 | SAST before production, DAST on live app | [11](11-devsecops-release.md) §3 | Scan reports | Planned |
| CERT-In secure app guidelines 4.9 | Threat modelling with DFD | [04](../archive/04-data-flow-diagrams.md), [07](07-threat-model.md) | Documents | Implemented (draft) |
| CERT-In secure app guidelines 4.13 | Remove commented code; safe errors | Code review checklist | Review records | Planned |
| CERT-In secure app guidelines 4.15 | Evaluate third-party components; SBOM; key rotation | [11](11-devsecops-release.md) §4; SEC-34 | PR records, SBOM | Planned |
| CERT-In secure app guidelines 5.2, 5.5 | Empanelled audit; secure design is precondition | [15](../archive/15-phased-plan.md) phase 3 | Audit certificate | Planned |
| CERT-In secure app guidelines 6.1 | No change after audit without control | [11](11-devsecops-release.md) §6-7 | Release record | Planned |
| GIGW 3.0 | Accessibility, quality, security, lifecycle policies for government apps | [05](../archive/05-design-ux.md); [policies](policies/) | Policy set, accessibility report | Planned |
| IS 17802 (Parts 1 and 2) | ICT accessibility | [05](../archive/05-design-ux.md) §5 | Accessibility report | Planned |
| Policy on Adoption of Open Source Software (2015) | Prefer open source | [03](../archive/03-architecture.md) §3 | SBOM licences | Implemented |
| Policy on Open APIs | Backward-compatible versioned APIs | [09](../archive/09-api-contracts.md) §7 | API docs | Planned |

## 3. Industry standards

| Source | Obligation | Control / document | Status |
| --- | --- | --- | --- |
| OWASP MASVS v2, MAS-L2 profile | Mobile security verification | [06](../archive/06-security-architecture.md) | Planned |
| OWASP MASTG | Test methods | [10](../archive/10-test-plan.md) §5 | Planned |
| OWASP API Security Top 10 | Gateway API security | SEC-33, ST-11 | Planned |
| WCAG 2.1 AA | Accessibility (referenced by GIGW and IS 17802) | [05](../archive/05-design-ux.md) §5 | Planned |

## 4. Platform

| Source | Obligation | Control | Status |
| --- | --- | --- | --- |
| Mobile Seva AppStore hosting requirements | Signed, verified APK | [11](11-devsecops-release.md) §8 | Planned |
| Store listing accuracy | Data disclosures match DPIA and privacy notice | Listing text | Planned |
| Android platform | Current target SDK, APK Signature Scheme v3 | SEC-55, SEC-62 | Planned |
