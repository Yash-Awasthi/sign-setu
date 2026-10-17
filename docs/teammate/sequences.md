# Sequences

Root: `app/src/main/java/app/signsetu/`. VM = `ui/session/SessionViewModel.kt`, Repo = `SessionRepository` (SQLCipher log).

## 1. Unlock

`ui/AppRoot.kt` (LockScreen branch), `SignSetuApp.kt` (`unlock`, `unlockAsync`), `MainActivity.kt` (`unlock`), `data/session/DatabaseKey.kt`, `ui/session/Handover.kt` (`StageGuard`).

```mermaid
sequenceDiagram
    actor O as Officer
    participant AR as AppRoot
    participant SG as StageGuard
    participant C as AppContainer
    participant DK as DatabaseKey
    participant MA as MainActivity
    participant DB as SessionDatabase
    O->>AR: open app, sessions == null
    AR->>SG: forceCredential?
    alt stage_live flag set (killed or locked mid-stage)
        AR->>MA: unlock(prompt) BIOMETRIC_STRONG or DEVICE_CREDENTIAL (API 29 WEAK)
        MA-->>AR: onSuccess
        AR->>SG: onStage(false)
        AR->>C: unlockAsync
    else flag clear
        AR->>C: unlockAsync (Keystore key still in auth window)
        C--xAR: UserNotAuthenticatedException then onFailed = prompt
    end
    C->>DK: unwrap() KEK (Keystore, auth-bound) AES-GCM unwraps DEK from dek.bin
    alt KeyPermanentlyInvalidated or AEADBadTag
        DK->>DK: destroy, new DEK, reset = true
    end
    C->>DB: SessionDatabase.open(DEK)
    alt SQLiteNotADatabaseException
        C->>DK: destroy, reopen, keyReset = true
    end
    C->>C: purgeExpired(retentionHours), delete cache/exports
    C-->>AR: sessions = repo, Navigation
```

## 2. Show item (local stage and paired stage)

`SessionViewModel.show` / `present`, `AutoPilot.onManualShow`, `pair/StageBridge.kt` (`rememberPairedStage`), `pair/StageWire.kt`.

```mermaid
sequenceDiagram
    actor O as Officer
    participant VM as SessionViewModel
    participant AP as AutoPilot
    participant R as Repo
    participant L as StageLink (officer)
    participant S as StageLink (stage)
    O->>VM: show() with pending draft
    VM->>VM: ShowTiming.start
    opt mode == SHOW (paired replace)
        VM->>VM: finishShown(OFFICER)
    end
    VM->>AP: onManualShow(pending) skip proposal, maybe turn Auto off
    VM->>VM: present: flushGiven, shuffle offered cards
    VM->>R: log QUESTION_SHOWN (or SYSTEM) returns id
    VM->>R: log PROMPT (PromptRecord, exact screen)
    VM->>VM: shown[id]=pending, mode=SHOW, onStage, itemId=id
    alt not paired
        VM-->>O: Stage composable renders onStage (pinned, immersive)
    else paired
        VM->>L: show(itemId, toStageView()) only IDs (WirePending)
        L->>S: ToStage.Show(rev, itemId, view) AES-GCM frame
        S->>S: resolve(content) null if ID unknown
        S->>L: ToOfficer.Displayed(itemId)
    end
```

## 3. Subject answer

Local: `answerCard` / `answerYesNo` / `answerText` / `submitDrawing`. Paired: `StageLink.answer`, `SessionViewModel.logRemote`.

```mermaid
sequenceDiagram
    actor Sub as Subject
    participant St as Stage UI
    participant S as StageLink (stage)
    participant L as StageLink (officer)
    participant VM as SessionViewModel
    participant R as Repo
    participant AP as AutoPilot
    alt local stage
        Sub->>St: tap card or Yes/No
        St->>VM: answerCard(id) / answerYesNo(b)
        VM->>VM: logChecked (show-back or say-back) else plain
        VM->>R: log ANSWER_CARD SUBJECT shownId=itemId
        VM->>VM: stop endRun, something_else present Drawing, else advanceRun (1400 ms)
    else paired stage
        Sub->>St: tap
        St->>S: answer(itemId, body) fits() caps
        S->>S: outbox[n]=Answer(itemId,n,body,late=!live)
        S->>L: ToOfficer.Answer
        L->>VM: logRemote(itemId,n,body,late,from=link)
        VM->>VM: dedupe (itemId,n) per link, via stage or stage_late
        VM->>R: log answer
        L->>S: ToStage.Ack(itemId,n)
        S->>S: outbox.remove(n)
    end
    R-->>AP: events flow, new answer id
    AP->>AP: onAnswer stop pause or off, k1 yes BREAK, else propose
```

## 4. Sign window read and subject confirm

`SessionViewModel.readSigns` / `startReading` / `onSignFrame` / `finishReading` / `askSubject` / `chooseSign`, `sign/SignCapture.kt`.

```mermaid
sequenceDiagram
    actor O as Officer
    actor Sub as Subject
    participant VM as SessionViewModel
    participant SC as SignCapture
    participant CAM as CameraMirror (analysis thread)
    participant RD as SignReader (readDispatcher, 1 thread)
    participant R as Repo
    O->>VM: readSigns(ANSWER)
    VM->>VM: present pending if any, else log SYSTEM read_signs, mode=SHOW
    VM->>SC: startReading: SignCapture(window), reading=[]
    loop each tracked frame
        CAM->>VM: onSignFrame(frame)
        VM->>SC: add(frame) synchronized
        SC-->>VM: closed segments (pause >= 400 ms, len >= 200 ms, >= 3 active frames)
        VM->>RD: read(frames) top 3 inContext(answerCards)
        RD-->>VM: SignSegment(guesses, slower)
    end
    alt Done tap, Stop, or cap 20 s
        Sub->>VM: finishReading
    end
    VM->>SC: finish() flush open segment
    VM->>VM: joinAll reads
    VM->>R: log ANSWER_SIGNED source MACHINE text=words
    VM->>VM: signed[eventId]=segments (memory only)
    O->>VM: acceptSigned(eventId, words) optional
    VM->>R: log ANSWER_SIGNED source OFFICER
    O->>VM: askSubject(eventId, words)
    VM->>VM: choiceQueue: signChoices shuffled per segment
    VM->>R: logChoices QUESTION_SHOWN SHOW_BACK + PROMPT
    VM-->>Sub: confirm = SignConfirm(queue), SignChoicePanel
    loop per segment
        Sub->>VM: chooseSign(choice or null)
        VM->>R: ANSWER_CARD SUBJECT or ANSWER_UNCLEAR
        VM->>R: logChoices(next) if any
    end
```

## 5. Tell me narrative

Same path as 4 with `SignWindow.NARRATIVE`: limit 180 s, pause 700 ms, faces dropped (`keepFace = false`).

```mermaid
sequenceDiagram
    actor O as Officer
    actor Sub as Subject
    participant VM as SessionViewModel
    participant SC as SignCapture
    participant R as Repo
    O->>VM: readSigns(NARRATIVE) with no pending
    VM->>R: log SYSTEM tell_me
    VM->>VM: mode=SHOW, startReading(NARRATIVE)
    VM->>VM: models.release (AppRoot, signWindow != null)
    loop phrases
        Sub->>VM: frames (face=null)
        VM->>SC: add, phrase chunks at 700 ms pauses
    end
    Sub->>VM: Done or 180 s cap (full)
    VM->>R: ANSWER_SIGNED MACHINE
    O->>VM: acceptSigned / askSubject as in 4
```

## 6. Pairing handshake

`pair/PairCrypto.kt` (`officerHandshake`, `stageHandshake`, `derive`, `LinkSession`), `pair/StageLink.kt` (`start`, `submitCode`, `match`, `maybeLive`). Transport: Bluetooth RFCOMM (`pair/BluetoothTransport.kt`). Handshake messages are length-prefixed plain JSON, cap 4 KiB.

```mermaid
sequenceDiagram
    participant O as Officer phone
    participant S as Stage device
    Note over O,S: Identity = versionCode, content manifestHash, model. mode = new or resume
    O->>O: P-256 keypair (pkO), nonce nO 32 B
    O->>S: Hello1 v=1, ids, mode, commit=SHA256(pkO||nO)
    S->>S: P-256 keypair (pkS), nonce nS
    S->>O: Hello2 v=1, ids, mode, pkS, nS (sent before checks)
    S->>S: check version, contentHash, mode
    O->>O: check version, contentHash, mode, decodeP256(pkS) on-curve, |nS|=32
    O->>S: Reveal pkO, nO
    S->>S: SHA256(pkO||nO) == commit else COMMIT abort, decodeP256(pkO)
    Note over O,S: ss = ECDH(P-256). T = SHA256(H1||H2||Reveal)
    Note over O,S: salt = T (new) or SHA256(resumeKey||T) (resume). PRK = HKDF-Extract(salt, ss)
    Note over O,S: o2s, s2o = Expand 32 B. code = Expand 4 B mod 10^6. check = Expand 4 B mod 10^4. resumeKey = Expand 32 B (new only)
    alt new pairing
        S->>S: state Code(code), 6 digits on screen, codeMs timer
        O->>O: officer types code, codeMatches (constant time), 3 misses abort CODE
        O->>S: Confirm (encrypted frame)
        O->>O: Code(null, check) shows 4-digit check
        S->>S: Code(code, check) shows check
        S->>S: subject-side tap match()
        S->>O: Confirm
    else resume
        O->>S: Confirm
        S->>O: Confirm
    end
    Note over O,S: both confirmed: Live. Officer resends lastShow. Stage resends outbox marked late
```

Frame format (`LinkSession`):

| Field | Size | Note |
|---|---|---|
| len | u32 | 16 .. 1 MiB, else SIZE abort |
| counter | u64 | must equal expected, else PROTOCOL abort (no replay, no gap) |
| ct+tag | len | AES-256-GCM, 128-bit tag |

- IV (12 B) = dir byte (1 officer, 2 stage) `00 00 00` counter(u64). Unique per key.
- AAD (9 B) = `01` counter(u64).
- Max 2^32 frames per direction.
- Heartbeat: Ping every `pingMs`, silent > `lostMs` = fault.

## 7. Export

`OfficerPane.kt` (end dialog), `SessionViewModel.exportReport`, `export/SessionReport.kt`.

```mermaid
sequenceDiagram
    actor O as Officer
    participant OP as OfficerPane
    participant VM as SessionViewModel
    participant SR as SessionReport
    participant FS as cacheDir/exports
    O->>OP: End, Export
    OP->>VM: exportReport(reportText, recordDetail, detailsSection)
    VM->>VM: grades(events), withQuestions, ReportRow(reading flag)
    VM->>SR: export(rows, text, drawing loader, section)
    SR->>SR: render A4 PdfDocument to bytes
    SR->>SR: newPassphrase 16 chars, 4x4 groups (SecureRandom)
    SR->>SR: PDFBox protect AES-256, random owner pw
    SR->>FS: save sign-setu-report.pdf
    SR->>SR: plain.fill(0)
    SR-->>OP: (file, passphrase), ExportDialog
    O->>OP: Share (share sheet) then Finish
    OP->>FS: file.delete
    OP->>VM: endSession: wipeSigns, repo.purge(sessionId)
```
