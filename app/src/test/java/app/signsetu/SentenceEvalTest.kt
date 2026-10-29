package app.signsetu

import app.signsetu.gloss.Filled
import app.signsetu.gloss.GlossLattice
import app.signsetu.gloss.GlossToken
import app.signsetu.gloss.IntentMatch
import app.signsetu.gloss.NonManual
import app.signsetu.gloss.Phrasebook
import app.signsetu.gloss.Rung
import app.signsetu.gloss.TokenType
import app.signsetu.gloss.ladder
import app.signsetu.sign.CANDIDATES
import app.signsetu.sign.Conformal
import app.signsetu.sign.SignGuess
import app.signsetu.sign.SignSegment
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Simulated lattices from real held-out readings (docs/15-gloss-to-text.md, design 3): every sentence the
 * phrasebook can say is "signed" by drawing, for each sign, a real reading of that word by a signer the reader never
 * saw (tools/ml/held_out_readings.py), with missed and extra segments at assumed rates. Development numbers only:
 * the release gates need recorded sentences. Prints the harness metrics per source, with and without the question. */
class SentenceEvalTest {
    private val book = Phrasebook(File(ASSETS, "intents.json").readText())
    private val readings = Json.parseToJsonElement(File("src/test/resources/held_out_readings.json").readText())
        .jsonObject.getValue("readings").jsonObject.mapValues { (_, byWord) ->
            byWord.jsonObject.mapValues { (_, rows) -> rows.jsonArray.map { r -> r.jsonArray.map { c -> SignGuess(c.jsonArray[0].jsonPrimitive.content, c.jsonArray[1].jsonPrimitive.float) } } }
        }
    private val conformal = Json.parseToJsonElement(File(ASSETS, "models/sign_reader.json").readText()).jsonObject.getValue("conformal").jsonObject.let { c ->
        Conformal(c.getValue("thresholds").jsonObject.mapValues { it.value.jsonPrimitive.float }, c.getValue("k_reg").jsonPrimitive.content.toInt(), c.getValue("lambda").jsonPrimitive.float)
    }
    private val slots = Json.parseToJsonElement(File(ASSETS, "intents.json").readText()).jsonObject.getValue("slots").jsonObject.mapValues { it.value.jsonObject.keys }

    private data class Case(val truth: IntentMatch, val lattice: GlossLattice, val question: String)

    private fun cases(source: Map<String, List<List<SignGuess>>>, rnd: Random): List<Case> = book.intents.flatMap { intent ->
        List(40) {
            val tokens = ArrayList<GlossToken>()
            val fills = ArrayList<Filled>()
            var shake = false
            var nod = false
            for (item in intent.pattern) {
                if (item.endsWith("?") && rnd.nextBoolean()) continue
                val name = item.removeSuffix("?")
                val id = tokens.size
                when {
                    name == "@shake" -> shake = true
                    name == "@nod" -> nod = true
                    name == "\$NAME" -> {
                        val n = listOf("ram", "sunita", "raipur", "durg").random(rnd)
                        // The speller's held-out character error rate is about 12%: a third of names come back unverified.
                        tokens += GlossToken(id, TokenType.SPELL, 0, 0, listOf(app.signsetu.gloss.Candidate(n, 0.8f)), verified = rnd.nextFloat() > 0.33f)
                        fills += Filled("NAME", n, id, 0.8f)
                    }
                    name == "\$NUM" -> { tokens += GlossToken(id, TokenType.NUM, 0, 0, listOf(app.signsetu.gloss.Candidate("12", 0.8f))); fills += Filled("NUM", "12", id, 0.8f) }
                    else -> {
                        val slot = name.removePrefix("$").takeIf { name.startsWith("$") }
                        val options = (if (slot != null) slots.getValue(slot) else name.split('|')).filter { it in source }
                        if (options.isEmpty()) return@List null
                        val word = options.random(rnd)
                        tokens += read(id, source.getValue(word).random(rnd))
                        fills += Filled(slot ?: name, word, id, 1f)
                    }
                }
            }
            if (tokens.isEmpty()) tokens += read(0, source.values.random(rnd).random(rnd))
            // Missed and extra segments at assumed rates until recorded sentences measure them.
            val kept = tokens.filter { rnd.nextFloat() > 0.05f }.toMutableList()
            if (rnd.nextFloat() < 0.1f) kept.add(rnd.nextInt(kept.size + 1), read(0, source.values.random(rnd).random(rnd)))
            if (kept.isEmpty()) return@List null
            val withHead = kept.mapIndexed { i, t ->
                t.copy(id = i, nonManual = NonManual(headShake = if (shake && i == 0) 1f else 0f, headNod = if (nod && i == 0) 1f else 0f))
            }
            val truth = IntentMatch(intent, 0f, fills, emptySet())
            Case(truth, GlossLattice(withHead), intent.questions.firstOrNull().orEmpty())
        }.filterNotNull()
    }

    private fun read(id: Int, row: List<SignGuess>): GlossToken {
        // Only the top 10 were exported, so a set that reaches 10 stands for ReadPolicy's "larger than 10".
        val size = conformal.size(row)
        return GlossLattice.token(id, SignSegment(emptyList(), row.take(3), candidates = row.take(size), unsure = size >= CANDIDATES))
    }

    @Test
    fun printTheLadderMetricsOnSimulatedLattices() {
        for ((name, source) in readings) for (withQuestion in listOf(true, false)) {
            val cs = cases(source, Random(11))
            var top1 = 0; var top2 = 0; val rungs = IntArray(4); var wrong1 = 0; var right12 = 0
            for (c in cs) {
                val q = c.question.takeIf { withQuestion && it.isNotEmpty() }
                val ms = book.match(c.lattice, q)
                val truth = book.english(c.truth)
                if (ms.firstOrNull()?.let(book::english) == truth) top1++
                if (ms.take(2).any { book.english(it) == truth }) top2++
                when (val r = ladder(c.lattice, ms, book)) {
                    is Rung.Sentence -> { rungs[0]++; if (r.en != truth) { wrong1++; if (name == "islrtc" && withQuestion) println("    WRONG ${c.question}: said '${r.en}', signed '$truth'") } else right12++ }
                    is Rung.FixWords -> { rungs[1]++; if (r.en == truth) right12++ }
                    is Rung.WordStrip -> rungs[2]++
                    Rung.SignAgain -> rungs[3]++
                }
            }
            val n = cs.size.toFloat()
            println("%-7s question=%-5s n=%d  sentence top1 %.3f top2 %.3f | rungs %.2f %.2f %.2f %.2f | rung-1 wrong %.3f of rung 1 | right at rung 1-2 %.3f"
                .format(name, withQuestion, cs.size, top1 / n, top2 / n, rungs[0] / n, rungs[1] / n, rungs[2] / n, rungs[3] / n,
                    if (rungs[0] == 0) 0f else wrong1.toFloat() / rungs[0], right12 / n))
            assertTrue(cs.isNotEmpty())
        }
    }

    /** Rung 1's threshold, chosen on even-numbered cases of the new signer and reported on the odd ones. */
    @Test
    fun printTheThresholdSweep() {
        val cs = cases(readings.getValue("islrtc"), Random(11))
        val tune = cs.filterIndexed { i, _ -> i % 2 == 0 }
        val test = cs.filterIndexed { i, _ -> i % 2 == 1 }
        fun stats(set: List<Case>, high: Float, doubt: Float): Pair<Float, Float> {
            var shown = 0; var wrong = 0
            for (c in set) {
                val r = ladder(c.lattice, book.match(c.lattice, c.question.ifEmpty { null }), book, high = high, doubt = doubt)
                if (r is Rung.Sentence) { shown++; if (r.en != book.english(c.truth)) wrong++ }
            }
            return shown.toFloat() / set.size to if (shown == 0) 0f else wrong.toFloat() / shown
        }
        // Doubt in conformal-set terms: 0 keeps only signs whose 50% set is one word, 1/9 allows two, 2/9 three.
        val grid = listOf(0f, 1f / 9, 2f / 9).flatMap { d -> listOf(0.5f, 0.6f, 0.7f, 0.8f, 0.9f).map { it to d } }
        for ((h, d) in grid) stats(tune, h, d).let { (cov, wrong) -> println("  tune high %.2f set<=%d: rung-1 share %.3f wrong %.3f".format(h, (d * 9).toInt() + 1, cov, wrong)) }
        // The largest rung-1 share whose wrong rate stays under 2% on the tuning half.
        val pick = grid.filter { (h, d) -> stats(tune, h, d).second <= 0.02f }.maxByOrNull { (h, d) -> stats(tune, h, d).first } ?: grid.last()
        stats(test, pick.first, pick.second).let { (cov, wrong) -> println("  chosen high %.2f set<=%d on held-out half: rung-1 share %.3f wrong %.3f".format(pick.first, (pick.second * 9).toInt() + 1, cov, wrong)) }
    }

    /** Writes docs/review/intents_review.csv (docs/15-gloss-to-text.md): every intent with its questions and templates and up
     * to 8 example sentences in English and Hindi, rendered by the engine itself, with columns for a Hindi speaker's
     * and a Deaf ISL reviewer's verdict (correct, awkward or wrong). Re-run after any change to the phrasebook. */
    @Test
    fun writeTheReviewSheet() {
        val questions = shipped.questions.associate { it.id to (it.text["en"] ?: it.id) }
        val examples = cases(readings.getValue("kaggle"), Random(5)).groupBy { it.truth.intent.id }
        fun cell(v: String) = "\"" + v.replace("\"", "\"\"") + "\""
        val rows = mutableListOf(listOf("intent", "answers questions", "pattern", "english template", "hindi template", "example gloss", "example english", "example hindi", "hindi verdict", "isl verdict", "reviewer", "date"))
        for (intent in book.intents) {
            val ex = examples[intent.id].orEmpty().distinctBy { book.english(it.truth) }.take(8)
            val q = intent.questions.joinToString(" | ") { questions[it] ?: it }
            if (ex.isEmpty()) rows += listOf(intent.id, q, intent.pattern.joinToString(" "), intent.en, intent.hi, "", "", "", "", "", "", "")
            for (c in ex) rows += listOf(intent.id, q, intent.pattern.joinToString(" "), intent.en, intent.hi,
                c.truth.fills.joinToString(" ") { it.label }, book.english(c.truth), book.hindi(c.truth), "", "", "", "")
        }
        val out = File("../docs/review/intents_review.csv")
        out.parentFile.mkdirs()
        // A byte-order mark so spreadsheet programs read the Hindi as UTF-8.
        out.writeText("﻿" + rows.joinToString("\n") { r -> r.joinToString(",", transform = ::cell) } + "\n")
        assertTrue(rows.size > book.intents.size)
    }
}
