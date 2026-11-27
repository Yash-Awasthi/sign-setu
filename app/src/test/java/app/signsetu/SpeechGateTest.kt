package app.signsetu

import app.signsetu.ui.components.SpeechGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The phone's voice is always cold on the first typed line of a stage; [SpeechGate] keeps that line and says it from
 * the init callback. The engine itself (TextToSpeech) is not testable on the JVM; the fake below stands in for it. */
class SpeechGateTest {
    private val said = mutableListOf<String>()
    private val gate = SpeechGate { said += it.text; true }

    private fun line(text: String) = SpeechGate.Request(text, null, 1f)

    @Test
    fun speak_before_init_keeps_the_line_and_init_says_it_once() {
        assertTrue(gate.speak(line("namaste")))
        assertEquals("namaste", gate.pending?.text)
        assertTrue(said.isEmpty())

        gate.initialized(true)
        assertEquals(listOf("namaste"), said)
        assertNull(gate.pending)

        gate.initialized(true)
        assertEquals(listOf("namaste"), said)
    }

    @Test
    fun second_speak_before_init_replaces_the_first() {
        gate.speak(line("first"))
        gate.speak(line("second"))
        assertEquals("second", gate.pending?.text)

        gate.initialized(true)
        assertEquals(listOf("second"), said)
    }

    @Test
    fun speak_after_init_says_at_once_and_drops_any_kept_line() {
        gate.speak(line("kept"))
        gate.initialized(true)
        said.clear()

        assertTrue(gate.speak(line("live")))
        assertEquals(listOf("live"), said)
        assertNull(gate.pending)
    }

    @Test
    fun stop_drops_the_kept_line() {
        gate.speak(line("typed"))
        gate.stop()
        assertNull(gate.pending)

        gate.initialized(true)
        assertTrue(said.isEmpty())
    }

    @Test
    fun failed_init_drops_the_kept_line_and_keeps_holding_later_lines() {
        gate.speak(line("typed"))
        gate.initialized(false)
        assertNull(gate.pending)
        assertTrue(said.isEmpty())
        assertFalse(gate.started)

        assertTrue(gate.speak(line("again")))
        assertEquals("again", gate.pending?.text)
    }

    @Test
    fun blank_text_is_never_kept() {
        assertFalse(gate.speak(line("  ")))
        assertNull(gate.pending)
    }
}
