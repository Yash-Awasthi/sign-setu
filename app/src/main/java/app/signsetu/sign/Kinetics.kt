package app.signsetu.sign

import androidx.compose.ui.geometry.Offset
import app.signsetu.camera.LandmarkFrame
import kotlin.math.hypot

/** Per-point weights for a hand's kinetic energy: the wrist carries the arm's movement, fingertips the handshape's. */
private val HAND_WEIGHTS = FloatArray(21) { i -> when (i) { 0 -> 2f; 4, 8, 12, 16, 20 -> 1f; else -> 0.5f } }
private val HAND_WEIGHT_SUM = HAND_WEIGHTS.sum()

/** A frame's shoulder span in frame widths, or a typical span when the shoulders are not seen. */
internal fun shoulderSpan(f: LandmarkFrame): Float {
    val p = f.pose?.takeIf { it.size > 12 } ?: return DEFAULT_SPAN
    val ky = 1f / f.aspect.coerceAtLeast(1e-3f)
    return hypot(p[11].x - p[12].x, (p[11].y - p[12].y) * ky).takeIf { it > 0.02f } ?: DEFAULT_SPAN
}

/** Kinetic energy of each hand between two frames: the weighted mean of squared point speeds, in shoulder widths
 * per second, so it is the same near the camera and far from it. A hand that just appeared has none yet. */
data class HandEnergy(val left: Float, val right: Float) {
    val total get() = left + right
}

internal fun handEnergy(prev: LandmarkFrame?, f: LandmarkFrame): HandEnergy {
    if (prev == null || f.timestampMs <= prev.timestampMs) return HandEnergy(0f, 0f)
    val seconds = (f.timestampMs - prev.timestampMs) / 1000f
    val span = shoulderSpan(f)
    val ky = 1f / f.aspect.coerceAtLeast(1e-3f)
    fun one(a: List<Offset>?, b: List<Offset>?): Float {
        if (a == null || b == null || a.size != 21 || b.size != 21) return 0f
        var e = 0f
        for (i in 0 until 21) {
            val vx = (b[i].x - a[i].x) / span / seconds
            val vy = (b[i].y - a[i].y) * ky / span / seconds
            e += HAND_WEIGHTS[i] * (vx * vx + vy * vy)
        }
        return e / HAND_WEIGHT_SUM
    }
    return HandEnergy(one(prev.leftHand, f.leftHand), one(prev.rightHand, f.rightHand))
}

/** Smoothed kinetic energy with two thresholds: active above [on], idle again only below [off], so a sign's slow
 * middle does not split it and tracker jitter at rest does not open one. */
class MotionGate(private val on: Float = GATE_ON, private val off: Float = GATE_OFF, private val alpha: Float = 0.5f) {
    var energy = 0f
        private set
    var active = false
        private set

    fun update(e: Float): Boolean {
        // A jump in the tracker squares into a huge value; capped, the gate still falls idle within a few frames.
        energy = alpha * e.coerceAtMost(on * 4) + (1 - alpha) * energy
        active = if (active) energy > off else energy > on
        return active
    }

    fun reset() { energy = 0f; active = false }
}

enum class Side { LEFT, RIGHT }

/** Which hand leads, from the energy each hand spends across signs; it changes only on clear evidence, since a
 * person's leading hand does not change within a session. Mirroring a left-led sign makes the right hand lead, as
 * every reader and the home signs see it. */
class DominantHand(private val decideAfter: Float = 40f, private val ratio: Float = 1.6f) {
    private var left = 0f
    private var right = 0f
    // Learnt on the analysis thread, read by the readers' threads.
    @Volatile var side: Side = Side.RIGHT
        private set
    /** Enough energy has been seen for [side] to be the person's, not the default. */
    @Volatile var decided = false
        private set

    fun see(e: HandEnergy) {
        left += e.left
        right += e.right
        if (left + right < decideAfter) return
        if (left > right * ratio) { side = Side.LEFT; decided = true } else if (right > left * ratio) { side = Side.RIGHT; decided = true }
    }

    /** The ways to read [frames] so that a sign and its mirror read alike: right-led by the segment's own lead hand,
     * else by the person's; with neither known, as seen and mirrored, for the reader to keep the surer. */
    fun orientations(frames: List<LandmarkFrame>): List<List<LandmarkFrame>> {
        val side = segmentLead(frames, ratio) ?: side.takeIf { decided } ?: return listOf(frames, frames.map(::mirror))
        return listOf(if (side == Side.LEFT) frames.map(::mirror) else frames)
    }

    /** [reader]'s guesses for [frames], from the orientation whose top guess is surer. */
    fun read(reader: SignReader, frames: List<LandmarkFrame>): List<SignGuess> =
        orientations(frames).map(reader::read).maxByOrNull { g -> g.maxOfOrNull { it.score } ?: -1f }.orEmpty()

    fun letter(reader: SignReader, frames: List<LandmarkFrame>): SignGuess? =
        orientations(frames).mapNotNull(reader::letter).maxByOrNull { it.score }
}

/** The hand that leads [frames]: the one spending clearly more energy or, with the hands held still, the one seen in
 * clearly more frames; null for a sign both hands make alike. A lone hand leads. */
internal fun segmentLead(frames: List<LandmarkFrame>, ratio: Float = 1.6f): Side? {
    var l = 0f; var r = 0f
    for (i in 1 until frames.size) { val e = handEnergy(frames[i - 1], frames[i]); l += e.left; r += e.right }
    lead(l, r, ratio)?.let { return it }
    // Moving, but alike: a symmetric sign, which no frame count can split.
    if (frames.size > 1 && (l + r) / (frames.size - 1) > GATE_OFF) return null
    return lead(frames.count { it.leftHand?.size == 21 }.toFloat(), frames.count { it.rightHand?.size == 21 }.toFloat(), ratio)
}

private fun lead(l: Float, r: Float, ratio: Float): Side? = when {
    l + r <= 0f -> null
    l > r * ratio -> Side.LEFT
    r > l * ratio -> Side.RIGHT
    else -> null
}

/** The frame as a mirror shows it: x flipped, the hands swapped, and the body's left and right points swapped. The
 * face is flipped with its points left in place, as the readers' training mirror does (tools/ml/exp.py augment). */
fun mirror(f: LandmarkFrame): LandmarkFrame {
    fun flip(p: List<Offset>?) = p?.map { Offset(1f - it.x, it.y) }
    val pose = flip(f.pose)?.let { p -> List(p.size) { i -> p[POSE_MIRROR.getOrElse(i) { i }.coerceAtMost(p.size - 1)] } }
    return f.copy(leftHand = flip(f.rightHand), rightHand = flip(f.leftHand), pose = pose, face = flip(f.face))
}

/** MediaPipe pose index of each point's mirror twin. */
private val POSE_MIRROR = intArrayOf(0, 4, 5, 6, 1, 2, 3, 8, 7, 10, 9, 12, 11, 14, 13, 16, 15, 18, 17, 20, 19, 22, 21, 24, 23, 26, 25, 28, 27, 30, 29, 32, 31)

/** A sliding window of the last [t] frames as a ready model input of shape (1, [t], 98): each row is written once,
 * when its frame arrives, centred on and scaled by a running shoulder estimate; reading it copies rows in time order
 * into a reused tensor. The frames themselves are kept too, for readers that take landmarks. */
class FeatureRing(val t: Int = RING_FRAMES) {
    private val rows = Array(t) { FloatArray(FEATURES) }
    private val frames = arrayOfNulls<LandmarkFrame>(t)
    private val tensor = Array(1) { Array(t) { FloatArray(FEATURES) } }
    private var next = 0
    var size = 0
        private set
    private var cx = Float.NaN
    private var cy = 0f
    private var scale = 0f

    fun push(f: LandmarkFrame) {
        val ky = 1f / f.aspect.coerceAtLeast(1e-3f)
        f.pose?.takeIf { it.size > 16 }?.let { p ->
            val x = (p[11].x + p[12].x) / 2
            val y = (p[11].y + p[12].y) / 2 * ky
            val s = hypot(p[11].x - p[12].x, (p[11].y - p[12].y) * ky)
            if (cx.isNaN()) { cx = x; cy = y; scale = s } else { cx += 0.2f * (x - cx); cy += 0.2f * (y - cy); scale += 0.2f * (s - scale) }
        }
        val row = rows[next]
        row.fill(0f)
        if (!cx.isNaN() && scale > 1e-6f) {
            fun put(at: Int, p: Offset) { row[at] = (p.x - cx) / scale; row[at + 1] = (p.y * ky - cy) / scale }
            f.leftHand?.takeIf { it.size == 21 }?.let { h -> h.forEachIndexed { i, p -> put(2 * i, p) }; row[96] = 1f }
            f.rightHand?.takeIf { it.size == 21 }?.let { h -> h.forEachIndexed { i, p -> put(42 + 2 * i, p) }; row[97] = 1f }
            f.pose?.takeIf { it.size > 16 }?.let { p -> for (i in 0 until 6) put(84 + 2 * i, p[11 + i]) }
        }
        frames[next] = f
        next = (next + 1) % t
        if (size < t) size++
    }

    /** The window oldest first; the same arrays every call, so read it before the next [push]. */
    fun tensor(): Array<Array<FloatArray>> {
        val start = (next - size + t) % t
        for (j in 0 until t) {
            val dst = tensor[0][j]
            if (j < t - size) dst.fill(0f) else rows[(start + j - (t - size)) % t].copyInto(dst)
        }
        return tensor
    }

    /** The last [n] frames, oldest first. */
    fun frames(n: Int = size): List<LandmarkFrame> {
        val k = n.coerceAtMost(size)
        return List(k) { i -> frames[(next - k + i + t) % t]!! }
    }

    fun clear() { frames.fill(null); next = 0; size = 0; cx = Float.NaN }
}

/** Live fingerspelling: a letter the reader keeps seeing, sure and with the hands still, for [holdMs] is committed
 * once. The same letter again needs the hand to change or move first, which is how a double letter is signed. */
class LetterCommitter(private val holdMs: Long = LETTER_HOLD_MS, private val floor: Float = LETTER_FLOOR) {
    private var candidate: String? = null
    private var since = 0L
    private var committed: String? = null
    val letters = StringBuilder()

    /** [guess] is the letter reader's top on the latest frames, null when the hands moved or nothing was read. */
    fun see(ms: Long, guess: SignGuess?): String? {
        val letter = guess?.takeIf { it.score >= floor && it.word.length == 1 }?.word
        if (letter == null) { candidate = null; committed = null; return null }
        if (letter != candidate) { candidate = letter; since = ms; if (letter != committed) committed = null; return null }
        if (committed == letter || ms - since < holdMs) return null
        committed = letter
        letters.append(letter)
        return letter
    }

    fun clear() { candidate = null; committed = null; letters.clear() }
}

/** A sign is ambiguous when two readers disagree on it or, with no calibrated set to judge by ([margin]), when the
 * runner-up is nearly as likely as the top. Spelled reads are judged by their own reader. */
fun ambiguous(guesses: List<SignGuess>, margin: Boolean): Boolean {
    val a = guesses.getOrNull(0) ?: return false
    if (a.spelled) return false
    if (a.disputed) return true
    val b = guesses.getOrNull(1) ?: return false
    return margin && b.score >= a.score * AMBIGUOUS_RATIO
}

private const val DEFAULT_SPAN = 0.25f
/** About 1.1 shoulder widths a second at the wrist: a little under the speed STILL_SPEED marks as moving. */
const val GATE_ON = 1.2f
const val GATE_OFF = 0.45f
const val RING_FRAMES = 30
/** Live top 3 while the hands move: fast enough to follow a sign made in half a second. */
const val LIVE_PEEK_MS = 250L
const val LETTER_HOLD_MS = 500L
const val LETTER_FLOOR = 0.6f
const val AMBIGUOUS_RATIO = 0.8f
/** The second reader's top must be at least this sure to count as a disagreement. */
const val OTHER_SURE = 0.3f
