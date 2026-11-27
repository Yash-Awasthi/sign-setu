package app.signsetu

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.SessionDatabase
import app.signsetu.data.session.SessionRepository
import app.signsetu.export.writeReport
import app.signsetu.ui.session.SessionUi
import app.signsetu.ui.session.SessionViewModel
import app.signsetu.ui.session.eventDetail
import app.signsetu.ui.session.recordDetail
import app.signsetu.ui.session.reportText
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

/** When an export cannot be written, nothing half-made is left for the share sheet to send, and the officer is told.
 * Uses the real export path on the device: the export folder is made unwritable for the failure, and an orphaned part
 * file from an export that died mid-write is cleared by the next one. */
@RunWith(AndroidJUnit4::class)
class ExportFailureDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as SignSetuApp
    private lateinit var db: SessionDatabase
    private val exports get() = File(app.cacheDir, "exports")

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        db = SessionDatabase.open(app, ByteArray(32) { 5 }, fileName = "export-failure.db")
        exports.deleteRecursively()
    }

    @After
    fun tearDown() {
        exports.deleteRecursively()
        db.close()
        app.deleteDatabase("export-failure.db")
    }

    private suspend fun exported(): File {
        val content = app.container.content.load()
        val repo = SessionRepository(db.dao())
        val id = repo.start(Profile.NOT_SURE, "en")
        repo.log(id, EventType.ANSWER_CARD, EventSource.SUBJECT, "en", cardId = "yes")
        val total = repo.eventsNow(id).size
        lateinit var vm: SessionViewModel
        instrumentation.runOnMainSync { vm = SessionViewModel(id, repo, content, "en", app.container.report) }
        val watcher = CoroutineScope(Dispatchers.Default).apply { launch { vm.ui.collect {} } }
        val ui: SessionUi = withTimeout(10_000) { vm.ui.first { it != null && it.events.size == total }!! }
        val res = app.resources
        try {
            return vm.exportReport(reportText(res, ui, content), recordDetail(res, content, "en", ui.events) { eventDetail(res, it) })
        } finally {
            watcher.cancel()
        }
    }

    @Test
    fun aFolderThatCannotBeWrittenLeavesNoReportAndNoPart() = runBlocking<Unit> {
        // A plain file where the export folder belongs: every write under it fails, as on a full or locked disk.
        exports.parentFile!!.mkdirs()
        exports.writeText("not a folder")
        val failed = runCatching { exported() }
        assertTrue("the export fails: ${failed.exceptionOrNull()}", failed.isFailure)
        assertTrue("and it is a write failure", failed.exceptionOrNull() is java.io.IOException)
        assertTrue("no part file was left", exports.listFiles() == null)
        exports.delete()
    }

    @Test
    fun anOrphanedPartFileFromAKilledExportIsClearedAndTheNextExportIsWhole() = runBlocking<Unit> {
        exports.mkdirs()
        val orphan = File(exports, "sign-setu-report-1.pdf.part").apply { writeText("half a report") }
        // A part younger than the grace period may be a write in progress, so this one is three minutes old.
        orphan.setLastModified(System.currentTimeMillis() - 3 * 60_000L)
        val whole = exported()
        assertTrue("the report is a PDF", whole.readBytes().let { it.size > 1000 && String(it, 0, 5) == "%PDF-" })
        assertEquals("no part file survives", emptyList<String>(), exports.list()!!.filter { it.endsWith(".part") })
        assertTrue("the orphan is gone", !orphan.exists())
        assertEquals("one report, named once it was whole", 1, exports.list()!!.size)
        // The share sheet's Finish deletes it; nothing here outlives the export.
        whole.delete()
        assertEquals(0, exports.list()!!.size)
    }

    @Test
    fun aReportThatCannotBeRenamedKeepsItsPartOutOfTheWay() = runBlocking<Unit> {
        exports.mkdirs()
        // The name is taken by a directory, so the finished part cannot be renamed onto it.
        File(exports, "taken.pdf").mkdirs()
        val part = File(exports, "taken.pdf.part")
        val failed = runCatching { writeReport(exports, "taken.pdf", ByteArray(4)) }
        assertTrue("naming the report fails", failed.isFailure)
        assertTrue("and the part is cleaned up", !part.exists())
        assertTrue("while whatever held the name stays untouched", File(exports, "taken.pdf").isDirectory)
    }
}
