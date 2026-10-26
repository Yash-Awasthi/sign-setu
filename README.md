# Sign Setu

An offline Android app that helps a police officer communicate with a person who cannot communicate in words: deaf, non-verbal, traumatised or speaking another language. A 3D character signs and gestures, pictures and captions carry the question, typed text is played in draft Indian Sign Language order, and signed answers are read on the phone inside explicit sign windows. The app is a communication aid, not an interpreter, and never records statements.

- Version 0.7.5 (version code 15). Android 10 (API 29) and later; Kotlin and Jetpack Compose; one `app` module.
- Works fully offline. One build carries the department gateway (`-PgatewayUrl=https://...`): turning the online assistant on asks the officer to continue with the gateway, which enrols with a one-time department code, or to set a custom API endpoint (any OpenAI-compatible service) in the same dialog. Either is used only while online and once the officer switches the online assistant on; otherwise nothing ever connects. No assistant key is ever compiled into an APK. No RECORD_AUDIO permission, and no location or scanning permission in the `standard` flavor; every build fails if a forbidden permission reaches the merged manifest.
- Optional on-device assistant: an instruction-tuned small language model (Gemma 3 1B Q4_K_M GGUF, about 806 MB, from [Hugging Face](https://huggingface.co/ggml-org/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf)) that the department hands out as a file; the app imports it in Settings and keeps it only if its SHA-256 is on the allow-list. The app never downloads it.
- A Type-to-Sign practice screen (Settings, Practice) plays any Hindi or English sentence outside a session, with nothing recorded.
- Raised hands open a sign window on the stage, and it closes by itself when the hands rest for 2 s, nothing is signed for 3 s, or 20 s pass.
- Sessions live in an encrypted SQLCipher database under a Keystore key that works only while the phone is unlocked. A finished session is saved under a name the officer types; every session is deleted 24 hours after its last activity by default, by an hourly background job even if the app is never opened; an officer or department can pick 4 to 72 hours or keep sessions until deleted.

---

## Screenshots

<a href="docs/screenshots/showcase.jpg"><img src="docs/screenshots/showcase.jpg" width="100%" alt="Sign Setu on a Pixel 9: home, read signs, transcript, picture board, question bank and ISL dictionary"></a>

---

## What it does

- **Stage for the person:** a 3D character signs questions in draft ISL order (recorded ISL signers for most words, recorded sentence clips where a typed sentence matches one), with pictures, captions in 17 languages and recorded speech; answers by tap, picture, writing, drawing, nod or shake, finger count, or signing to the camera; a signed meaning becomes the person's own only when they pick it from pictures. Water and food can be asked for at any moment and are logged as requests, not answers.
- **Sign reading:** MediaPipe Holistic landmarks feed small on-device readers for ISL and ASL words and fingerspelling. Guesses that answer the question on screen, through its own answer cards (`sign_words.json`, `cards`), are ranked first. The reading goes to the officer only; the officer can then ask the person to choose the meaning from pictures. Accuracy is in [tools/ml/EVAL.md](tools/ml/EVAL.md).
- **Officer side:** quick two-way transcription, a question bank including a full ISL lesson sheet (introduction, family, address, school, everyday phrases) in Hindi and English, phrases, show-back, an ISL dictionary and Manual or Auto conversation. Back leaves a session for the start screen without ending it; with a second screen connected it asks first, because leaving drops the link. Settings has Appearance (device default, light or dark), a pure-black AMOLED switch for dark mode, and colour themes: Emerald, Mocha, Cerulean, Amethyst, High contrast, and fourteen schemes from Mihon (Catppuccin, Green apple, Lavender, Midnight dusk, Monochrome, Nord, Strawberry, Blue, Tako, Teal and turquoise, Tidal wave, Tokyo night, Yin and yang, Yotsuba).
- **Second screen:** a paired phone shows the stage over bonded Bluetooth.
- **AI counsellor:** with the online assistant on, it can talk with a frightened victim or witness on the stage, on this phone or a second screen; each line waits 2 s on the officer's screen, where the officer can send it at once, replace it or take over; it is never offered for an accused person or anyone in custody (`ui/session/Counsellor.kt`).
- **Records:** finished sessions are listed on the start screen, can be opened again, and export to a password-protected PDF with a hash chain and a draft BSA s.63 certificate.

## Build and test

The build needs JDK 21, Android SDK 37, NDK `30.0.16248370` and CMake 4.1.1. Dependencies are pinned in `gradle/libs.versions.toml`, locked in `app/gradle.lockfile` and verified by `gradle/verification-metadata.xml`. Build from a checkout outside a OneDrive folder: Files On-Demand marks sources as reparse points, which Java reports as "not a regular file", and the annotation processing step then fails.

| Command | Result |
| --- | --- |
| `./gradlew assembleStandardDebug` | Debug APK |
| `./gradlew testStandardDebugUnitTest` | Unit tests (also `testNearbyDebugUnitTest`) |
| `./gradlew connectedStandardDebugAndroidTest` | Instrumented tests on an emulator or phone |
| `cd avatar && npm install && npx vp test` | Avatar module tests: rig, content motions and retargeting; `npx vp build` rebuilds `app/src/main/assets/avatar/avatar.js` |
| `cd portal && npm install && npx vp test` | Reviewer portal tests (needs `npm install` in `avatar/` first) |
| `cd backend && pip install -e ".[test]" && pytest` | Department gateway tests |
| `python tools/content/build_content.py --manifest-only` | Rebuilds the content hash manifest; run after any content change |

Tests worth knowing:

- `LessonTest` and `LessonSigningAuditTest` pin the lesson sheet's wording and how each sentence is signed.
- `SignPipelineDeviceTest` runs real ISLRTC clips (`app/src/androidTest/assets/islrtc/`) through the camera pipeline. It must pass after any MediaPipe or protobuf update: MediaPipe 1.1.1 runs on `protobuf-javalite`, and the full runtime breaks the landmarker without failing a unit test.

## MVP — Live links

| Resource | URL | Notes |
| --- | --- | --- |
| 📱 Phone APK (arm64, ~83 MB) | [sign-setu-0.7.2-arm64-v8a-gateway.apk](https://github.com/Yash-Awasthi/sign-setu/releases/download/v0.7.2/sign-setu-0.7.2-arm64-v8a-gateway.apk) | Signed; calls the department gateway |
| 📦 Universal APK (arm64 + x86\_64, ~109 MB) | [sign-setu-0.7.2-universal-gateway.apk](https://github.com/Yash-Awasthi/sign-setu/releases/download/v0.7.2/sign-setu-0.7.2-universal-gateway.apk) | Signed; phones, tablets and emulators; calls the department gateway |
| 🏷️ GitHub release | [v0.7.2](https://github.com/Yash-Awasthi/sign-setu/releases/tag/v0.7.2) | All APKs |
| 🌐 Department gateway (API) | [signsetu-gateway.onrender.com](https://signsetu-gateway.onrender.com/healthz) | FastAPI on Render free tier; backed by Neon Postgres; cold-start ~30 s |
| 🖥️ Reviewer portal | [signsetu-portal.pages.dev](https://signsetu-portal.pages.dev) | Vite + React; static on Cloudflare Pages |
| 🤖 On-device model | [gemma-3-1b-it-Q4\_K\_M.gguf on Hugging Face](https://huggingface.co/ggml-org/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf) | ~806 MB; import via Settings → On-device assistant |
| 📖 Full documentation | [docs/README.md](docs/README.md) | Requirements, architecture, security, DPIA |

---

## Release

| Command | Output |
| --- | --- |
| `./gradlew assembleStandardRelease` | Universal APK, arm64-v8a and x86_64 (about 109 MB): phones, tablets and emulators |
| `./gradlew assembleStandardRelease -Pabi=arm64-v8a` | Phone-only APK (about 83 MB) |

Release builds use R8 with resource shrinking. Screenshots and screen recording stay open while testing; set `secureScreen=true` in `gradle.properties` (or pass `-PsecureScreen=true`) for the final APK, which turns on `FLAG_SECURE`. Add `-PgatewayUrl=https://...` (or `gatewayUrl=` in `local.properties`) for a gateway build. No assistant key is compiled into any build: the gateway holds the service key, and a custom endpoint takes the officer's own key in Settings. The APKs are signed with the release key, which is held offline; signing is a manual `zipalign` and `apksigner` step on that machine. See [docs/README.md](docs/README.md), section 5.

## Repository

| Path | Contents |
| --- | --- |
| `app/` | The Android app (`app.signsetu`; application ID `in.signsetu`), flavors `standard` and `nearby` (Play services) |
| `app/src/main/assets/content/` | Content bundle: questions, cards, motions, vocabulary, audio, models, hash manifest |
| `backend/` | Department gateway (FastAPI, SQLAlchemy): device enrolment, model manifest, assistant proxy, and an endpoint for consented feedback labels that the app does not call yet; optional ([README](backend/README.md)) |
| `avatar/` | The 3D character (TypeScript, three.js, three-vrm): keyframe rig, retargeting onto the VRM model, clip playback; built into `app/src/main/assets/avatar/` |
| `portal/` | Static reviewer portal for content sign-off (Vite+, React, TypeScript); not shipped ([README](portal/README.md)) |
| `tools/content/` | Content bundle, audio and sign builders; `build_content.py --manifest-only` |
| `tools/isl/` | Retargeting recorded ISL and ASL signers onto the character ([README](tools/isl/README.md)) |
| `tools/ml/` | Sign reader training, export and evaluation ([README](tools/ml/README.md)) |
| `tools/security/` | CycloneDX SBOM of the release APK (`sbom.py`, needs Syft) |
| `content/` | Source symbols for the picture cards |
| `render.yaml` | Free hosting blueprint for the gateway |
| `docs/` | Requirements, architecture, security and data protection assessment ([index](docs/README.md)) |
