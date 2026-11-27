package app.signsetu

import app.signsetu.ui.session.FollowUpKey
import app.signsetu.ui.session.followUpsWanted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Follow-ups for the last answer are fetched when there is an answer and the officer is not typing. The typing flag is
 * part of the effect's key, so an answer that landed mid-sentence is fetched once the box is cleared. */
class OfficerDockFollowUpTest {

    @Test
    fun anAnswerSeenWhileTypingIsFetchedWhenTheBoxIsCleared() {
        val id = 12L
        val whileTyping = FollowUpKey(id, typing = true)
        val cleared = FollowUpKey(id, typing = false)
        assertNotEquals("typing is a key, so the effect restarts on the clear", whileTyping, cleared)
        assertEquals(listOf(false, true), listOf(whileTyping, cleared).map(::followUpsWanted))
    }

    @Test
    fun typingHoldsTheFetchBackEvenWithAnAnswer() {
        assertFalse(followUpsWanted(FollowUpKey(3L, typing = true)))
        assertTrue("the same answer is fetched once typing is over", followUpsWanted(FollowUpKey(3L, typing = false)))
    }

    @Test
    fun noAnswerMeansNoFetch() {
        assertFalse(followUpsWanted(FollowUpKey(null, typing = false)))
        assertFalse(followUpsWanted(FollowUpKey(null, typing = true)))
    }

    @Test
    fun theSameAnswerAndTypingStateDoNotRefetch() {
        assertEquals("unrelated log rows keep the key", FollowUpKey(9L, typing = false), FollowUpKey(9L, typing = false))
        assertNotEquals("a new answer restarts the effect", FollowUpKey(9L, typing = false), FollowUpKey(10L, typing = false))
    }
}
