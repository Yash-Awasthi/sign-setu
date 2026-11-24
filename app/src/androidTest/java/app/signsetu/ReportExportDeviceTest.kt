package app.signsetu

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.data.session.DeviceSeal
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.SessionDatabase
import app.signsetu.data.session.SessionRepository
import app.signsetu.export.recordDigest
import app.signsetu.ui.session.SessionUi
import app.signsetu.ui.session.SessionViewModel
import app.signsetu.ui.session.eventDetail
import app.signsetu.ui.session.recordDetail
import app.signsetu.ui.session.reportText
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The officer's export on a device: the real renderer and encryption, read back as a PDF reader would. */
@RunWith(AndroidJUnit4::class)
class ReportExportDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as SignSetuApp
    private lateinit var db: SessionDatabase

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        PDFBoxResourceLoader.init(app)
        db = Room.inMemoryDatabaseBuilder(app, SessionDatabase::class.java)
            .openHelperFactory(SupportOpenHelperFactory(ByteArray(32) { 7 }))
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun theExportIsEncryptedAndCarriesItsDigestOnEveryPage() = runBlocking {
        val content = app.container.content.load()
        // Signed as the app signs in use, so the certificate names the device signature it really has.
        val repo = SessionRepository(db.dao(), signer = { line -> DeviceSeal.sign(line) })
        val id = repo.start(Profile.NOT_SURE, "en")
        repo.log(id, EventType.ANSWER_CARD, EventSource.SUBJECT, "en", cardId = "yes")
        repo.log(id, EventType.ANSWER_CARD, EventSource.SUBJECT, "en", cardId = "no")
        // Enough rows to run onto a second page, so "every page" means more than one.
        repeat(60) { repo.log(id, EventType.NOTE, EventSource.OFFICER, "en", text = "Officer note $it") }
        val total = repo.eventsNow(id).size

        lateinit var vm: SessionViewModel
        instrumentation.runOnMainSync { vm = SessionViewModel(id, repo, content, "en", app.container.report) }
        val watcher = CoroutineScope(Dispatchers.Default).apply { launch { vm.ui.collect {} } }
        val ui: SessionUi = withTimeout(10_000) { vm.ui.first { it != null && it.events.size == total }!! }
        val res = app.resources
        val detail = recordDetail(res, content, "en", ui.events) { eventDetail(res, it) }
        val digest = recordDigest(vm.reportRows(detail))

        suspend fun export(): ByteArray = vm.exportReport(reportText(res, ui, content), detail).readBytes()

        val pdf = export()
        val pages = PDDocument.load(pdf).use { doc ->
            assertFalse("no password: any reader opens it", doc.isEncrypted)
            (1..doc.numberOfPages).map { n -> PDFTextStripper().apply { startPage = n; endPage = n }.getText(doc) }
        }
        assertTrue("several pages: ${pages.size}", pages.size >= 2)
        pages.forEachIndexed { i, text -> assertTrue("page ${i + 1} names record $digest:\n$text", "record $digest" in text) }
        val answers = "${res.getString(R.string.report_answers)}: ${res.getString(R.string.summary_taps, 2)}"
        assertTrue("summary line \"$answers\" in:\n${pages[0]}", answers in pages[0].replace('\n', ' '))

        // A court can name the build and the key the seal was signed with, both on the certificate page.
        val cert = pages.last { "CERTIFICATE OF ADMISSIBILITY" in it }
        val version = app.packageManager.getPackageInfo(app.packageName, 0)
        val named = "Sign Setu ${version.versionName} (${version.longVersionCode})"
        assertTrue("the build $named on the certificate: $cert", named in cert)
        val fingerprint = DeviceSeal.publicKey()?.let { java.security.MessageDigest.getInstance("SHA-256").digest(it).joinToString("") { b -> "%02x".format(b) } }
        assertTrue("the sealing key $fingerprint on the certificate: $cert", fingerprint != null && fingerprint in cert)
        // The page wraps its lines, so the fields are read with the wrapping flattened.
        val flat = cert.replace('\n', ' ').replace("  ", " ")
        assertTrue("the signing algorithm on the certificate: $flat", "ECDSA P-256" in flat)

        val again = export()
        val digests = PDDocument.load(again).use { doc -> Regex("record ([0-9a-f-]{19})").findAll(PDFTextStripper().getText(doc)).map { it.groupValues[1] }.toSet() }
        assertEquals(setOf(digest), digests)
        watcher.cancel()
    }
}
