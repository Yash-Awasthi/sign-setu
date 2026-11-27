package app.signsetu

import app.signsetu.pair.AnswerBody
import app.signsetu.ui.pair.answerItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the stage may send after a tap: [sent] stands in for the link's answer outbox, and the officer logs only what
 * reaches it. */
class StageAnswersTest {

    private val sent = mutableListOf<Pair<Long, AnswerBody>>()

    private fun tap(itemId: Long?, last: Long, body: AnswerBody) {
        answerItem(itemId, last, body)?.let { sent += it to body }
    }

    @Test
    fun aTapBetweenItemsIsNotSentOrLogged() {
        tap(null, 7L, AnswerBody.YesNo(true))
        tap(null, 7L, AnswerBody.Card("dont_know"))
        tap(null, 7L, AnswerBody.Text("hungry"))
        assertTrue(sent.isEmpty())
    }

    @Test
    fun aTapInsideAnItemIsSentAsToday() {
        tap(5L, 7L, AnswerBody.YesNo(true))
        tap(5L, 7L, AnswerBody.Card("stop"))
        assertEquals(listOf(5L to AnswerBody.YesNo(true), 5L to AnswerBody.Card("stop")), sent)
    }

    @Test
    fun aStopBetweenItemsKeepsTheItemItCovered() {
        tap(null, 7L, AnswerBody.Card("stop"))
        assertEquals(listOf(7L to AnswerBody.Card("stop")), sent)
    }

    @Test
    fun aStopBeforeAnyItemNamesNone() {
        tap(null, 0L, AnswerBody.Card("stop"))
        assertEquals(listOf(0L to AnswerBody.Card("stop")), sent)
        // Row ids start at 1, so the officer records that stop with no question named.
        assertNull(answerItem(null, 0L, AnswerBody.YesNo(false)))
    }
}
