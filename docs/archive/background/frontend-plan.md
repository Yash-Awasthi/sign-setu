# Police Assist App: Frontend Plan, Design and Architecture

Frontend team plan for the agile project. Version 1, written 15 October 2026. Target: working version in 15 days.

## 1. Purpose

The app helps police officers communicate with people who are found lost or recovered from kidnapping and cannot communicate in words. This includes people who are traumatised, mute, deaf, or both deaf and mute. The officer and the subject share one phone. The app translates the officer's words into Indian Sign Language (ISL) through an avatar, shows captions and pictures, and lets the subject answer by tapping pictures, signing to the camera, or drawing and writing.

## 2. Scope

**In scope for v1 (frontend)**

- Split-view interface for officer and subject on one phone
- Officer input by typed text, voice (server-side speech-to-text) and pre-set triage question flows
- 2D skeleton avatar that plays sign keypoints supplied by the ISL team
- Live captions in Hindi, English and regional languages
- Picture-card board (curated) and AI-generated images
- Subject input by picture cards, camera signing (sign-to-text via the ML team's model) and a drawing/writing canvas
- Officer notes and session record saved locally on the device
- Installable PWA, mobile-first

**Out of scope for the frontend team**

- ISL translation backend and keypoint generation (ISL team)
- Sign recognition model (ML team)
- Speech-to-text and image-generation servers (whoever hosts them; we only call them)
- Server-side case management, officer login, cloud sync
- Emotion or distress detection from the face (deliberately excluded: unreliable and sensitive)

## 3. Users and scenarios

**Officer.** Under pressure, possibly outdoors, holding one phone. Needs the shortest path from intent ("are you hurt?") to the subject seeing a sign, a caption and a picture.

**Subject.** May be frightened, a child, or unfamiliar with technology. Needs a calm, text-light screen with large targets and no surprises.

**Core scenarios**

1. Triage: the officer asks basic safety questions (are you safe, are you hurt, do you need water, food or medical help). The subject answers by tapping yes/no or a picture.
2. Information gathering: name, family, place, what happened, who to call. Mix of pre-set questions and free text.
3. Deaf subject: officer types or speaks, the avatar signs, the subject signs back and the app shows the recognised text to the officer.
4. Hearing but non-verbal subject: officer speaks, captions and pictures appear, the subject points to pictures or writes.
5. Fallback: any component fails (network, camera, server) and the officer continues with the picture board and typed text.

## 4. Decisions made so far

| Area | Decision |
| --- | --- |
| Stack | React with Tailwind, built as an installable PWA (Expo and Kotlin were considered; a web build is faster for a 15-day timeline and matches the single-page requirement. Capacitor can wrap it into an APK later if needed) |
| Device | Phone is the primary device |
| Layout | Split view on one screen, officer half and subject half |
| Avatar | 2D skeleton drawn on a canvas, driven by keypoint data |
| ISL output | Keypoints / pose data from the ISL team's API |
| Sign-to-text | Needed; handled by the ML team's separate model, we capture and send |
| Speech-to-text | Server-side (Whisper or similar) |
| Languages | Hindi, English and regional languages |
| Officer questions | Both pre-set flows and free text |
| Subject responses | Picture cards, signing on camera, drawing/writing |
| Images | Curated picture-card board and AI-generated images |
| Storage | Local on the device only |
| API status | The ISL and ML teams already have an API and a sample payload |

## 5. Design principles

1. **Calm and predictable.** Low-stimulation colours, no sudden sounds, no flashing, no pop-ups on the subject side. Motion only where it carries meaning (the avatar).
2. **Pictures before text.** The subject side should be usable with no reading ability.
3. **Officer in control.** Anything shown to the subject, especially AI-generated images, passes through an officer action first.
4. **Always a fallback.** Every input and output has an alternative path if its service fails.
5. **Large targets, high contrast.** Designed for stress, shaky hands, glare and small phones.
6. **Nothing leaves the phone without a reason.** Local-first storage, minimal data sent to servers, explicit clear-session action.
7. **Trauma-aware tone.** Short, gentle, non-judgemental wording in all subject-facing text and default questions. Questions about sensitive topics are never the first thing shown.

## 6. Screen design

### 6.1 Split view (main screen)

One page, divided vertically on a phone held upright.

**Top half: Officer panel**

- Input bar for typed text, with a microphone button for voice
- Quick-question strip: triage questions as large buttons, grouped (safety, health, needs, identity, family, location)
- Caption preview of what is about to be shown
- Subject response area: shows what the subject answered (tapped picture, recognised sign text, drawing thumbnail)
- Notes button and session menu
- Language selector and a flip button

**Bottom half: Subject panel**

- Avatar stage: the 2D skeleton, with replay and slow-motion controls
- Caption line in the chosen language, large font
- Picture-card area: cards relevant to the current question, large and few
- Response controls: tap cards, a camera toggle for signing, and a pen toggle for drawing/writing

**Flip control.** Rotates the subject panel 180 degrees so the officer can hold the phone between them and the subject reads it facing the right way. The divider can be dragged to change the proportion of each half. A "focus subject" mode makes the subject panel full-screen for moments when the officer wants nothing else visible.

### 6.2 Supporting views (modals or sheets, not separate pages)

- **Picture-card board.** Full categorised board (food, water, toilet, pain, police, family, home, vehicle, medical, yes, no, etc.), searchable by the officer, with favourites.
- **AI image generator.** Officer enters a short description, previews results, and explicitly sends one to the subject panel. A visible "back to cards" action is always available.
- **Notes and session log.** Chronological record of questions asked, responses received and free-form officer notes, with an export action.
- **Settings.** Language, avatar speed, text size, camera and microphone permissions, clear session data, about and safety information.
- **First-run screen.** Short explanation, language choice, permissions explained in plain words.

### 6.3 Key interaction flows

**Ask a question by text:** officer types, taps send, caption appears on the subject panel, text goes to the ISL service, keypoints return, avatar plays, the officer sees confirmation that the sign finished.

**Ask by voice:** officer holds the microphone, releases, audio is uploaded to the speech service, the transcript appears in the input bar for the officer to confirm or edit, then the same path as typed text. The officer always confirms before the subject sees anything.

**Ask a pre-set question:** officer taps a triage button. Caption, sign and matching pictures are shown at once. These can be cached on the device so they work with no network after first load.

**Subject signs back:** officer or subject turns on the camera. Frames or landmarks go to the ML service. Recognised text and a confidence indicator appear on the officer side, with an option to ask the subject to repeat.

**Subject taps a picture:** the selection is logged, shown to the officer with its caption, and optionally echoed back as a sign.

**Subject draws or writes:** a canvas captures the drawing; the officer sees it live and can save it to notes.

## 7. Architecture

### 7.1 Layers

1. **Presentation layer.** React components and Tailwind styling: layout shell, officer panel, subject panel, modals.
2. **State layer.** One lightweight store for session state (current question, caption, avatar queue, response history, notes, settings, language). Kept small and serialisable so it can be saved to the device.
3. **Service layer.** One module per external system, hiding endpoints and payload formats from the UI: speech-to-text, text-to-sign, sign-to-text, image generation. Each module has a real implementation and a mock implementation, switched by configuration.
4. **Device layer.** Camera, microphone, canvas, IndexedDB storage, service worker for offline caching.
5. **Content layer.** Static data bundled with the app: triage question sets, picture-card catalogue, translations, and cached sign keypoints for the pre-set questions.

### 7.2 Main components

| Component | Responsibility |
| --- | --- |
| App shell | Split layout, flip control, divider, focus mode, global error boundary |
| Officer panel | Input bar, quick questions, response viewer, notes entry |
| Subject panel | Avatar stage, captions, picture cards, response controls |
| Avatar player | Plays a sequence of keypoint frames on a canvas: body, hands, face; replay, pause, speed control, "sign not available" state |
| Caption component | Large, language-aware text with script-appropriate fonts |
| Picture board | Categorised cards, search, favourites, relevance to current question |
| Image generator | Prompt box, result preview, officer approval, send-to-subject |
| Camera capture | Permission handling, preview, frame or landmark extraction, on/off indicator |
| Drawing canvas | Pen, eraser, clear, save to notes |
| Voice recorder | Press-and-hold recording, level indicator, upload, retry |
| Notes and log | Timeline, search, edit, export, clear |
| Settings | Language, text size, speed, permissions, data clearing |

### 7.3 Data flow

1. Officer input becomes a **message** (source: text, voice or preset; language; text).
2. The message is confirmed by the officer and stored in the session log.
3. Three things happen in parallel: the caption is shown, matching picture cards are selected, and the text-to-sign service is called (or the cached sign is used for presets).
4. The sign response is converted to the avatar player's frame format and queued.
5. Subject responses (tap, sign, drawing) become **response events**, stored in the log and displayed to the officer.
6. The session record is written to IndexedDB after every event so a crash or reload loses nothing.

### 7.4 Integration contracts

The ISL and ML teams already have an API and sample payload. Before development, collect from them and document:

- **Text-to-sign:** endpoint, input fields (text, language), response structure (frame rate, number of frames, which landmarks per frame, coordinate system and scale, how hands and face are represented, how unknown words are reported), maximum text length, typical latency, error format.
- **Sign-to-text:** endpoint or client-side model, what it takes as input (full frames, cropped hands, landmark arrays), how often to send, response structure (text, confidence, language), how it signals no sign detected, latency.
- **Speech-to-text:** endpoint, audio format and maximum length, languages supported, response structure and error handling.
- **Image generation:** endpoint, prompt length, returned image format and size, content-safety behaviour, latency.

The frontend defines an internal, stable shape for each of these and a small adapter that converts the team's format into it. If a team changes their format later, only the adapter changes. Until real endpoints are reachable, the mock implementations use the sample payloads.

### 7.5 Offline and resilience

- The PWA shell, picture cards, triage questions, translations and cached preset signs work with no network after first load.
- Voice, free-text signing, sign-to-text and image generation need network or server access. If unavailable, the UI shows a plain notice and points to the available alternatives (typed text, picture board).
- Every service call has a timeout, one automatic retry and a clear failure state. No spinner runs for more than a few seconds without a visible way to cancel and use a fallback.
- If the camera or microphone permission is denied, the related buttons are replaced with an explanation and the alternative path.

### 7.6 Performance

- Avatar playback is capped at a steady frame rate suited to low-end phones and uses a single canvas with minimal redraw.
- Camera analysis runs at a reduced frame rate and resolution; frames are not sent when no hands are visible.
- Picture cards use compressed images and load lazily; the most-used set is precached.
- Fonts for the supported scripts are subset and cached.
- Target: first load usable within a few seconds on a mid-range Android phone on 4G.

### 7.7 Languages

- All interface text, triage questions and picture-card labels come from translation files. Hindi and English first, then regional languages as translations are supplied.
- Fonts covering Devanagari and the chosen regional scripts are bundled.
- Translated safety-critical questions (triage set) should be reviewed by a fluent speaker, not machine translation alone.
- The language sent to speech and sign services is explicit, never guessed.
- Note for the ISL team: ISL has its own grammar. Sending translated text from different languages may need different handling; confirm which input languages their service accepts.

### 7.8 Privacy and data handling

- Session data (log, notes, drawings) is stored only on the device, in IndexedDB.
- Camera frames, audio and prompts are sent only to the project's own services, only while the relevant feature is active, and are not stored by the frontend.
- Clear indicators when camera or microphone are live.
- A one-tap "end and clear session" action that deletes everything for that session; optional export before clearing.
- No third-party analytics or tracking.
- Consider an optional passcode or encryption for stored sessions, since the content may be sensitive. Decide with the team whether it is needed for v1.
- Confirm with the team whether there are legal or police-department requirements on recording and retention before any real-world use.

### 7.9 Accessibility and trauma-aware design

- Minimum touch target sizes, strong contrast, scalable text, no reliance on colour alone.
- Subject panel: minimal text, no timers or countdowns, no blinking or sudden movement, nothing that makes sound unless the officer chooses it.
- Sensitive questions are not default-visible on the first screen; the first questions are about safety and comfort.
- Default images and wording avoid graphic or frightening content; AI-generated images require officer approval and can be dismissed instantly.
- Officer interface works one-handed.

## 8. Testing plan

- **Device testing:** at least two Android phones (one low-end) and one iPhone, in portrait, with the browser in installed-PWA mode.
- **Integration testing:** each service against its mock and then against the real team's endpoint, including slow, failing and malformed responses.
- **Usability testing:** at least a few people unfamiliar with the app run the triage flow; observe where they hesitate. If possible, test with someone who knows ISL to judge avatar clarity, and with a counsellor or police contact for the question wording.
- **Failure drills:** switch off network, deny camera, deny microphone, kill the app mid-session and confirm recovery.
- **Language checks:** every supported script renders correctly and fits without overflow.

## 9. 15-day schedule

| Days | Work | Output |
| --- | --- | --- |
| 1-2 | Environment setup, folder structure, theme, translation setup, split-view shell with flip and focus mode, mock service modules | App runs on a phone with an empty but correct layout |
| 3-5 | Avatar player using the sample keypoints, caption component, playback controls, "sign not available" state | Officer types text, avatar plays a mock sign and shows a caption |
| 6-8 | Officer panel: typed input, voice recording and speech upload, triage question flows, picture-card board | Full officer-to-subject question flow with presets |
| 9-11 | Subject input: camera capture and sign-to-text, drawing canvas, AI image generation with officer approval, notes and local storage | Two-way communication loop working with mocks |
| 12-13 | Connect real ISL, ML, speech and image services; fix payload mismatches; add retries and fallbacks | Working end to end with real services |
| 14-15 | Device testing, accessibility pass, translation checks, error states, demo script and polish | Demo-ready build |

If time runs short, cut in this order: regional languages beyond Hindi and English, AI image generation, drawing canvas, optional encryption. Never cut the picture board, the fallbacks or the clear-session action.

## 10. Dependencies on other teams

| Need | From | Needed by |
| --- | --- | --- |
| Final API documentation and sample payloads for text-to-sign | ISL team | Day 2 |
| Sample keypoints for at least a few preset questions, with the vocabulary list | ISL team | Day 3 |
| Sign-to-text interface and input requirements | ML team | Day 8 |
| Speech-to-text endpoint and format | Whoever hosts it | Day 6 |
| Image-generation endpoint and safety behaviour | Whoever hosts it | Day 9 |
| Hosted test environment reachable from a phone | All backend teams | Day 11 |
| Reviewed translations for the triage set | Team or domain contact | Day 10 |

## 11. Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Real-time keypoint playback stutters on weak phones | Cap frame rate, minimal redraw, test on a low-end phone by day 5 |
| ISL vocabulary is small at first | Clear "sign not available" state, always show caption and a picture with it |
| Sign-to-text accuracy is limited | Show confidence, let the officer ask for a repeat, never treat a recognition as certain; always offer picture and writing alternatives |
| Speech recognition errors in Hindi or regional accents | Officer confirms transcript before it is shown or signed |
| Server downtime or slow responses in the field | Offline presets and picture board, timeouts and fallbacks |
| AI images are wrong or distressing | Officer approval step, instant dismiss, curated cards as default |
| Payload format changes mid-project | Adapter layer isolates each service |
| Sensitive data on a shared phone | Local-only storage, quick clear, optional passcode |
| Scope creep in 15 days | Fixed priority order for cuts (section 9) |
| Misinterpretation could affect a real case | State clearly in the app and demo that it is an aid, not a replacement for a trained interpreter or counsellor |

## 12. Open questions

1. Exact ISL payload details: coordinate system, hand and face landmarks, frame rate, vocabulary size.
2. Does the sign-to-text model run in the browser or on a server, and what does it take as input?
3. Which regional languages are needed for v1, and who supplies and reviews translations?
4. Who builds the triage question set and the picture-card content, and who reviews wording for sensitivity?
5. Is a passcode or encryption for local data required for v1?
6. Is there any official requirement from police or child-welfare contacts about recording or retention?
7. Is there a Figma file, brand guideline or design system from the team?
8. What is the demo format (live on a phone, recorded, projector) and who is the audience?
9. Will the app need officer sign-in later, and should the architecture leave room for it?
10. Is an Android APK wrapper needed after the PWA is working?

## 13. Next steps

1. Collect the ISL and ML sample payloads and fill in section 7.4.
2. Agree this plan with the team and confirm the open questions that block work (items 1, 2 and 3).
3. Produce wireframes of the split view and the supporting sheets.
4. Begin the day 1-2 work: project setup and layout shell.
