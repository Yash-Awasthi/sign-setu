# 34. Accessibility audit

A static audit of the interface at `402339f`: what a person can operate, what a screen
reader is told about it, and what the switch path does. Nothing here was measured on a
phone or an emulator; every claim below is read from the source or computed by a script
in the repository, and the doc says which.

What was checked:

- every interactive control in `ui/**`: 262 sites in 60 files, counted by
  `tools/ux/`-style regex over `.clickable`, `.toggleable`, `.selectable`,
  `.combinedClickable`, `Button`/`OutlinedButton`/`TextButton`, `IconButton`,
  `Surface(onClick = ...)`, `Switch`/`Checkbox`/`RadioButton`, `Slider`, and
  `Tab`/`Chip`/`SegmentedButton`;
- the name, role and state each one reports (`contentDescription`, `Role`,
  `selected`, `stateDescription`), and whether a visual-only state exists that a
  screen reader cannot see;
- reading order: the order Tab and the arrow keys walk, against the order the controls
  are drawn;
- the switch path end to end: Off, Dwell and Step, from the setting to the answer bar
  and the picture grid;
- the 48 dp floor, which is already covered by `TouchTargetsTest` and is referenced
  here rather than repeated;
- theme contrast: recomputed in Python from the palette hex values, all 480 pairs (10 themes x 3
  modes x 16 pairs), which is every row the script prints.

## The controls, by area

| Area | Controls | Files |
| --- | --- | --- |
| First run | 4 | 1 |
| Home, saved sessions, language | 21 | 3 |
| Settings, appearance, brain, developer | 44 | 8 |
| Practice and record-signs | 19 | 3 |
| Help and the walkthrough | 5 | 1 |
| Pairing and the stage device | 16 | 3 |
| The session stage (the person's side) | 85 | 25 |
| The officer page | 56 | 11 |
| Interpreter call, root and shared components | 12 | 5 |

Of the 262, 117 are `Button`-family, 25 `IconButton`, 38 `Surface(onClick)`, 49 modifier
taps, 12 switch/checkbox/radio, 20 tabs and chips, 1 slider.

## Per screen

`n` is the number of interactive controls. **Named** says how the control is announced;
a text child is enough, because `Surface(onClick)` and `clickable` merge their
descendants. **State** means a visual distinction that a screen reader must be told
about. **Switch** means the control is on the answer flow (Dwell or Step).

| Screen | n | Named | State | 48 dp | Switch |
| --- | --- | --- | --- | --- | --- |
| First run | 4 | text and icon labels | step dots are decorative | yes | no |
| Unlock | 0 | the credential window is the system's | no | yes | no |
| Stage device | 8 | text | no | yes | no |
| Home | 21 | text and icon labels; the language tile names its language | chosen language is `selected` | yes | no |
| Settings | 40 | every row's text is the label; radios and switches are `selectable`/`toggleable` | radio and switch state come from those modifiers | yes | no |
| Appearance | 4 | text; the theme swatch is named by its theme | the chosen theme is `selected` | yes | no |
| Practice, sign-to-type, record signs | 19 | text and icon labels | consent switch is a `Role.Switch` toggle | yes | no |
| Help | 5 | text and icon labels | topic headings use `heading()` | yes | no |
| Pairing sheet | 8 | phone rows name the phone | busy state is a progress row | yes | no |
| Session stage | 85 | every answer and tile has a name: its word, or the picture's English word when the language has none | tool in use, language, chosen tile were colour-only; fixed below | yes, and 56 dp on the subject surfaces | yes |
| Officer page | 56 | text and icon labels; transcript images name themselves | no | yes | no |
| Interpreter call, root, shared components | 12 | text | no | yes | no |

Not on the switch path: the officer pages, settings and start. A switch user is the
person in the session, and the bark of that path is the answer bar and the picture
grid; the officer's own screens are driven by a keyboard or a pointer, and a keyboard
already walks them because every Material control is focusable.

## The switch path

`Settings > Access` writes one of `SwitchAccess.OFF`, `DWELL` or `STEP`
(`ui/settings/SettingsScreen.kt:141`). The three are `ChoiceItem`s, so the chosen mode
is announced as a radio button's `selected`, not by colour.

On the stage the mode reaches two flows, the answer bar (`VideoCallStage.kt:226`) and
the picture or number grid (`StageCharacter.kt:192`), as one `AnswerOrder` each: the
control ids in the order they are drawn, with the column count, so Tab, the arrows and
the dwell scan all walk what the eye sees
(`ui/session/AnswerKeys.kt`, `AnswerOrder`).

Dwell moves the highlight every 1200 ms, checked every 60 ms. Step keeps it still: a
tap moves on, a hold of 900 ms chooses. Both are walked by a timer in
`rememberAnswerFlow`, and the ring round the focused control is drawn by
`focusRing`, white outside blue inside so it reads on every answer colour.

Three defects were found and two of them were fixed:

1. **The dwell scan kept running under a sheet.** Opening the language, pictures,
   emoji, typing or drawing sheet does not stop the bar's timer. The scan moved on
   behind the sheet and, in dwell mode, chose a control the person could not see, so
   closing the sheet could land on an answer they never picked. Fixed: a `paused`
   flag on `rememberAnswerFlow`, set while `sheetFor != null`; time under the sheet is
   cut out of the scan's clock, so the highlight is where they left it.
2. **Off is only a tap target, not a way out.** With Off the scan never starts, which
   is correct, but the same row is the only way back to On. A switch user who cannot
   reach the settings row is stuck. Not fixed: turning the setting off is an officer
   action, and the officer's own screens are pointer-driven.
3. **The scan highlight was colour-only for a screen reader.** The ring is drawn, not
   announced. Not fixed: `answerTarget` does not know each control's label, so a live
   region here would need the label threaded through every call site. Recorded below
   as the next change rather than half-done.

## Reading order

`AnswerOrder` is built in draw order, row by row, so Tab and the arrows agree with the
eye; `answerBarIds` (`AnswerKeys.kt`) names the bar's controls in the order
`VideoCallStage` lays them out, including the three answers that appear behind More.

There is no `traversalIndex` anywhere in the app and no explicit
`mergeDescendants`: controls are groups because they are clickable, which merges their
text into one announcement. That is the right default here — the tiles are one thing,
not a picture and a word, and the ticket is a heading plus a button.

## Contrast, recomputed

`tools/ux/contrast.py` reads the hex values out of `Palettes.kt` and
`MihonPalettes.kt`, fills tokens a scheme leaves unset from the Material 3 baseline,
composites translucent ink, and prints the ratio for every pair. It does not read the
document. For a borrowed scheme's band ink it repeats Compose's
`lerp(colour, black, fraction)` in Oklab, because that is what `mihon()` builds.

```
python tools/ux/contrast.py            # the full table
python tools/ux/contrast.py --failing  # only pairs under their floor
python tools/ux/contrast.py --doc      # every row of docs/26 against a recomputation
```

`--doc` reports 480 pair cells, 40 band cells and 40 call-stage cells, and found no
cell that disagreed by more than 0.03. The document's numbers are the measurement, not
a copy of the code.

The worst cell for each pair, across the ten themes and three modes:

| pair | floor | worst | where |
| --- | --- | --- | --- |
| `onBackground/background` | 4.5 | 4.52 | Tokyo Night light |
| `onSurface/surface` | 4.5 | 6.27 | Tokyo Night light |
| `onSurfaceVariant/surfaceVariant` | 4.5 | 4.54 | Tokyo Night dark |
| `onSurfaceVariant/surface` | 4.5 | 6.15 | Tokyo Night light |
| `onSurface/surfaceContainer` | 4.5 | 4.90 | Tokyo Night light |
| `onPrimary/primary` | 4.5 | 4.77 | Teal Turquoise light |
| `onPrimaryContainer/primaryContainer` | 4.5 | 4.77 | Teal Turquoise light |
| `onSecondary/secondary` | 4.5 | 4.77 | Teal Turquoise light |
| `onSecondaryContainer/secondaryContainer` | 4.5 | 4.79 | Tokyo Night light |
| `onTertiary/tertiary` | 4.5 | 5.46 | Tokyo Night light |
| `onTertiaryContainer/tertiaryContainer` | 4.5 | 4.65 | Teal Turquoise dark |
| `onError/error` | 4.5 | 5.40 | Tokyo Night light |
| `onErrorContainer/errorContainer` | 4.5 | 5.19 | Tokyo Night dark |
| `inverseOnSurface/inverseSurface` | 4.5 | 4.52 | Tokyo Night light |
| `outline/surface` | 3.0 | 3.00 | Amethyst dark |
| `outlineVariant/surface` | 1.0 | 1.40 | Mocha light |

The stage band's hint, white at 90 % over the band ink, is worst on the Cerulean dark
band at 4.83, over its 4.5 floor; the light bands are 5.17 or better. The dark call
stage's lowest line is Mocha's muted at 7.25, and its lowest accent is Tokyo Night's
6.66; every text line is 10.26 or better. `docs/26-theme-contrast.md` remains correct
as written.

## What changed

| Fix | Where |
| --- | --- |
| The dwell scan is paused while a tool sheet covers the stage, and the time spent there is cut out of its clock | `AnswerFlow.kt:88` (`cover`), `:165` (`paused`), `:175`; `VideoCallStage.kt:230`; `AnswerKeys.kt:100` (`resume`) |
| The open tool is announced as selected, not only drawn in the accent colour | `StageTools.kt:95` |
| The chosen language is announced as selected, with a button role | `StageButtons.kt:228` |

## Tests

`app/src/test/java/app/signsetu/AccessibilityAuditTest.kt`, four JVM tests:

- `dwell_does_not_count_time_spent_under_a_sheet` walks `SwitchScan` with a clock:
  the tick during the covered stretch would move the highlight (the old behaviour);
  after `resume` it does not, and a full dwell after the sheet closes does.
- `answer_flow_is_told_when_a_sheet_is_open` holds the wiring: the flow takes a
  `paused` flag, the stage sets it from `sheetFor`, and a paused flow starts no timer.
- `language_tiles_report_which_language_is_chosen`,
  `tool_bar_reports_which_tool_is_open` hold the two semantics fixes.

The last three read the source, because the module has no Robolectric or Compose
runtime test setup; the first is a plain state-machine test. The 48 dp floor is
covered by `TouchTargetsTest`, which is not duplicated here.

## Left open

- The switch highlight is still silent to a screen reader (#3 above). The fix is a
  label on `answerTarget`, which every call site already has in scope.
- Sheets are `ModalBottomSheet` and dialogs are platform dialogs, so focus is held by
  the window; a static audit cannot prove the trap, and no emulator run is claimed.
- TalkBack's own reading of a merged tile ("picture, word, button, selected") follows
  from the modifiers above; it was not heard on a device.
