package app.signsetu

import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Provenance
import app.signsetu.data.session.SessionEvent
import app.signsetu.ui.session.sessionLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProvenanceTest {
    private val line = "app 0.8.52 (8052), content abc123, device key 0123456789abcdef, officer 4521"
    private val provenance = Provenance("0.8.52 (8052)", "abc123", "0123456789abcdef", "4521")

    @Test
    fun theLineNamesEachField() {
        assertEquals(line, provenance.line())
    }

    @Test
    fun theDeviceKeyAndTheOfficerAreTheIdentifiers() {
        assertEquals(listOf("0123456789abcdef", "4521"), provenance.identifiers())
    }

    @Test
    fun anOldNoneFieldIsNotAValue() {
        val old = Provenance.parse("app 0.8.52 (8052), content abc, device key none, officer 4521")
        assertNotNull(old)
        assertNull(old!!.deviceKey)
        assertEquals(listOf("4521"), old.identifiers())
    }

    @Test
    fun parseReadsTheLineBack() {
        assertEquals(provenance, Provenance.parse(line))
    }

    @Test
    fun redactionRemovesBothIdentifiersFromTheLine() {
        val out = provenance.redact(line)
        assertFalse(out.contains("0123456789abcdef"))
        assertFalse(out.contains("4521"))
        assertFalse(out.contains("device key"))
        assertFalse(out.contains("officer"))
    }

    @Test
    fun redactionRemovesCopiesElsewhereInTheText() {
        val out = provenance.redact("key 0123456789abcdef signed for officer 4521 on 2024-04-05")
        assertFalse(out.contains("0123456789abcdef"))
        assertFalse(out.contains("4521"))
        assertEquals("key [redacted] signed for officer [redacted] on 2024-04-05", out)
    }

    @Test
    fun theAssistantLogLosesTheIdentifiers() {
        val opened = SessionEvent(
            id = 1,
            ts = 0,
            type = EventType.SYSTEM,
            source = EventSource.MACHINE,
            lang = null,
            text = "opened:$line",
            cardId = null,
            lexiconId = null,
            drawingId = null,
        )

        val log = sessionLog(listOf(opened))

        assertFalse(log.contains("0123456789abcdef"))
        assertFalse(log.contains("4521"))
        assertEquals("[SYSTEM] [MACHINE] text=opened:app 0.8.52 (8052), content abc123, device key [redacted], officer [redacted] card= lang=", log)
    }
}
