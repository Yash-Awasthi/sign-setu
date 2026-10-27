package app.signsetu.sign

import kotlin.math.ln

/** One reading of a whole signed answer: a word per sign, and the sentence they make. */
data class SentenceGuess(val words: List<String>, val text: String, val score: Float = 0f)

/** A word trigram model with stupid backoff over the app's own sentences: what its people are likely to say. */
class NGram(sentences: Iterable<String>) {
    private val counts = HashMap<List<String>, Int>()
    private val total: Int
    private val vocab: Int

    init {
        var n = 0
        for (s in sentences) {
            val t = listOf(START, START) + tokens(s)
            for (i in 2 until t.size) {
                n++
                counts.merge(listOf(t[i]), 1, Int::plus)
                counts.merge(listOf(t[i - 1], t[i]), 1, Int::plus)
                counts.merge(listOf(t[i - 2], t[i - 1], t[i]), 1, Int::plus)
                // Each history counted as it is followed, under "#", so start symbols have one too.
                counts.merge(listOf("#", t[i - 1]), 1, Int::plus)
                counts.merge(listOf("#", t[i - 2], t[i - 1]), 1, Int::plus)
            }
        }
        total = n
        vocab = counts.keys.count { it.size == 1 }
    }

    /** Log probability of [w] after [a], [b]. */
    fun logp(a: String, b: String, w: String): Float {
        val tri = counts[listOf(a, b, w)] ?: 0
        if (tri > 0) return ln(tri.toFloat() / (counts[listOf("#", a, b)] ?: 1))
        val bi = counts[listOf(b, w)] ?: 0
        if (bi > 0) return ln(BACKOFF * bi / (counts[listOf("#", b)] ?: 1))
        return ln(BACKOFF * BACKOFF * ((counts[listOf(w)] ?: 0) + 1f) / (total + vocab + 1))
    }

    companion object { const val START = "<s>" }
}

/** Levenshtein distance, two rows. */
fun editDistance(a: String, b: String): Int {
    var prev = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val cur = IntArray(b.length + 1).also { it[0] = i }
        for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        prev = cur
    }
    return prev[b.length]
}

/** Known words within one edit (two for long words) of a fingerspelled reading, nearest first: spelling slips. */
fun spellingFixes(spelled: String, known: Collection<String>, max: Int = 3): List<String> {
    val limit = if (spelled.length >= 6) 2 else 1
    return known.asSequence().filter { it != spelled && kotlin.math.abs(it.length - spelled.length) <= limit }
        .map { it to editDistance(spelled, it) }.filter { it.second <= limit }
        .sortedBy { it.second }.take(max).map { it.first }.toList()
}

/** Ranks whole readings out of each sign's guesses, beam search over every combination: the reader's own
 * probabilities, the trigram model, a lift for words the question expects, and spelling fixes for fingerspelling.
 * [render] turns the chosen words into the sentence shown. */
fun rankSentences(
    guesses: List<List<SignGuess>>,
    expected: Set<String>,
    lm: NGram?,
    known: Collection<String> = emptyList(),
    render: (List<String>) -> String = { w -> w.joinToString(" ") { it.replace('_', ' ') } },
    top: Int = 3,
    beam: Int = 24,
): List<SentenceGuess> {
    class Path(val words: List<String>, val score: Float, val a: String, val b: String)
    var paths = listOf(Path(emptyList(), 0f, NGram.START, NGram.START))
    for (raw in guesses) {
        val options = raw + raw.filter { it.spelled }.flatMap { g -> spellingFixes(g.word, known).map { SignGuess(it, g.score * FIX_DISCOUNT) } }
        paths = if (options.isEmpty()) paths.map { Path(it.words + "?", it.score, it.a, it.b) }
        else paths.flatMap { p ->
            options.map { g ->
                var (a, b) = p.a to p.b
                var lmScore = 0f
                for (t in tokens(g.word)) { lmScore += lm?.logp(a, b, t) ?: 0f; a = b; b = t }
                val lift = if (tokens(g.word).any { it in expected }) EXPECTED_LIFT else 0f
                Path(p.words + g.word, p.score + ln(g.score.coerceAtLeast(1e-4f)) + LM_WEIGHT * lmScore + lift, a, b)
            }
        }.sortedByDescending { it.score }.take(beam)
    }
    // Lazy, so a costly [render] runs only until [top] different sentences are found.
    return paths.asSequence().filter { it.words.isNotEmpty() }
        .map { SentenceGuess(it.words, render(it.words), it.score) }
        .distinctBy { it.text.lowercase() }.take(top).toList()
}

/** Words of [text] the answer may well use: the question's own words and its picture cards. */
fun expectedWords(text: String?, cards: Collection<String>): Set<String> =
    (tokens(text.orEmpty()) + cards.flatMap(::tokens)).filter { it.length > 2 && it !in STOP }.toSet()

/** Lower-case words; a possessive or contraction keeps only its stem ("father's" is father). */
internal fun tokens(text: String) =
    text.lowercase().split(Regex("[^\\p{L}\\p{N}'’]+")).map { it.substringBefore('\'').substringBefore('’') }.filter { it.isNotEmpty() }

private val STOP = setOf("the", "you", "your", "are", "was", "were", "did", "does", "what", "who", "how", "where", "when", "which", "why", "this", "that", "with", "have")

// Calibration knobs, in log-probability units: e^1.2 is about 3x, so a word the question expects beats a likelier stray.
private const val EXPECTED_LIFT = 1.2f
private const val LM_WEIGHT = 0.4f
/** A spelling fix is trusted a little less than what was actually spelled. */
private const val FIX_DISCOUNT = 0.8f
private const val BACKOFF = 0.4f

/** The prompt that asks a language model for the three likeliest sentences; the reply is parsed by [parseSentences]. */
fun sentencePrompt(question: String?, guesses: List<List<SignGuess>>): String = buildString {
    appendLine("Question asked: ${question ?: "(none)"}")
    guesses.forEachIndexed { i, g ->
        appendLine("Sign ${i + 1}: " + g.withIndex().joinToString("; ") { (n, x) ->
            "${n + 1}=${x.word.replace('_', ' ')}${if (x.spelled) " (fingerspelled, may hold a slip)" else ""} (%.2f)".format(x.score)
        })
    }
}

const val SENTENCE_SYSTEM = "A deaf person answered a police officer's question in Indian Sign Language. A sign reader gave a few " +
    "guesses per sign with probabilities; signs come in ISL order (time first, question word last, no small words). " +
    "Pick one guess per sign so the signs make the most likely answer to this question, and write it as a short plain " +
    "English first-person sentence that answers the question, e.g. signs 'father name R-A-M' to 'What is your father's name?' " +
    "give 'My father's name is Ram.' A fingerspelled word may be corrected by one letter. Give the three best different " +
    "readings, one per line, exactly as: 2,1,3 :: sentence. The numbers are the chosen guess per sign, in order. " +
    "Never add facts the signs do not carry. No other text."

/** Words a sentence may add to what was signed: grammar only, never content (docs/10-gloss-to-text.md, checker). */
private val FUNCTION = setOf(
    "i", "me", "my", "mine", "you", "your", "he", "him", "his", "she", "her", "we", "us", "our", "they", "them", "their", "it", "its",
    "a", "an", "the", "is", "am", "are", "was", "were", "be", "been", "do", "does", "did", "have", "has", "had", "to", "of",
    "in", "on", "at", "by", "with", "for", "from", "and", "or", "but", "this", "that", "there", "here", "some", "any",
    "will", "would", "can", "could", "please", "yes",
)

/** True when every content word of [sentence] traces to one of [signed] (by stem), and no negation or number appears
 * that the signs do not carry. A sentence that fails is dropped, never shown. */
fun faithful(sentence: String, signed: List<String>): Boolean {
    val have = signed.flatMap(::tokens).toSet()
    // ponytail: suffix stripping only; an irregular form ("took" for take) fails and the sentence is dropped, the safe side.
    fun stem(w: String) = w.replace(Regex("(ies|ied)$"), "y").replace(Regex("(ing|ed|es|s)$"), "")
    val stems = have.map(::stem).toSet()
    return tokens(sentence).all { w ->
        when {
            w in have || stem(w) in stems -> true
            w in setOf("not", "no", "nobody", "nothing", "never") -> have.any { it in setOf("not", "no", "nothing") }
            w.any { it.isDigit() } -> false
            else -> w in FUNCTION
        }
    }
}

/** Lines like "2,1,3 :: My father hit me" into readings; any line that does not fit the guesses is dropped. */
fun parseSentences(reply: String, guesses: List<List<SignGuess>>): List<SentenceGuess> = reply.lines().mapNotNull { line ->
    val (picks, text) = line.split("::", limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
    val idx = picks.trim().trimStart('-', '*', ' ').split(',').map { it.trim().toIntOrNull() ?: return@mapNotNull null }
    if (idx.size != guesses.size) return@mapNotNull null
    val words = idx.zip(guesses).map { (n, g) -> if (g.isEmpty()) "?" else g.getOrNull(n - 1)?.word ?: return@mapNotNull null }
    SentenceGuess(words, text.trim().takeIf { it.isNotEmpty() && faithful(it, words) } ?: return@mapNotNull null)
}
