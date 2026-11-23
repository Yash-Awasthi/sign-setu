package app.signsetu

import app.signsetu.gloss.Contributions
import app.signsetu.net.Http
import app.signsetu.net.Reply
import app.signsetu.net.TokenVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ContributionsTest {
    @Test
    fun deletionWaitsForUploadAndClearsTheQueuedRecord() = runBlocking {
        val root = Files.createTempDirectory("contributions-test").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val uploadEntered = CountDownLatch(1)
        val releaseUpload = CountDownLatch(1)
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val http = Http { method, _, _, _, _ ->
            calls += method
            when (method) {
                "POST" -> {
                    uploadEntered.countDown()
                    check(releaseUpload.await(5, TimeUnit.SECONDS))
                    Reply(500, "{}")
                }
                "DELETE" -> Reply(200, "{}")
                else -> error("unexpected method $method")
            }
        }
        val contributions = Contributions(root, "https://gateway.test", http, TestVault, { true }, scope)
        seedQueuedRecord(root)
        try {
            contributions.setConsent(true)
            assertTrue("upload did not start", uploadEntered.await(5, TimeUnit.SECONDS))
            val deletion = async(Dispatchers.IO) { contributions.deleteMine() }
            delay(100)
            assertFalse("deletion passed an in-flight upload", deletion.isCompleted)
            releaseUpload.countDown()
            assertTrue(withTimeout(5_000) { deletion.await() })
            assertEquals(listOf("POST", "DELETE"), calls)
            assertEquals(0, contributions.pending())
        } finally {
            releaseUpload.countDown()
            scope.cancel()
            root.deleteRecursively()
        }
    }

    @Test
    fun failedDeletionKeepsTheQueuedRecord() = runBlocking {
        val root = Files.createTempDirectory("contributions-test").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val http = Http { method, _, _, _, _ -> calls += method; Reply(500, "{}") }
        val contributions = Contributions(root, "https://gateway.test", http, TestVault, { false }, scope)
        seedQueuedRecord(root)
        try {
            contributions.setConsent(true)
            assertFalse(withTimeout(5_000) { kotlinx.coroutines.withContext(Dispatchers.IO) { contributions.deleteMine() } })
            assertEquals(1, contributions.pending())
            scope.cancel()

            val restartedScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                val restarted = Contributions(root, "https://gateway.test", http, TestVault, { true }, restartedScope)
                restarted.setConsent(true)
                delay(100)
                assertEquals(listOf("DELETE"), calls)
                assertEquals(1, restarted.pending())
            } finally {
                restartedScope.cancel()
            }
        } finally {
            scope.cancel()
            root.deleteRecursively()
        }
    }

    private fun seedQueuedRecord(root: File) {
        val queue = File(root, "queue")
        assertTrue(queue.mkdirs() || queue.isDirectory)
        File(queue, "100_prompt.json").writeText(
            """{"kind":"prompt","session":{},"window":{},"gloss":["hello"]}""",
        )
    }

    private object TestVault : TokenVault {
        override fun get() = "test-token"
        override fun put(token: String) = true
        override fun clear() = Unit
    }
}
