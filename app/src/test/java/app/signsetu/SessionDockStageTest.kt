package app.signsetu

import app.signsetu.data.content.Question
import app.signsetu.data.session.Profile
import app.signsetu.data.session.Session
import app.signsetu.ui.dockStageUi
import app.signsetu.ui.session.Mode
import app.signsetu.ui.session.Pending
import app.signsetu.ui.session.SessionUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The docked stage beside the officer pane previews the live item, never the draft the officer is still writing. */
class SessionDockStageTest {

    private val q1 = Question("q1", "safety", mapOf("en" to "Are you hurt?"), cards = listOf("yes"), lexicon = "pain")

    private fun ui(pending: Pending?, onStage: Pending?, itemId: Long?) = SessionUi(
        Session("s", 0, Profile.ISL_USER, "hi"), "safety", false, pending, Mode.OFFICER, false, null, null, null, emptyList(), null, emptyMap(), onStage, itemId,
    )

    @Test
    fun theDockShowsTheItemOnStageNotTheOfficersDraft() {
        val state = ui(pending = Pending.Drawing, onStage = Pending.Ask(q1), itemId = 7L)
        val preview = dockStageUi(state)
        assertEquals(Pending.Ask(q1), preview.pending)
        assertEquals(7L, preview.itemId)
        // The officer's own pane keeps the draft; only the preview is moved onto the live item.
        assertEquals(Pending.Drawing, state.pending)
    }

    @Test
    fun withNothingOnStageThePreviewShowsNothing() {
        val preview = dockStageUi(ui(pending = Pending.Ask(q1), onStage = null, itemId = null))
        assertNull(preview.pending)
    }
}
