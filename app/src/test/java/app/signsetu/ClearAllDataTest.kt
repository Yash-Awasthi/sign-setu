package app.signsetu

import app.signsetu.gloss.CONSENT_VERSION
import app.signsetu.gloss.Contributions
import app.signsetu.net.Http
import app.signsetu.net.Reply
import app.signsetu.net.TokenVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Collections

/**
 * Clear all data also clears the settings DataStore and this app's Documents/SignSetu copies through MediaStore.
 * Neither can be faked in a JVM test: the settings store needs an Android context for its file, and the Documents
 * copies are deleted through a ContentResolver. Only the contribution folder, a plain directory, is checked here.
 */
class ClearAllDataTest {
    @Test
    fun aWipeWithAReachableGatewayDeletesTheRemoteRowsThenTheSignerFile() {
        val root = Files.createTempDirectory("clear-all").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val http = Http { method, url, _, _, _ -> calls += "$method $url"; Reply(200, "{}") }
        seedShared(root)
        val contributions = Contributions(root, "https://gateway.test", http, OpenVault, { true }, scope)
        try {
            // The queue record and the session record, counting only what stayed deleted.
            assertEquals(2, contributions.discardAll())

            assertEquals("the rows are named by the signer id before the file goes", listOf("DELETE https://gateway.test/v1/contrib/s-0123456789abcdef"), calls)
            assertFalse("a confirmed delete takes the signer id", File(root, "signer.txt").exists())
            assertFalse("a confirmed delete clears the latch", File(root, "delete.pending").exists())
            assertFalse(root.exists())
        } finally {
            scope.cancel()
            root.deleteRecursively()
        }
    }

    @Test
    fun aWipeWithAnUnreachableGatewayKeepsTheSignerFileAndTheLatch() {
        val root = Files.createTempDirectory("clear-all").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val http = Http { method, _, _, _, _ -> calls += method; null }
        seedShared(root)
        val contributions = Contributions(root, "https://gateway.test", http, OpenVault, { true }, scope)
        try {
            // The two surviving files (signer id, latch) are not counted as removed.
            assertEquals(2, contributions.discardAll())

            assertEquals(listOf("DELETE"), calls)
            assertEquals("s-0123456789abcdef", File(root, "signer.txt").readText())
            assertTrue("a later attempt must still find the latch", File(root, "delete.pending").exists())
            assertFalse("local records go regardless", File(root, "sessions/record.json").exists())
            assertFalse(File(root, "queue/record.json").exists())
            assertFalse(File(root, "consent.txt").exists())
        } finally {
            scope.cancel()
            root.deleteRecursively()
        }
    }

    private fun seedShared(root: File) {
        File(root, "sessions").mkdirs()
        File(root, "queue").mkdirs()
        File(root, "sessions/record.json").writeText("{}")
        File(root, "queue/record.json").writeText("{}")
        File(root, "consent.txt").writeText(CONSENT_VERSION)
        File(root, "signer.txt").writeText("s-0123456789abcdef")
    }

    private object OpenVault : TokenVault {
        override fun get() = "test-token"
        override fun put(token: String) = true
        override fun clear() = Unit
    }
}
