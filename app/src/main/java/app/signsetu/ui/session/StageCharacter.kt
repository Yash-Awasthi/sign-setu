package app.signsetu.ui.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.onClick
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
import app.signsetu.ui.theme.Space
import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberUpdatedState
import app.signsetu.data.settings.SwitchAccess
import app.signsetu.ui.theme.TypeSize
import app.signsetu.ui.theme.Radius
import app.signsetu.ui.theme.StageTones

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
internal val SideBand = Space.s88

/** The word being fingerspelled, its current letter large, so a reader can follow the hands. */
@Composable
internal fun LetterTile(letters: List<String>, current: Int, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(Radius.r18), color = Color.White, shadowElevation = Space.xs, modifier = modifier.rotate(-4f).size(SideBand, Space.s108)) {
        Column(Modifier.padding(horizontal = Space.sm, vertical = Space.s6), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                letters.getOrNull(current)?.uppercase() ?: "",
                fontFamily = Baloo,
                fontWeight = FontWeight.Bold,
                fontSize = TypeSize.s44,
                lineHeight = TypeSize.s48,
                color = Ink,
            )
            Text(
                buildAnnotatedString {
                    letters.forEachIndexed { i, unit ->
                        withStyle(SpanStyle(color = if (i == current) Ink else StageTones.dimWord, fontWeight = if (i == current) FontWeight.Bold else FontWeight.Normal)) { append(unit.uppercase()) }
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = TypeSize.s1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Tells the person at the stage that auto mode is about to show the next item, so it never arrives unannounced. */
@Composable
internal fun AutoNextChip(seconds: Int, modifier: Modifier) {
    Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.55f), modifier = modifier.padding(top = Space.s13)) {
        Text(stringResource(R.string.auto_next_in, seconds), color = Color.White, fontSize = TypeSize.s12, modifier = Modifier.padding(horizontal = Space.md, vertical = Space.s5))
    }
}

/** The officer's way back to the phone: one tap on the chat button, no hold. The owner asked for it ("no timed hold
 * when going to msgs"): a person holding the phone all day should not have to wait out a ring to reach the officer. */
@Composable
internal fun OfficerHandleLayer(onClick: () -> Unit, modifier: Modifier) {
    OfficerHandle(onClick, modifier)
}

@Composable
private fun OfficerHandle(onClick: () -> Unit, modifier: Modifier) {
    val stage = LocalStageColors.current
    val label = stringResource(R.string.show_officer)
    Box(
        modifier
            .padding(Space.s6)
            .size(Space.s48)
            .clip(CircleShape)
            .clickable(onClickLabel = label, onClick = onClick)
            .semantics { role = Role.Button; contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = CircleShape, color = stage.handle, modifier = Modifier.size(Space.s34)) {
            Box(contentAlignment = Alignment.Center) { Text("💬", fontSize = TypeSize.s16) }
        }
    }
}

@Composable
internal fun ReplayControls(pace: Float, onReplay: () -> Unit, onPace: () -> Unit, modifier: Modifier) {
    val stage = LocalStageColors.current
    Surface(
        shape = RoundedCornerShape(Radius.r28),
        color = stage.tool.copy(alpha = 0.9f),
        contentColor = stage.onTool,
        border = BorderStroke(Space.hair, stage.onTool.copy(alpha = 0.14f)),
        modifier = modifier.width(Space.s56),
    ) {
        Column(Modifier.padding(vertical = Space.xs), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(Space.s48).clip(CircleShape).clickable(role = Role.Button, onClick = onReplay), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.show_replay), modifier = Modifier.size(Space.s26))
            }
            val shown = when { pace < 0.6f -> "½×"; pace < 0.9f -> "¾×"; else -> "1×" }
            val paceName = stringResource(R.string.show_pace, shown)
            Box(Modifier.size(Space.s48).clip(CircleShape).clickable(role = Role.Button, onClick = onPace).semantics { contentDescription = paceName }, contentAlignment = Alignment.Center) {
                Text(
                    shown,
                    fontFamily = Baloo,
                    fontWeight = FontWeight.Bold,
                    fontSize = TypeSize.s16,
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
    /** Off, dwell or step: the same tiles are one Tab or one switch away. */
    switchAccess: SwitchAccess = SwitchAccess.OFF,
    onEscape: () -> Unit = {},
) {
    val flow = rememberAnswerFlow(
        ids = choices,
        columns = if (numbers) 5 else 3,
        switch = switchAccess,
        onKeyboard = {},
        onLeave = onEscape,
    )
    key(choices) {
        // Tiles sit right under the question; the spare room goes below them, not in a gap above.
        Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.sm, Alignment.Top)) {
            if (numbers) {
                choices.chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { id ->
                            val pick = { onCard(id) }
                            NumberButton(id.removePrefix("n"), chosenCard == id, pick, Modifier.weight(1f).popIn(rememberPopIn(40 * choices.indexOf(id))).answerTarget(flow, id, pick), focused = flow.focused == id)
                        }
                        repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            } else {
                choices.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { id ->
                            val pick = { onCard(id) }
                            ChoiceTile(id, content.cards[id]?.label?.get(lang), chosenCard == id || id in picked, pick, Modifier.weight(1f).popIn(rememberPopIn(70 * choices.indexOf(id))).answerTarget(flow, id, pick), name = content.cards[id]?.label?.get("en"), focused = flow.focused == id)
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
    translationY = (1f - p) * Space.xl.toPx()
}
