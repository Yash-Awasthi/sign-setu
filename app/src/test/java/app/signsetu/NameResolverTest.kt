package app.signsetu

import app.signsetu.sign.NameResolver
import app.signsetu.sign.SignGuess
import app.signsetu.sign.faithful
import app.signsetu.sign.parseSentences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

class NameResolverTest {
    private val alphabet = "abmr"

    /** Frames that spell [text] cleanly, one letter per two frames with blanks between, [sure] on the right class. */
    private fun spell(text: String, sure: Float = 0.9f): Array<FloatArray> = text.flatMap { c ->
        listOf(alphabet.indexOf(c) + 1, 0)
    }.map { k -> FloatArray(alphabet.length + 1) { if (it == k) ln(sure) else ln((1 - sure) / alphabet.length) } }.toTypedArray()

    @Test
    fun ctcForwardEqualsTheSumOverEveryAlignment() {
        val rnd = Random(5)
        val logits = Array(4) { FloatArray(alphabet.length + 1) { rnd.nextFloat() * 3 } }
        val r = NameResolver(emptyMap(), alphabet)
        val logp = NameResolver.logSoftmax(logits)
        // Brute force: every frame path that collapses to "ab".
        var total = 0.0
        fun walk(t: Int, path: IntArray) {
            if (t == logp.size) {
                val collapsed = path.toList().fold(mutableListOf<Int>()) { acc, k -> if (acc.lastOrNull() != k) acc += k; acc }.filter { it != 0 }
                if (collapsed == listOf(1, 2)) total += exp(path.indices.sumOf { logp[it][path[it]].toDouble() })
                return
            }
            for (k in 0..alphabet.length) { path[t] = k; walk(t + 1, path) }
        }
        walk(0, IntArray(logp.size))
        assertEquals(ln(total), r.ctcLogProb(logp, "ab")!!.toDouble(), 1e-4)
    }

    @Test
    fun aClearSpellingSnapsToTheListedNameAndANearMissDoesNot() {
        val r = NameResolver(mapOf("ram" to 0.9f, "raam" to 0.2f, "bam" to 0.5f), alphabet)
        val clean = r.resolve(spell("ram"))!!
        assertTrue(clean.verified)
        assertEquals("Ram", clean.text)
        // "rm" with an unsure middle: ram and rm are close, so the raw spelling stays and the name is offered.
        val unsure = r.resolve(spell("rm", 0.5f))!!
        assertFalse(unsure.verified)
        assertEquals("Rm", unsure.text)
        assertTrue("Ram" in unsure.alternatives)
    }

    @Test
    fun anUnlistedSpellingStaysRawAndUnverified() {
        val r = NameResolver(mapOf("bam" to 0.5f), alphabet)
        val read = r.resolve(spell("amar"))!!
        assertFalse(read.verified)
        assertEquals("Amar", read.text)
    }

    @Test
    fun onlineSentencesWithUnsignedWordsAreDropped() {
        assertTrue(faithful("My uncle attacked me and I cried.", listOf("cry", "uncle", "attack")))
        assertFalse("invented weapon", faithful("My father hit me with a knife.", listOf("father", "hit")))
        assertFalse("invented negation", faithful("My father did not hit me.", listOf("father", "hit")))
        assertFalse("invented number", faithful("I am 12 years old.", listOf("year")))
        val g = listOf(listOf(SignGuess("water", 0.5f), SignGuess("father", 0.4f)), listOf(SignGuess("cry", 0.5f), SignGuess("hit", 0.45f)))
        val parsed = parseSentences("2,2 :: My father hit me.\nnonsense\n1,9 :: bad index\n1,1 :: I need water and I cried.", g)
        assertEquals(listOf("My father hit me."), parsed.map { it.text })
    }
}
