package app.signsetu

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.ui.session.WrapUp
import app.signsetu.ui.session.WrapUpDialog
import app.signsetu.ui.theme.SignSetuTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/** The owner's rule: at most three divergent paths on one screen. The close dialog is where the officer decides how the
 * session ends, so each of its states is rendered and its ways out are counted as they really appear. */
@RunWith(AndroidJUnit4::class)
class WrapUpPathsDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SignSetuApp

    private fun str(id: Int) = app.getString(id)

    private val tapped = AtomicInteger()

    @Composable
    private fun dialog(historyOff: Boolean, reopened: Boolean = false, thank: Boolean = true, onDelete: () -> Unit = {}) {
        SignSetuTheme {
            WrapUpDialog(
                historyOff = historyOff,
                summary = WrapUp(minutes = 12, shown = 4, answers = 3, unanswered = 1, unreadSigns = 0),
                title = "Session",
                onTitle = {},
                onThank = if (thank) ({ tapped.incrementAndGet() }) else null,
                aiOffer = false,
                includeSummary = false,
                onIncludeSummary = {},
                onExport = { tapped.incrementAndGet() },
                onSave = { tapped.incrementAndGet() },
                onDelete = { tapped.incrementAndGet(); onDelete() },
                onDismiss = {},
                reopened = reopened,
            )
        }
    }

    /** The ways out that are on the screen, by the words the officer reads. "Keep going" is not among them: it stays
     * where it is, so it diverges nowhere. */
    private fun shown(vararg words: String): List<String> =
        words.filter { rule.onAllNodes(hasText(it)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun aSessionWithHistoryEndsInThreeWays() {
        rule.setContent { dialog(historyOff = false) }
        rule.waitForIdle()
        assertEquals(
            listOf(str(R.string.wrap_thank), str(R.string.wrap_save), str(R.string.wrap_delete)),
            shown(str(R.string.wrap_thank), str(R.string.wrap_save), str(R.string.wrap_delete), str(R.string.end_export)),
        )
        // The report of a kept session goes to the phone from the history row, so it is not offered again here.
        assertEquals(0, rule.onAllNodes(hasText(str(R.string.end_export))).fetchSemanticsNodes().size)
        // Keep going is on the screen and still diverges nowhere.
        assertEquals(1, rule.onAllNodes(hasText(str(R.string.wrap_keep_going))).fetchSemanticsNodes().size)
    }

    @Test
    fun aRootedPhoneEndsInThreeWaysToo() {
        rule.setContent { dialog(historyOff = true) }
        rule.waitForIdle()
        assertEquals(
            listOf(str(R.string.wrap_thank), str(R.string.end_export), str(R.string.wrap_delete)),
            shown(str(R.string.wrap_thank), str(R.string.end_export), str(R.string.wrap_delete), str(R.string.wrap_save)),
        )
    }

    @Test
    fun aReopenedSessionEndsInThreeWaysAtMost() {
        rule.setContent { dialog(historyOff = false, reopened = true) }
        rule.waitForIdle()
        assertEquals(
            listOf(str(R.string.wrap_thank), str(R.string.wrap_save), str(R.string.wrap_discard)),
            shown(str(R.string.wrap_thank), str(R.string.wrap_save), str(R.string.wrap_discard), str(R.string.end_export), str(R.string.wrap_delete)),
        )
    }

    /** Deleting takes two taps: the second, deliberate one is the only control here that ends a session unsaved. */
    @Test
    fun theDeleteStillTakesTwoTaps() {
        val deletes = AtomicInteger()
        rule.setContent { dialog(historyOff = false, thank = false, onDelete = { deletes.incrementAndGet() }) }
        rule.waitForIdle()
        rule.onNode(hasText(str(R.string.wrap_delete))).performClick()
        rule.waitForIdle()
        assertEquals("one tap only arms it", 0, deletes.get())
        rule.onNode(hasText(str(R.string.wrap_delete_sure))).performClick()
        rule.waitForIdle()
        assertEquals(1, deletes.get())
    }
}
