package app.signsetu.ui.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import app.signsetu.ui.theme.LocalMotion
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.semantics.role
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import app.signsetu.ui.theme.Baloo
import app.signsetu.ui.theme.LocalStageColors
import app.signsetu.ui.theme.Spacing
import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberUpdatedState

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

/** Each side of the character: the replay pill on the left, a letter tile on the right. */
internal val SideBand = 88.dp

/** The word being fingerspelled, its current letter large, so a reader can follow the hands. */
@Composable
internal fun LetterTile(letters: List<String>, current: Int, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 4.dp, modifier = modifier.rotate(-4f).size(SideBand, 108.dp)) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                letters.getOrNull(current)?.uppercase() ?: "",
                fontFamily = Baloo,
                fontWeight = FontWeight.Bold,
                fontSize = 44.sp,
                lineHeight = 48.sp,
                color = Ink,
            )
            Text(
                buildAnnotatedString {
                    letters.forEachIndexed { i, unit ->
                        withStyle(SpanStyle(color = if (i == current) Ink else Color(0xFFB8A99A), fontWeight = if (i == current) FontWeight.Bold else FontWeight.Normal)) { append(unit.uppercase()) }
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal const val HANDLE_HOLD_MS = 333L

/** Tells the person at the stage that auto mode is about to show the next item, so it never arrives unannounced. */
@Composable
internal fun AutoNextChip(seconds: Int, modifier: Modifier) {
    Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.55f), modifier = modifier.padding(top = 13.dp)) {
        Text(stringResource(R.string.auto_next_in, seconds), color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
    }
}

/** The officer's way back to the phone: hold the chat button and a ring fills over a dimmed screen; when it closes
 * the officer's side opens. Letting go early, or sliding the finger away, starts over. */
@Composable
internal fun OfficerHandleLayer(onClick: () -> Unit, modifier: Modifier) {
    val hold = remember { Animatable(0f) }
    Box(Modifier.fillMaxSize()) {
        if (hold.value > 0f) HoldRing(hold.value)
        OfficerHandle(onClick, hold, modifier)
    }
}

@Composable
private fun HoldRing(progress: Float) {
    val motion = LocalMotion.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color.Black.copy(alpha = 0.6f * if (motion) (progress * 4f).coerceAtMost(1f) else 1f))
            val stroke = 6.dp.toPx()
            val radius = 44.dp.toPx()
            val topLeft = Offset(center.x - radius, center.y - radius)
            val box = Size(radius * 2, radius * 2)
            drawArc(Color.White.copy(alpha = 0.25f), 0f, 360f, false, topLeft, box, style = Stroke(stroke))
            drawArc(Color.White, -90f, 360f * progress, false, topLeft, box, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("💬", fontSize = 30.sp)
    }
}

@Composable
private fun OfficerHandle(onClick: () -> Unit, hold: Animatable<Float, AnimationVector1D>, modifier: Modifier) {
    val stage = LocalStageColors.current
    val label = stringResource(R.string.show_officer)
    val current by androidx.compose.runtime.rememberUpdatedState(onClick)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    Box(
        modifier
            .padding(6.dp)
            .size(48.dp)
            .clip(CircleShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val ring = scope.launch {
                        // Timed on the wall clock, not by an animation spec: the phone's animation-speed setting must not shorten the hold.
                        val started = System.nanoTime()
                        do {
                            withFrameNanos { }
                            hold.snapTo(((System.nanoTime() - started) / 1_000_000f / HANDLE_HOLD_MS).coerceAtMost(1f))
                        } while (hold.value < 1f)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        current()
                        hold.snapTo(0f)
                    }
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.first()
                        val slid = (change.position - down.position).getDistance() > viewConfiguration.touchSlop * 2
                        if (slid || !change.pressed) {
                            if (hold.value < 1f) { ring.cancel(); scope.launch { hold.snapTo(0f) } }
                            break
                        }
                    } while (true)
                }
            }
            .semantics { role = Role.Button; contentDescription = label; onLongClick(label) { current(); true } },
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = CircleShape, color = stage.handle, modifier = Modifier.size(34.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("💬", fontSize = 16.sp) }
        }
    }
}

@Composable
internal fun ReplayControls(pace: Float, onReplay: () -> Unit, onPace: () -> Unit, modifier: Modifier) {
    val stage = LocalStageColors.current
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = stage.tool.copy(alpha = 0.9f),
        contentColor = stage.onTool,
        border = BorderStroke(1.dp, stage.onTool.copy(alpha = 0.14f)),
        modifier = modifier.width(56.dp),
    ) {
        Column(Modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onReplay), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.show_replay), modifier = Modifier.size(26.dp))
            }
            val shown = when { pace < 0.6f -> "½×"; pace < 0.9f -> "¾×"; else -> "1×" }
            val paceName = stringResource(R.string.show_pace, shown)
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onPace).semantics { contentDescription = paceName }, contentAlignment = Alignment.Center) {
                Text(
                    shown,
                    fontFamily = Baloo,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }
    }
}

/** The pictures or numbers the question offers, as large tiles. */
@Composable
internal fun ChoiceGrid(
    choices: List<String>,
    numbers: Boolean,
    chosenCard: String?,
    content: ContentBundle,
    lang: String,
    onCard: (String) -> Unit,
    modifier: Modifier,
    picked: Set<String> = emptySet(),
) {
    key(choices) {
        // Tiles sit right under the question; the spare room goes below them, not in a gap above.
        Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.Top)) {
            if (numbers) {
                choices.chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { id ->
                            NumberButton(id.removePrefix("n"), chosenCard == id, { onCard(id) }, Modifier.weight(1f).popIn(rememberPopIn(40 * choices.indexOf(id))))
                        }
                        repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            } else {
                choices.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { id ->
                            ChoiceTile(id, content.cards[id]?.label?.get(lang), chosenCard == id || id in picked, { onCard(id) }, Modifier.weight(1f).popIn(rememberPopIn(70 * choices.indexOf(id))), name = content.cards[id]?.label?.get("en"))
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** Entrance for things put in front of the subject: a short rise and settle, so a new picture is noticed. */
@Composable
internal fun rememberPopIn(delayMillis: Int): Animatable<Float, AnimationVector1D> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        progress.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 380f))
    }
    return progress
}

internal fun Modifier.popIn(progress: Animatable<Float, AnimationVector1D>) = graphicsLayer {
    val p = progress.value
    alpha = p.coerceIn(0f, 1f)
    scaleX = 0.7f + 0.3f * p
    scaleY = 0.7f + 0.3f * p
    translationY = (1f - p) * 24.dp.toPx()
}
