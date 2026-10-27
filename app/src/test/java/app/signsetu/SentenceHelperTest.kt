package app.signsetu

import app.signsetu.gloss.parseOrder
import app.signsetu.gloss.rerankPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceHelperTest {
    /** The helper can only reorder: anything but numbers of the listed candidates is ignored. */
    @Test
    fun onlyTheCandidatesNumbersCount() {
        assertEquals(listOf(1, 0, 2), parseOrder("2,1,3", 3))
        assertEquals(listOf(2, 0, 1), parseOrder("3", 3))
        assertEquals(listOf(1, 0), parseOrder("2, 9, 2, 1\nextra 1", 2))
        assertNull(parseOrder("My father hit me.", 3))
        assertNull(parseOrder("7", 3))
    }

    @Test
    fun thePromptListsQuestionSignsAndNumberedCandidates() {
        val p = rerankPrompt("Who hit you?", listOf(listOf("uncle", "father"), listOf("attack")), listOf("My uncle attacked me.", "My father attacked me."))
        assertTrue("Sign 1: uncle | father" in p && "2. My father attacked me." in p && p.startsWith("Question: Who hit you?"))
    }
}
