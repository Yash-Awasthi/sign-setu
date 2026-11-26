package app.signsetu.ui.session

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import app.signsetu.ui.theme.LocalAnswerColors
import app.signsetu.ui.theme.LocalCall
import app.signsetu.ui.theme.Space
import app.signsetu.ui.theme.TypeSize
import app.signsetu.ui.theme.Radius

/** The six needs the row always offers, in the order they are drawn; the list lives in AnswerKeys with the walk order. */

/** The space this row takes at the bottom of the call; the stage lifts its own content by it. */
internal val NEED_ROW_HEIGHT = Space.s84

private val NeedGlyphs = mapOf(
    "water" to "\uD83D\uDCA7",
    "food" to "\uD83C\uDF5A",
    "toilet" to "\uD83D\uDEBB",
    "pain" to "\uD83D\uDE23",
    "help" to "\uD83D\uDE4B",
    "family" to "\uD83D\uDC6A",
)

/** The card's own name where the item has that card; otherwise the row's own label, never a bare id. */
@Composable
internal fun needLabel(id: String, content: ContentBundle, lang: String): String =
    content.cards[id]?.label?.get(lang) ?: when (id) {
        "family" -> stringResource(R.string.need_family)
        "toilet" -> stringResource(R.string.need_toilet)
        "help" -> stringResource(R.string.need_help)
        "pain" -> stringResource(R.string.need_pain)
        else -> id
    }

/** One tap for a need, in every state: six equal tiles under a quiet heading, reachable without opening anything. */
@Composable
internal fun NeedRow(
    content: ContentBundle,
    lang: String,
    selected: String?,
    onNeed: (String) -> Unit,
    modifier: Modifier = Modifier,
    flow: AnswerFlow? = null,
) {
    val haptic = LocalHapticFeedback.current
    val call = LocalCall.current
    val answer = LocalAnswerColors.current
    Column(modifier.height(NEED_ROW_HEIGHT), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
        Text(stringResource(R.string.need_row_title), fontSize = TypeSize.s11, color = call.muted, maxLines = 1)
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f).testTag("stage.needs"),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NEED_IDS.forEach { id ->
                val label = needLabel(id, content, lang)
                val chosen = selected == id
                val tap = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    onNeed(id)
                }
                Surface(
                    onClick = tap,
                    shape = RoundedCornerShape(Radius.r14),
                    color = if (chosen) answer.selection else call.surface,
                    border = BorderStroke(if (chosen) Space.xxs else Space.hair, if (chosen) answer.selection else call.line),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(if (flow == null) Modifier else Modifier.answerTarget(flow, id, tap))
                        .focusRing(flow?.focused == id, RoundedCornerShape(Radius.r14))
                        .testTag("stage.need." + id)
                        .semantics {
                            contentDescription = label
                            this.selected = chosen
                        },
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = Space.xxs, vertical = Space.xs),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(NeedGlyphs[id].orEmpty(), fontSize = TypeSize.s17, textAlign = TextAlign.Center)
                        Text(
                            label,
                            fontSize = TypeSize.s10,
                            color = if (chosen) answer.onYes else call.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
