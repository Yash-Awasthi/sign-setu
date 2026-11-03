package app.signsetu.export

import app.signsetu.BuildConfig
import app.signsetu.camera.LandmarkFrame
import app.signsetu.data.session.DeviceSeal
import app.signsetu.data.session.SessionEvent
import app.signsetu.data.session.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

/** One conversation as it is held, for the station's inbox (docs/18-station.md): no PDF, every row and every file
 * as recorded, in one zip. README.txt inside says what each file is and how to check the hash chain. */
object StationExport {
    /** The zip's name on the station: the officer's title and the session's id, so two titles never collide. */
    fun name(title: String?, sessionId: String) = "${title?.takeIf { it.isNotBlank() } ?: "conversation"} ${sessionId.take(8)}.zip"

    suspend fun build(repo: SessionRepository, sessionId: String): ByteArray? = withContext(Dispatchers.IO) {
        val session = repo.session(sessionId).first() ?: return@withContext null
        val events = repo.eventsNow(sessionId)
        val seal = repo.currentSeal(sessionId)
        val key = DeviceSeal.publicKey()
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            // WAV and PNG barely shrink and the landmarks are gzipped already: the fastest level saves the phone's CPU.
            zip.setLevel(java.util.zip.Deflater.BEST_SPEED)
            fun put(path: String, bytes: ByteArray) { zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry() }
            put("README.txt", README.toByteArray())
            put("session.json", buildJsonObject {
                put("format", 1)
                put("app", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                put("id", session.id)
                put("title", session.title)
                put("startedAt", session.startedAt)
                put("endedAt", session.endedAt)
                put("profile", session.profile.name)
                put("role", session.role.name)
                put("subjectLang", session.subjectLang)
                put("seal", seal?.let { s -> buildJsonObject { put("line", s.line); put("count", s.count); put("head", s.head); put("root", s.root); put("signature", s.sig) } } ?: JsonNull)
                put("deviceKey", key?.let { java.util.Base64.getEncoder().encodeToString(it) })
            }.toString().toByteArray())
            val rows = ArrayList<JsonElement>(events.size)
            for (e in events.sortedBy { it.id }) {
                val drawing = e.drawingId?.let { id -> repo.drawing(id)?.let { "drawings/$id.png".also { p -> put(p, it) } } }
                val audio = e.audioId?.let { id -> repo.audio(id)?.let { "audio/$id.wav".also { p -> put(p, it) } } }
                val signs = e.landmarksId?.let { id -> repo.landmarks(id)?.let { "signs/$id.json.gz".also { p -> put(p, it) } } }
                rows += row(e, drawing, audio, signs)
            }
            put("events.json", JsonArray(rows).toString().toByteArray())
        }
        out.toByteArray()
    }

    private fun row(e: SessionEvent, drawing: String?, audio: String?, signs: String?) = buildJsonObject {
        put("id", e.id); put("ts", e.ts); put("type", e.type.name); put("source", e.source.name)
        put("lang", e.lang); put("text", e.text); put("cardId", e.cardId); put("lexiconId", e.lexiconId)
        put("itemId", e.itemId); put("reason", e.reason); put("shownId", e.shownId); put("via", e.via); put("hash", e.hash)
        put("drawing", drawing); put("audio", audio); put("signs", signs)
    }

    /** The landmarks of one sign window, gzipped JSON: per sign the word read and its frames, as [frameJson] writes
     * them. Android reads no database value over 2 MB, so a long window (Tell me runs to three minutes) keeps every
     * second frame, then every fourth, until it fits; "every" in the file says which. */
    fun signsJson(signs: List<List<LandmarkFrame>>, words: List<String>): ByteArray {
        var every = 1
        while (true) {
            val text = buildJsonObject {
                put("format", 1)
                put("aspect", signs.firstOrNull()?.firstOrNull()?.aspect ?: (3f / 4f))
                put("every", every)
                put("signs", buildJsonArray {
                    signs.forEachIndexed { i, frames ->
                        val kept = if (every == 1) frames else frames.filterIndexed { n, _ -> n % every == 0 }
                        add(buildJsonObject { put("word", words.getOrNull(i)); put("frames", JsonArray(kept.map(::frameJson))) })
                    }
                })
            }.toString().toByteArray()
            val gz = ByteArrayOutputStream(text.size / 3).also { o -> java.util.zip.GZIPOutputStream(o).use { it.write(text) } }.toByteArray()
            if (gz.size <= MAX_STORED || every >= 16) return gz
            every *= 2
        }
    }

    /** Under the 2 MB a database value can be read back in, with room to spare. */
    private const val MAX_STORED = 1_800_000

    /** One frame: hands as 21 (x, y), pose as 33 (x, y), MediaPipe normalised image points, flat; a missing part is
     * null. The face mesh is never kept, only the cues read from it. The station's reader (brain/signs.py) takes the
     * same frames. */
    fun frameJson(f: LandmarkFrame): JsonObject = buildJsonObject {
        put("t", f.timestampMs)
        put("left", points(f.leftHand))
        put("right", points(f.rightHand))
        put("pose", points(f.pose))
        if (f.browsUp) put("browsUp", true)
        if (f.mouthOpen) put("mouthOpen", true)
        f.yaw?.let { put("yaw", round(it)) }
        f.pitch?.let { put("pitch", round(it)) }
        f.mouth?.let { m -> put("mouth", JsonArray(m.map { JsonPrimitive(round(it)) })) }
    }

    private fun points(p: List<androidx.compose.ui.geometry.Offset>?): JsonElement =
        p?.let { JsonArray(it.flatMap { o -> listOf(JsonPrimitive(round(o.x)), JsonPrimitive(round(o.y))) }) } ?: JsonNull

    private fun round(v: Float) = (v * 10_000f).roundToInt() / 10_000f

    private val README = """
        |Sign Setu conversation, as recorded on the officer's phone (format 1).
        |
        |session.json  the session: title, times, language, and its seal: the Merkle root over every row's hash, the
        |              line the phone's device key signed, the signature, and that key (X.509, base64).
        |events.json   every row in the order written. "hash" is the row's link in the hash chain; "drawing", "audio"
        |              and "signs" name the file the row holds, when it holds one.
        |drawings/     pictures drawn or kept: PNG.
        |audio/        the person's recorded voice: WAV, 16 kHz mono 16-bit.
        |signs/        the raw landmarks the app read signs from, gzipped JSON (the exact bytes the row's hash covers):
        |              "every" is 1, or 2, 4... when a long window kept only every so many frames; per sign the word
        |              read and its frames, each with
        |              "left" and "right" (21 x, y each), "pose" (33 x, y), MediaPipe normalised image points, and
        |              cues read from the face. No face mesh and no camera picture is ever kept.
        |
        |Checking the chain: start from sha256("signsetu-chain-v1" + f(session id) + f(str(startedAt))), where f(v)
        |is "|-" for null and "|<length>:<v>" otherwise, the length counted in UTF-16 code units, and every sha256 is
        |of the UTF-8 text, written as lowercase hex. Each row's hash is sha256 of the previous hash + f of the session
        |id, ts, type, source, lang, text, cardId, lexiconId, the drawing's sha256 hex, itemId, reason, shownId, via, then
        |"|audio" + f(the WAV's sha256 hex) when the row has audio, and "|signs" + f(the landmark file's sha256 hex)
        |when it has landmarks. The last hash and the Merkle root must equal those in session.json.
        |""".trimMargin()
}
