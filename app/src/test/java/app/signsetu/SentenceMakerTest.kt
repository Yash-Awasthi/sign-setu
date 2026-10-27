package app.signsetu

import app.signsetu.sign.NGram
import app.signsetu.sign.SignGuess
import app.signsetu.sign.editDistance
import app.signsetu.sign.expectedWords
import app.signsetu.sign.faithful
import app.signsetu.sign.parseSentences
import app.signsetu.sign.rankSentences
import app.signsetu.sign.spellingFixes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceMakerTest {
    private val guesses = listOf(
        listOf(SignGuess("water", 0.5f), SignGuess("father", 0.4f)),
        listOf(SignGuess("cry", 0.5f), SignGuess("hit", 0.45f)),
    )

    @Test
    fun questionContextLiftsAWordTheReaderRankedSecond() {
        assertEquals(listOf("water", "cry"), rankSentences(guesses, emptySet(), null).first().words)
        val asked = rankSentences(guesses, expectedWords("Who hit you? Was it your father?", emptyList()), null)
        assertEquals(listOf("father", "hit"), asked.first().words)
        assertEquals(3, asked.size)
    }

    @Test
    fun theTrigramModelLiftsAReadingItHasSeen() {
        val lm = NGram(listOf("my father hit me", "father hit me with a stick"))
        assertTrue(lm.logp("<s>", "father", "hit") > lm.logp("<s>", "water", "cry"))
        assertEquals("father hit", rankSentences(guesses, emptySet(), lm).first().text)
    }

    @Test
    fun fingerspellingSlipsAreFixedAgainstKnownWords() {
        assertEquals(1, editDistance("ramm", "ram"))
        assertEquals(listOf("ram"), spellingFixes("ramm", listOf("ram", "shyam", "father")))
        val spelled = listOf(listOf(SignGuess("father", 0.9f)), listOf(SignGuess("name", 0.9f)), listOf(SignGuess("ramm", 0.6f, spelled = true)))
        val ranked = rankSentences(spelled, emptySet(), NGram(listOf("father name ram")), listOf("ram"))
        assertEquals(listOf("father", "name", "ram"), ranked.first().words)
    }

    @Test
    fun onlineLinesMapBackToEachSignsGuess() {
        val parsed = parseSentences("2,2 :: My father hit me.\nnonsense\n1,9 :: bad index\n1,1 :: I cried for water.", guesses)
        assertEquals(2, parsed.size)
        assertEquals(listOf("father", "hit"), parsed[0].words)
        assertEquals("My father hit me.", parsed[0].text)
    }

    @Test
    fun aSentenceWithAnythingNotSignedIsDropped() {
        assertTrue(faithful("My father hit me.", listOf("father", "hit")))
        assertTrue(faithful("I have two brothers.", listOf("brother", "two")))
        assertTrue(faithful("I cried because my uncle attacked me.", listOf("cry", "uncle", "attack")) == false)
        assertTrue(faithful("My uncle attacked me and I cried.", listOf("cry", "uncle", "attack")))
        assertFalse("an invented weapon", faithful("My father hit me with a knife.", listOf("father", "hit")))
        assertFalse("an invented negation", faithful("My father did not hit me.", listOf("father", "hit")))
        assertFalse("an invented number", faithful("I am 12 years old.", listOf("year")))
        assertTrue(parseSentences("1,1 :: Water cried a lot today.", listOf(listOf(SignGuess("water", 1f)), listOf(SignGuess("cry", 1f)))).isEmpty())
    }
}
