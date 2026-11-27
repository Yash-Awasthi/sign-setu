package app.signsetu

import app.signsetu.pair.LinkState
import app.signsetu.pair.StageView
import app.signsetu.pair.ToOfficer
import app.signsetu.pair.WirePending
import app.signsetu.ui.pair.PairedPane
import app.signsetu.ui.pair.ShowQueue
import app.signsetu.ui.pair.StageReadout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StageBridgeTest {

    private val view = StageView("hi", "ISL_USER", true, WirePending.Ask("q1"), false, 1f, true)

    private fun pane(readout: StageReadout, state: LinkState = LinkState.Live) = PairedPane(
        label = "stage",
        state = state,
        status = readout.status,
        lastError = readout.lastError,
        pinned = readout.pinned,
        breakPending = false,
        askedAt = null,
        onDone = {},
        onReconnect = {},
        onUseThisPhone = {},
        onBreakGiven = {},
        allowShow = { true },
    )

    @Test
    fun anErrorStatusReachesThePaneAndTheNextGoodStatusClearsIt() {
        val read = StageReadout()
        // An error report carries no reading (it sends 0), so the last real status must survive it.
        val failed = read.onStatus(ToOfficer.Status(0, false, true, true, error = "unknown"))
        assertEquals("unknown", pane(failed).lastError)
        assertNull(pane(failed).status)

        val good = failed.onStatus(ToOfficer.Status(80, true, true, true))
        assertNull(pane(good).lastError)
        assertEquals(80, pane(good).status?.battery)
        assertTrue(pane(good).status?.charging == true)
    }

    @Test
    fun aStatusReportingTheStageUnpinnedRaisesTheAlert() {
        val read = StageReadout().onStatus(ToOfficer.Status(90, true, pinned = false, cameraOn = true))
        assertTrue(pane(read).unpinned)

        val pinnedAgain = read.onStatus(ToOfficer.Status(90, true, pinned = true, cameraOn = true))
        assertFalse(pane(pinnedAgain).unpinned)
    }

    @Test
    fun aShowWaitsWhileABreakIsPendingAndGoesWhenItIsAcknowledged() {
        val queue = ShowQueue()
        assertNull(queue.offer(5L, view, blocked = true))
        // The officer asks something else mid-break: only the newest Show waits.
        val newer = view.copy(item = WirePending.Cards(listOf("water")))
        assertNull(queue.offer(6L, newer, blocked = true))

        val sent = queue.offer(6L, newer, blocked = false)
        assertEquals(6L to newer, sent)
    }
}
