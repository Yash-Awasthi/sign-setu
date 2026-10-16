package app.signsetu.sign

import androidx.compose.runtime.Immutable
import app.signsetu.camera.LandmarkFrame
import kotlin.math.hypot

/** Longest signed answer captured: enough for a short reply, short enough to keep in memory. */
const val SIGN_LIMIT_MS = 20_000L

/** One sign-sized chunk of a signed answer; [guesses] is null while the reader is still on it. Memory only (D-25). */
@Immutable
class SignSegment(val frames: List<LandmarkFrame>, val guesses: List<SignGuess>? = null) {
    val word: String? get() = guesses?.let { it.firstOrNull()?.word ?: "?" }
}

/**
 * Splits a landmark stream into signs at pauses: hands gone or nearly still for [pauseMs]. Returns the index range of
 * each sign. A sign still open at the end is returned only when [final]; closed ranges never change as frames are added.
 */
fun segmentSigns(
    frames: List<LandmarkFrame>,
    final: Boolean,
    pauseMs: Long = 400,
    stillSpeed: Float = 0.3f,
    minMs: Long = 200,
): List<IntRange> {
    val out = ArrayList<IntRange>()
    var start = -1
    var lastActive = -1
    var activeFrames = 0
    fun close() {
        if (frames[lastActive].timestampMs - frames[start].timestampMs >= minMs && activeFrames >= 3) out.add(start..lastActive)
        start = -1
        activeFrames = 0
    }
    for (i in frames.indices) {
        val f = frames[i]
        if (start >= 0 && f.timestampMs - frames[lastActive].timestampMs >= pauseMs) close()
        if (moving(frames.getOrNull(i - 1), f, stillSpeed)) {
            if (start < 0) start = maxOf(0, i - 1)
            lastActive = i
            activeFrames++
        }
    }
    if (final && start >= 0) close()
    return out
}

/** Any hand moving faster than [stillSpeed] frame-widths per second, measured over all its points; a hand appearing counts. */
private fun moving(prev: LandmarkFrame?, f: LandmarkFrame, stillSpeed: Float): Boolean {
    val seconds = if (prev == null || f.timestampMs <= prev.timestampMs) 1f / 30 else (f.timestampMs - prev.timestampMs) / 1000f
    return listOf(prev?.leftHand to f.leftHand, prev?.rightHand to f.rightHand).any { (a, b) ->
        when {
            b == null -> false
            a == null || a.size != b.size -> true
            else -> a.indices.sumOf { hypot(b[it].x - a[it].x, b[it].y - a[it].y).toDouble() } / b.size / seconds > stillSpeed
        }
    }
}

/**
 * Collects one signed answer's frames in memory, capped at [limitMs], and hands out each sign once its pause closes it.
 * Called from the camera's analysis thread and the main thread, so every entry point is synchronised.
 */
class SignCapture(private val limitMs: Long = SIGN_LIMIT_MS) {
    private val frames = ArrayList<LandmarkFrame>()
    private var handed = 0
    var full = false
        private set

    /** Adds a frame and returns the signs it closed, in order. */
    @Synchronized
    fun add(frame: LandmarkFrame): List<List<LandmarkFrame>> {
        if (full) return emptyList()
        frames.add(frame)
        full = frame.timestampMs - frames[0].timestampMs >= limitMs
        return take(final = full)
    }

    /** Closes the answer and returns the signs not yet handed out. */
    @Synchronized
    fun finish(): List<List<LandmarkFrame>> = take(final = true).also { full = true }

    private fun take(final: Boolean): List<List<LandmarkFrame>> {
        val ranges = segmentSigns(frames, final)
        return ranges.drop(handed).map { frames.subList(it.first, it.last + 1).toList() }.also { handed = ranges.size }
    }
}
