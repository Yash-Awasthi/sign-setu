# 22. Critical Review and Fact Check

Review date: 15 October 2026. Scope: documents 01-21, all policies, agreements and forms, and the two background documents the README cites. Each factual claim was checked against the primary source where one could be reached, otherwise against the best secondary source; the result and source are recorded below. The design was then reviewed against how the app will actually be used in the field, with the harshest reading taken on purpose.

Fixes to plain factual errors have been made in the affected documents (listed in section 1). Changes that alter scope or design are recorded as **Proposed** decisions D-12 to D-18 in the [decision log](../07-decisions.md) and are not yet applied to the SRS, architecture or plan. The avatar and gesture design is in [document 23](../05-avatar-and-gestures.md); reusable open-source parts are in [document 24](../09-open-source.md).

## 1. Fact check

| ID | Claim (where) | Result | Action |
| --- | --- | --- | --- |
| F-01 | DPDP Rules notified 13 Nov 2025; most obligations from 13-14 May 2027 (08) | Correct. Rules G.S.R. 846(E); commencement G.S.R. 843(E); corrigendum G.S.R. 892(E) of 11 Dec 2025 | None |
| F-02 | MeitY proposed advancing commencement to 13 Nov 2026 (08, R-18) | Not found in any source as of 15 Oct 2026. 13 Nov 2026 is the scheduled commencement of Rule 4 (Consent Managers) only, which is the likely origin of the claim | Reworded 08 §4.1 and R-18 |
| F-03 | Retention: data erased when purpose is served (08, retention policy) | Incomplete. Rule 8(3) requires a Data Fiduciary to retain personal data, associated traffic data and processing logs for at least one year for the purposes in the Seventh Schedule (which include State functions under law). Its scope is contested: some commentators read it as applying only when the processing purpose falls in that Schedule. On its face it conflicts with 24-hour deletion | Added to 08 §4.1, compliance matrix and R-19 [confirm] |
| F-04 | Rule 12 with Fourth Schedule Part B item 1 exempts processing under law in a child's interest (08) | Rule 12 structure confirmed. Exact Part B wording not confirmed from the Gazette; secondary summaries disagree on the list | Kept [confirm]; adviser reads the Gazette text |
| F-05 | JJ Model Rules 2016 rule 92 covers missing children; model SOP follows it (08) | Correct. Rule 92(1) defines a missing child; the MWCD model SOP implements it | None |
| F-06 | IS 17802 applies under RPwD Rule 15 (08, 14) | Correct (amendment of 11 May 2023). New: the draft RPwD (Amendment) Rules 2026, published 20 July 2026, would require an Accessibility Conformance Report; final status not known | Document 21 now required for every release |
| F-07 | CERT-In government guidelines clauses 3.4, 6.1.1-6.1.12, 6.2.1-6.2.7, 7.4, 7.10, 8.4, 8.5, 10.1.13, 14.4 (06, 12, 14) | All match the published text | None |
| F-08 | CERT-In secure application guidelines clauses 3.3, 4.8, 4.9, 4.10, 4.13, 4.15, 4.16, 5.2, 5.6, 6.1 (06, 11, 14) | Match version 1.1 (updated January 2024), except 5.6, which does not exist. The audit precondition is 5.5 | Fixed in 14 |
| F-09 | App lock uses `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` (SEC-21) | Wrong for the Android 10 floor. That combination is unsupported on API 28-29 | Fixed SEC-21 |
| F-10 | Database key bound with `setUserAuthenticationParameters` (SEC-22) | API 30 and later only. API 29 needs `setUserAuthenticationValidityDurationSeconds` | Fixed SEC-22 |
| F-11 | Gateway verifies the chain to "the Google attestation root" (03 §5, SEC-61) | Incomplete. Google added an ECDSA P-384 attestation root; devices using remote key provisioning have used it exclusively since 10 April 2026, while factory-provisioned devices still chain to the RSA root. A verifier must trust both. Leaked device keys allow spoofing until Google revokes them | Fixed 03 and SEC-61 |
| F-12 | EncryptedSharedPreferences deprecated (SEC-07) | Correct | None |
| F-13 | SQLCipher 4.18.0 supports Room 3 (background) | Correct (released 18 Aug 2026) | None |
| F-14 | MediaPipe Tasks Vision for landmarks (03) | Correct. `tasks-vision` 1.x is current; the MediaPipe deprecation notice concerns the LLM task, not vision | Pin an exact version |
| F-15 | React Native 33.5 MB vs Flutter 17.3 MB release builds (D-01) | Correct (Software Mansion, 19 Aug 2026, arm64 release builds) | None |
| F-16 | Mulberry Symbols "free for unrestricted use" (copyright policy) | Wrong. Mulberry is CC BY-SA 4.0 (share-alike) | Fixed policy and D-10 |
| F-17 | ARASAAC is CC BY-NC-SA (policy) | Consistent with the published licence | None |
| F-18 | Jellow licence to be confirmed (policy) | No published icon licence found. The app is published by Ravi Poovaiah (IDC School of Design, IIT Bombay). Written permission is needed | Kept [confirm] |
| F-19 | ISLRTC dictionary has "no public word count" (background tech research) | Wrong. The third edition (February 2021) has 10,000 terms, including regional signs. No reuse licence is published | Ask ISLRTC for written permission |
| F-20 | CISLR: 7,050 videos, 4,765 words (background) | Correct. Download is gated behind access conditions on Hugging Face | None |
| F-21 | INCLUDE and iSign figures (background) | Consistent with the papers | None |
| F-22 | Bhashini data retention terms (08, 13) | Still not published | Kept [confirm] |
| F-23 | Mobile Seva AppStore: free hosting, signed APKs only (11) | Correct per the hosting page; no 2026 confirmation of terms | Kept [confirm] |
| F-24 | Childline 1098 (18, SOP) | Valid; being integrated with ERSS-112 under Mission Vatsalya | Added 112 to the quick card |
| F-25 | Avatars are understood less well than human signers (D-03) | Supported. Also: among avatars, cartoon-style characters are often preferred. No study of children's comprehension of avatars was found | Testing with children required (doc 23 §8) |

## 2. What is right and stays

- Offline core, officer confirms before anything reaches the subject, sensitive questions gated, no AI-generated images.
- Session data only on the phone, encrypted, short-lived; no analytics; India hosting.
- "An aid, not an interpreter" framing everywhere, and the explicit ban on statement recording.
- The CERT-In mapping is accurate clause by clause (F-07, F-08).

## 3. Design review

Severity: **Critical** means the app fails the people it exists for. **High** means it fails in the field, at audit or in law. **Medium** means it causes harm or waste that is cheap to prevent now.

### Critical

**C-1. The design assumes a deaf child knows ISL. Most do not.**
Roughly 90-95% of deaf children are born to hearing parents, and most such parents do not sign (global and US figures; no India figure was found). In India many deaf children are schooled orally or meet ISL late, and families develop private "home signs". A lost deaf child is therefore more likely to use home signs and iconic gestures (a bindi or sindoor for mother, a moustache for father, a roof for home, a hand on the chest for "me") than ISL. The triage set (D-03) is recorded only in ISL, so for most young deaf children the primary channel does not work.
*Fix:* add a **gesture lexicon** of iconic, culturally common gestures for children and non-signers, alongside the ISL lexicon for ISL users. Pictures remain the base layer for everyone (D-12, doc 23).

**C-2. A stick-figure skeleton is the wrong thing to show a frightened child.**
No study supports skeleton avatars; the evidence for full avatars is already weak (F-25). A skeleton has no face, so it cannot show ISL facial grammar: raised brows mark yes/no questions and a head shake marks negation. Every triage item is a question. A skeleton figure may also frighten a young child.
*Fix:* drop the skeleton from the subject panel. Use two original characters, one for children and one for adults, with expressive faces (D-13, doc 23).

**C-3. Free text turned into "ISL" is not ISL.**
Generating signs for arbitrary officer text by looking up words produces signed Hindi or English, not ISL grammar, and nobody can review it before a child sees it. This is the T-31 harm, delivered by design.
*Fix:* replace free-text signing with a **phrase bank**: reviewed sentences with slots ("Do you want [water]?"). Free text is shown as caption, pictures and, for hearing subjects, audio only (D-13).

**C-4. One phone, one front camera, two people.**
The front camera faces whoever looks at the screen. If the phone lies flat between officer and subject (the flip layout), the camera sees the ceiling. If the screen faces the subject so the camera sees their hands, the officer cannot read the officer panel. Simultaneous "ask, watch and read the result" is physically impossible on one phone.
*Fix:* explicit turn-taking. **Show** (screen to subject, subject panel full screen) and **Officer** (phone back to officer). Camera recognition, if kept, uses the rear camera with the officer holding the phone.

**C-5. Camera sign recognition cannot read children.**
Public ISL datasets are adult signers (the emergency-word set is ages 22-26). Children's home signs are not in any dataset. A false "yes" about a child's safety is the worst failure the app can produce (T-30), and children rarely perform on cue for a camera.
*Fix:* in v1, replace camera recognition with an **officer gesture lookup**: the officer records where the child touched and what the hand did, the app lists likely meanings, and the officer confirms with the child by showing the gesture back (D-14, doc 23 §6). Camera recognition moves to LATER, for ISL users only, and on-device.

**C-6. There is no path to a human interpreter.**
Bhopal and Indore police already run WhatsApp video-call helplines for deaf people, with officers trained in sign language (with the Deaf Can Foundation); private on-demand ISL interpretation services also exist. For an ISL user, a human interpreter on a call beats every feature in this app.
*Fix:* a **Call an interpreter** action that starts a video call (Sandes or WhatsApp) to a per-state contact from configuration (D-11 already allows per-state numbers). No video or audio passes through the app (D-16).

**C-7. A hearing child who speaks another language and cannot read gets nothing.**
Rescued and trafficked children in India often speak Bengali, Nepali, Assamese, Odia or a tribal language; shelters and officers report losing information through interpreters and resort to WhatsApp groups of volunteer speakers. Captions do not help a child who cannot read, and the subject panel is silent by default.
*Fix:* a **language finder**: tiles that each play a short recorded greeting in one language, so the child can show which one they understand, then recorded audio of the triage set in that language (D-18; a scope decision for the sponsor).

### High

**H-1. Phase 1 cannot be delivered in 15 days as written.** It combines learning Kotlin, CameraX with MediaPipe, a recognition integration, SQLCipher, Bhashini, recorded and reviewed ISL clips, a skeleton avatar and the gateway. Recording and reviewing ISL clips alone depends on a signer's and a reviewer's calendar. *Fix:* phase 1 delivers the offline core (pictures, gestures, triage, drawing, notes, security baseline); network features follow (D-15).

**H-2. Export recipients cannot open the export.** SEC-12 produces a custom AES-256-GCM file. A CWC member or a station writer has no tool to open it. *Fix:* export a password-protected PDF (AES-256), which any PDF reader opens; keep the one-time strong passphrase shared out of band (D-17).

**H-3. Android 10 authentication APIs were wrong** (F-09, F-10). Fixed in SEC-21 and SEC-22; ST-09 must run on an API 29 phone.

**H-4. The attestation verifier would reject newer phones** (F-11). Fixed. Use Google's `android/keyattestation` library rather than writing chain checks by hand.

**H-5. DPDP Rule 8(3) may require one-year retention** (F-03). If it applies, deleting session data after 24 hours breaches it; if it does not, keeping data longer breaches section 8(7). This needs the legal adviser's written answer before the pilot. Section 17(1) may exempt investigation purposes, but not every lost-child case is an offence.

**H-6. Half of a 5-inch screen cannot hold the subject panel.** At a 50/50 split the subject half is about 2.5 inches tall. An avatar, a caption, three 96 dp cards and response controls do not fit. *Fix:* the subject panel is full screen whenever it faces the subject (the Show state in C-4); the officer panel is used only when the phone is back with the officer. FR-04 (draggable divider) can then be dropped.

**H-7. Network features carry most of the security and audit cost but add the least value in v1.** The gateway, attestation, pinning, partner agreements and Bhashini terms exist for free-text signing (C-3: low value), server recognition (C-5: deferred, and better on-device), voice input (officers can type) and translation (triage is pre-translated). *Fix:* v1 ships with no INTERNET permission. Threats T-07 to T-13 disappear, the audit shrinks to the app, and DPIA risk P-03 closes. Network features return in v1.1 behind the gateway (D-15).

**H-8. One human signer is a single point of failure.** The consent form lets the signer withdraw, after which their clips leave the next release, which removes the whole triage set. *Fix:* record two signers, or keep the adult avatar's phrase bank able to cover the triage set.

**H-9. A child can leave the app on the officer's phone.** A subject holding the phone can reach the officer's messages and photos. *Fix:* use Android screen pinning (`startLockTask`) while the phone faces the subject; it needs the device's app-pinning setting, or MDM allowlisting on managed phones.

### Medium

**M-1. Yes and no gestures are ambiguous in India.** The sideways head wobble can mean yes, okay or maybe, and visitors routinely misread it. The avatar must never use it, and the SOP should tell officers to confirm yes and no with the cards.

**M-2. Iconic gestures are not universal across communities.** Sindoor and bindi mark married Hindu women; many Muslim, Christian, Sikh, tribal and North-East families use neither, and a bindi does not mean "mother". A moustache does not mean "father" in every family. Each concept needs several variants, with community and region tags, reviewed by people from those communities (doc 23 §3).

**M-3. "Silent by default" fails hearing children who cannot read.** For them, audio of the question in their language is the most effective channel. Make audio the default when the subject profile is a hearing child, at low volume, with the officer able to mute it.

**M-4. The content style contradicts itself.** "You are safe now" is listed as good wording, but it is a promise the officer may not be able to keep, which the same table forbids. Use "I am a police officer. I am here to help you."

**M-5. There is no research-ethics plan for testing with children.** Usability and avatar tests with deaf or hearing children need institutional ethics approval, parental or guardian consent, the child's own assent, and a child-welfare professional present. Never test with rescued children. Added to the test plan (§7).

**M-6. Background documents lived outside the repository.** The README cited two files on the Desktop. They are now in `docs/background/`, versioned with the decisions.

**M-7. Several SHOULD requirements cost more than they return** in v1: FR-04 (divider drag) and NFR-26 (one-handed use with a split screen). They become unnecessary under the Show/Officer model.

## 4. Proposed v1 shape

If D-12 to D-18 are accepted, v1 looks like this.

1. **Start.** Unlock, then pick a subject profile with one tap (changeable at any time): *Young child*, *ISL user*, *Hearing, other language*, *Not sure*. The profile decides what the subject sees and hears.
2. **Show.** The officer picks a question; the phone turns to the subject; the subject panel goes full screen and the app pins itself. The subject sees pictures and Yes/No cards, plus, by profile: the child character doing the gesture, the human ISL clip or adult character, or audio in their language.
3. **Answer.** The subject taps a card, draws, or gestures. For a gesture, the officer uses the gesture lookup and shows the meaning back to confirm.
4. **Officer.** The phone returns; the officer sees the answer log and notes. **Call an interpreter** is always one tap away.
5. **End.** Export to a password-protected PDF if needed, then clear.

No network in v1. Voice input, free-text translation, the gateway and camera recognition come in v1.1 and later, each behind evidence from the pilot.

### Revised phase 1 (15 days)

| Days | Work |
| --- | --- |
| 1 | Project setup, Show/Officer states, subject profiles, screen pinning |
| 2-4 | Triage flow with captions, picture cards and audio; picture board |
| 5-6 | Gesture cards (static illustrations of the child character, doc 23 §5.4) and the officer gesture lookup |
| 7 | Language finder with recorded greetings (if D-18 accepted) |
| 8 | Drawing, notes, session log |
| 9-10 | SQLCipher storage, app lock (API 29 path included), FLAG_SECURE, backup exclusion, auto-expiry, clear |
| 11 | PDF export, Call an interpreter |
| 12 | Human ISL clips for the triage set (if recorded and reviewed by then) |
| 13-14 | Device testing on the oldest phone, accessibility pass, MASVS self-check |
| 15 | Fixes and demo build |

The animated characters, ISL phrase bank, voice and recognition move to phase 2 and later.

## 5. Changes to other documents if the proposals are accepted

| Document | Change |
| --- | --- |
| 02 SRS | Add subject profiles, Show/Officer states, screen pinning, gesture lexicon and lookup, language finder, Call an interpreter. FR-16 becomes phrase bank only. FR-22/23 move to LATER. Drop FR-04 and NFR-26. NFR-24 allows profile-driven audio |
| 03 Architecture | v1 has no `net` package and no gateway; add `lexicon` (gestures, ISL, phrase bank) and `avatar` (clip playback) packages |
| 04, 07, 08 | v1 data flows lose F4, F9-F12 and TB2-TB4; P-03 and T-07 to T-13 move to v1.1 |
| 05 UX | Replace the split screen with Show/Officer; content style change (M-4) |
| 09 Contracts | Marked v1.1; the ISL team's output becomes build-time clip data, not a runtime API |
| 15 Plan | Phase 1 as in section 4 |
| 17 UAT | UAT-04 rewritten for the gesture lookup and interpreter call |

## 6. Questions answered (16 October 2026)

| Question | Answer | Effect |
| --- | --- | --- |
| Is a hearing child who speaks another language in scope? | Yes | D-18 accepted |
| Blank "mask" face for the adult avatar? | No; the face stays | Doc 23 §4, D-13 |
| Funding for an artist and animator? | None; another group may be building an avatar but cannot be relied on | In-house build with free tools (doc 23 §9), R-24, R-25 |
| Who answers the interpreter call in each state? | Open; needs the department | D-16 corrected to a video call; button hidden where no contact exists |
| Personal or department phones? | Personal | D-19, SEC-64, R-23 |
| Move the background documents into the repository? | Yes | Now in [background](background/) |

Still open: whether D-12 to D-17 are accepted, after which the SRS, architecture, data flows, threat model, DPIA, UX, contracts, plan and UAT are updated as listed in section 5.

## Sources

**Law and government**
- DPDP Rules and commencement: https://www.legal500.com/intelligence/india/privacy/digital-personal-data-protection-rules-2025-%E2%80%93-notified, https://www.mondaq.com/india/data-protection/1830402/, https://dpdprules.org/documents
- DPDP Rule 8(3): https://www.aparajitha.com/recommendation-letter-recommendations-regarding-rule-83-of-the-digital-personal-data-protection-rules-2025/, https://ssrana.in/articles/meity-notifies-final-digital-personal-data-protection-rules-2025/, https://dpdp.myndsolution.com/wiki/rules/rule-8-time-period-for-specified-purpose-to-be-deemed-as-no-longer/
- DPDP Rule 12: https://dpdp.myndsolution.com/wiki/rules/rule-12-exemptions-from-certain-obligations-applicable-to-processing/
- JJ Model Rules rule 92 and the missing children SOP: https://factly.in/understanding-the-sop-for-cases-of-missing-children, https://satyarthi.org.in/wp-content/uploads/2026/08/sop-for-missing-children.pdf
- RPwD Rule 15 and the 2026 draft: https://scconline.com/blog/?p=291941, https://www.indialaw.in/blog/regulatory/rpwd-amendment-rules-2026/
- CERT-In Guidelines on Information Security Practices for Government Entities: https://www.cert-in.org.in/PDF/guidelinesgovtentities.pdf
- CERT-In Guidelines for Secure Application Design, Development, Implementation and Operations v1.1: https://www.csk.gov.in/documents/Application_Security_Guidelines.pdf
- Mobile Seva hosting requirements: https://apps.mgov.gov.in/Admin/hosting_req
- Child helpline and ERSS-112: https://www.vikaspedia.in/news/government-takes-decision-for-integration-of-child-helpline-with-erss-112, https://newsonair.gov.in/nagaland-child-helpline-1098-and-women-helpline-181-integrated-with-erss-112

**Android**
- BiometricManager constraints: https://developer.android.com/reference/androidx/biometric/BiometricManager, https://developer.android.com/identity/sign-in/biometric-auth
- Key attestation and the root change: https://developer.android.com/privacy-and-security/security-key-attestation, https://bayton.org/android/android-enterprise-faq/key-attestation-root-certificate-change/, https://blog.quarkslab.com/bypassing-android-hardware-attestation.html, https://github.com/android/keyattestation
- MediaPipe: https://developers.google.com/edge/mediapipe/solutions/vision/hand_landmarker/android, https://github.com/google-ai-edge/mediapipe/releases
- SQLCipher 4.18.0: https://www.zetetic.net/blog/2026/08/18/sqlcipher-4.18.0-release/
- Flutter and React Native sizes: https://swmansion.com/blog/we-built-the-same-app-in-flutter-and-react-native-here-s-what-we-found/

**Content and sign language**
- Mulberry Symbols licence: https://globalsymbols.com/symbolsets/mulberry/symbols/5621?locale=en
- ISLRTC dictionary third edition: https://www.outlookindia.com/amp/story/national/from-just-3-000-to-10-000-words-now-indian-sign-language-dictionary-grows-to-help-hearing-impaired-news-322677, https://www.gktoday.in/third-edition-of-indian-sign-language-dictionary-to-be-released/
- CISLR: https://aclanthology.org/2022.emnlp-main.707, https://huggingface.co/datasets/Exploration-Lab/CISLR
- ISL emergency-word dataset: https://www.ncbi.nlm.nih.gov/pmc/articles/PMC7378574/
- Deaf children of hearing parents and home sign: https://www.bu.edu/articles/2017/asl-language-acquisition/, https://doi.org/10.1093/cdpers/aadag021, https://www.devex.com/news/deaf-and-hearing-impaired-indians-push-for-official-sign-language-recognition-93911
- Avatar comprehension and preference: https://www.frontiersin.org/journals/psychology/articles/10.3389/fpsyg.2022.730917/full, http://www.michaelkipp.de/publication/Kippetal11.pdf, https://www.researchgate.net/publication/254007035_Assessing_the_deaf_user_perspective_on_sign_language_avatars
- Head wobble ambiguity: https://folklore.usc.edu/confusing-gestures/, https://madrascourier.com/?p=26828

**Field practice**
- Police video-call helplines for deaf people: https://www.etvbharat.com/en/!state/bhopal-police-breaks-silence-of-deaf-mute-introduces-video-call-to-help-them-file-complaint-enn25030401850, https://www.freepressjournal.in/bhopal/bhopal-whatsapp-helpline-launched-for-deaf-mute-40-police-personnel-trained
- On-demand ISL interpretation: https://signable.live/about-us-indian-sign-language/
- Language barriers with rescued children: https://www.globalcitizen.org/en/content/feature-girls-freed-from-mumbais-brothels-learn-hi/, https://www.tribuneindia.com/news/chandigarh/kailash-satyarthi-praises-haryana-cop-for-reuniting-missing-kids-with-families-132505
