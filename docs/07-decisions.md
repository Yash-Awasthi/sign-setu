# 07. Decision Log

Each decision records context, the choice, alternatives and consequences. Decisions are changed by adding a new entry that supersedes the old one, not by editing history.

Status: Proposed, Accepted, Superseded.

---

## D-01 Native Android with Kotlin and Jetpack Compose

**Status:** Accepted 16 October 2026

**Context.** Production government app, distributed as an APK, oldest phone Android 10 with 3 GB RAM, on-device camera ML, strict security controls. The original plan chose React as a PWA for speed of development.

**Decision.** Build a native Android app in Kotlin with Jetpack Compose.

**Alternatives.**
- React PWA wrapped with Capacitor: fastest for a web team, but camera ML and the avatar run inside a WebView with more memory and lower frame rate; security controls need plugins.
- Flutter: good performance and smaller than React Native; acceptable second choice if the team knows Dart.
- React Native: largest builds of the cross-platform options in published comparisons.

**Consequences.** Smallest build, direct use of CameraX, MediaPipe, Keystore and key attestation. Costs 2-3 days if the team must learn Kotlin. No iOS (out of scope).

---

## D-02 Landmarks on device; only landmarks leave the phone

**Status:** Superseded by D-14 (release 1 has no camera). If recognition returns after the pilot, it runs entirely on the device.

**Context.** Sign recognition needs the subject's hands and body. Video of a child is highly sensitive and heavy on the network.

**Decision.** Run MediaPipe hand and pose landmarkers on the phone; send only landmark arrays, only when hands are visible.

**Consequences.** Lower privacy risk and bandwidth. The ML team must train on MediaPipe Tasks landmarks. Recognition quality is bounded by landmark quality.

---

## D-03 Recorded ISL clips for the triage set; skeleton avatar only for free text

**Status:** Accepted 16 October 2026 for human ISL clips on the triage set; the skeleton avatar is superseded by D-13. Needs a volunteer signer and reviewer.

**Context.** Studies show sentence comprehension from avatars is substantially lower than from human signers; ISL depends on facial grammar a skeleton may not show.

**Decision.** Record a fluent signer for every triage question; bundle the clips. Use the skeleton avatar only for free text.

**Consequences.** Better comprehension and full offline support for the most important flow. Needs recording and review time; clip size must stay small.

---

## D-04 Bhashini for speech recognition and translation

**Status:** Accepted for release 1.1 (D-15), subject to access and retention terms

**Context.** Need Hindi, English and regional languages; minors' audio should not go to foreign commercial providers.

**Decision.** Use Bhashini through the gateway. Consider on-device recognition later.

**Consequences.** Government-run, Indian-language focused. Dependence on Bhashini availability; retention terms must be confirmed.

---

## D-05 No AI image generation in v1

**Status:** Accepted

**Context.** The original plan included AI-generated images.

**Decision.** Remove it from v1. Use a curated picture set.

**Reasons.** Sends case details to an image service; risk of distressing or wrong images shown to traumatised children; latency and cost; curated cards cover the scenarios.

**Consequences.** Smaller scope; content team must curate a sufficient card set.

---

## D-06 Session data stored only on the phone, encrypted, short-lived

**Status:** Accepted

**Decision.** No server-side storage of sessions. SQLCipher with a Keystore-wrapped key; auto-delete after 24 hours by default; encrypted export to the official record.

**Consequences.** Minimal breach surface; no sync across devices; the official case system remains the record.

---

## D-07 Backend in India, behind a single project gateway

**Status:** Accepted for release 1.1 (D-15)

**Decision.** All partner calls go through a project-owned gateway hosted in an India region of a MeitY-empanelled cloud before production. Partner keys live only in the gateway.

**Consequences.** One pinned host in the app; keys out of the app; one place for logging and rate limiting. The gateway is an extra component to build and operate.

---

## D-08 No officer login in v1

**Status:** Accepted

**Context.** No department identity system integration is available within 15 days.

**Decision.** Protect the app with the device credential and biometrics; authenticate the app to the gateway with Android Key Attestation. Revisit with department SSO (for example Parichay) after the pilot.

**Consequences.** No per-officer accountability on the server side; the session log on the phone is the record.

---

## D-09 No third-party analytics or crash reporting SDKs

**Status:** Accepted

**Decision.** No remote crash reporting; crash traces stay on the phone and are shared by officers on request.

**Consequences.** Less product telemetry; pilot feedback is gathered by interviews and forms.

---

## D-10 Zero licence and service cost

**Status:** Accepted

**Context.** The project must run with no paid software, services or accounts.

**Decision.** Use only free and open-source tools and free government services.

| Need | Free choice | Replaces |
| --- | --- | --- |
| Distribution | Mobile Seva AppStore (MeitY, free for departments), department MDM, signed APK on a gov.in page | Google Play (one-time fee) |
| App attestation | Android Key Attestation verified by the gateway | Play Integrity (needs Play) |
| Crash data | Local crash traces shared on request | Play Console vitals |
| Speech, translation | Bhashini | Commercial cloud APIs |
| Hosting (production) | NIC / MeghRaj government cloud through the department [confirm charges] | Commercial cloud |
| Hosting (development) | Team machine or a free-tier VM | Paid VM |
| TLS certificates | Let's Encrypt with a reused key, so the public-key pin survives renewal | Paid certificates |
| CI | GitHub Actions free tier or a self-hosted runner | Paid CI |
| Security tooling | Android Lint, Semgrep CE, MobSF, OSV-Scanner, OWASP ZAP, CycloneDX Gradle plugin, gitleaks | Commercial scanners |
| Libraries and models | Apache-2.0 / MIT / BSD (Compose, CameraX, MediaPipe, Room, SQLCipher Community, OkHttp, Media3) | |
| Fonts, symbols | Noto (OFL), Mulberry and OpenMoji (CC BY-SA 4.0), Jellow (permission needed), ARASAAC (non-commercial) | Paid symbol sets |

**Not free, and outside the team's control:** the CERT-In empanelled audit (paid to the auditor by the department; no free route exists), production hosting if NIC charges the department, and people's time (ISL signer, reviewers). Reduce audit cost by arriving with clean internal MASTG results so only one follow-up round is needed.

**Consequences.** No Play vitals, staged rollout or in-app updates; replaced by MDM, district-wise rollout and a gateway-enforced minimum version.

---

## D-11 National scale

**Status:** Accepted

**Decision.**
- Gateway is stateless and horizontally scalable; capacity planned per state as states join.
- Content (triage clips, captions, cards, audio) is packaged per language. Release 1 bundles every approved language in the APK because it has no network (D-15); downloadable packs are reconsidered in release 1.1 if the size budget is exceeded.
- Rollout state by state, district by district, each with trained trainers.
- Configuration per state limited to: languages, helpline numbers, auto-delete period. No per-state code.
- Bhashini quota and the partners' capacity confirmed before each state joins.

---

## D-12 Subject profiles decide what the subject sees and hears

**Status:** Accepted 16 October 2026 (see [critical review](archive/22-critical-review.md) C-1, C-7)

**Context.** Most deaf children do not know ISL; hearing children may speak another language and be unable to read. One ISL-first output serves neither.

**Decision.** The officer picks a subject profile at the start with one tap, changeable at any time: *Young child* (gestures, pictures, audio), *ISL user* (human ISL clips, adult character, interpreter call), *Hearing, other language* (language finder, recorded audio, pictures), *Not sure* (pictures, Yes/No, drawing). Pictures and Yes/No are present in every profile.

**Consequences.** Content is organised by profile; the triage set needs captions, audio, gesture entries and ISL clips per question.

---

## D-13 Two original characters with a fixed, reviewed lexicon replace the skeleton avatar

**Status:** Accepted 16 October 2026; supersedes the skeleton part of D-03 (see [avatar design](05-avatar-and-gestures.md))

**Context.** A skeleton has no face, so it cannot show ISL facial grammar, and may frighten children. Free text converted word by word into signs is not ISL and cannot be reviewed before a child sees it.

**Decision.** A child character and an adult character share one rig and one motion library. They play only reviewed entries from the gesture lexicon and the ISL lexicon, including a phrase bank of sentences with slots. Free text is shown as caption, pictures and audio, never signed. Delivered in stages: illustrated gesture cards in phase 1, pre-rendered clips in phase 2, real-time rendering only if the pilot shows the need. Characters are original; no commercial cartoon character is copied. The adult character has a simplified but expressive face; a blank "mask" face was rejected because it loses ISL facial grammar. With no budget, the team builds both characters in-house with VRoid Studio and Blender (see avatar design §9).

**Consequences.** Needs team time to learn the tools, plus volunteer performers and reviewers. The ISL team's work becomes build-time clip data instead of a runtime API. Human ISL clips stay for the triage set.

---

## D-14 Officer gesture lookup replaces camera sign recognition in v1

**Status:** Accepted 16 October 2026 (see critical review C-4, C-5)

**Context.** Public ISL datasets are adult signers; children's home signs are in no dataset. One phone's front camera cannot watch the subject while the officer reads the screen. A false "yes" is the worst failure the app can produce.

**Decision.** In v1 the officer describes a gesture (where on the body, what the hand did), picks a likely meaning, and confirms it by showing the gesture back to the subject. Camera recognition moves to LATER, for ISL users only, runs on-device with the rear camera, and always ends with the same show-back confirmation.

**Consequences.** No camera, no model and no landmark upload in v1; DPIA risk P-04 and threat T-30 drop. D-02 applies only when recognition returns.

---

## D-15 v1 works fully offline, with no INTERNET permission

**Status:** Accepted 16 October 2026 (see critical review H-1, H-7)

**Context.** The network features (free-text signing, server recognition, voice, translation) carry most of the security, audit and partner-agreement cost, and the first two are replaced by D-13 and D-14.

**Decision.** v1 ships without the INTERNET permission. Voice input through Bhashini, free-text caption translation, the gateway and attestation arrive in v1.1, after the pilot shows they are needed.

**Consequences.** Threats T-07 to T-13 and DPIA risk P-03 do not apply to v1; the first CERT-In audit covers the app only; phase 1 fits in 15 days. Officers type instead of speaking in v1. D-04 and D-07 apply from v1.1.

---

## D-16 Call an interpreter

**Status:** Accepted 16 October 2026 (see critical review C-6)

**Context.** Bhopal and Indore police already run video-call helplines for deaf people with officers trained in sign language. A human interpreter outperforms any feature of this app for an ISL user.

**Decision.** A **Call an interpreter** action, always one tap away, opens a **video** call (sign language cannot be interpreted over a voice call) to the state's configured interpreter contact, in the app the state configures: Sandes, the NIC government messenger, whose video calls are available to organisation-verified users and the people they invite; otherwise WhatsApp, which most officers' personal phones already have and which the Bhopal and Indore helplines use. No call audio or video passes through Sign Setu. If a state has configured no contact, the button is hidden.

**Consequences.** Each state names who answers, at what hours: officers trained in ISL (the Bhopal model), a Deaf association or ISLRTC volunteers. This costs no money but needs the department's agreement. The child's video passes through the messenger's servers during the call (end-to-end encrypted in both), which the DPIA must record.

---

## D-17 Export as a password-protected PDF

**Status:** Accepted 16 October 2026 (see critical review H-2)

**Context.** The custom AES-256-GCM export in SEC-12 cannot be opened by a CWC member or a station writer without special software.

**Decision.** Export a PDF encrypted with AES-256 under a strong one-time passphrase shared out of band. Any PDF reader opens it. SEC-12 is amended accordingly.

**Consequences.** Adds one library (see [reuse map](09-open-source.md)). PDF password key derivation is weaker than Argon2id, so the passphrase must be long and random: 16 characters from a 32-symbol alphabet without look-alike characters (80 bits).

---

## D-18 Language finder for hearing children who speak another language

**Status:** Accepted 16 October 2026 (in scope; see critical review C-7)

**Context.** Rescued and trafficked children often speak Bengali, Nepali, Assamese, Odia or a tribal language and cannot read; captions do not reach them.

**Decision.** The subject sees language tiles; each plays a short recorded greeting in one language, so the child can show which one they understand. The triage set is then played as recorded audio in that language, with pictures.

**Consequences.** Mostly content work: recorded and reviewed audio per language, offline. Language packs follow D-11.

---

## D-19 The app runs on officers' personal phones

**Status:** Accepted 16 October 2026

**Context.** Officers will install the app on their own phones. There is no mobile device management, and a personal phone may be old, unpatched, rooted, or shared with family members who know its PIN.

**Decision.**
- No reliance on MDM: no remote wipe, no enforced settings. Encryption, app lock, End session and auto-delete are the only data controls on a lost phone.
- First run guides the officer to turn on the phone's app-pinning setting, used while the phone faces the subject (critical review H-9).
- Every build stops starting new sessions a fixed period after its build date (default 180 days) and asks for an update, because an offline v1 (D-15) has no gateway to enforce a minimum version. Existing sessions can still be exported and cleared.
- Rooted phones get a warning (SEC-63); there is no attestation in an offline v1.
- The SOP requires a phone PIN that is not shared, End session after every conversation, and Clear all data and uninstall on transfer or retirement.

**Consequences.** A family member who knows the phone PIN can open sessions that have not been ended or auto-deleted (R-23). If the pilot shows that phones are commonly shared, add an app-specific PIN.
