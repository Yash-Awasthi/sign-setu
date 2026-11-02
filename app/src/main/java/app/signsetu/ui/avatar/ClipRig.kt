package app.signsetu.ui.avatar

import app.signsetu.data.content.Clip
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/* Recorded sentence clips on the drawn character: a real signer's arms and hands, each frame turned into the rig's
 * own pose (wrists, recorded elbows, finger curls, spread, thumb and palm turn), so the 2D character signs from the
 * same recordings as the 3D one. Clip layout (tools/text2isl): shoulders, elbows, wrists (left then right), then 21
 * left-hand and 21 right-hand points, x y z each, y up, the signer's left at +x. */

private const val L_SHOULDER = 0
private const val R_SHOULDER = 1
private const val L_ELBOW = 2
private const val R_ELBOW = 3
private const val L_WRIST = 4
private const val R_WRIST = 5
private const val L_HAND = 6
private const val R_HAND = 27

/** Seconds a clip lasts. */
val Clip.seconds: Float get() = if (frames.isEmpty() || fps <= 0f) 0f else frames.size / fps

/** The pose [seconds] into [clip], blended between its two nearest frames. */
fun clipPose(clip: Clip, seconds: Float, build: Build): Pose {
    if (clip.frames.isEmpty()) return Timeline.restPose(build)
    val at = (seconds * clip.fps).coerceIn(0f, (clip.frames.size - 1).toFloat())
    val i = at.toInt()
    val f = at - i
    val a = clip.frames[i]
    val b = clip.frames[minOf(i + 1, clip.frames.size - 1)]
    val v = FloatArray(a.size) { k -> a[k] + (b.getOrElse(k) { a[k] } - a[k]) * f }
    return framePose(v, build)
}

internal fun framePose(v: FloatArray, build: Build): Pose {
    fun x(p: Int) = v.getOrElse(p * 3) { 0f }
    fun y(p: Int) = v.getOrElse(p * 3 + 1) { 0f }
    fun z(p: Int) = v.getOrElse(p * 3 + 2) { 0f }
    val width = hypot(x(L_SHOULDER) - x(R_SHOULDER), y(L_SHOULDER) - y(R_SHOULDER)).takeIf { it > 1e-4f }
        ?: return Timeline.restPose(build)
    val s = 2 * build.shoulderHalf / width
    val mx = (x(L_SHOULDER) + x(R_SHOULDER)) / 2
    val my = (y(L_SHOULDER) + y(R_SHOULDER)) / 2
    // Character space: the signer's left (+x) is the character's left, drawn at +x from the centre; y runs down.
    fun cx(p: Int) = Build.CENTER + (x(p) - mx) * s
    fun cy(p: Int) = build.shoulderY - (y(p) - my) * s

    fun hand(side: Side, wrist: Int, elbow: Int, base: Int): HandPose {
        val (sx, sy) = build.shoulder(side)
        // Bone directions are kept and lengths are the character's own: a person's upper arm is near a full
        // shoulder width, the drawn one about half, so scaling positions alone drops the elbow to the hip.
        val shoulderIdx = if (side == Side.R) R_SHOULDER else L_SHOULDER
        fun unit(ax: Float, ay: Float): Pair<Float, Float> { val n = hypot(ax, ay).takeIf { it > 1e-5f } ?: return 0f to 1f; return ax / n to ay / n }
        val (ux, uy) = unit(cx(elbow) - cx(shoulderIdx), cy(elbow) - cy(shoulderIdx))
        val ex = sx + ux * build.upperArm
        val ey = sy + uy * build.upperArm
        val (fx, fy) = unit(cx(wrist) - cx(elbow), cy(wrist) - cy(elbow))
        val wx = ex + fx * build.foreArm
        val wy = ey + fy * build.foreArm
        val present = (0 until 21).any { k -> x(base + k) != 0f || y(base + k) != 0f }
        val shape = if (present) shapeOf(v, base) else HandShape.ALL.getValue("RELAX")
        val angle = if (present) {
            val dx = cx(base + 9) - cx(base)
            val dy = cy(base + 9) - cy(base)
            Math.toDegrees(atan2(dx, -dy).toDouble()).toFloat()
        } else Float.NaN
        return HandPose(
            x = wx, y = wy, shape = shape,
            roll = if (present) rollOf(v, base, side) else -1f,
            angle = angle, elbowOut = 1f,
            elbowX = ex - sx, elbowY = ey - sy,
        )
    }
    return Pose(
        r = hand(Side.R, R_WRIST, R_ELBOW, R_HAND),
        l = hand(Side.L, L_WRIST, L_ELBOW, L_HAND),
        face = Face(), tilt = 0f, nod = 0f, turn = 0f, lean = 0f, shrug = 0f, hunch = 0f, shiver = 0f,
    )
}

/** Finger curls from how far each fingertip falls short of its finger's full length; spread from the angle between
 * index and pinky; the thumb is across when its tip is nearer the little finger's knuckle than the index's. */
internal fun shapeOf(v: FloatArray, base: Int): HandShape {
    fun p(k: Int) = floatArrayOf(v[(base + k) * 3], v[(base + k) * 3 + 1], v[(base + k) * 3 + 2])
    fun d(a: FloatArray, b: FloatArray) = sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]))
    fun curl(first: Int): Float {
        val joints = (first..first + 3).map(::p)
        val path = d(joints[0], joints[1]) + d(joints[1], joints[2]) + d(joints[2], joints[3])
        if (path < 1e-5f) return 0.3f
        val straight = d(joints[0], joints[3]) / path
        // A straight finger is near 1; a closed fist folds the tip back to about 0.35 of the path.
        return ((1f - straight) / 0.6f).coerceIn(0f, 1f)
    }
    val wrist = p(0)
    fun dir(k: Int) = p(k).let { floatArrayOf(it[0] - wrist[0], it[1] - wrist[1]) }
    val i = dir(8); val l = dir(20)
    val cos = (i[0] * l[0] + i[1] * l[1]) / ((hypot(i[0], i[1]) * hypot(l[0], l[1])).takeIf { it > 1e-6f } ?: 1f)
    val spread = ((1f - cos) / 0.3f - 0.3f).coerceIn(-1f, 1f)
    val across = (d(p(4), p(5)) / (d(p(4), p(17)) + d(p(4), p(5))).coerceAtLeast(1e-5f)).let { ((it - 0.35f) / 0.4f).coerceIn(0f, 1f) }
    return HandShape(curl(1), curl(5), curl(9), curl(13), curl(17), across, spread)
}

/** Palm turn from the palm's normal: near ±1 the palm or its back faces the viewer, near 0 it is seen edge-on. */
internal fun rollOf(v: FloatArray, base: Int, side: Side): Float {
    fun c(k: Int, a: Int) = v[(base + k) * 3 + a]
    val ax = c(5, 0) - c(0, 0); val ay = c(5, 1) - c(0, 1); val az = c(5, 2) - c(0, 2)
    val bx = c(17, 0) - c(0, 0); val by = c(17, 1) - c(0, 1); val bz = c(17, 2) - c(0, 2)
    val nx = ay * bz - az * by
    val ny = az * bx - ax * bz
    val nz = ax * by - ay * bx
    val n = sqrt(nx * nx + ny * ny + nz * nz).takeIf { it > 1e-9f } ?: return -1f
    // The cross product turns with the hand's side, so one sign means "palm to the viewer" for both hands.
    val facing = nz / n * (if (side == Side.R) 1f else -1f)
    return if (abs(facing) < 0.2f) 0f else facing
}
