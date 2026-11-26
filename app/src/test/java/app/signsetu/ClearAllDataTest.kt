package app.signsetu

import app.signsetu.gloss.CONSENT_VERSION
import app.signsetu.gloss.Contributions
import app.signsetu.net.Http
import app.signsetu.net.TokenVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Clear all data also clears the settings DataStore and this app's Documents/SignSetu copies through MediaStore.
 * Neither can be faked in a JVM test: the settings store needs an Android context for its file, and the Documents
 * copies are deleted through a ContentResolver. Only the contribution folder, a plain directory, is checked here.
 */
class ClearAllDataTest {
    @Test
    fun discardAllRemovesRecordsQueueConsentAndSigner() {
        val root = Files.createTempDirectory("clear-all").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            File(root, "sessions").mkdirs()
            File(root, "queue").mkdirs()
            File(root, "sessions/record.json").writeText("{}")
            File(root, "queue/record.json").writeText("{}")
            File(root, "consent.txt").writeText(CONSENT_VERSION)
            File(root, "signer.txt").writeText("s-0123456789abcdef")
            val contributions = Contributions(root, "https://gateway.test", Http { _, _, _, _, _ -> null }, SilentVault, { false }, scope)

            assertEquals(4, contributions.discardAll())
            assertFalse(root.exists())
        } finally {
            scope.cancel()
            root.deleteRecursively()
        }
    }

    private object SilentVault : TokenVault {
        override fun get() = null
        override fun put(token: String) = true
        override fun clear() = Unit
    }
}
