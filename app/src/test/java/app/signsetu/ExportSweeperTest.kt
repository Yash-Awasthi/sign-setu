package app.signsetu

import app.signsetu.export.ExportFile
import app.signsetu.export.ExportSweeper
import app.signsetu.export.ExportStore
import app.signsetu.export.FileExportStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ExportSweeperTest {
    private class FakeStore(files: List<ExportFile>) : ExportStore {
        val live = files.toMutableList()
        val deleted = mutableListOf<String>()

        override fun files(): List<ExportFile> = live.toList()

        override fun delete(file: ExportFile): Boolean = live.remove(file).also { if (it) deleted += file.name }
    }

    private val now = 1_000_000_000_000L
    private val minute = 60_000L

    @Test
    fun cacheDeletesStaleAndPartButKeepsFresh() {
        val store = FakeStore(
            listOf(
                ExportFile("old.pdf", "old.pdf", now - 16 * minute),
                ExportFile("fresh.pdf", "fresh.pdf", now - minute),
                ExportFile("half.pdf.part", "half.pdf.part", now - 3 * minute),
            ),
        )

        assertEquals(2, ExportSweeper.sweep(store, now, ExportSweeper.CACHE_MAX_AGE_MS))
        assertEquals(listOf("old.pdf", "half.pdf.part"), store.deleted)
        assertEquals(listOf("fresh.pdf"), store.live.map { it.name })
    }

    @Test
    fun aFreshPartFileSurvivesEvenWhenItsExportIsLongOver() {
        // A report takes minutes to lay out: a part written a moment ago may still be a live write, so it stays.
        val store = FakeStore(
            listOf(
                ExportFile("live.pdf.part", "live.pdf.part", now - 1_000),
                ExportFile("dead.pdf.part", "dead.pdf.part", now - 3 * minute),
            ),
        )

        assertEquals(1, ExportSweeper.sweep(store, now, ExportSweeper.CACHE_MAX_AGE_MS))
        assertEquals(listOf("dead.pdf.part"), store.deleted)
        assertEquals(listOf("live.pdf.part"), store.live.map { it.name })
        // A deliberate clear-all still takes the fresh part too.
        assertEquals(1, ExportSweeper.sweep(store, now, 0))
        assertEquals(0, store.live.size)
    }

    @Test
    fun savedCopyFollowsTheRetentionWindow() {
        val store = FakeStore(
            listOf(
                ExportFile("old", "old.zip", now - 25 * 60 * minute),
                ExportFile("kept", "kept.zip", now - 23 * 60 * minute),
            ),
        )

        assertEquals(1, ExportSweeper.sweep(store, now, 24 * 60 * minute))
        assertEquals(listOf("kept.zip"), store.live.map { it.name })
    }

    @Test
    fun clearAllRemovesFreshFilesToo() {
        val store = FakeStore(
            listOf(
                ExportFile("fresh", "fresh.pdf", now),
                ExportFile("part", "half.part", now),
            ),
        )

        assertEquals(2, ExportSweeper.sweep(store, now, 0))
        assertEquals(0, store.live.size)
    }

    @Test
    fun keptSessionsLeaveSavedCopiesAlone() {
        val store = FakeStore(listOf(ExportFile("any", "any.zip", now - 400L * 24 * 60 * minute)))

        assertEquals(0, ExportSweeper.sweep(store, now, Long.MAX_VALUE))
        assertEquals(1, store.live.size)
    }

    @Test
    fun fileStoreSweepsTheCacheDirectoryAtStartup() {
        val dir = Files.createTempDirectory("exports").toFile()
        try {
            val old = File(dir, "old.pdf").apply { writeText("old"); setLastModified(now - 16 * minute) }
            val fresh = File(dir, "fresh.pdf").apply { writeText("fresh"); setLastModified(now - minute) }
            val part = File(dir, "half.pdf.part").apply { writeText("half"); setLastModified(now - 3 * minute) }
            val folder = File(dir, "nested").apply { mkdirs() }

            assertEquals(2, ExportSweeper.sweep(FileExportStore(dir), now, ExportSweeper.CACHE_MAX_AGE_MS))
            assertFalse(old.exists())
            assertTrue(fresh.exists())
            assertFalse(part.exists())
            assertTrue("a folder is not an export", folder.isDirectory)
        } finally {
            dir.deleteRecursively()
        }
    }
}
