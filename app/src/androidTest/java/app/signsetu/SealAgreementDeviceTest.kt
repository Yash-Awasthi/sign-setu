package app.signsetu

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.DeviceSeal
import app.signsetu.data.session.SessionDatabase
import app.signsetu.data.session.SessionRepository
import app.signsetu.export.EvidenceIntegrity
import app.signsetu.export.SessionReport
import app.signsetu.export.checkReport
import app.signsetu.ui.session.SessionUi
import app.signsetu.ui.session.SessionViewModel
import app.signsetu.ui.session.eventDetail
import app.signsetu.ui.session.recordDetail
import app.signsetu.ui.session.reportText
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** The in-app "Check a report" and `tools/security/verify_seal.py` must read one exported report the same way. This
 * writes a report, asks the app's own check for its verdict, and leaves the PDF and that verdict under
 * `cacheDir/seal-agreement`, where `tools/security/seal_agreement.py` runs the off-phone check over the same bytes and
 * diffs every field. */
@RunWith(AndroidJUnit4::class)
class SealAgreementDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as SignSetuApp
    private lateinit var db: SessionDatabase

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        PDFBoxResourceLoader.init(app)
        db = SessionDatabase.open(app, ByteArray(32) { 9 }, fileName = "seal-agreement.db")
    }

    @After
    fun tearDown() {
        db.close()
        app.deleteDatabase("seal-agreement.db")
    }

    @Test
    fun theAppAndTheOffPhoneCheckerAgreeOnEverySealField() = runBlocking<Unit> {
        val content = app.container.content.load()
        val repo = SessionRepository(db.dao(), signer = { line -> DeviceSeal.sign(line) })
        val id = repo.start(Profile.NOT_SURE, "en")
        repo.log(id, EventType.ANSWER_CARD, EventSource.SUBJECT, "en", cardId = "yes")
        repo.log(id, EventType.NOTE, EventSource.OFFICER, "en", text = "Officer note")
        repo.log(id, EventType.ANSWER_CARD, EventSource.SUBJECT, "en", cardId = "no")
        val total = repo.eventsNow(id).size

        lateinit var vm: SessionViewModel
        instrumentation.runOnMainSync { vm = SessionViewModel(id, repo, content, "en", app.container.report) }
        val watcher = CoroutineScope(Dispatchers.Default).apply { launch { vm.ui.collect {} } }
        val ui: SessionUi = withTimeout(10_000) { vm.ui.first { it != null && it.events.size == total }!! }
        val res = app.resources
        val pdf: File = vm.exportReport(reportText(res, ui, content), recordDetail(res, content, "en", ui.events) { eventDetail(res, it) })
        watcher.cancel()

        // The seal the file carries, and the verdict the app reaches over that same file.
        val payload = PDDocument.load(pdf).use { doc -> doc.documentInformation.getCustomMetadataValue(SessionReport.SEAL_KEY) }
        assertNotNull("the export carries a seal", payload)
        val f = payload!!.split('|')
        assertEquals("a seal has its eight fields: $payload", 8, f.size)
        assertEquals("SS1", f[0])
        val verdict = checkReport(app, Uri.fromFile(pdf), repo)
        assertEquals("the app reads its own export as matching the phone", EvidenceIntegrity.SealCheck.MATCHES_DEVICE, verdict)

        // What the off-phone checker is asked to agree with, field by field.
        val out = File(app.cacheDir, "seal-agreement").apply { deleteRecursively(); mkdirs() }
        File(out, "report.pdf").writeBytes(pdf.readBytes())
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(android.util.Base64.decode(f[7], android.util.Base64.NO_WRAP))
            .joinToString("") { "%02x".format(it) }
        File(out, "app.json").writeText(
            JSONObject()
                .put("verdict", verdict!!.name)
                .put("session", f[1])
                .put("count", f[2].toInt())
                .put("head", f[3])
                .put("root", f[4])
                .put("digest", f[5])
                .put("fingerprint", fingerprint)
                .toString(2),
        )
        assertEquals("the report stays where the off-phone check will read it", 2, out.list()!!.size)
    }
}
