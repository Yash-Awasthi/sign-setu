package app.signsetu

import app.signsetu.ui.theme.motionEnabled
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The low-motion switch stops decoration but never the phone itself; signing is driven apart from it. */
class MotionTest {
    @Test
    fun decoration_runs_only_with_motion_on() {
        assertTrue(motionEnabled(reduceMotion = false, animationsOn = true))
    }

    @Test
    fun the_officer_switch_wins_over_the_phone() {
        assertFalse(motionEnabled(reduceMotion = true, animationsOn = true))
    }

    @Test
    fun the_phone_removing_animations_is_enough() {
        assertFalse(motionEnabled(reduceMotion = false, animationsOn = false))
    }

    @Test
    fun both_off_stays_off() {
        assertFalse(motionEnabled(reduceMotion = true, animationsOn = false))
    }
}
