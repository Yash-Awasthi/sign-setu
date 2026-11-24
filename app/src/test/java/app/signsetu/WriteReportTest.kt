package app.signsetu

import app.signsetu.export.writeReport
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/** An export either lands whole or does not land: no half PDF for the share sheet to send, no part file left behind. */
class WriteReportTest {
    @get:Rule val folder = TemporaryFolder()

    private val bytes = ByteArray(1024) { (it % 251).toByte() }

    @Test
    fun theReportIsNamedOnlyWhenItIsWhole() {
        val file = writeReport(folder.root, "report.pdf", bytes)
        assertEquals("report.pdf", file.name)
        assertArrayEquals(bytes, file.readBytes())
        assertEquals("no part file is left", listOf("report.pdf"), folder.root.list()!!.sorted())
    }

    @Test
    fun aWriteThatStopsHalfWayLeavesNothing() {
        val dir = folder.newFolder("exports")
        val broken = { file: File, data: ByteArray ->
            file.writeBytes(data.copyOf(data.size / 2))
        }
        val failed = runCatching { writeReport(dir, "report.pdf", bytes, broken) }
        assertTrue("the export fails", failed.exceptionOrNull() is IOException)
        assertEquals("no part file, no half PDF", emptyList<String>(), dir.list()!!.toList())
    }

    @Test
    fun aWriteThatThrowsLeavesNothing() {
        val dir = folder.newFolder("exports")
        val broken = { _: File, _: ByteArray -> throw IOException("no space left on device") }
        val failed = runCatching { writeReport(dir, "report.pdf", bytes, broken) }
        assertEquals("no space left on device", failed.exceptionOrNull()?.message)
        assertEquals(emptyList<String>(), dir.list()!!.toList())
    }

    @Test
    fun anEarlierExportOfTheSameNameIsNeverHalfOverwritten() {
        val dir = folder.newFolder("exports")
        val first = writeReport(dir, "report.pdf", bytes)
        // The writer dies before it renames anything: the earlier report is still whole and still there.
        val failed = runCatching { writeReport(dir, "report.pdf", ByteArray(10), { _, _ -> throw IOException("killed") }) }
        assertTrue(failed.isFailure)
        assertArrayEquals(bytes, first.readBytes())
        assertFalse("no part file", dir.list()!!.any { it.endsWith(".part") })
    }
}
