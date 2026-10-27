package app.signsetu

import app.signsetu.sign.Conformal
import app.signsetu.sign.SignGuess
import org.junit.Assert.assertEquals
import org.junit.Test

class ConformalTest {
    private val set = Conformal(mapOf("0.50" to 0.815f), kReg = 3, lambda = 0.02f)

    /** Same rule as calibrate.py's raps_sets: keep a rank while the mass above it plus the size penalty fits. */
    @Test
    fun aSureSignKeepsOneWordAndAnUnsureOneKeepsMany() {
        assertEquals(1, set.size(listOf(SignGuess("a", 0.95f), SignGuess("b", 0.03f), SignGuess("c", 0.02f))))
        val flat = List(40) { SignGuess("w$it", 0.025f) }
        assertEquals(19, set.size(flat))
        assertEquals("no threshold for that level keeps every guess", 40, set.size(flat, "0.99"))
    }
}
