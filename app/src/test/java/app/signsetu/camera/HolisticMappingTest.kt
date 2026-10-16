package app.signsetu.camera

import androidx.compose.ui.geometry.Offset
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HolisticMappingTest {
    private fun points(n: Int, x: Float) = List(n) { NormalizedLandmark.create(x, it / 100f, 0.5f) }

    @Test fun keepsSidesOrderAndTimestamp() {
        val frame = landmarkFrame(42L, points(21, 0.7f), points(21, 0.3f), points(468, 0.5f), points(33, 0.5f))
        assertEquals(42L, frame.timestampMs)
        assertEquals(Offset(0.7f, 0.05f), frame.leftHand!![5])
        assertEquals(Offset(0.3f, 0.2f), frame.rightHand!![20])
        assertEquals(468, frame.face!!.size)
        assertEquals(33, frame.pose!!.size)
    }

    @Test fun emptyListsAreNotSeen() {
        val frame = landmarkFrame(1L, emptyList(), points(21, 0.3f), emptyList(), emptyList())
        assertNull(frame.leftHand)
        assertNull(frame.face)
        assertNull(frame.pose)
        assertEquals(listOf(frame.rightHand), frame.hands)
    }
}
