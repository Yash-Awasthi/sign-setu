package app.signsetu

import app.signsetu.export.ExportSweeper
import app.signsetu.ui.session.folderToSweep
import app.signsetu.ui.session.pendingName
import app.signsetu.ui.session.stalePending
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The decisions a save makes that can be checked without a device: the name a row carries while it is being
 * written, when a pending row is dead, and which earlier folder a retitled save replaces. */
class PendingExportTest {
    private val folder = "Documents/SignSetu/FIR 214_2026 12345678"

    @Test
    fun aRowIsNamedWithPartWhileItsBytesAreWritten() {
        assertEquals("FIR 214_2026 12345678 report.pdf.part", pendingName("FIR 214_2026 12345678 report.pdf"))
        assertTrue("the final name is never the pending one", pendingName("FIR 214_2026 12345678.zip") != "FIR 214_2026 12345678.zip")
    }

    @Test
    fun aPendingRowIsStaleOnlyAfterTheGrace() {
        val now = 10_000_000L
        assertFalse("a write in progress is inside the grace", stalePending(now, now - ExportSweeper.PART_GRACE_MS + 1))
        assertTrue("at the grace the row is dead", stalePending(now, now - ExportSweeper.PART_GRACE_MS))
        assertTrue("a kill long ago", stalePending(now, now - ExportSweeper.PART_GRACE_MS * 10))
        assertFalse("a row dated in the future is not dead", stalePending(now, now + 1_000))
    }

    @Test
    fun theFolderOfAnEarlierTitleIsSweptOnce() {
        assertNull("never saved before", folderToSweep(null, folder))
        assertNull("the same folder", folderToSweep(folder, folder))
        assertNull("the same folder with a trailing slash", folderToSweep("$folder/", folder))
        assertNull("nothing stored", folderToSweep("", folder))
        assertEquals("a retitled save replaces the old folder", "Documents/SignSetu/Interview A 12345678", folderToSweep("Documents/SignSetu/Interview A 12345678", folder))
    }
}
