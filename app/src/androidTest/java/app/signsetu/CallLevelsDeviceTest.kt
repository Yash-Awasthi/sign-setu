package app.signsetu

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.data.content.CAPTION_MODES
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.session.Profile
import app.signsetu.data.session.Session
import app.signsetu.ui.components.LocalContentRepository
import app.signsetu.ui.components.LocalOnlineAssistant
import app.signsetu.ui.session.Mode
import app.signsetu.ui.session.Pending
import app.signsetu.ui.session.SessionUi
import app.signsetu.ui.session.Stage
import app.signsetu.ui.theme.SignSetuTheme
import app.signsetu.ui.theme.SubjectTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/** Hold and messages sit at one level: both on the call, neither hidden behind the other. */
@RunWith(AndroidJUnit4::class)
class CallLevelsDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as SignSetuApp
    private lateinit var content: ContentBundle
    private val cards = AtomicInteger()
    private val officer = AtomicInteger()

    @Before
    fun load() {
        // cameraOn draws the real self view; the permission is granted so no system dialog covers the call.
        instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.CAMERA)
        content = runBlocking { app.container.content.load() }
    }

    private fun ui(pending: Pending? = null) = SessionUi(
        session = Session("test", 0L, Profile.NOT_SURE, "en"),
        selectedGroup = "safety",
        sensitiveUnlocked = false,
        pending = pending,
        mode = Mode.SHOW,
        rotated = false,
        answeredCard = null,
        answeredYes = null,
        chosenLanguage = null,
        events = emptyList(),
        reading = null,
        signed = emptyMap(),
    )

    private fun showStage() {
        val ui = ui()
        rule.setContent {
            CompositionLocalProvider(LocalContentRepository provides app.container.content, LocalOnlineAssistant provides app.container.onlineAssistant) {
                SignSetuTheme {
                    SubjectTheme {
                        Box(Modifier) {
                            Stage(
                                ui, content, tracker = null, cameraOn = true, gestureSpeed = 1f, askModes = CAPTION_MODES,
                                onCard = { cards.incrementAndGet() }, onYesNo = {}, onLanguage = {}, onDrawing = {}, onWritten = {},
                                onSignFrame = {}, onStartSigning = {}, onDoneSigning = {},
                                onOfficer = { officer.incrementAndGet() },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun count(tag: String) = rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

    private fun node(tag: String) = rule.onNode(hasTestTag(tag), useUnmergedTree = true)

    private fun revealControls() {
        rule.onRoot().performTouchInput { click() }
        rule.waitForIdle()
    }

    @Test
    fun bothLevelsAreOnTheCall() {
        showStage()
        revealControls()

        assertEquals("hold", 1, count("call.hold"))
        assertEquals("messages", 1, count("call.messages"))
        assertEquals("one row holds both", 1, count("call.levels"))
    }

    @Test
    fun theTwoLevelsShareATopAndAHeight() {
        showStage()
        revealControls()

        val hold = node("call.hold").fetchSemanticsNode().boundsInRoot
        val messages = node("call.messages").fetchSemanticsNode().boundsInRoot
        assertTrue("same top: " + hold.top + " vs " + messages.top, abs(hold.top - messages.top) <= 2f)
        assertTrue("same height: " + hold.height + " vs " + messages.height, abs(hold.height - messages.height) <= 2f)
        assertTrue("side by side, not stacked", messages.left >= hold.right - 2f)
    }

    @Test
    fun holdDeliversThePauseOnce() {
        showStage()
        revealControls()

        node("call.hold").performClick()
        rule.waitForIdle()

        assertEquals("the stage got the stop card once", 1, cards.get())
        assertEquals("messages was not touched", 0, officer.get())
    }

    @Test
    fun messagesOpensTheOfficerSideAndHoldStays() {
        showStage()
        revealControls()

        node("call.messages").performClick()
        rule.waitForIdle()

        assertEquals("the officer side opened once", 1, officer.get())
        assertEquals("hold did not answer", 0, cards.get())
        assertEquals("hold is still there", 1, count("call.hold"))
        assertEquals("messages is still there", 1, count("call.messages"))
    }
}
