package app.signsetu

import app.signsetu.data.settings.SwitchAccess
import app.signsetu.ui.session.AnswerAction
import app.signsetu.ui.session.AnswerKey
import app.signsetu.ui.session.AnswerOrder
import app.signsetu.ui.session.DONT_UNDERSTAND
import app.signsetu.ui.session.MORE
import app.signsetu.ui.session.NEED_IDS
import app.signsetu.ui.session.SWITCH_DWELL_MS
import app.signsetu.ui.session.SWITCH_HOLD_MS
import app.signsetu.ui.session.SwitchAction
import app.signsetu.ui.session.SwitchScan
import app.signsetu.ui.session.answerBarIds
import app.signsetu.ui.session.choiceIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The person's answer flow walked by key events alone: the same [AnswerOrder] and [SwitchScan] the
 * stage uses, with no Android frame. The Compose layer only turns a key into one of these and applies it.
 */
class AnswerKeysTest {

    /** Feed [keys] to the order, recording what each did. */
    private fun walk(order: AnswerOrder, from: String, keys: List<AnswerKey>): List<AnswerAction> {
        var at = from
        return keys.map { key ->
            val action = order.key(at, key)
            if (action is AnswerAction.Move) at = action.id
            action
        }
    }

    @Test
    fun theAnswerBarIsWalkedInTheOrderItIsDrawn() {
        // Every control VideoCallStage can draw: Yes, No, Help and More, the trio, then the pinned needs row.
        val ids = answerBarIds(signing = false, statement = false, next = false, rights = false, controls = true, moreOpen = true)
        assertEquals(listOf("yes", "no", "express", MORE, "dont_know", DONT_UNDERSTAND, "wrong") + NEED_IDS, ids)
        assertEquals("an id must not appear twice, or two controls share one stop", ids.size, ids.distinct().size)
    }

    @Test
    fun aStatementOffersNoYesAndNoAndNoTrio() {
        assertEquals(listOf("next") + NEED_IDS, answerBarIds(signing = false, statement = true, next = true, rights = false, controls = false, moreOpen = false))
        assertEquals(
            "the needs stay reachable while a ground rule is read",
            listOf("next", "express") + NEED_IDS,
            answerBarIds(signing = false, statement = true, next = true, rights = false, controls = true, moreOpen = false),
        )
        assertEquals(
            "a statement never offers yes, no or More",
            listOf("express") + NEED_IDS,
            answerBarIds(signing = false, statement = true, next = false, rights = false, controls = true, moreOpen = true),
        )
        assertEquals(listOf("done") + NEED_IDS, answerBarIds(signing = true, statement = false, next = false, rights = false, controls = true, moreOpen = false))
    }

    @Test
    fun theExpressBoardKeepsOneSwitchStopAndOnlyWhenTheControlsShow() {
        val shown = answerBarIds(signing = false, statement = false, next = false, rights = false, controls = true, moreOpen = false)
        // One step on from No, where the answer row draws it: a dwell or step switch reaches the board.
        assertEquals("the walk reaches the board from the row", AnswerAction.Move("express"), AnswerOrder(shown, 7).key("no", AnswerKey.NEXT))
        val hidden = answerBarIds(signing = false, statement = false, next = false, rights = false, controls = false, moreOpen = false)
        assertTrue("no tile is drawn without the controls, so the walk must not offer it", "express" !in hidden)
    }

    @Test
    fun everyAnswerOnTheBarIsReachedByTabAloneAndTabLeavesAtTheEnd() {
        val ids = answerBarIds(signing = false, statement = false, next = false, rights = false, controls = true, moreOpen = true)
        val order = AnswerOrder(ids, 7)
        val actions = walk(order, "yes", List(ids.size - 1) { AnswerKey.NEXT })
        assertEquals("every control, in reading order", ids.drop(1), actions.map { (it as AnswerAction.Move).id })
        assertEquals("Tab leaves the bar instead of trapping the person on it", AnswerAction.Ignore, order.key(ids.last(), AnswerKey.NEXT))
    }

    @Test
    fun shiftTabGoesBackOneAnswerAndLeavesAtTheStart() {
        val order = AnswerOrder(listOf("yes", "no", "water"), 3)
        assertEquals(AnswerAction.Move("no"), order.key("water", AnswerKey.PREVIOUS))
        assertEquals(AnswerAction.Move("yes"), order.key("no", AnswerKey.PREVIOUS))
        assertEquals("Shift-Tab leaves the bar from its first control", AnswerAction.Ignore, order.key("yes", AnswerKey.PREVIOUS))
    }

    @Test
    fun thePictureGridIsWalkedRowByRowWithTheArrows() {
        // Six pictures drawn three to a row, the way ChoiceGrid lays them out.
        val order = choiceIds(listOf("p1", "p2", "p3", "p4", "p5", "p6"), numbers = false)
        assertEquals(AnswerAction.Move("p2"), order.key("p1", AnswerKey.RIGHT))
        assertEquals("right at the end of a row stays on it", AnswerAction.Move("p3"), order.key("p3", AnswerKey.RIGHT))
        assertEquals(AnswerAction.Move("p6"), order.key("p3", AnswerKey.DOWN))
        assertEquals(AnswerAction.Move("p2"), order.key("p5", AnswerKey.UP))
        assertEquals("left at the start of a row stays on it", AnswerAction.Move("p4"), order.key("p4", AnswerKey.LEFT))
        assertEquals("down from the last row lands on the last tile", AnswerAction.Move("p6"), order.key("p4", AnswerKey.DOWN))
        assertEquals(AnswerAction.Move("p4"), order.key("p3", AnswerKey.NEXT))
    }

    @Test
    fun everyPictureIsReachedByArrowKeysAlone() {
        val ids = listOf("p1", "p2", "p3", "p4", "p5", "p6")
        val order = choiceIds(ids, numbers = false)
        val seen = linkedSetOf("p1")
        var at = "p1"
        // Right along the row, down a row, left back, down again: the whole grid without Tab.
        for (key in listOf(
            AnswerKey.RIGHT, AnswerKey.RIGHT, AnswerKey.DOWN, AnswerKey.LEFT, AnswerKey.RIGHT,
            AnswerKey.DOWN, AnswerKey.LEFT, AnswerKey.LEFT, AnswerKey.UP, AnswerKey.UP,
        )) {
            val action = order.key(at, key)
            if (action is AnswerAction.Move) { at = action.id; seen += at }
        }
        assertEquals(ids.toSet(), seen)
    }

    @Test
    fun theNumberGridIsWalkedAlongItsFiveColumns() {
        val order = choiceIds(listOf("n1", "n2", "n3", "n4", "n5", "n6", "n7"), numbers = true)
        assertEquals(5, order.columns)
        assertEquals(AnswerAction.Move("n6"), order.key("n1", AnswerKey.DOWN))
        assertEquals(AnswerAction.Move("n7"), order.key("n6", AnswerKey.NEXT))
        assertEquals("Tab leaves the grid after the last tile", AnswerAction.Ignore, order.key("n7", AnswerKey.NEXT))
        assertEquals("down from a short last row lands on the last digit", AnswerAction.Move("n7"), order.key("n2", AnswerKey.DOWN))
    }

    @Test
    fun enterAndSpaceChooseWhatIsFocusedAndEscapeLeaves() {
        val order = AnswerOrder(listOf("yes", "no", "water"), 3)
        assertEquals(AnswerAction.Choose("no"), order.key("no", AnswerKey.SELECT))
        assertEquals(AnswerAction.Leave, order.key("yes", AnswerKey.ESCAPE))
        assertEquals("a key the flow does not know is handed back", AnswerAction.Ignore, order.key("nothing", AnswerKey.SELECT))
    }

    @Test
    fun theWholeBarIsWalkedByKeysToAnAnswer() {
        val ids = answerBarIds(signing = false, statement = false, next = false, rights = false, controls = true, moreOpen = true)
        val order = AnswerOrder(ids, 7)
        // Tab past the trio to "wrong", then Space chooses it.
        val keys = List(6) { AnswerKey.NEXT } + listOf(AnswerKey.SELECT)
        val actions = walk(order, "yes", keys)
        assertEquals(AnswerAction.Choose("wrong"), actions.last())
        assertEquals(6, actions.count { it is AnswerAction.Move })
    }

    @Test
    fun theMoreControlOpensTheTrioWithOneKey() {
        val order = AnswerOrder(answerBarIds(signing = false, statement = false, next = false, rights = false, controls = true, moreOpen = true), 6)
        assertEquals(AnswerAction.Choose(MORE), order.key(MORE, AnswerKey.SELECT))
        assertEquals(AnswerAction.Move("dont_know"), order.key(MORE, AnswerKey.NEXT))
    }

    @Test
    fun dwellMovesOneControlAtATimeAndNeverSkips() {
        val order = AnswerOrder(listOf("yes", "no", "water"), 3)
        val scan = SwitchScan(order, SwitchAccess.DWELL)
        val start = scan.start(0)
        val (early, none) = scan.tick(start, SWITCH_DWELL_MS - 1)
        assertNull("a dwell that has not finished leaves the highlight alone", none)
        assertEquals(0, early.at)

        val (second, first) = scan.tick(early, SWITCH_DWELL_MS)
        assertEquals(SwitchAction.Move("no"), first)
        val (third, next) = scan.tick(second, SWITCH_DWELL_MS * 2)
        assertEquals(SwitchAction.Move("water"), next)
        val (_, wrapped) = scan.tick(third, SWITCH_DWELL_MS * 3)
        assertEquals("after the last control the scan starts again", SwitchAction.Move("yes"), wrapped)
    }

    @Test
    fun aDwellPressChoosesWhatIsLit() {
        val order = AnswerOrder(listOf("yes", "no", "water"), 3)
        val scan = SwitchScan(order, SwitchAccess.DWELL)
        val (lit, chosen) = scan.press(scan.start(0), 10)
        assertEquals(SwitchAction.Choose("yes"), chosen)
        val (moved, _) = scan.tick(lit, SWITCH_DWELL_MS)
        assertEquals(1, moved.at)
        assertEquals("after the dwell the next press chooses the next control", SwitchAction.Choose("no"), scan.press(moved, SWITCH_DWELL_MS + 1).second)
    }

    @Test
    fun aStepSwitchTapAdvancesAndAHoldChooses() {
        val order = AnswerOrder(listOf("yes", "no", "water"), 3)
        val scan = SwitchScan(order, SwitchAccess.STEP)
        val (pressed, none) = scan.press(scan.start(0), 100)
        assertNull(none)
        val (advanced, tap) = scan.up(pressed, 200)
        assertEquals(SwitchAction.Move("no"), tap)
        assertEquals(1, advanced.at)

        val (heldDown, _) = scan.press(advanced, 1_000)
        val (_, hold) = scan.up(heldDown, 1_000 + SWITCH_HOLD_MS)
        assertEquals(SwitchAction.Choose("no"), hold)
    }

    @Test
    fun aSwitchOffIgnoresTheSwitch() {
        val order = AnswerOrder(listOf("yes", "no"), 2)
        val scan = SwitchScan(order, SwitchAccess.OFF)
        assertNull(scan.press(scan.start(0), 10).second)
        assertNull(scan.tick(scan.start(0), SWITCH_DWELL_MS * 4).second)
    }

    @Test
    fun theSwitchWalksTheSameBarTheKeyboardDoes() {
        val ids = answerBarIds(signing = false, statement = false, next = false, rights = false, controls = true, moreOpen = true)
        val order = AnswerOrder(ids, 7)
        val scan = SwitchScan(order, SwitchAccess.STEP)
        var state = scan.start(0)
        val seen = mutableListOf(scan.id(state))
        // Four taps of one switch, each press and release, one control at a time.
        repeat(4) { i ->
            val (down, _) = scan.press(state, i * 1_000L + 10)
            val (up, action) = scan.up(down, i * 1_000L + 100)
            state = up
            assertTrue("a tap moves exactly one control: ${action}", action is SwitchAction.Move)
            seen += scan.id(state)
        }
        assertEquals(ids.take(5), seen)
    }
}
