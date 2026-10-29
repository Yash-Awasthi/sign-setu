package app.signsetu.ui.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.signsetu.R
import app.signsetu.camera.LandmarkFrame
import app.signsetu.camera.LandmarkTracker
import app.signsetu.data.content.CAPTION_MODES
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.TOKEN
import app.signsetu.data.content.Gesture
import app.signsetu.data.content.Given
import app.signsetu.data.content.SignMode
import app.signsetu.data.content.TextPlan
import app.signsetu.data.content.WordBeat
import app.signsetu.data.content.chain
import app.signsetu.data.content.planText
import app.signsetu.data.content.negatedText
import app.signsetu.data.content.questionKind
import app.signsetu.data.content.signBack
import app.signsetu.data.session.Profile
import app.signsetu.sign.SignChoice
import app.signsetu.sign.SignWindow
import app.signsetu.ui.avatar.AVATAR_ASPECT
import app.signsetu.ui.avatar.Avatar
import app.signsetu.ui.avatar.LEAD_IN
import app.signsetu.ui.avatar.LEAD_OUT
import app.signsetu.ui.components.LocalOnlineAssistant
import app.signsetu.ui.components.rememberSpeechPlayer
import app.signsetu.ui.theme.LocalAnswerColors
import app.signsetu.ui.theme.Spacing

/** the reader's top labels a signed answer's confirm or say-back signs back before asking. */
fun readBackLabels(ui: SessionUi): List<String> {
    val confirm = ui.confirm?.takeIf { it.queue.isNotEmpty() }
    val sayBack = ui.pending as? Pending.FreeText
    return when {
        confirm != null -> listOfNotNull(confirm.read.firstOrNull())
        sayBack?.ref != null -> ui.signed[sayBack.ref].orEmpty().mapNotNull { it.guesses?.firstOrNull()?.word }
        else -> emptyList()
    }
}


/** A No to a reading of the subject's own answer gives them their own way to say it again, never a dead end. */
internal fun reopensInput(pending: Pending?, yes: Boolean) =
    !yes && !pending.asksChoice && (pending is Pending.ShowBack || (pending is Pending.FreeText && pending.ref != null))

/** A reopened input holds the item until the subject submits it; the next run step would close it. */
internal fun advancesRun(pending: Pending?, yes: Boolean) = !reopensInput(pending, yes)


/** The stage: what the subject sees while the phone faces them, laid out as a one-to-one video call. */
@Composable
fun Stage(
    ui: SessionUi,
    content: ContentBundle,
    tracker: LandmarkTracker?,
    cameraOn: Boolean,
    gestureSpeed: Float,
    /** How a question is signed: the officer's typed-text modes, so a quick question and the same words typed match. */
    askModes: Set<SignMode> = CAPTION_MODES,
    onCard: (String) -> Unit,
    onYesNo: (Boolean) -> Unit,
    onLanguage: (String) -> Unit,
    onDrawing: (ByteArray) -> Unit,
    onWritten: (String) -> Unit,
    onSignFrame: (LandmarkFrame) -> Unit,
    onStartSigning: () -> Unit,
    onDoneSigning: () -> Unit,
    /** Null on a second screen, whose only exit is its own cover and end screens. */
    onOfficer: (() -> Unit)?,
    /** Auto mode's next statement, offered only on the subject's own tap. */
    onSignChoice: (SignChoice?) -> Unit = {},
    onNextRule: () -> Unit = {},
    nextIn: Int? = null,
    onPauseEnd: () -> Unit = {},
    /** Lesson-sheet answers and phrases (LessonInputs.kt); null hides them. */
    onGiven: ((Given?) -> Unit)? = null,
    /** Called just before a tap that matches a gesture proposal: nod, shake, point or palm. */
    onCue: (String) -> Unit = {},
    /** Water or food the subject asks for by themselves; a second screen sends it as a card. */
    onNeed: (String) -> Unit = onCard,
    /** What the camera read, for the officer to accept (VideoCallStage). */
    onCameraRead: (card: String, cue: String?) -> Unit = { _, _ -> },
) {
    val lang = ui.session.subjectLang
    val profile = ui.session.profile
    val pending = ui.pending
    val rule = ruleStep(pending)
    var drawing by remember(pending) { mutableStateOf(pending == Pending.Drawing) }
    // A drawing the officer asked for ends with thanks and a hand-back, never a bare greeting that looks like a reset.
    var drawingSent by remember(pending) { mutableStateOf(false) }
    var writing by remember(pending) { mutableStateOf(false) }
    val drawn = remember(pending) { mutableStateListOf<DrawStroke>() }
    // A young child is not asked to write; anyone else, signers above all, can answer in their own words.
    // A ground rule asks for no answer, so it opens no other way to answer.
    val canWrite = pending != null && profile != Profile.YOUNG_CHILD && rule == null
    // A Yes under a right would read as the person accepting or waiving it; it is only shown.
    val rightsNotice = (pending as? Pending.Ask)?.question?.takeIf(content::isRights)?.let { content.subjectText("rights_interpreter", lang) }
    // The introduction, ground rules and rights only tell; Yes and No under them would ask for an answer nobody asked for.
    val statement = rule != null || rightsNotice != null
    val signing = ui.reading != null
    val narrative = ui.signWindow == SignWindow.NARRATIVE
    val choices = ui.confirm?.queue?.firstOrNull()
    val lesson = lessonQuestion(pending)?.takeIf { onGiven != null && !signing }
    var replays by remember(pending) { mutableIntStateOf(0) }
    // Each time the character finishes an item it waits, then signs it again, a few times at most.
    var finishedPlays by remember(pending) { mutableIntStateOf(0) }
    StagePace.forSession(ui.session.id, initialPace(profile, gestureSpeed))
    val pace = StagePace.speed.floatValue
    // A tapped word plays alone at half speed; the sentence clock waits until it is done.
    var focus by remember(pending) { mutableStateOf<WordBeat?>(null) }
    var wordTaps by remember(pending) { mutableIntStateOf(0) }
    // The word of the waiting line being signed, set by the character's own clock below.
    val idleLit = remember { mutableStateOf<IntRange?>(null) }
    var reacting by remember { mutableStateOf(false) }
    // Answer panels make room while the character signs the item or a replay; its automatic repeats leave them up.
    var moving by remember(pending) { mutableStateOf(true) }
    // ponytail: a fixed wait stands in for a finish the character never reports (no motion, page not loaded).
    LaunchedEffect(moving) { if (moving) { delay(MOVING_MAX_MS); moving = false } }
    val answered = ui.answeredCard != null || ui.answeredYes != null
    LaunchedEffect(ui.answeredCard, ui.answeredYes) { reacting = answered }
    val paused = ui.answeredCard in PauseCards
    val context = LocalContext.current
    var showExpressBoard by rememberSaveable { mutableStateOf(false) }

    // A young child is not startled by a buzz in the hand.
    LaunchedEffect(ui.answeredYes, ui.answeredCard) {
        if (profile != Profile.YOUNG_CHILD) buzzPattern(ui.answeredYes, ui.answeredCard)?.let { buzz(context, it) }
    }

    val speech = rememberSpeechPlayer()
    val audio = speechFor(pending, content, lang)
    // Every item speaks once by itself, signers included (many have some hearing, and a companion may hear); play replays it.
    LaunchedEffect(pending, lang) { if (audio != null) speech.play(audio) }
    val cloud = LocalOnlineAssistant.current
    // Typed text has no recording; with online help allowed, the gateway speaks it in the subject's language.
    LaunchedEffect(pending, lang) {
        val typed = (pending as? Pending.FreeText)?.text
        if (audio == null && typed != null) cloud.speak(typed, lang)?.let(speech::playBytes)
    }
    LaunchedEffect(pending) { withFrameNanos { }; ShowTiming.firstFrame() }

    val item = describeItem(pending, content, lang)
    // Several pictures may be true (a mother and a brother both came): each tap is ticked and logged, the tiles stay,
    // and 5 s after the last tap the call comes back by itself. Nothing to confirm.
    var picked by remember(pending) { mutableStateOf(setOf<String>()) }
    var settled by remember(pending) { mutableStateOf(false) }
    val pick: (String) -> Unit = { card ->
        if (card !in picked) {
            onCard(card)
            if (card != SOMETHING_ELSE && card != "stop") picked = picked + card
        }
    }
    LaunchedEffect(picked) { if (picked.isNotEmpty()) { delay(PICK_SETTLE_MS); settled = true } }
    // A show-back or say-back with pictures asks which one; the character must not act out the officer's reading.
    val checking = pending.asksChoice
    LaunchedEffect(finishedPlays) {
        val repeats = pending is Pending.Ask || pending is Pending.FreeText || pending is Pending.PhraseAsk || rule != null
        if (finishedPlays == 0 || finishedPlays > AUTO_REPEATS || !repeats || signing || answered) return@LaunchedEffect
        delay(REPEAT_PAUSE_MS)
        replays++
    }
    val readLabels = readBackLabels(ui)
    // The hands move only for the caption: a line the stage puts up itself is signed from its English wording.
    val readBack = remember(readLabels, content, lang) { readBackLine(content, readLabels, lang) }
    fun say(key: String) = content.subjectText(key, "en")?.let { CaptionLine(content.subjectText(key, lang), it) }
    val stageLine = when {
        reacting -> say("okay")
        drawingSent -> say("thanks")
        drawing -> say("draw")
        writing -> say("write")
        readBack != null -> readBack
        choices != null -> say("is_this_it")
        narrative -> say("watching")
        // While they sign the character only invites them to; a question or typed text stays as it is.
        signing && pending !is Pending.Ask && pending !is Pending.FreeText && pending !is Pending.PhraseAsk -> null
        pending is Pending.Ask || pending is Pending.PhraseAsk || rule != null -> null
        checking -> say("is_this_it")
        pending is Pending.ShowBack || pending is Pending.FreeText -> null
        // No caption of its own: the hint stands in for it, and is what is signed.
        pending is Pending.Cards -> say("hint_answer")?.copy(shown = null)
        pending == Pending.LanguageFinder -> content.language("en")?.hello?.let { CaptionLine(ui.chosenLanguage?.let { c -> content.language(c)?.hello }, it) }
        else -> content.language("en")?.hello?.let { CaptionLine(content.language(lang)?.hello, it) }
    }
    val caption = if (stageLine != null) stageLine.shown else item.caption
    // What the call shows in the caption's place; the language finder's prompt is in several scripts and lights nothing.
    val shownText = if (pending == Pending.LanguageFinder) caption.orEmpty() else caption ?: content.subjectText("hint_answer", lang).orEmpty()
    // A question or opening stageLine is signed from its English wording, unless a recorded clip plays it.
    val captionEnglish = when {
        stageLine != null || recordedClip(pending, content) != null -> null
        pending is Pending.Ask -> pending.question.text["en"]
        rule != null -> ruleText(content, rule.key, "en")
        else -> null
    }
    val typed = (pending as? Pending.FreeText)?.takeIf { stageLine == null && !it.asksChoice }
    val plan = remember(stageLine, typed, captionEnglish, shownText, content) {
        stageLine?.let { it.plan ?: linePlan(content, it.english, shownText, CAPTION_MODES).takeIf { p -> p.gesture != null } }
            ?: typed?.let { content.planText(it.text, it.modes) }
            ?: captionEnglish?.let {
                val from = (pending as? Pending.Ask)?.question?.text?.get(lang)?.let { q -> shownText.lastIndexOf(q).coerceAtLeast(0) } ?: 0
                linePlan(content, it, shownText, if (pending is Pending.Ask) askModes else CAPTION_MODES, from)
            }?.takeIf { it.gesture != null }
    }
    // Typed text lights its own words; any other stageLine lights the shown word with the same words.json entry as the
    // English word being signed, so a Hindi caption lights its Hindi word.
    val highlightable = typed != null && typed.caption == null
    val focusGesture = remember(focus) {
        focus?.let { f -> (pending as? Pending.FreeText)?.let { content.planText(it.text.substring(f.range), it.modes).gesture } }
    }
    // Every word of the opening is signed or spelled from its English wording; the step's gesture only covers a gap.
    val ruleSigns = remember(rule, content) {
        rule?.let { r -> ruleText(content, r.key, "en")?.let { content.planText(it, CAPTION_MODES).gesture } ?: content.gestures[r.gesture] }
    }
    val gesture = when {
        focusGesture != null -> focusGesture
        plan?.gesture != null -> plan.gesture
        // A line or question with no word the hands can light is not signed: face, head and breath carry on alone.
        stageLine != null || captionEnglish != null -> null
        signing && pending !is Pending.Ask && pending !is Pending.FreeText && pending !is Pending.PhraseAsk -> content.gestures["show_me"]
        pending is Pending.ShowBack -> content.gestures[pending.entry.id]
        pending is Pending.PhraseAsk -> content.gestureFor(pending.phrase, pending.slot)
        pending is Pending.FreeText -> content.gestures["ask"]
        pending is Pending.Ask -> content.gestureFor(pending.question)
        rule != null -> ruleSigns
        else -> null
    }
    // Follows the character through typed text, so the word being signed is lit and its picture or letters shown.
    val playClock = remember(pending) { mutableFloatStateOf(-1f) }
    LaunchedEffect(plan, replays, pace, reacting, focus) {
        playClock.floatValue = -1f
        if (plan == null || focus != null) return@LaunchedEffect
        val start = withFrameNanos { it }
        // The last word stays lit while the hands settle back to rest.
        val end = (plan.beats.lastOrNull()?.end ?: 0f) + LEAD_IN + LEAD_OUT
        while (playClock.floatValue < end) {
            withFrameNanos { now -> playClock.floatValue = (now - start) / 1e9f * pace }
        }
        playClock.floatValue = -1f
    }
    val beat by remember(plan) {
        derivedStateOf {
            val t = playClock.floatValue - LEAD_IN
            // A word stays lit until the next one starts: the hands are still on it between words.
            plan?.beats?.lastOrNull { t >= it.start }
        }
    }
    val letter by remember(plan) {
        derivedStateOf {
            val b = beat ?: return@derivedStateOf -1
            spelledIndex(b, playClock.floatValue - LEAD_IN)
        }
    }
    val onWordTap: ((Int) -> Unit)? = when {
        stageLine != null -> null
        pending is Pending.FreeText && highlightable && plan != null -> { offset ->
            beatAt(plan.beats, offset)?.let { b ->
                if (content.planText(pending.text.substring(b.range), pending.modes).gesture != null) { focus = b; wordTaps++ }
            }
        }
        pending is Pending.Ask || pending is Pending.PhraseAsk || rule != null -> { _ -> replays++; audio?.let { speech.play(it, audioRate(pace)) } }
        else -> null
    }

    val avatar: @Composable (String?) -> Unit = { line ->
        // A subject line the call lights while it waits is spoken and signed once, like every other caption, each
        // word lit as it is signed.
        val idlePlan = remember(line, content, lang) {
            line?.let { k -> content.subjectText(k, "en")?.let { linePlan(content, it, content.subjectText(k, lang).orEmpty(), CAPTION_MODES) } }
        }
        val lineSigns = idlePlan?.gesture
        LaunchedEffect(line, lang) { line?.let { content.subjectAudio(it, lang) }?.let { speech.play(it, audioRate(pace)) } }
        LaunchedEffect(idlePlan, pace) {
            idleLit.value = null
            val p = idlePlan?.takeIf { it.gesture != null } ?: return@LaunchedEffect
            val start = withFrameNanos { it }
            val end = (p.beats.lastOrNull()?.end ?: 0f) + LEAD_OUT
            while (true) {
                val t = withFrameNanos { now -> (now - start) / 1e9f * pace } - LEAD_IN
                if (t >= end) break
                idleLit.value = p.beats.lastOrNull { t >= it.start }?.range?.takeIf { !it.isEmpty() }
            }
            idleLit.value = null
        }
        Box(Modifier.fillMaxSize()) {
            Avatar(
                gesture = lineSigns ?: gesture,
                // A typed sentence or asked question with a recorded clip plays the recording instead of word-by-word signs.
                clip = if (lineSigns == null && focus == null && stageLine == null) recordedClip(pending, content) else null,
                playKey = if (lineSigns != null) "line:$line" else when {
                    reacting -> "react:${ui.answeredCard}:${ui.answeredYes}"
                    focus != null -> "word:$wordTaps"
                    // Each sign being confirmed plays its own read-back.
                    else -> Triple(pending ?: choices, replays, ui.chosenLanguage)
                },
                speed = if (focus != null) 0.5f else pace,
                // Breathing redraws every frame; inside a sign window the landmark tracker needs that time.
                idle = !signing,
                glance = pending != null && !drawing && !reacting,
                question = if (pending is Pending.Ask || pending is Pending.PhraseAsk || pending is Pending.FreeText) questionKind(caption.orEmpty()) else null,
                negated = (pending is Pending.Ask || pending is Pending.PhraseAsk || pending is Pending.FreeText) && negatedText(caption.orEmpty()),
                onFinished = {
                    moving = false
                    if (line == null && focus == null && !reacting) finishedPlays++
                    reacting = false; focus = null
                },
                modifier = Modifier.fillMaxSize().padding(16.dp).aspectRatio(AVATAR_ASPECT).align(Alignment.Center),
            )
            val spelled = if (focus == null) beat?.letters else null
            if (spelled != null) LetterTile(spelled, letter, Modifier.align(Alignment.CenterEnd).padding(end = Spacing.lg))
            val replayable = gesture != null || (audio != null && profile != Profile.ISL_USER)
            // Replay sits left of the character, clear of Stop and the answer bar.
            Column(Modifier.align(Alignment.CenterStart).padding(start = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (pending != null && !drawing && replayable) {
                    ReplayControls(
                        pace = pace,
                        onReplay = { focus = null; moving = true; replays++; audio?.let { speech.play(it, audioRate(pace)) } },
                        onPace = {
                            val next = nextPace(pace)
                            StagePace.speed.floatValue = next
                            focus = null
                            moving = true
                            replays++
                            audio?.let { speech.play(it, audioRate(next)) }
                        },
                        modifier = Modifier,
                    )
                }
            }
        }
    }

    // Answers that need room take the lower part of the call; the character and the camera window stay in view.
    val pauseRow: @Composable () -> Unit = { PauseRow(content, lang, ui.answeredCard, onCard) }
    val panel: (@Composable (Modifier) -> Unit)? = when {
        choices != null -> { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                val last = ui.confirm.queue.size == 1
                SignChoicePanel(choices, content, lang, {
                    onSignChoice(it)
                    reacting = true
                    if (it == null && last && tracker != null) onStartSigning()
                }, Modifier.weight(1f))
                pauseRow()
            }
        }
        drawing -> { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                // Only a picture that was asked for has nothing to go back to.
                DrawingPanel(
                    content, lang, drawn,
                    onDone = { png ->
                        onDrawing(png)
                        drawn.clear()
                        drawing = false
                        if (pending == Pending.Drawing) drawingSent = true else reacting = true
                    },
                    onBack = { drawing = false }.takeIf { pending != Pending.Drawing },
                    modifier = Modifier.weight(1f),
                )
                pauseRow()
            }
        }
        drawingSent -> { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                HandBackPanel(content, lang, onDrawMore = { drawn.clear(); drawingSent = false; drawing = true }, modifier = Modifier.weight(1f))
                pauseRow()
            }
        }
        writing -> { m ->
            WritingPanel(
                content, lang,
                onDone = { onWritten(it); writing = false; reacting = true },
                onBack = { writing = false },
                pauseRow = { PauseRow(content, lang, ui.answeredCard, onCard, compact = true) },
                modifier = m,
            )
        }
        lesson != null && onGiven != null -> { m ->
            AnswerPanel(lesson, content, lang, ui.answeredCard, onCard, { onGiven(it); if (it?.confirmed == true) reacting = true }, onStartSigning.takeIf { tracker != null }, m)
        }
        pending == Pending.LanguageFinder -> { m ->
            // Nobody can read a prompt yet: each language greets once in turn with its tile lit, until one is tapped.
            var hearing by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(ui.chosenLanguage) {
                hearing = null
                if (ui.chosenLanguage != null) return@LaunchedEffect
                delay(1_500)
                for (l in content.languages) {
                    val audio = content.greetingAudio(l.code) ?: continue
                    hearing = l.code
                    speech.play(audio)
                    delay(GREETING_STEP_MS)
                }
                hearing = null
            }
            Column(m, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                LanguageGrid(content, ui.chosenLanguage ?: hearing, Modifier.weight(1f)) { code ->
                    onLanguage(code)
                    content.greetingAudio(code)?.let { speech.play(it) }
                }
                pauseRow()
            }
        }
        item.choices.isNotEmpty() && !signing && !settled -> { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                ChoiceGrid(item.choices, item.numbers, ui.answeredCard, content, lang, pick, Modifier.weight(1f), picked)
                pauseRow()
            }
        }
        else -> null
    }

    Box(Modifier.fillMaxSize()) {
        VideoCallStage(
            ui = ui,
            content = content,
            tracker = tracker,
            avatar = avatar,
            caption = caption,
            captionHighlight = if (typed != null) (focus?.range ?: beat?.range)?.takeIf { highlightable } else beat?.range?.takeIf { !it.isEmpty() },
            idleHighlight = idleLit.value,
            onWordTap = onWordTap,
            onCard = onCard,
            onYesNo = { yes ->
                onYesNo(yes)
                if (reopensInput(pending, yes)) { if (canWrite) writing = true else drawing = true }
            },
            onSignFrame = onSignFrame,
            onStartSigning = onStartSigning,
            onDoneSigning = { onDoneSigning(); reacting = true },
            onOpenExpressBoard = { showExpressBoard = true },
            onCue = onCue,
            onCameraRead = onCameraRead,
            onNeed = onNeed,
            cameraOn = cameraOn,
            onNext = onNextRule.takeIf { rule != null },
            statement = statement,
            rightsNotice = rightsNotice,
            onDraw = { drawing = true }.takeIf { item.canDraw && panel == null },
            onWrite = { writing = true }.takeIf { canWrite && panel == null },
            panel = panel,
            // Picture tiles need little height; the character keeps the rest instead of an empty gap.
            panelCollapsed = moving && !signing && (lesson != null || item.choices.isNotEmpty()) && panel != null &&
                !drawing && !writing && !drawingSent && choices == null && pending != Pending.LanguageFinder,
            onExpandPanel = { moving = false },
            panelShare = if (item.choices.isNotEmpty() && !signing && !settled && panel != null && pending != Pending.LanguageFinder) (if (item.numbers) 0.35f else 0.45f) else 0.6f,
            modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = if (ui.rotated) 180f else 0f }
                // Covered by the pause layer: screen readers must not reach answers that cannot be tapped.
                .then(if (paused) Modifier.clearAndSetSemantics {} else Modifier),
        )
        if (showExpressBoard && !paused) {
            FullChildMuteCommunicationBoard(
                content = content,
                lang = lang,
                chosenCard = ui.answeredCard,
                onCard = { id -> onCard(id); reacting = true },
                onDismiss = { showExpressBoard = false },
            )
        }
        // Over the whole stage, so no answer can be tapped during a break; the officer's way out stays above it.
        // A break can still become a Stop without first ending the pause.
        if (paused) {
            val stopInPause: (@Composable (Modifier) -> Unit)? = if (ui.answeredCard != "break") null else { m ->
                val answer = LocalAnswerColors.current
                SmallAnswer("stop", content.subjectText("stop", lang), stringResource(R.string.stage_stop), answer.pause, answer.onPause, false, { onCard("stop") }, m)
            }
            PauseLayer(content.subjectText("ready", lang), onPauseEnd, stop = stopInPause, modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = if (ui.rotated) 180f else 0f })
        }
        onOfficer?.let { OfficerHandleLayer(it, Modifier.align(if (ui.rotated) Alignment.BottomEnd else Alignment.TopStart).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) }
        nextIn?.let { AutoNextChip(it, Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) }
    }
}

/** What one shown item puts on the stage. */
internal class StageItem(
    val caption: String?,
    val held: String?,
    val choices: List<String>,
    val numbers: Boolean,
    val canDraw: Boolean,
)

internal fun describeItem(pending: Pending?, content: ContentBundle, lang: String): StageItem {
    val (caption, cards) = when (pending) {
        is Pending.Ask -> listOfNotNull(pending.again?.let { content.subjectText(it, lang) }, pending.question.text.get(lang)).joinToString(" ") to pending.question.cards
        is Pending.FreeText -> (pending.caption ?: pending.text) to pending.cardIds
        is Pending.Cards -> null to pending.cardIds
        is Pending.ShowBack -> content.subjectText("is_this_it", lang) to pending.choices.takeIf { pending.asksChoice }.orEmpty()
        is Pending.PhraseAsk -> pending.slot.text.get(lang) to listOf(pending.slot.card)
        is Pending.GroundRules -> ruleStep(pending)?.let { ruleText(content, it.key, lang) } to listOfNotNull(ruleStep(pending)?.card)
        else -> null to emptyList()
    }
    val pictures = cards.filter { it != "draw" && it in content.cards }
    val numbers = pictures.isNotEmpty() && pictures.all { it.matches(Regex("n\\d+")) }
    val held = when {
        pending is Pending.ShowBack && !pending.asksChoice -> pending.entry.card
        pending !is Pending.Cards && pictures.size == 1 -> pictures.first()
        else -> null
    }
    val choices = if (held != null) emptyList() else pictures.take(if (numbers) 10 else 6)
    return StageItem(caption, held, choices, numbers, "draw" in cards)
}

/** A recorded signer for typed text, or for a question whose English wording was recorded. */
private fun recordedClip(pending: Pending?, content: ContentBundle) = when (pending) {
    is Pending.FreeText -> content.clipFor(pending.text)
    is Pending.Ask -> pending.question.text["en"]?.let(content::clipFor)
    is Pending.GroundRules -> ruleStep(pending)?.let { ruleText(content, it.key, "en") }?.let(content::clipFor)
    else -> null
}

private fun speechFor(pending: Pending?, content: ContentBundle, lang: String): String? = when (pending) {
    is Pending.Ask -> content.questionAudio(pending.question.id, lang)
    is Pending.ShowBack -> content.subjectAudio("is_this_it", lang)
    is Pending.PhraseAsk -> content.phraseAudio(pending.phrase, pending.slot, lang)
    is Pending.GroundRules -> ruleStep(pending)?.let { ruleAudio(content, it.key, lang) }
    else -> null
}

/** After the character finishes an item it pauses this long before signing it again, [AUTO_REPEATS] times at most. */
/** A line the stage shows itself: [shown] in the subject's language (null when the hint stands in), signed from
 * [english]; [plan], when set, is already in [shown]'s positions. */
internal data class CaptionLine(val shown: String?, val english: String, val plan: TextPlan? = null)

/** [english] planned, each word's range moved onto the word of [shown] it lights. A word with nothing to light in
 * [shown] is not signed at all, so the hands only ever move for a lit word. */
internal fun linePlan(
    content: ContentBundle, english: String, shown: String, modes: Set<SignMode>,
    /** Where in [shown] the signed wording starts: a repeated question carries a lead-in line first. */
    from: Int = 0, again: Boolean = true,
): TextPlan {
    val plan = content.planText(english, modes)
    if (shown == english) return plan
    val part = shown.substring(from)
    val lit = plan.beats.map { b -> b to captionRange(content, english.substring(b.range), part)?.let { it.first + from..it.last + from } }
    if (lit.all { it.second != null } || !again) return plan.copy(beats = lit.map { (b, r) -> b.copy(range = r ?: IntRange.EMPTY) })
    val kept = lit.filter { it.second != null }.sortedBy { it.first.range.first }.joinToString(" ") { english.substring(it.first.range) }
    return if (kept.isBlank()) TextPlan(null, emptyList()) else linePlan(content, kept, shown, modes, from, again = false)
}

/** Read words signed back as they were read, then "Is this it?" from its wording; the caption is the words and the
 * question, each lit while it plays. */
internal fun readBackLine(content: ContentBundle, labels: List<String>, lang: String): CaptionLine? {
    val signs = labels.mapNotNull { l -> content.signBack(l)?.takeIf { it.keys.isNotEmpty() }?.let { l to it } }
    if (signs.isEmpty()) return null
    val words = signs.joinToString(" ") { it.first }
    val askEn = content.subjectText("is_this_it", "en").orEmpty()
    val shown = "$words… ${content.subjectText("is_this_it", lang) ?: askEn}"
    val parts = mutableListOf<Gesture>()
    val beats = mutableListOf<WordBeat>()
    var t = 0f
    var at = 0
    for ((word, g) in signs) {
        val end = t + g.keys.maxOf { it.t }
        beats += WordBeat(at until at + word.length, null, null, t, end)
        parts += g
        t = end + 0.3f
        at += word.length + 1
    }
    val ask = linePlan(content, askEn, shown.substring(words.length + 2), CAPTION_MODES)
    ask.gesture?.let { g ->
        parts += g
        val base = words.length + 2
        beats += ask.beats.map { b -> b.copy(range = if (b.range.isEmpty()) b.range else b.range.first + base..b.range.last + base, start = b.start + t, end = b.end + t) }
    }
    return CaptionLine(shown, "$words… $askEn", TextPlan(chain("read_back", parts), beats))
}

private const val REPEAT_PAUSE_MS = 2_500L
private const val MOVING_MAX_MS = 15_000L
private const val AUTO_REPEATS = 2

/** The range in [caption] of the word that means [english]: a caption word written in the same words.json entry. */
internal fun captionRange(content: ContentBundle, english: String, caption: String): IntRange? {
    val forms = content.words[english.lowercase()]?.w?.map { it.lowercase() }?.toSet() ?: return null
    return TOKEN.findAll(caption).firstOrNull { it.value.lowercase() in forms }?.range
}

/** Recorded speech below normal speed only goes to 0.75: slower than that, words smear. */
private fun audioRate(pace: Float) = if (pace < 1f) 0.75f else 1f

/** Which unit of a spelled word the letter tile shows [t] seconds into the plan's gesture, before its lead-in:
 * one akshara at a time, left to right. -1 when the beat only holds a picture. */
internal fun spelledIndex(beat: WordBeat, t: Float): Int {
    val n = beat.letters?.size ?: return -1
    if (n == 0) return -1
    // A zero-length beat cannot be divided; show its first unit rather than crashing on the range.
    if (beat.end <= beat.start) return 0
    val progress = ((t - beat.start) / (beat.end - beat.start)).coerceIn(0f, 1f)
    return (progress * n).toInt().coerceIn(0, n - 1)
}

/** After the last picture tapped, how long the tiles wait for another before the call comes back. */
private const val PICK_SETTLE_MS = 5_000L

/** Long enough for one recorded greeting before the next language's. */
private const val GREETING_STEP_MS = 4_500L
