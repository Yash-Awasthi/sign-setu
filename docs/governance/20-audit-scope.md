# 20. Security Audit Scope (for the CERT-In empanelled auditor)

Annex for the audit work order. The auditor must be on the current CERT-In empanelment list.

## Targets

| Target | Type | Access provided |
| --- | --- | --- |
| Sign Setu Android app, release candidate `vX.Y.Z` (SHA-256 listed) | Source code review + black box | Repository at the tagged commit; signed APK; test phones |

Release 1 has no server: the app declares no INTERNET permission and sends nothing anywhere. The gateway, its cloud configuration, Bhashini and partner services join the scope with release 1.1. The partner hand model, when delivered, runs on the phone and is reviewed with the app against its [integration contract](../11-hand-model-integration.md).

## Standards

- OWASP MASVS v2 with the MASTG **MAS-L2** profile (and MAS-R checks for tampering)
- CERT-In Guidelines on Information Security Practices for Government Entities (2023), sections 6, 7, 9
- CERT-In Guidelines for Secure Application Design, Development, Implementation and Operations (2024)
- CERT-In Cyber Security Audit Baseline Requirements

## Focus areas

1. Data at rest: SQLCipher key handling, Keystore binding and invalidation on lock change, backup and device-transfer exclusion, export encryption.
2. No network: the merged manifest and the build gate that fails on INTERNET, RECORD_AUDIO or location permissions.
3. Camera mirror: preview only, nothing captured, stored or sent; the analysis path for the partner model.
4. Leakage: logs, screenshots, recents, clipboard, notifications, exported components, intents.
5. Subject isolation: screen pinning, return to the officer side only with the officer's credential, touch filtering against overlays in every window.
6. Integrity: content hash verification, build expiry, rooted-device warning, behaviour of repackaged apps.
7. Supply chain: SBOM completeness, vulnerable dependencies, dependency verification, build reproducibility of the tagged commit.

## Evidence provided to the auditor

[UX](../03-ux.md), [security](../04-security.md), [test plan](../06-test-plan.md), [test report](../10-test-report.md), [hand model contract](../11-hand-model-integration.md), [threat model](07-threat-model.md), [DPIA](08-privacy-dpia.md), [release process](11-devsecops-release.md); SBOM; SAST and dependency scan reports; internal MASTG results.

## Deliverables required

- Findings with severity, MASVS/MASTG reference, reproduction, remediation and closure timeline per severity.
- Follow-up audit confirming closure.
- Audit clearance / safe-to-host certificate naming the audited build hash.

## Rules of engagement

- Test only on the provided phones; no real case data exists or may be created.
- Findings shared only with the named project contacts and the department CISO.
