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
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/** Bounded imports, stale part files, and the digest re-check before a model reaches the native parser. */
class LocalModelStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private val bytes = ByteArray(4096) { (it * 31 + 7).toByte() }

    private fun hash(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun target() = File(folder.root, "model.gguf")

    private fun part() = File(folder.root, "model.gguf.part")

    private fun newStore(cap: Long = ModelStore.MAX_MODEL_BYTES) = ModelStore(ContextWrapper(null), maxBytes = cap)

    @Test
    fun theCapIsAboveTheLargestAllowedModel() {
        assertTrue("a 1.5 GB model is still accepted", ModelStore.MAX_MODEL_BYTES > 1_500_000_000L)
    }

    @Test
    fun aStreamLongerThanTheCapIsRefusedAndLeavesNoPart() = runBlocking {
        val store = newStore(cap = 1024)
        val oversized = ByteArray(4096) { (it * 11 + 5).toByte() }
        part().writeBytes(ByteArray(16)) // left by an earlier copy that died
        assertFalse("the running total passed the cap", store.accept(ByteArrayInputStream(oversized), target(), setOf(hash(oversized))))
        assertFalse("no model file", target().exists())
        assertFalse("no part file", part().exists())
        assertEquals("no leftovers", emptyList<String>(), folder.root.list()!!.toList())
    }

    @Test
    fun aProviderLengthOverTheCapIsRefusedBeforeAnyByteIsRead() = runBlocking {
        val store = newStore(cap = 1024)
        var reads = 0
        val stream = object : InputStream() {
            override fun read(): Int = throw IOException("unbuffered reads are not used")

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                reads += 1
                return -1
            }
        }
        assertFalse(store.accept(stream, target(), setOf(hash(bytes)), declaredLength = 1025))
        assertEquals("nothing was copied", 0, reads)
        assertFalse(target().exists())
        assertFalse(part().exists())
    }

    @Test
    fun aStreamUnderTheCapStillImportsAndIsReady() = runBlocking {
        val store = newStore()
        val file = target()
        assertTrue(store.accept(ByteArrayInputStream(bytes), file, setOf(hash(bytes))))
        assertArrayEquals("the bytes are copied unchanged", bytes, file.readBytes())
        assertFalse("no part file survives", part().exists())
        val installed = file.exists() && store.intact(file, setOf(hash(bytes)))
        assertTrue("the installed file hashes as imported", installed)
        assertEquals("an installed, intact file is READY", ModelStore.State.READY, ModelStore.stateOf(true, true, installed))
    }

    @Test
    fun aFileWhoseBytesChangedIsNotReadyAndIsTreatedAsAbsent() {
        val store = newStore()
        val file = target().apply { writeBytes(bytes) }
        val allowed = setOf(hash(bytes))
        assertTrue("the imported file is intact", store.intact(file, allowed))
        file.writeBytes(bytes.copyOf().also { it[0] = (it[0] + 1).toByte() })
        file.setLastModified(file.lastModified() + 2_000)
        assertFalse("the changed bytes no longer match", store.intact(file, allowed))
        assertFalse("dropped like a failed import", file.exists())
        assertEquals("reported as absent", ModelStore.State.NOT_INSTALLED, ModelStore.stateOf(true, true, file.exists()))
    }

    @Test
    fun removeDeletesAStalePartFile() = runBlocking {
        val store = newStore()
        val file = target().apply { writeBytes(bytes) }
        part().writeBytes(ByteArray(8))
        store.removeAt(file)
        assertFalse("the model is gone", file.exists())
        assertFalse("a stale part is gone too", part().exists())
        assertEquals("no leftovers", emptyList<String>(), folder.root.list()!!.toList())
    }
}
