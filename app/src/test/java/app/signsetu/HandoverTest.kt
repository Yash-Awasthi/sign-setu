package app.signsetu

import app.signsetu.ui.session.ChordDetector
import app.signsetu.ui.session.HardKey
import app.signsetu.ui.session.HardKeys
import app.signsetu.ui.session.KeyMode
import app.signsetu.ui.session.TiltDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandoverTest {

    /** Feeds one gravity-y sample every 50 ms from [t0] to [t1]; returns the times it fired. */
    private fun TiltDetector.feed(gy: Float, t0: Long, t1: Long) = (t0..t1 step 50).filter { update(gy, it) }

    @Test
    fun tiltFiresOnceAfterTheDwell() {
        val tilt = TiltDetector()
        assertEquals(emptyList<Long>(), tilt.feed(7f, 0, 500))
        assertEquals(listOf(1150L), tilt.feed(-6f, 550, 3000))
    }

    @Test
    fun shortDipDoesNotFire() {
        val tilt = TiltDetector()
        tilt.feed(7f, 0, 500)
        assertEquals(emptyList<Long>(), tilt.feed(-6f, 550, 850))
        assertEquals(emptyList<Long>(), tilt.feed(7f, 900, 1000))
    }

    @Test
    fun tiltMustArmFirstAndRearmsOnlyAfterTopIsUpAgain() {
        val tilt = TiltDetector()
        assertEquals("starts top-down", emptyList<Long>(), tilt.feed(-6f, 0, 2000))
        tilt.feed(7f, 2050, 2100)
        assertEquals(1, tilt.feed(-6f, 2150, 4000).size)
        assertEquals("level is not top-up", emptyList<Long>(), tilt.feed(0f, 4050, 4100) + tilt.feed(-6f, 4150, 6000))
        tilt.feed(7f, 6050, 6100)
        assertEquals(1, tilt.feed(-6f, 6150, 8000).size)
    }

    @Test
    fun chordFiresOnceWithBothKeysHeld() {
        val chord = ChordDetector()
        chord.down(HardKey.UP, 0)
        chord.down(HardKey.DOWN, 100)
        assertFalse(chord.poll(1500))
        assertTrue(chord.poll(1600))
        assertFalse("once per hold", chord.poll(3000))
    }

    @Test
    fun oneKeyNeverFiresAndReleaseResets() {
        val chord = ChordDetector()
        chord.down(HardKey.UP, 0)
        assertFalse(chord.poll(10_000))
        chord.down(HardKey.DOWN, 10_000)
        chord.up(HardKey.DOWN)
        assertFalse(chord.poll(20_000))
        chord.down(HardKey.DOWN, 20_000)
        assertFalse(chord.poll(21_000))
        assertTrue(chord.poll(21_500))
    }

    @Test
    fun volumeKeysAreLeftToTheSystemUnlessSomethingIsReadyOrShown() {
        val keys = HardKeys()
        assertFalse(keys.consumes(HardKey.UP))
        keys.mode = KeyMode.OFFICER_READY
        assertTrue(keys.consumes(HardKey.UP))
        assertFalse(keys.consumes(HardKey.DOWN))
        keys.mode = KeyMode.STAGE
        assertTrue(keys.consumes(HardKey.UP) && keys.consumes(HardKey.DOWN))
    }
}
