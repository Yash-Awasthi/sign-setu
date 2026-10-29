package app.signsetu.sign

import androidx.compose.ui.geometry.Offset
import app.signsetu.camera.LandmarkFrame
import kotlin.math.abs
import kotlin.math.hypot

/** A sideways point in a sign, ISL's "he", "she" or "there" (docs/15-gloss-to-text.md, POINT tokens), or null. The
 * landmarks carry no depth, so a finger aimed at the camera or back at the chest looks like a fist; "i" and "you" are
 * left to the word reader, which knows those signs. Needs the same point on two thirds of the sign's frames. */
fun pointing(frames: List<LandmarkFrame>): List<SignGuess>? {
    if (frames.isEmpty()) return null
    val pointed = frames.count { f -> listOfNotNull(f.rightHand, f.leftHand).any { pointsSideways(it, f.aspect) } }
    if (pointed * 3 < frames.size * 2) return null
    return listOf(SignGuess("he", 0.4f), SignGuess("she", 0.3f), SignGuess("there", 0.2f))
}

/** The index straight and running mostly across the picture, the other three fingers folded. */
internal fun pointsSideways(hand: List<Offset>, aspect: Float): Boolean {
    if (hand.size != 21) return false
    fun d(a: Int, b: Int) = hypot((hand[a].x - hand[b].x) * aspect, hand[a].y - hand[b].y)
    val size = d(0, 9).takeIf { it > 1e-4f } ?: return false
    val indexOut = d(5, 8) > LONG * size && d(0, 8) > d(0, 12) * 1.3f
    val folded = listOf(12, 16, 20).all { d(0, it) < FOLDED * size }
    val vx = (hand[8].x - hand[5].x) * aspect
    val vy = hand[8].y - hand[5].y
    return indexOut && folded && abs(vx) > 1.5f * abs(vy)
}

// Calibration knobs in hand sizes (wrist to middle knuckle); tune on recorded pointing.
private const val LONG = 0.6f
private const val FOLDED = 1.25f
