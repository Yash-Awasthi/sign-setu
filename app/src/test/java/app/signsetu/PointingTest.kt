package app.signsetu

import androidx.compose.ui.geometry.Offset
import app.signsetu.camera.LandmarkFrame
import app.signsetu.gloss.Candidate
import app.signsetu.gloss.GlossLattice
import app.signsetu.gloss.GlossToken
import app.signsetu.gloss.Phrasebook
import app.signsetu.gloss.TokenType
import app.signsetu.sign.SignGuess
import app.signsetu.sign.SignSegment
import app.signsetu.sign.pointing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class PointingTest {
    /** A right hand at (0.5, 0.5): wrist below, knuckles above, the index running out to the [dx] side, others folded. */
    private fun hand(dx: Float, indexUp: Boolean = false) = List(21) { i ->
        when (i) {
            0 -> Offset(0.5f, 0.6f)
            5 -> Offset(0.5f, 0.5f)
            6 -> if (indexUp) Offset(0.5f, 0.45f) else Offset(0.5f + dx * 0.3f, 0.5f)
            7 -> if (indexUp) Offset(0.5f, 0.41f) else Offset(0.5f + dx * 0.6f, 0.5f)
            8 -> if (indexUp) Offset(0.5f, 0.37f) else Offset(0.5f + dx, 0.5f)
            9 -> Offset(0.51f, 0.5f)
            in 10..20 -> Offset(0.51f, 0.55f)
            else -> Offset(0.49f, 0.55f)
        }
    }
    private fun frames(h: List<Offset>) = List(10) { LandmarkFrame(it * 40L, rightHand = h, aspect = 1f) }

    @Test
    fun aSidewaysPointReadsAsHeSheOrThere() {
        assertEquals("he", pointing(frames(hand(0.12f)))?.first()?.word)
        assertNull("a fist is not a point", pointing(frames(hand(0.01f))))
        assertNull("an index straight up is not sideways", pointing(frames(hand(0f, indexUp = true))))
    }

    @Test
    fun anUnsureSignThatPointsBecomesAPointTokenAndAnAttackerInTheSentence() {
        val point = SignSegment(frames(hand(0.12f)), listOf(SignGuess("lion", 0.1f)), candidates = listOf(SignGuess("lion", 0.1f)))
        val t = GlossLattice.token(0, point)
        assertEquals(TokenType.POINT, t.type)
        val book = Phrasebook(File(ASSETS, "intents.json").readText())
        val l = GlossLattice(listOf(t, GlossToken(1, TokenType.SIGN, 0, 0, listOf(Candidate("attack", 0.8f)))))
        val m = book.match(l, "x1").first()
        assertEquals("He attacked me.", book.english(m))
        assertEquals("उसने मुझ पर हमला किया।", book.hindi(m))
    }
}
