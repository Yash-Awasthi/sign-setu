package app.signsetu.gloss

import app.signsetu.camera.Head
import app.signsetu.camera.LandmarkFrame
import app.signsetu.sign.CANDIDATES
import app.signsetu.sign.SignSegment
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.atan2

/** One reading of a signed unit and its calibrated probability. */
@Serializable
data class Candidate(val label: String, val p: Float)

enum class TokenType { SIGN, SPELL, NUM, POINT, UNKNOWN }

/** Face and head evidence over a token's span, each 0 to 1: the only evidence for questions and negation. */
@Serializable
data class NonManual(val browsRaised: Float = 0f, val headShake: Float = 0f, val headNod: Float = 0f, val headTilt: Float = 0f)

/** One signed unit, in time order. [candidates] is the reader's conformal set (best first, at most [CANDIDATES]);
 * [rejectScore] is 0 when the reader is sure and 1 when it cannot narrow the sign down. A SPELL token carries the
 * resolved name first, then the alternatives; [verified] is false when the name was not matched with a margin. */
@Serializable
data class GlossToken(
    val id: Int,
    val type: TokenType,
    val startMs: Long,
    val endMs: Long,
    val candidates: List<Candidate>,
    val nonManual: NonManual = NonManual(),
    val rejectScore: Float = 0f,
    val verified: Boolean = true,
) {
    val top: String? get() = candidates.firstOrNull()?.label
}

/** Everything read from one signed answer; every later stage reads this and nothing else (docs/10-gloss-to-text.md). */
@Serializable
data class GlossLattice(val tokens: List<GlossToken>, val signerMeta: String? = null, val appVersion: String = "") {
    fun toJson(): String = JSON.encodeToString(serializer(), this)

    /** The best reading of each token, "?" where a token has none: the old plain gloss, for views that want words. */
    val transcript: List<String> get() = tokens.map { it.top ?: "?" }

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        fun fromJson(text: String): GlossLattice = JSON.decodeFromString(serializer(), text)

        /** The reader's segments as lattice tokens. */
        fun of(segments: List<SignSegment>, appVersion: String = ""): GlossLattice = GlossLattice(segments.mapIndexed(::token), appVersion = appVersion)

        internal fun token(id: Int, s: SignSegment): GlossToken {
            val pool = s.candidates.ifEmpty { s.guesses.orEmpty() }
            val first = pool.firstOrNull()
            val type = when {
                first == null || s.unsure -> TokenType.UNKNOWN
                first.spelled && first.word.all { it.isDigit() || it == ' ' } -> TokenType.NUM
                first.spelled -> TokenType.SPELL
                else -> TokenType.SIGN
            }
            val set = pool.take(CANDIDATES).map { Candidate(it.word, it.score) }
            return GlossToken(
                id, type,
                s.frames.firstOrNull()?.timestampMs ?: 0L, s.frames.lastOrNull()?.timestampMs ?: 0L,
                set, nonManual(s.frames).let { nm ->
                    // The window's head movement belongs to the whole answer, so every token carries it.
                    nm.copy(headShake = maxOf(nm.headShake, if (Head.Shake in s.windowHead) 1f else 0f), headNod = maxOf(nm.headNod, if (Head.Nod in s.windowHead) 1f else 0f))
                },
                rejectScore = if (s.unsure || first == null) 1f else ((set.size - 1).toFloat() / (CANDIDATES - 1)).coerceIn(0f, 1f),
                verified = first?.name?.verified ?: true,
            )
        }

        /** Brows: share of face frames with raised brows. Shake and nod: whether the head gesture reader saw one.
         * Tilt: mean roll of the line between the outer eye corners (face mesh 33 and 263), 30 degrees as full. */
        fun nonManual(frames: List<LandmarkFrame>): NonManual {
            val faces = frames.filter { it.face != null }
            val brows = if (faces.isEmpty()) 0f else faces.count { it.browsUp }.toFloat() / faces.size
            val rolls = faces.mapNotNull { f ->
                val p = f.face!!.takeIf { it.size > 263 } ?: return@mapNotNull null
                Math.toDegrees(atan2(((p[263].y - p[33].y) / f.aspect).toDouble(), (p[263].x - p[33].x).toDouble())).toFloat()
            }
            return NonManual(
                browsRaised = brows,
                headShake = if (frames.any { it.head == Head.Shake }) 1f else 0f,
                headNod = if (frames.any { it.head == Head.Nod }) 1f else 0f,
                headTilt = if (rolls.isEmpty()) 0f else (abs(rolls.average().toFloat()) / 30f).coerceAtMost(1f),
            )
        }
    }
}
