package app.signsetu.camera

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/**
 * Debug builds: the bundled holistic model, or a stand-in tracker that reports one waving open right hand, to check the
 * camera, analysis, overlay and capture path without a model. Turn it on with `adb shell run-as in.signsetu touch files/demo_hands`.
 */
object LandmarkTrackers {
    fun create(context: Context): LandmarkTracker? =
        if (File(context.filesDir, "demo_hands").exists()) DemoTracker() else holisticTracker(context)
}

private class DemoTracker : LandmarkTracker {
    override fun track(frame: Bitmap, timestampMs: Long): LandmarkFrame {
        val a = sin(timestampMs / 400.0).toFloat() * 0.25f
        val wrist = OPEN_HAND[0]
        val points = OPEN_HAND.map { p ->
            val dx = p.x - wrist.x
            val dy = p.y - wrist.y
            Offset(wrist.x + dx * cos(a) - dy * sin(a), wrist.y + dx * sin(a) + dy * cos(a))
        }
        return LandmarkFrame(timestampMs, rightHand = points)
    }
}

// An open right hand in the 21-point MediaPipe order, normalised to the frame.
private val OPEN_HAND = listOf(
    Offset(0.50f, 0.80f),
    Offset(0.42f, 0.74f), Offset(0.36f, 0.68f), Offset(0.32f, 0.62f), Offset(0.29f, 0.57f),
    Offset(0.43f, 0.58f), Offset(0.42f, 0.48f), Offset(0.415f, 0.42f), Offset(0.41f, 0.37f),
    Offset(0.50f, 0.56f), Offset(0.50f, 0.45f), Offset(0.50f, 0.38f), Offset(0.50f, 0.32f),
    Offset(0.57f, 0.58f), Offset(0.58f, 0.48f), Offset(0.585f, 0.42f), Offset(0.59f, 0.37f),
    Offset(0.63f, 0.62f), Offset(0.65f, 0.55f), Offset(0.665f, 0.50f), Offset(0.68f, 0.46f),
)
