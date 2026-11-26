# 33. Kotlin static findings

A pass over `app/src/main` started on HEAD 402339f while other units kept committing to the same tree,
looking for defects that can be shown to be defects: a warning that names a bug, or a pattern whose
invariant is written down in the same file. Style is out of scope throughout. Line numbers are the ones
the quoted revisions had.

## How the pass was run

    node C:\GITHUB\.fleet\gradlew-locked.cjs <repo> :app:compileStandardDebugKotlin \
        --console=plain --warning-mode all

plus one full compile (`--rerun-tasks --no-build-cache`) so that no file was skipped as up to date,
and a grep of `app/src/main` for the usual defect shapes.

Commands used for the evidence below:

    node C:\GITHUB\.fleet\gradlew-locked.cjs <repo> :app:compileStandardDebugKotlin \
        --console=plain --warning-mode all --rerun-tasks --no-build-cache
    node C:\GITHUB\.fleet\gradlew-locked.cjs <repo> :app:testStandardDebugUnitTest --console=plain

## Warnings: 32 before, 32 after

A plain `:app:compileStandardDebugKotlin` printed nothing, because the task was up to date and the
compiler never ran. The numbers here come from the same task with `--rerun-tasks --no-build-cache`,
which recompiles every source of `app` (the tree holds 203 `.kt` files under `app/src/main`) and
re-emits every warning; the count is `Select-String -Pattern "^w: "` over that log.

All 32 are Kotlin deprecation warnings from the Compose and Material3 versions in the lockfile:

| Kind | Count | Sites |
| --- | --- | --- |
| `ListItem(headlineContent)` deprecation, use the trailing `content` lambda | 17 | `HelpScreen:146`, `SecondScreenSheet:140,191`, `IslDictionarySheet:122`, `SessionEventRows:61`, `Sheets:240`, `AppearanceScreen:160`, `BrainSettings:71,159`, `ModelImport:73`, `SettingsItems:59,75,87,113,134`, `SettingsScreen:157,198` |
| `BasicTextField(value, onValueChange)` deprecation, use the `TextFieldState` overload | 3 | `LessonEntries:291`, `OfficerDockParts:106`, `StageInputs:124` |
| `VisualTransformation` and its types deprecation (`TransformedText`, `OffsetMapping`, `Identity`, `PasswordVisualTransformation`) | 9 | `MainPageParts:92,102` (four), `OfficerDockParts:95` (two), `BrainSettings:121`, `DeveloperAiDialog:108`, `SettingsScreen:289` |
| `rememberModalBottomSheetState` deprecation | 2 | `Sheets:271`, `StageToolSheets:53` |
| `Slider(value, onValueChange)` deprecation | 1 | `SettingsItems:62` |

None of the five bug-capable kinds is in that list:

- **unused result**: no compiler warning marks a result the tree drops; the drops it does make are
  cleanup calls (`delete`, `close`, a log write) whose failure the caller does not depend on.
- **deprecated security API**: nothing withdrawn. The closest are the three `PasswordVisualTransformation`
  calls (`BrainSettings.kt:121` gateway token, `DeveloperAiDialog.kt:108` developer key,
  `SettingsScreen.kt:289`), which is a text-input migration to `BasicSecureTextField` rather than the
  removal of a crypto primitive; the fields still mask. Listed under "what remains".
- **unchecked cast**: no `as` to a generic type anywhere in `app/src/main`, no
  `@Suppress("UNCHECKED_CAST")`.
- **nullable platform type**: no `!!` on a platform-typed value without an `is`/`!= null`/`takeIf` guard
  in the same expression; the 29 `!!` sites are each checked in the list below.
- **coroutine scope leak**: no `GlobalScope`, no `Thread` and no scope created per recomposition; every
  `CoroutineScope(...)` is an app-lifetime field (`SignSetuApp.kt:125,245`, `BrainClient.kt:48`,
  `Contributions`), and every composable uses `rememberCoroutineScope()`.

The five `@Suppress("DEPRECATION")` sites are each an API-level branch or a documented choice:
`SignSetuApp.kt:309` (trim levels), `BluetoothBonding.kt:92` (`getParcelableExtra` before Tiramisu),
`Avatar.kt:135` (`TRIM_MEMORY_RUNNING_CRITICAL`), `OfficerAuth.kt:34` (device credential) and
`Buzz.kt:25` (`Vibrator` before S).

The 32 are identical before and after the changes below: neither changed file contains a deprecated
call, and the full compile after both fixes reports 32 as well. So the count is not where the value of
this pass is: two defects were found by reading code against its own stated invariant.

## Finding 1: the pairing code is written in the device's digits

`app/src/main/java/app/signsetu/pair/PairCrypto.kt:124` and `:125` (before the fix)

    val code = "%06d".format(digits(codeBytes, 1_000_000L))
    val check = "%04d".format(digits(checkBytes, 10_000L))

**Why it is a defect.** `String.format(String, vararg)` uses `Locale.getDefault()`, and Java's `%d`
renders the digits of that locale. On a phone set to Arabic, Persian or Bengali the six-digit code
that `StageLink` shows for the officer to type is not a string the officer can type: the code screen
(`StageDevice.kt:192`) prints it, and `codeMatches` (`PairCrypto.kt:235`) compares
`Charsets.US_ASCII` bytes of what was typed. The characters produced are U+0660..U+0669
(Arabic-Indic), U+06F0.. (Extended Arabic-Indic) and U+09E6.. (Bengali), none of which a numeric keypad
emits; they encode to `?` in US-ASCII, so the comparison can never succeed. The same two phones also
print different check values if their locales differ (the stage shows `check`,
`SecondScreenSheet.kt:111`), so the match step can never be confirmed either. Bengali is an Indian
language: this is a normal setting for the user base, not a contrived one.

Proof of the premise, run on the same JDK the build uses (`java L3.java`):

    default ascii=false cp=1632 1632 1633 1634 1635 1636
    ROOT ascii=true value=001234

**The change.** Both formats take `Locale.ROOT`, so the code and the check are the same ASCII strings on
every phone. Two lines, no behaviour change on an ASCII-digit locale.

**Test.** `app/src/test/java/app/signsetu/PairCryptoTest.kt`, new
`pairingCodeIsAsciiDigitsWhateverTheDeviceLocale`: derives the keys twice from the same secret, once
under `Locale.US` and once under `ar-EG`, and requires six and four ASCII digits, equal strings from both
runs, and `codeMatches(code, code)`. On the old code the `ar-EG` run starts with U+0660 (as printed
above), so the test fails there; it passes now.

## Finding 2: a failed save leaves an empty file in Documents

`app/src/main/java/app/signsetu/ui/session/SavedExport.kt:90` (before the fix)

    val uri = resolver.insert(files, values) ?: return false
    return runCatching { resolver.openOutputStream(uri)?.use { it.write(bytes) } != null }.getOrDefault(false)

**Why it is a defect.** `insert` publishes the row first; the write then happens through the returned
stream. If `openOutputStream` returns null (a provider that refuses the stream) or throws, the
`runCatching` turns it into `false` and the row is left behind: a zero-byte
`Documents/SignSetu/<session>/<session> report.pdf` for a report the app has just told the officer was
not saved (`saved_download_failed`, `SavedSessions.kt:139`). The file name carries the session's own
title and it sits in the shared Documents folder, where an officer may later attach or print it
expecting the record. This is the invariant
`SessionReport.writeReport` states for the cache copy ("a write that fails ... leaves no half PDF for the
share sheet to send", `SessionReport.kt:54`), broken for the copy that outlives the session. Nothing
deletes that row later: the next save recomputes the same name and replaces it, but only if the officer
saves that session again.

**The change.** When the write fails, the row this call inserted is deleted, and the same `false` is
returned. The URI is the app's own insert, so deleting it cannot touch another app's file; a failing
delete is swallowed because the caller is already on the failure path.

**Test.** None on the JVM: `ContentResolver` and `MediaStore` are Android-only, and the repository has no
instrumented test harness for Documents. The invariant is the one already written for `writeReport`,
which does have a JVM test (`WriteReportTest`).

## Patterns grepped, and what each hit was

| Pattern | Hits in `app/src/main` | Verdict |
| --- | --- | --- |
| `runBlocking` | 0 | - |
| `GlobalScope` | 0 | - |
| `Thread.sleep` | 0 | - |
| `println(` | 0 | - |
| `printStackTrace` | 0 | - |
| `Locale.getDefault()` | 0 | - |
| `!!` | 29 | all guarded by an `is`/`!= null`/`takeIf` in the same expression, or inside a `runCatching`; see below |
| `catch (e: Exception) { }` (empty) | 0 | every catch logs, rethrows or returns a failure value |
| `runCatching` / `getOrNull` | 200+ | read at the export, evidence, pairing and model-store sites; every one either returns a failure value the caller shows, or is documented in place. One left a file behind (Finding 2) |
| `System.currentTimeMillis()` | 22 | audit timestamps, retention and UI timers; see "what remains" |
| `Date()` | 1 | `EvidenceIntegrity.kt:164`, the certificate's own issue time (correct: it is "generated at"); the session's start comes from `session.startedAt` at `:163` |
| float equality | 7 | sentinels (MediaPipe writes exact 0 for a missing point) and `animator_duration_scale == 0f`, which is exact by contract |

The `!!` sites, checked one by one: `DatabaseKey.kt:55` (`existingOrNew` with `unlockedOnly = false`
either returns a key or throws), `GlossLattice.kt:91` (`faces` filtered `face != null`),
`IslLexicon.kt:98` (filtered `sign?.startsWith` above), `Kinetics.kt:174` (`k = n.coerceAtMost(size)`),
`HomeSigns.kt:28` (guarded by `taught.all { it.features != null }`), `OnlineAssistant.kt:191,221`
(`d!!` after `d ?: return`), `SignReaders.kt:32,42,57,66` (seven occurrences, each in a
`runCatching(...).getOrNull()` whose `verified(path)` returns null for a missing or hash-mismatched
asset), `SessionReport.kt:142,176,211,223,237,261,266,279,283,320` (ten, all on `canvas`, set by
`newPage()` before any draw), `BrainSettings.kt:207` (`viaIroh` is defined as `ticket != null`),
`SignPredictions.kt:155` (inside `runCatching` with `onFailure`), `RecordSignsScreen.kt:108` (twice,
inside `runCatching`), `LocalModel.kt:65` (inside `runCatching`, returns `false`).

## What remains

- `AnswerFlow` (key repeat, the dwell scan and the switch hold), `AutoPilot` (the gap between plan
  requests) and `Counsellor` (its hold) measure short intervals with `System.currentTimeMillis()`. A
  backwards clock step during a switch press or a plan request extends a wait instead of ending it; a
  forwards step turns a held switch into a tap. `VideoCallStage` (the control fade) and `OfficerAuth`
  (its grace window) already use `SystemClock.elapsedRealtime()`. Not changed: the swap is mechanical but
  no JVM test can reach a `KeyEvent` or the planner's clock, so the defect is argued rather than shown.
- `RecordSignsScreen.kt:108` (`text("words.json")!!`) turns a missing or unverified asset into an empty
  prompt list with no message. Debug-only screen (Settings shows it under `BuildConfig.DEBUG`), so left.
- `SessionReport.signsJson` (`StationExport.kt:98`) stops sub-sampling at `every = 16` and may still
  return a file over the 2 MB the database can read back, for a very long "Tell me" window. Needs a
  real long-window capture to show; left.
- The `runCatching` in `Contributions.save` (`Contributions.kt:80`) hides a failed local record write:
  the signer sees "done" and the record is lost. It would need a JVM test for `writeAtomic` on a full
  disk; left as a known gap.
- Three secret-entry fields still use `PasswordVisualTransformation`, which masks the glyphs but lets
  the value be copied out of the field's text toolbar (and the activity be captured in recents while the
  field is focused, subject to the window flag): `BrainSettings.kt:121` (gateway token),
  `DeveloperAiDialog.kt:108` (developer AI key), `SettingsScreen.kt:289`. The compiler's own advice is
  `BasicSecureTextField`, which drops copy/cut and the toolbar actions. Not changed: it is a state-based
  field with no `Outlined` decoration, so each site needs its own label, error and visibility UI, and
  none of it can be exercised by a JVM test; the values are officer-entered settings, not subject data,
  so it is a hardening item rather than a disclosed-data defect.
