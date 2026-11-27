package app.signsetu

import app.signsetu.ui.session.isLatestTake
import app.signsetu.voice.WavPlayer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A finished answer's marker must not stop the answer that replaced it. AudioTrack only exists on a device, so the
 * decision itself is the seam: the callback releases only the track it fired for. The focus/mute gate and the
 * late-fetch ordering are pure decisions for the same reason; the AudioTrack, AudioManager and ON_STOP wiring stay
 * device-only. */
class WavPlayerRaceTest {
    private class Take(val name: String)

    @Test
    fun marker_for_the_playing_take_is_current() {
        val a = Take("A")
        assertTrue(WavPlayer.stillCurrent(a, a))
    }

    @Test
    fun marker_for_a_replaced_take_is_stale() {
        val a = Take("A")
        val b = Take("B")
        assertFalse(WavPlayer.stillCurrent(a, b))
        assertFalse(WavPlayer.stillCurrent(b, a))
    }

    @Test
    fun nothing_is_current_once_the_take_was_stopped() {
        assertFalse(WavPlayer.stillCurrent(Take("A"), null))
        assertTrue(WavPlayer.stillCurrent(null, null))
    }

    @Test
    fun a_granted_focus_with_unmuted_speech_plays() {
        assertTrue(WavPlayer.shouldPlay(focused = true, muted = false))
    }

    @Test
    fun muted_speech_never_plays_even_with_focus() {
        assertFalse(WavPlayer.shouldPlay(focused = true, muted = true))
    }

    @Test
    fun a_refused_focus_never_plays() {
        assertFalse(WavPlayer.shouldPlay(focused = false, muted = false))
        assertFalse(WavPlayer.shouldPlay(focused = false, muted = true))
    }

    @Test
    fun the_answer_last_tapped_is_the_one_that_plays() {
        assertTrue(isLatestTake(requested = 7L, latest = 7L))
    }

    @Test
    fun an_answer_that_resolved_after_a_newer_tap_is_dropped() {
        assertFalse(isLatestTake(requested = 7L, latest = 8L))
    }

    @Test
    fun an_answer_fetched_before_any_tap_is_dropped() {
        assertFalse(isLatestTake(requested = 7L, latest = null))
    }
}
