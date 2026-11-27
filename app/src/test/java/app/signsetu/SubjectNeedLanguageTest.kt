package app.signsetu

import app.signsetu.data.content.Language
import app.signsetu.data.content.PRELOADED_CARDS
import app.signsetu.ui.session.finderPrompt
import app.signsetu.ui.session.needWord
import app.signsetu.ui.session.scriptOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A subject reads the stage in their own language: every word of the need row, and the finder's one line. */
class SubjectNeedLanguageTest {

    @Test
    fun a_need_with_no_card_still_reads_in_the_subject_language() {
        assertNull("family has no picture card, so its word must come from the bundle", shipped.cards["family"])
        for (language in shipped.languages) {
            val home = shipped.subjectUi["family"]?.get(language.code)
            assertEquals("family for ${language.code}", home, needWord("family", shipped, language.code))
            assertFalse("family for ${language.code} is not the English tile", needWord("family", shipped, language.code) == "Family" && language.code != "en")
        }
        assertEquals("குடும்பம்", needWord("family", shipped, "ta"))
        assertEquals("خاندان", needWord("family", shipped, "ur"))
    }

    @Test
    fun every_language_writes_the_need_row_heading_and_its_words() {
        for (key in listOf("family", "need_row_title", "back")) {
            for (language in shipped.languages) {
                val word = shipped.subjectText(key, language.code)
                assertTrue("$key for ${language.code} is missing", word != null)
                assertTrue("$key for ${language.code} is blank", !word.isNullOrBlank())
            }
        }
        assertEquals("मुझे चाहिए…", shipped.subjectText("need_row_title", "hi"))
        assertEquals("मोला चाही…", shipped.subjectText("need_row_title", "hne"))
        assertEquals("वापस", shipped.subjectText("back", "hi"))
    }

    @Test
    fun the_finder_prompt_carries_every_script_the_bundle_offers() {
        val line = finderPrompt(shipped.languages)
        assertNotNull("the finder shows a line before any language is chosen", line)
        val shown = line!!
        assertTrue("one readable line", !shown.contains('\n'))
        for (language in shipped.languages) {
            val script = scriptOf(language.hello)
            assertNotNull("${language.code} has no greeting to show", script)
            assertTrue(
                "${language.code} gets no line in its own script",
                shown.any { helloChar -> scriptOf(helloChar.toString()) == script },
            )
        }
    }

    @Test
    fun the_finder_prompt_falls_back_when_no_language_has_a_greeting() {
        assertNull(finderPrompt(emptyList()))
        assertNull(finderPrompt(listOf(Language("xx", "X", "X", ""))))
    }

    @Test
    fun every_preloaded_card_is_one_the_bundle_has() {
        val missing = PRELOADED_CARDS.filter { it !in shipped.cards }
        assertEquals("preload only names cards the bundle carries", emptyList<String>(), missing)
    }
}
