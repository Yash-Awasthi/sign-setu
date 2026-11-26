package app.signsetu

import app.signsetu.ui.components.LazyResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Releasing a stage must not build the phone's voice first: the old lazy delegate was touched by release(), which
 * bound the TTS service on every screen exit only to shut it down again. */
class LazyResourceTest {
    @Test
    fun take_without_use_builds_nothing() {
        var built = 0
        val voice = LazyResource { built++; "voice" }
        assertNull(voice.take())
        assertEquals(0, built)
        assertNull(voice.peek())
    }

    @Test
    fun get_builds_once_and_take_hands_it_over() {
        var built = 0
        val voice = LazyResource { built++; "voice" }
        assertEquals("voice", voice.get())
        assertEquals("voice", voice.get())
        assertEquals(1, built)
        assertEquals("voice", voice.peek())
        assertEquals("voice", voice.take())
        assertEquals(1, built)
        assertNull(voice.peek())
        assertNull(voice.take())
    }
}
