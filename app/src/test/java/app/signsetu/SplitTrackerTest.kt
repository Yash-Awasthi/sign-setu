package app.signsetu

import androidx.compose.ui.geometry.Offset
import app.signsetu.camera.Head
import app.signsetu.camera.HeadGestures
import app.signsetu.camera.LandmarkFrame
import app.signsetu.camera.assignHands
import app.signsetu.camera.headAngles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class SplitTrackerTest {
    private fun hand(x: Float, y: Float) = List(21) { Offset(x + it * 0.001f, y) }
    private val pose = List(33) { i -> when (i) { 15 -> Offset(0.7f, 0.6f); 16 -> Offset(0.3f, 0.6f); else -> Offset(0.5f, 0.5f) } }

    @Test
    fun handsGoToTheNearerWristLikeHolistic() {
        val a = hand(0.31f, 0.58f)
        val b = hand(0.69f, 0.61f)
        val (left, right) = assignHands(listOf(a, b), listOf("Left", "Right"), pose)
        assertSame(b, left)
        assertSame(a, right)
        assertEquals(null to a, assignHands(listOf(a), listOf("Right"), pose))
        // Without a body the hand model's label is used, flipped for an unmirrored frame.
        assertEquals(null to a, assignHands(listOf(a), listOf("Left"), null))
    }

    /** Column-major rotation about the vertical axis by [deg], as the face model's transform would hold it. */
    private fun yawMatrix(deg: Float): FloatArray {
        val r = Math.toRadians(deg.toDouble())
        val c = cos(r).toFloat(); val s = sin(r).toFloat()
        // Rows: [c 0 s], [0 1 0], [-s 0 c]; stored column by column.
        return floatArrayOf(c, 0f, -s, 0f, 0f, 1f, 0f, 0f, s, 0f, c, 0f, 0f, 0f, 0f, 1f)
    }

    @Test
    fun headAnglesSeparateTurningFromNodding() {
        val (yaw, pitch) = headAngles(yawMatrix(20f))
        assertEquals(20f, yaw, 0.01f)
        assertEquals(0f, pitch, 0.01f)
    }

    /** A head turning side to side by about 12 degrees, a few times a second, is a shake; the face points stay put. */
    @Test
    fun aShakeIsReadFromRotationAlone() {
        val face = List(478) { Offset(0.5f, 0.4f) }
        val g = HeadGestures(windowMs = 1300L, swing = 0.10f)
        var seen: Head? = null
        for (i in 0 until 40) {
            val yaw = 12f * sin(i * 0.9f)
            seen = seen ?: g.mark(LandmarkFrame(i * 40L, face = face, yaw = yaw, pitch = 0f)).head
        }
        assertEquals(Head.Shake, seen)
        val still = HeadGestures(windowMs = 1300L, swing = 0.10f)
        assertNull((0 until 40).firstNotNullOfOrNull { still.mark(LandmarkFrame(it * 40L, face = face, yaw = 1f, pitch = 0f)).head })
    }
}
