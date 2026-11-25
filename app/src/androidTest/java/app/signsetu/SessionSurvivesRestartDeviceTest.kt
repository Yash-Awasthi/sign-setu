package app.signsetu

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.SessionDatabase
import app.signsetu.data.session.SessionRepository
import app.signsetu.export.EvidenceIntegrity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The phone dying mid-session, at the store: rows committed before the kill are on the disk, and a fresh open of the
 * same encrypted file under the same key returns the session, its rows and an intact seal chain. */
@RunWith(AndroidJUnit4::class)
class SessionSurvivesRestartDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as SignSetuApp
    private val name = "restart-test.db"
    private val key = ByteArray(32) { 9 }
    private lateinit var db: SessionDatabase

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        app.deleteDatabase(name)
        db = SessionDatabase.open(app, key, fileName = name)
    }

    @After
    fun tearDown() {
        if (this::db.isInitialized) db.close()
        app.deleteDatabase(name)
    }

    @Test
    fun aSessionAndItsSealChainSurviveADatabaseReopen() = runBlocking<Unit> {
        val repo = SessionRepository(db.dao())
        val id = repo.start(Profile.ISL_USER, "hi")
        repo.log(id, EventType.QUESTION_SHOWN, EventSource.OFFICER, "hi", text = "Are you hurt?", cardId = "yes")
        repo.log(id, EventType.ANSWER_CARD, EventSource.SUBJECT, "hi", cardId = "yes")
        repo.log(id, EventType.NOTE, EventSource.OFFICER, "hi", text = "Left hand wrapped")
        val before = repo.eventsNow(id)
        val head = repo.head(id)
        assertEquals("three rows were written", 3, before.size)
        assertTrue("every row is sealed", before.all { it.hash != null })
        assertNotNull(head)

        // The app is killed: the database is closed without an end, a title or a root being written.
        db.close()

        // The next unlock opens a fresh instance on the same file with the same key.
        db = SessionDatabase.open(app, key, fileName = name)
        val reopened = SessionRepository(db.dao())
        val session = reopened.session(id).first()
        assertNotNull("the session is still there", session)
        assertEquals("and still open", null, session!!.endedAt)
        val after = reopened.eventsNow(id)
        assertEquals(before.map { it.hash }, after.map { it.hash })
        assertEquals(before.map { it.text }, after.map { it.text })
        assertEquals("the chain head is unchanged", head, reopened.head(id))

        val chain = EvidenceIntegrity.verifyChain(session, after) { null }
        assertTrue("the seal chain still verifies at entry ${chain.brokenAt}", chain.intact)
        assertEquals("every sealed row was re-walked", 3, chain.sealed)
    }
}
