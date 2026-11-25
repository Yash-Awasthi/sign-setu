package app.signsetu

import android.content.ContextWrapper
import app.signsetu.assistant.ModelStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest

/** A GGUF file is imported only when its SHA-256 is on the department's allow-list; anything else leaves nothing behind. */
class ModelAllowListTest {
    @get:Rule val folder = TemporaryFolder()

    private val store = ModelStore(ContextWrapper(null))
    private val bytes = ByteArray(4096) { (it * 31 + 7).toByte() }

    private fun hash(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun target() = File(folder.root, "model.gguf")

    @Test
    fun aListedHashIsInstalledWhole() = runBlocking {
        val file = target()
        assertTrue(store.accept(ByteArrayInputStream(bytes), file, setOf(hash(bytes))))
        assertTrue("the target file exists", file.exists())
        assertArrayEquals("the bytes are copied unchanged", bytes, file.readBytes())
        assertFalse("no part file survives", File(folder.root, "model.gguf.part").exists())
    }

    @Test
    fun oneFlippedByteIsRefused() = runBlocking {
        val file = target()
        val flipped = bytes.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
        assertFalse(store.accept(ByteArrayInputStream(flipped), file, setOf(hash(bytes))))
        assertFalse("no target file", file.exists())
        assertFalse("no part file", File(folder.root, "model.gguf.part").exists())
    }

    @Test
    fun anUnlistedHashIsRefused() = runBlocking {
        val file = target()
        val other = ByteArray(1024) { (it % 251).toByte() }
        assertFalse(store.accept(ByteArrayInputStream(bytes), file, setOf(hash(other))))
        assertFalse("no target file", file.exists())
        assertFalse("no part file", File(folder.root, "model.gguf.part").exists())
    }

    @Test
    fun theShippedAllowListsAreWellFormed() {
        val hex = Regex("^[0-9a-f]{64}$")
        assertTrue("assistant models", ModelStore.KNOWN_MODELS.isNotEmpty())
        assertTrue("sentence helper models", ModelStore.KNOWN_SENTENCE_MODELS.isNotEmpty())
        assertTrue("shipped hashes are lowercase SHA-256", (ModelStore.KNOWN_MODELS + ModelStore.KNOWN_SENTENCE_MODELS).all { hex.matches(it) })
    }

    @Test
    fun plantedBytesCannotPassAgainstTheShippedAllowList() = runBlocking {
        val file = target()
        val planted = ByteArray(4096) { (it * 17).toByte() }
        assertFalse(store.accept(ByteArrayInputStream(planted), file, ModelStore.KNOWN_MODELS))
        assertFalse("no target file", file.exists())
        assertFalse("no part file", File(folder.root, "model.gguf.part").exists())
        assertEquals("no leftovers", emptyList<String>(), folder.root.list()!!.toList())
    }
}
