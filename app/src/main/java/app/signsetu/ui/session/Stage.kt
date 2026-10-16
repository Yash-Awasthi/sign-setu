package app.signsetu.ui.session

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.signsetu.R
import app.signsetu.camera.CameraMirror
import app.signsetu.camera.HandTracker
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.pick
import app.signsetu.data.session.Profile
import app.signsetu.ui.avatar.Avatar
import app.signsetu.ui.avatar.Character
import app.signsetu.ui.components.CardImage
import app.signsetu.ui.components.rememberSpeechPlayer
import app.signsetu.ui.theme.Baloo
import app.signsetu.ui.theme.LocalAnswerColors
import app.signsetu.ui.theme.LocalStageColors
import app.signsetu.ui.theme.Spacing
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.delay

/** Profiles whose subjects hear the questions without the officer pressing play (03-ux §3). */
private val AutoSpeech = setOf(Profile.YOUNG_CHILD, Profile.OTHER_LANGUAGE)

fun characterFor(profile: Profile) = if (profile == Profile.ISL_USER) Character.ADULT else Character.CHILD

/**
 * The stage: what the subject sees while the phone faces them. The character on top, a live mirror of their hands
 * below, and the answers within reach of a thumb. Nothing here shows notes, the log, or unconfirmed input.
 */
@Composable
fun Stage(
    ui: SessionUi,
    content: ContentBundle,
    tracker: HandTracker?,
    cameraOn: Boolean,
    largeCaptions: Boolean,
    gestureSpeed: Float,
    onCard: (String) -> Unit,
    onYesNo: (Boolean) -> Unit,
    onLanguage: (String) -> Unit,
    onDrawing: (ByteArray) -> Unit,
    onOfficer: () -> Unit,
) {
    val stage = LocalStageColors.current
    val lang = ui.session.subjectLang
    val profile = ui.session.profile
    val pending = ui.pending
    var drawing by remember(pending) { mutableStateOf(pending == Pending.Drawing) }
    var replays by remember(pending) { mutableIntStateOf(0) }
    var slow by remember(pending) { mutableStateOf(false) }
    var reacting by remember { mutableStateOf(false) }
    val answered = ui.answeredCard != null || ui.answeredYes != null
    LaunchedEffect(ui.answeredCard, ui.answeredYes) { reacting = answered }

    val speech = rememberSpeechPlayer()
    val audio = speechFor(pending, content, lang)
    // Speech plays by itself only for subjects who hear and may not read; ISL users never get sound (NFR-24).
    LaunchedEffect(pending, lang) { if (audio != null && profile in AutoSpeech) speech.play(audio) }
    LaunchedEffect(pending) { withFrameNanos { }; ShowTiming.firstFrame() }

    val item = describeItem(pending, content, lang)
    val gestureId = when {
        reacting -> "okay"
        drawing -> "school.write"
        pending is Pending.Ask -> pending.question.gesture ?: pending.question.lexicon
        pending is Pending.ShowBack -> pending.entry.id
        pending is Pending.FreeText || pending is Pending.Cards -> "ask"
        pending is Pending.PhraseAsk -> null
        pending == Pending.LanguageFinder -> "wave"
        else -> "namaste"
    }
    val gesture = if (pending is Pending.PhraseAsk && gestureId == null) content.gestureFor(pending.phrase, pending.slot) else gestureId?.let { content.gestures[it] }
    val caption = when {
        reacting -> content.subjectText("okay", lang)
        drawing -> content.subjectText("draw", lang)
        pending == null -> content.language(lang)?.hello
        // Greeting back in the language they picked tells them the tap was understood.
        pending == Pending.LanguageFinder -> ui.chosenLanguage?.let { content.language(it)?.hello }
        else -> item.caption
    }

    Surface(color = stage.skyBottom, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = if (ui.rotated) 180f else 0f },
        ) {
            val topWeight = when {
                drawing -> 0.36f
                pending == Pending.LanguageFinder -> 0.36f
                item.choices.isNotEmpty() -> 0.44f
                else -> 0.52f
            }
            val topAnimated by animateFloatAsState(topWeight, spring(stiffness = 300f), label = "split")
            Box(
                Modifier
                    .weight(topAnimated)
                    .fillMaxWidth(),
            ) {
                Backdrop(Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    Avatar(
                        character = characterFor(profile),
                        gesture = gesture,
                        playKey = if (reacting) "react:${ui.answeredCard}:${ui.answeredYes}" else Triple(pending, replays, ui.chosenLanguage),
                        speed = if (slow) 0.5f else gestureSpeed,
                        glance = pending != null && !drawing && !reacting,
                        onFinished = { reacting = false },
                        modifier = Modifier
                            .fillMaxHeight(0.76f)
                            .aspectRatio(1f)
                            .align(Alignment.BottomCenter),
                    )
                    AnimatedContent(
                        targetState = caption,
                        transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.92f)) togetherWith fadeOut() },
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp, start = 52.dp, end = 52.dp),
                        label = "caption",
                    ) { text -> if (text != null) Bubble(text, large = largeCaptions) }
                    item.held?.let { id ->
                        key(id) {
                            HeldCard(
                                id,
                                content.cards[id]?.label?.pick(lang),
                                Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 20.dp).popIn(rememberPopIn(150)),
                            )
                        }
                    }
                    OfficerHandle(onOfficer, Modifier.align(Alignment.TopEnd))
                    val replayable = gesture != null || (audio != null && profile != Profile.ISL_USER)
                    if (pending != null && !drawing && replayable) {
                        ReplayControls(
                            onReplay = { slow = false; replays++; audio?.let { speech.play(it) } },
                            onSlow = { slow = true; replays++; audio?.let { speech.play(it, 0.75f) } },
                            modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 12.dp),
                        )
                    }
                }
            }
            Column(
                Modifier
                    .weight(1f - topAnimated)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg)
                    .padding(top = Spacing.md, bottom = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                when {
                    drawing -> DrawingPanel(content, lang, onDone = onDrawing, modifier = Modifier.weight(1f))
                    pending == Pending.LanguageFinder -> LanguageGrid(content, ui.chosenLanguage, Modifier.weight(1f)) { code ->
                        onLanguage(code)
                        content.greetingAudio(code)?.let { speech.play(it) }
                    }
                    else -> {
                        MirrorWindow(
                            tracker = tracker,
                            cameraOn = cameraOn,
                            choices = item.choices,
                            numbers = item.numbers,
                            canDraw = item.canDraw,
                            chosenCard = ui.answeredCard,
                            content = content,
                            lang = lang,
                            onCard = onCard,
                            onDraw = { drawing = true },
                            modifier = Modifier.weight(1f),
                        )
                        if (pending != null) {
                            AnswerBar(content, lang, ui.answeredYes, ui.answeredCard, onYesNo, onCard)
                        }
                    }
                }
            }
        }
    }
}

/** What one shown item puts on the stage. */
private class StageItem(
    val caption: String?,
    val held: String?,
    val choices: List<String>,
    val numbers: Boolean,
    val canDraw: Boolean,
)

private fun describeItem(pending: Pending?, content: ContentBundle, lang: String): StageItem {
    val (caption, cards) = when (pending) {
        is Pending.Ask -> pending.question.text.pick(lang) to pending.question.cards
        is Pending.FreeText -> pending.text to pending.cardIds
        is Pending.Cards -> null to pending.cardIds
        is Pending.ShowBack -> content.subjectText("is_this_it", lang) to emptyList()
        is Pending.PhraseAsk -> pending.slot.text.pick(lang) to listOf(pending.slot.card)
        else -> null to emptyList()
    }
    val pictures = cards.filter { it != "draw" && it in content.cards }
    val numbers = pictures.isNotEmpty() && pictures.all { it.matches(Regex("n\\d+")) }
    val held = when {
        pending is Pending.ShowBack -> pending.entry.card
        pending !is Pending.Cards && pictures.size == 1 -> pictures.first()
        else -> null
    }
    val choices = if (held != null) emptyList() else pictures.take(if (numbers) 10 else 6)
    return StageItem(caption, held, choices, numbers, "draw" in cards)
}

private fun speechFor(pending: Pending?, content: ContentBundle, lang: String): String? = when (pending) {
    is Pending.Ask -> content.questionAudio(pending.question.id, lang)
    is Pending.ShowBack -> content.subjectAudio("is_this_it", lang)
    else -> null
}

@Composable
internal fun Backdrop(modifier: Modifier) {
    val stage = LocalStageColors.current
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(stage.skyTop, stage.skyBottom)))
        // A low sun and a soft floor give the character a place to stand, without anything to look at instead.
        drawCircle(stage.glow, size.minDimension * 0.42f, Offset(size.width * 0.86f, size.height * 0.16f), alpha = 0.55f)
        drawCircle(stage.glow, size.minDimension * 0.18f, Offset(size.width * 0.08f, size.height * 0.42f), alpha = 0.35f)
        drawOval(stage.ground, Offset(-size.width * 0.2f, size.height * 0.82f), Size(size.width * 1.4f, size.height * 0.5f))
    }
}

private val BubbleTail = 12.dp

/** A speech bubble whose tail points down at the character. */
private object BubbleShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val tail = with(density) { BubbleTail.toPx() }
        val r = with(density) { 22.dp.toPx() }
        val path = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height - tail, CornerRadius(r, r)))
            moveTo(size.width / 2f - tail, size.height - tail - 1f)
            lineTo(size.width / 2f + tail * 0.2f, size.height)
            lineTo(size.width / 2f + tail, size.height - tail - 1f)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
internal fun Bubble(text: String, large: Boolean = false) {
    val stage = LocalStageColors.current
    Surface(
        shape = BubbleShape,
        color = stage.bubble,
        contentColor = stage.onBubble,
        shadowElevation = 6.dp,
        modifier = Modifier.widthIn(max = 340.dp),
    ) {
        Text(
            text,
            style = if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            maxLines = if (large) 4 else 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 10.dp + BubbleTail),
        )
    }
}

/** The picture the question is about, held up beside the character. */
@Composable
private fun HeldCard(cardId: String, label: String?, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        shadowElevation = 8.dp,
        modifier = modifier.rotate(-5f).size(width = 108.dp, height = if (label != null) 132.dp else 108.dp),
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CardImage(cardId, Modifier.fillMaxWidth().aspectRatio(1f))
            label?.let {
                Text(it, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color(0xFF2B2118))
            }
        }
    }
}

@Composable
private fun OfficerHandle(onClick: () -> Unit, modifier: Modifier) {
    val stage = LocalStageColors.current
    // Small and quiet: the subject should not be drawn to it; the officer knows where it is.
    Box(
        modifier
            .padding(6.dp)
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = CircleShape, color = stage.handle, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.show_officer), tint = stage.onHandle, modifier = Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun ReplayControls(onReplay: () -> Unit, onSlow: () -> Unit, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.85f), shadowElevation = 2.dp, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onReplay), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.show_replay), tint = Color(0xFF2B2118), modifier = Modifier.size(28.dp))
            }
            val slowName = stringResource(R.string.show_slow)
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onSlow).semantics { contentDescription = slowName }, contentAlignment = Alignment.Center) {
                Text(
                    "½×",
                    fontFamily = Baloo,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF2B2118),
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }
    }
}

/** The mirror: the subject sees their own hands; choices sit at the bottom edge of it. */
@Composable
private fun MirrorWindow(
    tracker: HandTracker?,
    cameraOn: Boolean,
    choices: List<String>,
    numbers: Boolean,
    canDraw: Boolean,
    chosenCard: String?,
    content: ContentBundle,
    lang: String,
    onCard: (String) -> Unit,
    onDraw: () -> Unit,
    modifier: Modifier,
) {
    val stage = LocalStageColors.current
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier
            .fillMaxWidth()
            .shadow(6.dp, shape)
            .background(stage.windowFrame, shape)
            .padding(5.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(stage.window),
    ) {
        if (cameraOn) {
            CameraMirror(tracker, Modifier.fillMaxSize()) { MirrorPlaceholder() }
        } else {
            MirrorPlaceholder()
        }
        if (choices.isNotEmpty() || canDraw) key(choices) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                    .padding(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (numbers) {
                    choices.chunked(5).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { id ->
                                val delay = 40 * choices.indexOf(id)
                                NumberButton(id.removePrefix("n"), chosenCard == id, { onCard(id) }, Modifier.weight(1f).popIn(rememberPopIn(delay)))
                            }
                        }
                    }
                } else {
                    choices.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { id ->
                                val delay = 70 * choices.indexOf(id)
                                ChoiceTile(id, content.cards[id]?.label?.pick(lang), chosenCard == id, { onCard(id) }, Modifier.weight(1f).popIn(rememberPopIn(delay)))
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                if (canDraw) DrawButton(content.subjectText("draw", lang), onDraw)
            }
        }
    }
}

/** Entrance for things put in front of the subject: a short rise and settle, so a new picture is noticed. */
@Composable
private fun rememberPopIn(delayMillis: Int): Animatable<Float, AnimationVector1D> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        progress.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 380f))
    }
    return progress
}

private fun Modifier.popIn(progress: Animatable<Float, AnimationVector1D>) = graphicsLayer {
    val p = progress.value
    alpha = p.coerceIn(0f, 1f)
    scaleX = 0.7f + 0.3f * p
    scaleY = 0.7f + 0.3f * p
    translationY = (1f - p) * 24.dp.toPx()
}

@Composable
private fun MirrorPlaceholder() {
    val stage = LocalStageColors.current
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF4A4352), stage.window))), contentAlignment = Alignment.Center) {
        CardImage("stop", Modifier.size(96.dp).graphicsLayer { alpha = 0.55f })
    }
}

@Composable
private fun ChoiceTile(cardId: String, label: String?, chosen: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val answer = LocalAnswerColors.current
    PressableSurface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = if (chosen) BorderStroke(4.dp, answer.selection) else null,
        modifier = modifier.heightIn(min = 96.dp).semantics { label?.let { contentDescription = it } },
    ) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CardImage(cardId, Modifier.size(58.dp))
            label?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = Color(0xFF2B2118), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun NumberButton(digit: String, chosen: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val answer = LocalAnswerColors.current
    PressableSurface(
        onClick = onClick,
        shape = CircleShape,
        color = if (chosen) answer.selection else Color.White,
        modifier = modifier.aspectRatio(1f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(digit, fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = if (chosen) answer.onSelection else Color(0xFF2B2118))
        }
    }
}

@Composable
private fun DrawButton(label: String?, onClick: () -> Unit) {
    PressableSurface(onClick = onClick, shape = RoundedCornerShape(50), color = Color(0xFFFFE7BF), modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Create, contentDescription = null, tint = Color(0xFF3D2700))
            label?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = Color(0xFF3D2700), modifier = Modifier.padding(start = Spacing.sm)) }
        }
    }
}

/**
 * Yes and No always sit in the same place with icon, word and colour (FR-20, NFR-21). "I don't know" and "Stop" are
 * always offered too: a child must never feel pushed to guess, and may ask for a break at any time.
 */
@Composable
private fun AnswerBar(
    content: ContentBundle,
    lang: String,
    chosenYes: Boolean?,
    chosenCard: String?,
    onYesNo: (Boolean) -> Unit,
    onCard: (String) -> Unit,
) {
    val answer = LocalAnswerColors.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            BigAnswer(
                label = content.subjectText("yes", lang), name = stringResource(R.string.show_yes),
                icon = Icons.Filled.Check, color = answer.yes, onColor = answer.onYes,
                chosen = chosenYes == true, onClick = { onYesNo(true) }, modifier = Modifier.weight(1f),
            )
            BigAnswer(
                label = content.subjectText("no", lang), name = stringResource(R.string.show_no),
                icon = Icons.Filled.Close, color = answer.no, onColor = answer.onNo,
                chosen = chosenYes == false, onClick = { onYesNo(false) }, modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SmallAnswer("dont_know", content.subjectText("dont_know", lang), answer.unsure, answer.onUnsure, chosenCard == "dont_know", { onCard("dont_know") }, Modifier.weight(1f))
            SmallAnswer("stop", content.subjectText("stop", lang), answer.pause, answer.onPause, chosenCard == "stop", { onCard("stop") }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BigAnswer(
    label: String?, name: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color, onColor: Color, chosen: Boolean, onClick: () -> Unit, modifier: Modifier,
) {
    PressableSurface(
        onClick = onClick,
        shape = RoundedCornerShape(26.dp),
        color = color,
        border = if (chosen) BorderStroke(4.dp, Color(0xFF2B2118)) else null,
        shadowElevation = if (chosen) 0.dp else 3.dp,
        modifier = modifier.height(76.dp).semantics { contentDescription = name },
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = onColor.copy(alpha = 0.2f), modifier = Modifier.size(44.dp)) {
                Icon(icon, contentDescription = null, tint = onColor, modifier = Modifier.padding(8.dp))
            }
            label?.let { Text(it, style = MaterialTheme.typography.headlineSmall, color = onColor, modifier = Modifier.padding(start = Spacing.md)) }
        }
    }
}

@Composable
private fun SmallAnswer(cardId: String, label: String?, color: Color, onColor: Color, chosen: Boolean, onClick: () -> Unit, modifier: Modifier) {
    PressableSurface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = color,
        border = if (chosen) BorderStroke(3.dp, onColor) else null,
        modifier = modifier.height(52.dp).semantics { label?.let { contentDescription = it } },
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            CardImage(cardId, Modifier.size(30.dp))
            label?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = onColor, modifier = Modifier.padding(start = Spacing.sm)) }
        }
    }
}

/** Buttons squash softly and tick in the hand when pressed, so a child sees and feels the tap land. */
@Composable
private fun PressableSurface(
    onClick: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
    color: Color,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    shadowElevation: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "press")
    // A deaf child cannot hear the tap land; a short tick in the hand confirms it.
    val haptics = LocalHapticFeedback.current
    Surface(
        onClick = { haptics.performHapticFeedback(HapticFeedbackType.Confirm); onClick() },
        shape = shape,
        color = color,
        border = border,
        shadowElevation = shadowElevation,
        interactionSource = interaction,
        modifier = modifier.scale(scale),
        content = content,
    )
}

@Composable
private fun LanguageGrid(content: ContentBundle, chosen: String?, modifier: Modifier, onLanguage: (String) -> Unit) {
    val answer = LocalAnswerColors.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        content.languages.chunked(3).forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                row.forEach { l ->
                    val selected = chosen == l.code
                    PressableSurface(
                        onClick = { onLanguage(l.code) },
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                        border = if (selected) BorderStroke(4.dp, answer.selection) else BorderStroke(1.dp, Color(0x22000000)),
                        shadowElevation = 1.dp,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) {
                        Column(Modifier.padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(l.name, fontFamily = Baloo, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = Color(0xFF2B2118), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(l.english, fontSize = 11.sp, color = Color(0xFF6B5E50), maxLines = 1)
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private class DrawStroke(val points: List<Offset>)

@Composable
private fun DrawingPanel(content: ContentBundle, lang: String, onDone: (ByteArray) -> Unit, modifier: Modifier) {
    val strokes = remember { mutableStateListOf<DrawStroke>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val ink = Color(0xFF2B2118)
    val paper = Color.White
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = paper,
            shadowElevation = 4.dp,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { current = listOf(it) },
                            onDrag = { change, _ -> current = current + change.position },
                            onDragEnd = { strokes.add(DrawStroke(current)); current = emptyList() },
                            onDragCancel = { current = emptyList() },
                        )
                    },
            ) {
                (strokes + DrawStroke(current)).forEach { s ->
                    if (s.points.size < 2) return@forEach
                    val path = Path().apply {
                        moveTo(s.points[0].x, s.points[0].y)
                        s.points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(path, ink, style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            PressableSurface(onClick = { strokes.clear() }, shape = RoundedCornerShape(50), color = Color(0xFFFFE7BF), modifier = Modifier.weight(1f).height(64.dp)) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = Color(0xFF3D2700))
                    content.subjectText("clear", lang)?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = Color(0xFF3D2700), modifier = Modifier.padding(start = Spacing.sm)) }
                }
            }
            val answer = LocalAnswerColors.current
            PressableSurface(
                onClick = { if (strokes.isNotEmpty()) onDone(renderPng(strokes, canvasSize, ink.toArgb(), paper.toArgb())) },
                shape = RoundedCornerShape(50),
                color = if (strokes.isNotEmpty()) answer.yes else Color(0xFFBFD8CB),
                modifier = Modifier.weight(1f).height(64.dp),
            ) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = answer.onYes)
                    content.subjectText("done", lang)?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = answer.onYes, modifier = Modifier.padding(start = Spacing.sm)) }
                }
            }
        }
    }
}

private fun renderPng(strokes: List<DrawStroke>, size: IntSize, ink: Int, paper: Int): ByteArray {
    val w = size.width.coerceAtLeast(1)
    val h = size.height.coerceAtLeast(1)
    val bitmap = createBitmap(w, h)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(paper)
    val density = android.content.res.Resources.getSystem().displayMetrics.density
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 8 * density
        color = ink
    }
    strokes.forEach { s ->
        if (s.points.size < 2) return@forEach
        val path = android.graphics.Path().apply {
            moveTo(s.points[0].x, s.points[0].y)
            s.points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        canvas.drawPath(path, paint)
    }
    return ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        out.toByteArray()
    }
}
