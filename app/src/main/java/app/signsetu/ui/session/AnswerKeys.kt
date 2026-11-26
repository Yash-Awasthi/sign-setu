package app.signsetu.ui.session

import app.signsetu.data.settings.SwitchAccess

/** Keys that walk the subject's answers, whatever board sent them: a keyboard, a D-pad, or a switch. */
enum class AnswerKey { NEXT, PREVIOUS, RIGHT, LEFT, DOWN, UP, SELECT, ESCAPE }

/** What one key did to the answer flow. */
sealed interface AnswerAction {
    /** Move the highlight to [id]. */
    data class Move(val id: String) : AnswerAction

    /** The person chose [id]; the caller runs that control's own tap. */
    data class Choose(val id: String) : AnswerAction

    /** Leave the answer flow without answering. */
    data object Leave : AnswerAction

    /** The key is not one the flow takes; the platform may have it. */
    data object Ignore : AnswerAction
}

/** A switch press: move the highlight on, or choose what it is on. */
sealed interface SwitchAction {
    data class Move(val id: String) : SwitchAction
    data class Choose(val id: String) : SwitchAction
}

/** Longest wait for the scan to move on by itself (dwell mode). */
const val SWITCH_DWELL_MS = 1_200L

/** Shortest press that counts as a choice rather than an advance (step mode). */
const val SWITCH_HOLD_MS = 900L

/**
 * The subject's controls in reading order, with the number of [columns] they are drawn in.
 * Callers append ids exactly as they draw the controls, row by row, so Tab order is visual order.
 * [key] is the whole keyboard decision; the composables only apply what it returns.
 */
class AnswerOrder(val ids: List<String>, val columns: Int) {
    init {
        require(columns >= 1) { "a grid needs at least one column" }
    }

    fun indexOf(id: String): Int = ids.indexOf(id)

    fun key(at: String, key: AnswerKey): AnswerAction {
        val i = indexOf(at)
        if (i < 0) return AnswerAction.Ignore
        return when (key) {
            // Tab leaves the surface at either end, so a keyboard user is never trapped on the answers.
            AnswerKey.NEXT -> if (i == ids.lastIndex) AnswerAction.Ignore else AnswerAction.Move(ids[i + 1])
            AnswerKey.PREVIOUS -> if (i == 0) AnswerAction.Ignore else AnswerAction.Move(ids[i - 1])
            // Arrows stay inside a row, or inside the grid; never off it.
            AnswerKey.RIGHT -> AnswerAction.Move(ids[if (i % columns < columns - 1 && i + 1 < ids.size) i + 1 else i])
            AnswerKey.LEFT -> AnswerAction.Move(ids[if (i % columns > 0) i - 1 else i])
            AnswerKey.DOWN -> AnswerAction.Move(ids[(i + columns).coerceAtMost(ids.size - 1)])
            AnswerKey.UP -> AnswerAction.Move(ids[(i - columns).coerceAtLeast(0)])
            AnswerKey.SELECT -> AnswerAction.Choose(at)
            AnswerKey.ESCAPE -> AnswerAction.Leave
        }
    }
}

/**
 * One switch and the one control it is on, whether the highlight moves by itself (dwell) or on
 * each press (step). Time is passed in, so the whole state machine can be walked without a clock.
 */
class SwitchScan(val order: AnswerOrder, val mode: SwitchAccess) {
    /** Where the scan is, when it last moved, and when the switch went down. */
    data class State(val at: Int, val since: Long, val downAt: Long? = null)

    /** The control the scan is on. */
    fun id(state: State): String = order.ids[state.at]

    fun start(now: Long): State {
        require(order.ids.isNotEmpty()) { "nothing to scan" }
        return State(0, now)
    }

    /** Dwell mode moves one control on when [dwellMs] has passed; step mode waits for a press. */
    fun tick(state: State, now: Long, dwellMs: Long = SWITCH_DWELL_MS): Pair<State, SwitchAction?> {
        if (mode != SwitchAccess.DWELL || order.ids.size < 2) return state to null
        if (now - state.since < dwellMs) return state to null
        val next = (state.at + 1) % order.ids.size
        return State(next, now) to SwitchAction.Move(order.ids[next])
    }

    /** Dwell mode: the press chooses what is lit. Step mode: remember when the press began. */
    fun press(state: State, now: Long): Pair<State, SwitchAction?> = when (mode) {
        SwitchAccess.OFF -> state to null
        SwitchAccess.DWELL -> state to SwitchAction.Choose(id(state))
        SwitchAccess.STEP -> state.copy(downAt = state.downAt ?: now) to null
    }

    /** What the press did when it ended: a tap advances one control, a hold chooses. */
    fun up(state: State, now: Long, holdMs: Long = SWITCH_HOLD_MS): Pair<State, SwitchAction?> = when (mode) {
        SwitchAccess.OFF -> state to null
        SwitchAccess.DWELL -> state.copy(downAt = null) to null
        SwitchAccess.STEP -> {
            val pressed = state.downAt
            if (pressed == null) state to null
            else if (now - pressed >= holdMs) State(state.at, now) to SwitchAction.Choose(id(state))
            else {
                val next = (state.at + 1) % order.ids.size
                State(next, now) to SwitchAction.Move(order.ids[next])
            }
        }
    }
}

/** The answer bar's controls in the order VideoCallStage draws them, so the walk and the picture agree. */
fun answerBarIds(signing: Boolean, statement: Boolean, next: Boolean, rights: Boolean, controls: Boolean, moreOpen: Boolean): List<String> = buildList {
    if (signing) {
        add("done")
        return@buildList
    }
    if (next) add("next")
    if (!statement && !next) add("yes")
    if (!statement && !next) add("no")
    if (rights) add(DONT_UNDERSTAND)
    if (controls) {
        add("water")
        add("food")
        add("express")
        // The trio exists only behind More, and a statement has no More.
        if (!statement) {
            add(MORE)
            if (moreOpen) {
                add("dont_know")
                add(DONT_UNDERSTAND)
                add("wrong")
            }
        }
    }
}

/** The More control's id; it opens the unsure trio, so it is a way on, not an answer. */
const val MORE = "more"

/** The picture or number tiles of a question, in the rows they are drawn in. */
fun choiceIds(choices: List<String>, numbers: Boolean): AnswerOrder = AnswerOrder(choices, if (numbers) 5 else 3)
