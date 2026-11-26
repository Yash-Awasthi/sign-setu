package app.signsetu

import app.signsetu.ui.session.finderTour
import app.signsetu.ui.session.languageTiles
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A language the finder shows must be a language the finder walks; a tile skipped for its missing
 * recording is a person who never hears their own language and never sees their tile lit. */
class LanguageFinderTest {

    @Test
    fun the_tour_walks_every_tile_the_grid_shows() {
        assertEquals(languageTiles(shipped).map { it.code }, finderTour(shipped).map { it.code })
    }

    @Test
    fun every_declared_language_is_in_the_tour() {
        val declared = Regex("\"code\"\\s*:\\s*\"([a-z]+)\"")
            .findAll(File(ASSETS, "languages.json").readText())
            .map { it.groupValues[1] }
            .toSet()
        assertEquals(declared, finderTour(shipped).map { it.code }.toSet())
    }

    @Test
    fun a_language_without_a_recording_is_still_offered() {
        val steps = finderTour(shipped).associateBy { it.code }
        for (code in listOf("gon", "hlb")) {
            assertTrue("$code must be offered", steps.containsKey(code))
            assertNull("$code has no recording yet", steps.getValue(code).audio)
        }
    }
}
