package app.signsetu.net

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** The station's local AI brain (brain/ in this repository): speech to text, translation, gloss expansion and a local
 * language model, on a computer on the same network. Only a private address is accepted, the connection trusts the
 * one certificate whose SHA-256 was entered at pairing, and every call carries the pairing token. Paired by the
 * brain's iroh ticket instead, it is reached from any network through IrohBrain, end-to-end encrypted to the brain's
 * key. Any failure returns null and the caller falls back to what the phone does alone. */
class BrainClient(private val vault: TokenVault) {
    @Serializable
    data class Pairing(val url: String, val token: String, val pin: String, val ticket: String? = null) {
        val viaIroh: Boolean get() = ticket != null
    }

    /** What the brain heard, in [lang], and the same words in the language asked for, when it translated them. */
    data class Heard(val text: String, val lang: String, val seconds: Double, val translated: String? = null)

    data class Status(val ms: Long, val llm: Boolean)

    @Volatile private var cached: Pairing? = null

    fun pairing(): Pairing? = cached ?: vault.get()?.let { runCatching { JSON.decodeFromString<Pairing>(it) }.getOrNull() }?.also { cached = it }

    val paired: Boolean get() = pairing() != null

    /** Checks the address, then the server, before keeping the pairing; false leaves any earlier pairing in place.
     * [address] is the brain's https:// LAN address (then [pin] is required) or its iroh ticket (no pin: the ticket
     * names the brain's key). */
    suspend fun pair(address: String, token: String, pin: String): Boolean {
        val a = address.trim()
        val p = if (a.startsWith("https://")) {
            Pairing(a.trimEnd('/'), token.trim(), pin.trim().lowercase().replace(":", "").replace(" ", ""))
        } else {
            if (!IrohBrain.validTicket(a)) return false
            Pairing("", token.trim(), "", ticket = a)
        }
        if (p.token.length < 20) return false
        if (!p.viaIroh && (!lanUrl(p.url) || p.pin.length != 64)) return false
        status(p) ?: return false
        if (!vault.put(JSON.encodeToString(Pairing.serializer(), p))) return false
        cached = p
        return true
    }

    fun forget() { vault.clear(); cached = null }

    suspend fun status(p: Pairing? = pairing()): Status? = withContext(Dispatchers.IO) {
        p ?: return@withContext null
        val t0 = System.nanoTime()
        // Over iroh the first call also finds a path, through a relay if need be.
        val r = call(p, "GET", "/v1/status", null, null, if (p.viaIroh) 20_000 else 5_000) ?: return@withContext null
        val llm = runCatching { JSON.parseToJsonElement(r).jsonObject["llm"]?.jsonPrimitive?.content == "true" }.getOrDefault(false)
        Status((System.nanoTime() - t0) / 1_000_000, llm)
    }

    /** [wav] is 16-bit mono PCM in a WAV container; it is sent once and not kept on either side. */
    suspend fun recognise(wav: ByteArray, lang: String?, to: String? = null): Heard? = withContext(Dispatchers.IO) {
        val p = pairing() ?: return@withContext null
        val boundary = "----signsetu" + System.nanoTime()
        val body = ByteArrayOutputStream().apply {
            fun line(s: String) = write((s + "\r\n").toByteArray())
            if (lang != null) { line("--$boundary"); line("Content-Disposition: form-data; name=\"lang\""); line(""); line(lang) }
            if (to != null) { line("--$boundary"); line("Content-Disposition: form-data; name=\"to\""); line(""); line(to) }
            line("--$boundary"); line("Content-Disposition: form-data; name=\"audio\"; filename=\"a.wav\""); line("Content-Type: audio/wav"); line("")
            write(wav); line(""); line("--$boundary--")
        }.toByteArray()
        val r = call(p, "POST", "/v1/asr", body, "multipart/form-data; boundary=$boundary", 60_000) ?: return@withContext null
        runCatching {
            val o = JSON.parseToJsonElement(r).jsonObject
            Heard(
                o.getValue("text").jsonPrimitive.content, o["lang"]?.jsonPrimitive?.content ?: (lang ?: ""), o["seconds"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                (o["translation"] as? kotlinx.serialization.json.JsonObject)?.get("text")?.jsonPrimitive?.content,
            )
        }.getOrNull()?.takeIf { it.text.isNotBlank() }
    }

    suspend fun translate(texts: List<String>, src: String, tgt: String): List<String>? = withContext(Dispatchers.IO) {
        val p = pairing() ?: return@withContext null
        val body = buildJsonObject { put("texts", JsonArray(texts.map(::JsonPrimitive))); put("src", src); put("tgt", tgt) }.toString()
        val r = call(p, "POST", "/v1/translate", body.toByteArray(), JSON_TYPE, 60_000) ?: return@withContext null
        runCatching { JSON.parseToJsonElement(r).jsonObject.getValue("texts").jsonArray.map { it.jsonPrimitive.content } }.getOrNull()
    }

    /** Draft ISL gloss for [text], limited to [vocabulary]: the words the phone can sign. */
    suspend fun gloss(text: String, lang: String, vocabulary: Collection<String>): List<String>? = withContext(Dispatchers.IO) {
        val p = pairing() ?: return@withContext null
        val body = buildJsonObject { put("text", text); put("lang", lang); put("vocabulary", JsonArray(vocabulary.map(::JsonPrimitive))) }.toString()
        val r = call(p, "POST", "/v1/gloss", body.toByteArray(), JSON_TYPE, 60_000) ?: return@withContext null
        runCatching { JSON.parseToJsonElement(r).jsonObject.getValue("gloss").jsonArray.map { it.jsonPrimitive.content } }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private suspend fun call(p: Pairing, method: String, path: String, body: ByteArray?, type: String?, timeoutMs: Int): String? {
        p.ticket?.let { t -> return IrohBrain.call(t, method, path, body, type, p.token, timeoutMs.toLong())?.takeIf { it.first == 200 }?.second?.decodeToString() }
        return https(p, method, path, body, type, timeoutMs)
    }

    private fun https(p: Pairing, method: String, path: String, body: ByteArray?, type: String?, timeoutMs: Int): String? = runCatching {
        val c = URL(p.url + path).openConnection() as HttpsURLConnection
        c.sslSocketFactory = pinned(p.pin)
        // The certificate is self-signed for a LAN address; its pin, checked above, is the identity.
        c.setHostnameVerifier { _, _ -> true }
        c.requestMethod = method
        c.connectTimeout = 3_000
        c.readTimeout = timeoutMs
        c.instanceFollowRedirects = false
        c.useCaches = false
        c.setRequestProperty("Authorization", "Bearer ${p.token}")
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", type)
            c.setFixedLengthStreamingMode(body.size)
            c.outputStream.use { it.write(body) }
        }
        try {
            if (c.responseCode != HttpURLConnection.HTTP_OK) null else c.inputStream.use { it.readBytes().decodeToString() }
        } finally { c.disconnect() }
    }.getOrNull()

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }
        private const val JSON_TYPE = "application/json"
        private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")

        /** https only, to a private, link-local or loopback address, or a .local name. */
        fun lanUrl(url: String): Boolean = runCatching {
            val u = URL(url)
            if (u.protocol != "https") return false
            val host = u.host
            if (host.endsWith(".local")) return true
            // Literal addresses only; a name would need a DNS lookup that could point anywhere.
            if (!IPV4.matches(host) && !host.startsWith("[")) return false
            val a = InetAddress.getByName(host)
            a.isSiteLocalAddress || a.isLinkLocalAddress || a.isLoopbackAddress
        }.getOrDefault(false)

        private fun pinned(pin: String): SSLSocketFactory {
            val trust = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = throw CertificateException("server only")
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                    val leaf = chain?.firstOrNull() ?: throw CertificateException("no certificate")
                    val got = MessageDigest.getInstance("SHA-256").digest(leaf.encoded).toHexString()
                    if (!MessageDigest.isEqual(got.toByteArray(), pin.toByteArray())) throw CertificateException("certificate is not the paired brain")
                }
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            return SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }.socketFactory
        }
    }
}
