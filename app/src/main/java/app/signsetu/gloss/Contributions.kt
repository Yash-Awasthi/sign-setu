package app.signsetu.gloss

import app.signsetu.net.Http
import app.signsetu.net.TokenVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom

/** Version of the consent card's wording; a new wording asks again. */
const val CONSENT_VERSION = "v1"

/** Signs recorded for training (docs/15-gloss-to-text.md): every record is kept on the phone, and also queued for the
 * gateway only while the signer's opt-in stands. Landmarks, gloss and corrections only; never video or audio. The
 * queue is files, so it survives restarts; a failed upload waits and retries and never holds up the session. */
class Contributions(
    private val root: File,
    gatewayUrl: String,
    private val http: Http,
    private val vault: TokenVault,
    private val online: () -> Boolean,
    private val scope: CoroutineScope,
) {
    private val base = gatewayUrl.trimEnd('/')
    private val sessions = File(root, "sessions")
    private val queue = File(root, "queue")
    private val consentFile = File(root, "consent.txt")
    private val deletionPendingFile = File(root, "delete.pending")
    private var flusher: Job? = null
    private val uploadLock = Any()

    /** False in release builds: they record nothing and send nothing, even if a debug install left files behind. */
    @Volatile private var enabled = true

    /** Random and local: the only link between a signer and their uploads, and the key to deleting them. */
    val signer: String by lazy {
        val f = File(root, "signer.txt")
        f.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.length >= 16 } ?: run {
            val b = ByteArray(16).also { SecureRandom().nextBytes(it) }
            "s-" + b.joinToString("") { "%02x".format(it) }
        }.also { root.mkdirs(); f.writeText(it) }
    }

    val consented: Boolean get() = enabled && consentFile.exists() && consentFile.readText().trim() == CONSENT_VERSION

    // Same monitor as save's write, so a queued record cannot land after withdrawal clears the queue.
    fun setConsent(on: Boolean) {
        if (!enabled) return
        synchronized(this) {
            root.mkdirs()
            if (on) {
                consentFile.writeText(CONSENT_VERSION)
                kick()
            } else {
                // Withdrawal stops the queue at once: nothing not yet sent will be.
                consentFile.delete()
                flusher?.cancel()
                queue.listFiles()?.forEach { it.delete() }
            }
        }
    }

    /** Saves one record locally, and queues it when the signer has opted in. */
    fun save(kind: String, session: JsonObject, window: JsonObject, gloss: List<String>, correction: String? = null): Job {
        val record = buildJsonObject {
            put("kind", kind)
            put("session", session)
            put("window", window)
            put("gloss", JsonArray(gloss.map(::JsonPrimitive)))
            correction?.let { put("correction", it) }
        }
        val text = record.toString()
        // Off the caller's (often the main) thread; one writer at a time keeps names unique.
        return scope.launch(Dispatchers.IO) {
            if (!enabled) return@launch
            synchronized(this@Contributions) {
                val name = uniqueName(kind)
                runCatching { writeAtomic(File(sessions, name), text) }
                if (consented) runCatching { writeAtomic(File(queue, name), text) }
            }
            if (consented) kick()
        }
    }

    private fun uniqueName(kind: String): String {
        val ms = System.currentTimeMillis()
        var name = "${ms}_${kind}.json"
        var n = 1
        while (File(sessions, name).exists()) name = "${ms}_${kind}_${n++}.json"
        return name
    }

    /** Written beside and renamed over, so the uploader never reads (and then deletes) a half-written record. */
    private fun writeAtomic(target: File, text: String) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".part")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) { tmp.delete(); error("rename") }
    }

    private fun queued(): List<File> = queue.listFiles { f -> f.name.endsWith(".json") }?.sortedBy { it.name }.orEmpty()

    /** Local records, for the export that tools/ml/import_contrib.py reads. */
    fun saved(): List<File> = sessions.listFiles { f -> f.name.endsWith(".json") }?.sortedBy { it.name }.orEmpty()

    fun pending(): Int = queued().size

    /** Starts sending in the background unless already sending; waits longer after each failure, up to an hour. */
    @Synchronized
    fun kick() {
        if (!enabled || flusher?.isActive == true || base.isEmpty()) return
        flusher = scope.launch(Dispatchers.IO) {
            var wait = FIRST_RETRY_MS
            while (consented && !deletionPendingFile.exists() && pending() > 0) {
                if (sendBatch()) wait = FIRST_RETRY_MS else { delay(wait); wait = minOf(wait * 2, LAST_RETRY_MS) }
            }
        }
    }

    /** One upload of up to [BATCH] records; false when it should be retried later. */
    private fun sendBatch(): Boolean {
        return synchronized(uploadLock) { sendBatchLocked() }
    }

    private fun sendBatchLocked(): Boolean {
        val token = vault.get() ?: return false
        if (!online() || !consented || deletionPendingFile.exists()) return false
        val files = queued().take(BATCH)
        if (files.isEmpty()) return true
        val records = files.mapNotNull { f ->
            runCatching { f to kotlinx.serialization.json.Json.parseToJsonElement(f.readText()) }.getOrNull()
        }
        val body = buildJsonObject {
            put("signer", signer)
            put("consent", CONSENT_VERSION)
            put("records", JsonArray(records.map { it.second }))
            put("record_ids", JsonArray(records.map { JsonPrimitive(recordId(it.first)) }))
        }
        val reply = http.call("POST", "$base/v1/contrib", body.toString(), token, UPLOAD_TIMEOUT_MS) ?: return false
        return when {
            // Stored, or refused by the schema check: either way these records leave the queue.
            reply.ok || reply.code == 422 || reply.code == 413 -> { files.forEach { it.delete() }; true }
            else -> false
        }
    }

    /** Deletes local records and queued uploads older than [maxAgeMs]; the consent and the signer id are settings. */
    fun purgeExpired(nowMs: Long = System.currentTimeMillis(), maxAgeMs: Long = app.signsetu.data.session.LocalRetention.CONTRIB_MAX_AGE_MS): Int =
        synchronized(this) {
            var deleted = 0
            val files = sessions.listFiles().orEmpty().toList() + queue.listFiles().orEmpty().toList()
            for (file in files) {
                val stale = maxAgeMs <= 0 || nowMs - file.lastModified() >= maxAgeMs
                if ((stale || file.name.endsWith(".part")) && file.delete()) deleted++
            }
            deleted
        }

    /** Release builds send nothing: the consent and the queue a debug install left are discarded; local records stay
     * on the phone, where retention expires them, and are never uploaded. */
    fun restrictToDebug(debug: Boolean) {
        enabled = debug
        if (debug) return
        synchronized(this) {
            flusher?.cancel()
            flusher = null
            consentFile.delete()
            queue.listFiles()?.forEach { it.delete() }
        }
    }

    /** Removes every local record, queued upload, consent and signer id (Clear all data). */
    fun discardAll(): Int = synchronized(this) {
        flusher?.cancel()
        flusher = null
        app.signsetu.data.session.LocalRetention.purgeAll(root)
    }

    /** Asks the gateway to delete everything this signer sent; true once it has. */
    fun deleteMine(): Boolean = synchronized(this) outer@{
        val token = vault.get() ?: return@outer false
        root.mkdirs()
        try {
            deletionPendingFile.writeText("v1")
        } catch (_: java.io.IOException) {
            return@outer false
        }
        synchronized(uploadLock) inner@{
            if (http.call("DELETE", "$base/v1/contrib/$signer", null, token, UPLOAD_TIMEOUT_MS)?.ok != true) {
                return@inner false
            }
            flusher?.cancel()
            flusher = null
            val files = queue.listFiles()
            val cleared = (files != null || !queue.exists()) && files.orEmpty().all { it.delete() || !it.exists() }
            if (!cleared) {
                consentFile.delete()
                return@inner false
            }
            if (!deletionPendingFile.delete() && deletionPendingFile.exists()) return@inner false
            true
        }
    }

    private fun recordId(file: File): String =
        MessageDigest.getInstance("SHA-256").digest("$signer\u0000${file.name}".toByteArray()).toHexString()

    private companion object {
        const val BATCH = 10
        const val FIRST_RETRY_MS = 30_000L
        const val LAST_RETRY_MS = 3_600_000L
        const val UPLOAD_TIMEOUT_MS = 30_000
    }
}
