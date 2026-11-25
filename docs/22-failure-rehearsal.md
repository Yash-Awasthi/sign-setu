# 22. Failure rehearsal

This page is for the officer whose session is interrupted: the phone dies, the second screen drops, a content or model file turns out to be damaged, or the report export dies while it is being written. For each failure it says what is already in the record when it happens, what is lost, what the officer sees, what to do and not do, and what in the tree proves the claim.

## 1. What this covers, and how to repeat it

Four failures, all rehearsed on a desk with a real build, never with a real conversation on the device:

- the phone dies or is killed mid-session;
- the second screen drops while an item is on it;
- a content file or the imported assistant model is corrupt;
- the export dies mid-write.

Each claim below carries one of two labels. **exercised** means a test in the tree runs the path and fails if it stops working. **code path** means only the code was read: the behaviour is there, but no test drives it, so treat it as design, not proof.

To repeat the whole rehearsal, from the repository root:

```
python tools/docs/check_docs.py
./gradlew testStandardDebugUnitTest --tests app.signsetu.ContentFallbackTest --tests app.signsetu.PairTest
./gradlew connectedStandardDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.signsetu.ExportFailureDeviceTest
./gradlew connectedStandardDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.signsetu.SessionSurvivesRestartDeviceTest
```

The first command checks this page's links and index row. The unit tests need no device; the two instrumented classes need an emulator or phone with the debug build installed. The first and last commands were run for this page on an API 34 emulator; the two unit commands are the existing tests named under the labels below, not run again for this page.

## 2. The phone dies mid-session

**Already written.** Every action is committed as it happens, not at the end. A row is sealed onto the session's hash chain and written in one transaction with its drawing, recorded speech or landmarks, so a kill cannot leave a row that names a picture or clip which is not there (app/src/main/java/app/signsetu/data/session/SessionDao.kt, insertSealed). The session row itself was written at start, and the chain is anchored to the session id and start time (app/src/main/java/app/signsetu/data/session/EventSeal.kt). Evidence: **exercised** by app/src/androidTest/java/app/signsetu/SessionSurvivesRestartDeviceTest.kt, which writes a session and three sealed rows, closes the database, opens a fresh instance on the same file and key, and finds the session, the same rows and a chain that still verifies.

**Lost.** At most the action in flight when the phone died: the tap, drawing or note being written, and any item shown whose row had not committed. Nothing already acknowledged on screen is lost, because the interface reflects a row only after it is stored (app/src/main/java/app/signsetu/data/session/SessionRepository.kt, `log`). The session also has no end time and no title, and the session root and signature are not written until it is ended, so the stored root may be null or may cover an earlier head.

**What the officer sees.** On the next unlock the Start screen shows an "Unfinished session" card with the profile, the language and the start time, and a Continue button (app/src/main/java/app/signsetu/ui/start/StartScreen.kt). Continuing logs a SYSTEM row that states the chain check made then: "resumed: chain intact, N entries", or "CHAIN BROKEN at entry <id>" (app/src/main/java/app/signsetu/ui/session/SessionViewModel.kt). Both are **code path**: no test drives the screen.

If the phone stayed off past the retention window, the session is gone instead of listed: retention counts an open session from its last row, defaults to 24 hours, and the sweep runs on unlock and hourly (app/src/main/java/app/signsetu/data/session/Retention.kt; app/src/main/java/app/signsetu/SignSetuApp.kt). Evidence: **code path**.

**What the officer must do.** Unlock, open the unfinished session with Continue, read the resumed row, and compare the last entry in the record with what was last said or shown. If the last answer is missing, ask again or log a note saying it was not recorded. Finish the session with a title before the shift ends so its root and signature are written.

**What the officer must not do.** Do not start a new session to get going: Start ends an unfinished session under a default title first, so the unfinished one is no longer offered with its own name (app/src/main/java/app/signsetu/ui/start/StartScreen.kt, `begin`). Do not delete the session to tidy up, and do not assume the last tap survived because the screen showed it.

## 3. The second screen drops

**Already written.** Every answer from the second screen is stored before it is acknowledged back to that screen, and a resent answer is stored once (app/src/main/java/app/signsetu/ui/session/SessionViewModel.kt, `logRemote`; app/src/main/java/app/signsetu/ui/pair/StageBridge.kt). The link's life is in the same log as LINK rows: connected, code verified, lost, resumed, on hold, unpinned, ended, and "not confirmed as shown on the second screen" when the item on the stage was never confirmed before the drop (app/src/main/java/app/signsetu/ui/pair/LinkText.kt). The row also records whether an answer came from the stage on time or late, as `stage` or `stage_late` (SessionRepository.SessionEvent.via). Evidence: **exercised** at the protocol level by app/src/test/java/app/signsetu/PairTest.kt: `pairShowAnswerLoseAndResume` shows, answers, drops the link, answers again and resumes, and `twoAnswersToOneItemAreBothLogged`, `aLateAnswerStaysWithItsQuestion` and `answersFromANewLinkAreNotTakenForResends` cover the rows.

**Lost.** Anything the subject tapped after the drop that the stage still held unacknowledged, if the link never resumes: the stage keeps unacked answers and resends them on a resume, marked late, but a resume is only offered for two minutes (app/src/main/java/app/signsetu/pair/StageLink.kt, LinkTiming.resumeWindowMs). What the subject did on the second screen while the link was down, and did not tap again, is not in the record. If the link is lost before the officer's phone ever confirmed the item as shown, the record says so instead of implying it was seen.

**What the officer sees.** A red alert above the conversation: "The second screen is not connected." with Reconnect and Use this phone (app/src/main/java/app/signsetu/ui/pair/PairedBar.kt). The bar keeps the device label, and the item on the stage no longer stays in Show mode. After two minutes without a resume the alert changes to "The second screen left the session." with Use this phone. The log shows the lost row, and resumed or ended after it. The alerts are **code path**: no test renders the officer bar; the link states and rows behind them are **exercised** by PairTest.

**What the officer must do.** Tap Reconnect within the two minutes if the other screen is still there, then read the last answer on it against the record. Otherwise tap Use this phone: what was on the stage moves back to this phone and a LINK row "Moved back to this phone" is logged (SessionViewModel.fallbackToLocal). Ask the person again for anything given while the link was down.

**What the officer must not do.** Do not keep asking through a dead link: Show is gated off while the link is down, so the tap does nothing and the subject sees nothing new (StageBridge.kt, `allowShow`). Do not type the stage's answer in on the subject's behalf. Do not treat an answer as recorded because it was tapped on the second screen; only the officer-side row is the record.

## 4. A content file or the model file is corrupt

**Content.** The bundle is checked against its SHA-256 manifest before anything is parsed. A file that fails the check is never parsed and joins the rejected set (app/src/main/java/app/signsetu/data/content/Content.kt, `loadOnce`). A rejected text file such as the questions or the card list sets the bundle damaged; a rejected picture, sound, or the ASL files are dropped, and ASL is turned off while ISL still starts.

Already written when it happens: nothing is changed in the record. Sessions live in the encrypted database, not in the bundle, so earlier sessions open, export and verify as before. Lost: the affected questions, pictures or sounds, and ASL if its files failed; a damaged text file costs new sessions entirely.

The officer sees, on the Start screen, "Content check failed" with the instruction to install the current version from the department, or the quieter notice that some pictures, sounds or gestures are left out, or that ASL is off (app/src/main/res/values/strings.xml, start_damaged_title and start_asl_off). A damaged bundle refuses a new session rather than showing the person wrong words. Evidence: **exercised** by app/src/test/java/app/signsetu/ContentFallbackTest.kt, which loads a bundle whose manifest hash is valid but whose text file is malformed, and one with an unreadable manifest, and gets a damaged bundle instead of a crash.

**What the officer must do.** Finish or export the session already in progress, then reinstall the department's current build. **Must not:** start a new session on the damaged copy, sideload a copy from outside the department release, or delete sessions while trying to fix content: the sessions are not the problem.

**Model.** The optional assistant model is imported from a file the department hands out, copied to a `.part` name, hashed, and renamed into place only when its SHA-256 is in `ModelStore.KNOWN_MODELS` (app/src/main/java/app/signsetu/assistant/LocalModel.kt; app/src/main/java/app/signsetu/ui/settings/ModelImport.kt). A swapped, truncated or corrupt file is deleted before it can be parsed, and an already installed model is left where it was.

The officer sees "That file is not a model this app knows; nothing was installed." in Settings, in the error colour (strings.xml, settings_assistant_bad). Lost: only the import attempt. The record, the session and any previously installed model are untouched. If a model file installed earlier is damaged on disk afterwards, loading it returns nothing and the assistant is simply unavailable, with no crash (LocalModel.load returns null, ModelStore.use returns null). Evidence: **code path**; the allow-list check and the delete-before-parse were read in the code, not driven by a test here.

**What the officer must do.** Re-copy the model file from the department's own medium and import it again, checking its SHA-256 against the published value. **Must not:** keep the file under a different name or try to make the app load it, and must not reinstall the app to fix the assistant: that would delete every session.

## 5. The export dies mid-write

**Already written.** The report is built in memory, written to `<name>.pdf.part`, checked for its full length, and only then renamed to `<name>.pdf` (app/src/main/java/app/signsetu/export/SessionReport.kt, `writeReport`). If the write or the rename fails, the part file is deleted and any report already under the target name is left untouched. The record itself is not rewritten during an export: rows, chain and seal are read from the database as they stand.

**Lost.** The PDF being written, and only that. The session, its rows and its chain are unchanged, so the report can be built again from the same rows. If the app was killed between the write and the rename, a part file is left on disk; it is never offered to the share sheet, and the next export deletes every part file and every report older than 15 minutes before it writes (SessionReport.purgeStaleExports). Every unlock also empties the export folder (app/src/main/java/app/signsetu/SignSetuApp.kt, `unlock`).

**What the officer sees.** If the export itself failed, the dialog "Could not create the report: the phone may be full. The session is unchanged." (strings.xml, export_failed). If the app died before the rename, nothing is offered: the next Export report starts from a clean folder. Either way the session stays saved, and "Done" in the share dialog is only about the file.

**What the officer must do.** Free space if the phone is full, choose Export report again, and share the new PDF promptly. The check that a report is genuine is "Check a report", which reads the seal out of the PDF. Evidence: **exercised** by app/src/androidTest/java/app/signsetu/ExportFailureDeviceTest.kt on the device for a folder that cannot be written, an orphaned part file left by a killed export, and a finished part that cannot be renamed; the 15-minute purge and the unlock-time empty are **code path**.

**What the officer must not do.** Do not delete the session to make the export work: the record is the evidence and cannot be rebuilt. Do not send a `.part` file, rename one by hand, or send a report from an earlier export as if it were the current record. Do not retry in a loop on a full disk; free space first.

## 6. What always survives, and what never does

| Failure | In the record after it | Gone |
| --- | --- | --- |
| Phone dies | every committed row, its drawing or clip, the seal chain | the row in flight, the end and title, the root and signature |
| Second screen drops | every answer stored before the ack, every LINK row | taps the stage held and never resent within two minutes |
| Content or model file corrupt | every session in the database | the affected content files, or the assistant model |
| Export dies mid-write | every session in the database | the half-written PDF only |

The rule behind the table: the record is the database, everything else is a copy of it. The app writes a row before showing it and names an export only when it is whole, so an interruption costs at most the action in progress. The officer's part is to finish the session with a title, export before the retention window closes, and say plainly in the record what was not captured.
