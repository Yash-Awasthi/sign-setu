# Sign Setu: Documentation

Sign Setu is an offline Android app that helps a police officer communicate with a lost, rescued or kidnapped person who cannot communicate in words, often a child who is deaf, non-verbal, traumatised or speaks another language. Officer and subject share the officer's phone. The app is a communication aid, not an interpreter, and never records statements.

## Build documents

| File | Contents |
| --- | --- |
| [system-design.html](system-design.html) | Data flow diagrams (levels 0 to 2), data dictionary, trust boundaries, delivery phases |
| [01-requirements.md](01-requirements.md) | Purpose, users, subject profiles, constraints, functional and non-functional requirements |
| [02-architecture.md](02-architecture.md) | Technology, packages, content bundle, data model, storage, export, performance |
| [03-ux.md](03-ux.md) | Officer and Show states, profiles, flows, accessibility, content style |
| [04-security.md](04-security.md) | Security controls (MASVS L2, CERT-In), keys, threats in scope |
| [05-avatar-and-gestures.md](05-avatar-and-gestures.md) | Gesture and ISL lexicons, the two characters, clip pipeline, validation |
| [06-test-plan.md](06-test-plan.md) | Functional, performance, security, accessibility tests; testing with children |
| [07-decisions.md](07-decisions.md) | Decision log D-01 to D-21 |
| [08-plan.md](08-plan.md) | Gated delivery phases and phase 0 status |
| [09-open-source.md](09-open-source.md) | Reusable open-source components per module, with licences |
| [10-test-report.md](10-test-report.md) | Phase 1 test results against the test plan, with open items |
| [11-hand-model-integration.md](11-hand-model-integration.md) | Contract for the partner team's hand model: interface, input, output, budgets, packaging, acceptance |

## Other folders

| Folder | Contents |
| --- | --- |
| [governance/](governance/README.md) | Compliance pack for the department, legal adviser and CERT-In auditor: threat model, DPIA, compliance matrix, risk register, release process, incident response, UAT, user guide, training, audit scope, policies, agreements, forms |
| [archive/](archive/) | Superseded drafts and research, kept for reference |
| [tools/](tools/) | Generator for `system-design.html` |

## Conventions

- Requirement, control, threat, risk and decision IDs (FR-, NFR-, SEC-, T-, R-, D-) are stable. Withdrawn IDs are not reused.
- **[confirm]** marks anything that depends on a person or body outside the team.
- Legal summaries come from public sources and are not legal advice.

## Glossary

| Term | Meaning |
| --- | --- |
| Subject | The person the officer is helping |
| Session | One conversation, from start to end or purge |
| Officer state, Show state | The phone held by the officer, or facing the subject with the subject panel full screen |
| Subject profile | Young child, ISL user, hearing with another language, or not sure; decides what the subject sees and hears |
| Gesture lexicon | Reviewed iconic, culturally common gestures for people who do not sign |
| Show-back | Showing a candidate meaning to the subject for a Yes or No before it is logged |
| ISL | Indian Sign Language |
| CWC | Child Welfare Committee |
| SJPU | Special Juvenile Police Unit |
| DPDP | Digital Personal Data Protection Act 2023 and Rules 2025 |
| MASVS | OWASP Mobile Application Security Verification Standard |
