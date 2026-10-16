# Sign Setu: Documentation

Sign Setu is an offline Android app that helps a police officer communicate with a person who cannot communicate in words: deaf, non-verbal, traumatised or speaking another language; a victim, a witness or an accused person; a child or an adult. Officer and subject share the officer's phone, or the subject uses a paired second screen. The app is a bridge for first contact, not an interpreter, and never records statements.

## Build documents

| File | Contents |
| --- | --- |
| [system-design.html](system-design.html) | Data flow diagrams (levels 0 to 2), data dictionary, trust boundaries, delivery phases |
| [01-requirements.md](01-requirements.md) | Purpose, users, subject profiles, constraints, functional and non-functional requirements |
| [02-architecture.md](02-architecture.md) | Technology, packages (including `sign`, `pair` and the flavor source sets), content bundle, data model, storage, export, performance, second-screen protocol, reviewer portal |
| [03-ux.md](03-ux.md) | Officer and Show states, profiles, flows, accessibility, content style |
| [04-security.md](04-security.md) | Security controls (MASVS L2, CERT-In), keys, threats in scope |
| [05-avatar-and-gestures.md](05-avatar-and-gestures.md) | Gesture and ISL lexicons, the two characters, clip pipeline, validation |
| [06-test-plan.md](06-test-plan.md) | Functional, performance, security, accessibility tests; testing with children |
| [07-decisions.md](07-decisions.md) | Decision log D-01 to D-34 |
| [08-plan.md](08-plan.md) | Release path: the four gates to production, and pilot success criteria |
| [09-open-source.md](09-open-source.md) | Reusable open-source components per module, with licences |
| [10-test-report.md](10-test-report.md) | Test results against the test plan, for the first build and the integrated build, with open items |
| [11-hand-model-integration.md](11-hand-model-integration.md) | The built vision engine and sign reader, sign windows, and the path for the partner team's model to replace them |
| [12-on-device-assistant.md](12-on-device-assistant.md) | The optional on-device translation assistant: model, installation, budgets |
| [13-product-overview.md](13-product-overview.md) | One reference for explaining the product: features, mechanisms, character, sign reading, export, stack, frontend, readiness |
| [14-presentation-script.md](14-presentation-script.md) | Spoken walk-through with demonstration steps and likely questions |

## Other folders

| Folder | Contents |
| --- | --- |
| [governance/](governance/README.md) | Compliance pack for the department, legal adviser and CERT-In auditor: threat model, DPIA, compliance matrix, risk register, release process, incident response, UAT, user guide, training, audit scope, policies, agreements, forms |
| [tools/](tools/) | Generator for `system-design.html` |
| [../portal/](../portal/README.md) | Reviewer portal for content sign-off (static web page; not shipped in the app) |

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
| Show-back | Showing a candidate meaning to the subject, who picks it among alternatives or answers Yes or No, before it is logged as theirs |
| Sign window | The only time camera frames are analysed: a 20-second answer or a 3-minute account |
| Evidence grade | Said, Confirmed, Officer's reading, App's reading, Said no or Unclear, on every answer line |
| Stage device | A paired second phone or tablet that shows the stage and stores nothing |
| Role | Not known yet, victim or witness, or accused or in custody |
| BNSS, BSA | Bharatiya Nagarik Suraksha Sanhita 2023; Bharatiya Sakshya Adhiniyam 2023 |
| ISL | Indian Sign Language |
| CWC | Child Welfare Committee |
| SJPU | Special Juvenile Police Unit |
| DPDP | Digital Personal Data Protection Act 2023 and Rules 2025 |
| MASVS | OWASP Mobile Application Security Verification Standard |
