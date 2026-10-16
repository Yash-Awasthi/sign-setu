package app.signsetu.ui.session

import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.SessionEvent

/** How one prompt was answered: every answer in order, how long it took, and how long a pause lasted. */
data class Trail(val answers: List<String>, val latencyMs: Long, val finalAfterMs: Long, val pauseMs: Long?) {
    val changed: Boolean get() = answers.size > 1
    /** Worth a second look, never a sign of deceit: the person changed their answer or took long to give one. */
    val unsteady: Boolean get() = changed || latencyMs > SLOW_MS
}

private const val SLOW_MS = 20_000L

/** The subject's own taps, writing and confirmations; an unanswered item logged at handback is not one of them. */
private val TrailAnswers = setOf(EventType.ANSWER_CARD, EventType.ANSWER_TEXT, EventType.ANSWER_DRAWING, EventType.ANSWER_GESTURE)

/** Trails keyed by the id of the last answer to each prompt, derived from the rows already logged. */
fun answerTrails(events: List<SessionEvent>): Map<Long, Trail> {
    val out = mutableMapOf<Long, Trail>()
    var question: SessionEvent? = null
    val group = mutableListOf<Int>()
    fun close() {
        val q = question
        if (q != null && group.isNotEmpty()) {
            val first = events[group.first()]
            val last = events[group.last()]
            // A stop that ends a run logs the run's end at once; the pause lasts until the next real step.
            val next = events.drop(group.last() + 1).firstOrNull { it.text?.startsWith(SYSTEM_RUN_ENDED) != true }
            val pause = if (last.cardId == "stop") next?.let { it.ts - last.ts } else null
            out[last.id] = Trail(group.map { events[it].text ?: events[it].cardId.orEmpty() }, first.ts - q.ts, last.ts - q.ts, pause)
        }
        group.clear()
    }
    events.forEachIndexed { i, e ->
        when {
            e.type == EventType.QUESTION_SHOWN -> { close(); question = e }
            e.type == EventType.SYSTEM -> { close(); question = null }
            e.type in TrailAnswers && e.source == EventSource.SUBJECT && question != null -> group += i
        }
    }
    close()
    return out
}
