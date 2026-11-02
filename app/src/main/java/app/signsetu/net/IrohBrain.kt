package app.signsetu.net

import android.content.Context
import computer.iroh.Endpoint
import computer.iroh.EndpointOptions
import computer.iroh.EndpointTicket
import computer.iroh.RecvStream
import computer.iroh.presetN0
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.nio.ByteBuffer

/** The station brain over iroh, for a phone that is not on the station network: QUIC end-to-end encrypted to the
 * brain's key, which the pairing ticket names, directly where a hole can be punched and through an iroh relay
 * otherwise; the relay forwards ciphertext only. Each call is one stream framed as the brain's link.py expects. The
 * endpoint and its connection are kept for the app's life and rebuilt after a failure. */
object IrohBrain {
    private val ALPN = "signsetu/brain/1".toByteArray()
    private const val MAX_REPLY = 8 shl 20
    private const val CONNECT_MS = 15_000L

    private val lock = Mutex()
    private var endpoint: Endpoint? = null
    private var connection: Pair<String, computer.iroh.Connection>? = null
    @Volatile private var installed = false

    /** Once per process, before the first call: iroh reads network state through the app's context. */
    fun init(context: Context) {
        if (installed) return
        runCatching { computer.iroh.IrohAndroid.installAndroidContext(context.applicationContext) }
        installed = true
    }

    /** A pairing ticket as printed by the brain; anything else is not one. */
    fun validTicket(ticket: String): Boolean = runCatching { EndpointTicket.fromString(ticket.trim()) }.isSuccess

    /** Status and body of one request, or null when the brain cannot be reached. */
    suspend fun call(ticket: String, method: String, path: String, body: ByteArray?, type: String?, bearer: String, timeoutMs: Long): Pair<Int, ByteArray>? =
        runCatching {
            withTimeout(timeoutMs) {
                val conn = connection(ticket)
                val bi = conn.openBi()
                val head = buildJsonObject { put("m", method); put("p", path); put("a", "Bearer $bearer"); type?.let { put("t", it) } }.toString().toByteArray()
                val send = bi.send()
                send.writeAll(frame(head) + frame(body ?: ByteArray(0)))
                send.finish()
                val recv = bi.recv()
                val reply = Json.parseToJsonElement(read(recv).decodeToString()).jsonObject
                reply.getValue("s").jsonPrimitive.content.toInt() to read(recv)
            }
        }.getOrElse {
            // A dead path throws from every pending call: the next call dials again.
            lock.withLock { connection?.second?.let { c -> runCatching { c.close(0L, ByteArray(0)) } }; connection = null }
            null
        }

    /** Drops the connection after a network change, so the next call finds a path on the new network. */
    suspend fun reset() = lock.withLock {
        connection?.second?.let { runCatching { it.close(0L, ByteArray(0)) } }
        connection = null
        endpoint?.let { runCatching { it.shutdown() } }
        endpoint = null
    }

    private suspend fun connection(ticket: String) = lock.withLock {
        connection?.takeIf { it.first == ticket && it.second.closeReason() == null }?.second ?: run {
            val ep = endpoint ?: Endpoint.bind(EndpointOptions(preset = presetN0(), alpns = listOf(ALPN))).also { endpoint = it }
            val addr = EndpointTicket.fromString(ticket.trim()).endpointAddr()
            withTimeout(CONNECT_MS) { ep.connect(addr, ALPN) }.also { connection = ticket to it }
        }
    }

    private fun frame(b: ByteArray) = ByteBuffer.allocate(4 + b.size).putInt(b.size).put(b).array()

    private suspend fun read(s: RecvStream): ByteArray {
        val n = ByteBuffer.wrap(s.readExact(4u)).int
        require(n in 0..MAX_REPLY) { "reply of $n bytes" }
        return if (n == 0) ByteArray(0) else s.readExact(n.toUInt())
    }
}
