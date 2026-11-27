package app.signsetu.assistant

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest

/** A llama.cpp model in memory; one request at a time, each starting from an empty context. */
class LocalModel private constructor(private var handle: Long) {
    fun chat(system: String, user: String, maxTokens: Int): String =
        synchronized(this) { if (handle == 0L) "" else nativeChat(handle, system, user, maxTokens).trim() }

    fun close() = synchronized(this) {
        if (handle != 0L) nativeFree(handle)
        handle = 0L
    }

    companion object {
        private val loaded by lazy { runCatching { System.loadLibrary("assistant") }.isSuccess }

        fun load(file: File): LocalModel? {
            if (!loaded || !file.exists()) return null
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
            return nativeLoad(file.absolutePath, threads, CONTEXT).takeIf { it != 0L }?.let(::LocalModel)
        }

        val supported: Boolean get() = loaded

        private const val CONTEXT = 1024

        @JvmStatic private external fun nativeLoad(path: String, threads: Int, context: Int): Long
        @JvmStatic private external fun nativeChat(handle: Long, system: String, user: String, maxTokens: Int): String
        @JvmStatic private external fun nativeFree(handle: Long)
    }
}

/** A model file on this phone. It is imported once from a file the department hands out, and accepted only if
 * its hash is in [known], so a swapped or damaged file is never parsed. Each store ([name]) has its own file and its
 * own import: the assistant's is for the automation agents, the sentence helper's for reading signed answers. */
class ModelStore(
    private val context: Context,
    private val name: String = "assistant",
    private val known: Set<String> = KNOWN_MODELS,
    private val maxBytes: Long = MAX_MODEL_BYTES,
) {
    private val file = File(context.noBackupFilesDir, "models/$name.gguf")
    private val lock = Mutex()
    private var model: LocalModel? = null
    // Digest checks already paid for this process, keyed by path, size and modified time; see [intact].
    private val verified = HashMap<String, Boolean>()

    enum class State { NOT_INSTALLED, READY, TOO_LITTLE_MEMORY, UNSUPPORTED }

    fun state(): State = if (!LocalModel.supported) State.UNSUPPORTED else stateOf(true, enoughMemory(), file.exists())

    /** Copies and checks the picked file; returns false, keeping nothing, if it is not a known model. */
    suspend fun import(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        lock.withLock {
            unload()
            runCatching {
                val declared = lengthOf(uri)
                context.contentResolver.openInputStream(uri)!!.use { acceptLocked(it, file, known, declared) }
            }.getOrDefault(false)
        }
    }

    /** The hashing and allow-list decision behind [import], reachable in JVM tests without a real Uri. */
    internal suspend fun accept(input: InputStream, target: File = file, known: Set<String> = this.known, declaredLength: Long = -1L): Boolean =
        withContext(Dispatchers.IO) { lock.withLock { acceptLocked(input, target, known, declaredLength) } }

    private fun acceptLocked(input: InputStream, target: File, known: Set<String>, declaredLength: Long): Boolean {
        val partial = partialOf(target).apply { parentFile?.mkdirs(); delete() }
        // A provider that reports an oversized file, or a phone with too little room, is refused before any byte is copied.
        if (declaredLength > maxBytes) return false
        if (declaredLength > 0 && declaredLength > (partial.parentFile?.usableSpace ?: Long.MAX_VALUE)) return false
        val digest = MessageDigest.getInstance("SHA-256")
        // A copy that dies partway leaves no partial file: the digest was never compared, so nothing is installed.
        val ok = try {
            DigestInputStream(input, digest).use { s ->
                partial.outputStream().use { out ->
                    val buffer = ByteArray(1 shl 20)
                    var total = 0L
                    while (true) {
                        val n = s.read(buffer)
                        if (n < 0) break
                        total += n
                        // A stream that never ends cannot fill internal storage or hold the store's lock forever.
                        if (total > maxBytes) throw IOException("over $maxBytes bytes")
                        out.write(buffer, 0, n)
                    }
                }
            }
            digest.digest().toHexString() in known
        } catch (_: Exception) {
            false
        }
        return (ok && partial.renameTo(target)).also { if (!it) partial.delete() }
    }

    private fun lengthOf(uri: Uri): Long = runCatching {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
    }.getOrDefault(-1L)

    private fun partialOf(target: File) = File(target.parentFile, "${target.name}.part")

    /** Whether [candidate] is still the file that was imported: its SHA-256 is on [allowed]. Hashed once per process
     * and cached, so only the first load after a start pays for it; a mismatch deletes the file, as a failed import
     * leaves nothing, so no swapped or damaged bytes reach the native parser (docs/10-on-device-assistant.md §3). */
    internal fun intact(candidate: File = file, allowed: Set<String> = known): Boolean {
        if (!candidate.exists()) return false
        val key = "${candidate.absolutePath}:${candidate.length()}:${candidate.lastModified()}"
        verified[key]?.let { return it }
        val ok = runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            candidate.inputStream().use { s ->
                val buffer = ByteArray(1 shl 20)
                while (true) {
                    val n = s.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            digest.digest().toHexString() in allowed
        }.getOrDefault(false)
        verified[key] = ok
        if (!ok) candidate.delete()
        return ok
    }

    suspend fun remove() = removeAt(file)

    /** Deletes a model file and any stale partial beside it; reachable in JVM tests without a real Context. */
    internal suspend fun removeAt(target: File) = withContext(Dispatchers.IO) {
        lock.withLock { unload(); target.delete(); partialOf(target).delete(); verified.clear() }
    }

    /** Runs [block] with the model, loading it on first use. */
    suspend fun <T> use(block: (LocalModel) -> T): T? = withContext(Dispatchers.Default) {
        lock.withLock {
            if (state() != State.READY) return@withLock null
            // The bytes are re-checked before the first native load: a file swapped or damaged since import is absent.
            val m = model ?: (if (roomToLoad() && intact()) LocalModel.load(file)?.also { model = it } else null) ?: return@withLock null
            block(m)
        }
    }

    /** Frees the model's memory; it loads again on the next request. */
    suspend fun release() = lock.withLock { unload() }

    private fun unload() {
        model?.close()
        model = null
    }

    private fun memory() = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }

    private fun enoughMemory(): Boolean = memory().totalMem >= MIN_MEMORY_BYTES

    // Checked at load time: total memory says nothing about what the camera and the stage are using right now.
    private fun roomToLoad(): Boolean = memory().let { !it.lowMemory && it.availMem >= MIN_FREE_BYTES }

    companion object {
        // A 3 GB phone reports well under 3 GB; whether 0.9 GB is free for the model is checked when it loads.
        private const val MIN_MEMORY_BYTES = 2_400L * 1024 * 1024
        private const val MIN_FREE_BYTES = 1_200L * 1024 * 1024
        // Larger than any allowed model (1.5 GB) and larger than the shipped 806 MB one, so an oversized pick or a
        // stream that never ends cannot fill internal storage before the digest refuses it.
        internal const val MAX_MODEL_BYTES = 1_600_000_000L

        /** The install decision behind [state], reachable in JVM tests without an ActivityManager. */
        internal fun stateOf(supported: Boolean, enoughMemory: Boolean, present: Boolean): State = when {
            !supported -> State.UNSUPPORTED
            !enoughMemory -> State.TOO_LITTLE_MEMORY
            !present -> State.NOT_INSTALLED
            else -> State.READY
        }

        /** SHA-256 of the model files the department may install; see docs/10-on-device-assistant.md. */
        /** SHA-256 of the sentence helper files the department may install (docs/15-gloss-to-text.md). */
        val KNOWN_SENTENCE_MODELS = setOf(
            // Qwen2.5 0.5B Instruct, Q4_K_M GGUF (Apache-2.0), from Qwen/Qwen2.5-0.5B-Instruct-GGUF; 491 MB.
            "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db",
        )

        val KNOWN_MODELS = setOf(
            // Gemma 3 1B instruction-tuned, Q4_K_M GGUF (Gemma Terms of Use), from ggml-org/gemma-3-1b-it-GGUF.
            "8ccc5cd1f1b3602548715ae25a66ed73fd5dc68a210412eea643eb20eb75a135",
        )
    }
}
