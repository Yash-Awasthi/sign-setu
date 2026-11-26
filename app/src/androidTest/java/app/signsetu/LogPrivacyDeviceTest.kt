package app.signsetu

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.data.session.DatabaseKey
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.SessionDatabase
import app.signsetu.data.session.SessionRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.UUID

/** The app's own log buffer, after a scripted session, holds none of the words a session stores: no transcript,
 * no name, no absolute path. A marker separates this test's own lines from whatever the process logged earlier. */
@RunWith(AndroidJUnit4::class)
class LogPrivacyDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as SignSetuApp
    private val dbName = "log-privacy-test.db"
    private var db: SessionDatabase? = null
    private var dek: DatabaseKey.Unwrapped? = null

    @After
    fun clean() {
        try {
            db?.close()
        } finally {
            db = null
            try {
                dek?.close()
            } finally {
                dek = null
                app.deleteDatabase(dbName)
            }
        }
    }

    @Test
    fun theLogBufferHoldsNoTranscriptNoNameAndNoPath() = runBlocking<Unit> {
        app.deleteDatabase(dbName)
        val marker = "marker-" + UUID.randomUUID()
        Log.i(PROBE_TAG, marker)

        val key = DatabaseKey(app).unwrap()
        dek = key
        val opened = SessionDatabase.open(app, key.key, fileName = dbName)
        db = opened
        val repo = SessionRepository(opened.dao())
        val id = repo.start(Profile.NOT_SURE, "en")
        repo.log(id, EventType.ANSWER_TEXT, EventSource.SUBJECT, "en", text = TRANSCRIPT)
        repo.log(id, EventType.ANSWER_TEXT, EventSource.SUBJECT, "en", text = NAME)
        repo.end(id, "session with $NAME")

        // A silent write would make the log scan pass for the wrong reason.
        val stored = repo.eventsNow(id).mapNotNull { it.text }
        assertTrue("the transcript is really stored, not a no-op that would fake the log result", stored.any { it == TRANSCRIPT })
        assertTrue("the name is really stored, not a no-op that would fake the log result", stored.any { it == NAME })

        val proc = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "--pid=${android.os.Process.myPid()}"))
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val drainOut = Thread { proc.inputStream.use { it.copyTo(stdout) } }
        val drainErr = Thread { proc.errorStream.use { it.copyTo(stderr) } }
        drainOut.start()
        drainErr.start()
        val exit = proc.waitFor()
        drainOut.join()
        drainErr.join()

        val raw = stdout.toByteArray().toString(Charsets.UTF_8)
        val err = stderr.toByteArray().toString(Charsets.UTF_8)
        val lines = raw.lines()
        val at = lines.indexOfFirst { it.contains(marker) }
        if (at < 0) {
            fail("UNPROVABLE: the marker line is missing from logcat output (exit=$exit, ${lines.size} lines); stderr=$err")
        }
        val sliceLines = lines.drop(at + 1).map { line -> line.trimEnd() }.dropLastWhile { it.isEmpty() }
        val slice = sliceLines.joinToString("\n")
        if (slice.isBlank()) {
            fail("UNPROVABLE: nothing was logged between the marker and the read; stderr=$err")
        }
        println("LOG-PRIVACY-SLICE-LINES=${sliceLines.size}")

        val lower = slice.lowercase()
        for (needle in SECRETS) {
            assertFalse(
                "the app logged \"$needle\" after the marker:\n" + sliceLines.filter { it.lowercase().contains(needle.lowercase()) }.joinToString("\n"),
                lower.contains(needle.lowercase()),
            )
        }

        val pathLine = Regex("""(/[A-Za-z0-9._-]+){2,}""")
        val driveLine = Regex("""[A-Za-z]:\\""")
        val pathHits = sliceLines.filter { pathLine.containsMatchIn(it) || driveLine.containsMatchIn(it) }
        assertTrue(
            "an absolute path appeared in the app's own log after the marker:\n" + pathHits.joinToString("\n"),
            pathHits.isEmpty(),
        )
    }

    private companion object {
        const val PROBE_TAG = "SignSetuPrivacyProbe"
        const val TRANSCRIPT = "Zubair wants the mango tree"
        const val NAME = "Zubair"
        val SECRETS = listOf("Zubair", "mango tree", "wants the")
    }
}
