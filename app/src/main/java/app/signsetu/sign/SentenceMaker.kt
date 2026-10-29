package app.signsetu.sign

/** One sentence the online assistant offered for a reading: a word per sign, and the sentence they make. */
data class SentenceGuess(val words: List<String>, val text: String)

/** Lower-case words; a possessive or contraction keeps only its stem ("father's" is father). */
internal fun tokens(text: String) =
    text.lowercase().split(Regex("[^\\p{L}\\p{N}'’]+")).map { it.substringBefore('\'').substringBefore('’') }.filter { it.isNotEmpty() }

/** The prompt that asks a language model for the likeliest sentences; the reply is parsed by [parseSentences]. */
fun sentencePrompt(question: String?, guesses: List<List<SignGuess>>): String = buildString {
    appendLine("Question asked: ${question ?: "(none)"}")
    guesses.forEachIndexed { i, g ->
        appendLine("Sign ${i + 1}: " + g.withIndex().joinToString("; ") { (n, x) ->
            "${n + 1}=${x.word.replace('_', ' ')}${if (x.spelled) " (fingerspelled)" else ""} (%.2f)".format(x.score)
        })
    }
}

const val SENTENCE_SYSTEM = "A deaf person answered a police officer's question in Indian Sign Language. A sign reader gave a few " +
    "guesses per sign with probabilities; signs come in ISL order (time first, question word last, no small words). " +
    "Pick one guess per sign so the signs make the most likely answer to this question, and write it as a short plain " +
    "English first-person sentence, e.g. signs 'father name R-A-M' to 'What is your father's name?' give 'My father's name " +
    "is Ram.' Use only the chosen signs' words plus small grammar words. Give the three best different readings, one per " +
    "line, exactly as: 2,1,3 :: sentence. The numbers are the chosen guess per sign, in order. No other text."

/** Words a sentence may add to what was signed: grammar only, never content (docs/15-gloss-to-text.md, checker). */
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

/** Lines like "2,1,3 :: My father hit me" into readings; any line that does not fit the guesses or the check is dropped. */
fun parseSentences(reply: String, guesses: List<List<SignGuess>>): List<SentenceGuess> = reply.lines().mapNotNull { line ->
    val (picks, text) = line.split("::", limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
    val idx = picks.trim().trimStart('-', '*', ' ').split(',').map { it.trim().toIntOrNull() ?: return@mapNotNull null }
    if (idx.size != guesses.size) return@mapNotNull null
    val words = idx.zip(guesses).map { (n, g) -> if (g.isEmpty()) "?" else g.getOrNull(n - 1)?.word ?: return@mapNotNull null }
    SentenceGuess(words, text.trim().takeIf { it.isNotEmpty() && faithful(it, words) } ?: return@mapNotNull null)
}
