package app.signsetu

import app.signsetu.data.content.SignMode
import app.signsetu.data.content.planText
import app.signsetu.ui.avatar.AdultBuild
import app.signsetu.ui.avatar.Timeline
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot

/** Ranks the joins between signs in typed sentences for the eye check: each join's peak wrist speed, a wrist that
 * jumps through the face, hands that cross, and a hand that drops below the waist mid-sentence. The reel then only
 * needs the joins at the top. Runs only when JOIN_SCAN_OUT names the output (Markdown table):
 *
 *     JOIN_SCAN_OUT=joins.md gradlew :app:testStandardDebugUnitTest --tests app.signsetu.JoinScan
 */
class JoinScan {
    private class Join(val sentence: String, val from: String, val to: String, val at: Float, val speed: Float, val flags: List<String>) {
        val score get() = speed / SPEED_OK + flags.size
    }

    @Test
    fun scan() {
        val out = System.getenv("JOIN_SCAN_OUT")
        assumeTrue(out != null)
        val modes = setOf(SignMode.SIGNS, SignMode.SPELL, SignMode.PICTURES)
        val sentences = (shipped.clips.values.map { it.sentence } + shipped.questions.mapNotNull { it.text["en"] }).distinct()
        val b = AdultBuild
        val chinY = b.anchor("chin", app.signsetu.ui.avatar.Side.R)!!.second
        val waistY = b.anchor("low", app.signsetu.ui.avatar.Side.R)!!.second
        val joins = sentences.flatMap { s ->
            val plan = shipped.planText(s, modes)
            val g = plan.gesture ?: return@flatMap emptyList()
            val tl = Timeline(g, b)
            val beats = plan.beats.sortedBy { it.start }
            beats.zipWithNext().map { (a, c) ->
                // From the last 0.1 s of one word to the first 0.1 s of the next, sampled at 120 Hz.
                val t0 = a.end - 0.1f
                val t1 = c.start + 0.1f
                var peak = 0f
                val flags = mutableSetOf<String>()
                var prev = tl.pose(Timeline.LEAD_IN + t0)
                var t = t0 + DT
                while (t <= t1) {
                    val p = tl.pose(Timeline.LEAD_IN + t)
                    for ((h, q) in listOf(p.r to prev.r, p.l to prev.l)) {
                        peak = maxOf(peak, hypot(h.x - q.x, h.y - q.y) / DT)
                        if (h.y < chinY && abs(h.x - 100f) < 25f && q.y >= chinY) flags += "through face"
                        if (h.y > waistY + 12f) flags += "drops below waist"
                    }
                    // The character's right hand is drawn on the viewer's left; crossing puts it to the right.
                    if (p.r.x > p.l.x + 30f) flags += "hands cross"
                    prev = p
                    t += DT
                }
                val word = { w: app.signsetu.data.content.WordBeat -> s.substring(w.range.first, minOf(s.length, w.range.last + 1)) }
                Join(s, word(a), word(c), a.end, peak, flags.toList())
            }
        }.sortedByDescending { it.score }
        val lines = listOf(
            "| Score | Sentence | Join | At (s) | Peak wrist speed (units/s) | Flags |",
            "| --- | --- | --- | --- | --- | --- |",
        ) + joins.map { j -> "| %.2f | %s | %s → %s | %.2f | %.0f | %s |".format(j.score, j.sentence, j.from, j.to, j.at, j.speed, j.flags.joinToString()) }
        File(out!!).writeText(lines.joinToString("\n") + "\n")
        println("joins: ${joins.size} in ${sentences.size} sentences, ${joins.count { it.score >= 1f }} over the line -> $out")
    }

    private companion object {
        const val DT = 1f / 120
        /** Character units a second a wrist may move between signs before the join reads as a jump (about a
         * shoulder width in a fifth of a second). */
        const val SPEED_OK = 440f
    }
}
