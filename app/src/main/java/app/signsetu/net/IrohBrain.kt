package app.signsetu.net

import android.content.Context
import computer.iroh.BiStream
import computer.iroh.Connection
import computer.iroh.Endpoint
import computer.iroh.EndpointAddr
import computer.iroh.EndpointOptions
import computer.iroh.EndpointTicket
import computer.iroh.RecvStream
import computer.iroh.RelayMode
import computer.iroh.SecretKey
import computer.iroh.SendStream
import computer.iroh.presetMinimal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI
import java.nio.ByteBuffer
import java.util.Base64

/** The station brain over iroh, for a phone that is not on the station network: QUIC end-to-end encrypted to the
 * brain's key, which the pairing ticket names, directly where a hole can be punched and through an iroh relay
 * otherwise; the relay forwards ciphertext only. The relay is the one the ticket names, the department's own; no
 * third-party relay or address lookup is used. Each call is one stream framed as the brain's link.py expects. The
 * endpoint and its connection are kept for the app's life and rebuilt after a failure. */
object IrohBrain {
    private val ALPN = "signsetu/brain/1".toByteArray()
    private const val MAX_REPLY = 8 shl 20
    private const val CONNECT_MS = 15_000L
    /** Beats come every 5 s (brain/link.py); two missed and the station counts as gone. A station stopped hard sends
     * no goodbye, so this is how long the phone takes to notice. */
    private const val STALE_MS = 12_000L
    /** Bounds tidying up an abandoned stream or a closing endpoint, so a dead path cannot hold the lock. */
    private const val CLEANUP_MS = 3_000L

    // Guards endpoint, connection and key; calls run on the connection outside it, as QUIC multiplexes them.
    private val lock = Mutex()
    private var endpoint: Pair<String?, Endpoint>? = null
    private var connection: Pair<String, Connection>? = null
    @Volatile private var vault: TokenVault? = null
    private var key: ByteArray? = null
    @Volatile private var installed = false

    /** Once per process, before the first call: iroh reads network state through the app's context. */
    @Synchronized
    fun init(context: Context) {
        if (installed) return
        runCatching { computer.iroh.IrohAndroid.installAndroidContext(context.applicationContext) }
            .onFailure { lastError = "android context: ${it::class.java.simpleName}: ${it.message}" }
        vault = KeystoreVault(context.applicationContext, "iroh.bin", "signsetu.iroh.key")
        installed = true
    }

    /** Why the latest call failed, as the exception's type and message; null after one that worked. Shown when
     * pairing fails, since release builds keep no log. */
    @Volatile var lastError: String? = null
        private set

    /** A pairing ticket as printed by the brain; anything else is not one. */
    fun validTicket(ticket: String): Boolean = runCatching { EndpointTicket.fromString(ticket.trim()) }.isSuccess

    /** Status and body of one request, or null when the brain cannot be reached. */
    suspend fun call(ticket: String, method: String, path: String, body: ByteArray?, type: String?, bearer: String, timeoutMs: Long): Pair<Int, ByteArray>? {
        var used: Connection? = null
        return try {
            withTimeout(timeoutMs) {
                val x = open(connection(ticket).also { used = it }, method, path, bearer, type, body)
                var whole = false
                try { (x.status to read(x.recv)).also { whole = true } } finally { x.end(abandon = !whole) }
            }.also { lastError = null }
        } catch (e: Throwable) {
            // The caller stopped waiting: nothing failed, so nothing is reported.
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            lastError = "${e::class.java.simpleName}: ${e.message ?: ""}".take(300)
            // A slow reply says nothing about the path, and dropping it would cut the heartbeat and every other call on
            // it; a dead path is found by the heartbeat. Other errors drop the connection this call used.
            if (e !is TimeoutCancellationException) used?.let { drop(it) }
            null
        }
    }

    /** The station's heartbeat (link.py, GET /v1/beat): [onBeat] gets the status code and body of each beat, about
     * every 5 seconds, until the stream ends, a beat is [STALE_MS] late, or the token is refused. Throws when the
     * station cannot be reached; the connection is then dropped, as after a failed call. */
    suspend fun beat(ticket: String, bearer: String, onBeat: suspend (Int, ByteArray) -> Unit) {
        var used: Connection? = null
        var stream: Exchange? = null
        var ended = false
        try {
            val x = withTimeout(CONNECT_MS) { open(connection(ticket).also { used = it }, "GET", "/v1/beat", bearer, null, null) }
            stream = x
            while (true) {
                onBeat(x.status, withTimeout(STALE_MS) { read(x.recv) })
                if (x.status != 200) { ended = true; return }
            }
        } catch (e: Throwable) {
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            // A beat stream that went quiet leaves the connection to the calls on it, an upload or a live take among
            // them; the next beat opens on it again. Only a connection that cannot even open a stream is dropped.
            val c = used
            if (c != null && (stream == null || c.closeReason() != null)) drop(c)
            throw java.io.IOException("station heartbeat lost", e)
        } finally {
            // Without this the brain keeps writing beats into a stream nobody reads until the connection closes.
            stream?.end(abandon = !ended)
        }
    }

    /** This phone's iroh endpoint ID, the same across launches: what a relay's `[access]` allowlist names
     * (docs/16-relay.md). Null when iroh cannot load on this device. */
    suspend fun phoneId(): String? = withContext(Dispatchers.IO) {
        lock.withLock { runCatching { SecretKey.fromBytes(key()).public().toString() }.getOrNull() }
    }

    /** Drops the connection and endpoint after a network change, so the next call finds a path on the new network. */
    suspend fun reset() = withContext(NonCancellable) {
        lock.withLock {
            connection?.second?.let { runCatching { it.close(0L, ByteArray(0)) } }
            connection = null
            endpoint?.second?.let { shut(it) }
            endpoint = null
        }
    }

    private suspend fun drop(c: Connection) = withContext(NonCancellable) {
        lock.withLock {
            // Another call may already have dialled afresh; that newer connection is left alone.
            if (connection?.second === c) {
                runCatching { c.close(0L, ByteArray(0)) }
                connection = null
            }
        }
    }

    private suspend fun connection(ticket: String): Connection = lock.withLock {
        connection?.takeIf { it.first == ticket && it.second.closeReason() == null }?.second?.let { return@withLock it }
        val named = EndpointTicket.fromString(ticket.trim()).endpointAddr()
        // A ticket from before the brain had its own relay names n0's; that relay is never used.
        val relay = named.relayUrl()?.takeUnless(::isN0Relay)
        val addr = EndpointAddr(named.id(), relay, named.directAddresses())
        val ep = endpoint?.takeIf { it.first == relay }?.second ?: run {
            endpoint?.second?.let { shut(it) }
            endpoint = null
            val mode = relay?.let { RelayMode.customFromUrls(listOf(it)) } ?: RelayMode.disabled()
            Endpoint.bind(EndpointOptions(preset = presetMinimal(), secretKey = key(), alpns = listOf(ALPN), relayMode = mode))
                .also { endpoint = relay to it }
        }
        val fresh = withTimeout(CONNECT_MS) { ep.connect(addr, ALPN) }
        // The connection to an earlier ticket is closed only once the new one works, so a failed re-pair keeps it.
        connection?.second?.let { old -> runCatching { old.close(0L, ByteArray(0)) } }
        connection = ticket to fresh
        fresh
    }

    private suspend fun shut(ep: Endpoint) {
        withTimeoutOrNull(CLEANUP_MS) { runCatching { ep.shutdown() } }
    }

    /** One device key for the install, so the phone's endpoint ID stays the same across launches and a relay's
     * allowlist can name it. Kept wrapped by a Keystore key, outside backups. */
    private fun key(): ByteArray = key ?: run {
        val v = vault
        val raw = v?.get()
        // A Keystore busy at boot keeps the file; a new key here would change the endpoint ID a relay allowlist names.
        if (raw == null && v?.stored() == true) throw java.io.IOException("device key not readable yet")
        val saved = raw?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }?.takeIf { it.size == 32 }
        saved ?: SecretKey.generate().toBytes().also { v?.put(Base64.getEncoder().encodeToString(it)) }
    }.also { key = it }

    /** One request's stream, after the brain's status line has been read. */
    private class Exchange(val bi: BiStream, val send: SendStream, val recv: RecvStream) {
        var status = 0

        /** Frees the stream; [abandon] also tells the brain to stop, for a reply not read to its end. */
        suspend fun end(abandon: Boolean) {
            withContext(NonCancellable) {
                if (abandon) withTimeoutOrNull(CLEANUP_MS) {
                    runCatching { send.reset(0uL) }
                    runCatching { recv.stop(0uL) }
                }
                runCatching { recv.destroy() }
                runCatching { send.destroy() }
                runCatching { bi.destroy() }
            }
        }
    }

    private suspend fun open(conn: Connection, method: String, path: String, bearer: String, type: String?, body: ByteArray?): Exchange {
        val bi = conn.openBi()
        val x = Exchange(bi, bi.send(), bi.recv())
        try {
            val head = buildJsonObject { put("m", method); put("p", path); put("a", "Bearer $bearer"); type?.let { put("t", it) } }.toString().toByteArray()
            x.send.writeAll(frame(head) + frame(body ?: ByteArray(0)))
            x.send.finish()
            x.status = Json.parseToJsonElement(read(x.recv).decodeToString()).jsonObject.getValue("s").jsonPrimitive.content.toInt()
            return x
        } catch (e: Throwable) {
            x.end(abandon = true)
            throw e
        }
    }

    private fun frame(b: ByteArray) = ByteBuffer.allocate(4 + b.size).putInt(b.size).put(b).array()

    private suspend fun read(s: RecvStream): ByteArray {
        val n = ByteBuffer.wrap(s.readExact(4u)).int
        require(n in 0..MAX_REPLY) { "reply of $n bytes" }
        return if (n == 0) ByteArray(0) else s.readExact(n.toUInt())
    }
}

/** n0's public relays live under iroh.link, written with a trailing dot; an unparsable URL counts as one. */
internal fun isN0Relay(url: String): Boolean {
    val host = runCatching { URI(url).host }.getOrNull()?.trimEnd('.')?.lowercase() ?: return true
    return host == "iroh.link" || host.endsWith(".iroh.link")
}
