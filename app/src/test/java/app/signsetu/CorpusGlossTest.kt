package app.signsetu

import app.signsetu.data.content.SignMode
import app.signsetu.data.content.planText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Typed sentences from the ISL-CSLTR corpus (CC BY 4.0) against the signs its glosses say they carry. */
class CorpusGlossTest {

    private val SIGNS = setOf(SignMode.SIGNS)
    private val corpus: List<Pair<String, String>> = javaClass.getResource("/isl_csltr_glosses.csv")!!.readText().lines()
        .drop(1).filter { it.isNotBlank() }
        .map { Regex("^\"?([^\"]*?)\"?,\"?([^\"]*?)\"?$").find(it)!!.groupValues.let { it[1] to it[2] } }

    private fun motions(text: String) = shipped.planText(text, SIGNS).motions

    @Test
    fun everyNegatedGlossShakesTheHead() {
        val missed = corpus.filter { (_, g) -> Regex("\\b(DO ?NOT|DONT|NOT)\\b").containsMatchIn(g) }
            .filter { (s, _) -> "no.shake" !in motions(s) }
        assertEquals("negated without a shake", emptyList<Pair<String, String>>(), missed)
    }

    @Test
    fun englishDoIsNeverGive() {
        val gives = corpus.filter { (s, _) -> "sign.give" in motions(s) }
        assertEquals(emptyList<Pair<String, String>>(), gives)
        // Hindi "do" at the end of its sentence is still give.
        assertTrue(motions("pani do").contains("sign.give"))
    }

    @Test
    fun whoAndWhyAreNotSignedAsWhich() {
        val wrong = corpus.filter { (s, g) -> g.startsWith("WHO ") || g.startsWith("WHY ") }
            .filter { (s, _) -> "isl.which" in motions(s) }
        assertEquals(emptyList<Pair<String, String>>(), wrong)
        assertTrue(motions("which college").contains("isl.which"))
    }

    @Test
    fun contractionsKeepTheirNegation() {
        for (s in listOf("I don't agree", "I can’t help", "he doesnt know")) assertTrue(s, "no.shake" in motions(s))
        assertTrue("no.shake" !in motions("I want water"))
    }
}
