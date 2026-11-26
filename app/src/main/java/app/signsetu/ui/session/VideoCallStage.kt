package app.signsetu.ui.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
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
import app.signsetu.camera.SkeletonOverlay
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
import androidx.compose.ui.semantics.contentDescription
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.imePadding
import app.signsetu.ui.theme.Baloo
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import app.signsetu.ui.theme.Space
import app.signsetu.ui.theme.TypeSize
import app.signsetu.ui.theme.Radius
import app.signsetu.ui.theme.StageTones

/** The transient reaction chip: plain text, or a counted noun that needs a plural form. */
private sealed interface Reaction {
    data class Text(@StringRes val id: Int, val arg: Any? = null) : Reaction
    data class Count(@PluralsRes val id: Int, val n: Int) : Reaction
}

/** The stage as a one-to-one video call: the character is the caller, the subject's camera a small window. */
@Composable
internal fun VideoCallStage(
    ui: SessionUi,
    content: ContentBundle,
    tracker: LandmarkTracker?,
    /** The character as the stage builds it, given the subject line the call has lit while it waits, if any. */
    avatar: @Composable (String?) -> Unit,
    caption: String?,
    /** The word being signed, with the text it belongs to: it lights only where that text is the one on screen. */
    captionLight: CaptionLight?,
    /** The word of the waiting line under the caption being signed now. */
    idleHighlight: CaptionLight?,
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
    /** The messages shortcut, offered beside Hold at the same level. */
    onMessages: (() -> Unit)? = null,
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
    /** The language the subject picked in the tool sheet; LANGUAGE is shown only when this is set. */
    onLanguage: ((String) -> Unit)? = null,
    /** The item's picture cards, offered in the sheet's PICTURES group. */
    pictureCards: List<String> = emptyList(),
    /** Drawing, writing, pictures and the other answer panels take the lower part of the call. */
    panel: (@Composable (Modifier) -> Unit)? = null,
    /** The share of the height the panel takes; the character gets the rest. */
    panelShare: Float = 0.6f,
    /** While the character signs, the panel waits as a strip below the caption; a tap on it opens it early. */
    panelCollapsed: Boolean = false,
    onExpandPanel: () -> Unit = {},
    /** A nod, shake, thumb, point, held fingers, palm, shrug or courtesy the camera read, sent to the officer to accept. */
    onCameraRead: (card: String, cue: String?) -> Unit = { _, _ -> },
) {
    val lang = ui.session.subjectLang
    val pending = ui.pending
    val signing = ui.reading != null
    val haptic = LocalHapticFeedback.current
    // The pinned need row's labels, resolved once so its tap can name what was asked for.
    val needNames = NEED_IDS.associateWith { needLabel(it, content, lang) }

    var pipOffsetX by remember { mutableFloatStateOf(0f) }
    var pipOffsetY by remember { mutableFloatStateOf(0f) }
    var swapped by rememberSaveable { mutableStateOf(false) }
    // The unsure trio stays folded behind one button, so the call shows at most three paths at once.
    var moreAnswers by remember(pending) { mutableStateOf(false) }
    // One sheet holds the whole toolset; a tool tag names the group to show first.
    var sheetFor by remember { mutableStateOf<StageTool?>(null) }
    // The tool last picked from the bar, so a slide up opens the sheet on it; none means the default group.
    var lastTool by remember { mutableStateOf<StageTool?>(null) }
    // 0 to 1 while the handle is pulled up; past a quarter, letting go opens the sheet.
    var slideUp by remember { mutableFloatStateOf(0f) }

    // A string resource and its optional argument, resolved where it is drawn so a language change is followed.
    var recentReaction by remember { mutableStateOf<Reaction?>(null) }
    LaunchedEffect(recentReaction) {
        if (recentReaction != null) {
            delay(4000L)
            recentReaction = null
        }
    }
    /** [feel] null is for a caller that has already ticked, so one tap buzzes once. */
    fun react(@StringRes reaction: Int, arg: Any? = null, feel: HapticFeedbackType? = HapticFeedbackType.LongPress) {
        feel?.let { haptic.performHapticFeedback(it) }
        recentReaction = Reaction.Text(reaction, arg)
    }
    fun reactCount(@PluralsRes reaction: Int, n: Int) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        recentReaction = Reaction.Count(reaction, n)
    }

    // One switch with the officer's menu, kept for every item of the session.
    var showVictimSkeleton by SkeletonOverlay.on

    // Left alone, the hint under the caption lights up, then a reassurance takes its place, each spoken and signed once;
    // once per quiet spell, and a touch or a signed answer starts the wait again. No yes or no signs: they read as answers.
    var guideStep by remember { mutableIntStateOf(-1) }
    var touches by remember { mutableIntStateOf(0) }
    // Avatar and caption only until a touch: every button shows on a tap and fades CONTROLS_MS after the last one.
    // A screen reader user explores by touch without taps landing here, so their buttons always stay.
    val context = LocalContext.current
    val explore = remember { (context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager).isTouchExplorationEnabled }
    var tapped by remember { mutableStateOf(false) }
    LaunchedEffect(touches) {
        if (touches == 0) return@LaunchedEffect
        tapped = true
        delay(CONTROLS_MS)
        tapped = false
    }
    val controls = tapped || explore
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

    // What the camera reads goes to the officer, never to the person: they may not read, so nothing is asked of them.
    var lastRead by remember { mutableStateOf("" to 0L) }
    fun propose(card: String, cue: String?) {
        if (statement && card != "stop") return
        // One nod fires several head events; the same read within a few seconds is the same answer.
        val now = SystemClock.elapsedRealtime()
        if (card == lastRead.first && now - lastRead.second < SAME_READ_MS) return
        lastRead = card to now
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onCameraRead(card, cue)
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
    }

    fun courtesyRead(seen: Everyday) = if (seen == Everyday.THANKS) propose("thank_you", "thanks") else propose("namaste", seen.name.lowercase())

    val hint = content.subjectText("hint_answer", lang)
    // Their language is not known yet, so the prompt is in several scripts at once.
    val promptText = if (pending == Pending.LanguageFinder) LANGUAGE_PROMPT else caption ?: hint ?: stringResource(R.string.call_with_you)
    // The preset chips follow the words on the call: what was asked, and what the camera has read so far.
    val heard = (listOf(promptText) + ui.reading.orEmpty().mapNotNull { it.word }).joinToString(" ")
    val related = remember(heard, content) { relatedCards(heard, content.cards.keys) }

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
        // The camera analyses every frame here, so its own proposal callbacks never fire: outside a sign window each
        // reading is taken from the frame itself. Inside one, head movement is part of the answer (SessionViewModel).
        if (!signing) {
            when (frame.head) {
                Head.Nod -> propose("yes", "nod")
                Head.Shake -> propose("no", "shake")
                null -> Unit
            }
            frame.pointsAt?.let { propose(it, "point") }
            if (frame.stopPalm) propose("stop", "palm")
            if (frame.shrug) propose("dont_know", "shrug")
            frame.everyday?.let(::courtesyRead)
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
                    onEveryday = ::courtesyRead,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(LocalCall.current.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.ic_videocam), contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(Space.xl))
                    }
                }

                Surface(
                    shape = RoundedCornerShape(Radius.r12),
                    color = if (showVictimSkeleton) LocalCall.current.bg.copy(alpha = 0.87f) else StageTones.chromeVeil,
                    border = BorderStroke(Space.hair, if (showVictimSkeleton) StageTones.victimBorder else StageTones.hairline),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Space.s6)
                        .toggleable(value = showVictimSkeleton, role = Role.Switch) { showVictimSkeleton = it },
                ) {
                    Text(
                        text = stringResource(if (showVictimSkeleton) R.string.call_skeleton_on else R.string.call_skeleton_off),
                        fontSize = TypeSize.s9,
                        color = if (showVictimSkeleton) StageTones.victimInk else LocalCall.current.text,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = Space.s6, vertical = Space.s3),
                    )
                }

                Surface(
                    shape = RoundedCornerShape(Radius.r8),
                    color = if (signing) StageTones.signing else StageTones.veil,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(Space.s6),
                ) {
                    Text(
                        text = stringResource(if (signing) R.string.call_signing else R.string.call_you),
                        color = Color.White,
                        fontSize = TypeSize.s10,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = Space.s6, vertical = Space.xxs),
                    )
                }
            }
        }

        if (panel != null) {
            // Lifted clear of the pinned need row below it, so no panel button is covered.
            Column(Modifier.fillMaxSize().imePadding().padding(bottom = NEED_ROW_HEIGHT)) {
                Box(Modifier.weight(if (panelCollapsed) 1f else 1f - panelShare).fillMaxWidth()) { avatarView() }
                Text(
                    litWord(promptText, captionLight?.inText(promptText)), fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = TypeSize.s17, color = Color.White,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.s6),
                )
                if (panelCollapsed) PanelStrip(onExpandPanel, Modifier.fillMaxWidth().navigationBarsPadding().padding(start = Space.lg, end = Space.lg, bottom = Space.lg))
                else panel(Modifier.weight(panelShare).fillMaxWidth().navigationBarsPadding().padding(start = Space.lg, end = Space.lg, bottom = Space.lg).popIn(rememberPopIn(0)))
            }
        } else Box(
            modifier = Modifier.fillMaxSize().padding(bottom = if (swapped && cameraOn) Space.none else Space.s90),
            contentAlignment = Alignment.Center,
        ) { if (swapped && cameraOn) cameraView() else avatarView() }

        if (cameraOn) {
            val pipWidth = Space.s130
            val pipShape = RoundedCornerShape(Radius.r20)

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = if (swapped) Space.s44 else Space.s13, end = Space.s13)
                    .offset { IntOffset(pipOffsetX.roundToInt(), pipOffsetY.roundToInt()) }
                    .size(pipWidth, pipWidth * (16f / 9f))
                    .clip(pipShape)
                    .border(
                        BorderStroke(
                            if (signing) Space.s3 else Space.xxs,
                            if (signing) StageTones.signing else StageTones.veilSoft,
                        ),
                        pipShape,
                    )
                    .shadow(Space.sm, pipShape)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            pipOffsetX += dragAmount.x
                            pipOffsetY += dragAmount.y
                        }
                    }
                    .clickable(onClickLabel = stringResource(R.string.stage_swap_views)) { swapped = !swapped },
            ) {
                if (swapped && panel == null) avatarView() else cameraView()
            }
        }

        // The call's own chrome, like a call app: who is on the line and the two ways forward, up on a tap and gone
        // when idle. Hold and messages share one row, so neither sits behind the other.
        if (controls) Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = Space.s6, start = Space.md, end = if (cameraOn) Space.s148 else Space.md)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            CallHeader(
                avatarName = stringResource(R.string.call_avatar_name),
                personName = stringResource(R.string.call_person_name),
                signing = signing,
                modifier = Modifier.fillMaxWidth(),
            )
            CallLevelRow(
                onHold = { answer("stop") },
                onMessages = onMessages,
                holdSelected = ui.answeredCard == "stop",
                holdLabel = stringResource(R.string.call_hold),
                messagesLabel = stringResource(R.string.call_messages),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // The open panel has its own Stop row; folded or absent, Stop waits in the corner, below the chrome.
        if (panel == null || panelCollapsed) StopOctagon(content.subjectText("stop", lang) ?: stringResource(R.string.stage_stop), ui.answeredCard == "stop", { answer("stop") },
            Modifier.align(Alignment.TopStart).padding(top = Space.s156, start = Space.md).testTag(StagePaths.EXIT))

        // Pinned for every state: content, not a control, so it never fades and an open panel never buries it.
        NeedRow(
            content = content,
            lang = lang,
            selected = ui.answeredCard,
            onNeed = { id ->
                onNeed(id)
                react(
                    when (id) {
                        "water" -> R.string.call_r_water
                        "food" -> R.string.call_r_food
                        else -> R.string.call_r_point
                    },
                    if (id == "water" || id == "food") null else needNames[id],
                    feel = null, // The row already ticked for this tap.
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = Space.md, end = Space.md, bottom = Space.sm),
        )
        if (panel != null) return@Box

        // A pull up on the handle opens the whole toolset without leaving the call. It sits between the need row and
        // the answer group and never fades with the chrome: it is the affordance that makes the call read as a call.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = NEED_ROW_HEIGHT + Space.sm)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (slideUp > 0.25f) sheetFor = lastTool ?: StageTool.TYPING
                            slideUp = 0f
                        },
                        onVerticalDrag = { change, dy ->
                            change.consume()
                            slideUp = (slideUp - dy / Space.s240.toPx()).coerceIn(0f, 1f)
                        },
                    )
                },
        ) {
            SlidePanelHandle(
                onOpen = { sheetFor = lastTool ?: StageTool.TYPING },
                dragFraction = slideUp,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = Space.s56 + NEED_ROW_HEIGHT, start = Space.lg, end = Space.lg)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            // One dot per sign seen, never the reader's words: those are proposals the subject has not confirmed.
            SeenSigns(ui.reading.orEmpty(), Modifier)
            SignSlowerCue(ui.reading?.lastOrNull()?.takeIf { it.slower }, content, lang, Modifier)

            AnimatedVisibility(visible = recentReaction != null) {
                Surface(
                    shape = RoundedCornerShape(Radius.r20),
                    color = LocalCall.current.surface.copy(alpha = 0.93f),
                    border = BorderStroke(Space.hair, LocalCall.current.accent),
                ) {
                    Text(
                        text = when (val reaction = recentReaction) {
                            null -> ""
                            is Reaction.Text -> if (reaction.arg == null) stringResource(reaction.id) else stringResource(reaction.id, reaction.arg)
                            is Reaction.Count -> pluralStringResource(reaction.id, reaction.n, reaction.n)
                        },
                        fontFamily = Baloo,
                        fontWeight = FontWeight.Bold,
                        fontSize = TypeSize.s13,
                        color = LocalCall.current.accent,
                        modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.s6),
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(Radius.r24),
                color = LocalCall.current.bg.copy(alpha = 0.87f),
                border = BorderStroke(Space.s1_5, StageTones.hairline),
                shadowElevation = Space.s10,
                modifier = Modifier.fillMaxWidth().testTag("call.caption"),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = Space.s18, vertical = Space.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CallCaption(promptText, captionLight?.inText(promptText), onWordTap)
                    // Preset chips for the related words on this call; content, so they stay after the controls fade.
                    if (related.isNotEmpty()) RelatedChipRow(related, content, lang, { answer(it) }, Modifier.padding(top = Space.sm))
                    rightsNotice?.let {
                        Text(it, fontSize = TypeSize.s13, color = LocalCall.current.accentSoft, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Space.xs))
                    }
                    // A young child is not made to read instructions; the character and the guide show them.
                    if (!ui.child && !statement && caption != null) (idleLine ?: hint)?.let {
                        // Lit and larger while the character acts it out, and the reassurance takes its place when idle.
                        val lit = hintLit || idleLine != null
                        Text(litWord(it, idleHighlight?.inText(it).takeIf { lit }), fontSize = if (lit) TypeSize.s15 else TypeSize.s11, color = if (lit) Color.White else LocalCall.current.muted, fontWeight = if (lit) FontWeight.Bold else null, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Space.xxs))
                    }
                }
            }

            // Path one, answers: one primary action and the needs; the unsure trio waits behind More.
            val answers = LocalAnswerColors.current
            if (signing || controls) Column(Modifier.fillMaxWidth().testTag(StagePaths.ANSWERS), verticalArrangement = Arrangement.spacedBy(Space.s6)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s6), verticalAlignment = Alignment.CenterVertically) {
                    when {
                        signing -> StagePrimaryAction(stringResource(R.string.stage_primary_done), onDoneSigning, Modifier.weight(1.6f))
                        onNext != null -> StagePrimaryAction(stringResource(R.string.stage_next), onNext, Modifier.weight(1.6f))
                        !statement -> StagePrimaryAction(content.subjectText("yes", lang) ?: stringResource(R.string.stage_primary_yes), { answer("yes") }, Modifier.weight(1.6f))
                    }
                    if (!signing) {
                        if (!statement && onNext == null) SmallAnswer("no", null, cardLabel(content, "no", lang), StageTones.answerNo, Color.White, false, { answer("no") }, Modifier.weight(1f), height = Space.s52, icon = rememberVectorPainter(Icons.Filled.Close))
                        if (rightsNotice != null) SmallAnswer(DONT_UNDERSTAND, content.subjectText(DONT_UNDERSTAND, lang), stringResource(R.string.stage_dont_understand), answers.unsure, answers.onUnsure, ui.answeredCard == DONT_UNDERSTAND, { answer(DONT_UNDERSTAND) }, Modifier.weight(1.6f), height = Space.s52)
                    }
                    if (controls) {
                        SmallAnswer("water", null, cardLabel(content, "water", lang), StageTones.answerWater, Color.White, false, { answer("water") }, Modifier.weight(1f), height = Space.s52)
                        SmallAnswer("food", null, cardLabel(content, "food", lang), StageTones.answerFood, Color.White, false, { answer("food") }, Modifier.weight(1f), height = Space.s52)
                        SmallAnswer("express", null, cardLabel(content, "help", lang), StageTones.answerExpress, Color.White, false, { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onOpenExpressBoard() }, Modifier.weight(1f), height = Space.s52, icon = rememberVectorPainter(Icons.Filled.Favorite))
                        if (!signing && pending != null && !statement) StageMoreAnswers(moreAnswers, { moreAnswers = !moreAnswers }, Modifier.weight(1f))
                    }
                }
                // The trio keeps its old calls, haptics and selected states; only its visibility now waits on More.
                if (!signing && pending != null && !statement && moreAnswers) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s6)) {
                    SmallAnswer("dont_know", null, content.subjectText("dont_know", lang) ?: stringResource(R.string.stage_dont_know), answers.unsure, answers.onUnsure, ui.answeredCard == "dont_know", { answer("dont_know") }, Modifier.weight(1f), height = Space.s64, caption = content.subjectText("dont_know", lang) ?: stringResource(R.string.stage_dont_know))
                    SmallAnswer(DONT_UNDERSTAND, null, content.subjectText(DONT_UNDERSTAND, lang) ?: stringResource(R.string.stage_dont_understand), answers.unsure, answers.onUnsure, ui.answeredCard == DONT_UNDERSTAND, { answer(DONT_UNDERSTAND) }, Modifier.weight(1f), height = Space.s64, caption = content.subjectText(DONT_UNDERSTAND, lang) ?: stringResource(R.string.stage_dont_understand))
                    SmallAnswer("wrong", null, content.subjectText("wrong", lang) ?: stringResource(R.string.stage_wrong), answers.unsure, answers.onUnsure, ui.answeredCard == "wrong", { answer("wrong") }, Modifier.weight(1f), height = Space.s64, caption = content.subjectText("wrong", lang) ?: stringResource(R.string.stage_wrong))
                }
            }

            // Path two, tools: language and emoji always; drawing, typing and pictures only when this item offers them.
            if (controls && !signing) {
                // The fading action bar: its own node, so a test can watch it leave while the caption stays.
                Box(Modifier.fillMaxWidth().testTag("call.controls")) {
                    val available = buildSet {
                        if (onLanguage != null) add(StageTool.LANGUAGE)
                        if (pictureCards.isNotEmpty()) add(StageTool.PICTURES)
                        if (onDraw != null) add(StageTool.DRAWING)
                        add(StageTool.EMOJI)
                        if (onWrite != null) add(StageTool.TYPING)
                    }
                    Surface(
                        shape = RoundedCornerShape(Radius.r32),
                        color = LocalCall.current.surface.copy(alpha = 0.93f),
                        border = BorderStroke(Space.hair, LocalCall.current.line),
                        shadowElevation = Space.md,
                        modifier = Modifier.fillMaxWidth().testTag(StagePaths.TOOLS),
                    ) {
                        StageToolBar(
                            selected = sheetFor,
                            available = available,
                            onSelect = { tool ->
                                lastTool = tool
                                when (tool) {
                                    // The sheet shows the whole toolset; its group opens first for language, pictures and emoji.
                                    StageTool.LANGUAGE, StageTool.PICTURES, StageTool.EMOJI -> sheetFor = tool
                                    StageTool.DRAWING -> onDraw?.invoke()
                                    StageTool.TYPING -> onWrite?.invoke()
                                }
                            },
                            modifier = Modifier.padding(horizontal = Space.sm, vertical = Space.s6),
                        )
                    }
                }
            }
        }

        if (sheetFor != null) {
            StageToolSheet(
                open = true,
                initial = sheetFor,
                content = content,
                lang = lang,
                pictureCards = pictureCards,
                onLanguage = { code -> onLanguage?.invoke(code) },
                onCard = { card -> answer(card) },
                onDraw = onDraw,
                onWrite = onWrite,
                onExpress = {
                    sheetFor = null
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOpenExpressBoard()
                },
                onDismiss = { sheetFor = null },
            )
        }
    }
}

private const val CONTROLS_MS = 5_000L

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


private val FINGERS = Regex("n\\d+")

private fun cardLabel(content: ContentBundle, card: String, lang: String): String =
    content.cards[card]?.label?.or(lang) ?: card.removePrefix("n").takeIf { card.matches(FINGERS) } ?: card

private const val HANDS_HOLD_MS = 800L
private const val REOPEN_MS = 10_000L

private const val LANGUAGE_PROMPT = "भाषा चुनें · ভাষা বাছুন · மொழி · Choose your language"
