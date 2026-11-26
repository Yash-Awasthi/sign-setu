package app.signsetu

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A pull up on the call's handle opens the whole toolset without leaving the call, and a tap does the same. */
@RunWith(AndroidJUnit4::class)
class SlidePanelDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as SignSetuApp
    private lateinit var content: ContentBundle

    private val sections = listOf("language", "pictures", "drawing", "emoji", "typing")

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
                            Stage(ui, content, tracker = null, cameraOn = true, gestureSpeed = 1f, askModes = CAPTION_MODES, {}, {}, {}, {}, {}, {}, {}, {}, onOfficer = null)
                        }
                    }
                }
            }
        }
    }

    private fun count(tag: String) = rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

    private fun assertAllSections() {
        assertEquals("the sheet", 1, count("stage.sheet"))
        sections.forEach { assertEquals("section " + it, 1, count("stage.sheet." + it)) }
    }

    /** One pull up on the handle, as the owner asked for. */
    private fun pullUp() {
        rule.onNode(hasTestTag("call.slide"), useUnmergedTree = true)
            .performTouchInput { swipeUp(startY = centerY, endY = centerY - 300f) }
        rule.waitForIdle()
    }

    @Test
    fun theHandleOutlivesTheFadingChrome() {
        rule.mainClock.autoAdvance = false
        showStage()
        rule.mainClock.advanceTimeBy(100)
        assertEquals("the handle at once", 1, count("call.slide"))

        // Six seconds on, the header is long gone; the handle is still the way in.
        rule.mainClock.advanceTimeBy(6_000)
        assertEquals("header faded", 0, count("call.header"))
        assertEquals("the handle stays", 1, count("call.slide"))
    }

    @Test
    fun aPullUpOpensTheWholeToolset() {
        showStage()
        rule.waitForIdle()

        pullUp()
        assertAllSections()
    }

    @Test
    fun theCallStaysUnderTheSheet() {
        showStage()
        rule.waitForIdle()

        pullUp()
        assertAllSections()
        assertEquals("the call is still composed", 1, count("call.caption"))
        assertEquals("the handle is still there", 1, count("call.slide"))
    }

    @Test
    fun aTapAlsoOpensTheSheet() {
        showStage()
        rule.waitForIdle()

        rule.onNode(hasTestTag("call.slide"), useUnmergedTree = true).performClick()
        rule.waitForIdle()
        assertAllSections()
    }

    @Test
    fun closingTheSheetReturnsToTheCall() {
        showStage()
        rule.waitForIdle()
        rule.onNode(hasTestTag("call.slide"), useUnmergedTree = true).performClick()
        rule.waitForIdle()
        assertAllSections()

        rule.onNode(hasTestTag("stage.sheet.close"), useUnmergedTree = true).performClick()
        rule.waitUntil(5_000) { count("stage.sheet") == 0 }

        assertEquals("back on the call", 1, count("call.caption"))
        assertEquals("the handle is back", 1, count("call.slide"))
        assertEquals("no sheet left", 0, count("stage.sheet"))
    }
}
