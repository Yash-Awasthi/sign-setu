package app.signsetu.ui.avatar

import app.signsetu.data.content.HandKey
import app.signsetu.data.content.HeadKey
import app.signsetu.data.content.BodyKey
import app.signsetu.data.content.Key
import app.signsetu.data.content.Gesture
import app.signsetu.data.content.RestKey
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sign
import kotlin.math.sin

/** Plays a gesture: eases in from rest, through the keys, and back to rest. */
class Timeline(val gesture: Gesture?, private val build: Build) {
    private val keys: List<Key> = gesture?.keys.orEmpty().sortedBy { it.t }
    val duration: Float = if (keys.isEmpty()) 0f else LEAD_IN + keys.last().t + LEAD_OUT

    fun pose(seconds: Float): Pose {
        if (keys.isEmpty()) return restPose(build)
        val t = seconds - LEAD_IN
        val rest = restFrame()
        val frames = listOf(rest.copy(t = -LEAD_IN)) + filled() + rest.copy(t = keys.last().t + LEAD_OUT)
        val i = frames.indexOfLast { it.t <= t }.coerceAtLeast(0)
        val a = frames[i]
        val b = frames.getOrElse(i + 1) { a }
        if (flows(frames, i)) return flow(frames, i, ((t - a.t) / (b.t - a.t)).coerceIn(0f, 1f))
        // Each move lands a little early and holds, so a sign's shape reads before the next one starts.
        val hold = minOf(0.08f, 0.25f * (b.t - a.t))
        val raw = if (b.t > a.t) ((t - a.t) / (b.t - a.t - hold)).coerceIn(0f, 1f) else 0f
        return blend(a, b, raw)
    }

    private data class Frame(
        val t: Float, val r: HandKey, val l: HandKey, val face: Face, val head: HeadKey, val body: BodyKey,
    )

    private fun restFrame() = Frame(0f, RestKey, RestKey, Face(), HeadKey(), BodyKey())

    /** Each key holds the previous key's values for anything it leaves out. */
    private fun filled(): List<Frame> {
        var prev = restFrame()
        return keys.map { k ->
            Frame(
                t = k.t,
                r = k.R ?: prev.r,
                l = k.L ?: prev.l,
                face = k.face?.let { Face.PRESETS[it] } ?: prev.face,
                head = k.head ?: prev.head,
                body = k.body ?: prev.body,
            ).also { prev = it }
        }
    }

    private fun measured(f: Frame) = f.r.shape.startsWith("c:") || f.l.shape.startsWith("c:")

    /** Recorded keys close together sample one movement, so the hands pass through them without stopping. */
    private fun flows(frames: List<Frame>, i: Int) =
        i in 0 until frames.lastIndex && frames[i + 1].t - frames[i].t < FLOW && measured(frames[i]) && measured(frames[i + 1])

    /** Catmull-Rom through the recorded keys: velocity carries across a key, and is zero where a run starts or ends. */
    private fun flow(frames: List<Frame>, i: Int, u: Float): Pose {
        val base = blend(frames[i], frames[i + 1], u)
        fun key(k: Int, side: Side) = if (side == Side.R) frames[k].r else frames[k].l
        fun track(k: Int, side: Side): FloatArray {
            val key = key(k, side)
            val (x, y) = resolve(key, side, null)
            val s = handShape(key.shape) ?: HandShape.ALL.getValue("RELAX")
            val e = recordedElbow(key, side) ?: solvedElbow(x to y, side, elbow(key.elbow))
            return floatArrayOf(x, y, key.roll, (key.angle ?: 0f) * side.sign, s.thumb, s.index, s.middle, s.ring, s.pinky, s.thumbAcross, s.spread, e.first, e.second)
        }
        fun at(side: Side, h: HandPose): HandPose {
            val p1 = track(i, side)
            val p2 = track(i + 1, side)
            val p0 = if (flows(frames, i - 1)) track(i - 1, side) else null
            val p3 = if (flows(frames, i + 1)) track(i + 2, side) else null
            val t1 = frames[i].t
            val t2 = frames[i + 1].t
            val v = FloatArray(p1.size) { j ->
                val m1 = p0?.let { (p2[j] - it[j]) / (t2 - frames[i - 1].t) } ?: 0f
                val m2 = p3?.let { (it[j] - p1[j]) / (frames[i + 2].t - t1) } ?: 0f
                hermite(p1[j], p2[j], m1 * (t2 - t1), m2 * (t2 - t1), u)
            }
            fun curl(j: Int) = v[j].coerceIn(0f, 1f)
            val rec = recordedElbow(key(i, side), side) != null || recordedElbow(key(i + 1, side), side) != null
            return h.copy(
                elbowX = if (rec) v[11] else h.elbowX, elbowY = if (rec) v[12] else h.elbowY,
                x = v[0], y = v[1], roll = v[2],
                angle = if (key(i, side).angle != null && key(i + 1, side).angle != null) v[3] else h.angle,
                shape = HandShape(curl(4), curl(5), curl(6), curl(7), curl(8), curl(9), v[10].coerceIn(-1f, 1f)),
            )
        }
        return base.copy(r = at(Side.R, base.r), l = at(Side.L, base.l))
    }

    private fun blend(a: Frame, b: Frame, raw: Float): Pose {
        val f = ease(raw)
        // The hand placed on the other one is solved second; fingerspelling places the right hand on the left.
        val rFollows = a.r.at in OTHER_HAND || b.r.at in OTHER_HAND
        val lFollows = a.l.at in OTHER_HAND || b.l.at in OTHER_HAND
        val r: HandPose
        val l: HandPose
        if (rFollows) {
            l = hand(a.l, b.l, raw, Side.L)
            r = hand(a.r, b.r, raw, Side.R, l)
        } else {
            r = hand(a.r, b.r, raw, Side.R)
            l = hand(a.l, b.l, raw, Side.L, r)
        }
        return Pose(
            r = r, l = l,
            face = a.face.lerp(b.face, f),
            tilt = lerp(a.head.tilt, b.head.tilt, f), nod = lerp(a.head.nod, b.head.nod, f), turn = lerp(a.head.turn, b.head.turn, f),
            lean = lerp(a.body.lean, b.body.lean, f), shrug = lerp(a.body.shrug, b.body.shrug, f),
            hunch = lerp(a.body.hunch, b.body.hunch, f), shiver = lerp(a.body.shiver, b.body.shiver, f),
            rFront = rFollows,
            handsTouch = rFollows || lFollows,
        )
    }

    private fun hand(a: HandKey, b: HandKey, raw: Float, side: Side, other: HandPose? = null): HandPose {
        val f = ease(raw)
        // A free move overshoots slightly and settles; contacts and moves to or from rest never pass their target.
        val free = b.touch == "wrist" && b.at !in OTHER_HAND && b.at != "rest" && a.at != "rest"
        val m = if (free) settle(raw) else f
        val pa = resolve(a, side, other)
        val pb = resolve(b, side, other)
        // handShape (MeasuredShape.kt) also reads measured "c:" shapes from isl_signs.json.
        val sa = handShape(a.shape) ?: HandShape.ALL.getValue("RELAX")
        val sb = handShape(b.shape) ?: HandShape.ALL.getValue("RELAX")
        val angleA = a.angle?.let { it * side.sign } ?: Float.NaN
        val angleB = b.angle?.let { it * side.sign } ?: Float.NaN
        val ea = recordedElbow(a, side)
        val eb = recordedElbow(b, side)
        // A recorded elbow next to a solved one starts or ends at the solve, so it moves without a jump.
        val oa = if (eb != null) ea ?: solvedElbow(pa, side, elbow(a.elbow)) else ea
        val ob = if (ea != null) eb ?: solvedElbow(pb, side, elbow(b.elbow)) else eb
        return HandPose(
            x = lerp(pa.first, pb.first, m),
            y = lerp(pa.second, pb.second, m),
            shape = blendShape(sa, sb, f),
            roll = lerp(a.roll, b.roll, f),
            angle = when {
                angleA.isNaN() && angleB.isNaN() -> Float.NaN
                angleA.isNaN() -> if (f < 0.5f) Float.NaN else angleB
                angleB.isNaN() -> if (f < 0.5f) angleA else Float.NaN
                else -> lerp(angleA, angleB, m)
            },
            elbowOut = lerp(elbow(a.elbow), elbow(b.elbow), f),
            prop = if (f < 0.5f) a.prop else b.prop,
            elbowX = if (oa != null && ob != null) lerp(oa.first, ob.first, m) else Float.NaN,
            elbowY = if (oa != null && ob != null) lerp(oa.second, ob.second, m) else Float.NaN,
        )
    }

    private fun recordedElbow(k: HandKey, side: Side) = if (k.ex != null && k.ey != null) k.ex * side.sign to k.ey else null

    /** The two-bone elbow for wrist [p] as an offset from the unshifted shoulder, like a recorded one. */
    private fun solvedElbow(p: Pair<Float, Float>, side: Side, out: Float): Pair<Float, Float> {
        val (sx, sy) = build.shoulder(side)
        val (ex, ey) = solveElbow(sx, sy, p.first, p.second, build.upperArm, build.foreArm, side, out)
        return (ex - sx) to (ey - sy)
    }

    private fun resolve(k: HandKey, side: Side, other: HandPose?): Pair<Float, Float> {
        val base = when {
            k.at == "rest" -> build.rest(side)
            k.at in OTHER_HAND && other != null -> other.point(k.at.removeSuffix("_opp"), if (side == Side.R) Side.L else Side.R, build.handScale)
            else -> build.anchor(k.at, side) ?: build.rest(side)
        }
        // +dx is outward: towards the viewer's left for the right hand.
        val (bx, by) = reachBack(k, (base.first + k.dx * side.sign) to (base.second + k.dy), side)
        val (sx, sy) = build.shoulder(side)
        return clampReach(sx, sy, bx to by, build.upperArm, build.foreArm)
    }

    /** Moves the target back along the hand so the fingertip or palm, not the wrist, lands on the anchor. */
    private fun reachBack(k: HandKey, point: Pair<Float, Float>, side: Side): Pair<Float, Float> {
        val reach = when (k.touch) {
            "palm" -> 6.5f
            "thumb" -> 9f
            "tip" -> 12f + 9.5f * (1f - 0.75f * (handShape(k.shape)?.index ?: 0f))
            else -> return point
        } * build.handScale
        val a = Math.toRadians(((k.angle ?: 0f) * side.sign).toDouble())
        return (point.first - sin(a).toFloat() * reach) to (point.second + cos(a).toFloat() * reach)
    }

    companion object {
        /** Anchors on the other hand: its wrist, palm centre, or a fingertip. */
        val OTHER_HAND = setOf("wrist_opp", "palm_opp", "thumb_opp", "index_opp", "middle_opp", "ring_opp", "pinky_opp")
        const val LEAD_IN = 0.35f
        const val LEAD_OUT = 0.45f
        const val FLOW = 0.35f

        fun restPose(build: Build): Pose {
            val r = build.rest(Side.R)
            val l = build.rest(Side.L)
            val relax = HandShape.ALL.getValue("RELAX")
            return Pose(
                HandPose(r.first, r.second, relax, -1f, Float.NaN, 1f),
                HandPose(l.first, l.second, relax, -1f, Float.NaN, 1f),
                Face(), 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            )
        }

        private fun elbow(name: String) = when (name) {
            "in" -> -1f
            "down" -> 0f
            else -> 1f
        }
    }
}

fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f

private fun ease(f: Float) = if (f < 0.5f) 4 * f * f * f else 1 - (-2 * f + 2).let { it * it * it } / 2

private fun hermite(p1: Float, p2: Float, m1: Float, m2: Float, u: Float): Float {
    val u2 = u * u
    val u3 = u2 * u
    return (2 * u3 - 3 * u2 + 1) * p1 + (u3 - 2 * u2 + u) * m1 + (-2 * u3 + 3 * u2) * p2 + (u3 - u2) * m2
}

/** Ease-out-back (a 4.5% overshoot that settles) on a time warp that starts at rest: plain, it left every hold at
 * 4.1 times the mean speed, a jump; warped, its peak is 2.5 times, early, and the end stays slow. rig.ts matches. */
private fun settle(f: Float): Float {
    val g = f * f * (2 - f) - 1f
    return 1f + 2.1f * g * g * g + 1.1f * g * g
}

private fun blendShape(a: HandShape, b: HandShape, f: Float) = HandShape(
    lerp(a.thumb, b.thumb, f), lerp(a.index, b.index, f), lerp(a.middle, b.middle, f), lerp(a.ring, b.ring, f),
    lerp(a.pinky, b.pinky, f), lerp(a.thumbAcross, b.thumbAcross, f), lerp(a.spread, b.spread, f),
)

/** Two-bone arm: returns the elbow for a shoulder, wrist target and segment lengths. */
fun solveElbow(
    sx: Float, sy: Float, tx: Float, ty: Float, upper: Float, fore: Float, side: Side, elbowOut: Float,
    prefer: Pair<Float, Float>? = null,
): Pair<Float, Float> {
    val dx = tx - sx
    val dy = ty - sy
    val d = hypot(dx, dy).coerceIn(abs(upper - fore) + 0.01f, upper + fore - 0.01f)
    val base = atan2(dy, dx)
    val offset = acos(((upper * upper + d * d - fore * fore) / (2 * upper * d)).coerceIn(-1f, 1f))
    val e1 = (sx + upper * cos(base + offset)) to (sy + upper * sin(base + offset))
    val e2 = (sx + upper * cos(base - offset)) to (sy + upper * sin(base - offset))
    // "Out" picks the elbow further from the body's midline, "in" the nearer one, "down" the lower one.
    val score = { e: Pair<Float, Float> ->
        val outward = (e.first - Build.CENTER) * side.sign
        if (elbowOut >= 0f) outward * elbowOut + e.second * (1f - elbowOut) else -outward
    }
    val s1 = score(e1)
    val s2 = score(e2)
    // A level reach gives two mirror elbows with equal scores; the margin settles float noise on e1 (rig.ts matches).
    val best = if (s1 >= s2 - 1e-3f) e1 else e2
    if (prefer == null) return best
    // The elbow drawn last frame keeps its side, since a jump between the two solves reads as a broken elbow. When that
    // side is the wrong one it swings round the shoulder toward the scored side a little each frame, so it never stays bent in.
    // Between the solves the elbow sits at base + u * offset, u = 1 on e1 and -1 on e2; moving u stays on the upper arm's circle.
    val bestU = if (best === e1) 1f else -1f
    var rel = atan2(prefer.second - sy, prefer.first - sx) - base
    if (rel > PI) rel -= 2 * PI.toFloat() else if (rel < -PI) rel += 2 * PI.toFloat()
    val u = if (offset < 1e-4f) bestU else (rel / offset).coerceIn(-1f, 1f)
    val du = ELBOW_SWING / upper / offset.coerceAtLeast(1e-4f)
    if (abs(bestU - u) <= du) return best
    val a = base + (u + du * sign(bestU - u)) * offset
    return (sx + upper * cos(a)) to (sy + upper * sin(a))
}

/** How far a wrong-sided elbow moves back toward the scored side in one frame, in character units. */
private const val ELBOW_SWING = 2f

/** The reachable wrist for [target]: a two-bone arm cannot put the hand further than upper + fore from the
 * shoulder. */
fun clampReach(sx: Float, sy: Float, target: Pair<Float, Float>, upper: Float, fore: Float): Pair<Float, Float> {
    val dx = target.first - sx
    val dy = target.second - sy
    val d = hypot(dx, dy)
    val max = upper + fore - 0.01f
    if (d <= max) return target
    val k = max / d
    return (sx + dx * k) to (sy + dy * k)
}
