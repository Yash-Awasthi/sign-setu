# 09. Open-Source Reuse Map

What to reuse for each independent part of the app, checked on 15 October 2026 (licence as reported by the project's repository; activity is the last push date). Every entry still goes through the [copyright and licensing policy](governance/policies/copyright-licensing-policy.md) and into the SBOM before use.

**How to use a component:**
- **Ship**: linked into the app or gateway.
- **Tool**: used by the team to produce assets; not shipped, so its licence does not reach the app.
- **Reference**: read for design or architecture; copy no code.
- **Data**: trained on or derived from; check the data licence separately.

GPL and AGPL code is never shipped in the app (policy §2); it may be used as a Tool or a Reference.

## 1. Per module

| Module (doc 03 package) | Component | Licence | Use as | Notes |
| --- | --- | --- | --- | --- |
| Picture board (`content`, `ui.subject`) | Open Board Format (OBF/OBZ) | Open specification | Ship (format) | Store card sets as OBF so boards can be exchanged with other AAC tools and edited without code |
| | AAC-Board (fesave) | Apache-2.0 | Reference | Kotlin AAC app; small and inactive since 2023; read its board model |
| | LibreAAC | GPL-3.0 | Reference | Offline Android AAC with no INTERNET permission, OBF/OBZ support; same privacy stance as ours |
| | Cboard, Asterics-AAC | GPL-3.0, AGPL-3.0 | Reference | Mature web AAC boards; interaction patterns |
| Symbols | Mulberry Symbols | CC BY-SA 4.0 | Ship (assets) | Attribution; share-alike applies to edited symbols |
| | OpenMoji | CC BY-SA 4.0 | Ship (assets) | Emoji-style; useful for feelings and objects |
| | ARASAAC | CC BY-NC-SA | Ship only with legal approval | Non-commercial clause; government use needs a written legal view |
| | Jellow | Not published | Ship only with written permission | Indian-context icons; ask the publisher |
| Avatar authoring (doc 23) | Blender | GPL | Tool | Clean-up, rendering of clips; rendered output is not covered by the GPL |
| | MediaPipe (Holistic, Hand, Pose, Face) | Apache-2.0 | Tool | Landmarks from performer video |
| | Kalidokit | MIT | Tool or Reference | Solves MediaPipe landmarks (including 21-point hands) into rig rotations; port the maths if needed |
| | BlendArMocap | GPL-3.0 | Tool | Blender add-on that brings MediaPipe capture onto a rig |
| | FreeMoCap | AGPL-3.0 | Tool | Multi-camera markerless capture if single-camera quality is not enough |
| | pose-format | MIT | Tool | Store and visualise pose sequences during review |
| | VRM specification, three-vrm | Open spec, MIT | Tool | Rig standard; three-vrm gives reviewers a browser preview of clips |
| | VRoid Studio | Free tool; creator owns own textures and hair; pixiv preset items usable commercially unless an item says otherwise | Tool | Builds both characters with rig, fingers and face expressions; record each preset item used (doc 23 §9) |
| | VRM Add-on for Blender | MIT | Tool | Imports the VRoid model into Blender for posing, keyframing and scripted rendering |
| Avatar runtime | Media3 ExoPlayer | Apache-2.0 | Ship | Stage 1 clip playback, gapless playlists, slow speed (already in the stack) |
| | Filament, SceneView | Apache-2.0 | Ship (stage 2 only) | glTF with skinning and morph targets on Android |
| | Rive runtime | MIT | Not chosen | 2D bone animation; editor is a hosted service, a dependency we avoid |
| Sign recognition (LATER) | MediaPipe Tasks Vision | Apache-2.0 | Ship | On-device landmarks (already planned) |
| | LiteRT | Apache-2.0 | Ship | On-device inference of the recognition model, so landmarks never leave the phone |
| | Kaggle Isolated Sign Language Recognition, first-place solution | MIT | Reference | Landmark-to-sign model that ran as TFLite under tight size and latency limits; retrain on ISL |
| | OpenHands (AI4Bharat) | Apache-2.0 | Reference, Data | Pose-based ISL models and pretraining data; inactive since 2023 |
| ISL and gesture data | ISLRTC dictionary | Not published | Data with written permission | 10,000 terms; the reference for the ISL lexicon |
| | INCLUDE, CISLR, iSign | Per dataset | Data | Recognition training and evaluation; CISLR download is gated |
| | ISL emergency-word video set | Per dataset (Mendeley) | Data | 8 words (accident, call, doctor, help, hot, lose, pain, thief), adult signers |
| Speech (v1.1) | Bhashini | Government service | Ship (via gateway) | Retention terms still unconfirmed |
| | sherpa-onnx, Vosk | Apache-2.0 | Ship (optional pack) | Offline speech recognition so officer audio never leaves the phone |
| Language identification (v1.1) | IndicLID (Bhashini and AI4Bharat) | See model card | Ship (via gateway) or Reference | Text language identification for 22+ Indic languages; no audio language identification service was found |
| Storage and crypto | SQLCipher for Android | BSD-style | Ship | Room 3 supported from 4.18.0 |
| Export | PdfBox-Android | Apache-2.0 | Ship | AES-256 password-protected PDF export (D-17); last updated 2024, so watch advisories. Its BouncyCastle is held at 1.86 by a build constraint because 1.72 has known critical vulnerabilities |
| | zip4j | Apache-2.0 | Alternative | AES-256 zip; recipients need 7-Zip, so PDF is preferred |
| Gateway attestation (v1.1) | android/keyattestation | Apache-2.0 | Ship (gateway) | Google's verification library; handles both attestation roots and revocation |
| Networking (v1.1) | OkHttp, kotlinx.serialization | Apache-2.0 | Ship | Already planned |
| Drawing canvas | Compose Canvas | Platform | Ship | No library needed |
| Characters (D-21) | Compose Canvas | Platform | Ship | 2D rig and keyframes drawn in code; no animation library |
| Camera mirror (D-20) | CameraX (camera-camera2, camera-lifecycle, camera-compose) | Apache-2.0 | Ship | Preview only; ImageAnalysis when the partner hand model is present |
| Type | Baloo 2 (Ek Type) | SIL OFL 1.1 | Ship (font) | Latin and Devanagari; licence text kept with the font |
| Icons | Material Icons (selected vectors) | Apache-2.0 | Ship (assets) | Hand, grid, translate, keyboard, camera, volume, pin |
| Interpreter call (D-16) | Sandes (NIC) or WhatsApp, opened by intent or link | Government service; third-party app | Ship (hand-off only) | Video call happens in the messenger, not in this app; no library needed |
| | Jitsi Meet | Apache-2.0 | Not chosen | Would need a self-hosted server, which has a running cost |
| Screen pinning | `Activity.startLockTask` | Platform | Ship | No library needed |

## 2. Considered and rejected

| Component | Reason |
| --- | --- |
| sign.mt | CC BY-NC-SA, no ISL model; useful only as a reference for pose-to-avatar ideas |
| CWASA / JASigning | Research licence; HamNoSys authoring per sign |
| Ready Player Me | Shut down 31 January 2026 |
| Google SignGemma | ASL only; weights not openly released |
| Any hosted avatar or text-to-sign service | Sends officer text to a third party and can disappear |

## Sources

Repositories (licence and activity read from each): https://github.com/fesave/AAC-Board, https://github.com/libretastic/libreaac-android, https://github.com/cboard-org/cboard, https://github.com/asterics/Asterics-AAC, https://github.com/hfg-gmuend/openmoji, https://github.com/google-ai-edge/mediapipe, https://github.com/yeemachine/kalidokit, https://github.com/cgtinker/BlendArMocap, https://github.com/freemocap/freemocap, https://github.com/sign-language-processing/pose, https://github.com/vrm-c/vrm-specification, https://github.com/pixiv/three-vrm, https://github.com/google/filament, https://github.com/sceneview/sceneview, https://github.com/rive-app/rive-android, https://github.com/hoyso48/Google---Isolated-Sign-Language-Recognition-1st-place-solution, https://github.com/AI4Bharat/OpenHands, https://github.com/k2-fsa/sherpa-onnx, https://github.com/alphacep/vosk-api, https://github.com/sqlcipher/sqlcipher-android, https://github.com/TomRoush/PdfBox-Android, https://github.com/srikanth-lingala/zip4j, https://github.com/android/keyattestation, https://github.com/sign/translate, https://github.com/saturday06/VRM-Addon-for-Blender, https://github.com/jitsi/jitsi-meet

Other: https://vroid.com/en/studio/guidelines, https://www.sandes.gov.in/resources/um/faq.pdf, https://globalsymbols.com/symbolsets/mulberry/symbols/5621?locale=en, https://f-droid.org/en/packages/org.libreaac.app/, https://developer.vroid.com/en/guidelines/conditions_of_use.html, https://huggingface.co/datasets/Exploration-Lab/CISLR, https://data.mendeley.com/datasets/2vfdm42337/1, https://aikosh.indiaai.gov.in/home/models/details/bhashini_ai4bharat_textual_language_detection_v1_0.html, https://arxiv.org/pdf/2110.05877
