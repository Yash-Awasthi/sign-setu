package app.signsetu.ui.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.pick
import app.signsetu.sign.SignChoice
import app.signsetu.sign.SignSegment
import app.signsetu.ui.theme.Spacing

private val Ink = Color(0xFF2B2118)

/**
 * The subject says what their sign meant: two or three pictures, in their language, and "none of these". Nothing is
 * pre-selected and nothing counts until they tap (FR-66).
 */
@Composable
internal fun SignChoicePanel(choices: List<SignChoice>, content: ContentBundle, lang: String, onChoose: (SignChoice?) -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
            choices.forEach { c ->
                if (c.card != null) {
                    ChoiceTile(c.card, content.cards[c.card]?.label?.pick(lang), false, { onChoose(c) }, Modifier.weight(1f), name = content.cards[c.card]?.label?.pick("en"))
                } else {
                    PressableSurface(onClick = { onChoose(c) }, shape = RoundedCornerShape(20.dp), color = Color.White, modifier = Modifier.weight(1f).heightIn(min = 96.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text(c.text, style = MaterialTheme.typography.titleLarge, color = Ink, textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp)) }
                    }
                }
            }
        }
        val none = content.subjectText("none_of_these", lang)
        val noneName = stringResource(R.string.stage_none)
        PressableSurface(
            onClick = { onChoose(null) },
            shape = RoundedCornerShape(50),
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.fillMaxWidth().height(64.dp).semantics { contentDescription = none ?: noneName },
        ) {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = Ink)
                none?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = Ink, modifier = Modifier.padding(start = Spacing.sm)) }
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

/**
 * While signing, one quiet dot per sign seen: the subject knows they are being followed, without being shown the
 * reader's guesses, which are only proposals.
 */
@Composable
internal fun SeenSigns(segments: List<SignSegment>, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        segments.takeLast(12).forEach { Box(Modifier.size(14.dp).background(Color.White.copy(alpha = 0.85f), CircleShape)) }
    }
}

/** Shown only after a failed read of fast or lost hands, never up front: a picture and a gentle caption. */
@Composable
internal fun SignSlowerCue(visible: Boolean, content: ContentBundle, lang: String, modifier: Modifier) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.92f)) {
            Row(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_slow_hand), contentDescription = null, tint = Ink, modifier = Modifier.size(36.dp))
                content.subjectText("sign_slower", lang)?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.padding(start = Spacing.sm)) }
            }
        }
    }
}
