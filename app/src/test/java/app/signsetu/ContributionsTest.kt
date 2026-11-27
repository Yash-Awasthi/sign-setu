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
import kotlinx.serialization.json.buildJsonObject
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
    fun aBatchIsNotSentWhenConsentIsWithdrawnBeforeTheCall() = runBlocking {
        val root = Files.createTempDirectory("contributions-withdraw").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val http = Http { method, _, _, _, _ -> calls += method; Reply(200, "{}") }
        val contributions = Contributions(root, "https://gateway.test", http, TestVault, { true }, scope)
        seedQueuedRecord(root)
        try {
            val withdrawn = CountDownLatch(1)
            // Runs inside the batch, after its first consent check and body build, before the call.
            contributions.beforeConsentRecheck = { contributions.setConsent(false); withdrawn.countDown() }
            assertTrue(contributions.setConsent(true))
            assertTrue("no batch reached the window", withdrawn.await(5, TimeUnit.SECONDS))
            delay(300)
            assertTrue("a batch was posted after withdrawal: $calls", calls.none { it == "POST" })
            assertEquals(0, contributions.pending())
        } finally {
            scope.cancel()
            root.deleteRecursively()
        }
    }

    @Test
    fun aBatchIsNotSentWhileTheDeletionLatchStands() = runBlocking {
        val root = Files.createTempDirectory("contributions-latch").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val http = Http { method, _, _, _, _ -> calls += method; Reply(200, "{}") }
        val contributions = Contributions(root, "https://gateway.test", http, TestVault, { true }, scope)
        seedQueuedRecord(root)
        try {
            val latched = CountDownLatch(1)
            // The latch appears inside the window, after the batch's first check passed with none set.
            contributions.beforeConsentRecheck = { File(root, "delete.pending").writeText("v1"); latched.countDown() }
            assertTrue(contributions.setConsent(true))
            assertTrue("no batch reached the window", latched.await(5, TimeUnit.SECONDS))
            delay(300)
            assertTrue("a batch was posted while the latch stood: $calls", calls.none { it == "POST" })
            assertEquals("the record waits on the phone", 1, contributions.pending())
            assertTrue(File(root, "delete.pending").exists())
        } finally {
            scope.cancel()
            root.deleteRecursively()
        }
    }

    @Test
    fun turningSharingOnWithAStaleLatchRetriesTheDeleteAndRefusesWhenItFails() {
        val root = Files.createTempDirectory("contributions-stale-latch").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val calls = Collections.synchronizedList(mutableListOf<String>())
        var reachable = false
        val http = Http { method, _, _, _, _ -> calls += method; if (reachable) Reply(200, "{}") else null }
        File(root, "signer.txt").writeText("s-0123456789abcdef")
        File(root, "delete.pending").writeText("v1")
        val contributions = Contributions(root, "https://gateway.test", http, TestVault, { true }, scope)
        try {
            assertFalse("sharing claimed on while the writer is latched", contributions.setConsent(true))
            assertFalse(contributions.consented)
            assertEquals(listOf("DELETE"), calls)
            assertTrue("a network failure keeps the latch for the next attempt", File(root, "delete.pending").exists())

            reachable = true
            assertTrue(contributions.setConsent(true))
            assertTrue(contributions.consented)
            assertFalse("a confirmed delete clears the latch", File(root, "delete.pending").exists())
            assertEquals(listOf("DELETE", "DELETE"), calls)
        } finally {
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
                // The latch left by the failure is retried rather than overridden, and the retry fails here too.
                assertFalse(restarted.setConsent(true))
                delay(100)
                assertEquals("no batch is posted while the latch stands", listOf("DELETE", "DELETE"), calls)
                assertFalse("sharing must not be claimed on while the writer is latched", restarted.consented)
                assertEquals(1, restarted.pending())
            } finally {
                restartedScope.cancel()
            }
        } finally {
            scope.cancel()
            root.deleteRecursively()
        }
    }

    @Test
    fun releaseBuildDropsConsentAndQueueLeftByADebugInstall() {
        val root = Files.createTempDirectory("contributions-release").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            seedQueuedRecord(root)
            File(root, "consent.txt").writeText(app.signsetu.gloss.CONSENT_VERSION)
            File(root, "sessions").mkdirs()
            File(File(root, "sessions"), "kept.json").writeText("{}")
            val contributions = Contributions(root, "https://gateway.test", Http { _, _, _, _, _ -> null }, TestVault, { true }, scope)

            contributions.restrictToDebug(false)

            assertFalse(contributions.consented)
            assertEquals(0, contributions.pending())
            assertFalse("the consent file is gone", File(root, "consent.txt").exists())
            assertTrue("local records are not an upload queue", File(root, "sessions/kept.json").exists())
        } finally {
            scope.cancel()
            root.deleteRecursively()
        }
    }

    @Test
    fun debugBuildKeepsConsentAndQueue() {
        val root = Files.createTempDirectory("contributions-debug").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            seedQueuedRecord(root)
            File(root, "consent.txt").writeText(app.signsetu.gloss.CONSENT_VERSION)
            val contributions = Contributions(root, "https://gateway.test", Http { _, _, _, _, _ -> null }, TestVault, { false }, scope)

            contributions.restrictToDebug(true)

            assertTrue(contributions.consented)
            assertEquals(1, contributions.pending())
        } finally {
            scope.cancel()
            root.deleteRecursively()
        }
    }

    @Test
    fun purgeExpiredDropsOldRecordsButKeepsTheConsent() {
        val root = Files.createTempDirectory("contributions-purge").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val now = 1_000_000_000_000L
        try {
            File(root, "sessions").mkdirs()
            File(root, "queue").mkdirs()
            val old = File(root, "sessions/old.json").apply { writeText("{}"); setLastModified(now - 31L * 24 * 60 * 60 * 1000) }
            val fresh = File(root, "sessions/fresh.json").apply { writeText("{}"); setLastModified(now - 1000) }
            val queued = File(root, "queue/old.json").apply { writeText("{}"); setLastModified(now - 31L * 24 * 60 * 60 * 1000) }
            File(root, "consent.txt").writeText(app.signsetu.gloss.CONSENT_VERSION)
            val contributions = Contributions(root, "https://gateway.test", Http { _, _, _, _, _ -> null }, TestVault, { false }, scope)

            assertEquals(2, contributions.purgeExpired(now))

            assertFalse(old.exists())
            assertTrue(fresh.exists())
            assertFalse(queued.exists())
            assertTrue("consent is a setting, not a record", contributions.consented)
        } finally {
            scope.cancel()
            root.deleteRecursively()
        }
    }

    @Test
    fun slowDeletionDoesNotBlockConsentOrSave() = runBlocking {
        val root = Files.createTempDirectory("contributions-slow-delete").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val deleteEntered = CountDownLatch(1)
        val releaseDelete = CountDownLatch(1)
        // A gateway that takes its time answering the delete: the wait must not hold the object monitor.
        val http = Http { method, _, _, _, _ ->
            when (method) {
                "DELETE" -> {
                    deleteEntered.countDown()
                    check(releaseDelete.await(5, TimeUnit.SECONDS))
                    Reply(200, "{}")
                }
                else -> null
            }
        }
        val contributions = Contributions(root, "https://gateway.test", http, TestVault, { false }, scope)
        try {
            contributions.setConsent(true)
            val deletion = async(Dispatchers.IO) { contributions.deleteMine() }
            assertTrue("delete did not start", deleteEntered.await(5, TimeUnit.SECONDS))

            val consentDone = CountDownLatch(1)
            Thread { contributions.setConsent(false); consentDone.countDown() }.start()
            assertTrue("setConsent waited for the gateway", consentDone.await(2, TimeUnit.SECONDS))
            assertFalse("the withdrawal did not land", contributions.consented)

            val saved = contributions.save("prompt", buildJsonObject { }, buildJsonObject { }, listOf("hello"))
            withTimeout(2_000) { saved.join() }
            assertTrue("save waited for the gateway", File(root, "sessions").listFiles().orEmpty().isNotEmpty())

            releaseDelete.countDown()
            assertTrue(withTimeout(5_000) { deletion.await() })
            assertEquals(0, contributions.pending())
        } finally {
            releaseDelete.countDown()
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
