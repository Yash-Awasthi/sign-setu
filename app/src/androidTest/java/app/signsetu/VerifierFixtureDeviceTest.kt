package app.signsetu

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.data.session.DeviceSeal
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.SessionDatabase
import app.signsetu.data.session.SessionRepository
import app.signsetu.export.EvidenceIntegrity.SealCheck
import app.signsetu.export.checkReport
import app.signsetu.ui.session.SessionUi
import app.signsetu.ui.session.SessionViewModel
import app.signsetu.ui.session.eventDetail
import app.signsetu.ui.session.recordDetail
import app.signsetu.ui.session.reportText
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exports one real report to the app's external files directory, so the host can check the same PDF with
 * `tools/security/verify_seal.py`: the app's verdict and the desktop verifier's must agree on one file. */
@RunWith(AndroidJUnit4::class)
class VerifierFixtureDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as SignSetuApp
    private lateinit var db: SessionDatabase

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        PDFBoxResourceLoader.init(app)
        db = Room.inMemoryDatabaseBuilder(app, SessionDatabase::class.java)
            .openHelperFactory(SupportOpenHelperFactory(ByteArray(32) { 11 }))
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun anExportedReportIsLeftForTheDesktopVerifier() = runBlocking {
        val content = app.container.content.load()
        val repo = SessionRepository(db.dao(), provenance = { "verifier fixture" }, signer = { DeviceSeal.sign(it) })
        val id = repo.start(Profile.NOT_SURE, "en")
        repo.log(id, EventType.QUESTION_SHOWN, EventSource.PRESET, "en", text = "What is your name?", itemId = "personal_name")
        repo.log(id, EventType.ANSWER_TEXT, EventSource.SUBJECT, "en", text = "Meena")
        repo.log(id, EventType.ANSWER_CARD, EventSource.SUBJECT, "en", cardId = "yes")
        val total = repo.eventsNow(id).size

        lateinit var vm: SessionViewModel
        instrumentation.runOnMainSync { vm = SessionViewModel(id, repo, content, "en", app.container.report) }
        val watcher = CoroutineScope(Dispatchers.Default).apply { launch { vm.ui.collect {} } }
        val ui: SessionUi = withTimeout(10_000) { vm.ui.first { it != null && it.events.size == total }!! }
        val res = app.resources
        val detail = recordDetail(res, content, "en", ui.events) { eventDetail(res, it) }
        val (file, pass) = vm.exportReport(reportText(res, ui, content), detail)
        watcher.cancel()

        // The app's own files directory: `adb exec-out run-as in.signsetu cat files/verify/report.pdf` reads it
        // from the host, so the desktop verifier checks exactly the file the app wrote.
        val out = File(app.filesDir, "verify/report.pdf").also { it.parentFile?.mkdirs() }
        file.copyTo(out, overwrite = true)
        File(out.parentFile, "passphrase.txt").writeText(pass)

        assertTrue("the fixture pdf was written: $out", out.length() > 0)
        assertEquals(
            "the app's own check on the file it just wrote",
            SealCheck.MATCHES_DEVICE,
            checkReport(app, Uri.fromFile(out), pass, repo),
        )
    }
}
