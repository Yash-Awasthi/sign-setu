# 12. Operations and Incident Response

Covers monitoring, logging, incident response, breach notification, backup and continuity for the gateway and the app. Based on the CERT-In Directions of 28 April 2022 and the CERT-In Guidelines on Information Security Practices for Government Entities (2023), sections 7, 14 and 15.

## 1. Roles

| Role | Responsibility |
| --- | --- |
| Department CISO | Point of contact with CERT-In; owns incident reporting |
| Operations lead | Runs the gateway, monitoring, backups |
| Security lead | Triage of security events, forensics coordination |
| Privacy lead | Assesses personal data impact; Data Protection Board and data principal notifications |
| Project lead | Communication with department, partners, CWC |

Keep a current contact sheet (names, phones, backups) outside the systems it covers.

## 2. Logging and monitoring

| Item | Requirement |
| --- | --- |
| What is logged (gateway) | Time, request ID, route, status, latency, size, salted install hash, integrity verdict, source IP |
| What is never logged | Request or response bodies, text, audio, landmarks, names |
| Retention | Rolling 180 days, stored in India (CERT-In Directions 2022) |
| Time | All servers synced to NIC NTP (samay1.nic.in, samay2.nic.in) or NPL (time.nplindia.org) |
| Integrity | Logs shipped to a separate, append-only store with restricted access (CERT-In govt guidelines, server checklist) |
| Alerts | Error rate, latency, integrity failures, rate-limit hits, admin logins, configuration changes |
| Review | Alerts continuous; manual review weekly during pilot, monthly after |
| App | No remote logging. Crash stack traces (no personal data) kept on the phone; officers share them on request |

## 3. Incident classification

| Severity | Examples | Response start |
| --- | --- | --- |
| Critical | Confirmed leak of subject data; compromised signing key; malicious update | Immediately |
| High | Gateway compromise suspected; partner key leaked; vulnerability exploitable remotely | Within 1 hour |
| Medium | Service outage over 1 hour; vulnerability needing local access | Same day |
| Low | Single-user issue, minor bug with no data impact | Next working day |

## 4. Response procedure

1. **Detect and record.** Open an incident record: time noticed, who noticed, what is known. Time noticed starts the reporting clock.
2. **Assess.** Security lead decides severity; privacy lead assesses personal data impact.
3. **Report to CERT-In within 6 hours of noticing** any reportable cyber security incident (CERT-In Directions 2022), through the department CISO, using CERT-In's incident reporting form or email.
4. **Contain.** Examples: rotate leaked keys; block abusive installs at the gateway; pull a bad release with a halted rollout; push a forced update; revoke a certificate pin with the backup pin.
5. **Preserve evidence.** Snapshot logs and affected systems before changes where possible.
6. **Eradicate and recover.** Fix root cause; restore service; verify.
7. **Notify** as required: Data Protection Board and affected data principals under DPDP Rule 7 once in force; the Child Welfare Committee where a child's data is involved [confirm procedure]; partners if their systems are affected.
8. **Review.** Written post-incident report within 10 working days: timeline, root cause, actions, owners (CERT-In govt guidelines 14.4).

## 5. App-specific playbooks

| Situation | Action |
| --- | --- |
| Officer's phone lost or stolen | Officer reports to supervisor at once. If MDM is used: remote lock and wipe. Otherwise the data is protected by encryption and device lock and is deleted at auto-delete expiry on next run; record the incident; assess whether any export is also exposed. |
| Export file sent to the wrong person | Ask for deletion; the file is encrypted and the passphrase was shared separately, so assess whether the passphrase also leaked. |
| Malicious or broken app version | Halt district rollout; release fixed version; raise minimum version at the gateway so old builds stop calling services. |
| Partner service compromise | Disable the route at the gateway; app falls back to offline features. |
| Signing or upload key compromise | Rotate the signing key (APK Signature Scheme v3), push via MDM and Mobile Seva; audit releases since suspected compromise. |

## 6. Backup and continuity

- **App data:** deliberately not backed up (SEC-03). Continuity comes from export to the official case record.
- **Gateway:** stateless; infrastructure defined as code so it can be rebuilt in another zone within 4 hours. Configuration and infrastructure code backed up daily to a separate location (CERT-In govt guidelines 7.10).
- **Logs:** replicated within India; 180-day retention survives zone loss.
- **Partners:** each partner documents its own recovery plan [confirm].
- **Offline core:** if the whole backend is down, triage, cards and drawing keep working (FR-40). This is the main continuity control for officers in the field.

## 7. Contingency management

| Event | Plan |
| --- | --- |
| Gateway outage | Status shown in app; officers use offline features; operations restores within the target time |
| Bhashini outage | Voice and translation unavailable; typed input and preset content continue |
| Mobile Seva unavailable | Department MDM or direct signed APK with published SHA-256 |
| Key team member unavailable | Two people know each critical procedure (release, key custody, incident reporting) |

## 8. Audits and drills

- Internal security audit every 6 months; third-party CERT-In empanelled audit at least yearly and after significant change (CERT-In govt guidelines 3.4).
- Incident response tabletop exercise before the pilot and yearly.
- Lost-phone drill in the pilot.
- Restore test of gateway infrastructure every 6 months.
