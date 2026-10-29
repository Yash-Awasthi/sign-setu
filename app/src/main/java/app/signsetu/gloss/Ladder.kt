package app.signsetu.gloss

import kotlin.math.exp
import kotlin.math.ln

/** What the app may say about one signed answer, from most to least (docs/15-gloss-to-text.md). */
sealed interface Rung {
    /** The sentence in English and Hindi; [confirm] when the intent is risky (health, an attack) and must be confirmed. */
    data class Sentence(val en: String, val hi: String, val match: IntentMatch, val confidence: Float, val confirm: Boolean) : Rung

    /** The sentence, with the tokens whose reading is in doubt to be checked or swapped. */
    data class FixWords(val en: String, val hi: String, val match: IntentMatch, val confidence: Float, val doubtful: Set<Int>, val confirm: Boolean) : Rung

    /** No intent is clear: each sign with its candidates, and the best intents as suggestions only. */
    data class WordStrip(val tokens: List<GlossToken>, val suggestions: List<IntentMatch>) : Rung

    /** Too little was read: ask to sign again. */
    data object SignAgain : Rung
}

/** Words a signer may add that no sentence needs: their tokens can be left out without failing the check. */
private val DROPPABLE = setOf("i", "you", "hello", "thank you", "good morning", "good afternoon", "good evening", "he", "she", "we", "they")

/** Deterministic faithfulness check of an intent match against its lattice: every fill must be one of its token's
 * candidates (a name exactly as resolved), and every token the reader was sure of must be used or be droppable.
 * Negation and questions come only from pattern items, so they cannot appear without their evidence. */
fun faithful(m: IntentMatch, lattice: GlossLattice): Boolean {
    val byId = lattice.tokens.associateBy { it.id }
    for (f in m.fills) {
        val t = byId[f.token] ?: return false
        val ok = when (f.slot) {
            "NAME" -> t.type == TokenType.SPELL && t.top == f.label
            "NUM" -> t.type == TokenType.NUM && t.top?.filter(Char::isDigit) == f.label
            else -> t.candidates.any { it.label == f.label }
        }
        if (!ok) return false
    }
    return lattice.tokens.all { t -> t.id in m.used || !sure(t) || t.top in DROPPABLE }
}

private fun sure(t: GlossToken) = t.type != TokenType.UNKNOWN && t.rejectScore < DOUBT && (t.candidates.firstOrNull()?.p ?: 0f) >= SURE_P

/** The rung for [lattice]: the best intent's sentence if it passes the check and is clearly ahead, with its words
 * marked when any of them is in doubt; the word strip when no intent is clear; sign again when most signs went unread.
 * Confidence is the geometric mean of the used tokens' probabilities times the intent's posterior. */
fun ladder(lattice: GlossLattice, matches: List<IntentMatch>, book: Phrasebook, gender: Gender = Gender.UNKNOWN, high: Float = HIGH, doubt: Float = RUNG1_DOUBT): Rung {
    val toks = lattice.tokens
    val best = matches.firstOrNull { faithful(it, lattice) }?.takeIf { it.posterior >= CLEAR }
    // An unsure sign still carries its candidates, so the question's intents can rescue it; only without that does a
    // mostly unread answer ask to sign again.
    if (toks.isEmpty() || best == null && toks.count { it.type == TokenType.UNKNOWN } * 2 > toks.size) return Rung.SignAgain
    if (best == null) return Rung.WordStrip(toks, matches.filter { faithful(it, lattice) }.take(3))
    val used = best.fills.mapNotNull { f -> toks.firstOrNull { it.id == f.token } }
    val gm = if (best.fills.isEmpty()) 1f else exp(best.fills.map { ln(it.p.coerceAtLeast(1e-4f)) }.average().toFloat())
    val confidence = gm * best.posterior
    // A sign the sentence does not use never vanishes silently: unless it is a courtesy, it is shown to be checked.
    val unplaced = toks.filter { it.id !in best.used && it.top !in DROPPABLE }.map { it.id }
    val doubtful = used.filter { it.rejectScore > doubt || !it.verified || (it.candidates.firstOrNull()?.p ?: 0f) < SURE_P }.map { it.id }.toSet() + unplaced
    val en = book.english(best)
    val hi = book.hindi(best, gender)
    return if (confidence >= high && doubtful.isEmpty()) Rung.Sentence(en, hi, best, confidence, best.intent.risk)
    else Rung.FixWords(en, hi, best, confidence, doubtful, best.intent.risk)
}

// Calibration knobs: tune on held-out lattices so that rung 1's wrong-sentence rate meets its target (docs/15-gloss-to-text.md).
/** A token's top reading at or above this, with a reject score under [DOUBT], counts as sure. */
private const val SURE_P = 0.5f
private const val DOUBT = 0.3f
/** The best intent's posterior must reach this to be shown as a sentence at all. */
private const val CLEAR = 0.5f
/** Rung 1 takes only signs whose conformal set is one word (reject score 0): on simulated new-signer lattices that
 * cut rung 1's wrong rate to under 1% (tools/ml/EVAL.md). */
const val RUNG1_DOUBT = 0f
/** Confidence a full sentence needs (rung 1); SentenceEvalTest tunes it so rung 1's wrong rate stays under 2%. */
const val HIGH = 0.6f
