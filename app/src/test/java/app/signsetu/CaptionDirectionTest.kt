package app.signsetu

import androidx.compose.ui.text.style.TextDirection
import app.signsetu.ui.session.captionDirection
import org.junit.Assert.assertEquals
import org.junit.Test

/** The subject's caption and answer bar take their direction from the language, not from the LTR call layout. */
class CaptionDirectionTest {

    @Test
    fun an_urdu_caption_reads_right_to_left() {
        assertEquals(TextDirection.Rtl, captionDirection("ur"))
    }

    @Test
    fun an_english_caption_reads_left_to_right() {
        assertEquals(TextDirection.Ltr, captionDirection("en"))
    }

    @Test
    fun a_regional_urdu_tag_uses_its_language() {
        assertEquals(TextDirection.Rtl, captionDirection("ur-PK"))
    }

    @Test
    fun a_hindi_caption_reads_left_to_right() {
        assertEquals(TextDirection.Ltr, captionDirection("hi"))
    }
}
