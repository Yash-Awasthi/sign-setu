package app.signsetu

import app.signsetu.ui.session.FAMILY_ROLES
import app.signsetu.ui.session.callCue
import app.signsetu.ui.session.calledRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pointing at a person on the family card travels in the answer's cue as "call:<role>". */
class CallCueTest {

    @Test
    fun pointingAtSomebodyBecomesACallCue() {
        for (role in FAMILY_ROLES) {
            assertEquals(role, calledRole(callCue(role)))
            assertEquals(role, calledRole("cue:" + callCue(role)))
        }
        assertNull("a role that is not on the card", calledRole(callCue("uncle")))
        assertNull("another cue", calledRole("feel:sad:4"))
        assertNull(calledRole(null))
        assertEquals("the card's roles are distinct", FAMILY_ROLES.size, FAMILY_ROLES.toSet().size)
        assertEquals("six people to point at", 6, FAMILY_ROLES.size)
    }

    @Test
    fun everyPersonOnTheCardIsARealContentCard() {
        for (role in FAMILY_ROLES) {
            assertTrue("the person's word and picture come from card $role", shipped.cards.containsKey(role))
        }
    }
}
