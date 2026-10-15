# 20. Security Audit Scope (for the CERT-In empanelled auditor)

Annex for the audit work order. The auditor must be on the current CERT-In empanelment list.

## Targets

| Target | Type | Access provided |
| --- | --- | --- |
| Sign Setu Android app, release candidate `vX.Y.Z` (SHA-256 listed) | Source code review + black box | Repository at the tagged commit; signed APK; test phones |
| API gateway, staging | Source code review + black box + API testing | Repository; staging URL; test credentials |
| Gateway cloud configuration | Configuration review | Read-only console access |

Partner services (ISL generation, sign recognition) and Bhashini are out of scope except their interface with the gateway; partners provide their own audit evidence.

## Standards

- OWASP MASVS v2 with the MASTG **MAS-L2** profile (and MAS-R checks for tampering and attestation)
- OWASP API Security Top 10
- CERT-In Guidelines on Information Security Practices for Government Entities (2023), sections 6, 7, 9
- CERT-In Guidelines for Secure Application Design, Development, Implementation and Operations (2024)
- CERT-In Cyber Security Audit Baseline Requirements

## Focus areas

1. Data at rest: SQLCipher key handling, Keystore binding, backup exclusion, export encryption.
2. Data in transit: pinning, TLS configuration, cleartext.
3. Key attestation flow and token issuance at the gateway; bypass attempts with emulators, rooted and repackaged apps.
4. Leakage: logs, screenshots, recents, clipboard, notifications, exported components, intents.
5. Gateway input validation, rate limiting, error handling, secret management, logging content.
6. Supply chain: SBOM completeness, vulnerable dependencies, build reproducibility of the tagged commit.

## Evidence provided to the auditor

Documents 03, 04, 06, 07, 08, 10, 11; SBOM; SAST and dependency scan reports; internal MASTG results.

## Deliverables required

- Findings with severity, MASVS/MASTG reference, reproduction, remediation and closure timeline per severity.
- Follow-up audit confirming closure.
- Audit clearance / safe-to-host certificate naming the audited build hash.

## Rules of engagement

- Test only staging and provided phones; no real case data exists or may be created.
- No denial-of-service testing against shared government services (Bhashini).
- Findings shared only with the named project contacts and the department CISO.
