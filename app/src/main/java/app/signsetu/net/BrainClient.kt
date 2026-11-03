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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** The station's local AI brain (brain/ in this repository): speech to text, translation, gloss expansion and a local
 * language model, on a computer on the same network. Only a private address is accepted, the connection trusts the
 * one certificate whose SHA-256 was entered at pairing, and every call carries the pairing token. Paired by the
 * brain's iroh ticket instead, it is reached from any network through IrohBrain, end-to-end encrypted to the brain's
 * key. Any failure returns null and the caller falls back to what the phone does alone.
 *
 * The station link is off until the officer turns it on ([setOn]), so the radio is not kept busy for nothing. While it
 * is on and the app is in front, the station's heartbeat ([state]) says what it can do; a feature it has not
 * announced is never asked for, so nothing is sent only to fail (docs/18-station.md). */
class BrainClient(
    private val vault: TokenVault,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    /** What a station can do for the phone; its heartbeat names the ones it has (brain/app.py, "caps").
     * [ASR] is one service: the words shown live while the person speaks, and the whole take's text kept after. */
    enum class Feature(val key: String) { ASR("asr"), ISL("isl"), LLM("llm"), GLOSS("gloss"), ARCHIVE("archive"), PLAN("plan") }

    /** [on] is the officer's switch; [offered] is what the latest heartbeat named, null until one arrives and again once
     * they stop; [allowed] is what the officer lets go to the station (Settings, Local AI brain). */
    data class Station(val on: Boolean = false, val offered: Set<Feature>? = null, val allowed: Set<Feature> = Feature.entries.toSet()) {
        val online: Boolean get() = on && offered != null
        /** The station has [f] and the officer has not turned it off. */
        fun uses(f: Feature): Boolean = online && offered?.contains(f) == true && f in allowed
    }

    private val _state = MutableStateFlow(Station())
    val state: StateFlow<Station> = _state.asStateFlow()
    @Volatile private var foreground = true
    private var beating: Job? = null
    private var resetting: Job? = null

    /** The officer's switch. Off closes the iroh endpoint, so the phone keeps no connection open. */
    fun setOn(on: Boolean) {
        _state.update { it.copy(on = on) }
        restartBeat()
    }

    /** The features the officer lets go to the station; the rest stay on this phone even when the station has them. */
    fun setAllowed(allowed: Set<Feature>) {
        _state.update { it.copy(allowed = allowed) }
    }

    /** The phone's default network changed (Wi-Fi, mobile data). Android denies iroh the netlink socket it watches
     * interfaces with, so the endpoint would keep sockets bound to the old network: it is dropped and dialled afresh. */
    fun networkChanged() {
        if (!_state.value.on) return
        scope.launch { IrohBrain.reset(); restartBeat() }
    }

    /** The app went behind another or came back: the heartbeat runs only while it is in front. */
    fun setForeground(front: Boolean) {
        if (foreground == front) return
        foreground = front
        restartBeat()
    }

    // Called from the main thread and from pairing on IO: one restart at a time, so no two heartbeats run.
    @Synchronized
    private fun restartBeat() {
        beating?.cancel()
        beating = null
        _state.update { it.copy(offered = null) }
        if (!_state.value.on || !foreground) {
            resetting = scope.launch { IrohBrain.reset() }
            return
        }
        val closing = resetting
        beating = scope.launch {
            // A quick off and on: the endpoint is closed first, so it cannot cut the connection this heartbeat dials.
            closing?.join()
            var wait = 2_000L
            while (isActive) {
                // Nothing paired, nothing to listen for: pair() and forget() start this again.
                val p = pairing() ?: return@launch
                runCatching {
                    if (p.ticket != null) {
                        IrohBrain.beat(p.ticket, p.token) { code, body -> if (code == 200) { heard(body.decodeToString()); wait = 2_000L } }
                    } else {
                        // On the station network alone there is no stream to hold open: the status is asked for instead.
                        while (isActive) { heard(https(p, "GET", "/v1/status", null, null, 5_000) ?: break); wait = 2_000L; delay(BEAT_MS) }
                    }
                }
                // A heartbeat cancelled by a restart must not clear what the new one has heard.
                if (!isActive) return@launch
                _state.update { it.copy(offered = null) }
                delay(wait)
                // Capped low: a station back from a restart is found within 10 s, at the cost of one small dial per try.
                wait = (wait * 2).coerceAtMost(10_000L)
            }
        }
    }

    private fun heard(status: String) {
        val offered = runCatching {
            val c = JSON.parseToJsonElement(status).jsonObject.getValue("caps").jsonObject
            Feature.entries.filter { c[it.key]?.jsonPrimitive?.content == "true" }.toSet()
        }.getOrNull()
        paused.clear() // the beat says afresh what the station offers
        _state.update { it.copy(offered = offered) }
    }

    // Services that failed since the last heartbeat. The station answers a service that broke with 503 and
    // {"unavailable": service}, and drops it from its heartbeat; a call with no reply in time counts the same. Either
    // way the phone stops asking at once and waits for a heartbeat that offers the service again.
    private val paused: MutableSet<Feature> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    /** Whether to send [f] to the station: the link is on, its heartbeat offers [f], the officer allows it, and it has
     * not failed since that heartbeat. Otherwise the phone does without, at once. */
    fun can(f: Feature): Boolean = _state.value.uses(f) && f !in paused

    /** Notes the outcome of one call for [f]: null, a refusal or no usable reply, pauses it until the next heartbeat. */
    private fun <T> tally(f: Feature, reply: T?): T? = reply.also { if (it == null) paused += f }
    @Serializable
    data class Pairing(val url: String, val token: String, val pin: String, val ticket: String? = null) {
        val viaIroh: Boolean get() = ticket != null
    }

    /** What the brain heard, in [lang], and the same words in the language asked for, when it translated them. */
    data class Heard(val text: String, val lang: String, val seconds: Double, val translated: String? = null)

    data class Status(val ms: Long, val llm: Boolean)

    /** Words heard so far in a live take: [text] is settled, [tentative] may still change; [end] once they stopped. */
    data class Live(val text: String, val tentative: String, val end: Boolean)

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
            // A ticket copied from a wrapped terminal line carries breaks; a ticket itself never has whitespace.
            val ticket = a.filterNot(Char::isWhitespace)
            if (!IrohBrain.validTicket(ticket)) return false
            Pairing("", token.trim(), "", ticket = ticket)
        }
        if (p.token.length < 20) return false
        if (!p.viaIroh && (!lanUrl(p.url) || p.pin.length != 64)) return false
        status(p) ?: return false
        if (!vault.put(JSON.encodeToString(Pairing.serializer(), p))) return false
        cached = p
        restartBeat()
        return true
    }

    fun forget() { vault.clear(); cached = null; restartBeat() }

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
        val p = pairing()?.takeIf { can(Feature.ASR) } ?: return@withContext null
        val boundary = "----signsetu" + System.nanoTime()
        val body = ByteArrayOutputStream().apply {
            fun line(s: String) = write((s + "\r\n").toByteArray())
            if (lang != null) { line("--$boundary"); line("Content-Disposition: form-data; name=\"lang\""); line(""); line(lang) }
            if (to != null) { line("--$boundary"); line("Content-Disposition: form-data; name=\"to\""); line(""); line(to) }
            line("--$boundary"); line("Content-Disposition: form-data; name=\"audio\"; filename=\"a.wav\""); line("Content-Type: audio/wav"); line("")
            write(wav); line(""); line("--$boundary--")
        }.toByteArray()
        val r = tally(Feature.ASR, call(p, "POST", "/v1/asr", body, "multipart/form-data; boundary=$boundary", 60_000)) ?: return@withContext null
        runCatching {
            val o = JSON.parseToJsonElement(r).jsonObject
            Heard(
                o.getValue("text").jsonPrimitive.content, o["lang"]?.jsonPrimitive?.content ?: (lang ?: ""), o["seconds"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                (o["translation"] as? kotlinx.serialization.json.JsonObject)?.get("text")?.jsonPrimitive?.content,
            )
        }.getOrNull()?.takeIf { it.text.isNotBlank() }
    }

    /** Opens a live take: the brain transcribes the pieces sent by [liveFeed] as they come. Null when it cannot. */
    suspend fun liveStart(lang: String?): String? = withContext(Dispatchers.IO) {
        val p = pairing()?.takeIf { can(Feature.ASR) } ?: return@withContext null
        val body = buildJsonObject { lang?.let { put("lang", it) } }.toString()
        val r = tally(Feature.ASR, call(p, "POST", "/v1/live", body.toByteArray(), JSON_TYPE, 5_000)) ?: return@withContext null
        runCatching { JSON.parseToJsonElement(r).jsonObject.getValue("id").jsonPrimitive.content }.getOrNull()
    }

    /** Sends [pcm] (16 kHz mono 16-bit, may be empty) and returns the words so far. */
    suspend fun liveFeed(id: String, pcm: ByteArray): Live? = withContext(Dispatchers.IO) {
        val p = pairing()?.takeIf { _state.value.on } ?: return@withContext null
        tally(Feature.ASR, live(call(p, "POST", "/v1/live/$id", pcm, "application/octet-stream", 5_000)))
    }

    suspend fun liveStop(id: String): Live? = withContext(Dispatchers.IO) {
        val p = pairing()?.takeIf { _state.value.on } ?: return@withContext null
        live(call(p, "POST", "/v1/live/$id/stop", ByteArray(0), "application/octet-stream", 5_000))
    }

    private fun live(r: String?): Live? = r?.let {
        runCatching {
            val o = JSON.parseToJsonElement(it).jsonObject
            Live(o.getValue("text").jsonPrimitive.content, o["tentative"]?.jsonPrimitive?.content.orEmpty(), o["end"]?.jsonPrimitive?.content == "true")
        }.getOrNull()
    }

    /** The station reader's likeliest words for one sign: [frames] as StationExport.frameJson writes them. */
    suspend fun sign(frames: JsonArray, aspect: Float, timeoutMs: Int): List<Pair<String, Float>>? = withContext(Dispatchers.IO) {
        val p = pairing()?.takeIf { can(Feature.ISL) } ?: return@withContext null
        val body = buildJsonObject { put("aspect", aspect); put("frames", frames); put("top", 20) }.toString()
        val r = call(p, "POST", "/v1/sign", body.toByteArray(), JSON_TYPE, timeoutMs)
        val guesses = r?.let {
            runCatching {
                JSON.parseToJsonElement(it).jsonObject.getValue("guesses").jsonArray.map { g ->
                    val o = g.jsonObject
                    o.getValue("word").jsonPrimitive.content to o.getValue("score").jsonPrimitive.content.toFloat()
                }
            }.getOrNull()
        }
        // An empty list is an answer (no shoulders in view); no reply, or one that does not parse, is a miss.
        tally(Feature.ISL, guesses)?.takeIf { it.isNotEmpty() }
    }

    /** One reply from the station's local language model, for [system] and [user]; null when it has none. */
    suspend fun chat(system: String, user: String, maxTokens: Int, timeoutMs: Int = 60_000, names: Collection<String> = emptyList(), details: Collection<String> = emptyList()): String? = withContext(Dispatchers.IO) {
        val p = pairing()?.takeIf { can(Feature.LLM) } ?: return@withContext null
        val messages = JsonArray(listOf(
            buildJsonObject { put("role", "system"); put("content", system) },
            buildJsonObject { put("role", "user"); put("content", user) },
        ))
        val body = buildJsonObject {
            put("messages", messages); put("max_tokens", maxTokens.coerceAtMost(1024))
            // A station whose model is behind an API masks these before the text leaves it.
            put("names", JsonArray(names.map(::JsonPrimitive))); put("details", JsonArray(details.map(::JsonPrimitive)))
        }.toString()
        val r = tally(Feature.LLM, call(p, "POST", "/v1/chat", body.toByteArray(), JSON_TYPE, timeoutMs)) ?: return@withContext null
        runCatching { JSON.parseToJsonElement(r).jsonObject.getValue("text").jsonPrimitive.content }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /** The station's next step for the conversation [turns] (a JSON array of role and text): one of [items], or a short
     * sentence of its model's own for the phone to check. Null when it has no model, failed, or nothing fitted. */
    suspend fun plan(turns: String, items: List<PlanItem>, timeoutMs: Int, names: Collection<String> = emptyList(), details: Collection<String> = emptyList(), lang: String = "en"): Planned? = withContext(Dispatchers.IO) {
        val p = pairing()?.takeIf { can(Feature.PLAN) } ?: return@withContext null
        val body = buildJsonObject {
            put("turns", JSON.parseToJsonElement(turns))
            put("items", JsonArray(items.map { i -> buildJsonObject { put("id", i.id); put("text", i.text); put("cues", JsonArray(i.cues.map(::JsonPrimitive))) } }))
            put("names", JsonArray(names.map(::JsonPrimitive))); put("details", JsonArray(details.map(::JsonPrimitive)))
            put("lang", lang)
        }.toString()
        val r = tally(Feature.PLAN, call(p, "POST", "/v1/plan", body.toByteArray(), JSON_TYPE, timeoutMs)) ?: return@withContext null
        runCatching {
            val o = JSON.parseToJsonElement(r).jsonObject
            fun text(key: String) = o[key]?.jsonPrimitive?.contentOrNull
            val id = text("id")
            val say = text("say")?.takeIf { it.isNotBlank() }
            if (id == null && say == null) return@runCatching null
            Planned(id, text("word").orEmpty(), text("model").orEmpty(), say, text("why").orEmpty(), text("caption")?.takeIf { it.isNotBlank() })
        }.getOrNull()
    }

    data class PlanItem(val id: String, val text: String, val cues: List<String>)
    /** [id] a bank item, or [say] a sentence the model wrote, with [caption], the same in the person's language; [word]
     * the cue word it read, [why] its short reason. */
    data class Planned(val id: String?, val word: String, val model: String, val say: String? = null, val why: String = "", val caption: String? = null)

    /** Sends one conversation's zip to the station's inbox in pieces; [progress] gets the share sent, 0 to 1. Returns
     * where the station saved it (its full path on that computer), or null when it could not be sent whole. */
    suspend fun archive(name: String, zip: ByteArray, progress: (Float) -> Unit = {}): String? = withContext(Dispatchers.IO) {
        val p = pairing()?.takeIf { can(Feature.ARCHIVE) } ?: return@withContext null
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(zip).toHexString()
        val start = buildJsonObject { put("name", name); put("size", zip.size); put("sha256", sha) }.toString()
        val id = tally(Feature.ARCHIVE, call(p, "POST", "/v1/archive", start.toByteArray(), JSON_TYPE, 20_000)
            ?.let { runCatching { JSON.parseToJsonElement(it).jsonObject.getValue("id").jsonPrimitive.content }.getOrNull() }) ?: return@withContext null
        var at = 0
        var tries = 0
        while (at < zip.size) {
            val end = minOf(zip.size, at + ARCHIVE_PIECE)
            val held = call(p, "POST", "/v1/archive/$id?at=$at", zip.copyOfRange(at, end), "application/octet-stream", 60_000)
                ?.let { runCatching { JSON.parseToJsonElement(it).jsonObject.getValue("held").jsonPrimitive.content.toInt() }.getOrNull() }
                // The station only ever moves forward (brain/inbox.py); anything else is treated as a lost reply.
                ?.takeIf { it in (at + 1)..zip.size }
            // A lost reply: the same piece goes again, and the station takes it once.
            if (held == null) { if (++tries > 3) return@withContext null; delay(1_000L * tries); continue }
            tries = 0
            at = held
            progress(at.toFloat() / zip.size)
        }
        call(p, "POST", "/v1/archive/$id/done", ByteArray(0), "application/octet-stream", 60_000)?.let {
            runCatching {
                val o = JSON.parseToJsonElement(it).jsonObject
                o["path"]?.jsonPrimitive?.content ?: o.getValue("saved").jsonPrimitive.content
            }.getOrNull()
        }
    }

    suspend fun translate(texts: List<String>, src: String, tgt: String): List<String>? = withContext(Dispatchers.IO) {
        val p = pairing()?.takeIf { _state.value.online } ?: return@withContext null
        val body = buildJsonObject { put("texts", JsonArray(texts.map(::JsonPrimitive))); put("src", src); put("tgt", tgt) }.toString()
        val r = call(p, "POST", "/v1/translate", body.toByteArray(), JSON_TYPE, 60_000) ?: return@withContext null
        runCatching { JSON.parseToJsonElement(r).jsonObject.getValue("texts").jsonArray.map { it.jsonPrimitive.content } }.getOrNull()
    }

    /** Draft ISL gloss for [text], limited to [vocabulary]: the words the phone can sign. */
    suspend fun gloss(text: String, lang: String, vocabulary: Collection<String>): List<String>? = withContext(Dispatchers.IO) {
        val p = pairing()?.takeIf { can(Feature.GLOSS) } ?: return@withContext null
        val body = buildJsonObject { put("text", text); put("lang", lang); put("vocabulary", JsonArray(vocabulary.map(::JsonPrimitive))) }.toString()
        val r = tally(Feature.GLOSS, call(p, "POST", "/v1/gloss", body.toByteArray(), JSON_TYPE, 60_000)) ?: return@withContext null
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
            if (c.responseCode != HttpURLConnection.HTTP_OK) null else c.inputStream.use { it.readCapped(MAX_REPLY) }?.decodeToString()
        } finally { c.disconnect() }
    }.getOrNull()

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }
        private const val JSON_TYPE = "application/json"
        /** The station takes pieces of up to 4 MB (brain/inbox.py); 2 MB keeps one piece's retry short. */
        private const val ARCHIVE_PIECE = 2 * 1024 * 1024
        /** Asked for this often on the station network, where no heartbeat stream is held open. */
        private const val BEAT_MS = 5_000L
        /** The brain's largest reply, as over iroh; a longer one is refused rather than read into memory. */
        private const val MAX_REPLY = 8 shl 20
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
            // Java's site-local test knows only the retired fec0::/10; IPv6 LANs use unique local fc00::/7.
            val uniqueLocal = a is java.net.Inet6Address && (a.address[0].toInt() and 0xfe) == 0xfc
            a.isSiteLocalAddress || a.isLinkLocalAddress || a.isLoopbackAddress || uniqueLocal
        }.getOrDefault(false)

        /** Null when the reply is longer than [max]. */
        private fun java.io.InputStream.readCapped(max: Int): ByteArray? {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = read(buf)
                if (n < 0) return out.toByteArray()
                if (out.size() + n > max) return null
                out.write(buf, 0, n)
            }
        }

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
