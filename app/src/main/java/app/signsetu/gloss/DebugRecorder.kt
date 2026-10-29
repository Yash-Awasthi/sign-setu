package app.signsetu.gloss

import app.signsetu.camera.LandmarkFrame
import app.signsetu.sign.SignGuess
import app.signsetu.sign.SignSegment
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File

/** Debug builds only (docs/15-gloss-to-text.md): each sign window as one JSON record of landmarks,
 * the reader's top 5, face and head cues and the lattice, and each decision (the rung shown, what the officer took)
 * as a line in decisions.jsonl. Landmarks only, never video. Read back by tools/eval/run_eval.py after
 * `adb pull /sdcard/Android/data/in.signsetu/files/recordings`. */
object DebugRecorder {
    /** Set at start in debug builds; null switches recording off. */
    @Volatile var dir: File? = null

    fun window(eventId: Long, question: String?, segments: List<SignSegment>, appVersion: String) {
        val d = dir ?: return
        val record = record(eventId, question, segments, appVersion)
        runCatching { d.mkdirs(); File(d, "window_$eventId.json").writeText(record.toString()) }
    }

    /** A typed correction of a window, stored in that window's record beside its lattice. */
    fun correction(eventId: Long, gloss: List<String>, text: String) {
        val f = File(dir ?: return, "window_$eventId.json").takeIf { it.exists() } ?: return
        runCatching {
            val old = Json.parseToJsonElement(f.readText()).jsonObject
            val fix = buildJsonObject {
                put("gloss", JsonArray(gloss.map(::JsonPrimitive))); put("text", text); put("at", System.currentTimeMillis())
            }
            f.writeText(JsonObject(old + ("correction" to fix)).toString())
        }
    }

    /** One sign window in the recorder schema; also what a training contribution carries. */
    fun record(eventId: Long, question: String?, segments: List<SignSegment>, appVersion: String): JsonObject {
        val lattice = GlossLattice.of(segments, appVersion)
        return buildJsonObject {
            put("eventId", eventId)
            put("app", appVersion)
            question?.let { put("question", it) }
            // Frame width over height: y is a share of the height, so the points cannot be put back without it.
            segments.firstOrNull()?.frames?.firstOrNull()?.let { put("aspect", round(it.aspect)) }
            put("lattice", Json.parseToJsonElement(lattice.toJson()))
            put("signs", buildJsonArray {
                for (s in segments) add(buildJsonObject {
                    put("top5", JsonArray(s.candidates.ifEmpty { s.guesses.orEmpty() }.take(5).map { g -> JsonArray(listOf(JsonPrimitive(g.word), JsonPrimitive(g.score))) }))
                    put("frames", JsonArray(s.frames.map(::frame)))
                })
            })
            // Filled in by a person before the record joins the evaluation set.
            put("label", buildJsonObject { put("gloss", JsonPrimitive(null as String?)); put("en", JsonPrimitive(null as String?)); put("hi", JsonPrimitive(null as String?)) })
        }
    }

    fun decision(eventId: Long, what: String, detail: String?) {
        val d = dir ?: return
        val line = buildJsonObject {
            put("eventId", eventId); put("at", System.currentTimeMillis()); put("what", what); detail?.let { put("detail", it) }
        }
        runCatching { d.mkdirs(); File(d, "decisions.jsonl").appendText(line.toString() + "\n") }
    }

    /** Shadow mode: the shown reader's and the silent reader's top guess and time for one segment, a line in
     * shadow.jsonl, so a week of team use gives their agreement and latency before format 3 becomes the default. */
    fun shadow(shownFormat: Int, shown: SignGuess?, other: SignGuess?, shownMs: Float, otherMs: Float) {
        val d = dir ?: return
        val line = buildJsonObject {
            put("at", System.currentTimeMillis()); put("shown_format", shownFormat)
            put("shown", shown?.word); put("shown_p", shown?.score?.let(::round))
            put("other", other?.word); put("other_p", other?.score?.let(::round))
            put("agree", shown?.word == other?.word); put("shown_ms", round(shownMs)); put("other_ms", round(otherMs))
        }
        runCatching { d.mkdirs(); File(d, "shadow.jsonl").appendText(line.toString() + "\n") }
    }

    /** Hands (21 points each) and the six arm points, rounded: what the readers use. */
    private fun frame(f: LandmarkFrame) = buildJsonObject {
        put("t", f.timestampMs)
        fun pts(p: List<androidx.compose.ui.geometry.Offset>?) = p?.let { JsonArray(it.flatMap { o -> listOf(JsonPrimitive(round(o.x)), JsonPrimitive(round(o.y))) }) }
        pts(f.leftHand)?.let { put("left", it) }
        pts(f.rightHand)?.let { put("right", it) }
        pts(f.pose?.takeIf { it.size > 16 }?.subList(11, 17))?.let { put("arms", it) }
        if (f.browsUp) put("brows", true)
        f.head?.let { put("head", it.name) }
        f.yaw?.let { put("yaw", round(it)) }
        f.pitch?.let { put("pitch", round(it)) }
        // Mouth blendshapes in MOUTH_SHAPES order: the data a mouthing model will be trained on.
        f.mouth?.let { m -> put("mouth", JsonArray(m.map { JsonPrimitive(round(it)) })) }
    }

    private fun round(v: Float) = Math.round(v * 1e5f) / 1e5f
}
