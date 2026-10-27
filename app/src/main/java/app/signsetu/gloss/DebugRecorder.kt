package app.signsetu.gloss

import app.signsetu.camera.LandmarkFrame
import app.signsetu.sign.SignSegment
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/** Debug builds only (docs/10-gloss-to-text.md, steps 0.2 and 5.6): each sign window as one JSON record of landmarks,
 * the reader's top 5, face and head cues and the lattice, and each decision (the rung shown, what the officer took)
 * as a line in decisions.jsonl. Landmarks only, never video. Read back by tools/eval/run_eval.py after
 * `adb pull /sdcard/Android/data/in.signsetu/files/recordings`. */
object DebugRecorder {
    /** Set at start in debug builds; null switches recording off. */
    @Volatile var dir: File? = null

    fun window(eventId: Long, question: String?, segments: List<SignSegment>, appVersion: String) {
        val d = dir ?: return
        val lattice = GlossLattice.of(segments, appVersion)
        val record = buildJsonObject {
            put("eventId", eventId)
            put("app", appVersion)
            question?.let { put("question", it) }
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
        runCatching { d.mkdirs(); File(d, "window_$eventId.json").writeText(record.toString()) }
    }

    fun decision(eventId: Long, what: String, detail: String?) {
        val d = dir ?: return
        val line = buildJsonObject {
            put("eventId", eventId); put("at", System.currentTimeMillis()); put("what", what); detail?.let { put("detail", it) }
        }
        runCatching { d.mkdirs(); File(d, "decisions.jsonl").appendText(line.toString() + "\n") }
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
    }

    private fun round(v: Float) = Math.round(v * 1e5f) / 1e5f
}
