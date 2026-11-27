package app.signsetu.assistant

import app.signsetu.net.Http
import app.signsetu.net.Reply
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Addresses of an OpenAI- or Anthropic-style service from whatever the developer typed: a base such as .../v1, or a full
 * chat, responses or messages address. */
object AiEndpoint {
    private val suffixes = listOf("/chat/completions", "/responses", "/messages")

    private fun clean(input: String): String = input.trim().trimEnd('/')

    private fun base(input: String): String {
        val url = clean(input)
        val stripped = suffixes.firstOrNull { url.endsWith(it) }?.let { url.removeSuffix(it) } ?: url
        // A bare host has no path; the services that take this form all serve under /v1.
        val hostOnly = stripped.substringAfter("://", "").let { '/' !in it }
        return if (hostOnly && "://" in stripped) "$stripped/v1" else stripped
    }

    fun chatUrl(input: String): String {
        val url = clean(input)
        return if (suffixes.any { url.endsWith(it) }) url else base(input) + "/chat/completions"
    }

    fun modelsUrl(input: String): String = base(input) + "/models"

    /** Model ids from an OpenAI list ({"data":[{"id"}]}) or a {"models":[{"name"}]} list, sorted and unique. */
    fun parseModels(body: String): List<String> {
        val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptyList()
        val rows = (root["data"] ?: root["models"]) as? JsonArray ?: return emptyList()
        return rows.mapNotNull { row ->
            val o = row as? JsonObject ?: return@mapNotNull null
            (o["id"] ?: o["name"])?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        }.distinct().sorted()
    }
}

class PingResult(val ok: Boolean, val code: Int?, val ms: Long, val detail: String)

/** What the officer is told when the service refuses: its status code and that it refused, never a word of the reply.
 * The reply's text belongs to whoever answers at that address, and this detail is shown as the app's own and kept in
 * saved state, so a service can put nothing of its own on the screen through it. */
internal fun refusal(reply: Reply): String = "HTTP ${reply.code}: " + when (reply.code) {
    in 400..499 -> "the service refused"
    in 500..599 -> "the service failed"
    else -> "the service did not take the request"
}

@Serializable private data class ProbeMsg(val role: String, val content: String)
@Serializable private data class ProbeChat(val model: String, val messages: List<ProbeMsg>, val max_tokens: Int)
@Serializable private data class ProbeResponses(val model: String, val input: String, val max_output_tokens: Int, val store: Boolean = false)
@Serializable private data class ProbeMessages(val model: String, val messages: List<ProbeMsg>, val max_tokens: Int)

/** Lists a service's models and sends it one tiny request, for the developer settings. Nothing from a session goes here. */
class AiProbe(private val http: Http) {
    private val json = Json { encodeDefaults = true }

    /** Model ids, or null when the service refused, did not answer or the address is not HTTPS. */
    suspend fun models(url: String, key: String): List<String>? {
        if (!url.trim().startsWith("https://")) return null
        val reply = withContext(Dispatchers.IO) { runCatching { http.call("GET", AiEndpoint.modelsUrl(url), null, key.ifBlank { null }, TIMEOUT_MS) }.getOrNull() }
        return reply?.takeIf { it.ok }?.let { AiEndpoint.parseModels(it.body) }
    }

    suspend fun ping(url: String, model: String, key: String): PingResult {
        if (!url.trim().startsWith("https://")) return PingResult(false, null, 0, "The address must start with https://")
        if (model.isBlank()) return PingResult(false, null, 0, "Choose or type a model name")
        val target = AiEndpoint.chatUrl(url)
        val body = if (target.endsWith("/responses")) json.encodeToString(ProbeResponses.serializer(), ProbeResponses(model, "ping", 64))
        else if (target.endsWith("/messages")) json.encodeToString(ProbeMessages.serializer(), ProbeMessages(model, listOf(ProbeMsg("user", "Reply with the word pong.")), 64))
        else json.encodeToString(ProbeChat.serializer(), ProbeChat(model, listOf(ProbeMsg("user", "Reply with the word pong.")), 64))
        val start = System.nanoTime()
        val reply = withContext(Dispatchers.IO) { runCatching { http.call("POST", target, body, key.ifBlank { null }, TIMEOUT_MS) }.getOrNull() }
        val ms = (System.nanoTime() - start) / 1_000_000
        return when {
            reply == null -> PingResult(false, null, ms, "No reply: check the address and the network")
            reply.ok -> PingResult(true, reply.code, ms, "Answered in $ms ms")
            else -> PingResult(false, reply.code, ms, refusal(reply))
        }
    }

    private companion object { const val TIMEOUT_MS = 20_000 }
}
