package app.signsetu

import androidx.compose.ui.geometry.Offset
import app.signsetu.camera.LandmarkFrame
import app.signsetu.sign.SignCapture
import app.signsetu.sign.segmentSigns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentTest {

    private fun hand(x: Float) = List(21) { Offset(x + it * 0.01f, 0.5f) }

    /** One frame every 33 ms; each script entry is a hand x position, or null for no hand. */
    private fun stream(vararg script: Float?) = script.mapIndexed { i, x -> LandmarkFrame(i * 33L, rightHand = x?.let(::hand)) }

    private fun moving(n: Int, from: Float = 0.2f) = Array<Float?>(n) { from + it * 0.03f }
    private fun still(n: Int, at: Float = 0.5f) = Array<Float?>(n) { at }
    private fun gone(n: Int) = arrayOfNulls<Float>(n)

    @Test
    fun pausesSplitSigns() {
        // Two signs separated by 0.5 s of resting hands (the jump into the rest pose still counts as moving), then hands gone.
        val frames = stream(*gone(5), *moving(15), *still(15), *moving(15), *gone(20))
        assertEquals(listOf(4..20, 34..49), segmentSigns(frames, final = false))
    }

    @Test
    fun shortHoldsStayInsideOneSign() {
        val frames = stream(*moving(10), *still(6), *moving(10), *gone(20))
        assertEquals(1, segmentSigns(frames, final = false).size)
    }

    @Test
    fun anOpenSignClosesOnlyWhenFinal() {
        val frames = stream(*gone(3), *moving(12))
        assertTrue(segmentSigns(frames, final = false).isEmpty())
        assertEquals(1, segmentSigns(frames, final = true).size)
    }

    @Test
    fun twitchesAreNotSigns() {
        val frames = stream(*gone(5), *moving(2), *gone(20))
        assertTrue(segmentSigns(frames, final = true).isEmpty())
    }

    @Test
    fun captureHandsOutEachSignOnceAndStopsAtItsCap() {
        val capture = SignCapture(limitMs = 3_000)
        val handed = stream(*gone(5), *moving(15), *gone(20), *moving(15)).flatMap { capture.add(it) }
        assertEquals(1, handed.size)
        assertEquals(1, capture.finish().size)
        assertTrue(capture.finish().isEmpty())
        val capped = SignCapture(limitMs = 1_000)
        val all = stream(*moving(60)).flatMap { capped.add(it) }
        assertTrue(capped.full)
        assertEquals(1, all.size)
        assertTrue(all[0].last().timestampMs <= 1_000 + 33)
    }
}
