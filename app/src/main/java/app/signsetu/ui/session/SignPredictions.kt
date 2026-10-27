package app.signsetu.ui.session

import app.signsetu.ui.theme.motion
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.SignMode
import app.signsetu.data.content.glossToText
import app.signsetu.data.content.planText
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.SessionEvent
import app.signsetu.sign.NGram
import app.signsetu.sign.SENTENCE_SYSTEM
import app.signsetu.sign.SentenceGuess
import app.signsetu.sign.SignSegment
import app.signsetu.sign.expectedWords
import app.signsetu.sign.parseSentences
import app.signsetu.sign.rankSentences
import app.signsetu.sign.sentencePrompt
import app.signsetu.ui.components.LocalOnlineAssistant
import app.signsetu.ui.theme.Spacing

private const val PREDICTIONS = 4

/** The latest machine reading still waiting on the officer: no answer, and no reading of the officer's, has come since. */
internal fun pendingReading(events: List<SessionEvent>): SessionEvent? {
    val i = events.indexOfLast { it.type == EventType.ANSWER_SIGNED && it.source == EventSource.MACHINE }
    if (i < 0) return null
    return events[i].takeIf { events.drop(i + 1).none { it.type in AnswerEvents } }
}

/** Up to five likely sentences for a reading: ranked on the phone at once for two or more signs (reader scores, a
 * trigram model of the app's own sentences, the question's words, spelling fixes), with the online assistant's
 * answers to the question first when the officer has switched it on and it replies. */
@Composable
internal fun rememberSentences(
    eventId: Long,
    segments: List<SignSegment>,
    pending: Pending?,
    content: ContentBundle,
    names: Collection<String>,
    details: Collection<String>,
): List<SentenceGuess> {
    val guesses = segments.map { it.candidates.ifEmpty { it.guesses.orEmpty() } }
    val cloud = LocalOnlineAssistant.current
    // Signs arrive in ISL order without small words, so the model learns each sentence as the character would sign
    // it (planText's own order) as well as written; built off the main thread, once per bundle.
    val lm by produceState<NGram?>(null, content) {
        value = withContext(Dispatchers.Default) {
            val written = content.clips.keys + content.questions.mapNotNull { it.text["en"] } + content.phrases.flatMap { p -> p.slots.mapNotNull { it.text["en"] } }
            NGram(written + written.map { t -> content.planText(t, setOf(SignMode.SIGNS)).beats.sortedBy { it.start }.joinToString(" ") { t.substring(it.range) } })
        }
    }
    val known = remember(content, names) { content.words.keys.filter { w -> w.all { it.isLetter() } } + names.map { it.lowercase() } }
    val question = describeItem(pending, content, "en").caption
    val offline by produceState(emptyList<SentenceGuess>(), eventId, lm) {
        if (guesses.count { it.isNotEmpty() } >= 2) value = withContext(Dispatchers.Default) {
            rankSentences(guesses, expectedWords(question, pending.answerCards()), lm, known, { glossToText(it, content.words, content.language).en }, top = SENTENCES)
        }
    }
    val online by produceState(emptyList<SentenceGuess>(), eventId) {
        if (guesses.any { it.isNotEmpty() }) value = cloud.ask(SENTENCE_SYSTEM, sentencePrompt(question, guesses.map { it.take(ONLINE_GUESSES) }), names, 250, details)?.let { parseSentences(it, guesses.map { g -> g.take(ONLINE_GUESSES) }) }.orEmpty()
    }
    return (online + offline).distinctBy { it.text.lowercase().trimEnd('.', '?', '!') }.take(SENTENCES)
}

private const val SENTENCES = 5
/** Guesses per sign sent online: enough to recover a misread, few enough to keep the prompt short. */
private const val ONLINE_GUESSES = 6

/** The likeliest whole sentences first, then the reader's top guesses for each sign. A sentence tap logs that reading at
 * once. Otherwise the officer taps one guess per sign, which hides the rest; tapping it again takes the choice back.
 * Once every sign with guesses has one, the reading is logged once as the officer's: the record is append-only, so
 * half-made picks never reach it. The subject is never asked to confirm. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SignPredictions(
    eventId: Long,
    segments: List<SignSegment>,
    onPick: (words: List<String>, sentence: String?) -> Unit,
    modifier: Modifier = Modifier,
    sentences: List<SentenceGuess> = emptyList(),
) {
    val picked = remember(eventId) { mutableStateMapOf<Int, String>() }
    val guesses = segments.map { s -> s.guesses.orEmpty().take(PREDICTIONS).map { it.word } }
    if (guesses.all { it.isEmpty() }) return
    // A long account reads as many signs; the guesses scroll so the conversation above stays in view.
    Column(modifier.fillMaxWidth().animateContentSize(motion(spring())).heightIn(max = 168.dp).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg, vertical = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (picked.isEmpty()) sentences.forEach { g ->
            SuggestionChip(onClick = { onPick(g.words, g.text) }, label = { Text(g.text, style = MaterialTheme.typography.bodyMedium) })
        }
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
                                onPick(segments.mapIndexed { n, s -> picked[n] ?: s.word ?: "?" }, null)
                            }
                        },
                        label = { Text(w.replace('_', ' ')) },
                    )
                }
            }
        }
    }
}
