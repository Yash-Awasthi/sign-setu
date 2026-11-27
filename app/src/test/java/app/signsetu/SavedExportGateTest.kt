package app.signsetu

import app.signsetu.ui.start.guardedExport
import org.junit.Assert.assertEquals
import org.junit.Test

/** Saving a stored session to this phone and sending it to the station wait for OfficerAuth, as Delete and the
 * in-session export do. The prompt itself needs an Activity, so the gate is driven by a fake confirm. */
class SavedExportGateTest {
    @Test
    fun nothingLeavesBeforeTheOfficerConfirms() {
        var ok: (() -> Unit)? = null
        var ran = 0
        guardedExport({ ok = it }) { ran++ }
        assertEquals("the export waits for the prompt", 0, ran)
        ok!!.invoke()
        assertEquals("then runs once", 1, ran)
    }

    @Test
    fun aDismissedPromptLeavesNothing() {
        var asked = 0
        guardedExport({ asked++ }) { throw AssertionError("exported without the officer") }
        assertEquals("asked once, and the body never ran", 1, asked)
    }
}
