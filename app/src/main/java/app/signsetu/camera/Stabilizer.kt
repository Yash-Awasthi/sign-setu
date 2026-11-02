package app.signsetu.camera

import androidx.compose.ui.geometry.Offset

/** Cleans the tracker's output before anything reads it: a hand or pose lost for a frame or two is held, so a
 * sign is not split by a dropout. Points pass through as tracked: any smoothing here made the skeleton trail the hands. */
class Stabilizer(private val holdMs: Long = HOLD_MS) {
    private class Track(val points: List<Offset>, val seenAt: Long)

    // Left hand, right hand, pose.
    private val tracks = arrayOfNulls<Track>(3)
    private var lastMs = Long.MIN_VALUE

    fun mark(frame: LandmarkFrame): LandmarkFrame {
        // A new capture starts clean, never from the last person's hands.
        if (frame.timestampMs <= lastMs || frame.timestampMs - lastMs > RESET_MS) tracks.fill(null)
        lastMs = frame.timestampMs
        return frame.copy(
            leftHand = step(frame.leftHand, frame.timestampMs, 0),
            rightHand = step(frame.rightHand, frame.timestampMs, 1),
            pose = step(frame.pose, frame.timestampMs, 2),
        )
    }

    private fun step(now: List<Offset>?, ms: Long, slot: Int): List<Offset>? {
        val prev = tracks[slot]
        if (now == null) {
            if (prev != null && ms - prev.seenAt <= holdMs) return prev.points
            tracks[slot] = null
            return null
        }
        tracks[slot] = Track(now, ms)
        return now
    }

    companion object {
        const val HOLD_MS = 100L
        const val RESET_MS = 1000L
    }
}
