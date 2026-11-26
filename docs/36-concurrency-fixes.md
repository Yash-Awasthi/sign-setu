# Concurrency fixes

Four defects from the concurrency audit of `app/src/main` were fixed in the working tree. The evidence for them is the
JVM unit test suite; nothing was run on a device, so camera timing, Bluetooth link timing and Compose snapshot behaviour
under a real recomposer are outside what these changes were checked against. The result of the suite is not recorded
here.

The audit numbers are kept so the report and this file read together: the fixed items are D1, D3, D4 and D6. D2 was
investigated and found to be intended behaviour, described below. D5, D7, D8 and D9 remain documentation-only and are
listed at the end.

## D1: the camera thread called back into the view model and wrote Compose state

The frame callback ran on the camera analysis thread and both read and wrote Compose snapshot state, and could call
`startReading` after `wipeSigns` had cleared a window.

- `ui/session/VideoCallStage.kt:368-372`: the per-frame work is posted to the main looper when the callback arrives on
  another thread; on the main thread it runs directly.
- `ui/session/SignReading.kt:71`: a `wiped` flag is set by `wipe()` at `:178`.
- `ui/session/SignReading.kt:108`: `start` refuses while wiped, so a start the camera already queued cannot reopen the
  window the wipe cleared.
- `ui/session/SignReading.kt:119`: `onFrame` refuses while wiped, before it touches `lastFrameMs` or the frame ring.
- `ui/session/SessionViewModel.kt:281`: `startReading` refuses when the session repository reports the session ended.

## D2: a double answer for one shown item

The audit read `answerCard` and `answerYesNo` as able to write two rows for one shown item. They can, and that is the
product: a yes/no followed by a card, or a correction, are both recorded and `lastAnswer` picks the one to display.
`PairTest.twoAnswersToOneItemAreBothLogged`, `PairTest.aGestureCueFromTheStagePhoneReachesTheRecord` and
`ConversationE2ETest.aWholeConversationReachesTheRecord` pin the two-row behaviour.

The repeat that is a real duplicate is refused one layer down: `SessionLink.logRemote` keys on
`(itemId, sequence number)` for the same link, so a resend is dropped while a new answer is not. A per-item guard was
tried and reverted because it broke those three tests; `ConcurrencyFixesTest.eachTapOnOneItemIsItsOwnAnswer` records the
rule.

- No source change for D2. The transport-level dedupe at `ui/session/SessionLink.kt:19` is the guard.

## D3: an answer arriving as the session ended landed outside the Merkle root

`log` checked only whether the app had locked, not whether the session had ended, so a stage answer after `end()`
appended a row that the certificate's root did not cover.

- `data/session/SessionRepository.kt:129`: the ended session ids.
- `data/session/SessionRepository.kt:131`: `isEnded`, used by the view model and the link.
- `data/session/SessionRepository.kt:185`: `end` marks the session inside the sealing lock, after the end row and
  before the root is computed.
- `data/session/SessionRepository.kt:150`: `log` refuses an ended session and returns `-1`, so no caller treats it as
  kept.
- `ui/session/SessionLink.kt:24`: the stage path refuses before it writes anything.
- `data/session/SessionRepository.kt:226`: `reopen` clears the mark, because a taken-up session may be written again.

## D4: the shown id was read when the row was written, not when the person answered

`present` sets the next item id before recomposition, so a tap on the still-visible old item logged against the new
item.

- `ui/session/SessionViewModel.kt:509-514`: `answerCard` snapshots the item and its pending question at entry and uses
  the snapshot for the checked write and the plain write.
- `ui/session/SessionViewModel.kt:542-548`: `answerYesNo` does the same.

## D6: the phone model rung outlived its share of the plan budget

`withTimeoutOrNull` cancelled only the wait, not the `async` the rung had started, so the native model call kept the
model lock past the rung's timeout and delayed the release at app lock.

- `ui/session/Planner.kt:73`: the rung keeps the `Deferred` and waits through the helper.
- `ui/session/Planner.kt:84`: `within` cancels the `Deferred` when the wait returns null, so a rung that lost its
  timeout does not take the lock later.

## Tests

`app/src/test/java/app/signsetu/ConcurrencyFixesTest.kt` holds one test per behaviour: each stage tap is its own answer,
the item that was on screen, a post-end answer refused with the root unchanged, no frame after a wipe, no window after the
session ends, and no waiter left after a rung's budget. The tests use the content and the in-memory DAO the other
session tests use. Each was written to fail against the code before the change; whether it failed and then passed is a
run result and is not asserted in this file.

The teardown of that test class cancels its view models and does not join them. Joining was measured to hang the whole
suite: the run spent 1002 s in `unmain()` because the jobs were joined on a test scheduler that had already stopped, so
the join could never complete. Cancelling is enough for this class and lets the suite finish.

## Documentation only

These were confirmed in the audit and are not changed here.

- D5, the stage's answer using a snapshot taken before it suspends: the fix belongs in `SessionLink`'s use of `current`
  and changes acknowledgement behaviour on the wire; it needs the link's resend path exercised, which the JVM tests do
  not cover.
- D7, main-thread blocking: the recording stop, the provenance read and the whole-chain hashing are real stalls, but the
  fix is per call site and the acceptable duration is a device question.
- D8, one frame after camera disposal: dependent on whether CameraX delivers a callback after `clearAnalyzer`; that is a
  device answer.
- D9, a cue surviving the answer that did not consume it: a behaviour question about gestures rather than a race, and
  changing it alters what the record means.

## Not decidable without a device

Whether Compose throws on an off-thread snapshot write or only shows a torn frame; whether CameraX calls the analyzer
after disposal; the real stall behind the microphone stop and the 500 ms join; whether the stage link's callbacks can
leave the main thread; whether one second before the database close is enough on a slow phone; whether an end and a
stage answer can cross on Bluetooth; and how often the phone-model rung loses its timeout in practice.
