package app.signsetu

import androidx.compose.ui.text.AnnotatedString
import app.signsetu.ui.session.CaptionLight
import app.signsetu.ui.session.litWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.junit4.createComposeRule
import app.signsetu.ui.theme.SignSetuTheme

/** The owner's bug: on the call surface the wrong words lit, because a caption's offsets were applied to a different
 * line (the language finder's multi-script prompt, or the hint standing in for the caption). A light now carries the
 * text it was measured in and is dropped everywhere else. */
@RunWith(AndroidJUnit4::class)
class CallCaptionLightTest {
    @get:org.junit.Rule val rule = createComposeRule()

    private fun lit(text: String, light: CaptionLight?): AnnotatedString {
        var out: AnnotatedString? = null
        rule.setContent {
            SignSetuTheme { out = litWord(text, light?.inText(text)) }
        }
        rule.waitForIdle()
        return out!!
    }

    @Test
    fun aLightForAnotherLineLightsNothing() {
        // The caption "नमस्ते" was planned and lit; the call shows its own multi-script prompt instead.
        val prompt = "नमस्ते · నమస్కారం · நமஸ்காரம்"
        assertNull("the caption's offsets do not belong to the prompt", CaptionLight("नमस्ते", 0 until 5).inText(prompt))
        assertTrue("nothing on the prompt is lit", lit(prompt, CaptionLight("नमस्ते", 0 until 5)).spanStyles.isEmpty())
    }

    @Test
    fun aLightInItsOwnLineLightsThatWord() {
        val caption = "Meena is here"
        val light = CaptionLight(caption, 0 until 5)
        assertEquals(0 until 5, light.inText(caption))
        val styled = lit(caption, light)
        assertEquals("one word is lit", 1, styled.spanStyles.size)
        val span = styled.spanStyles.single()
        assertEquals("the first word is lit", "Meena", caption.substring(span.start, span.end))
    }

    @Test
    fun nothingIsLitWithoutAWord() {
        val none: CaptionLight? = null
        assertNull("no light at all", none?.inText("Meena is here"))
        assertNull("an empty range lights nothing", CaptionLight("Meena", IntRange.EMPTY).inText("Meena"))
        assertNull("a range past the end lights nothing", CaptionLight("Meena", 3 until 9).inText("Meena"))
        assertTrue(lit("Meena", CaptionLight("Meena", IntRange.EMPTY)).spanStyles.isEmpty())
    }
}
