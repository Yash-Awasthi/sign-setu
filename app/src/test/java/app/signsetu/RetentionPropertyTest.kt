package app.signsetu

import app.signsetu.data.session.Retention
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The retention rule over every start time and every window, by property rather than by example: exactly one moment
 * of deletion at the boundary, never a deletion inside the window, and never an undeletion as the clock moves on. */
class RetentionPropertyTest {
    private val hour = Retention.HOUR_MS
    private val windows = app.signsetu.data.settings.AppSettings.RETENTION_CHOICES.filter { it > 0 }

    @Test
    fun theWindowsOfferedAreTheOnesTheRuleWasTestedFor() {
        assertEquals(listOf(4, 8, 12, 24, 48, 72), windows)
    }

    /** Any start time, any window: on the boundary the session is still there, one millisecond later it is not. */
    @Test
    fun exactlyOneDeletionAtTheBoundaryForAnyStartAndAnyWindow() {
        val rng = Random(20_261_124)
        repeat(5_000) {
            val idle = rng.nextLong(-1_000_000_000_000L, 1_000_000_000_000L)
            val hours = windows.random(rng)
            val boundary = idle + hours * hour
            assertFalse("kept exactly on the boundary: idle $idle, $hours h", Retention.expired(idle, boundary, hours))
            assertTrue("deleted one millisecond later: idle $idle, $hours h", Retention.expired(idle, boundary + 1, hours))
        }
    }

    @Test
    fun aSessionInsideItsWindowNeverGoesWhateverTheClockSays() {
        val rng = Random(7)
        repeat(2_000) {
            val idle = rng.nextLong(-1_000_000_000_000L, 1_000_000_000_000L)
            val hours = windows.random(rng)
            val now = idle + rng.nextLong(0, hours * hour + 1)
            assertFalse("idle $idle, $hours h, now $now", Retention.expired(idle, now, hours))
        }
    }

    @Test
    fun theClockMovingOnNeverBringsASessionBack() {
        val rng = Random(11)
        repeat(2_000) {
            val idle = rng.nextLong(0, 1_000_000_000_000L)
            val hours = windows.random(rng)
            val now = idle + rng.nextLong(hours * hour, 5_000 * hour)
            assertTrue("expired by now $now", Retention.expired(idle, now, hours))
            assertTrue("still expired later", Retention.expired(idle, now + rng.nextLong(0, 5_000 * hour), hours))
        }
    }

    @Test
    fun zeroHoursKeepsEverythingAndNegativeHoursIsTreatedTheSameWay() {
        val rng = Random(13)
        repeat(500) {
            val idle = rng.nextLong(0, 1_000_000_000_000L)
            val now = idle + rng.nextLong(0, 10_000 * hour)
            assertFalse(Retention.expired(idle, now, 0))
            assertFalse(Retention.expired(idle, now, -24))
        }
    }

    /** A sweep deletes exactly the sessions the rule names, whatever mix of old, new and boundary cases it is given. */
    @Test
    fun aSweepDeletesExactlyTheSessionsTheRuleNames() {
        val rng = Random(23)
        repeat(300) {
            val hours = windows.random(rng)
            val now = rng.nextLong(0, 1_000_000_000_000L)
            val idle = List(40) { rng.nextLong(now - 200 * hour, now + 200 * hour) }
            val named = idle.filter { Retention.expired(it, now, hours) }
            val byCutoff = idle.filter { it < Retention.cutoff(now, hours) }
            assertEquals("the rule and the cutoff it hands the database agree", named, byCutoff)
            assertTrue("nothing on or inside the window is named", named.none { it >= Retention.cutoff(now, hours) })
        }
    }

    /** The idle moment: the end; else the last row while open; else the start. */
    @Test
    fun idleCountsFromTheEndThenTheLastRowThenTheStart() {
        assertEquals(500L, Retention.idleSince(startedAt = 100, endedAt = 500, lastRowAt = 400))
        assertEquals(400L, Retention.idleSince(startedAt = 100, endedAt = null, lastRowAt = 400))
        assertEquals(100L, Retention.idleSince(startedAt = 100, endedAt = null, lastRowAt = null))
    }
}
