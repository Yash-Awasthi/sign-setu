package app.signsetu

import app.signsetu.gloss.Contributions
import app.signsetu.net.Http
import app.signsetu.net.Reply
import app.signsetu.net.TokenVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ContributionsRetentionTest {
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun purgeExpiredDeletesOldRecordsAndQueueFiles() = runBlocking {
        val root = Files.createTempDirectory("contrib-retention").toFile()
        val now = 1_700_000_000_000L
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val oldSession = seed(root, "sessions/old.json", now - 31 * day)
            val freshSession = seed(root, "sessions/fresh.json", now - 1 * day)
            val oldQueued = seed(root, "queue/old.json", now - 31 * day)
            val freshQueued = seed(root, "queue/fresh.json", now - 1 * day)

            val contributions = Contributions(root, "", Http { _, _, _, _, _ -> Reply(500, "{}") }, TestVault, { false }, scope)

            assertEquals(2, contributions.purgeExpired(now))
            assertFalse(oldSession.exists())
            assertFalse(oldQueued.exists())
            assertTrue(freshSession.exists())
            assertTrue(freshQueued.exists())
            assertEquals(1, contributions.saved().size)
            assertEquals(1, contributions.pending())
        } finally {
            scope.cancel()
            root.deleteRecursively()
        }
    }

    @Test
    fun aReleaseBuildDiscardsADebugQueueAndWritesNothing() = runBlocking {
        val root = Files.createTempDirectory("contrib-release").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            seed(root, "sessions/record.json", 0L)
            seed(root, "queue/record.json", 0L)
            File(root, "consent.txt").writeText("v1")
            File(root, "signer.txt").writeText("s-0123456789abcdef")

            val contributions = Contributions(root, "https://gateway.test", Http { _, _, _, _, _ -> error("release sent") }, TestVault, { true }, scope)
            contributions.restrictToDebug(false)

            assertFalse(contributions.consented)
            assertFalse("the consent file is gone", File(root, "consent.txt").exists())
            assertEquals("the debug queue is gone", 0, contributions.pending())
            // Local records were saved with the signer's consent; a release build stops uploading and writing, and
            // retention expires them, but it does not destroy them.
            assertEquals(1, contributions.saved().size)

            contributions.save("prompt", buildJsonObject { }, buildJsonObject { }, listOf("hello")).join()

            assertEquals(0, contributions.pending())
            assertEquals("a release build writes no new record", 1, contributions.saved().size)
        } finally {
            scope.cancel()
            root.deleteRecursively()
        }
    }

    private fun seed(root: File, name: String, modifiedAt: Long): File {
        val file = File(root, name)
        file.parentFile?.mkdirs()
        file.writeText("""{"kind":"prompt","session":{},"window":{},"gloss":["hello"]}""")
        if (modifiedAt > 0) file.setLastModified(modifiedAt)
        return file
    }

    private object TestVault : TokenVault {
        override fun get() = "test-token"
        override fun put(token: String) = true
        override fun clear() = Unit
    }
}
