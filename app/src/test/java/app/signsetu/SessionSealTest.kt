package app.signsetu

import app.signsetu.data.session.AudioEntity
import app.signsetu.data.session.DrawingEntity
import app.signsetu.data.session.EventEntity
import app.signsetu.data.session.LandmarksEntity
import app.signsetu.data.session.SessionEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** insertSealed on the in-memory DAO the other session tests use: a row and what it holds are written only while
 * its session is still there, since retention or Discard can remove it between the caller's check and the insert. */
class SessionSealTest {
    private val dao = MemoryDao()

    private fun event(sessionId: String) = EventEntity(
        id = 0,
        sessionId = sessionId,
        ts = 1L,
        type = "NOTE",
        source = "OFFICER",
        lang = null,
        text = "a row",
        cardId = null,
        lexiconId = null,
        drawingId = null,
    )

    @Test
    fun aSessionDeletedBeforeTheInsertLeavesNoRowAndNoChildren() = runBlocking {
        dao.insertSession(SessionEntity("gone", startedAt = 1L, endedAt = null, profile = "NOT_SURE", subjectLang = "en"))
        assertEquals("the retention sweep takes the session", 1, dao.deleteIdleBefore(Long.MAX_VALUE))
        val bytes = byteArrayOf(1, 2, 3)

        val written = dao.insertSealed(
            event("gone"),
            DrawingEntity(sessionId = "gone", png = bytes),
            AudioEntity(sessionId = "gone", wav = bytes),
            LandmarksEntity(sessionId = "gone", json = bytes),
        )

        assertEquals("the row is refused rather than breaking the foreign key", -1L, written)
        assertTrue("no event row", dao.rows.value.isEmpty())
        // The ids an insert would have taken; none of the three rows may exist.
        assertNull("no drawing", dao.drawing(1L))
        assertNull("no audio", dao.audio(2L))
        assertNull("no landmarks", dao.landmarks(3L))
    }

    @Test
    fun aRowForALiveSessionKeepsItsIdAndItsChildren() = runBlocking {
        dao.insertSession(SessionEntity("live", startedAt = 1L, endedAt = null, profile = "NOT_SURE", subjectLang = "en"))
        val png = byteArrayOf(4, 5)
        val wav = byteArrayOf(6)
        val json = byteArrayOf(7)

        val id = dao.insertSealed(
            event("live"),
            DrawingEntity(sessionId = "live", png = png),
            AudioEntity(sessionId = "live", wav = wav),
            LandmarksEntity(sessionId = "live", json = json),
        )

        assertTrue("the insert keeps its row id", id > 0)
        val row = dao.rows.value.single()
        assertEquals("the event row is the one returned", id, row.id)
        assertArrayEquals("the drawing is written", png, dao.drawing(row.drawingId!!))
        assertArrayEquals("the audio is written", wav, dao.audio(row.audioId!!))
        assertArrayEquals("the landmarks are written", json, dao.landmarks(row.landmarksId!!))
    }
}
