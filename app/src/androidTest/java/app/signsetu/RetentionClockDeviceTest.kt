package app.signsetu

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.Retention
import app.signsetu.data.session.SessionDatabase
import app.signsetu.data.session.SessionRepository
import kotlinx.coroutines.flow.first
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicLong

/** The retention sweep on the encrypted database the app really uses, with the clock in the test's hand: what is
 * past its window goes, what is inside its window — including exactly on the boundary — stays. */
@RunWith(AndroidJUnit4::class)
class RetentionClockDeviceTest {

    private val hour = 3_600_000L
    private val now = AtomicLong(1_700_000_000_000L)
    private lateinit var db: SessionDatabase
    private lateinit var repo: SessionRepository

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SignSetuApp,
            SessionDatabase::class.java,
        ).openHelperFactory(SupportOpenHelperFactory(ByteArray(32) { 13 })).build()
        repo = SessionRepository(db.dao(), clock = { now.get() })
    }

    @After
    fun tearDown() = db.close()

    /** A finished session whose last activity was [idleHoursAgo] hours before the clock. */
    private suspend fun finished(idleHoursAgo: Long, title: String): String {
        val at = now.get() - idleHoursAgo * hour
        now.set(at)
        val id = repo.start(Profile.NOT_SURE, "en")
        repo.log(id, EventType.ANSWER_CARD, EventSource.SUBJECT, "en", cardId = "yes")
        repo.end(id, title)
        now.set(at + idleHoursAgo * hour)
        return id
    }

    @Test
    fun aSessionPastItsWindowGoesAndOneInsideItStays() = kotlinx.coroutines.runBlocking {
        val gone = finished(25, "twenty five hours idle")
        val kept = finished(23, "twenty three hours idle")
        assertEquals("both sessions are in the database before the sweep", 2, repo.saved().first().size)

        assertEquals("exactly one session is purged", 1, repo.purgeExpired(24))
        val left = repo.saved().first()
        assertEquals("the session inside the window is the only one left", listOf(kept), left.map { it.id })
        assertNull("the expired session's rows are gone with it", repo.eventsNow(gone).firstOrNull())
        assertTrue("the kept session still has its row", repo.eventsNow(kept).isNotEmpty())
    }

    @Test
    fun aSessionExactlyOnTheBoundaryIsKeptUntilTheNextSweep() = kotlinx.coroutines.runBlocking {
        val onBoundary = finished(24, "exactly twenty four hours idle")
        assertEquals("a session exactly at the window edge is not deleted yet", 0, repo.purgeExpired(24))
        now.set(now.get() + 1)
        assertEquals("one hour-millisecond later it is", 1, repo.purgeExpired(24))
        assertTrue(repo.saved().first().isEmpty())
        assertTrue(onBoundary.isNotEmpty())
    }

    @Test
    fun zeroHoursKeepsEverything() = kotlinx.coroutines.runBlocking {
        finished(500, "old but kept by the officer's setting")
        assertEquals(0, repo.purgeExpired(0))
        assertEquals(1, repo.saved().first().size)
    }

    /** The SQL the sweep runs and the rule the app states agree over a random mix of ends, last rows and windows —
     * including sessions sitting exactly on the boundary, which stay. */
    @Test
    fun aRandomSweepTakesExactlyTheSessionsTheRuleNames() = kotlinx.coroutines.runBlocking {
        val rng = kotlin.random.Random(31)
        val windows = listOf(4, 8, 12, 24, 48, 72)
        val base = now.get()
        val sessions = mutableListOf<Pair<String, Long>>()
        repeat(24) { i ->
            val start = base - rng.nextLong(1, 200) * hour
            now.set(start)
            val id = repo.start(Profile.NOT_SURE, "en")
            val lastRow = if (rng.nextBoolean()) {
                now.set(start + rng.nextLong(0, 20) * hour)
                repo.log(id, EventType.NOTE, EventSource.OFFICER, "en", text = "row $i")
                now.get()
            } else null
            val ended = if (rng.nextBoolean()) {
                now.set(start + rng.nextLong(0, 200) * hour)
                repo.end(id, "session $i")
                now.get()
            } else null
            sessions += id to Retention.idleSince(start, ended, lastRow)
        }
        val hours = windows.random(rng)
        // Half the runs sit exactly on a boundary, which must not delete.
        now.set(if (rng.nextBoolean()) sessions.first().second + hours * hour else base + rng.nextLong(0, 100) * hour)
        val expected = sessions.filter { (_, idle) -> Retention.expired(idle, now.get(), hours) }.map { it.first }.toSet()
        val swept = repo.purgeExpired(hours)
        val left = repo.saved().first().map { it.id }.toSet()
        assertEquals("as many sessions went as the rule names", expected.size, swept)
        assertEquals("and they are the same ones", expected, sessions.map { it.first }.toSet() - left)
    }

    @Test
    fun anOpenSessionIsJudgedByItsLastRowNotItsStart() = kotlinx.coroutines.runBlocking {
        val start = now.get() - 30 * hour
        now.set(start)
        val id = repo.start(Profile.NOT_SURE, "en")
        now.set(start + 10 * hour)
        repo.log(id, EventType.NOTE, EventSource.OFFICER, "en", text = "still talking")
        now.set(start + 30 * hour)
        assertEquals("open, but its last row is 20 hours old: inside the window", 0, repo.purgeExpired(24))
        now.set(start + 40 * hour)
        assertEquals("the last row is now 30 hours old", 1, repo.purgeExpired(24))
    }
}
