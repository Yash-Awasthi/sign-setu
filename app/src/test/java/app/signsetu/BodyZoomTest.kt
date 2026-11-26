package app.signsetu

import androidx.compose.ui.geometry.Offset
import app.signsetu.ui.session.BodySide
import app.signsetu.ui.session.BodyPart
import app.signsetu.ui.session.BodyTouch
import app.signsetu.ui.session.ZOOM_PARTS
import app.signsetu.ui.session.bodyBox
import app.signsetu.ui.session.bodyHit
import app.signsetu.ui.session.zoomFraction
import app.signsetu.ui.session.zoomPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The body map's close-ups: the part is enlarged, the finger's exact point maps back onto the figure, and that point
 * is what the record keeps. */
class BodyZoomTest {

    @Test
    fun eachSmallPartHasABoxOnTheFigure() {
        for (part in ZOOM_PARTS) {
            val box = bodyBox(part)
            assertNotNull("$part has a close-up", box)
            box!!
            assertTrue("$part is inside the figure: $box", box.left >= 0f && box.top >= 0f && box.right <= 1f && box.bottom <= 1f)
            assertTrue("$part has an area: $box", box.width > 0.02f && box.height > 0.02f)
        }
    }

    @Test
    fun theCloseUpIsTheSideThePersonTouched() {
        val left = bodyBox(BodyPart.HAND, near = 0.1f)!!
        val right = bodyBox(BodyPart.HAND, near = 0.9f)!!
        assertEquals("the viewer-left hand", 0.06f, left.left, 1e-4f)
        assertEquals("the viewer-right hand is its mirror", 1f - left.right, right.left, 1e-4f)
        assertEquals(1f - left.left, right.right, 1e-4f)
        assertTrue("a hand is on one side only", right.left > 0.5f)
    }

    @Test
    fun theCornersOfThePadAreTheCornersOfThePart() {
        val box = bodyBox(BodyPart.CHEST)!!
        assertEquals(Offset(box.left, box.top), zoomPoint(box, 0f, 0f))
        assertEquals(Offset(box.right, box.bottom), zoomPoint(box, 1f, 1f))
        val middle = zoomPoint(box, 0.5f, 0.5f)
        assertEquals(box.center.x, middle.x, 1e-4f)
        assertEquals(box.center.y, middle.y, 1e-4f)
    }

    @Test
    fun aPointOffThePadIsHeldAtItsEdgeNotThrownAway() {
        val box = bodyBox(BodyPart.BELLY)!!
        assertEquals(Offset(box.left, box.top), zoomPoint(box, -0.4f, -2f))
        assertEquals(Offset(box.right, box.bottom), zoomPoint(box, 3f, 7f))
    }

    @Test
    fun everyPointOfACloseUpStillReadsAsTheSamePart() {
        for (part in listOf(BodyPart.HAND, BodyPart.FOOT, BodyPart.CHEST, BodyPart.BELLY)) {
            for (near in listOf(0.1f, 0.9f)) {
                val box = bodyBox(part, near) ?: error("no box for $part")
                for (i in 1..9) for (j in 1..9) {
                    val p = zoomPoint(box, i / 10f, j / 10f)
                    val hit = bodyHit(BodySide.FRONT, p.x, p.y)
                    assertEquals("$part at ${i / 10f},${j / 10f} reads back as the same part", part, hit?.part)
                    assertTrue("the point is inside its box", box.contains(p))
                }
            }
        }
    }

    @Test
    fun theRefinedPointIsRecordedExactlyInsteadOfTheCoarseOne() {
        val box = bodyBox(BodyPart.CHEST)!!
        val coarse = BodyTouch(BodySide.FRONT, box.left + box.width * 0.5f, box.top + box.height * 0.5f, BodyPart.CHEST, true)
        val refined = bodyHit(BodySide.FRONT, zoomPoint(box, 0.62f, 0.37f).x, zoomPoint(box, 0.62f, 0.37f).y)!!
        assertTrue("the close-up moves the point, it does not round it", refined.x != coarse.x || refined.y != coarse.y)
        val back = BodyTouch.parse(refined.cue)!!
        assertEquals("the cue keeps the exact point to 3 decimals", refined.x, back.x, 5e-4f)
        assertEquals(refined.y, back.y, 5e-4f)
        assertEquals(BodyPart.CHEST, back.part)
        assertEquals("the chest keeps the person's side", true, back.personsLeft)
    }

    @Test
    fun theMarkOnThePadSitsWhereTheFingerWent() {
        val box = bodyBox(BodyPart.FOOT, near = 0.9f)!!
        val p = zoomPoint(box, 0.25f, 0.75f)
        val f = zoomFraction(box, p)
        assertEquals(0.25f, f.x, 1e-4f)
        assertEquals(0.75f, f.y, 1e-4f)
        // A mark from the other foot's copy is held at the pad's edge, never drawn outside it.
        val elsewhere = zoomFraction(box, Offset(0f, 0f))
        assertTrue(elsewhere.x in 0f..1f && elsewhere.y in 0f..1f)
    }
}
