# Police Assist App: Tech Stack and Compliance Research

Companion to the [frontend plan](frontend-plan.md). Written 15 October 2026.

Constraints used: production government app, Android 10+ phones with 3 GB RAM, distributed as an APK through Play Store, data of minors and victims, any cloud acceptable during development.

## 1. Summary of recommendations

| Area | Recommendation | Changes the plan? |
| --- | --- | --- |
| App type | Native Android: Kotlin + Jetpack Compose, single activity | Yes. Replaces React PWA |
| Min / target SDK | minSdk 29 (Android 10), targetSdk as required by Play at release | New |
| Build | App Bundle, R8 full mode, Baseline Profile, Play Asset Delivery for ML models | New |
| Avatar | Keep 2D skeleton for free text; use short recorded human-signer clips for the preset triage questions | Yes |
| Sign-to-text | On-device MediaPipe hand + pose landmarks via CameraX; send landmark arrays only, never video | Narrows the plan |
| Speech-to-text | Bhashini (Government of India, 22 languages) as primary; on-device IndicConformer later for offline | Yes. Replaces generic Whisper |
| Translation | Bhashini | New |
| Picture cards | Curated set: Jellow (Indian context) plus Mulberry or ARASAAC, licences checked | Refines |
| AI image generation | Drop from v1 | Yes |
| Local storage | Room + SQLCipher, key in Android Keystore; DataStore for settings | Refines |
| Security baseline | OWASP MASVS L2, CERT-In empanelled audit before release | New |
| Accessibility | GIGW 3.0 (WCAG 2.1 AA) | New |
| Hosting | Any cloud for development; India-region, MeitY-empanelled cloud before real use | Refines |

## 2. App platform

### 2.1 Why native Kotlin instead of the React PWA

The plan chose React because a web build is fast for a 15-day timeline. The new constraints change that:

- **Distribution is an APK.** A PWA wrapped in Capacitor runs inside a WebView. Camera, landmark detection and the avatar canvas all go through the WebView, which costs memory and frame rate on a 3 GB phone.
- **On-device ML.** MediaPipe Tasks and LiteRT have first-class Android libraries. In a WebView they run as WebAssembly, which is slower and uses more memory.
- **Security controls.** Screenshot blocking (`FLAG_SECURE`), Keystore-backed encryption, backup exclusion and Play Integrity are native APIs. A web app reaches them only through plugins.
- **Size.** A Software Mansion comparison measured a React Native release build at 33.5 MB against Flutter at 17.3 MB. A plain native app can stay well under both.

**Alternatives.** Flutter is a reasonable second choice if the team already knows Dart: it produces a smaller build than React Native and uses less CPU, though one study found Compose uses less memory. React Native is the weakest fit for this device class. A PWA is acceptable only if officers will never install an APK.

**Risk.** The frontend team planned for React. If nobody on the team writes Kotlin, switching costs 2-3 days of the 15. Decide this first (see section 10).

### 2.2 Performance setup for old phones

- Release builds with R8 full optimisation. Google reports that Startup Profiles add about 15% faster startup, and only work with R8 fully enabled. Monzo reported 35% fewer ANRs after enabling full R8.
- A Baseline Profile covering cold start, opening the split view and playing the first sign.
- Ship as an App Bundle so each phone downloads only its own ABI and density. Put ML models in Play Asset Delivery packs, not in the base APK.
- Compose rules from the official performance guide: `remember` expensive work, stable keys in lazy lists, `derivedStateOf` for fast-changing state, measure on a real low-end phone in release mode.
- No dependency injection framework, no analytics SDK, no ads SDK. Fewer libraries means a smaller app and a smaller audit.

### 2.3 Suggested libraries

| Need | Library |
| --- | --- |
| UI | Jetpack Compose, Material 3 |
| Camera | CameraX |
| Hand and pose landmarks | MediaPipe Tasks Vision (`com.google.mediapipe:tasks-vision`) |
| On-device inference (later) | LiteRT |
| Networking | OkHttp + kotlinx.serialization |
| Database | Room + SQLCipher for Android (4.18.0 supports Room 3) |
| Settings | Jetpack DataStore |
| Background clean-up | WorkManager |
| Fonts | Noto Sans, Noto Sans Devanagari and the needed regional Noto scripts, bundled |

## 3. Indian Sign Language output

### 3.1 What the evidence says about avatars

- Studies with Deaf signers consistently prefer human signers over avatars, mainly for expressiveness and fluency.
- A Dutch healthcare study found individual signs from an avatar were recognised nearly as well as from a human, but sentence comprehension was substantially lower. The causes were missing mouth shapes, prosody and smooth transitions.
- A motion-capture avatar was rated better than a computer-synthesised one on every measure in a survey of 191 ASL users.
- No study was found that tests a skeleton or stick-figure avatar. Expect it to be harder to read than a full avatar, especially facial grammar, which ISL relies on.

### 3.2 Recommendation

1. For the preset triage questions (about 30-50 short phrases), record a fluent ISL signer and ship the clips with the app. Short, compressed clips work offline, are easy to understand and need no ML.
2. Use the 2D skeleton only for free-text questions, where no recording exists. Always show the caption and a picture with it.
3. Render the skeleton on a Compose `Canvas`: draw bones as lines between landmark pairs, with thicker hand and finger lines and a simple face outline including mouth points.
4. Ask the ISL team whether their keypoints include face landmarks. Without them, questions and negation are hard to read in ISL.

### 3.3 ISL data available

- **INCLUDE** (AI4Bharat, IIT Madras): about 4,287 videos of 263 word signs in 15 categories.
- **CISLR**: about 7,050 videos covering 4,765 words, with very few samples per word.
- **iSign** (IIT Kanpur with ISLRTC collaborators): more than 118,000 ISL video and English sentence pairs.
- **ISLRTC dictionary**: the official reference. No public word count or licence terms were found; ask ISLRTC directly about use in a government app.

## 4. Sign-to-text (subject signs back)

- Run MediaPipe hand and pose landmark detection on the phone through CameraX, at reduced resolution and frame rate.
- Send only landmark arrays to the ML team's service. Video of a child never leaves the phone, and the payload is a few kilobytes instead of a video stream.
- Do not send frames when no hands are detected.
- Use isolated-sign recognition for a small vocabulary that matches the triage answers (yes, no, pain, water, food, toilet, mother, father, home, police and similar). Published landmark-plus-LSTM systems reach about 88% accuracy on 45 signs. Continuous sentence-level recognition is not reliable enough for this use.
- Avoid MediaPipe Holistic for this. A TUM thesis found its preprocessing reduced accuracy for signing and it failed to track typical signing poses.
- Always show the recognised word with its confidence and a "repeat" option. Never treat a recognition as certain.
- No public benchmark of hand landmarking on low-end Android phones was found. Measure frame rate on the oldest target phone in week 1.

## 5. Speech and language services

### 5.1 Speech-to-text

| Option | For | Against |
| --- | --- | --- |
| Bhashini ASR (MeitY) | Government platform, 22 scheduled languages, reported free for developers, data stays with a government body | No public data-retention terms were found; confirm before production |
| On-device IndicConformer via sherpa-onnx | Works offline, audio never leaves the phone | About 131 MB per language (int8); licence differs between model copies; a later phase |
| Commercial cloud (OpenAI, Google) | Easy to integrate | Sends minors' audio to a foreign provider; avoid for production |

Recommendation: Bhashini for v1. Plan on-device Hindi ASR as a later offline feature. The officer always confirms the transcript, as in the existing plan.

### 5.2 Translation

Use Bhashini translation for captions in regional languages. The distilled IndicTrans2 model (200 million parameters, roughly 200-400 MB) is too large to bundle in v1. Triage questions and card labels should be pre-translated and reviewed by fluent speakers, so they need no translation service at all.

## 6. Picture cards and AI images

**Picture cards.** Combine Jellow (IDC School of Design, includes Indian sociocultural icons) with Mulberry (free SVG set) or ARASAAC (CC BY-NC-SA, non-commercial only). Confirm each licence from its source before shipping.

**AI image generation: drop it from v1.** Reasons:

- It sends case descriptions about a child to an image service.
- A wrong or frightening image shown to a traumatised child does harm that cannot be undone.
- It adds cost, latency and a network dependency.
- The curated board covers the triage and information-gathering scenarios.

## 7. Legal and compliance

This section summarises public sources and is not legal advice. Every point needs confirmation from the department's legal adviser.

### 7.1 Digital Personal Data Protection Act 2023 and Rules 2025

- The Rules were notified on 13 November 2025. Most substantive obligations take effect on 13-14 May 2027. MeitY has floated advancing this to 13 November 2026, which had not been notified at the time of writing. Check the current status.
- **Section 17(1)** exempts processing for the prevention, detection, investigation or prosecution of offences from most of the Act. The duty to take reasonable security safeguards (section 8(5)) still applies. Design as if all security duties apply.
- **Children's data.** Section 9 requires verifiable parental consent. The Fourth Schedule, Part B, item 1 exempts processing for a power, function or duty under law in the child's interest, limited to what is necessary. A police officer helping a found or rescued child is likely to fall under this; confirm with legal.
- **Retention.** Rule 8 requires erasure once the purpose is served. The app should clear session data after handover, and the plan's one-tap clear session already supports this.

### 7.2 Child-protection law

- **POCSO Act section 26.** Statements may be recorded with an interpreter, and for a child with a disability, with a special educator or a person familiar with how the child communicates. Statements should be audio-video recorded where possible. The app is a communication aid. It must not be presented as a substitute for a formal statement or an interpreter.
- **Juvenile Justice Act 2015 section 74.** Disclosure of a child's identity is prohibited and punishable. The app must not expose names, photos or case details in notifications, logs, crash reports or shared exports.
- **JJ Model Rules 2016, rule 92** and the 2016 SOP on missing children cover the police procedure for found children. The app's triage flow should fit that procedure, not replace it.

### 7.3 Government app requirements

- **CERT-In audit.** Government tenders require a "safe to host" certificate from a CERT-In empanelled auditor before production, following CERT-In and OWASP guidance. Budget time for this after the 15 days.
- **GIGW 3.0** (2023) covers apps as well as websites, harmonised with WCAG 2.1 AA, with STQC certification.
- **CERT-In Directions of 28 April 2022** require incident reporting within six hours.
- **Google Play.** Complete the government apps declaration in Play Console and create the developer account with an official government email. Approved apps get a "Government" badge.

## 8. Security design (MASVS L2)

| Control | Implementation |
| --- | --- |
| Data at rest | Room + SQLCipher; random 256-bit key wrapped by Android Keystore. Do not use EncryptedSharedPreferences (deprecated in April 2025) |
| App lock | Device credential or BiometricPrompt to open the app and to view saved sessions |
| Screenshots and recents | `FLAG_SECURE` on all screens showing the subject or notes |
| Backups | `android:allowBackup="false"` and `dataExtractionRules` excluding everything |
| Network | HTTPS only through Network Security Config, no cleartext; certificate pinning to the project's own backend |
| Logs | No personal data in logs; strip debug logging in release with R8 |
| Exports | Encrypted export only, through the Android share sheet; never to shared storage |
| Auto-expiry | WorkManager deletes sessions older than a set limit (for example 24 hours) unless exported |
| Device integrity | Play Integrity API check before talking to the backend |
| Third parties | No analytics, ads or crash SDKs that collect personal data |
| Permissions | Camera and microphone requested only when the feature is used, with a visible "live" indicator |

## 9. Revised 15-day schedule

| Days | Work |
| --- | --- |
| 1 | Project setup (Kotlin, Compose, minSdk 29), split view shell with flip and focus mode, mock services |
| 2-3 | Triage question flow with recorded sign clips, captions, picture board (curated cards) |
| 4-5 | Skeleton avatar on Canvas from sample keypoints; frame-rate test on the oldest phone |
| 6-7 | Voice input via Bhashini, officer confirmation, typed input, language switching |
| 8-9 | CameraX + MediaPipe landmarks, landmark upload to the ML mock, confidence display |
| 10 | Drawing canvas, notes and session log |
| 11 | SQLCipher storage, app lock, `FLAG_SECURE`, backup exclusion, auto-expiry, clear session |
| 12-13 | Connect real ISL, ML and Bhashini services; timeouts, retries, fallbacks |
| 14 | Device testing, accessibility pass, MASVS self-check |
| 15 | Fixes, demo build, handover notes for the CERT-In audit |

Cut order if time runs short: regional languages beyond Hindi and English, drawing canvas, skeleton avatar for free text (keep recorded clips). Never cut the picture board, fallbacks, encryption or clear session.

## 10. Questions to settle before day 1

1. Does anyone on the frontend team know Kotlin? If not, choose between learning it (about 2-3 days) and Flutter or a WebView build with weaker performance.
2. Who records the ISL clips for the triage set, and who checks them (ISLRTC, a Deaf school, an interpreter)?
3. Will the department provide a legal adviser to confirm the DPDP, POCSO and JJ Act points in section 7?
4. Has the department registered for Bhashini API access, or does the team need to apply?
5. Are the phones personal or department-issued, and is there mobile device management? This decides whether app lock and remote wipe are handled by the app or by MDM.
6. Should sessions ever leave the phone (for example to a CCTNS case file), or is the phone the only record?

## 11. Sources

**Law and policy**
- DPDP Rules 2025 timeline: https://dpdpa.dcomply.in/rules/
- DPDP Fourth Schedule: https://privacylawhub.com/bare-acts/dpdp-rules-2025/schedule-iv-fourth-schedule-exemptions-from-section-9-1-and-9-3-
- DPDP Rule 12: https://www.dpdpa.com/dpdparules/rule12.html
- DSCI summary of the Rules: https://www.dsci.in/files/content/documents/2025/Digital-Personal-Data-Protection-Rules-2025.pdf
- Children's data commentary: https://elplaw.in/leadership/processing-of-childrens-personal-data-under-the-dpdp-act-what-does-it-means-for-businesses-3/
- POCSO section 26: https://courtbook.in/pocso/section-26
- Patna High Court POCSO SOP: https://www.patnahighcourt.gov.in/jjs/PDF/UPLOADED/211.PDF
- JJ Act section 74: https://lawx.in/acts/section/114/1026/8854
- SOP for missing children: https://satyarthi.org.in/wp-content/uploads/2026/08/sop-for-missing-children.pdf
- CERT-In safe-to-host requirement (NPC tender): https://npcindia.gov.in/NPC/Uploads/image/International Services/NIQ -Security Audit final 16.11.2021173925.pdf
- GIGW 3.0: https://guidelines.india.gov.in/?p=10063 and https://www.digit.in/features/general/what-is-gigw-3-0-indian-govts-design-guidelines-for-official-websites-and-apps.html
- Google Play government apps: https://support.google.com/googleplay/android-developer/answer/9514050

**Android and security**
- Framework size comparison: https://swmansion.com/blog/we-built-the-same-app-in-flutter-and-react-native-here-s-what-we-found/
- Compose vs Flutter study: https://ph.pollub.pl/index.php/jcsi/article/download/7914/5284/39320
- Baseline Profiles: https://developer.android.com/baseline-profiles
- Monzo R8 results: https://android-developers.googleblog.com/2026/03/monzo-boosts-performance-metrics-by-up-to-35-percent.html
- Compose performance: https://developer.android.com/develop/ui/compose/performance
- EncryptedSharedPreferences deprecation: https://mas.owasp.org/MASTG/knowledge/android/MASVS-STORAGE/MASTG-KNOW-0036/ and https://proandroiddev.com/goodbye-encryptedsharedpreferences-a-2026-migration-guide-4b819b4a537a
- SQLCipher 4.18.0: https://www.zetetic.net/blog/2026/08/18/sqlcipher-4.18.0-release/
- MediaPipe Hand Landmarker for Android: https://developers.google.com/edge/mediapipe/solutions/vision/hand_landmarker/android

**Sign language**
- Avatar acceptance survey: https://www.frontiersin.org/journals/psychology/articles/10.3389/fpsyg.2022.730917/epub
- Avatar comprehension in healthcare: https://www.hu.nl/onderzoek/publicaties/exploring-automatic-texttosign-translation-in-a-healthcare-setting
- Avatar vs human, Gallaudet: https://gallaudet.edu/?p=86116
- INCLUDE dataset: https://zenodo.org/records/4010759
- iSign benchmark: https://arxiv.org/html/2407.05404v1
- CISLR: https://arxiv.org/pdf/2407.14224
- Continuous ISL recognition with landmarks: https://arxiv.org/pdf/2411.04517
- MediaPipe Holistic: https://research.google/blog/mediapipe-holistic-simultaneous-face-hand-and-pose-prediction-on-device/
- Sign translation on mobile (TUM): https://mediatum.ub.tum.de/1658159
- SignSpire 2D ISL pose animation: https://link.springer.com/chapter/10.1007/978-3-032-26905-8_28

**Language services and content**
- Bhashini overview: https://egov.eletsonline.com/2026/03/bhashini-bridging-1-4-billion-citizens-to-services-and-opportunities-via-language-ai/
- IndicConformer: https://github.com/AI4Bharat/IndicConformerASR
- Per-language IndicConformer ONNX packs: https://huggingface.co/nmukthap/vertexvoice-indic
- Offline Indic ASR app example: https://github.com/ashb155/uktam
- IndicTrans2: https://ai4bharat.iitm.ac.in/blog/indictrans2
- Free symbol sets (Jellow, Mulberry, ARASAAC): https://en.commtap.org/additional-resource/free-symbol-sets and https://globalsymbols.com/symbolsets
- Existing police helplines for deaf people: https://www.freepressjournal.in/indore/indore-deaf-mute-now-a-text-away-from-cop-help
