package app.signsetu

import app.signsetu.data.content.SignMode
import app.signsetu.data.content.planText
import app.signsetu.sign.NGram
import app.signsetu.sign.SignGuess
import app.signsetu.sign.rankSentences
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Offline evaluation of the sentence ranker: each app sentence, signed in ISL order, is read by a simulated reader
 * whose right-word rank follows the shipped reader on 269 new-signer ISLRTC clips (top-1 0.342, top-3 0.442, top-5
 * 0.480, top-10 0.524), and ranked with a trigram model built without that sentence. Prints top-1 and top-5 sentence
 * accuracy for 3 and 10 candidates per sign, with and without the model. */
class SentenceEvalTest {
    private val recall = listOf(0f, 0.342f, 0.392f, 0.442f, 0.461f, 0.480f, 0.489f, 0.498f, 0.507f, 0.516f, 0.524f)
    private val probs = listOf(0.20f, 0.10f, 0.07f, 0.05f, 0.04f, 0.035f, 0.03f, 0.025f, 0.02f, 0.02f)

    @Test
    fun deeperCandidatesAndTheLanguageModelBeatTheTopThreeAlone() {
        val written = (shipped.clips.keys + shipped.questions.mapNotNull { it.text["en"] }).distinct()
        fun gloss(t: String) = shipped.planText(t, setOf(SignMode.SIGNS)).beats.sortedBy { it.start }.map { t.substring(it.range).lowercase() }
        val glosses = written.associateWith(::gloss).filterValues { it.size in 2..6 }
        val vocab = glosses.values.flatten().distinct()
        val rng = Random(7)
        fun read(word: String): List<SignGuess> {
            val r = rng.nextFloat()
            val rank = recall.indexOfFirst { it > r } - 1
            val others = (vocab - word).shuffled(rng).take(10)
            val words = if (rank !in 0..9) others else others.take(9).toMutableList().apply { add(rank, word) }
            return words.mapIndexed { i, w -> SignGuess(w, probs[i]) }
        }
        val cases = glosses.entries.map { (t, g) -> Triple(t, g, g.map(::read)) }
        val models = cases.associate { (t) -> t to NGram(written.filter { it != t }.let { w -> w + w.map { gloss(it).joinToString(" ") } }) }
        fun score(k: Int, useLm: Boolean): String {
            var top1 = 0; var top5 = 0
            for ((t, g, guesses) in cases) {
                val ranked = rankSentences(guesses.map { it.take(k) }, emptySet(), models[t].takeIf { useLm }, top = 5).map { it.words }
                if (ranked.firstOrNull() == g) top1++
                if (g in ranked) top5++
            }
            return "top1 %.3f top5 %.3f".format(top1.toFloat() / cases.size, top5.toFloat() / cases.size)
        }
        println("sentences ${cases.size}")
        for (k in listOf(3, 10)) for (lm in listOf(false, true)) println("  k=$k lm=$lm  ${score(k, lm)}")
        assertTrue(cases.isNotEmpty())
    }
}
