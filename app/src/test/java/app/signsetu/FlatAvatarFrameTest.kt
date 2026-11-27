package app.signsetu

import app.signsetu.data.content.Gesture
import app.signsetu.data.content.HandKey
import app.signsetu.data.content.Key
import app.signsetu.ui.avatar.AdultBuild
import app.signsetu.ui.avatar.HandShape
import app.signsetu.ui.avatar.Timeline
import app.signsetu.ui.avatar.lerp
import app.signsetu.ui.avatar.shapeOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.sqrt

/** The drawn character's frame path: a gesture's frames are built once, and a recorded hand's shape is read
 * element by element. The numbers below are the maths the frame path had, so the values it returns stay the same. */
class FlatAvatarFrameTest {

    // Resting, then one key a second later on the chest: the middle of that move is a real blend.
    private val wave = Gesture(
        "wave",
        listOf(
            Key(t = 0f, R = HandKey(at = "rest"), L = HandKey(at = "rest")),
            Key(t = 1f, R = HandKey(at = "chest"), L = HandKey(at = "chest")),
        ),
    )

    // Adult build: rest wrist is (CENTER + side * (shoulderHalf - 6), 206) = (62, 206) and (138, 206); the
    // "chest" anchor is (100, 154) for both sides. Neither wrist is far enough from its shoulder to be clamped.
    private val restRightX = 62f
    private val restLeftX = 138f
    private val restY = 206f
    private val chestX = 100f
    private val chestY = 154f

    @Test
    fun poseStartsAtRestBlendsAtTheMidpointAndHoldsPastTheEnd() {
        val timeline = Timeline(wave, AdultBuild)
        assertEquals(1.8f, timeline.duration, 1e-6f)

        // 0 s is still the lead-in: the first frame is the rest pose and the blend has not started.
        val start = timeline.pose(0f)
        assertEquals(restRightX, start.r.x, 1e-4f)
        assertEquals(restY, start.r.y, 1e-4f)
        assertEquals(restLeftX, start.l.x, 1e-4f)
        assertEquals(restY, start.l.y, 1e-4f)
        assertEquals(HandShape.ALL.getValue("RELAX"), start.r.shape)
        assertEquals(-1f, start.r.roll, 1e-6f)
        assertTrue("no key sets an angle, so the hand follows the forearm", start.r.angle.isNaN())
        assertEquals(1f, start.r.elbowOut, 1e-6f)
        assertTrue("no recorded elbow", start.r.elbowX.isNaN())

        // 0.85 s: 0.5 s into the animation, 0.543 of the way from the first key to the chest key. The move lands
        // 0.08 s early (minOf(0.08, 0.25 * 1 s)) and holds, and the blend eases that.
        val raw = 0.5f / (1f - 0.08f)
        val m = ease(raw)
        val mid = timeline.pose(0.85f)
        assertEquals(lerp(restRightX, chestX, m), mid.r.x, 1e-4f)
        assertEquals(lerp(restY, chestY, m), mid.r.y, 1e-4f)
        assertEquals(lerp(restLeftX, chestX, m), mid.l.x, 1e-4f)
        assertEquals(lerp(restY, chestY, m), mid.l.y, 1e-4f)
        assertEquals(HandShape.ALL.getValue("RELAX"), mid.r.shape)

        // Long past the end the last frame is the rest pose, held.
        val end = timeline.pose(1_000_000f)
        assertEquals(restRightX, end.r.x, 1e-4f)
        assertEquals(restY, end.r.y, 1e-4f)
        assertEquals(start, end)
        // The face, head and body keys are all defaults here.
        assertEquals(0f, end.tilt, 1e-6f)
        assertEquals(0f, end.lean, 1e-6f)
        assertTrue(!end.rFront && !end.handsTouch)
    }

    @Test
    fun theSameTimelineGivesTheSamePoseOnEveryRepeat() {
        val timeline = Timeline(wave, AdultBuild)
        val times = (0..36).map { it * 0.05f } + 1_000_000f
        val first = times.map { timeline.pose(it) }
        repeat(3) { assertEquals(first, times.map { timeline.pose(it) }) }
        assertEquals("a second Timeline over the same keys agrees", first, times.map { Timeline(wave, AdultBuild).pose(it) })
        assertTrue("the sweep really moves", first.any { it.r.x != first.first().r.x })
    }

    @Test
    fun shapeOfReadsTheSameCurlsSpreadAndThumbFromTheFrame() {
        val base = 6
        val v = FloatArray((base + 21) * 3)
        // A right hand in MediaPipe's layout: wrist, then thumb, index, middle, ring and pinky, three joints each.
        val points = listOf(
            floatArrayOf(0f, 0f, 0f),
            floatArrayOf(0.02f, -0.01f, 0.01f), floatArrayOf(0.04f, -0.03f, 0.02f), floatArrayOf(0.06f, -0.05f, 0.03f), floatArrayOf(0.07f, -0.07f, 0.04f),
            floatArrayOf(0.03f, -0.03f, 0f), floatArrayOf(0.04f, -0.06f, 0f), floatArrayOf(0.045f, -0.08f, 0.005f), floatArrayOf(0.05f, -0.1f, 0.01f),
            floatArrayOf(0f, -0.03f, 0f), floatArrayOf(0f, -0.07f, 0f), floatArrayOf(0f, -0.09f, 0.005f), floatArrayOf(0f, -0.11f, 0.01f),
            floatArrayOf(-0.03f, -0.03f, 0f), floatArrayOf(-0.03f, -0.065f, 0f), floatArrayOf(-0.033f, -0.085f, 0.005f), floatArrayOf(-0.035f, -0.1f, 0.01f),
            floatArrayOf(-0.055f, -0.025f, 0f), floatArrayOf(-0.06f, -0.05f, 0f), floatArrayOf(-0.065f, -0.065f, 0.005f), floatArrayOf(-0.07f, -0.08f, 0.01f),
        )
        points.forEachIndexed { k, p -> for (axis in 0..2) v[(base + k) * 3 + axis] = p[axis] }

        val shape = shapeOf(v, base)
        assertEquals(indexedShapeOf(v, base), shape)
        // The sample is a real hand, not a degenerate frame that only reads its fallback curl.
        assertTrue("the fingers read as their own curls", shape.thumb != shape.index && shape.index != shape.pinky)
        assertTrue("the pinky is the most bent", shape.pinky > shape.middle)
        assertTrue("the index is spread away from the pinky", shape.spread > 0f)
    }

    private fun ease(f: Float) = if (f < 0.5f) 4 * f * f * f else 1 - (-2 * f + 2).let { it * it * it } / 2

    /** shapeOf before it indexed the frame directly: a FloatArray per joint, and a list per finger. */
    private fun indexedShapeOf(v: FloatArray, base: Int): HandShape {
        fun p(k: Int) = floatArrayOf(v[(base + k) * 3], v[(base + k) * 3 + 1], v[(base + k) * 3 + 2])
        fun d(a: FloatArray, b: FloatArray) = sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]))
        fun curl(first: Int): Float {
            val joints = (first..first + 3).map(::p)
            val path = d(joints[0], joints[1]) + d(joints[1], joints[2]) + d(joints[2], joints[3])
            if (path < 1e-5f) return 0.3f
            val straight = d(joints[0], joints[3]) / path
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
}
