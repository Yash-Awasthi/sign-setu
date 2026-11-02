package app.signsetu

import android.net.Uri
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
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The record as a forensic examiner would test it, on the real encrypted database file: the file is unreadable
 * without the key, a sealed report checks out, and an edit to a stored row or an inserted row is each caught. */
@RunWith(AndroidJUnit4::class)
class ForensicExportDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as SignSetuApp
    private val name = "forensic-test.db"
    private val key = ByteArray(32) { (it * 7 + 3).toByte() }

    @After
    fun clean() { app.deleteDatabase(name) }

    @Test
    fun anEditedRowAndAnInsertedRowAreEachCaught() = runBlocking {
        PDFBoxResourceLoader.init(app)
        app.deleteDatabase(name)
        val db = SessionDatabase.open(app, key, fileName = name)
        val repo = SessionRepository(db.dao(), provenance = { "forensic test" }, signer = { DeviceSeal.sign(it) })
        val id = repo.start(Profile.NOT_SURE, "en")
        repo.log(id, EventType.ANSWER_CARD, EventSource.SUBJECT, "en", cardId = "yes")
        repo.log(id, EventType.ANSWER_TEXT, EventSource.SUBJECT, "en", text = "near the school")
        repo.log(id, EventType.ANSWER_DRAWING, EventSource.SUBJECT, "en", drawingPng = byteArrayOf(1, 2, 3, 4))

        // At rest: no SQLite header and no answer text anywhere in the file.
        db.close()
        val bytes = app.getDatabasePath(name).readBytes()
        assertFalse("the file has a plain SQLite header", String(bytes, 0, 15, Charsets.US_ASCII) == "SQLite format 3")
        assertFalse("answer text is visible in the file", String(bytes, Charsets.ISO_8859_1).contains("near the school"))

        val reopened = SessionDatabase.open(app, key, fileName = name)
        val repo2 = SessionRepository(reopened.dao(), signer = { DeviceSeal.sign(it) })
        val (pdf, pass) = export(repo2, id)
        assertEquals(SealCheck.MATCHES_DEVICE, checkReport(app, Uri.fromFile(pdf), pass, repo2))
        assertEquals("away from the phone the signature alone is checked", SealCheck.SIGNATURE_VALID, checkReport(app, Uri.fromFile(pdf), pass, null))

        // An edited stored row, behind the app's back: the seal line is unchanged but the row no longer matches its seal.
        reopened.openHelper.writableDatabase.execSQL("UPDATE event SET text = 'far from the school' WHERE text = 'near the school'")
        assertEquals(SealCheck.RECORD_ALTERED, checkReport(app, Uri.fromFile(pdf), pass, repo2))

        // An inserted row changes the seal line itself.
        repo2.log(id, EventType.NOTE, EventSource.OFFICER, "en", text = "added later")
        assertEquals(SealCheck.DIFFERS_FROM_DEVICE, checkReport(app, Uri.fromFile(pdf), pass, repo2))

        // The certificate printed now says the chain is broken.
        val (again, pass2) = export(repo2, id)
        val text = PDDocument.load(again, pass2).use { PDFTextStripper().getText(it) }
        assertTrue("the new certificate reports the break:\n$text", "BROKEN" in text)
        reopened.close()
    }

    private suspend fun export(repo: SessionRepository, id: String): Pair<File, String> {
        val content = app.container.content.load()
        lateinit var vm: SessionViewModel
        instrumentation.runOnMainSync { vm = SessionViewModel(id, repo, content, "en", app.container.report) }
        val watcher = CoroutineScope(Dispatchers.Default).apply { launch { vm.ui.collect {} } }
        val total = repo.eventsNow(id).size
        val ui: SessionUi = withTimeout(10_000) { vm.ui.first { it != null && it.events.size == total }!! }
        val res = app.resources
        val detail = recordDetail(res, content, "en", ui.events) { eventDetail(res, it) }
        return vm.exportReport(reportText(res, ui, content), detail).also { watcher.cancel() }
    }
}
