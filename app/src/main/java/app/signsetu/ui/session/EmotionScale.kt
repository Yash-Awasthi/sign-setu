package app.signsetu.ui.session

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.or
import java.util.Locale
import app.signsetu.ui.theme.Space
import app.signsetu.ui.theme.Radius
import app.signsetu.ui.theme.StageTones
import app.signsetu.ui.theme.StageScheme

/** The face the person points at; its id is a content card, so every language already has the word. */
enum class Mood(val card: String) { OKAY("okay"), SAD("sad"), SCARED("scared"), ANGRY("angry"), PAIN("pain") }

/** Highest pain the scale records. */
const val PAIN_MAX = 10

/** A face and a pain number, carried in the answer's cue as "feel:<card>:<pain>". */
data class Feeling(val mood: Mood, val pain: Int) {
    val cue: String get() = "feel:%s:%d".format(Locale.ROOT, mood.card, pain.coerceIn(0, PAIN_MAX))

    companion object {
        fun parse(cue: String?): Feeling? {
            val f = cue?.removePrefix("cue:")?.split(':') ?: return null
            if (f.size != 3 || f[0] != "feel") return null
            val mood = Mood.entries.firstOrNull { it.card == f[1] } ?: return null
            val pain = f[2].toIntOrNull() ?: return null
            return Feeling(mood, pain.coerceIn(0, PAIN_MAX))
        }
    }
}

/** The faces and the 0-10 scale, big enough to point at on the stage. A face is chosen first; the numbers appear
 * under it, so a number is never recorded without the face it belongs to. Every tap is sent at once. */
@Composable
internal fun FeelingPanel(content: ContentBundle, lang: String, onFeeling: (Feeling) -> Unit, modifier: Modifier = Modifier) {
    var mood by remember { mutableStateOf<Mood?>(null) }
    var pain by remember { mutableIntStateOf(0) }
    val ink = StageScheme.onSurfaceVariant
    val chosenFill = StageTones.chosenFill
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.s6)) {
        Text(stringResource(R.string.feel_title), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Mood.entries.forEach { m ->
                val label = content.cards[m.card]?.label?.or(lang) ?: m.card
                val chosen = m == mood
                Box(
                    Modifier.size(Space.s56).clip(CircleShape)
                        .background(if (chosen) chosenFill else Color.Transparent)
                        .clickable(role = Role.RadioButton) { mood = m; onFeeling(Feeling(m, pain)) }
                        .semantics { selected = chosen; contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) { Canvas(Modifier.size(Space.s44)) { drawFace(m, ink) } }
            }
        }
        mood?.let { m ->
            Text(stringResource(R.string.feel_pain, pain), style = MaterialTheme.typography.labelLarge)
            for (row in listOf(0..5, 6..PAIN_MAX)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    for (n in row) {
                        val chosen = n == pain
                        val painName = stringResource(R.string.feel_pain, n)
                        Surface(
                            shape = RoundedCornerShape(Radius.r12),
                            color = if (chosen) chosenFill else StageScheme.surfaceContainer,
                            border = BorderStroke(if (chosen) Space.s3 else Space.hair, ink.copy(alpha = if (chosen) 1f else 0.25f)),
                            modifier = Modifier.size(Space.s48)
                                .clickable { pain = n; onFeeling(Feeling(m, n)) }
                                .semantics { selected = chosen; contentDescription = painName },
                        ) {
                            Box(contentAlignment = Alignment.Center) { Text("$n", style = MaterialTheme.typography.titleMedium, color = ink) }
                        }
                    }
                }
            }
        }
    }
}

/** The face alone, drawn so it reads at a glance and in any language: brows and mouth carry the feeling. */
internal fun DrawScope.drawFace(mood: Mood, line: Color) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = Space.s2_5.toPx(), cap = StrokeCap.Round)
    drawCircle(StageTones.bodySkin, w * 0.46f, Offset(w / 2, h / 2))
    drawCircle(line, w * 0.46f, Offset(w / 2, h / 2), style = stroke)
    drawCircle(line, w * 0.045f, Offset(w * 0.38f, h * 0.40f))
    drawCircle(line, w * 0.045f, Offset(w * 0.62f, h * 0.40f))
    when (mood) {
        // A small smile.
        Mood.OKAY -> drawArc(line, 20f, 140f, false, Offset(w * 0.32f, h * 0.50f), Size(w * 0.36f, h * 0.22f), style = stroke)
        // A frown, turned the other way.
        Mood.SAD -> drawArc(line, 200f, 140f, false, Offset(w * 0.32f, h * 0.64f), Size(w * 0.36f, h * 0.22f), style = stroke)
        // An open mouth and raised brows.
        Mood.SCARED -> {
            drawCircle(line, w * 0.10f, Offset(w * 0.50f, h * 0.66f), style = stroke)
            drawLine(line, Offset(w * 0.28f, h * 0.26f), Offset(w * 0.44f, h * 0.20f), Space.xxs.toPx())
            drawLine(line, Offset(w * 0.72f, h * 0.26f), Offset(w * 0.56f, h * 0.20f), Space.xxs.toPx())
        }
        // Brows driven down over a flat mouth.
        Mood.ANGRY -> {
            drawLine(line, Offset(w * 0.26f, h * 0.30f), Offset(w * 0.46f, h * 0.38f), Space.s2_5.toPx())
            drawLine(line, Offset(w * 0.74f, h * 0.30f), Offset(w * 0.54f, h * 0.38f), Space.s2_5.toPx())
            drawLine(line, Offset(w * 0.36f, h * 0.68f), Offset(w * 0.64f, h * 0.68f), Space.s2_5.toPx())
        }
        // A grimace and a tear.
        Mood.PAIN -> {
            drawLine(line, Offset(w * 0.36f, h * 0.66f), Offset(w * 0.64f, h * 0.66f), Space.s2_5.toPx())
            drawCircle(line, w * 0.05f, Offset(w * 0.36f, h * 0.52f))
            drawLine(line, Offset(w * 0.36f, h * 0.44f), Offset(w * 0.36f, h * 0.40f), Space.xxs.toPx())
        }
    }
}
