package app.signsetu

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.data.session.Profile
import app.signsetu.data.session.SessionDatabase
import app.signsetu.data.session.SessionRepository
import app.signsetu.ui.components.LocalContentRepository
import app.signsetu.ui.components.LocalOnlineAssistant
import app.signsetu.ui.session.OfficerPane
import app.signsetu.ui.session.SessionUi
import app.signsetu.ui.session.SessionViewModel
import app.signsetu.ui.theme.SignSetuTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The owner's rule ("easy the closing of session"): one End control on the officer's surface, with the other
 * exits in exactly one menu, and the wrap-up dialog still the only place a session really ends. */
@RunWith(AndroidJUnit4::class)
class EndSessionDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as SignSetuApp
    private lateinit var db: SessionDatabase
    private lateinit var repo: SessionRepository
    private lateinit var watcher: CoroutineScope
    private lateinit var id: String

    private fun str(id: Int) = app.getString(id)

    @Before
    fun open() {
        db = SessionDatabase.open(app, ByteArray(32) { (it + 23).toByte() }, fileName = "end-session-test.db")
        repo = SessionRepository(db.dao())
        id = runBlocking { repo.start(Profile.NOT_SURE, "en") }
    }

    @After
    fun close() {
        if (this::watcher.isInitialized) watcher.cancel()
        db.close()
        app.deleteDatabase("end-session-test.db")
    }

    /** The officer pane as it opens: no menu, no tab change, nothing tapped. */
    private fun showPane(): SessionUi = runBlocking {
        val container = app.container
        val content = container.content.load()
        lateinit var vm: SessionViewModel
        instrumentation.runOnMainSync { vm = SessionViewModel(id, repo, content, "en", container.report) }
        watcher = CoroutineScope(Dispatchers.Default).apply { launch { vm.ui.collect {} } }
        val ui: SessionUi = withTimeout(10_000) { vm.ui.first { it != null }!! }
        rule.setContent {
            CompositionLocalProvider(LocalContentRepository provides container.content, LocalOnlineAssistant provides container.onlineAssistant) {
                SignSetuTheme {
                    OfficerPane(ui, vm, "en", emptySet(), {}, null, null, {}, {}, historyOff = false)
                }
            }
        }
        rule.waitForIdle()
        ui
    }

    private fun count(matcher: androidx.compose.ui.test.SemanticsMatcher) =
        rule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun theEndButtonIsOnTheSurfaceBeforeAnyMenuOpens() {
        showPane()

        val end = hasTestTag("officer.end")
        assertEquals("one End control", 1, count(end))
        rule.onNode(end).assertExists().assertHasClickAction().assertTextEquals(str(R.string.wrap_end_now))
        rule.onNode(end).assert(hasContentDescription(str(R.string.wrap_end_now)))
        // The overflow menu is still closed, so this was not found through it.
        assertEquals("no menu open", 0, count(hasContentDescription(str(R.string.officer_sign_language))))
    }

    @Test
    fun oneTapOpensTheWrapUpDialog() {
        showPane()

        rule.onNode(hasTestTag("officer.end")).performClick()
        rule.waitForIdle()

        // The dialog that really ends a session; no long press, no menu row in between.
        rule.onNodeWithText(str(R.string.wrap_title)).assertExists()
    }

    @Test
    fun theMorePageNoLongerCarriesASecondEnd() {
        showPane()

        rule.onNodeWithText(str(R.string.officer_tab_more)).performClick()
        rule.waitForIdle()

        assertEquals("the header still has the one End control", 1, count(hasText(str(R.string.wrap_end_now))))
        assertEquals("and no second End row on the More page", 0, count(hasText(str(R.string.officer_end))))
    }

    @Test
    fun keepGoingLeavesTheSessionRunning() {
        showPane()

        rule.onNode(hasTestTag("officer.end")).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.wrap_keep_going)).performClick()
        rule.waitForIdle()

        assertEquals("the dialog is gone", 0, count(hasText(str(R.string.wrap_title))))
        assertEquals("the call is still open", 1, count(hasTestTag("officer.end")))
    }
}
