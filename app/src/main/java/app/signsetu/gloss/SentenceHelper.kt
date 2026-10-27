package app.signsetu.gloss

/** The optional on-device sentence helper (docs/10-gloss-to-text.md, item 6): a small imported language model that
 * only reorders sentences the phrasebook already built and checked. It sees a numbered list and answers with numbers,
 * so it cannot add a word; a reply that is not a clean list of those numbers is ignored. */
const val RERANK_SYSTEM = "A deaf person answered a police officer in Indian Sign Language. You get the question, what a sign " +
    "reader saw (a few guesses per sign), and numbered candidate answers. Reply only with the candidate numbers from most " +
    "to least likely, comma separated, for example: 2,1,3. No other text."

fun rerankPrompt(question: String?, signs: List<List<String>>, candidates: List<String>): String = buildString {
    appendLine("Question: ${question ?: "(none)"}")
    signs.forEachIndexed { i, g -> appendLine("Sign ${i + 1}: ${g.joinToString(" | ") { it.replace('_', ' ') }}") }
    appendLine("Candidates:")
    candidates.forEachIndexed { i, c -> appendLine("${i + 1}. $c") }
}

/** The order the model gave, as indexes into the candidates; null unless it is a usable list of their numbers. Numbers
 * it left out keep their place after the ones it ranked. */
fun parseOrder(reply: String, count: Int): List<Int>? {
    val picked = Regex("\\d+").findAll(reply.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty())
        .map { it.value.toInt() - 1 }.filter { it in 0 until count }.distinct().toList()
    if (picked.isEmpty()) return null
    return picked + (0 until count).filter { it !in picked }
}
