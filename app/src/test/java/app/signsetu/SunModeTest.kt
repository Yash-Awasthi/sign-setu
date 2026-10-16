package app.signsetu

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.signsetu.ui.theme.SunAnswerColors
import app.signsetu.ui.theme.SunHysteresis
import app.signsetu.ui.theme.SunStageColors
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SunModeTest {

    @Test
    fun turnsOnOnlyAfterSteadyGlareAndOffOnlyAfterSteadyShade() {
        val sun = SunHysteresis()
        assertFalse(sun.update(20_000f, 0))
        assertFalse(sun.update(20_000f, 2_000))
        assertFalse("a 2 s burst is not glare", sun.update(500f, 2_100))
        assertFalse(sun.update(20_000f, 3_000))
        assertTrue(sun.update(20_000f, 6_000))
        assertTrue("between thresholds keeps the state", sun.update(5_000f, 30_000))
        assertTrue(sun.update(500f, 31_000))
        assertTrue("a 5 s dip is a passing shadow", sun.update(500f, 36_000))
        assertTrue(sun.update(5_000f, 36_100))
        assertTrue(sun.update(500f, 40_000))
        assertFalse(sun.update(500f, 50_000))
        assertFalse("between thresholds keeps the state", sun.update(5_000f, 90_000))
    }

    private fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    // WCAG 2.2 AA: 4.5:1 for text, 3:1 for shapes. Caption yellow on black is about 16:1, white on Yes 5.0:1, on No 5.6:1.
    @Test
    fun sunColoursMeetAa() {
        val stage = SunStageColors
        val answer = SunAnswerColors
        assertTrue(contrast(stage.onBubble, stage.bubble) >= 4.5f)
        assertTrue(contrast(stage.onHighlight, stage.highlight) >= 4.5f)
        assertTrue(contrast(answer.onYes, answer.yes) >= 4.5f)
        assertTrue(contrast(answer.onNo, answer.no) >= 4.5f)
        assertTrue(contrast(answer.onPause, answer.pause) >= 4.5f)
        assertTrue(contrast(answer.onUnsure, answer.unsure) >= 4.5f)
        assertTrue(contrast(stage.windowFrame, stage.skyBottom) >= 3f)
        assertTrue(contrast(stage.onHandle, stage.skyTop) >= 3f)
    }
}
