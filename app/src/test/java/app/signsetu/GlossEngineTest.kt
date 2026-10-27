package app.signsetu

import app.signsetu.camera.Head
import app.signsetu.gloss.Candidate
import app.signsetu.gloss.Filled
import app.signsetu.gloss.Gender
import app.signsetu.gloss.GlossLattice
import app.signsetu.gloss.GlossToken
import app.signsetu.gloss.NonManual
import app.signsetu.gloss.Phrasebook
import app.signsetu.gloss.Rung
import app.signsetu.gloss.TokenType
import app.signsetu.gloss.faithful
import app.signsetu.sign.SignGuess
import app.signsetu.sign.SignSegment
import app.signsetu.ui.session.captionRange
import app.signsetu.gloss.ladder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class GlossEngineTest {
    private val book = Phrasebook(File(ASSETS, "intents.json").readText())

    private fun sign(id: Int, vararg c: Pair<String, Float>, reject: Float = 0f, nm: NonManual = NonManual()) =
        GlossToken(id, TokenType.SIGN, id * 500L, id * 500L + 400, c.map { Candidate(it.first, it.second) }, nm, reject)
    private fun spell(id: Int, name: String, verified: Boolean = true) =
        GlossToken(id, TokenType.SPELL, 0, 0, listOf(Candidate(name, 0.9f)), verified = verified)
    private fun num(id: Int, n: String) = GlossToken(id, TokenType.NUM, 0, 0, listOf(Candidate(n, 0.9f)))

    @Test
    fun latticesRoundTripThroughJson() {
        for (n in listOf(0, 1, 20)) {
            val l = GlossLattice(List(n) { sign(it, "mother" to 0.6f, "father" to 0.3f, nm = NonManual(browsRaised = 0.5f)) }, appVersion = "t")
            assertEquals(l, GlossLattice.fromJson(l.toJson()))
        }
    }

    @Test
    fun theQuestionOnScreenDecidesBetweenReadingsOfTheSameSigns() {
        val l = GlossLattice(listOf(sign(0, "father" to 0.7f), spell(1, "ram")))
        val r = ladder(l, book.match(l, "f5"), book)
        assertTrue(r is Rung.Sentence)
        assertEquals("My father's name is Ram.", (r as Rung.Sentence).en)
        assertEquals("मेरे पिता का नाम Ram है।", r.hi)
        assertEquals("relative_name", book.match(l, "f5").first().intent.id)
    }

    @Test
    fun aMissedOrExtraSignStillMatchesThroughTheLattice() {
        val extra = GlossLattice(listOf(sign(0, "uncle" to 0.6f), sign(1, "tea" to 0.3f, "money" to 0.1f), sign(2, "knife" to 0.5f), sign(3, "attack" to 0.6f)))
        val m = book.match(extra, "x1").first()
        assertEquals("attacked_with", m.intent.id)
        assertEquals("My uncle attacked me with a knife.", book.english(m))
        val second = GlossLattice(listOf(sign(0, "bus" to 0.2f, "car" to 0.15f)))
        assertEquals("came_by", book.match(second, "l2").first().intent.id)
    }

    @Test
    fun hindiGenderFormsFollowTheSpeaker() {
        val l = GlossLattice(listOf(sign(0, "bus" to 0.8f)))
        val m = book.match(l, "l2").first()
        assertEquals("मैं बस से आया/आई।", book.hindi(m))
        assertEquals("मैं बस से आई।", book.hindi(m, Gender.FEMININE))
        assertEquals("मैं बस से आया।", book.hindi(m, Gender.MASCULINE))
    }

    @Test
    fun negationAndYesNeedTheirHeadMovement() {
        val plain = GlossLattice(listOf(sign(0, "happy" to 0.8f)))
        assertEquals("I am happy.", book.english(book.match(plain, "e2").first()))
        val shaken = GlossLattice(listOf(sign(0, "happy" to 0.8f, nm = NonManual(headShake = 1f))))
        assertEquals("I am not happy.", book.english(book.match(shaken, "e2").first()))
        assertTrue(book.match(plain, "e2").none { it.intent.id == "not_feel" || it.intent.id == "no" })
    }

    @Test
    fun doubtfulWordsAndUnverifiedNamesDropToTheFixRung() {
        val l = GlossLattice(listOf(sign(0, "father" to 0.7f), spell(1, "rmm", verified = false)))
        val r = ladder(l, book.match(l, "f5"), book)
        assertTrue(r is Rung.FixWords && 1 in r.doubtful)
        val unread = GlossLattice(listOf(GlossToken(0, TokenType.UNKNOWN, 0, 0, listOf(Candidate("lion", 0.05f)), rejectScore = 1f)))
        assertEquals(Rung.SignAgain, ladder(unread, book.match(unread, "a1"), book))
    }

    @Test
    fun riskyIntentsAskForConfirmation() {
        val l = GlossLattice(listOf(sign(0, "fever" to 0.9f)))
        val r = ladder(l, book.match(l, "h3"), book)
        assertTrue(r is Rung.Sentence && r.confirm)
    }

    /** Fuzz: whatever the lattice, a fill that is not one of its token's candidates never passes the checker. */
    @Test
    fun theCheckerRejectsEveryInventedWord() {
        val rnd = Random(3)
        val vocab = listOf("mother", "father", "knife", "bus", "happy", "fever", "school", "red", "shirt", "attack", "doctor", "tea")
        var checked = 0
        repeat(3000) {
            val n = 1 + rnd.nextInt(5)
            val l = GlossLattice(List(n) { i -> sign(i, *vocab.shuffled(rnd).take(1 + rnd.nextInt(3)).map { it to rnd.nextFloat() }.toTypedArray()) })
            val m = book.match(l, null).firstOrNull() ?: return@repeat
            val t = rnd.nextInt(n)
            val invented = vocab.first { w -> l.tokens[t].candidates.none { it.label == w } }
            val corrupt = m.copy(fills = m.fills + Filled("OBJECT", invented, t, 0.9f), used = m.used + t)
            assertFalse(faithful(corrupt, l))
            checked++
        }
        assertTrue(checked > 1000)
    }

    /** A shake between signs is outside every sign's frames; the window carries it to the whole answer. */
    @Test
    fun aShakeAnywhereInTheWindowNegatesTheAnswer() {
        val seg = SignSegment(emptyList(), listOf(SignGuess("happy", 0.8f))).withWindowHead(setOf(Head.Shake))
        val l = GlossLattice.of(listOf(seg))
        assertEquals("I am not happy.", book.english(book.match(l, "e2").first()))
    }

    /** The sign plays from the English wording; the Hindi caption lights the word with the same entry. */
    @Test
    fun theCaptionLightsTheWordBeingSignedInTheSubjectsLanguage() {
        val caption = "क्या आपको चोट लगी है?"
        val hurt = shipped.words.entries.firstOrNull { (_, e) -> "चोट" in e.w }?.key
        if (hurt != null) assertEquals("चोट", captionRange(shipped, hurt, caption)?.let { caption.substring(it) })
        assertEquals(null, captionRange(shipped, "xyzzy", caption))
    }
}
