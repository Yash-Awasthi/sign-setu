package app.signsetu.ui.session

import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import app.signsetu.ui.theme.LocalAnswerColors
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.signsetu.R
import app.signsetu.ui.theme.LocalCall
import app.signsetu.ui.components.CardImage
import app.signsetu.ui.theme.Baloo

@Composable
internal fun CallActionButton(icon: String, label: String, backgroundColor: Color, highlight: Boolean = false, onClick: () -> Unit) {
    val pulse = if (highlight) rememberInfiniteTransition(label = "guide").animateFloat(1f, 1.18f, infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse").value else 1f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Surface(
            shape = CircleShape,
            color = backgroundColor,
            border = if (highlight) BorderStroke(3.dp, Color.White) else null,
            modifier = Modifier.size(50.dp).scale(pulse),
            shadowElevation = 4.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                when (icon) {
                    "yes" -> Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                    "no" -> Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                    "express" -> Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    else -> CardImage(icon, Modifier.size(28.dp))
                }
            }
        }
        Text(
            text = label,
            fontFamily = Baloo,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = LocalCall.current.text,
        )
    }
}

/** The folded answer panel: a bar that opens it at once when tapped. */
@Composable
internal fun PanelStrip(onClick: () -> Unit, modifier: Modifier) {
    val label = stringResource(R.string.stage_show_answers)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = LocalCall.current.surface.copy(alpha = 0.93f),
        border = BorderStroke(1.dp, LocalCall.current.line),
        modifier = modifier.height(48.dp).semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) { Text("︿", fontSize = 22.sp, color = LocalCall.current.text) }
    }
}

/** Stop in a corner on every item: a small red octagon, the one way out that never hides. */
@Composable
internal fun StopOctagon(label: String, chosen: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val answers = LocalAnswerColors.current
    PressableSurface(
        onClick = onClick,
        shape = Octagon,
        color = answers.pause,
        border = if (chosen) BorderStroke(3.dp, answers.onPause) else null,
        modifier = modifier.size(56.dp).semantics { role = Role.Button; contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) { CardImage("stop", Modifier.size(30.dp)) }
    }
}

private val Octagon = GenericShape { size, _ ->
    val c = 0.29f
    moveTo(size.width * c, 0f)
    lineTo(size.width * (1 - c), 0f)
    lineTo(size.width, size.height * c)
    lineTo(size.width, size.height * (1 - c))
    lineTo(size.width * (1 - c), size.height)
    lineTo(size.width * c, size.height)
    lineTo(0f, size.height * (1 - c))
    lineTo(0f, size.height * c)
    close()
}

/** The call's caption: the word being signed is lit, and a tap replays the line or that word. */
@Composable
internal fun CallCaption(text: String, highlight: IntRange?, onWordTap: ((Int) -> Unit)?) {
    val styled = litWord(text, highlight)
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val replayName = stringResource(R.string.word_replay)
    // The caller's lambda changes every frame of typed-text playback; the tap detector must not restart with it.
    val onTap by rememberUpdatedState(onWordTap)
    // The action's target is at least 48 dp tall; taps are read inside the text itself, so offsets map to its words.
    val tap = if (onWordTap == null) Modifier else Modifier
        .heightIn(min = 48.dp)
        .semantics { onClick(replayName) { onTap?.invoke(0); true } }
        .wrapContentHeight()
        .pointerInput(Unit) { detectTapGestures { p -> layout?.let { onTap?.invoke(it.getOffsetForPosition(p)) } } }
    Text(
        styled, fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White, textAlign = TextAlign.Center,
        onTextLayout = { layout = it }, modifier = tap,
    )
}

@Composable
internal fun litWord(text: String, highlight: IntRange?) = buildAnnotatedString {
    append(text)
    highlight?.takeIf { it.last < text.length }?.let {
        addStyle(SpanStyle(color = LocalCall.current.bg, background = LocalCall.current.accent), it.first, it.last + 1)
    }
}
