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

    // Guards the slot and the key; calls run on the connection outside it, as QUIC multiplexes them.
    private val lock = Mutex()
    /** The station's connection, and the endpoint under it, kept apart from any dial still proving a ticket. */
    private val slot = LinkSlot<Connection, Endpoint>()
    @Volatile private var vault: TokenVault? = null
    private var key: ByteArray? = null
    @Volatile private var installed = false

    /** Once per process, before the first call: iroh reads network state through the app's context. */
    @Synchronized
    fun init(context: Context) {
        if (installed) return
        runCatching { computer.iroh.IrohAndroid.installAndroidContext(context.applicationContext) }
            .onFailure { lastError = "android context: ${it::class.java.simpleName}" }
        vault = KeystoreVault(context.applicationContext, "iroh.bin", "signsetu.iroh.key")
        installed = true
    }

    /** Why the latest call failed, as the exception's type and a sentence of this app's ([failureNotice]); null after
     * one that worked. Shown when pairing fails, since release builds keep no log. Never carries the exception's own
     * message: a station's reply or a mistyped ticket's text can ride in one. */
    @Volatile var lastError: String? = null
        private set

    /** A pairing ticket as printed by the brain; anything else is not one. */
    fun validTicket(ticket: String): Boolean = runCatching { EndpointTicket.fromString(ticket.trim()) }.isSuccess

    /** Status and body of one request, or null when the brain cannot be reached. */
    suspend fun call(ticket: String, method: String, path: String, body: ByteArray?, type: String?, bearer: String, timeoutMs: Long): Pair<Int, ByteArray>? {
        var used: Connection? = null
        var pending: LinkSlot.Dial<Connection, Endpoint>? = null
        return try {
            withTimeout(timeoutMs) {
                val d = use(ticket)
                used = d.conn
                pending = d.takeIf { it.pending }
                val x = open(d.conn, method, path, bearer, type, body)
                var whole = false
                try { (x.status to read(x.recv)).also { whole = true } } finally { x.end(abandon = !whole) }
            }.also { reply ->
                lastError = null
                // A ticket dialled afresh becomes the station's only once the station answered on it, so a mistyped one
                // leaves the connection the station is answering on exactly as it was.
                pending?.let { settle(ticket, it, answered = reply.first == 200); pending = null }
            }
        } catch (e: Throwable) {
            // The caller stopped waiting: nothing failed, so nothing is reported.
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            lastError = failureNotice(e)
            pending?.let { settle(ticket, it, answered = false); pending = null }
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
        var pending: LinkSlot.Dial<Connection, Endpoint>? = null
        var stream: Exchange? = null
        var ended = false
        try {
            val d = use(ticket)
            used = d.conn
            pending = d.takeIf { it.pending }
            val x = withTimeout(CONNECT_MS) { open(d.conn, "GET", "/v1/beat", bearer, null, null) }
            stream = x
            // The first beat settles a fresh dial the way a call's reply does.
            pending?.let { settle(ticket, it, answered = x.status == 200); pending = null }
            while (true) {
                onBeat(x.status, withTimeout(STALE_MS) { read(x.recv) })
                if (x.status != 200) { ended = true; return }
            }
        } catch (e: Throwable) {
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            pending?.let { settle(ticket, it, answered = false); pending = null }
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
        val gone = lock.withLock { slot.reset() }
        gone.conn?.let { runCatching { it.close(0L, ByteArray(0)) } }
        gone.endpoint?.let { shut(it) }
    }

    /** Forgets this phone's station identity for good: the wrapped key file, the cached bytes and the endpoint dialled
     * with them. The next call makes a fresh identity, so a wiped phone is a stranger to the station. Part of Clear all
     * data; [installed] and the Android context stay, so iroh keeps working. */
    suspend fun forget() = withContext(NonCancellable) {
        val gone = lock.withLock {
            vault?.clear()
            key?.fill(0)
            key = null
            slot.reset()
        }
        gone.conn?.let { runCatching { it.close(0L, ByteArray(0)) } }
        gone.endpoint?.let { shut(it) }
    }

    /** Drops the cached identity bytes; the vault is untouched, so [key] reads them back on the next call. A memory
     * hygiene step when the app locks, not a revocation of the station pairing. */
    suspend fun dropKey() = withContext(NonCancellable) {
        lock.withLock {
            key?.fill(0)
            key = null
        }
    }

    private suspend fun drop(c: Connection) = withContext(NonCancellable) {
        // Another call may already have dialled afresh; that newer connection is left alone.
        if (lock.withLock { slot.drop(c) }) runCatching { c.close(0L, ByteArray(0)) }
    }

    /** The connection to use for [ticket]: the station's own while this ticket names it and it is alive, else one
     * dialled afresh and marked pending, so pairing a wrong ticket never unseats the station that is answering. */
    private suspend fun use(ticket: String): LinkSlot.Dial<Connection, Endpoint> = lock.withLock {
        val t = ticket.trim()
        slot.kept(t) { it.closeReason() == null }?.let { return@withLock LinkSlot.Dial(it) }
        val named = EndpointTicket.fromString(t).endpointAddr()
        // A ticket from before the brain had its own relay names n0's; that relay is never used.
        val relay = named.relayUrl()?.takeUnless(::isN0Relay)
        val kept = slot.endpointOn(relay)
        val ep = kept ?: run {
            val mode = relay?.let { RelayMode.customFromUrls(listOf(it)) } ?: RelayMode.disabled()
            Endpoint.bind(EndpointOptions(preset = presetMinimal(), secretKey = key(), alpns = listOf(ALPN), relayMode = mode))
        }
        val addr = EndpointAddr(named.id(), relay, named.directAddresses())
        try {
            LinkSlot.Dial(withTimeout(CONNECT_MS) { ep.connect(addr, ALPN) }, ep, relay, own = kept == null, pending = true)
        } catch (e: Throwable) {
            // A dial that never opened takes the endpoint made for it; the station's endpoint and connection stay.
            if (kept == null) shut(ep)
            throw e
        }
    }

    /** A pending dial that the station answered on becomes its connection, replacing the one it is closed on; one it
     * did not answer on is closed alone. Either way the station's connection is left as it was until the swap. */
    private suspend fun settle(ticket: String, d: LinkSlot.Dial<Connection, Endpoint>, answered: Boolean) = withContext(NonCancellable) {
        if (!d.pending) return@withContext
        val gone = lock.withLock { if (answered) slot.adopt(ticket.trim(), d) else slot.abandon(d) }
        gone.conn?.let { runCatching { it.close(0L, ByteArray(0)) } }
        gone.endpoint?.let { shut(it) }
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

/** Why a call failed, as the officer is told it: the exception's type, then a sentence of this app's. Never the
 * exception's own message, which can carry a station's reply or a mistyped ticket's text (a parse error quotes what it
 * read), and would then be read on the pairing dialog as the app's own words. */
internal fun failureNotice(e: Throwable): String = "${e::class.java.simpleName}: " + when (e) {
    is TimeoutCancellationException -> "the station did not answer in time"
    is kotlinx.serialization.SerializationException -> "the station's reply was not one this app reads"
    is IllegalArgumentException -> "the station's reply was not one this app reads"
    is java.io.IOException -> "the station could not be reached"
    else -> "the station did not answer as expected"
}

/** Which connection is the station's, kept apart from a connection still being proved. A pairing ticket that does not
 * work is dialled as a candidate and closed again, so it can never take the working station's connection with it.
 * Generic over the connection and endpoint types so the rule can be tested without a QUIC peer. */
internal class LinkSlot<T : Any, E : Any> {
    /** The station's connection, with the ticket it was dialled for, and the endpoint it rides on with its relay. */
    var current: Pair<String, T>? = null
    var endpoint: Pair<String?, E>? = null

    /** A connection for one ticket: [on] the endpoint it was dialled on, [pending] when it is not yet the station's,
     * and [own] when that endpoint was made for this dial rather than the station's. */
    class Dial<T : Any, E : Any>(val conn: T, val on: E? = null, val relay: String? = null, val own: Boolean = false, val pending: Boolean = false)

    /** What an outcome leaves to close; either may be absent. */
    class Gone<T : Any, E : Any>(val conn: T? = null, val endpoint: E? = null)

    /** The station's connection for [ticket], when [alive] still holds for it. */
    fun kept(ticket: String, alive: (T) -> Boolean): T? = current?.takeIf { it.first == ticket && alive(it.second) }?.second

    /** The endpoint the station's connection rides on, when it is the one [relay] names. */
    fun endpointOn(relay: String?): E? = endpoint?.takeIf { it.first == relay }?.second

    /** The candidate answered: it becomes the station's, and the connection it replaces — with the endpoint the station
     * was on, when the candidate brought its own — comes back to be closed. */
    fun adopt(ticket: String, d: Dial<T, E>): Gone<T, E> {
        if (current?.second === d.conn) return Gone()
        val old = current?.second
        val oldEndpoint = if (d.own) endpoint?.second?.takeIf { it !== d.on } else null
        if (d.own) endpoint = d.relay to d.on!!
        current = ticket to d.conn
        return Gone(old, oldEndpoint)
    }

    /** The candidate failed: only it, and the endpoint made for it, go; the station's connection and endpoint stay. */
    fun abandon(d: Dial<T, E>): Gone<T, E> =
        if (current?.second === d.conn) Gone() else Gone(d.conn, if (d.own) d.on else null)

    /** Drops [conn] only while it is still the station's; returns whether it was. */
    fun drop(conn: T): Boolean = if (current?.second === conn) { current = null; true } else false

    /** Forgets everything, handing back what to close. */
    fun reset(): Gone<T, E> {
        val gone = Gone(current?.second, endpoint?.second)
        current = null
        endpoint = null
        return gone
    }
}

