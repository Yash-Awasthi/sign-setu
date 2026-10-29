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
    private var flusher: Job? = null

    /** Random and local: the only link between a signer and their uploads, and the key to deleting them. */
    val signer: String by lazy {
        val f = File(root, "signer.txt")
        f.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.length >= 16 } ?: run {
            val b = ByteArray(16).also { SecureRandom().nextBytes(it) }
            "s-" + b.joinToString("") { "%02x".format(it) }
        }.also { root.mkdirs(); f.writeText(it) }
    }

    val consented: Boolean get() = consentFile.exists() && consentFile.readText().trim() == CONSENT_VERSION

    fun setConsent(on: Boolean) {
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

    /** Saves one record locally, and queues it when the signer has opted in. */
    fun save(kind: String, session: JsonObject, window: JsonObject, gloss: List<String>, correction: String? = null) {
        val record = buildJsonObject {
            put("kind", kind)
            put("session", session)
            put("window", window)
            put("gloss", JsonArray(gloss.map(::JsonPrimitive)))
            correction?.let { put("correction", it) }
        }
        val name = "${System.currentTimeMillis()}_${kind}.json"
        runCatching { sessions.mkdirs(); File(sessions, name).writeText(record.toString()) }
        if (consented) {
            runCatching { queue.mkdirs(); File(queue, name).writeText(record.toString()) }
            kick()
        }
    }

    /** Local records, for the export that tools/ml/import_contrib.py reads. */
    fun saved(): List<File> = sessions.listFiles()?.sortedBy { it.name }.orEmpty()

    fun pending(): Int = queue.listFiles()?.size ?: 0

    /** Starts sending in the background unless already sending; waits longer after each failure, up to an hour. */
    fun kick() {
        if (flusher?.isActive == true || base.isEmpty()) return
        flusher = scope.launch(Dispatchers.IO) {
            var wait = FIRST_RETRY_MS
            while (consented && pending() > 0) {
                if (sendBatch()) wait = FIRST_RETRY_MS else { delay(wait); wait = minOf(wait * 2, LAST_RETRY_MS) }
            }
        }
    }

    /** One upload of up to [BATCH] records; false when it should be retried later. */
    private fun sendBatch(): Boolean {
        val token = vault.get() ?: return false
        if (!online() || !consented) return false
        val files = queue.listFiles()?.sortedBy { it.name }?.take(BATCH).orEmpty()
        if (files.isEmpty()) return true
        val records = files.mapNotNull { f -> runCatching { kotlinx.serialization.json.Json.parseToJsonElement(f.readText()) }.getOrNull() }
        val body = buildJsonObject {
            put("signer", signer)
            put("consent", CONSENT_VERSION)
            put("records", JsonArray(records))
        }
        val reply = http.call("POST", "$base/v1/contrib", body.toString(), token, UPLOAD_TIMEOUT_MS) ?: return false
        return when {
            // Stored, or refused by the schema check: either way these records leave the queue.
            reply.ok || reply.code == 422 || reply.code == 413 -> { files.forEach { it.delete() }; true }
            else -> false
        }
    }

    /** Asks the gateway to delete everything this signer sent; true once it has. */
    fun deleteMine(): Boolean {
        val token = vault.get() ?: return false
        return http.call("DELETE", "$base/v1/contrib/$signer", null, token, UPLOAD_TIMEOUT_MS)?.ok == true
    }

    private companion object {
        const val BATCH = 10
        const val FIRST_RETRY_MS = 30_000L
        const val LAST_RETRY_MS = 3_600_000L
        const val UPLOAD_TIMEOUT_MS = 30_000
    }
}
