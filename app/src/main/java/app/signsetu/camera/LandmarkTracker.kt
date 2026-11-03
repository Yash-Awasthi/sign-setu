package app.signsetu.camera

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset

/** The seam for on-device landmark tracking. The app calls [track] on one background thread with one upright
 * RGBA frame at a time, newest frame only. */
interface LandmarkTracker : AutoCloseable {
    fun track(frame: Bitmap, timestampMs: Long): LandmarkFrame?
    /** Loads the model ahead of the first frame, off the main thread; it analyses nothing. */
    fun warm() {}
    /** The next frame shows a different part of the picture: nothing read from earlier frames may be reused for it. */
    fun viewMoved() {}
    override fun close() {}
}

/** Landmarks of one frame in MediaPipe Holistic topology, normalised to the frame (origin top-left, no
 * mirroring). Hands are the subject's own left and right: 21 points each. Face: 468 points. Pose: 33 points. */
@Immutable
data class LandmarkFrame(
    val timestampMs: Long,
    val leftHand: List<Offset>? = null,
    val rightHand: List<Offset>? = null,
    val face: List<Offset>? = null,
    val pose: List<Offset>? = null,
    val aspect: Float = 3f / 4f,
    /** Brows raised above this face's running baseline: ISL marks yes/no questions this way. */
    val browsUp: Boolean = false,
    val mouthOpen: Boolean = false,
    /** A nod, head shake or held thumb that ended on this frame: a proposed yes or no. */
    val head: Head? = null,
    /** A picture card id the subject points at, on the frame the hold completes. */
    val pointsAt: String? = null,
    val stopPalm: Boolean = false,
    val shrug: Boolean = false,
    /** A courtesy held on this frame (hands joined, a wave, a hand on the chest, a bow): a proposed word, never an answer. */
    val everyday: Everyday? = null,
    /** The camera picture behind this frame was smeared; the landmarks are kept, the picture is not. */
    val blurry: Boolean = false,
    /** Head turn and nod angles in degrees from the face model's transform; null from trackers without one. */
    val yaw: Float? = null,
    val pitch: Float? = null,
    /** Mouth blendshape scores, 0 to 1, in [MOUTH_SHAPES] order; null without the face model. */
    val mouth: List<Float>? = null,
) {
    val hands: List<List<Offset>> get() = listOfNotNull(leftHand, rightHand)
}

val HandBones = listOf(
    0 to 1, 1 to 2, 2 to 3, 3 to 4,
    0 to 5, 5 to 6, 6 to 7, 7 to 8,
    5 to 9, 9 to 10, 10 to 11, 11 to 12,
    9 to 13, 13 to 14, 14 to 15, 15 to 16,
    13 to 17, 0 to 17, 17 to 18, 18 to 19, 19 to 20,
)

/** Pose points of the arms (shoulders 11, 12; elbows 13, 14; wrists 15, 16), for drawing. */
val ArmBones = listOf(11 to 12, 11 to 13, 13 to 15, 12 to 14, 14 to 16)

/** Face mesh outlines that carry ISL facial grammar: brows, eyes, lips, and the face oval. */
object FaceOutlines {
    val oval = listOf(10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379, 378, 400, 377, 152, 148, 176, 149, 150, 136, 172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109, 10)
    val leftBrow = listOf(276, 283, 282, 295, 285, 300, 293, 334, 296, 336)
    val rightBrow = listOf(46, 53, 52, 65, 55, 70, 63, 105, 66, 107)
    val leftEye = listOf(263, 249, 390, 373, 374, 380, 381, 382, 362, 398, 384, 385, 386, 387, 388, 466, 263)
    val rightEye = listOf(33, 7, 163, 144, 145, 153, 154, 155, 133, 173, 157, 158, 159, 160, 161, 246, 33)
    val lips = listOf(61, 146, 91, 181, 84, 17, 314, 405, 321, 375, 291, 409, 270, 269, 267, 0, 37, 39, 40, 185, 61)
    val all = listOf(oval, leftBrow, rightBrow, leftEye, rightEye, lips)
}
