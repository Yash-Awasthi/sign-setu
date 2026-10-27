package app.signsetu.sign

import app.signsetu.camera.LandmarkFrame
import kotlin.math.sqrt

private const val T = 32
private const val F = 98

/** Signs taught in this session: when the officer reads a sign as a word the reader did not give, its shape is
 * kept and offered first when the subject signs it again. Memory only, gone with the session. [embed] is the word
 * reader's pooled features; without them, signs are compared frame by frame. */
class HomeSigns(private val embed: (List<LandmarkFrame>) -> FloatArray? = { null }) {
    private class Taught(val word: String, val shape: FloatArray, val features: FloatArray?)

    private val taught = mutableListOf<Taught>()

    @Synchronized
    fun teach(frames: List<LandmarkFrame>, word: String) {
        signFeatures(frames, T)?.let { taught += Taught(word.trim(), it, embed(frames)) }
    }

    @Synchronized
    fun match(frames: List<LandmarkFrame>): SignGuess? {
        if (taught.isEmpty()) return null
        val x = signFeatures(frames, T) ?: return null
        val e = embed(frames)
        if (e != null && taught.all { it.features != null }) {
            val (word, c) = taught.map { it.word to cosine(e, it.features!!) }.maxBy { it.second }
            return if (c >= MIN_COSINE) SignGuess(word, (c - MIN_COSINE) / (1f - MIN_COSINE)) else null
        }
        val (word, d) = taught.map { it.word to dtw(x, it.shape) }.minBy { it.second }
        return if (d < LIMIT) SignGuess(word, 1f - d / LIMIT) else null
    }

    private companion object {
        // Mean per-frame distance in shoulder widths. On INCLUDE, the same word by other signers has median 3.1 and other
        // words have 10th percentile 3.0, so 3 keeps most repeats by one signer and few strangers; tune on devices.
        const val LIMIT = 3f
        // Words the reader never learned, one clip each from another signer (tools/ml/oneshot.py): 0.65 keeps 74% of
        // repeats and lets 0.7% of other words through. One signer repeating scores higher; tune on devices.
        const val MIN_COSINE = 0.65f
    }
}

private fun cosine(a: FloatArray, b: FloatArray): Float {
    var ab = 0f; var aa = 0f; var bb = 0f
    for (i in a.indices) { ab += a[i] * b[i]; aa += a[i] * a[i]; bb += b[i] * b[i] }
    return if (aa > 0f && bb > 0f) ab / sqrt(aa * bb) else 0f
}

/** Banded dynamic time warping over [T] frames of [F] features, mean frame distance along the best path. */
internal fun dtw(a: FloatArray, b: FloatArray, band: Int = 4): Float {
    val inf = Float.MAX_VALUE / 4
    val cost = Array(T + 1) { FloatArray(T + 1) { inf } }
    cost[0][0] = 0f
    for (i in 1..T) for (j in maxOf(1, i - band)..minOf(T, i + band)) {
        var s = 0f
        for (k in 0 until F) { val d = a[(i - 1) * F + k] - b[(j - 1) * F + k]; s += d * d }
        cost[i][j] = sqrt(s) + minOf(cost[i - 1][j], cost[i][j - 1], cost[i - 1][j - 1])
    }
    return cost[T][T] / T
}
