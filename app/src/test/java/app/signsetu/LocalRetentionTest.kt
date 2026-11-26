package app.signsetu

import app.signsetu.data.session.LocalRetention
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LocalRetentionTest {
    @Test
    fun purgeDeletesOnlyFilesPastTheBound() {
        val now = 1_000_000_000_000L
        val root = Files.createTempDirectory("retention").toFile()
        try {
            val queue = File(root, "queue").apply { mkdirs() }
            val old = File(queue, "old.json").apply { writeText("old"); setLastModified(now - 31L * 24 * 60 * 60 * 1000) }
            val fresh = File(queue, "fresh.json").apply { writeText("fresh"); setLastModified(now - 1000) }

            assertEquals(1, LocalRetention.purge(root, now, LocalRetention.CONTRIB_MAX_AGE_MS))
            assertFalse(old.exists())
            assertTrue(fresh.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun purgeAllRemovesFreshFilesAndTheDirectory() {
        val root = Files.createTempDirectory("retention-all").toFile()
        val nested = File(root, "sessions").apply { mkdirs() }
        File(nested, "record.json").writeText("landmarks")
        File(root, "consent.txt").writeText("v1")

        assertEquals(2, LocalRetention.purgeAll(root))
        assertFalse(root.exists())
    }

    @Test
    fun trainingRecordsFollowTheirOwnBound() {
        val now = 1_000_000_000_000L
        val day = 24L * 60 * 60 * 1000
        val root = Files.createTempDirectory("retention-contrib").toFile()
        try {
            val old = File(root, "session_old.json").apply { writeText("{}"); setLastModified(now - 31 * day) }
            val fresh = File(root, "session_fresh.json").apply { writeText("{}"); setLastModified(now - 29 * day) }

            assertEquals(1, LocalRetention.purge(root, now, LocalRetention.CONTRIB_MAX_AGE_MS))
            assertFalse(old.exists())
            assertTrue(fresh.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun debugCapturesFollowTheirOwnShorterBound() {
        val now = 1_000_000_000_000L
        val day = 24L * 60 * 60 * 1000
        val root = Files.createTempDirectory("recordings").toFile()
        try {
            val old = File(root, "window_1.json").apply { writeText("{}"); setLastModified(now - 8 * day) }
            val fresh = File(root, "window_2.json").apply { writeText("{}"); setLastModified(now - 6 * day) }

            assertEquals(1, LocalRetention.purge(root, now, LocalRetention.DEBUG_RECORDING_MAX_AGE_MS))
            assertFalse(old.exists())
            assertTrue(fresh.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
