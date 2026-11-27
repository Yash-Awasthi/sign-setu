package app.signsetu

import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.SessionEvent
import app.signsetu.export.ReportRow
import app.signsetu.export.recordDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** The printed digest says a PDF was not edited, so two different records must never share one. */
class RecordDigestTest {
    private fun row(text: String?, cardId: String?): ReportRow =
        ReportRow(
            SessionEvent(
                id = 7,
                ts = 1_700_000_000_000,
                type = EventType.ANSWER_TEXT,
                source = EventSource.TEXT,
                lang = "en",
                text = text,
                cardId = cardId,
                lexiconId = null,
                drawingId = null,
            ),
            question = null,
            detail = "",
        )

    @Test
    fun aFieldBoundaryChangeChangesTheDigest() {
        // Same bytes, split differently across two fields: joining them plainly would hash the same.
        assertNotEquals(recordDigest(listOf(row("ab", "c"))), recordDigest(listOf(row("a", "bc"))))
    }

    @Test
    fun theSameRowsGiveTheSameDigestTwice() {
        val rows = listOf(row("ab", "c"), row(null, null))
        assertEquals(recordDigest(rows), recordDigest(rows))
    }
}
