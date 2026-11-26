package app.signsetu.ui.session

import app.signsetu.ui.theme.LocalStageColors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.signsetu.R
import app.signsetu.data.content.AnswerType
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.Given
import app.signsetu.data.content.or
import app.signsetu.data.content.Method
import app.signsetu.data.content.Question
import app.signsetu.data.content.answerText
import app.signsetu.ui.components.CardImage
import app.signsetu.ui.theme.Baloo
import app.signsetu.ui.theme.LocalAnswerColors
import app.signsetu.ui.theme.Space
import app.signsetu.ui.theme.TypeSize
import app.signsetu.ui.theme.Radius
import app.signsetu.ui.theme.StageTones
import app.signsetu.ui.theme.AnswerLight

internal val Soft = AnswerLight.pause
internal val OnSoft = AnswerLight.onPause
/** A Done that cannot be pressed yet. */
internal val Off = StageTones.lessonOff

/** The question on stage, when it takes a lesson-sheet answer input instead of the answer rails. */
fun lessonQuestion(pending: Pending?): Question? = (pending as? Pending.Ask)?.question?.takeIf { it.answer != null }

/** Answer inputs for the lesson-sheet questions. A written or typed answer is read back large and logged as the
 * subject's own only when they accept it; a read-back left open is logged unconfirmed. */
@Composable
internal fun AnswerPanel(
    q: Question,
    content: ContentBundle,
    lang: String,
    chosen: String?,
    onCard: (String) -> Unit,
    onGiven: (Given?) -> Unit,
    onSign: (() -> Unit)?,
    modifier: Modifier,
) {
    val type = q.answer ?: return
    var readBack by remember(q.id) { mutableStateOf<Given?>(null) }
    var accepted by remember(q.id) { mutableStateOf<Given?>(null) }
    val entered = { g: Given -> readBack = g; accepted = null; onGiven(g) }
    Column(modifier.imePadding(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val back = readBack
            val done = accepted
            when {
                done != null -> ReadBack(content.answerText(type, done, lang), content, lang, onYes = null, onFix = { accepted = null })
                back != null -> ReadBack(
                    content.answerText(type, back, lang), content, lang,
                    onYes = { val g = back.copy(confirmed = true); onGiven(g); accepted = g; readBack = null },
                    onFix = { onGiven(null); readBack = null },
                )
                else -> when (type) {
                    AnswerType.NAME, AnswerType.TEXT -> WordEntry(type, null) { entered(Given(q.id, Method.WRITTEN, false, it.trim())) }
                    AnswerType.AGE, AnswerType.COUNT, AnswerType.PHONE -> NumberPad(type) { entered(Given(q.id, Method.PAD, false, it)) }
                    AnswerType.SIBLINGS -> SiblingPads(content, lang) { entered(Given(q.id, Method.PAD, false, it)) }
                    AnswerType.CLASS -> ClassGrid(content, lang) { entered(Given(q.id, Method.PICKER, false, it)) }
                    AnswerType.STATE, AnswerType.DISTRICT, AnswerType.PLACE ->
                        PlacePicker(type, content, lang) { picks, written -> entered(Given(q.id, Method.PICKER, false, written.trim(), picks)) }
                    // Done on their own selection is the subject's confirmation.
                    AnswerType.PICTURES -> PictureChoices(q.cards, content, lang) { picks ->
                        val g = Given(q.id, Method.PICTURE, true, picks = picks)
                        onGiven(g)
                        accepted = g
                    }
                }
            }
        }
        WaysOut(content, lang, chosen, onCard, onSign, extra = "no_phone".takeIf { type == AnswerType.PHONE })
    }
}

/** The answer shown back large; Yes makes it the subject's, Change lets them enter it again. */
@Composable
private fun ReadBack(text: String, content: ContentBundle, lang: String, onYes: (() -> Unit)?, onFix: () -> Unit) {
    val answer = LocalAnswerColors.current
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Space.sm, Alignment.CenterVertically)) {
        Surface(shape = RoundedCornerShape(Radius.r24), color = Color.White, shadowElevation = Space.xs, modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
            Column(Modifier.padding(Space.lg), horizontalAlignment = Alignment.CenterHorizontally) {
                if (onYes != null) content.subjectText("is_this_right", lang)?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = Ink) }
                else Icon(Icons.Filled.Check, contentDescription = null, tint = answer.onYesContainer, modifier = Modifier.size(Space.xxl))
                Text(
                    text, fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = TypeSize.s40, lineHeight = TypeSize.s44, color = Ink,
                    textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = TypeSize.s16, maxFontSize = TypeSize.s40),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            BigButton(rememberVectorPainter(Icons.Filled.Edit), content.subjectText("fix", lang), stringResource(R.string.stage_fix), Soft, OnSoft, Modifier.weight(1f), onFix)
            onYes?.let { BigButton(rememberVectorPainter(Icons.Filled.Check), content.subjectText("yes", lang), stringResource(R.string.stage_confirm), answer.yes, answer.onYes, Modifier.weight(1f), it) }
        }
    }
}

/** I don't know, I don't understand, That's wrong and Stop under every input, in the same order as the rails. */
@Composable
private fun WaysOut(content: ContentBundle, lang: String, chosen: String?, onCard: (String) -> Unit, onSign: (() -> Unit)?, extra: String? = null) {
    val answer = LocalAnswerColors.current
    Row(Modifier.fillMaxWidth().height(Space.s64), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        extra?.let { Way(it, content.cards[it]?.label?.get(lang), stringResource(R.string.stage_no_phone), Color.White, Ink, chosen == it) { onCard(it) } }
        Way("dont_know", content.subjectText("dont_know", lang), stringResource(R.string.stage_dont_know), answer.unsure, answer.onUnsure, chosen == "dont_know") { onCard("dont_know") }
        Way(DONT_UNDERSTAND, content.subjectText(DONT_UNDERSTAND, lang), stringResource(R.string.stage_dont_understand), answer.unsure, answer.onUnsure, chosen == DONT_UNDERSTAND) { onCard(DONT_UNDERSTAND) }
        Way("wrong", content.subjectText("wrong", lang), stringResource(R.string.stage_wrong), answer.unsure, answer.onUnsure, chosen == "wrong") { onCard("wrong") }
        onSign?.let {
            val word = content.subjectText("sign", lang)
            val name = word ?: stringResource(R.string.officer_read_signs)
            val stage = LocalStageColors.current
            // A tool colour and a word, so the camera never reads as a second Stop beside it.
            PressableSurface(onClick = it, shape = RoundedCornerShape(Radius.r16), color = stage.tool, border = stage.toolEdge, modifier = Modifier.weight(1f).fillMaxSize().semantics { contentDescription = name }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(painterResource(R.drawable.ic_videocam), contentDescription = null, tint = stage.onTool, modifier = Modifier.size(Space.xl))
                    word?.let { w -> Text(w, fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = TypeSize.s11, color = stage.onTool, maxLines = 1) }
                }
            }
        }
        Way("stop", content.subjectText("stop", lang), stringResource(R.string.stage_stop), answer.pause, answer.onPause, chosen == "stop") { onCard("stop") }
    }
}

@Composable
private fun RowScope.Way(cardId: String, label: String?, name: String, color: Color, onColor: Color, chosen: Boolean, onClick: () -> Unit) {
    PressableSurface(
        onClick = onClick,
        shape = RoundedCornerShape(Radius.r16),
        color = color,
        border = if (chosen) BorderStroke(Space.s3, onColor) else null,
        modifier = Modifier.weight(1f).fillMaxSize().semantics { contentDescription = label ?: name },
    ) {
        Column(Modifier.padding(Space.xxs), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            CardImage(cardId, Modifier.size(if (label != null) Space.xl else Space.s36))
            label?.let { Text(it, fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = TypeSize.s11, lineHeight = TypeSize.s12, color = onColor, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}
