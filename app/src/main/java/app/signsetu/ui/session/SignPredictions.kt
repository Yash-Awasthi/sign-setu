package app.signsetu.ui.session

import app.signsetu.ui.theme.motion
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.SessionEvent
import app.signsetu.sign.SignSegment
import app.signsetu.ui.theme.Spacing

private const val PREDICTIONS = 4

/** The latest machine reading still waiting on the officer: no answer, and no reading of the officer's, has come since. */
internal fun pendingReading(events: List<SessionEvent>): SessionEvent? {
    val i = events.indexOfLast { it.type == EventType.ANSWER_SIGNED && it.source == EventSource.MACHINE }
    if (i < 0) return null
    return events[i].takeIf { events.drop(i + 1).none { it.type in AnswerEvents } }
}

/** The reader's top guesses for each sign. The officer taps one per sign, which hides the rest; tapping it again takes
 * the choice back. Once every sign with guesses has one, the reading is logged once as the officer's: the record is
 * append-only, so half-made picks never reach it. The subject is never asked to confirm. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SignPredictions(eventId: Long, segments: List<SignSegment>, onPick: (List<String>) -> Unit, modifier: Modifier = Modifier) {
    val picked = remember(eventId) { mutableStateMapOf<Int, String>() }
    val guesses = segments.map { s -> s.guesses.orEmpty().take(PREDICTIONS).map { it.word } }
    if (guesses.all { it.isEmpty() }) return
    Column(modifier.fillMaxWidth().animateContentSize(motion(spring())).padding(horizontal = Spacing.lg, vertical = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        guesses.forEachIndexed { i, words ->
            if (words.isEmpty()) return@forEachIndexed
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val choice = picked[i]
                (if (choice != null) listOf(choice) else words).forEach { w ->
                    FilterChip(
                        selected = choice == w,
                        onClick = {
                            if (choice == w) picked.remove(i) else picked[i] = w
                            if (guesses.indices.all { n -> guesses[n].isEmpty() || n in picked }) {
                                onPick(segments.mapIndexed { n, s -> picked[n] ?: s.word ?: "?" })
                            }
                        },
                        label = { Text(w.replace('_', ' ')) },
                    )
                }
            }
        }
    }
}
