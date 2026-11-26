package app.signsetu

import app.signsetu.net.BrainClient
import app.signsetu.net.TokenVault
import java.io.File
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The phone's station client (app/signsetu/net/BrainClient.kt) and the station (brain/brain/app.py) must agree on
 * every route, body key and timeout. The table below is the contract; both files are read as text and checked against
 * it, so editing either side without editing the table fails here. */
class BrainContractTest {
    private val clientSrc = File("src/main/java/app/signsetu/net/BrainClient.kt").readText()
    private val brainSrc = File("../brain/brain/app.py").readText()

    private class FakeVault(private var raw: String?) : TokenVault {
        override fun get() = raw
        override fun put(token: String): Boolean { raw = token; return true }
        override fun clear() { raw = null }
    }

    /** Fails the test if any URL is opened while it is the JVM's proxy selector. */
    private class Tripwire : ProxySelector() {
        val hits = AtomicInteger()
        override fun select(uri: URI): MutableList<Proxy> {
            hits.incrementAndGet()
            throw IllegalStateException("network touched: $uri")
        }
        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
    }

    private data class Api(
        val method: String,
        val route: String,
        val client: String,
        val funName: String,
        val timeout: String,
        val model: String? = null,
        val clientKeys: Set<String> = emptySet(),
        val extras: Set<String> = emptySet(),
        val optional: Set<String> = emptySet(),
        val nested: Set<String> = emptySet(),
    )

    private val apis = listOf(
        Api("GET", "/v1/status", "/v1/status", "status", "if (p.viaIroh) 20_000 else 5_000"),
        Api("POST", "/v1/asr", "/v1/asr", "recognise", "60_000", clientKeys = setOf("audio", "lang", "to")),
        Api("POST", "/v1/live", "/v1/live", "liveStart", "5_000", "LiveIn", setOf("lang")),
        Api("POST", "/v1/live/{tid}", "/v1/live/${D}id", "liveFeed", "5_000"),
        Api("POST", "/v1/live/{tid}/stop", "/v1/live/${D}id/stop", "liveStop", "5_000"),
        Api("POST", "/v1/sign", "/v1/sign", "sign", "timeoutMs", "SignIn", setOf("aspect", "frames", "top"), optional = setOf("top")),
        Api("POST", "/v1/chat", "/v1/chat", "chat", "timeoutMs", "ChatIn", setOf("messages", "max_tokens", "names", "details"), extras = setOf("temperature")),
        Api("POST", "/v1/plan", "/v1/plan", "plan", "timeoutMs", "PlanIn", setOf("turns", "items", "names", "details", "lang"), nested = setOf("id", "text", "cues")),
        Api("POST", "/v1/archive", "/v1/archive", "archive", "20_000", "ArchiveIn", setOf("name", "size", "sha256")),
        Api("POST", "/v1/archive/{tid}", "/v1/archive/${D}id?at=${D}at", "archive", "60_000"),
        Api("POST", "/v1/archive/{tid}/done", "/v1/archive/${D}id/done", "archive", "60_000"),
        Api("POST", "/v1/translate", "/v1/translate", "translate", "60_000", "TranslateIn", setOf("texts", "src", "tgt")),
        Api("POST", "/v1/gloss", "/v1/gloss", "gloss", "60_000", "GlossIn", setOf("text", "lang", "vocabulary"), optional = setOf("vocabulary")),
    )

    @Test
    fun theContractTableMatchesBothSources() {
        val errors = contractErrors(clientSrc, brainSrc)
        assertTrue("contract drift:\n" + errors.joinToString("\n"), errors.isEmpty())

        // The same checks must catch drift, so the table is load-bearing rather than decorative.
        val routeDrift = contractErrors(clientSrc, brainSrc.replace("@app.post(\"/v1/gloss\")", "@app.post(\"/v1/glossary\")"))
        assertTrue("a changed brain route must be caught", routeDrift.isNotEmpty())
        val keyDrift = contractErrors(clientSrc.replace("put(\"aspect\", aspect)", "put(\"angle\", aspect)"), brainSrc)
        assertTrue("a changed client key must be caught", keyDrift.isNotEmpty())
        val fieldDrift = contractErrors(clientSrc, brainSrc.replace("temperature: float", "temp: float"))
        assertTrue("a changed brain field must be caught", fieldDrift.isNotEmpty())
    }

    @Test
    fun everyCallFallsBackOfflineWithoutTouchingTheNetwork() = runBlocking {
        val trip = Tripwire()
        val previous = ProxySelector.getDefault()
        ProxySelector.setDefault(trip)
        try {
            val alone = BrainClient(FakeVault(null))
            assertFalse(alone.paired)
            assertNull(alone.status())
            assertNull(alone.recognise(ByteArray(0), null))
            assertNull(alone.liveStart("en"))
            assertNull(alone.liveFeed("id", ByteArray(0)))
            assertNull(alone.liveStop("id"))
            assertNull(alone.sign(JsonArray(emptyList()), 1.0f, 1_000))
            assertNull(alone.chat("s", "u", 16))
            assertNull(alone.plan("[]", emptyList(), 1_000))
            assertNull(alone.archive("n", byteArrayOf(1)))
            assertNull(alone.translate(listOf("a"), "en", "hi"))
            assertNull(alone.gloss("a", "en", emptyList()))
            assertTrue("a call with no pairing opened a URL", trip.hits.get() == 0)

            // A paired but unreachable station still falls back, and proves the tripwire can fire.
            val dead = BrainClient(FakeVault(pairingJson("https://127.0.0.1:1")))
            assertTrue(dead.paired)
            assertNull(dead.status())
            assertTrue("the tripwire never fired", trip.hits.get() > 0)
        } finally {
            ProxySelector.setDefault(previous)
        }
    }

    @Test
    fun lanAddressesAndPairingShapesAreAsDocumented() {
        assertTrue(BrainClient.lanUrl("https://192.168.1.5:8443"))
        assertTrue(BrainClient.lanUrl("https://10.0.0.7"))
        assertTrue(BrainClient.lanUrl("https://172.16.4.9"))
        assertTrue(BrainClient.lanUrl("https://127.0.0.1"))
        assertTrue(BrainClient.lanUrl("https://169.254.10.2"))
        assertTrue(BrainClient.lanUrl("https://station.local"))
        assertFalse(BrainClient.lanUrl("http://192.168.1.5"))
        assertFalse(BrainClient.lanUrl("https://8.8.8.8"))
        assertFalse(BrainClient.lanUrl("https://brain.example.com"))

        val json = Json { ignoreUnknownKeys = true }
        val token = "t".repeat(24)
        val pin = "a".repeat(64)
        val noTicket = json.decodeFromString(
            BrainClient.Pairing.serializer(),
            "{\"url\":\"https://192.168.1.5\",\"token\":\"$token\",\"pin\":\"$pin\"}",
        )
        assertNull(noTicket.ticket)
        assertFalse(noTicket.viaIroh)
        val withTicket = json.decodeFromString(
            BrainClient.Pairing.serializer(),
            "{\"url\":\"\",\"token\":\"$token\",\"pin\":\"\",\"ticket\":\"ticket-bytes\"}",
        )
        assertTrue(withTicket.viaIroh)
    }

    @Test
    fun everyFeatureKeyIsACapAndLiveIsACapAlone() {
        val errors = capsAndFeatureErrors(clientSrc, brainSrc)
        assertTrue("caps/Feature drift:\n" + errors.joinToString("\n"), errors.isEmpty())
        assertTrue("live" in brainCapsKeys(brainSrc))
        assertTrue(BrainClient.Feature.entries.none { it.key == "live" })
        assertTrue(BrainClient.Feature.entries.all { it.key in brainCapsKeys(brainSrc) })
    }

    private fun contractErrors(clientSrc: String, brainSrc: String): List<String> {
        val errors = mutableListOf<String>()
        fun bad(message: String) { errors += message }

        val routes = brainRoutes(brainSrc)
        val expectedRoutes = apis.map { it.route }.toSet() + "/healthz"
        if (routes.keys != expectedRoutes) bad("brain routes ${routes.keys.sorted()} != ${expectedRoutes.sorted()}")

        val calls = clientCalls(clientSrc)
        val expectedCalls = apis.map { it.client }.toSet()
        if (calls.keys != expectedCalls) bad("client paths ${calls.keys.sorted()} != ${expectedCalls.sorted()}")

        for (a in apis) {
            val call = calls[a.client]
            if (call == null) { bad("client has no ${a.method} ${a.client}"); continue }
            if (call.method != a.method) bad("${a.client} method ${call.method} != ${a.method}")
            if (call.timeout != a.timeout) bad("${a.client} timeout '${call.timeout}' != '${a.timeout}'")
            val brainPath = a.client.replace("${D}id", "{tid}").substringBefore("?")
            if (brainPath != a.route) bad("${a.client} maps to $brainPath, table says ${a.route}")
            if (brainPath !in routes) bad("brain has no route $brainPath")

            if (a.model == null) {
                if (a.funName == "recognise") {
                    if (clientAsrFields(clientSrc) != a.clientKeys) bad("asr form fields ${clientAsrFields(clientSrc)} != ${a.clientKeys}")
                    val handler = routes["/v1/asr"]
                    if (handler == null) bad("brain route /v1/asr missing") else {
                        val fields = brainFormFields(brainSrc, handler)
                        if (fields != a.clientKeys) bad("asr brain fields $fields != ${a.clientKeys}")
                    }
                } else if (apis.count { it.funName == a.funName } == 1 && clientBodyKeys(clientSrc, a.funName).isNotEmpty()) {
                    bad("${a.funName} sends a body the table does not list")
                }
                continue
            }

            val fields = brainModelFields(brainSrc, a.model)
            if (fields == null) { bad("brain has no pydantic class ${a.model}"); continue }
            val expected = a.clientKeys + a.extras
            if (fields != expected) bad("${a.model} fields ${fields.sorted()} != ${expected.sorted()}")
            if (a.extras.any { it in a.clientKeys }) bad("${a.model} extras listed as client keys")
            for (o in a.optional) if (!fieldHasDefault(brainSrc, a.model, o)) bad("${a.model}.$o needs a default")
            val keys = clientBodyKeys(clientSrc, a.funName) - a.nested
            if (keys != a.clientKeys) bad("${a.funName} keys ${keys.sorted()} != ${a.clientKeys.sorted()}")
        }

        val chatDefault = Regex("fun chat\\([^)]*timeoutMs: Int = (\\d[\\d_]*)").find(clientSrc)?.groupValues?.get(1)
        if (chatDefault != "60_000") bad("chat default timeout is '$chatDefault', table says 60_000")

        val constants = mapOf(
            "c.connectTimeout = 3_000" to clientSrc,
            "BEAT_MS = 5_000L" to clientSrc,
            "MAX_REPLY = 8 shl 20" to clientSrc,
            "ARCHIVE_PIECE = 2 * 1024 * 1024" to clientSrc,
            "MAX_BODY = 8 * 1024 * 1024" to brainSrc,
        )
        for ((literal, text) in constants) if (!text.contains(literal)) bad("missing constant: $literal")

        errors += capsAndFeatureErrors(clientSrc, brainSrc)
        return errors
    }

    private fun capsAndFeatureErrors(clientSrc: String, brainSrc: String): List<String> {
        val errors = mutableListOf<String>()
        val caps = brainCapsKeys(brainSrc)
        if (caps != CAPS) errors += "caps keys ${caps.sorted()} != ${CAPS.sorted()}"
        val features = clientFeatureKeys(clientSrc)
        if (features != FEATURE_KEYS) errors += "Feature keys ${features.sorted()} != ${FEATURE_KEYS.sorted()}"
        if (!FEATURE_KEYS.all { it in caps }) errors += "a Feature key is not a caps key"
        if ("live" !in caps) errors += "caps lost 'live'"
        if ("live" in FEATURE_KEYS) errors += "Feature gained 'live'"
        return errors
    }

    private class Call(val method: String, val path: String, val timeout: String)

    private fun clientCalls(src: String): Map<String, Call> {
        val re = Regex("call\\(p, \"(GET|POST)\", \"([^\"]+)\",\\s*.*?,\\s*(if \\(p\\.viaIroh\\) 20_000 else 5_000|timeoutMs|\\d[\\d_]*)\\)")
        return re.findAll(src).associate { m -> m.groupValues[2] to Call(m.groupValues[1], m.groupValues[2], m.groupValues[3]) }
    }

    /** One function's own text: from its signature to the next member function. */
    private fun functionRegion(src: String, funName: String): String? {
        val fn = Regex("fun ${Regex.escape(funName)}\\(").find(src) ?: return null
        val next = Regex("\\n    (?:private )?(?:suspend )?fun ").find(src, fn.range.first + 1)?.range?.first ?: src.length
        return src.substring(fn.range.first, next)
    }

    /** Every top-level JSON key the function puts into its request body. */
    private fun clientBodyKeys(src: String, funName: String): Set<String> {
        val region = functionRegion(src, funName) ?: return emptySet()
        val opening = Regex("val (?:body|start) = buildJsonObject \\{").find(region) ?: return emptySet()
        val end = region.indexOf("}.toString()", opening.range.first)
        if (end < 0) return emptySet()
        return Regex("put\\(\"(\\w+)\"").findAll(region.substring(opening.range.first, end)).map { it.groupValues[1] }.toSet()
    }

    /** The multipart part names recognise() sends. */
    private fun clientAsrFields(src: String): Set<String> {
        val start = src.indexOf("fun recognise(")
        if (start < 0) return emptySet()
        val end = src.indexOf("suspend fun liveStart", start).let { if (it < 0) src.length else it }
        return Regex("name=\\\\?\"(\\w+)\\\\?\"").findAll(src.substring(start, end)).map { it.groupValues[1] }.toSet()
    }

    private fun brainRoutes(src: String): Map<String, String> =
        Regex("@app\\.(get|post)\\(\"([^\"]+)\"\\)\\s*(?:async\\s+)?def\\s+(\\w+)")
            .findAll(src).associate { it.groupValues[2] to it.groupValues[3] }

    private fun brainModelFields(src: String, model: String): Set<String>? {
        val head = Regex("^class ${Regex.escape(model)}\\(BaseModel\\):", RegexOption.MULTILINE).find(src) ?: return null
        val from = src.indexOf('\n', head.range.first) + 1
        val block = src.substring(from).lines().takeWhile { it.isBlank() || it.startsWith("    ") }.joinToString("\n")
        return Regex("^    (\\w+):", RegexOption.MULTILINE).findAll(block).map { it.groupValues[1] }.toSet()
    }

    private fun fieldHasDefault(src: String, model: String, field: String): Boolean {
        val head = Regex("^class ${Regex.escape(model)}\\(BaseModel\\):", RegexOption.MULTILINE).find(src) ?: return false
        val from = src.indexOf('\n', head.range.first) + 1
        val block = src.substring(from).lines().takeWhile { it.isBlank() || it.startsWith("    ") }
        return block.any { it.trimStart().startsWith("$field:") && "=" in it }
    }

    /** The Form/File parameters of a handler, which are the multipart fields a phone may send. */
    private fun brainFormFields(src: String, handler: String): Set<String> {
        val line = Regex("(?:async\\s+)?def ${Regex.escape(handler)}\\([^\\n]*").find(src)?.value ?: return emptySet()
        return Regex("(\\w+):\\s*[^,]*\\b(?:Form|File)\\(").findAll(line).map { it.groupValues[1] }.toSet()
    }

    private fun brainCapsKeys(src: String): Set<String> {
        val block = Regex("caps = \\{(.*?)\\}", RegexOption.DOT_MATCHES_ALL).find(src)?.groupValues?.get(1) ?: return emptySet()
        return Regex("\"(\\w+)\":").findAll(block).map { it.groupValues[1] }.toSet()
    }

    private fun clientFeatureKeys(src: String): Set<String> {
        val block = Regex("enum class Feature\\(val key: String\\) \\{(.*?)\\}", RegexOption.DOT_MATCHES_ALL)
            .find(src)?.groupValues?.get(1) ?: return emptySet()
        return Regex("\\w+\\(\"(\\w+)\"\\)").findAll(block).map { it.groupValues[1] }.toSet()
    }

    private fun pairingJson(url: String, ticket: String? = null): String =
        Json.encodeToString(BrainClient.Pairing.serializer(), BrainClient.Pairing(url, "t".repeat(24), "a".repeat(64), ticket))

    private companion object {
        const val D = "$"
        val CAPS = setOf("asr", "live", "isl", "llm", "gloss", "plan", "archive")
        val FEATURE_KEYS = setOf("asr", "isl", "llm", "gloss", "archive", "plan")
    }
}
