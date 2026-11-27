package app.signsetu

import app.signsetu.net.LinkSlot
import app.signsetu.net.failureNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a station, or a mistyped ticket, may put on this app's screen. Both rules are pure and checked here; the dial
 * itself needs a real QUIC peer and is not reached from a JVM test. */
class BrainPeerTextTest {
    @Test
    fun aFailureShowsTheExceptionTypeAndNeverThePeersWords() {
        val peer = NumberFormatException("station says: pay now, call 112 <b>trust me</b>")
        val shown = failureNotice(peer)
        assertTrue(shown, shown.startsWith("NumberFormatException:"))
        assertFalse(shown, shown.contains("112"))
        assertFalse(shown, shown.contains("trust me"))
        assertFalse(shown, shown.contains("station says"))
        // The type is what support needs; the rest is one of the app's own sentences, so it never varies with the peer.
        assertEquals(failureNotice(NumberFormatException("other text")), shown)

        assertTrue(failureNotice(java.io.EOFException("ticket text")).startsWith("EOFException:"))
        assertTrue(failureNotice(IllegalStateException("reply body")).startsWith("IllegalStateException:"))
        val peerJson = """{"peer":"text"}"""
        val badJson = runCatching { kotlinx.serialization.json.Json.decodeFromString<String>(peerJson) }.exceptionOrNull()!!
        assertFalse(failureNotice(badJson).contains("peer"))
    }

    @Test
    fun aFailedCandidateClosesOnlyItselfAndLeavesTheStationCurrent() {
        val slot = LinkSlot<String, String>()
        slot.current = "ticket-1" to "station"
        slot.endpoint = "relay-1" to "station-endpoint"

        // A mistyped ticket: dialled on its own endpoint, never adopted.
        val stray = LinkSlot.Dial("candidate", "candidate-endpoint", "relay-2", own = true, pending = true)
        val gone = slot.abandon(stray)

        assertEquals("ticket-1" to "station", slot.current)
        assertEquals("relay-1" to "station-endpoint", slot.endpoint)
        assertEquals("candidate", gone.conn)
        assertEquals("candidate-endpoint", gone.endpoint)

        // A candidate on the station's own endpoint goes alone; the endpoint stays up for the station.
        val same = LinkSlot.Dial("candidate-2", "station-endpoint", "relay-1", own = false, pending = true)
        val gone2 = slot.abandon(same)
        assertEquals("candidate-2", gone2.conn)
        assertNull(gone2.endpoint)
        assertEquals("relay-1" to "station-endpoint", slot.endpoint)
        assertEquals("ticket-1" to "station", slot.current)
    }

    @Test
    fun aCandidateThatAnsweredReplacesTheStationAndHandsTheOldOneBack() {
        val slot = LinkSlot<String, String>()
        slot.current = "ticket-1" to "station"
        slot.endpoint = "relay-1" to "station-endpoint"

        val worked = LinkSlot.Dial("candidate", "candidate-endpoint", "relay-2", own = true, pending = true)
        val gone = slot.adopt("ticket-2", worked)

        assertEquals("ticket-2" to "candidate", slot.current)
        assertEquals("relay-2" to "candidate-endpoint", slot.endpoint)
        assertEquals("station", gone.conn)
        assertEquals("station-endpoint", gone.endpoint)

        // Dropping only reaches the connection that is still the station's.
        assertFalse(slot.drop("station"))
        assertTrue(slot.drop("candidate"))
        assertNull(slot.current)
    }

    @Test
    fun theStationsConnectionIsKeptForItsOwnTicketOnly() {
        val slot = LinkSlot<String, String>()
        slot.current = "ticket-1" to "station"
        assertEquals("station", slot.kept("ticket-1") { true })
        assertNull(slot.kept("ticket-2") { true })
        assertNull(slot.kept("ticket-1") { false })
        assertNull(slot.endpointOn("relay-2"))
        assertEquals("candidate", LinkSlot.Dial<String, String>("candidate", pending = true).conn)
    }
}
