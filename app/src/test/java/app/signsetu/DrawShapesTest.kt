package app.signsetu

import androidx.compose.ui.geometry.Offset
import app.signsetu.ui.session.DrawTool
import app.signsetu.ui.session.drawnLines
import app.signsetu.ui.session.shapeSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The drawing board's shapes: a drag leaves the lines that the screen, the PNG and undo all work from. */
class DrawShapesTest {

    private val from = Offset(0.2f, 0.3f)
    private val to = Offset(0.8f, 0.7f)
    // A real finger wobbles through points; a shape reads only where the drag began and ended.
    private val trail = listOf(from, Offset(0.5f, 0.9f), Offset(0.1f, 0.1f), to)

    @Test
    fun thePenKeepsEveryPointOfTheDrag() {
        assertEquals(listOf(trail), drawnLines(DrawTool.PEN, trail))
    }

    @Test
    fun theLineJoinsTheTwoEndsAndIgnoresTheWobble() {
        assertEquals(listOf(listOf(from, to)), drawnLines(DrawTool.LINE, trail))
    }

    @Test
    fun theRectangleIsTheBoxBetweenTheEndsAndIsClosed() {
        val rect = drawnLines(DrawTool.RECT, trail).single()
        assertEquals(5, rect.size)
        assertEquals(listOf(from, Offset(to.x, from.y), to, Offset(from.x, to.y), from), rect)
        assertEquals("closed, so the last side is drawn", rect.first(), rect.last())
    }

    @Test
    fun theOvalFillsTheBoxAndIsClosed() {
        val oval = drawnLines(DrawTool.OVAL, trail).single()
        assertTrue("a smooth outline", oval.size > 12)
        assertEquals(oval.first(), oval.last())
        val cx = (from.x + to.x) / 2
        val cy = (from.y + to.y) / 2
        val rx = (to.x - from.x) / 2
        val ry = (to.y - from.y) / 2
        for (p in oval) {
            val on = ((p.x - cx) / rx) * ((p.x - cx) / rx) + ((p.y - cy) / ry) * ((p.y - cy) / ry)
            assertTrue("every point sits on the ellipse, not in the box: $p", abs(on - 1f) < 0.01f)
        }
        assertEquals("reaches the left edge", from.x, oval.minOf { it.x }, 1e-3f)
        assertEquals("reaches the right edge", to.x, oval.maxOf { it.x }, 1e-3f)
        assertEquals("reaches the top edge", from.y, oval.minOf { it.y }, 1e-3f)
        assertEquals("reaches the bottom edge", to.y, oval.maxOf { it.y }, 1e-3f)
    }

    @Test
    fun theArrowIsAShaftWithATwoLineHeadAtTheTip() {
        val lines = drawnLines(DrawTool.ARROW, trail)
        assertEquals(3, lines.size)
        assertEquals("the shaft runs from the drag's start to its end", listOf(from, to), lines[0])
        val ux = (to.x - from.x) / shapeSpan(listOf(from, to)).coerceAtLeast(1e-4f)
        lines.drop(1).forEach { head ->
            assertEquals("each head starts at the tip", to, head.first())
            val back = head.last()
            assertTrue("the head points back along the shaft", (back.x - to.x) * ux <= 0f)
        }
        val sides = lines.drop(1).map { (it.last().x - to.x) * (to.y - from.y) - (it.last().y - to.y) * (to.x - from.x) }
        assertTrue("one head line each side of the shaft: $sides", sides[0] * sides[1] < 0f)
    }

    @Test
    fun shapesAreFractionsSoTheySurviveAnyCanvasSize() {
        for (tool in DrawTool.entries) {
            for (line in drawnLines(tool, trail)) {
                for (p in line) {
                    assertTrue("$tool stays inside 0..1: $p", p.x in 0f..1f && p.y in 0f..1f)
                }
            }
        }
    }

    @Test
    fun aTapLeavesNoShapeAndNoEmptyDragLeavesNothing() {
        for (tool in DrawTool.entries - DrawTool.PEN) {
            assertTrue("$tool from a tap", drawnLines(tool, listOf(from)).isEmpty())
            assertTrue("$tool from a wobble", drawnLines(tool, listOf(from, Offset(from.x + 0.004f, from.y))).isEmpty())
        }
        assertTrue("no drag at all", drawnLines(DrawTool.PEN, emptyList()).isEmpty())
    }

    @Test
    fun aZeroLengthShapeIsStillNumbers() {
        for (tool in DrawTool.entries - DrawTool.PEN) {
            for (line in drawnLines(tool, listOf(from, from, from))) {
                assertTrue("$tool has no NaN or infinity", line.all { it.x.isFinite() && it.y.isFinite() })
            }
        }
    }
}
