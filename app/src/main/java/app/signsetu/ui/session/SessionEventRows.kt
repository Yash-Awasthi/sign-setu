package app.signsetu.ui.session

import android.content.res.Resources
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.signsetu.R
import app.signsetu.data.content.BodyLocation
import app.signsetu.data.content.HandAction
import app.signsetu.data.content.givenOf
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.SessionEvent
import app.signsetu.ui.components.CardImage
import app.signsetu.ui.pair.linkText
import app.signsetu.ui.theme.PictureWell

/** Pairs each answer with its question, so the log reads "Water? Yes" rather than a bare "Yes": by the shown
 * item's id when the answer names it, otherwise the question shown before it. */
fun List<SessionEvent>.withQuestions(): List<Pair<SessionEvent, String?>> {
    // Answers from a second screen name their question, since they can arrive after the next one was shown.
    val shown = filter { it.type == EventType.QUESTION_SHOWN }.associate { it.id to it.text }
    var question: String? = null
    return map { e ->
        when (e.type) {
            EventType.QUESTION_SHOWN -> question = e.text
            EventType.SYSTEM -> question = null
            else -> Unit
        }
        e to if (e.type in AnswerEvents) (if (e.shownId != null) shown[e.shownId] else question) else null
    }
}

val AnswerEvents = setOf(EventType.ANSWER_CARD, EventType.ANSWER_DRAWING, EventType.ANSWER_GESTURE, EventType.ANSWER_UNCLEAR, EventType.LANGUAGE_CHOSEN, EventType.ANSWER_TEXT, EventType.ANSWER_SIGNED)

/** A signed answer is always marked as the app's reading or the officer's, never as the subject's own words. */
fun signedMark(e: SessionEvent) = if (e.source == EventSource.OFFICER) R.string.signed_officer else R.string.signed_app

/** Words of a signed answer for display; null when no sign has been read yet. The record keeps its "?" marks. */
fun signedWords(text: String?): String? =
    text?.split(' ')?.takeUnless { w -> w.all { it == "?" } }?.joinToString(" ") { if (it == "?") "…" else it }

/** How a signed answer reads on screen and in the export: its words, or how many signs wait to be read. */
fun signedDisplay(res: Resources, text: String?): String =
    signedWords(text) ?: (text?.split(' ')?.size ?: 0).let { res.getQuantityString(R.plurals.signed_unread, it, it) }

@Composable
fun EventRow(e: SessionEvent, modifier: Modifier = Modifier, question: String? = null) {
    ListItem(
        overlineContent = {
            val mark = when {
                e.type == EventType.ANSWER_SIGNED -> "  ·  ${stringResource(signedMark(e))}"
                e.reason == SHOWN_RIGHTS -> "  ·  ${stringResource(R.string.rights_shown_mark)}"
                e.via != null -> "  ·  ${stringResource(R.string.report_via_stage)}"
                else -> ""
            }
            val kind = givenOf(e.reason)?.let { stringResource(it.first.label()).replaceFirstChar(Char::uppercase) } ?: stringResource(e.type.label())
            Text("${clockTime(e.ts)}  ·  $kind$mark")
        },
        headlineContent = {
            val res = LocalResources.current
            Text(
                when (e.type) {
                    EventType.SYSTEM -> systemText(res, e.text)
                    EventType.ANSWER_SIGNED -> signedDisplay(res, e.text)
                    else -> linkText(e) ?: e.text ?: if (e.drawingId != null) res.getString(R.string.report_drawing) else "—"
                },
            )
        },
        supportingContent = question?.takeIf { it != e.text }?.let { q -> { Text(q, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        leadingContent = e.cardId?.let { id ->
            { Surface(shape = RoundedCornerShape(12.dp), color = PictureWell, modifier = Modifier.size(40.dp)) { CardImage(id, Modifier.padding(4.dp)) } }
        },
        modifier = modifier,
    )
}

fun BodyLocation.label() = when (this) {
    BodyLocation.HEAD -> R.string.loc_head
    BodyLocation.FOREHEAD -> R.string.loc_forehead
    BodyLocation.EYES -> R.string.loc_eyes
    BodyLocation.NOSE -> R.string.loc_nose
    BodyLocation.EAR -> R.string.loc_ear
    BodyLocation.FACE -> R.string.loc_face
    BodyLocation.MOUTH -> R.string.loc_mouth
    BodyLocation.CHIN -> R.string.loc_chin
    BodyLocation.CHEST -> R.string.loc_chest
    BodyLocation.BELLY -> R.string.loc_belly
    BodyLocation.ARM -> R.string.loc_arm
    BodyLocation.HANDS -> R.string.loc_hands
    BodyLocation.AWAY -> R.string.loc_away
}

fun HandAction.label() = when (this) {
    HandAction.TOUCH -> R.string.act_touch
    HandAction.STROKE -> R.string.act_stroke
    HandAction.POINT -> R.string.act_point
    HandAction.CIRCLE -> R.string.act_circle
    HandAction.FLAT_HAND -> R.string.act_flat
    HandAction.FIST -> R.string.act_fist
    HandAction.BOTH_HANDS -> R.string.act_both
    HandAction.MOVE -> R.string.act_move
    HandAction.HEAD_MOVE -> R.string.act_head
}

fun EventType.label() = when (this) {
    EventType.QUESTION_SHOWN -> R.string.log_shown
    EventType.ANSWER_CARD -> R.string.log_answer
    EventType.ANSWER_DRAWING -> R.string.log_drawing
    EventType.ANSWER_GESTURE -> R.string.log_gesture
    EventType.ANSWER_UNCLEAR -> R.string.log_unclear
    EventType.LANGUAGE_CHOSEN -> R.string.log_language
    EventType.NOTE -> R.string.log_note
    EventType.SYSTEM -> R.string.log_system
    EventType.ANSWER_TEXT -> R.string.log_typed
    EventType.ANSWER_SIGNED -> R.string.log_signed
    EventType.ANSWER_SPOKEN -> R.string.log_spoken
    EventType.LINK -> R.string.log_link
    EventType.SUGGESTED -> R.string.log_suggested
    EventType.APPROVED -> R.string.log_approved
    EventType.SKIPPED -> R.string.log_skipped
    EventType.AUTO_ADVANCED -> R.string.log_auto_advanced
    EventType.PROMPT -> R.string.log_prompt
    EventType.REQUEST -> R.string.log_request
}
