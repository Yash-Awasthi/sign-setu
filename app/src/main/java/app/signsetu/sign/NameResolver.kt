package app.signsetu.sign

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

/** Levenshtein distance, two rows: only to gather lexicon names near a spelling; ranking is by CTC probability. */
internal fun editDistance(a: String, b: String): Int {
    var prev = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val cur = IntArray(b.length + 1).also { it[0] = i }
        for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        prev = cur
    }
    return prev[b.length]
}

/** A fingerspelled span resolved against known names (docs/10-gloss-to-text.md, Phase 3). [text] is what the
 * sentence uses: a lexicon name only when it clearly beats every other reading, otherwise the raw spelling with
 * [verified] false. [alternatives] are the next readings, best first, for the officer to tap. */
data class ResolvedName(val text: String, val raw: String, val verified: Boolean, val alternatives: List<String>, val margin: Float)

/** Scores spelling candidates by their exact CTC probability under the speller's own frame outputs plus a name prior,
 * and snaps to a known name only past a margin. A wrong snap names the wrong person; a raw spelling only looks odd. */
class NameResolver(
    /** Lower-case name to prior weight in (0, 1]; the session's own names should come in near 1. */
    private val lexicon: Map<String, Float>,
    private val alphabet: String,
) {
    private val index = alphabet.lowercase().withIndex().associate { (i, c) -> c to i + 1 }
    /** This session's confirmed names (docs/10, step 3.6), on top of [lexicon] at weight 1. */
    @Volatile var session: Set<String> = emptySet()
    private fun prior(name: String) = if (name in session) 1f else lexicon[name]

    fun resolve(logits: Array<FloatArray>): ResolvedName? {
        val logp = logSoftmax(logits)
        val beams = prefixBeam(logp, BEAM).filter { it.first.isNotEmpty() }
        val raw = beams.firstOrNull()?.first ?: return null
        val near = (lexicon.keys + session).filter { name -> beams.any { (b, _) -> kotlin.math.abs(name.length - b.length) <= 2 && editDistance(name, b) <= 2 } }
        val scored = (beams.map { it.first } + near).distinct().mapNotNull { c ->
            ctcLogProb(logp, c)?.let { lp -> c to lp + LAMBDA * ln(prior(c) ?: UNLISTED) }
        }.sortedByDescending { it.second }
        val best = scored.first()
        val margin = (best.second - (scored.getOrNull(1)?.second ?: Float.NEGATIVE_INFINITY)).coerceAtMost(99f)
        val sure = prior(best.first) != null && margin >= MARGIN
        val text = if (sure) best.first else raw
        return ResolvedName(
            text.replaceFirstChar(Char::uppercase), raw, sure,
            scored.map { it.first }.filter { it != text }.take(3).map { it.replaceFirstChar(Char::uppercase) }, margin,
        )
    }

    /** CTC prefix beam search: the [width] likeliest label strings with their log probabilities. */
    fun prefixBeam(logp: Array<FloatArray>, width: Int): List<Pair<String, Float>> {
        // prefix -> (log P ending in blank, log P ending in a letter)
        var beams = mapOf("" to (0f to NEG))
        for (row in logp) {
            val next = HashMap<String, Pair<Float, Float>>()
            fun add(p: String, b: Float, nb: Float) {
                val (ob, onb) = next[p] ?: (NEG to NEG)
                next[p] = logAdd(ob, b) to logAdd(onb, nb)
            }
            for ((prefix, pr) in beams) {
                val (pb, pnb) = pr
                add(prefix, logAdd(pb, pnb) + row[0], NEG)
                val last = prefix.lastOrNull()
                for (c in 1 until row.size) {
                    val ch = alphabet[c - 1].lowercaseChar()
                    val lp = row[c]
                    if (lp < PRUNE) continue
                    if (ch == last) {
                        add(prefix, NEG, pnb + lp)          // the same letter held
                        add(prefix + ch, NEG, pb + lp)      // a doubled letter needs a blank between
                    } else add(prefix + ch, NEG, logAdd(pb, pnb) + lp)
                }
            }
            beams = next.entries.sortedByDescending { logAdd(it.value.first, it.value.second) }.take(width).associate { it.key to it.value }
        }
        return beams.map { (p, v) -> p to logAdd(v.first, v.second) }.sortedByDescending { it.second }
    }

    /** log P(text | frames) by the CTC forward algorithm; null when a letter is outside the alphabet or too long. */
    fun ctcLogProb(logp: Array<FloatArray>, text: String): Float? {
        val labels = text.map { index[it] ?: return null }
        val s = 2 * labels.size + 1
        if (labels.size > logp.size) return null
        fun lab(i: Int) = if (i % 2 == 0) 0 else labels[i / 2]
        var alpha = FloatArray(s) { NEG }
        alpha[0] = logp[0][0]
        if (s > 1) alpha[1] = logp[0][lab(1)]
        for (t in 1 until logp.size) {
            val next = FloatArray(s) { NEG }
            for (i in 0 until s) {
                var a = alpha[i]
                if (i >= 1) a = logAdd(a, alpha[i - 1])
                if (i >= 2 && lab(i) != 0 && lab(i) != lab(i - 2)) a = logAdd(a, alpha[i - 2])
                next[i] = a + logp[t][lab(i)]
            }
            alpha = next
        }
        return if (s > 1) logAdd(alpha[s - 1], alpha[s - 2]) else alpha[0]
    }

    companion object {
        private const val NEG = -1e30f
        private const val BEAM = 10
        private const val PRUNE = -12f
        // Calibration knobs: tune on held-out spelling clips so wrong snaps stay under 2% (docs/10, Phase 3).
        private const val LAMBDA = 1f
        private const val MARGIN = 2f
        /** The prior of a spelling that is not a listed name. */
        private const val UNLISTED = 0.01f

        fun logAdd(a: Float, b: Float): Float {
            if (a <= NEG) return b
            if (b <= NEG) return a
            val m = max(a, b)
            return m + ln(exp(a - m) + exp(b - m))
        }

        fun logSoftmax(logits: Array<FloatArray>): Array<FloatArray> = Array(logits.size) { t ->
            val row = logits[t]
            val m = row.max()
            val z = m + ln(row.sumOf { exp((it - m).toDouble()) }.toFloat())
            FloatArray(row.size) { row[it] - z }
        }

        /** "name<TAB>weight" lines, "#" comments; a missing weight is 0.5. */
        fun parseLexicon(text: String): Map<String, Float> = text.lineSequence()
            .map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }
            .associate { line -> line.substringBefore('\t').trim().lowercase() to (line.substringAfter('\t', "0.5").trim().toFloatOrNull() ?: 0.5f) }
    }
}
