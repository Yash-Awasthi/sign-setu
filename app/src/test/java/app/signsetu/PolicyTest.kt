package app.signsetu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyTest {

    @Test
    fun buildExpiresAfterOneHundredAndEightyDays() {
        assertFalse(buildExpired(todayEpochDay = 1000 + 180, builtEpochDay = 1000))
        assertTrue(buildExpired(todayEpochDay = 1000 + 181, builtEpochDay = 1000))
    }

    @Test
    fun retentionShredsOnlyWhenTheNewestSessionIsTooOld() {
        val hour = 3_600_000L
        assertFalse("no sessions yet", allSessionsExpired(nowMs = 100 * hour, newestSessionAt = 0, retentionHours = 24))
        assertFalse(allSessionsExpired(nowMs = 100 * hour, newestSessionAt = 76 * hour, retentionHours = 24))
        assertTrue(allSessionsExpired(nowMs = 100 * hour + 1, newestSessionAt = 76 * hour, retentionHours = 24))
    }
}
