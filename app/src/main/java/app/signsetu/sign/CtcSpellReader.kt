package app.signsetu.sign

import app.signsetu.camera.LandmarkFrame
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.hypot

// Parsed once per process: a Json instance is expensive to build and the spec never changes shape.
private val SPEC_JSON = Json { ignoreUnknownKeys = true }

/** A bundled fingerspelling reader (format 2): landmarks to letters by CTC. ASL reads one hand
 * (tools/ml/spell_ctc.py); ISL reads both, as its letters are two-handed (tools/ml/isl_spell_ctc.py). */
class CtcSpellReader(spec: String, weights: ByteArray) {
    @Serializable
    private class Spec(
        val format: Int,
        val fps: Int,
        @SerialName("max_frames") val maxFrames: Int,
        val features: Int,
        val hidden: Int,
        val kernel: Int,
        val dilations: List<Int>,
        val alphabet: String,
        val dtype: String = "float32",
        val input: String = "one_hand",
        @SerialName("min_peak") val minPeak: Float? = null,
    )

    private val s = SPEC_JSON.decodeFromString<Spec>(spec)
    val alphabet: String = s.alphabet
    /** How sure each letter must be at its best frame for a read to count, when the model sets its own. */
    val minPeak: Float? = s.minPeak
    private val twoHands = s.input == "two_hands"
    private val nf = if (twoHands) TWO_HAND_FEATURES else FEATURES
    private val h = s.hidden
    private val k = s.kernel
    private val classes = alphabet.length + 1
    private val embedW: FloatArray
    private val embedB: FloatArray
    private val convW: List<FloatArray>
    private val convB: List<FloatArray>
    private val headW: FloatArray
    private val headB: FloatArray

    init {
        require(s.format == 2 && s.features == nf) { "format" }
        val flat = weightFloats(weights, s.dtype)
        var at = 0
        fun take(n: Int) = flat.copyOfRange(at, at + n).also { at += n }
        embedW = take(h * nf); embedB = take(h)
        val w = ArrayList<FloatArray>(); val b = ArrayList<FloatArray>()
        repeat(s.dilations.size) { w += take(h * h * k); b += take(h) }
        convW = w; convB = b
        headW = take(classes * h); headB = take(classes)
        require(at == flat.size) { "weights size" }
    }

    /** The letters read, empty when no hand was seen. */
    fun read(frames: List<LandmarkFrame>): String = logits(frames)?.let(::decode).orEmpty()

    /** Greedy CTC: the best class per frame, repeats merged, blanks (class 0) dropped. */
    fun decode(logits: Array<FloatArray>): String = buildString {
        var last = -1
        for (row in logits) {
            val best = argmax(row)
            if (best != 0 && best != last) append(alphabet[best - 1])
            last = best
        }
    }

    /** Per-frame class scores at the model's frame rate, or null when no frame shows a hand. */
    fun logits(frames: List<LandmarkFrame>): Array<FloatArray>? {
        val x = features(frames) ?: return null
        val t = x.size / nf
        var a = FloatArray(h * t)
        embed(x, embedW, embedB, a, h, t, nf)
        for ((n, d) in s.dilations.withIndex()) {
            val c = convRelu(a, convW[n], convB[n], h, t, k, d)
            for (i in c.indices) c[i] += a[i]
            a = c
        }
        return Array(t) { j ->
            FloatArray(classes) { c ->
                var v = headB[c]
                for (o in 0 until h) v += headW[c * h + o] * a[o * t + j]
                v
            }
        }
    }

    /** Source frame for each frame at [Spec.fps], by nearest timestamp (ties to the earlier frame). */
    private fun resample(frames: List<LandmarkFrame>): IntArray? {
        if (frames.isEmpty()) return null
        val t0 = frames.first().timestampMs
        val n = minOf((frames.last().timestampMs - t0) * s.fps / 1000 + 1, s.maxFrames.toLong()).toInt()
        if (n <= 0) return null
        val idx = IntArray(n)
        var i = 0
        for (q in 0 until n) {
            val target = t0 + q * 1000L / s.fps
            while (i + 1 < frames.size && abs(frames[i + 1].timestampMs - target) < abs(frames[i].timestampMs - target)) i++
            idx[q] = i
        }
        return idx
    }

    fun features(frames: List<LandmarkFrame>): FloatArray? = if (twoHands) twoHandFeatures(frames) else oneHandFeatures(frames)

    /** One hand per frame at [Spec.fps], from the wrist, in wrist to middle-knuckle lengths, plus wrist motion. */
    private fun oneHandFeatures(frames: List<LandmarkFrame>): FloatArray? {
        val idx = resample(frames) ?: return null
        val n = idx.size
        fun ok(p: List<*>?) = p != null && p.size == 21
        val useLeft = idx.count { ok(frames[it].leftHand) } > idx.count { ok(frames[it].rightHand) }
        val kx = if (useLeft) -1f else 1f
        val ky = 1f / frames.first().aspect
        val out = FloatArray(n * FEATURES)
        var seen = false
        var px = 0f; var py = 0f; var prev = false
        for (q in 0 until n) {
            val f = frames[idx[q]]
            val hand = (if (useLeft) f.leftHand else f.rightHand)?.takeIf { it.size == 21 }
            val wx = (hand?.get(0)?.x ?: 0f) * kx
            val wy = (hand?.get(0)?.y ?: 0f) * ky
            val size = if (hand == null) 0f else hypot(hand[9].x * kx - wx, hand[9].y * ky - wy)
            if (hand == null || size < 1e-6f) { prev = false; continue }
            val row = q * FEATURES
            hand.forEachIndexed { p, pt -> out[row + 2 * p] = (pt.x * kx - wx) / size; out[row + 2 * p + 1] = (pt.y * ky - wy) / size }
            if (prev) { out[row + 42] = (wx - px) / size; out[row + 43] = (wy - py) / size }
            out[row + 44] = 1f
            px = wx; py = wy; prev = true; seen = true
        }
        return out.takeIf { seen }
    }

    /** Both hands per frame at [Spec.fps], as isl_spell_features in tools/ml/isl_spell_ctc.py: each hand from its
     * wrist in its own wrist to middle-knuckle lengths, the right wrist from the left, both wrists from the shoulder
     * midpoint, and each wrist's step since the previous frame, those three in shoulder widths. */
    private fun twoHandFeatures(frames: List<LandmarkFrame>): FloatArray? {
        val idx = resample(frames) ?: return null
        val ky = 1f / frames.first().aspect
        var cx = 0f; var cy = 0f; var width = 0f; var shown = 0
        for (f in frames) {
            val p = f.pose?.takeIf { it.size > 12 } ?: continue
            cx += (p[11].x + p[12].x) / 2; cy += (p[11].y + p[12].y) * ky / 2
            width += hypot(p[11].x - p[12].x, (p[11].y - p[12].y) * ky); shown++
        }
        if (shown > 0) { cx /= shown; cy /= shown; width /= shown }
        val out = FloatArray(idx.size * TWO_HAND_FEATURES)
        val px = FloatArray(2); val py = FloatArray(2); val prev = BooleanArray(2)
        var seen = false
        for ((q, j) in idx.withIndex()) {
            val row = q * TWO_HAND_FEATURES
            val f = frames[j]
            val wx = FloatArray(2); val wy = FloatArray(2); val at = BooleanArray(2)
            for ((side, hand) in listOf(f.leftHand, f.rightHand).withIndex()) {
                val pts = hand?.takeIf { it.size == 21 }
                val x0 = pts?.get(0)?.x ?: 0f
                val y0 = (pts?.get(0)?.y ?: 0f) * ky
                val size = if (pts == null) 0f else hypot(pts[9].x - x0, pts[9].y * ky - y0)
                if (pts == null || size < 1e-6f) { prev[side] = false; continue }
                pts.forEachIndexed { p, pt ->
                    out[row + 42 * side + 2 * p] = (pt.x - x0) / size
                    out[row + 42 * side + 2 * p + 1] = (pt.y * ky - y0) / size
                }
                if (width > 1e-6f) {
                    out[row + 86 + 2 * side] = (x0 - cx) / width; out[row + 87 + 2 * side] = (y0 - cy) / width
                    if (prev[side]) { out[row + 90 + 2 * side] = (x0 - px[side]) / width; out[row + 91 + 2 * side] = (y0 - py[side]) / width }
                }
                out[row + 94 + side] = 1f
                px[side] = x0; py[side] = y0; prev[side] = true
                wx[side] = x0; wy[side] = y0; at[side] = true
                seen = true
            }
            if (at[0] && at[1] && width > 1e-6f) { out[row + 84] = (wx[1] - wx[0]) / width; out[row + 85] = (wy[1] - wy[0]) / width }
        }
        return out.takeIf { seen }
    }

    private companion object {
        const val FEATURES = 45
        const val TWO_HAND_FEATURES = 96
    }
}

internal fun argmax(row: FloatArray): Int {
    var best = 0
    for (c in 1 until row.size) if (row[c] > row[best]) best = c
    return best
}
