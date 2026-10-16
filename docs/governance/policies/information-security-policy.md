# Information Security Policy: Sign Setu

**Applies to:** everyone who develops, operates, administers or supports the Sign Setu app and its backend, including partner teams and vendors.
**Owner:** Security lead. **Approved by:** Department CISO. **Review:** yearly and after any major incident.

## 1. Purpose

To protect the confidentiality, integrity and availability of Sign Setu and the personal data it handles, in line with the CERT-In Guidelines on Information Security Practices for Government Entities (2023) and the CERT-In Guidelines for Secure Application Design, Development, Implementation and Operations (2024).

## 2. Principles

1. Collect and keep the least data needed, for the shortest time.
2. Least privilege for people, services and the app.
3. Security is built in at every development stage, not added after audit.
4. Every change is reviewed, tested and traceable.

## 3. Access control

- Access to repositories, CI, cloud and distribution portals is role-based and granted by the project lead; reviewed every quarter (CERT-In 6.1.4).
- Multi-factor authentication is mandatory for all of these accounts.
- No shared accounts. Leavers lose access on their last day.
- Production cloud access only through named accounts; administrative actions are logged.

## 4. Devices of team members

- Full-disk encryption, screen lock, supported OS with security updates, and endpoint protection.
- No production secrets on personal devices.
- No real subject data on development devices; test data only.

## 5. Secrets and keys

- Secrets live only in the cloud secret manager or the designated key custody; never in code, chat, email or documents.
- Partner API keys rotated at least every 90 days and immediately on suspected exposure.
- Upload signing key held by the release manager with a second custodian.

## 6. Secure development

Follow [secure development and release](../11-devsecops-release.md): protected branches, peer review, SAST, dependency scanning, SBOM, signed releases. Every developer completes secure-coding orientation covering OWASP MASVS before writing production code (CERT-In app guidelines 3.3).

## 7. Third parties and partners

- From release 1.1, partners (ISL team, ML team, cloud provider, Bhashini) confirm in writing: where data is processed, that request data is not stored, security contacts and incident notification duties, and right to audit (CERT-In 8).
- Vendors supply an SBOM and inform of vulnerabilities within a reasonable time (CERT-In 8.5).

## 8. Data classification

| Class | Examples | Handling |
| --- | --- | --- |
| Restricted | Session data, exports, face and hand landmarks (memory only), second-screen traffic; voice from release 1.1 | Encrypted at rest and in transit; never in logs; on-phone only; landmarks never stored |
| Confidential | Keys, credentials, audit reports; gateway logs from release 1.1 | Need-to-know; encrypted; access logged |
| Internal | Source code, documents | Team access only |
| Public | Privacy notice, accessibility statement, store listing | Approved before publication |

## 9. Incidents

Report any suspected incident at once to the security lead. Incidents are handled and reported per [operations and incident response](../12-operations-incident-response.md), including reporting to CERT-In within six hours.

## 10. Compliance

Breaches of this policy are handled under department rules and the terms of engagement of the person or vendor concerned.
