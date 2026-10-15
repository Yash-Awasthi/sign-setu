# 04. Data Flow Diagrams

These diagrams are the basis of the [threat model](../governance/07-threat-model.md) and the [DPIA](../governance/08-privacy-dpia.md). Element IDs (E, P, D, F, TB) are referenced there.

Notation: external entities are rectangles, processes are rounded, data stores are cylinders, dashed boxes are trust boundaries.

## Level 0: Context

```mermaid
flowchart LR
  E1["E1 Officer"]
  E2["E2 Subject"]
  P0(["P0 Police Assist app"])
  E3["E3 Project API gateway"]
  E4["E4 Recipient of export\n(case file, CWC)"]
  E1 -- "questions, notes, settings" --> P0
  P0 -- "answers, sign results, session log" --> E1
  P0 -- "ISL, captions, pictures" --> E2
  E2 -- "card taps, signs (camera), drawings" --> P0
  P0 -- "text, landmarks, audio" --> E3
  E3 -- "sign keypoints, recognised text, transcript, translation" --> P0
  P0 -- "encrypted export" --> E4
```

## Level 1: App

```mermaid
flowchart TB
  E1["E1 Officer"]
  E2["E2 Subject"]
  E3["E3 API gateway"]
  E4["E4 Export recipient"]

  subgraph TB1["TB1 App sandbox on officer's phone"]
    P1(["P1 Officer input\n(text, voice, presets)"])
    P2(["P2 Present to subject\n(clips, avatar, captions, cards)"])
    P3(["P3 Subject input\n(cards, drawing)"])
    P4(["P4 Sign capture\n(camera to landmarks)"])
    P5(["P5 Session manager\n(log, notes, export, clear)"])
    P6(["P6 Network client"])
    D1[("D1 Session store\nencrypted")]
    D2[("D2 Bundled content\nread-only")]
    D3[("D3 Settings")]
    D4[("D4 Keystore key")]
  end

  E1 -- "F1 typed text, preset choice" --> P1
  E1 -- "F2 voice audio" --> P1
  P1 -- "F3 confirmed question" --> P2
  P1 -- "F4 audio / text for service" --> P6
  D2 -- "F5 clips, cards, captions" --> P2
  P2 -- "F6 visual output" --> E2
  E2 -- "F7 card taps, strokes" --> P3
  E2 -- "F8 camera image" --> P4
  P4 -- "F9 landmark windows" --> P6
  P6 -- "F10 HTTPS requests" --> E3
  E3 -- "F11 responses" --> P6
  P6 -- "F12 keypoints, transcript, recognised text" --> P1
  P6 --> P2
  P3 -- "F13 answer events" --> P5
  P1 -- "F14 question events, notes" --> P5
  P5 -- "F15 read/write events" --> D1
  D4 -- "F16 database key" --> P5
  P5 -- "F17 encrypted export file" --> E4
  D3 --> P1
```

## Level 2: Gateway and partner services

```mermaid
flowchart LR
  P6(["P6 App network client"])
  subgraph TB2["TB2 Project cloud, India region"]
    G1(["G1 Gateway: integrity check,\nrate limit, validate"])
    G2(["G2 Router"])
    D5[("D5 Access logs\nmetadata only, 180 days")]
    D6[("D6 Secret manager\npartner keys")]
  end
  subgraph TB3["TB3 Partner services"]
    S1(["S1 ISL generation"])
    S2(["S2 Sign recognition"])
  end
  subgraph TB4["TB4 Bhashini (MeitY)"]
    S3(["S3 Speech recognition"])
    S4(["S4 Translation"])
  end
  PI["E5 Google attestation
revocation list"]
  P6 -- "F10 HTTPS, pinned" --> G1
  G1 -- "F18 check key status" --> PI
  G1 --> G2
  G1 -- "F19 metadata" --> D5
  D6 -- "F20 keys" --> G2
  G2 -- "F21 text" --> S1
  G2 -- "F22 landmarks" --> S2
  G2 -- "F23 audio" --> S3
  G2 -- "F24 text" --> S4
```

## Trust boundaries

| ID | Boundary | Crosses it | Main concern |
| --- | --- | --- | --- |
| TB0 | Between officer and subject (same screen) | F6, F7, F8 | Subject seeing officer-only information; shoulder surfing by bystanders |
| TB1 | App sandbox on the phone | F1-F8, F10, F11, F17 | Lost or seized phone, other apps, backups, screenshots, rooted device |
| TB2 | Project cloud | F10, F18-F24 | Interception, abuse of API, secret theft, log leakage |
| TB3 | Partner services | F21, F22 | Partner storing or misusing data |
| TB4 | Bhashini | F23, F24 | Retention of audio and text by a third party |

## Data inventory

| Data | Personal? | Where | Flows | Retention |
| --- | --- | --- | --- | --- |
| Questions shown (text) | Possibly (may contain names, places) | D1 | F3, F14, F15, F21, F24 | Session life, max auto-delete period |
| Subject answers (cards, recognised text) | Yes, about the subject | D1 | F13, F15 | Same |
| Drawings | Yes, may contain names or faces | D1 (encrypted blob) | F13, F15 | Same |
| Officer notes | Yes | D1 | F14, F15 | Same |
| Camera images | Yes, biometric-like | Memory only | F8 | Not stored; discarded per frame |
| Hand and pose landmarks | Yes, derived from body | Memory, transit | F9, F10, F22 | Not stored by app; partner must not store [confirm] |
| Voice audio | Yes, officer and possibly subject | Memory, transit | F2, F4, F10, F23 | Not stored by app; Bhashini retention [confirm] |
| Export file | Yes | Leaves the phone | F17 | Recipient's responsibility; encrypted |
| Gateway access logs | Pseudonymous install hash, IP | D5 | F19 | 180 days (CERT-In direction) |
| Settings | No | D3 | | Until uninstall |
| Bundled content | No | D2 | F5 | Until update |
