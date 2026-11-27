package app.signsetu

import app.signsetu.ui.session.CaptionLight
import app.signsetu.ui.session.sameLight
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The idle caption light is stored only when the lit word changes: CaptionLight compares by identity, so the
 * stage's per-frame write is suppressed by text and range, not by the object. */
class CaptionLightDedupeTest {

    private val hint = "Tap the answer below"

    @Test
    fun theSameWordOfTheSameTextIsNotStoredAgain() {
        assertTrue(sameLight(CaptionLight(hint, 4..6), CaptionLight(hint, 4..6)))
    }

    @Test
    fun theSameInstanceIsTheSameLight() {
        val light = CaptionLight(hint, 4..6)
        assertTrue(sameLight(light, light))
    }

    @Test
    fun aDifferentRangeIsANewLight() {
        assertFalse(sameLight(CaptionLight(hint, 4..6), CaptionLight(hint, 8..12)))
    }

    @Test
    fun theSameRangeOfADifferentTextIsANewLight() {
        assertFalse(sameLight(CaptionLight(hint, 4..6), CaptionLight("Watch my hands", 4..6)))
    }

    @Test
    fun noLightAfterALitWordIsANewLight() {
        assertFalse(sameLight(null, CaptionLight(hint, 4..6)))
        assertFalse(sameLight(CaptionLight(hint, 4..6), null))
    }

    @Test
    fun noLightTwiceStaysNoLight() {
        assertTrue(sameLight(null, null))
    }
}
