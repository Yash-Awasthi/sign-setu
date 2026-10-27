package app.signsetu.sign

import androidx.compose.runtime.Immutable
import app.signsetu.camera.LandmarkFrame

/** Reads one isolated sign from the landmark frames of one segment. Runs on device; frames are not kept.
 * Guesses are suggestions only: a meaning is recorded when the officer accepts it or the subject confirms it. */
interface SignReader : AutoCloseable {
    /** The vocabulary the reader knows, as words (lower case, English), in model output order. */
    val labels: List<String>
    /** Labels to the app's written words (assets/content/sign_words.json), where the two share a concept. */
    val words: Map<String, String> get() = emptyMap()
    /** Labels to more picture cards they answer than their word's own, e.g. "police" for a police station. */
    val cards: Map<String, List<String>> get() = emptyMap()
    /** Every label with its probability, highest first; empty when the frames cannot be read. */
    fun read(frames: List<LandmarkFrame>): List<SignGuess>
    /** The word reader's pooled features for comparing two signs, or null when it has none or cannot read the frames. */
    fun embed(frames: List<LandmarkFrame>): FloatArray? = null
    /** Split-conformal sets fitted on held-out signers (tools/ml/calibrate.py); null when the model has none. */
    val conformal: Conformal? get() = null
    override fun close() {}
}

/** RAPS prediction sets: [thresholds] by target coverage ("0.50"), with the set-size penalty [lambda] past [kReg]. */
@kotlinx.serialization.Serializable
data class Conformal(val thresholds: Map<String, Float>, @kotlinx.serialization.SerialName("k_reg") val kReg: Int, val lambda: Float) {
    /** How many of [guesses] (best first, probabilities) the set at [coverage] holds; at least one. */
    fun size(guesses: List<SignGuess>, coverage: String = "0.50"): Int {
        val q = thresholds[coverage] ?: return guesses.size
        var above = 0f
        var n = 0
        for ((i, g) in guesses.withIndex()) {
            if (i > 0 && above + lambda * maxOf(0, i + 1 - kReg) > q) break
            above += g.score
            n++
        }
        return n
    }
}

/** One candidate word with the model's probability, highest first. */
@Immutable
data class SignGuess(
    val word: String,
    val score: Float,
    val spelled: Boolean = false,
    /** A spelled reading checked against known names; null for word signs and unchecked spellings. */
    val name: ResolvedName? = null,
)
