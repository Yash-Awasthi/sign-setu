package app.signsetu.ui.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import android.os.SystemClock
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.signsetu.R
import app.signsetu.ui.theme.LocalAnswerColors
import app.signsetu.ui.theme.LocalCall
import app.signsetu.camera.CameraMirror
import app.signsetu.camera.Everyday
import app.signsetu.camera.Head
import app.signsetu.camera.LandmarkFrame
import app.signsetu.camera.LandmarkTracker
import app.signsetu.camera.countOpenFingers
import app.signsetu.data.content.or
import app.signsetu.data.content.AnswerType
import app.signsetu.data.content.ContentBundle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Create
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import app.signsetu.ui.theme.LocalStageColors
import app.signsetu.ui.components.CardImage
import app.signsetu.ui.theme.Baloo
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** The transient reaction chip: plain text, or a counted noun that needs a plural form. */
private sealed interface Reaction {
    data class Text(@StringRes val id: Int, val arg: Any? = null) : Reaction
    data class Count(@PluralsRes val id: Int, val n: Int) : Reaction
}

/** The stage as a one-to-one video call: the character is the caller, the subject's camera a small window. */
@Composable
fun VideoCallStage(
    ui: SessionUi,
    content: ContentBundle,
    tracker: LandmarkTracker?,
    /** The character as the stage builds it, given the subject line the call has lit while it waits, if any. */
    avatar: @Composable (String?) -> Unit,
    caption: String?,
    captionHighlight: IntRange?,
    /** Tapping the caption replays it, or one word of typed text. */
    onWordTap: ((Int) -> Unit)?,
    onCard: (String) -> Unit,
    onYesNo: (Boolean) -> Unit,
    onSignFrame: (LandmarkFrame) -> Unit,
    onStartSigning: () -> Unit,
    onOpenExpressBoard: () -> Unit,
    onCue: (String) -> Unit,
    /** Water or food asked for by the subject: a request, not an answer to what is on screen. */
    onNeed: (String) -> Unit,
    modifier: Modifier = Modifier,
    cameraOn: Boolean = true,
    /** Set on the opening and ground rules, which move on only when the subject is ready. */
    onNext: (() -> Unit)? = null,
    /** The item only tells, so a nod or shake must not be offered as an answer nobody asked for. */
    statement: Boolean = false,
    /** A right is only shown: it gets a way to say it was not understood, never a Yes. */
    rightsNotice: String? = null,
    onDoneSigning: () -> Unit = {},
    onDraw: (() -> Unit)? = null,
    onWrite: (() -> Unit)? = null,
    /** Drawing, writing, pictures and the other answer panels take the lower part of the call. */
    panel: (@Composable (Modifier) -> Unit)? = null,
    /** The share of the height the panel takes; the character gets the rest. */
    panelShare: Float = 0.6f,
    /** A nod, shake, thumb, point, held fingers, palm or shrug the camera read, sent to the officer to accept. The
     * person may not read, so nothing is asked of them; null (a second screen) keeps the tap-to-accept chip. */
    onCameraRead: ((card: String, cue: String?) -> Unit)? = null,
) {
    val lang = ui.session.subjectLang
    val pending = ui.pending
    val signing = ui.reading != null
    val haptic = LocalHapticFeedback.current

    var pipOffsetX by remember { mutableFloatStateOf(0f) }
    var pipOffsetY by remember { mutableFloatStateOf(0f) }
    var swapped by remember { mutableStateOf(false) }

    // A string resource and its optional argument, resolved where it is drawn so a language change is followed.
    var recentReaction by remember { mutableStateOf<Reaction?>(null) }
    LaunchedEffect(recentReaction) {
        if (recentReaction != null) {
            delay(4000L)
            recentReaction = null
        }
    }
    fun react(@StringRes reaction: Int, arg: Any? = null, feel: HapticFeedbackType = HapticFeedbackType.LongPress) {
        haptic.performHapticFeedback(feel)
        recentReaction = Reaction.Text(reaction, arg)
    }
    fun reactCount(@PluralsRes reaction: Int, n: Int) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        recentReaction = Reaction.Count(reaction, n)
    }

    // Stays as the officer left it for every item: turning it on once is enough for the whole session.
    var showVictimSkeleton by StageSkeleton.on

    // Left alone, the hint under the caption lights up, then a reassurance takes its place, each spoken and signed once;
    // once per quiet spell, and a touch or a signed answer starts the wait again. No yes or no signs: they read as answers.
    var guideStep by remember { mutableIntStateOf(-1) }
    var touches by remember { mutableIntStateOf(0) }
    LaunchedEffect(touches, signing, pending) {
        guideStep = -1
        if (signing || pending == null) return@LaunchedEffect
        delay(HINT_IDLE_MS)
        for (i in IdleSteps.indices) {
            if (i == HintSteps) delay(REASSURE_IDLE_MS)
            guideStep = i
            delay(IdleSteps[i].ms)
        }
        guideStep = -1
    }
    val idleStep = IdleSteps.getOrNull(guideStep)
    val idleLine = idleStep?.line?.takeIf { it != "hint_answer" }?.let { content.subjectText(it, lang) }
    val hintLit = idleStep?.line == "hint_answer"

    // The card a gesture proposed and the cue it came from; the cue is logged only if the subject taps it.
    var proposal by remember { mutableStateOf<Pair<String, String?>?>(null) }
    LaunchedEffect(pending) { proposal = null }
    // A courtesy the camera read — hands joined, a wave, a hand on the chest, a bow — waits here as a word the
    // subject can accept. It is never logged until they tap it, and it is forgotten if they ignore it.
    var courtesy by remember { mutableStateOf<Pair<String, String?>?>(null) }
    LaunchedEffect(courtesy) { if (courtesy != null) { delay(COURTESY_MS); courtesy = null } }
    var lastRead by remember { mutableStateOf("" to 0L) }
    fun propose(card: String, cue: String?) {
        if (statement && card != "stop") return
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onCameraRead?.let { send ->
            // One nod fires several head events; the same read within a few seconds is the same answer.
            val now = SystemClock.elapsedRealtime()
            if (card == lastRead.first && now - lastRead.second < SAME_READ_MS) return
            lastRead = card to now
            send(card, cue)
            return
        }
        proposal = card to cue
    }
    fun answer(card: String, cue: String? = null) {
        cue?.let(onCue)
        when {
            card == "yes" || card == "no" -> onYesNo(card == "yes")
            card in NeedCards && !pending.offers(card) -> onNeed(card)
            else -> onCard(card)
        }
        when {
            card == "yes" -> react(R.string.call_r_yes)
            card == "no" -> react(R.string.call_r_no)
            card == "stop" -> react(R.string.call_r_stop)
            card == "dont_know" || card == DONT_UNDERSTAND || card == "wrong" -> react(R.string.call_r_unsure, feel = HapticFeedbackType.TextHandleMove)
            card == "water" -> react(R.string.call_r_water)
            card == "food" -> react(R.string.call_r_food)
            card.matches(FINGERS) -> reactCount(R.plurals.call_r_fingers, card.drop(1).toInt())
            else -> react(R.string.call_r_point, cardLabel(content, card, lang), HapticFeedbackType.TextHandleMove)
        }
        proposal = null
    }

    val hint = content.subjectText("hint_answer", lang)
    // Their language is not known yet, so the prompt is in several scripts at once.
    val promptText = if (pending == Pending.LanguageFinder) LANGUAGE_PROMPT else caption ?: hint ?: stringResource(R.string.call_with_you)

    // Held-up fingers answer an age or count question without opening a keypad.
    val isNumberOrAgeQuestion = remember(pending) {
        val q = (pending as? Pending.Ask)?.question
        q?.answer == AnswerType.AGE || q?.answer == AnswerType.COUNT || q?.id in setOf("w_age", "p_siblings", "a_age")
    }
    var heldFingerCount by remember { mutableIntStateOf(-1) }
    var heldFingerSince by remember { mutableLongStateOf(0L) }

    // Hands raised when a window closes on its own must come down once before they open the next one, or the answer
    // just ingested would be read again straight away.
    val handsDown = remember { mutableStateOf(true) }
    // A stray detection must not open a window: hands stay in view for HANDS_HOLD_MS, and after a window closes the
    // answer row holds still for REOPEN_MS, so the tiles never flip back and forth while the subject decides.
    val handsSince = remember { mutableLongStateOf(0L) }
    val closedAt = remember { mutableLongStateOf(0L) }
    var wasSigning by remember { mutableStateOf(signing) }
    LaunchedEffect(signing) {
        if (wasSigning && !signing) { handsDown.value = false; closedAt.longValue = SystemClock.elapsedRealtime() }
        wasSigning = signing
    }

    val onFrameAnalyzed: (LandmarkFrame) -> Unit = { frame ->
        onSignFrame(frame)
        val now = frame.timestampMs
        val handsVisible = frame.leftHand != null || frame.rightHand != null
        val clock = SystemClock.elapsedRealtime()
        if (!handsVisible) { handsDown.value = true; handsSince.longValue = 0L } else if (handsSince.longValue == 0L) handsSince.longValue = clock
        // The camera analyses every frame here, so its own head proposals never fire: outside a sign window a nod or
        // shake is offered from the frame itself. Inside one it is part of the answer (SessionViewModel's head margin).
        if (!signing) when (frame.head) {
            Head.Nod -> propose("yes", "nod")
            Head.Shake -> propose("no", "shake")
            null -> Unit
        }
        val held = handsVisible && clock - handsSince.longValue >= HANDS_HOLD_MS
        // Picture tiles are the answer while they are up: a hand seen then must not cover them with a reading window.
        if (held && panel == null && !signing && !statement && handsDown.value && clock - closedAt.longValue >= REOPEN_MS) onStartSigning()
        if (isNumberOrAgeQuestion && handsVisible) {
            val fingers = countOpenFingers(frame)
            if (fingers != null) {
                if (fingers == heldFingerCount) {
                    if (now - heldFingerSince > 450L && heldFingerSince > 0L) {
                        propose("n$fingers", null)
                        // Far in the future, so the same held count is sent once.
                        heldFingerSince = Long.MAX_VALUE / 2
                    }
                } else {
                    heldFingerCount = fingers
                    heldFingerSince = now
                }
            } else {
                heldFingerCount = -1
                heldFingerSince = 0L
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LocalCall.current.bg)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        if (awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed }) touches++
                    }
                }
            },
    ) {
        val avatarView: @Composable () -> Unit = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.radialGradient(listOf(LocalCall.current.surface, LocalCall.current.bg))),
                )
                avatar(idleStep?.line)

        }

        val cameraView: @Composable () -> Unit = {
            Box(Modifier.fillMaxSize()) {
                CameraMirror(
                    tracker = tracker,
                    modifier = Modifier.fillMaxSize(),
                    showSkeleton = showVictimSkeleton,
                showFace = true,
                fill = swapped,
                    onFrame = onFrameAnalyzed,
                    onHead = { head ->
                        when (head) {
                            Head.Nod -> propose("yes", "nod")
                            Head.Shake -> propose("no", "shake")
                        }
                    },
                    onPoint = { pointId -> propose(pointId, "point") },
                    onStopPalm = { propose("stop", "palm") },
                    onShrug = { propose("dont_know", "shrug") },
                    onEveryday = { seen ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        courtesy = when (seen) {
                            Everyday.THANKS -> "thank_you" to "thanks"
                            else -> "namaste" to seen.name.lowercase()
                        }
                    },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(LocalCall.current.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.ic_videocam), contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(24.dp))
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (showVictimSkeleton) LocalCall.current.bg.copy(alpha = 0.87f) else Color(0x99000000),
                    border = BorderStroke(1.dp, if (showVictimSkeleton) Color(0xFFFBBF24) else Color(0x44FFFFFF)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clickable { showVictimSkeleton = !showVictimSkeleton },
                ) {
                    Text(
                        text = stringResource(if (showVictimSkeleton) R.string.call_skeleton_on else R.string.call_skeleton_off),
                        fontSize = 9.sp,
                        color = if (showVictimSkeleton) Color(0xFFFDE68A) else LocalCall.current.text,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (signing) Color(0xFF10B981) else Color(0xCC000000),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp),
                ) {
                    Text(
                        text = stringResource(if (signing) R.string.call_signing else R.string.call_you),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }

        if (panel != null) {
            Column(Modifier.fillMaxSize().imePadding()) {
                Box(Modifier.weight(1f - panelShare).fillMaxWidth()) { avatarView() }
                Text(
                    promptText, fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                )
                panel(Modifier.weight(panelShare).fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp))
            }
        } else Box(
            modifier = Modifier.fillMaxSize().padding(bottom = if (swapped && cameraOn) 0.dp else 90.dp),
            contentAlignment = Alignment.Center,
        ) { if (swapped && cameraOn) cameraView() else avatarView() }

        if (cameraOn) {
            val pipWidth = 130.dp
            val pipShape = RoundedCornerShape(20.dp)

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = if (swapped) 44.dp else 13.dp, end = 13.dp)
                    .offset { IntOffset(pipOffsetX.roundToInt(), pipOffsetY.roundToInt()) }
                    .size(pipWidth, pipWidth * (16f / 9f))
                    .clip(pipShape)
                    .border(
                        BorderStroke(
                            if (signing) 3.dp else 2.dp,
                            if (signing) Color(0xFF10B981) else Color(0x88FFFFFF),
                        ),
                        pipShape,
                    )
                    .shadow(8.dp, pipShape)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            pipOffsetX += dragAmount.x
                            pipOffsetY += dragAmount.y
                        }
                    }
                    .clickable { swapped = !swapped },
            ) {
                if (swapped && panel == null) avatarView() else cameraView()
            }
        }

        if (panel != null) return@Box

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 124.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // One dot per sign seen, never the reader's words: those are proposals the subject has not confirmed.
            SeenSigns(ui.reading.orEmpty(), Modifier)
            SignSlowerCue(ui.reading?.lastOrNull()?.takeIf { it.slower }, content, lang, Modifier)

            courtesy?.let { (card, cue) ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = LocalCall.current.accentDeep,
                    border = BorderStroke(1.5.dp, LocalCall.current.accentSoft),
                    modifier = Modifier.padding(horizontal = 8.dp).clickable {
                        answer(card, cue)
                        courtesy = null
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("🙏", fontSize = 16.sp)
                        Text(
                            text = stringResource(R.string.call_courtesy, cardLabel(content, card, lang)),
                            fontFamily = Baloo,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = LocalCall.current.accentSoft,
                        )
                    }
                }
            }

            AnimatedVisibility(visible = recentReaction != null) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = LocalCall.current.surface.copy(alpha = 0.93f),
                    border = BorderStroke(1.dp, LocalCall.current.accent),
                ) {
                    Text(
                        text = when (val reaction = recentReaction) {
                            null -> ""
                            is Reaction.Text -> if (reaction.arg == null) stringResource(reaction.id) else stringResource(reaction.id, reaction.arg)
                            is Reaction.Count -> pluralStringResource(reaction.id, reaction.n, reaction.n)
                        },
                        fontFamily = Baloo,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = LocalCall.current.accent,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = LocalCall.current.bg.copy(alpha = 0.87f),
                border = BorderStroke(1.5.dp, Color(0x44FFFFFF)),
                shadowElevation = 10.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CallCaption(promptText, captionHighlight, onWordTap)
                    rightsNotice?.let {
                        Text(it, fontSize = 13.sp, color = LocalCall.current.accentSoft, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                    }
                    // A young child is not made to read instructions; the character and the guide show them.
                    if (!ui.child && !statement && caption != null) (idleLine ?: hint)?.let {
                        // Lit and larger while the character acts it out, and the reassurance takes its place when idle.
                        val lit = hintLit || idleLine != null
                        Text(it, fontSize = if (lit) 15.sp else 11.sp, color = if (lit) Color.White else LocalCall.current.muted, fontWeight = if (lit) FontWeight.Bold else null, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }

            // Stop and the honest ways out stay in reach on every item, as on the full stage; Stop is the only one named.
            val answers = LocalAnswerColors.current
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallAnswer("stop", content.subjectText("stop", lang), stringResource(R.string.stage_stop), answers.pause, answers.onPause, ui.answeredCard == "stop", { answer("stop") }, Modifier.weight(1.6f), height = 48.dp)
                if (signing) {
                    SmallAnswer("done", content.subjectText("done", lang), stringResource(R.string.stage_done), answers.yes, answers.onYes, false, onDoneSigning, Modifier.weight(1.6f), height = 48.dp, icon = rememberVectorPainter(Icons.Filled.Check))
                }
                if (rightsNotice != null) {
                    SmallAnswer(DONT_UNDERSTAND, content.subjectText(DONT_UNDERSTAND, lang), stringResource(R.string.stage_dont_understand), answers.unsure, answers.onUnsure, ui.answeredCard == DONT_UNDERSTAND, { answer(DONT_UNDERSTAND) }, Modifier.weight(1.6f), height = 48.dp)
                }
                if (onNext != null) {
                    SmallAnswer("next", content.subjectText("next", lang), stringResource(R.string.stage_next), answers.yes, answers.onYes, false, onNext, Modifier.weight(1.6f), height = 48.dp, icon = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowForward))
                }
            }
            // Their own row, so Stop and Next never change size or place between items.
            if (pending != null && !signing && !statement) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallAnswer("dont_know", null, content.subjectText("dont_know", lang) ?: stringResource(R.string.stage_dont_know), answers.unsure, answers.onUnsure, ui.answeredCard == "dont_know", { answer("dont_know") }, Modifier.weight(1f), height = 64.dp, caption = content.subjectText("dont_know", lang) ?: stringResource(R.string.stage_dont_know))
                SmallAnswer(DONT_UNDERSTAND, null, content.subjectText(DONT_UNDERSTAND, lang) ?: stringResource(R.string.stage_dont_understand), answers.unsure, answers.onUnsure, ui.answeredCard == DONT_UNDERSTAND, { answer(DONT_UNDERSTAND) }, Modifier.weight(1f), height = 64.dp, caption = content.subjectText(DONT_UNDERSTAND, lang) ?: stringResource(R.string.stage_dont_understand))
                SmallAnswer("wrong", null, content.subjectText("wrong", lang) ?: stringResource(R.string.stage_wrong), answers.unsure, answers.onUnsure, ui.answeredCard == "wrong", { answer("wrong") }, Modifier.weight(1f), height = 64.dp, caption = content.subjectText("wrong", lang) ?: stringResource(R.string.stage_wrong))
            }
            if (!signing && (onDraw != null || onWrite != null)) {
                val stage = LocalStageColors.current
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    onDraw?.let { SmallAnswer("draw", content.subjectText("draw", lang), stringResource(R.string.stage_draw), stage.tool, stage.onTool, false, it, Modifier.weight(1f), height = 48.dp, icon = rememberVectorPainter(Icons.Filled.Create)) }
                    onWrite?.let { SmallAnswer("write", content.subjectText("write", lang), stringResource(R.string.stage_write), stage.tool, stage.onTool, false, it, Modifier.weight(1f), height = 48.dp, icon = painterResource(R.drawable.ic_keyboard)) }
                }
            }
        }

        proposal?.let { (card, cue) ->
            val label = cardLabel(content, card, lang)
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = LocalCall.current.accentDeep,
                border = BorderStroke(2.dp, LocalCall.current.accent),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 184.dp)
                    .clickable { answer(card, cue) }
                    .semantics { role = Role.Button; contentDescription = label },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (content.hasCardImage(card)) CardImage(card, Modifier.size(36.dp))
                    Text(label, fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                    content.subjectText("is_this_it", lang)?.let { Text(it, fontSize = 14.sp, color = LocalCall.current.accentSoft) }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(32.dp),
            color = LocalCall.current.surface.copy(alpha = 0.93f),
            border = BorderStroke(1.dp, LocalCall.current.line),
            shadowElevation = 12.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CallActionButton("water", cardLabel(content, "water", lang), Color(0xFF0369A1), highlight = false) {
                    answer("water")
                }
                CallActionButton("food", cardLabel(content, "food", lang), Color(0xFFD97706), highlight = false) {
                    answer("food")
                }
                if (!statement) {
                    CallActionButton("yes", cardLabel(content, "yes", lang), Color(0xFF15803D), highlight = false) {
                        answer("yes")
                    }
                    CallActionButton("no", cardLabel(content, "no", lang), Color(0xFFB91C1C), highlight = false) {
                        answer("no")
                    }
                }
                CallActionButton("express", cardLabel(content, "help", lang), Color(0xFF7C3AED), highlight = false) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOpenExpressBoard()
                }
            }
        }
    }
}

/** Whether the subject's own camera picture shows the traced skeleton; kept outside the screen so it survives the
 * handover between items, where the stage is built again. */
internal object StageSkeleton {
    val on = mutableStateOf(false)
}

/** One idle beat: the subject line shown ("hint_answer" lights the hint) and for how long. */
private class IdleStep(val line: String, val ms: Long)
// Long enough for each line to be spoken and signed in full.
private val IdleSteps = listOf(IdleStep("hint_answer", 10_000), IdleStep("watching", 7_000), IdleStep("rule_dont_know", 7_000))
/** The first step lights the hint; the rest reassure after a longer wait. */
private const val HintSteps = 1
private const val HINT_IDLE_MS = 8_000L
private const val REASSURE_IDLE_MS = 15_000L
/** A repeat of the same camera read within this is not sent again. */
private const val SAME_READ_MS = 3_000L

/** How long a read courtesy waits for the subject's tap before it is dropped. */
private const val COURTESY_MS = 6_000L

private val FINGERS = Regex("n\\d+")

private fun cardLabel(content: ContentBundle, card: String, lang: String): String =
    content.cards[card]?.label?.or(lang) ?: card.removePrefix("n").takeIf { card.matches(FINGERS) } ?: card

/** The call's caption: the word being signed is lit, and a tap replays the line or that word. */
@Composable
private fun CallCaption(text: String, highlight: IntRange?, onWordTap: ((Int) -> Unit)?) {
    val styled = buildAnnotatedString {
        append(text)
        highlight?.takeIf { it.last < text.length }?.let {
            addStyle(SpanStyle(color = LocalCall.current.bg, background = LocalCall.current.accent), it.first, it.last + 1)
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val replayName = stringResource(R.string.word_replay)
    // The caller's lambda changes every frame of typed-text playback; the tap detector must not restart with it.
    val onTap by rememberUpdatedState(onWordTap)
    val tap = if (onWordTap == null) Modifier else Modifier
        .pointerInput(Unit) { detectTapGestures { p -> layout?.let { onTap?.invoke(it.getOffsetForPosition(p)) } } }
        .semantics { onClick(replayName) { onTap?.invoke(0); true } }
    Text(
        styled, fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White, textAlign = TextAlign.Center,
        onTextLayout = { layout = it }, modifier = tap,
    )
}

private const val HANDS_HOLD_MS = 800L
private const val REOPEN_MS = 10_000L

private const val LANGUAGE_PROMPT = "भाषा चुनें · ভাষা বাছুন · மொழி · Choose your language"
