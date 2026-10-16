package app.signsetu.camera

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset

/**
 * The seam for the hand-detection model built by the partner team (07-decisions D-20).
 *
 * The app calls [detect] on one background thread with one upright RGBA frame at a time, newest frame only.
 * Frames never leave the device and are never stored: the bitmap is reused after the call returns, so an
 * implementation must not keep it. Results only draw an overlay on the mirror; any meaning a model suggests must
 * still be confirmed by the subject through show-back (FR-66).
 */
interface HandTracker : AutoCloseable {
    fun detect(frame: Bitmap, timestampMs: Long): List<TrackedHand>
    override fun close() {}
}

/**
 * One detected hand: 21 points in the MediaPipe hand topology (0 wrist, 4 thumb tip, 8 index tip, 12 middle tip,
 * 16 ring tip, 20 little finger tip), normalised to the frame with the origin top-left and no mirroring.
 */
@Immutable
data class TrackedHand(val points: List<Offset>, val score: Float)

/** Bones of the MediaPipe hand topology, for drawing. */
internal val HandBones = listOf(
    0 to 1, 1 to 2, 2 to 3, 3 to 4,
    0 to 5, 5 to 6, 6 to 7, 7 to 8,
    5 to 9, 9 to 10, 10 to 11, 11 to 12,
    9 to 13, 13 to 14, 14 to 15, 15 to 16,
    13 to 17, 0 to 17, 17 to 18, 18 to 19, 19 to 20,
)
