package app.signsetu

import app.signsetu.ui.session.BodyPart
import app.signsetu.ui.session.BodySide
import app.signsetu.ui.session.BodyTouch
import app.signsetu.ui.session.markToRefine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** A close-up corrects the mark for its own part and side; the other side's mark is never the one it eats. */
class HurtMapRefineTest {

    private fun chest(side: BodySide, y: Float) = BodyTouch(side, 0.5f, y, BodyPart.CHEST, true)

    @Test
    fun theBackCloseUpCorrectsTheBackMarkAndLeavesTheFrontOne() {
        val front = chest(BodySide.FRONT, 0.2f)
        val back = chest(BodySide.BACK, 0.3f)
        val marks = mutableListOf(front, back)
        val at = markToRefine(marks, BodyPart.CHEST, BodySide.BACK)
        assertEquals("the back chest mark", 1, at)
        marks.removeAt(at)
        marks += chest(BodySide.BACK, 0.31f)
        assertEquals("the front chest mark is untouched", listOf(front, chest(BodySide.BACK, 0.31f)), marks)
        assertEquals("one place per part and side", 1, marks.count { it.part == BodyPart.CHEST && it.side == BodySide.BACK })
        assertEquals(1, marks.count { it.part == BodyPart.CHEST && it.side == BodySide.FRONT })
    }

    @Test
    fun theLastMarkIsTheWrongOneWhenTheOtherSideWasTouchedLast() {
        val back = chest(BodySide.BACK, 0.3f)
        val front = chest(BodySide.FRONT, 0.2f)
        val marks = mutableListOf(back, front)
        // The close-up targets its own mark, which is not the last one here.
        assertEquals(0, markToRefine(marks, BodyPart.CHEST, BodySide.BACK))
        assertNotEquals("the old removeAt(lastIndex) would hit the front mark", markToRefine(marks, BodyPart.CHEST, BodySide.BACK), marks.lastIndex)
        val old = marks.toMutableList().also { it.removeAt(it.lastIndex) }
        assertEquals("the old behaviour deletes the front chest", listOf(back), old)
    }

    @Test
    fun aCloseUpWithNoMarkYetRemovesNothing() {
        val marks = mutableListOf(chest(BodySide.BACK, 0.3f))
        assertEquals(-1, markToRefine(marks, BodyPart.CHEST, BodySide.FRONT))
        assertEquals("the other side's mark survives the first close-up", listOf(chest(BodySide.BACK, 0.3f)), marks)
    }

    @Test
    fun onlyTheCloseUpsPartIsCorrected() {
        val belly = BodyTouch(BodySide.BACK, 0.5f, 0.4f, BodyPart.BELLY, true)
        val chest = chest(BodySide.BACK, 0.3f)
        val marks = mutableListOf(belly, chest)
        assertEquals("the chest close-up leaves the belly alone", 1, markToRefine(marks, BodyPart.CHEST, BodySide.BACK))
    }
}
