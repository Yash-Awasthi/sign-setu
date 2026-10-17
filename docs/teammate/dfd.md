# Sign Setu: Data Flow Diagrams

Notation: rectangles are external entities, rounded boxes are processes, cylinders are data stores, dashed subgraphs are trust boundaries. Process numbers 1.0, 2.0, 3.0, 3.1, 5.0 and 7.0 match the DFD column of [02-architecture.md](../02-architecture.md) §4. Process 6.0 here groups what that table calls 3.5 (capture) and 3.6 (reading), since 6.0 there is the on-device assistant; 8.0 (content pipeline) is local to this document.

## 1. Level 0: context

```mermaid
flowchart LR
  OFF[Officer]
  SUB[Subject]
  SUP[Support person]
  REV[Reviewer]
  STG[Stage device]
  MSG[Sandes or WhatsApp]
  SHR[Share target]
  APP((Sign Setu app))

  OFF -- questions, text, confirms, role, notes --> APP
  APP -- proposals, answers, trails, grades --> OFF
  APP -- cards, captions, signs, clips, audio --> SUB
  SUB -- taps, drawings, typed text, camera frames --> APP
  SUP -- word per sign via officer --> APP
  APP <-- encrypted link frames --> STG
  APP -- contact intent, no session data --> MSG
  APP -- encrypted PDF --> SHR
  REV -. reviewed content at build time .-> APP
```

## 2. Level 1

```mermaid
flowchart TB
  OFF[Officer]
  SUB[Subject]
  STG[Stage device]
  SHR[Share target]
  REV[Reviewer]

  P1([1.0 Unlock and session control])
  P2([2.0 Officer panel and asking])
  P3([3.0 Stage and text to sign])
  P31([3.1 Avatar rig])
  P6([6.0 Sign capture and reading])
  P7([7.0 Pairing and link])
  P5([5.0 Export])
  P8([8.0 Content pipeline])

  D1[(D1 SQLCipher Room DB)]
  D3[(D3 DataStore settings)]
  D2[(D2 Assets plus manifest)]
  D4[(D4 In-memory landmarks)]
  KS[(Keystore KEK)]

  OFF -- credential --> P1
  P1 -- unwrap DEK --> KS
  P1 -- open, resume, purge --> D1
  P1 -- read --> D3
  OFF -- picks, free text, confirm --> P2
  P2 -- event before UI --> D1
  P2 -- confirmed item --> P3
  P3 -- read verified --> D2
  P3 --> P31
  P31 -- signs, gestures --> SUB
  SUB -- taps, drawing, text --> P3
  P3 -- answer event --> D1
  SUB -- frames in window --> P6
  P6 -- landmarks --> D4
  P6 -- proposals --> OFF
  P6 -- picked answer, sets shown --> D1
  P2 -- Show rev, itemId --> P7
  P7 <-- frames --> STG
  P7 -- answer then Ack --> D1
  OFF -- end, export --> P5
  P5 -- read session --> D1
  P5 -- PDF AES-256 --> SHR
  REV -- approvals --> P8
  P8 -- files, SHA-256 manifest --> D2
```

| Process | Code | Requirements |
| --- | --- | --- |
| 1.0 Session control | `MainActivity`, `AppContainer`, `ui.start`, `data.session` | FR-05, FR-06, FR-33, FR-34, FR-61, FR-67, FR-101 |
| 2.0 Officer panel | `ui.session`, `data.content` (`IslLexicon`, `Lesson`) | FR-10 to FR-19, FR-77 to FR-83, FR-88 to FR-91 |
| 3.0 Stage, text to sign | `ui.session` stage, `data.content/TextSigning.kt` | FR-13, FR-20 to FR-24, FR-69, FR-70, FR-95 to FR-107 |
| 3.1 Avatar | `ui.avatar` (`Rig.kt`, `Avatar.kt`) | FR-11, FR-13 |
| 6.0 Sign capture | `camera`, `sign` | FR-84 to FR-87 |
| 7.0 Pairing | `pair`, `ui.pair`, flavor `src/standard`, `src/nearby` | FR-72 to FR-76 |
| 5.0 Export | `export` | FR-32, FR-94, FR-104 |
| 8.0 Content pipeline | `portal/`, `tools/content/build_content.py` | SEC-54, SEC-74 |

### 2.1 Session

```mermaid
flowchart LR
  OFF[Officer] -- credential --> U([1.1 BiometricPrompt])
  U -- authorise --> KS[(Keystore KEK)]
  KS -- DEK in memory --> DB[(D1 SQLCipher DB)]
  OFF -- action --> VM([1.2 SessionViewModel])
  VM -- write event first --> DB
  DB -- committed row --> VM
  VM -- SessionUi StateFlow --> UI([1.3 Officer panel or stage])
  W([1.4 Retention worker]) -- delete, VACUUM --> DB
  S[(D3 DataStore)] -- retention, Auto allowed --> W
```

### 2.2 Text to sign

```mermaid
flowchart LR
  OFF[Officer] -- typed text --> T([3.2 TextSigning plan])
  A[(D2 verified assets)] -- lexicon, gestures, cards --> T
  T -- per word: recorded, authored, gesture, fingerspell, picture --> OFF
  OFF -- confirm --> L([3.3 Log PROMPT and QUESTION_SHOWN])
  L --> DB[(D1)]
  L --> R([3.1 Rig timeline])
  R -- drawn frames --> SUB[Subject]
```

### 2.3 Pairing

```mermaid
sequenceDiagram
  participant O as Officer phone
  participant S as Stage device
  O->>S: Hello1 (identity, commitment)
  S->>O: Hello2 (identity, public key, nonce)
  O->>S: Reveal (public key, nonce)
  Note over O,S: ECDH P-256, HKDF-SHA256, direction keys, code, check number
  S-->>O: 6-digit code typed by officer
  O->>S: Confirm
  S->>O: Confirm (Match)
  O->>S: Show (rev, itemId, view) after logging
  S->>O: Answer (itemId, n, body)
  O->>S: Ack (itemId, n) after commit
  O->>S: Bye
```

### 2.4 Export

```mermaid
flowchart LR
  DB[(D1)] -- session, events, drawings --> C([5.1 Compose PdfDocument A4])
  C -- plain PDF in app-private cache --> E([5.2 PdfBox AES-256])
  P([5.3 One-time passphrase]) -- shown once --> OFF[Officer]
  P --> E
  E -- encrypted PDF via FileProvider --> SH[Share sheet]
  F([5.4 Cache wipe]) -- on finish, clear, unlock --> C
```

### 2.5 Content pipeline

```mermaid
flowchart LR
  REV[Reviewer] -- approve items, signs, motions --> PO([8.1 portal/])
  SRC[(content sources)] --> PO
  SRC --> B([8.2 build_content.py])
  B -- normalised LF JSON, SHA-256 --> M[(D2 assets + manifest.json)]
  M -- APK packaging --> APK[(APK)]
  APK --> V([8.3 Content.kt verify])
  V -- pass: parse and show --> APP([App processes])
  V -- fail: never parsed --> X[Item shown as not available]
```

## 3. Level 2: sign capture (6.0)

```mermaid
flowchart TB
  SUB[Subject]
  OFF[Officer]
  CAM([6.1 CameraX front camera])
  WIN{6.2 Sign window open?}
  MIR([Mirror preview only])
  HOL([6.3 HolisticTracker MediaPipe, GPU then CPU])
  BUF[(D4 Landmark buffer, view-model memory)]
  SEG([6.4 SignWindow pause segmenter])
  RD([6.5 LandmarkSignReader 262 words])
  CH([6.6 SignChoices context ranking])
  CF([6.7 Subject confirm: 2 or 3 pictures + None of these])
  LOG([6.8 Event log])
  DB[(D1 SQLCipher DB)]
  MOD[(D2 holistic_landmarker.task, sign_reader.bin, sign_reader.json)]

  SUB -- frames --> CAM
  CAM --> WIN
  WIN -- no --> MIR
  WIN -- yes: Read signs 20 s, Tell me 3 min --> HOL
  MOD -- manifest-verified load --> HOL
  MOD -- manifest-verified load --> RD
  HOL -- LandmarkFrame --> BUF
  BUF --> SEG
  SEG -- segments --> RD
  RD -- ranked labels --> CH
  CH -- proposals, read by the app, not checked --> OFF
  OFF -- send to subject --> CF
  CH -- alternative set --> LOG
  LOG -- set logged before shown --> DB
  LOG --> CF
  CF -- picks --> SUB
  SUB -- pick --> LOG
  LOG -- ANSWER_SIGNED or ANSWER_UNCLEAR --> DB
  BUF -- replay 1/4x, 1/2x with markers --> OFF
```

| Step | Code | Rule |
| --- | --- | --- |
| 6.2 | `sign/SignWindow` | Analysis only inside a window (FR-84) |
| 6.3 | `camera/HolisticTracker`, `LandmarkTrackers` | Model hash checked by `HolisticTracker.verifiedModel` (SEC-74); telemetry stubbed (SEC-72) |
| D4 | `SignCapture` in view model | Memory only; no face points kept for Tell me; wiped at session end, freed on lock (SEC-71) |
| 6.5 | `sign/LandmarkSignReader`, `SignReaders.create` | Plain Kotlin, no inference library |
| 6.6 | `sign/SignChoices` | Item answer cards rank first (D-38); never shown to subject automatically (FR-85) |
| 6.7 | stage UI | Only the subject's pick makes it an answer (FR-86) |

## 4. Data stores

| Store | Location | Contents | Protection | Lifetime |
| --- | --- | --- | --- | --- |
| D1 SQLCipher Room DB | App-private, `data.session` | Session, Event, Drawing (schema v3) | SQLCipher, 256-bit DEK wrapped by Keystore KEK (SEC-01) | Retention 4 to 72 h, default 24 h; purge plus `VACUUM` |
| D3 DataStore | App-private `settings`; plain prefs `locale`, `stage_guard` | Settings only, no personal data (SEC-07) | App sandbox, excluded from backup | Until Clear all data |
| D2 Assets plus manifest | APK `assets/` | Bank, lexicon, cards, audio, motions, models; `manifest.json` SHA-256 per file | Signed APK; hash check before use (SEC-54, SEC-74) | Per build |
| D4 In-memory landmarks | `SessionViewModel` heap | Landmark frames, segments, readings | Memory only, never written | Session; wiped at end, freed on lock |
| Keystore KEK | Android Keystore | AES key wrapping the DEK | Non-exportable, auth-bound, hardware-backed where available | Until reinstall or lock removal |
| Link keys, resume secret | Memory, both devices | Direction keys, resume key | Never persisted | Per link; zeroed on Bye, expiry, lock |
| Export cache | App-private cache | Plain and encrypted PDF | Deleted on finish, Clear all, every unlock (SEC-02) | Minutes |

## 5. Trust boundaries

```mermaid
flowchart LR
  subgraph TB1 [Officer phone sandbox, unlocked]
    direction TB
    APP([App processes])
    D1[(D1)]
    D4[(D4)]
  end
  subgraph TB2 [Android OS]
    KS[(Keystore)]
    BP([BiometricPrompt])
    SS([Share sheet])
  end
  subgraph TB3 [Peer device]
    STG[Stage device]
  end
  subgraph TB4 [Build time]
    REV[Reviewer] --> PO([portal and build_content.py])
  end
  SUB[Subject] -- untrusted taps and frames --> APP
  APP <-- authenticated encrypted frames --> STG
  APP --> KS
  BP --> APP
  APP -- encrypted PDF only --> SS
  PO -- hashed assets in signed APK --> APP
  classDef b stroke-dasharray: 5 5
  class TB1,TB2,TB3,TB4 b
```

| Boundary | Crossing | Control |
| --- | --- | --- |
| Subject to app | Taps, text, camera frames | Input caps (FR-71, FR-102); readings need subject pick (FR-86); pinning (FR-61); `filterTouchesWhenObscured` (SEC-45) |
| App to OS | Keystore, BiometricPrompt, share sheet | Auth-bound KEK (SEC-21); only encrypted PDF leaves the sandbox (FR-32) |
| Officer phone to stage device | Link frames | ECDH commit-reveal, HKDF direction keys, code plus check number (SEC-67); stage stores only `stageDevice` (FR-73) |
| Build to runtime | Content and models | SHA-256 manifest, failed files never parsed (SEC-54, SEC-74) |
| App to network | None | No INTERNET permission; build fails if merged (SEC-30) |
