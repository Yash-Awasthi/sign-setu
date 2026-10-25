package app.signsetu

import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.SessionEvent
import app.signsetu.ui.session.pendingReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PredictionsTest {
    private var id = 0L
    private fun event(type: EventType, source: EventSource = EventSource.SUBJECT) =
        SessionEvent(++id, id, type, source, "en", null, null, null, null)

    @Test
    fun aMachineReadingWaitsUntilSomethingElseIsAnswered() {
        val asked = event(EventType.QUESTION_SHOWN)
        val read = event(EventType.ANSWER_SIGNED, EventSource.MACHINE)
        assertNull(pendingReading(listOf(asked)))
        assertEquals(read.id, pendingReading(listOf(asked, read))?.id)
        // The officer's reading settles it: logged once, the bar goes, so no second reading is logged by a stray tap.
        assertNull(pendingReading(listOf(asked, read, event(EventType.ANSWER_SIGNED, EventSource.OFFICER))))
        // A tapped card is a new answer, so the old reading no longer waits.
        assertNull(pendingReading(listOf(asked, read, event(EventType.ANSWER_CARD))))
    }
}
