package app.signsetu.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.signsetu.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** [raw] is the body as received, for replies that are not text, such as speech. */
class Reply(val code: Int, val body: String, val raw: ByteArray = body.toByteArray()) {
    val ok get() = code in 200..299
}

/** One blocking HTTPS call; null when the network or server could not be reached. Faked in tests. */
fun interface Http {
    /** [timeoutMs] bounds the wait for the reply; a short one keeps a dead gateway from holding up enrolment. */
    fun call(method: String, url: String, body: String?, bearer: String?, timeoutMs: Int): Reply?

    /** The chat/report timeout; a full report from the free gateway model takes 30 to 45 s. */
    fun call(method: String, url: String, body: String?, bearer: String?): Reply? =
        call(method, url, body, bearer, 60_000)
}

/** Platform HTTP over TLS. Cleartext is refused by the network security config and again here. */
class UrlHttp : Http {
    override fun call(method: String, url: String, body: String?, bearer: String?, timeoutMs: Int): Reply? {
        if (!url.startsWith("https://")) return null
        val bytes = body?.toByteArray()
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            // The bearer token is for this host alone; an API has no reason to redirect.
            instanceFollowRedirects = false
            connectTimeout = minOf(10_000, timeoutMs)
            // A full report from the free gateway model takes 30 to 45 s; the officer waits behind a progress dialog.
            readTimeout = timeoutMs
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            // The configured service can insist on a particular client; the value comes from the build, not from here.
            setRequestProperty("User-Agent", BuildConfig.ASSIST_UA)
            // Anthropic-style services read the key from x-api-key and want an API version; everything else takes a bearer token.
            if (bearer != null && (url.endsWith("/messages") || "://api.anthropic.com/" in url)) {
                setRequestProperty("x-api-key", bearer)
                setRequestProperty("anthropic-version", "2023-06-01")
            } else bearer?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (bytes != null) {
                doOutput = true
                // A declared length lets the gateway refuse an oversized body before reading it, and avoids chunked uploads.
                setFixedLengthStreamingMode(bytes.size)
            }
        }
        return try {
            bytes?.let { conn.outputStream.use { o -> o.write(it) } }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            // Replies are a line of text or a short JSON object; a larger body is cut rather than filling memory.
            val raw = stream?.use { it.readCapped(MAX_REPLY_BYTES) } ?: ByteArray(0)
            Reply(conn.responseCode, raw.decodeToString(), raw)
        } catch (e: java.io.IOException) {
            null
        } finally {
            conn.disconnect()
        }
    }
}

private const val MAX_REPLY_BYTES = 1 shl 20

// InputStream.readNBytes needs API 33; minSdk is 29.
private fun InputStream.readCapped(max: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (out.size() < max) {
        val n = read(buffer, 0, minOf(buffer.size, max - out.size()))
        if (n < 0) break
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}

/** A network with working internet, not just a connected one: captive portals fail this. */
fun isOnline(context: Context): Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
