# State machines

Root: `app/src/main/java/app/signsetu/`.

## 1. Session (`ui/session/SessionViewModel.kt`, `Mode`)

`Mode { OFFICER, SHOW }`. `pending` = officer draft, `onStage` = logged item on stage, `itemId` = its row (D-26).

```mermaid
stateDiagram-v2
    [*] --> Opening: init, log has only StartRows
    Opening --> SHOW: present LanguageFinder (OTHER_LANGUAGE) or GroundRules
    [*] --> OFFICER
    OFFICER --> OFFICER: prepare(pending)
    OFFICER --> SHOW: show() / readSigns() / showRun() / sayBack()
    SHOW --> SHOW: answer, advanceRun next run item after 1400 ms
    SHOW --> SHOW: paired show() replaces item (finishShown OFFICER first)
    SHOW --> OFFICER: returnToOfficer (handle, chord) finishShown
    SHOW --> OFFICER: leavePaired
    OFFICER --> Ended: endSession wipeSigns, purge
    Ended --> [*]
```

`finishShown`: unanswered show-back or say-back logs ANSWER_UNCLEAR, skipped ground rules log `rules_skipped`, `endRun`, apply `chosenLanguage`, `auto.onReturn()`.

## 2. Stage modes (`ui/session/Stage.kt`, `Stage` composable)

Panel under the avatar, first match wins.

```mermaid
stateDiagram-v2
    [*] --> Rail
    Rail: Rail + MirrorWindow (cards, Yes, No, dont_know, wrong, dont_understand)
    Choices: SignChoicePanel (confirm != null)
    Drawing: DrawingPanel + PauseRow
    Writing: WritingPanel + PauseRow
    Phrase: PhraseBoard (saying)
    Lesson: AnswerPanel (lessonQuestion)
    Language: LanguageGrid
    Signing: SigningPanel, rail Stop and Done
    Rail --> Drawing: Draw tool (item.canDraw)
    Rail --> Writing: Write tool (canWrite, not YOUNG_CHILD)
    Rail --> Signing: camera tool, onStartSigning (reading != null)
    Rail --> Phrase: Say button
    Drawing --> Rail: back (not when pending == Drawing)
    Writing --> Rail: done or back
    Phrase --> Rail: given or back
    Signing --> Rail: Done or Stop (finishReading)
    Rail --> Choices: askSubject
    Choices --> Rail: last chooseSign
    [*] --> Language: pending == LanguageFinder
    [*] --> Lesson: lesson sheet item
    [*] --> Drawing: pending == Drawing
```

Overlays: `rightsNotice` replaces Yes/No with `dont_understand` only. `PauseCards` answer = paused. `checking` (asksChoice) stops the avatar acting out the officer reading.

## 3. Sign window (`sign/SignCapture.kt`, `SessionViewModel`)

| Window | limitMs | pauseMs | minMs | keepFace |
|---|---|---|---|---|
| ANSWER | 20 000 | 400 | 200 | true |
| NARRATIVE | 180 000 | 700 | 200 | false |

```mermaid
stateDiagram-v2
    [*] --> Closed: capture == null, camera preview only
    Closed --> Open: startReading(window)
    state Open {
        [*] --> Idle
        Idle --> InSign: hand moving > 0.3 frame-widths/s, or hand appears
        InSign --> InSign: moving frame, lastActive = i
        InSign --> Idle: gap >= pauseMs, emit if len >= minMs and >= 3 active
        InSign --> Full: t - t0 >= limitMs, close and emit
        Idle --> Full: t - t0 >= limitMs
    }
    Open --> Finishing: finishReading (Done, Stop, cap)
    Full --> Finishing: reachedCap
    Finishing --> Closed: finish() flush, joinAll reads, log ANSWER_SIGNED MACHINE
    Open --> Closed: wipeSigns (lock, end, onCleared)
```

## 4. Pairing link (`pair/StageLink.kt`, `LinkState`)

```mermaid
stateDiagram-v2
    [*] --> Idle
    Idle --> Connecting: connect(peer) officer / listen() stage
    Connecting --> Code: handshake ok, mode new
    Connecting --> Failed: PairAbort (PROTOCOL VERSION MODE COMMIT KEY SIZE) or timeout
    Code --> Code: officer submitCode ok, Code(null, check)
    Code --> Code: stage gets Confirm, Code(code, check)
    Code --> Failed: 3 wrong codes (CODE), codeMs timer (stage), Bye before Live
    Code --> Live: myConfirm and peerConfirm
    Live --> Hold: hold() officer, ToStage.Hold
    Hold --> Live: unhold(), or stage receives Show
    Live --> Lost: fault after everLive (IO, silent > lostMs)
    Hold --> Lost: fault
    Lost --> Live: reconnect() resume handshake (resumeKey, pinned peer), both Confirm
    Lost --> Ended: resumeWindowMs elapsed
    Live --> Ended: end(reason) or Bye received
    Hold --> Ended: end(reason) or Bye
    Failed --> Idle: stage relistens after 1 s
    Ended --> [*]
```

Stage side relistens 500 ms after Lost. On resume Live: stage resends outbox with `late = true`. Before Live any non-handshake frame = PROTOCOL abort.

## 5. Auto mode (`ui/session/AutoPilot.kt`, `Conv`)

Every transition logs first (SUGGESTED, APPROVED, SKIPPED, AUTO_ADVANCED, SYSTEM keys). Memory only, reopens Off.

```mermaid
stateDiagram-v2
    [*] --> Off
    Off --> Proposed: setAuto(true) log mode_auto, propose()
    Proposed --> Waiting: approve() then present
    Proposed --> Waiting: advance() subject Next, advance allowed, chain++
    Proposed --> Proposed: skip() then propose
    Proposed --> Waiting: onManualShow, non-substantive
    Proposed --> Off: onManualShow substantive (FreeText, ShowBack, sensitive, FREE_RECALL, FOLLOW_UP)
    Waiting --> Proposed: answer (onAnswer) or onReturn
    Waiting --> Proposed: statement shown, propose at once
    Waiting --> Paused: Stop answer (first), k1 yes BREAK, k3 not yes STOP
    Waiting --> Off: second Stop since mode_auto (ended_stop)
    Proposed --> Paused: pause() OFFICER
    Waiting --> Paused: pause()
    Paused --> Proposed: resume()
    Proposed --> Exhausted: next() == null
    Exhausted --> Proposed: wrapUp, onRoleChanged, resume
    Proposed --> Off: setAuto(false), log mode_manual
    Waiting --> Off: setAuto(false)
    Paused --> Off: setAuto(false)
    Off --> Off: IllegalStateException or SQLException in guarded
```

`autoAdvance` flag: only while not Off; off on role change.

## 6. Handover (`ui/session/Handover.kt`)

Local stage only (not paired). `ready` = mode OFFICER and pending != null.

### Tilt (`TiltDetector`, gravity y, accelerometer low-passed 0.2)

```mermaid
stateDiagram-v2
    [*] --> Disarmed
    Disarmed --> Armed: gy > 3 (top up)
    Armed --> Armed: gy > 3
    Armed --> Flipping: gy < -3, flippedSince = t
    Flipping --> Armed: gy >= -3, reset
    Flipping --> Fired: t - flippedSince >= 600 ms
    Fired --> Disarmed: show(rotate = true), haptic
```

### Volume keys (`HardKeys`, `KeyMode`, `ChordDetector`)

```mermaid
stateDiagram-v2
    [*] --> NONE
    NONE --> OFFICER_READY: volumeKeys and ready
    OFFICER_READY --> STAGE: Volume Up down, show(rotate = false)
    NONE --> STAGE: mode == SHOW
    STAGE --> NONE: leave stage, setting off, dispose
    state STAGE {
        [*] --> Free
        Free --> One: key down
        One --> Both: second key down, bothSince = t
        Both --> One: any key up, reset
        Both --> Leave: held >= 1500 ms, fires once
        Leave --> Free: onLeaveStage (pin check if pinToLeaveStage)
    }
```

- OFFICER_READY consumes Up only. STAGE consumes both keys. NONE leaves keys to the system. TalkBack on: keys never consumed (`MainActivity`).
- `StageGuard.onStage(live)` writes `stage_live` with `commit()` before the subject holds the phone, forcing full credential on next unlock.
