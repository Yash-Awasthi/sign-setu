# 05. UX and Interaction Design

## 1. Design principles

1. **Calm and predictable.** Muted colours, no sudden sound, no flashing, no pop-ups on the subject side. Motion only where it carries meaning (signs).
2. **Pictures before text.** The subject side must work for someone who cannot read.
3. **Officer in control.** Nothing reaches the subject panel without an officer action (FR-15).
4. **Always a fallback.** Every input and output has another path when a service or permission fails.
5. **Large targets, high contrast.** For stress, shaking hands, sunlight and small screens.
6. **Trauma-aware wording.** Short, gentle, non-judgemental. Safety and comfort first; sensitive questions never first (FR-12).
7. **Nothing leaves the phone without a reason.** Visible indicators whenever camera or microphone is live.

## 2. Main screen

Portrait phone, one screen, two halves.

```
+--------------------------------+
| [Lang HI v] [Flip] [Focus] [=] |  officer bar
| +----------------------------+ |
| | Type or hold mic to ask... | |  input + mic
| +----------------------------+ |
| [Safety][Health][Needs][More>] |  triage groups
| (Are you safe?) (Are you hurt?)|  preset chips
| Answer: [card: Water]  12:03   |  latest response
|--------------- = --------------|  draggable divider
|      +------------------+      |
|      |  ISL clip/avatar |      |  subject panel
|      +------------------+      |
|  "Do you need water?"          |  large caption
|  [ YES ]   [ NO ]   [water]    |  big cards
|  [camera]   [pen]              |  response controls
+--------------------------------+
```

- **Flip** rotates the subject panel 180 degrees for a subject sitting opposite (FR-02).
- **Focus** makes the subject panel full screen; a small officer handle remains at the top edge to come back (FR-03).
- The officer panel never shows the subject anything; the subject panel never shows officer notes or the log.

## 3. Supporting sheets

Bottom sheets over the main screen, never separate apps or browser views.

| Sheet | Content |
| --- | --- |
| Picture board | Categories (food, water, toilet, pain, body parts, family, home, police, vehicle, medical, places, feelings, yes, no), search, favourites |
| Notes and log | Timeline of questions and answers, notes field, export, end session |
| Settings | Officer language, text size, avatar speed, auto-delete period, clear all data, about, legal notices, open-source licences |
| First run | Three screens: what the app is (an aid, not an interpreter), language choice, permissions explained |

## 4. Key flows

**Preset question.** Tap group, tap question. Subject panel immediately shows clip, caption, Yes/No and matching cards. Officer sees "shown at 12:03".

**Free text.** Type, tap send, confirm preview. Caption appears at once; avatar plays when sign data arrives; "sign not available" otherwise.

**Voice.** Hold mic (red "recording" indicator), release. Transcript appears in the input box for editing. Officer taps send; same as free text.

**Subject signs.** Officer or subject taps camera. Camera indicator shows on both panels. Officer sees recognised words with a confidence badge (High / Medium / Low) and an "Ask again" button. Low confidence is shown greyed with a question mark.

**Subject taps a card.** Card briefly enlarges on the subject panel; officer sees the card and its label in the officer language.

**Subject draws.** Pen opens a canvas over the subject panel with pen, eraser and clear. Officer sees it live and can tap "Save to notes".

**End session.** Menu, End session. Dialog: "Export before clearing?" with Export and Clear. Clear is final and says so.

## 5. Accessibility

Applies GIGW 3.0 and IS 17802 (aligned with WCAG 2.1 AA) to the officer interface, and stricter rules to the subject panel.

| Area | Rule |
| --- | --- |
| Targets | 48 dp minimum; subject cards 96 dp minimum (NFR-20) |
| Contrast | 4.5:1 for text, 3:1 for icons and card borders (NFR-21) |
| Colour | Yes/No also differ by icon and position, not colour alone |
| Text size | Follows system font scale; officer panel reflows up to 200% (NFR-22) |
| Screen reader | Officer controls have content descriptions; TalkBack order follows visual order (NFR-23) |
| Motion | Avatar and clips start only on officer action; no autoplay loops; respects "remove animations" setting for non-sign motion |
| Sound | Silent by default; officer can enable spoken captions |
| Language | All text from resources; script-specific Noto fonts; layout tested with longest translations |

## 6. Visual style

- Background: soft neutral; subject panel slightly warmer than officer panel so the halves are distinct.
- One accent colour for officer actions; subject panel uses only neutral, card and Yes/No colours.
- Typeface: Noto Sans family across scripts for consistent shapes.
- Icons: simple filled outlines, consistent with card style.
- Cards: flat illustrations, no photographs of real people, no frightening imagery (police and medical cards drawn friendly).
- Dark theme for officers at night; subject panel stays calm in both themes.

## 7. Content style

| Do | Avoid |
| --- | --- |
| "Are you hurt?" | "Did someone hurt you?" as a first question |
| "You are safe now." | Promises the officer cannot keep |
| "Can you show me?" | Leading questions with suggested answers |
| Present tense, one idea per question | Double questions ("Are you hungry or thirsty?") |

Every subject-facing string, translation, card and ISL clip goes through the [content policy](../governance/policies/content-policy.md) review before release.

## 8. Wireframes and prototypes

Produce low-fidelity wireframes of the main screen, each sheet and each flow in phase 0, and test them with at least three people before phase 1 day 2. Store exported wireframes in `docs/design/` when created.
