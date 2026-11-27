package app.signsetu

import app.signsetu.data.settings.SwitchAccess
import app.signsetu.ui.session.AnswerOrder
import app.signsetu.ui.session.SwitchAction
import app.signsetu.ui.session.SwitchScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Accessibility states that were carried by colour alone, or by a clock that kept running behind a
 * sheet, checked on the JVM.
 *
 * The behaviour test walks [SwitchScan] the way the stage does: dwell for a while, a sheet opens
 * over the stage, and time under the sheet must not count against the scan. The three source tests
 * hold the wiring that no JVM unit test can compose: the flow is told when a sheet is open, the
 * language tiles say which one is chosen, and the tool bar says which tool is showing.
 */
class AccessibilityAuditTest {

    private val session = File("src/main/java/app/signsetu/ui/session")

    /** The body of one top-level declaration, up to the next blank-line-separated `@Composable`. */
    private fun bodyOf(file: String, function: String): String {
        val text = File(session, "$file.kt").readText()
        val start = text.indexOf("fun $function(")
        assertTrue("$file declares $function", start >= 0)
        val rest = text.substring(start)
        val end = rest.indexOf("\n@Composable")
        return if (end > 0) rest.substring(0, end) else rest
    }

    @Test
    fun dwell_does_not_count_time_spent_under_a_sheet() {
        val scan = SwitchScan(AnswerOrder(listOf("yes", "no", "more"), 3), SwitchAccess.DWELL)
        val start = scan.start(0)

        // 1150 ms of dwell, then a sheet covers the stage for thirty seconds.
        assertEquals("nothing moves before the dwell is up", null, scan.tick(start, 1_150).second)

        // Without the rebase the next tick fires at once, which is the bug: the sheet closes and the
        // highlight has already moved on to a control the person never saw.
        assertEquals(SwitchAction.Move("no"), scan.tick(start, 30_100).second)

        val covered = scan.resume(start, 30_000)
        val (after, action) = scan.tick(covered, 30_100)
        assertNull("the dwell resumes where it was, not where the clock is", action)

        val (_, moved) = scan.tick(after, 31_200)
        assertEquals("a full dwell after the sheet closes moves on", SwitchAction.Move("no"), moved)
    }

    @Test
    fun answer_flow_is_told_when_a_sheet_is_open() {
        val remember = bodyOf("AnswerFlow", "rememberAnswerFlow")
        assertTrue("the flow takes a paused flag", remember.contains("paused: Boolean"))

        val call = File(session, "VideoCallStage.kt").readText()
        assertTrue(
            "the stage pauses the scan while a tool sheet covers it",
            call.contains("paused = sheetFor != null"),
        )
        assertTrue(
            "a paused flow runs no dwell timer",
            remember.contains("if (paused) return@LaunchedEffect"),
        )
    }

    @Test
    fun language_tiles_report_which_language_is_chosen() {
        val grid = bodyOf("StageButtons", "LanguageGrid")
        assertTrue("a tile is a button", grid.contains("role = Role.Button"))
        assertTrue("the chosen tile is announced as selected", grid.contains("this.selected = selected"))
    }

    @Test
    fun tool_bar_reports_which_tool_is_open() {
        val bar = bodyOf("StageTools", "StageToolBar")
        assertTrue("the open tool is announced as selected", bar.contains("selected = chosen"))
    }
}
