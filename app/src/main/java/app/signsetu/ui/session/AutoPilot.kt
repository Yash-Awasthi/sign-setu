package app.signsetu.ui.session

import android.database.SQLException
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.Kind
import app.signsetu.data.content.Phase
import app.signsetu.data.content.Question
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.SessionEvent
import app.signsetu.data.session.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Auto mode's state. Memory only: after a lock or restart the session reopens in Manual. */
sealed interface Conv {
    data object Off : Conv
    /** [held]: auto mode is paused, so the officer decides what happens to this proposal. */
    data class Proposed(val p: Proposal, val held: Boolean = false) : Conv
    /** Something is on the stage; the next proposal waits for its answer or the officer's return. */
    data object Waiting : Conv
    data class Paused(val cause: PauseCause) : Conv
    data object Exhausted : Conv
}

enum class PauseCause { OFFICER, STOP, BREAK }

/** Runs auto mode beside the view model: every proposal, approval and skip is logged before it changes
 * anything, and every decision is re-derived from a fresh read of the log. */
class AutoPilot(
    private val sessionId: String,
    private val repo: SessionRepository,
    private val content: ContentBundle,
    private val officerLang: String,
    private val scope: CoroutineScope,
    /** True while the subject has hands up for a sign. */
    private val signing: () -> Boolean = { false },
    /** True: each proposal goes on the stage by itself on a timer. False: the officer approves each one. */
    private val handsOff: Boolean = false,
    private val present: suspend (Question, String) -> Unit,
) {
    private val state = MutableStateFlow<Conv>(Conv.Off)
    val conv: StateFlow<Conv> = state.asStateFlow()
    /** Auto mode shows each proposal itself; false while the officer has paused it. */
    private var running = false
    /** Bumped by every proposal and every shown item, so an older timer can tell it has been replaced. */
    private var pace = 0
    private val lock = Mutex()
    private val secondsLeft = MutableStateFlow<Int?>(null)
    /** Whole seconds until auto mode puts the next item on the stage; null when nothing is due. */
    val countdown: StateFlow<Int?> = secondsLeft.asStateFlow()

    init {
        // Answers arrive from the stage through the log, whoever logged them.
        scope.launch {
            var seen: Long? = null
            repo.events(sessionId).collect { events ->
                val answer = events.lastOrNull { it.type in AnswerEvents }
                val before = seen
                seen = answer?.id ?: -1L
                if (answer != null && before != null && answer.id > before) act { onAnswer(answer) }
            }
        }
    }

    fun setAuto(on: Boolean) = act {
        if (on == (state.value != Conv.Off)) return@act
        if (on) {
            system(AutoKey.MODE_AUTO)
            running = handsOff
            propose()
        } else {
            skipProposed("mode_off")
            system(AutoKey.MODE_MANUAL)
            turnOff()
        }
    }

    fun approve() = act {
        val p = (state.value as? Conv.Proposed)?.p ?: return@act
        mark(EventType.APPROVED, EventSource.OFFICER, p.q)
        // Showing a held proposal sends the phone back to the subject, so auto mode carries on from there.
        running = handsOff
        show(p.q)
    }

    fun skip() = act {
        if (skipProposed(SKIP_OFFICER)) propose()
    }

    fun pause() = act {
        if (state.value == Conv.Off) return@act
        running = false
        system(AutoKey.PAUSED)
        // A proposal already waiting stays on screen with Show, Skip and Resume; otherwise the officer sees a notice.
        state.value = (state.value as? Conv.Proposed)?.takeIf { handsOff }?.copy(held = true) ?: Conv.Paused(PauseCause.OFFICER)
    }

    fun resume() = act {
        if (state.value !is Conv.Paused && (state.value as? Conv.Proposed)?.held != true) return@act
        system(AutoKey.RESUMED)
        running = handsOff
        propose()
    }

    fun wrapUp() = act {
        if (state.value == Conv.Off) return@act
        system(AutoKey.WRAP_UP)
        propose()
    }

    /** Called before a manual Show is logged, so the record says the officer chose something else. */
    suspend fun onManualShow(pending: Pending) = lock.withLock {
        guarded {
            if (state.value == Conv.Off) return@guarded
            skipProposed("officer_chose_other")
            val q = (pending as? Pending.Ask)?.question
            // Typed words and show-backs can ask about events, and Auto covers welfare triage only.
            val substantive = pending is Pending.FreeText || pending is Pending.ShowBack ||
                q != null && (content.group(q.group)?.sensitive == true || q.phase == Phase.FREE_RECALL || q.phase == Phase.FOLLOW_UP)
            if (substantive) {
                system(AutoKey.MODE_MANUAL_SUBSTANTIVE)
                turnOff()
            } else {
                state.value = Conv.Waiting
            }
        }
    }

    /** The officer took the phone back from the stage, which pauses auto mode. */
    fun onReturn() = act {
        if (running) {
            running = false
            system(AutoKey.PAUSED)
            state.value = (state.value as? Conv.Proposed)?.copy(held = true) ?: Conv.Paused(PauseCause.OFFICER)
        } else if (state.value == Conv.Waiting) propose()
    }

    fun onRoleChanged() = act {
        if (state.value == Conv.Off) return@act
        skipProposed("role_changed")
        if (state.value !is Conv.Paused) propose()
    }

    private suspend fun onAnswer(answer: SessionEvent) {
        val now = state.value
        if (now == Conv.Off) return
        val events = repo.eventsNow(sessionId)
        val item = events.firstOrNull { it.id == answer.shownId }?.itemId
        when {
            answer.isStop() -> {
                running = false
                val since = events.indexOfLast { it.type == EventType.SYSTEM && it.text == AutoKey.MODE_AUTO }
                if (events.drop(since + 1).count { it.isStop() && it.type in AnswerEvents } >= 2) {
                    system(AutoKey.ENDED_STOP)
                    turnOff()
                } else {
                    state.value = Conv.Paused(PauseCause.STOP)
                }
            }
            now is Conv.Paused -> Unit
            item == "k1" && answer.cardId == "yes" -> {
                system(AutoKey.BREAK)
                running = false
                state.value = Conv.Paused(PauseCause.BREAK)
            }
            item == "k3" && answer.cardId != "yes" -> { running = false; state.value = Conv.Paused(PauseCause.STOP) }
            else -> propose(if (answer.type == EventType.ANSWER_SIGNED) SIGN_GAP_MS else AUTO_GAP_MS)
        }
    }

    private suspend fun show(q: Question) {
        val session = repo.session(sessionId).filterNotNull().first()
        state.value = Conv.Waiting
        present(q, session.subjectLang)
        // A statement asks for no answer, so the next item follows after time to read it; a question waits for its answer.
        if (q.kind == Kind.STATEMENT) propose(STATEMENT_GAP_MS) else if (running) watchForAnswer(q, session.subjectLang)
    }

    private suspend fun propose(gapMs: Long = AUTO_GAP_MS) {
        val session = repo.session(sessionId).filterNotNull().first()
        val events = repo.eventsNow(sessionId)
        val next = next(content, session.role, events)
        if (next == null) {
            running = false
            state.value = Conv.Exhausted
            return
        }
        if ((state.value as? Conv.Proposed)?.p?.q?.id != next.q.id) mark(EventType.SUGGESTED, EventSource.RULE, next.q, next.reason.name)
        state.value = Conv.Proposed(next, held = !running)
        pace++
        if (running) showAfterPause(next.q, gapMs, pace)
    }

    /** Hands-off: the proposal goes on the stage after [gapMs] unless the officer paused or chose otherwise. Hands still
     * signing hold it back, and a sign just given gets a quiet spell, so a small pause is not read as the end. */
    private fun showAfterPause(q: Question, gapMs: Long, token: Int) {
        scope.launch {
            var left = gapMs
            while (left > 0) {
                secondsLeft.value = ((left + 999) / 1000).toInt()
                delay(TICK_MS)
                if (token != pace || !running) { if (token == pace) secondsLeft.value = null; return@launch }
                left = if (signing()) SIGN_GAP_MS else left - TICK_MS
            }
            secondsLeft.value = null
            act {
                val proposed = state.value as? Conv.Proposed ?: return@act
                if (token != pace || !running || proposed.held || proposed.p.q.id != q.id) return@act
                mark(EventType.AUTO_ADVANCED, EventSource.RULE, q)
                show(q)
            }
        }
    }

    /** No sign or answer for [NO_ANSWER_MS]: the item is shown again once; after the same wait again it is skipped. */
    private fun watchForAnswer(q: Question, lang: String) {
        val token = ++pace
        scope.launch {
            var quiet = 0L
            var repeated = false
            while (true) {
                delay(TICK_MS)
                if (token != pace || !running) return@launch
                if (signing()) { quiet = 0; continue }
                quiet += TICK_MS
                if (quiet < NO_ANSWER_MS) continue
                quiet = 0
                if (!repeated) {
                    repeated = true
                    act { if (token == pace && running) present(q, lang) }
                } else {
                    act {
                        if (token != pace || !running) return@act
                        mark(EventType.SKIPPED, EventSource.RULE, q, SKIP_NO_ANSWER)
                        propose()
                    }
                    return@launch
                }
            }
        }
    }

    private suspend fun skipProposed(reason: String): Boolean {
        val p = (state.value as? Conv.Proposed)?.p ?: return false
        mark(EventType.SKIPPED, EventSource.OFFICER, p.q, reason)
        state.value = Conv.Waiting
        return true
    }

    private fun turnOff() {
        running = false
        secondsLeft.value = null
        state.value = Conv.Off
    }

    // The outcome row and the QUESTION_SHOWN after it are two inserts, not one transaction; a crash between
    // them leaves an approval with nothing shown, which the sequencer reads as not shown.
    private suspend fun mark(type: EventType, source: EventSource, q: Question, reason: String? = null) {
        repo.log(sessionId, type, source, officerLang, text = q.text.get(officerLang), itemId = q.id, reason = reason)
    }

    private suspend fun system(key: String) {
        repo.log(sessionId, EventType.SYSTEM, EventSource.OFFICER, text = key)
    }

    private fun act(block: suspend () -> Unit) {
        scope.launch { lock.withLock { guarded(block) } }
    }

    /** A lock can close the database under a running action; auto mode then stops instead of crashing in a subject's hands. */
    private suspend fun guarded(block: suspend () -> Unit) {
        try {
            block()
        } catch (_: IllegalStateException) {
            turnOff()
        } catch (_: SQLException) {
            turnOff()
        }
    }
}

private const val AUTO_GAP_MS = 4_000L
/** After a sign: a further sign may follow, so the next item waits this long past the last one. */
private const val SIGN_GAP_MS = 5_000L
private const val STATEMENT_GAP_MS = 10_000L
private const val NO_ANSWER_MS = 10_000L
private const val TICK_MS = 250L
private const val SKIP_NO_ANSWER = "no_answer"
