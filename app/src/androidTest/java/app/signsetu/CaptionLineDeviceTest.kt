package app.signsetu

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.data.content.CAPTION_MODES
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.session.Profile
import app.signsetu.data.session.Session
import app.signsetu.ui.components.LocalContentRepository
import app.signsetu.ui.components.LocalOnlineAssistant
import app.signsetu.ui.session.CallCaption
import app.signsetu.ui.session.Mode
import app.signsetu.ui.session.SessionUi
import app.signsetu.ui.session.Stage
import app.signsetu.ui.session.captionChunks
import app.signsetu.ui.session.chunkFor
import app.signsetu.ui.theme.SignSetuTheme
import app.signsetu.ui.theme.SubjectTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The caption draws one large line, the line that holds the lit word, so the light never lands on another line. */
@RunWith(AndroidJUnit4::class)
class CaptionLineDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as SignSetuApp
    private lateinit var content: ContentBundle

    private val text = "Do you want water or food before we begin the next question"

    @Before
    fun load() {
        instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.CAMERA)
        content = runBlocking { app.container.content.load() }
    }

    /** The line the caption would draw for [highlight], from the pure splitter. */
    private fun expectedLine(highlight: IntRange?): String {
        val chunks = captionChunks(text)
        return text.substring(chunks[chunkFor(chunks, highlight, text)])
    }

    private fun draw(highlight: IntRange?): TextLayoutResult {
        var layout: TextLayoutResult? = null
        rule.setContent {
            SignSetuTheme { CallCaption(text, highlight, null, onTextLayout = { layout = it }) }
        }
        rule.waitForIdle()
        return layout!!
    }

    @Test
    fun theLineDrawnIsTheOneHoldingTheLitWord() {
        val water = text.indexOf("water")
        val layout = draw(water..water + 4)

        assertEquals("the chunk holding water", expectedLine(water..water + 4), layout.layoutInput.text.text)
        assertTrue("water is on the line drawn", layout.layoutInput.text.text.contains("water"))
        assertEquals("one line", 1, layout.lineCount)
    }

    @Test
    fun theTypeIsLarge() {
        val water = text.indexOf("water")
        val layout = draw(water..water + 4)

        assertTrue("at least 24sp: " + layout.layoutInput.style.fontSize.value, layout.layoutInput.style.fontSize.value >= 24f)
    }

    @Test
    fun aWordOnTheSecondLinePullsThatLineUp() {
        val next = text.indexOf("next")
        val layout = draw(next..next + 3)

        assertEquals("the second chunk", expectedLine(next..next + 3), layout.layoutInput.text.text)
        assertTrue("next is on the line drawn", layout.layoutInput.text.text.contains("next"))
        assertEquals("still one line", 1, layout.lineCount)
    }

    @Test
    fun theStageStillDrawsTheCaption() {
        val ui = SessionUi(
            session = Session("test", 0L, Profile.NOT_SURE, "en"),
            selectedGroup = "safety",
            sensitiveUnlocked = false,
            pending = null,
            mode = Mode.SHOW,
            rotated = false,
            answeredCard = null,
            answeredYes = null,
            chosenLanguage = null,
            events = emptyList(),
            reading = null,
            signed = emptyMap(),
        )
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
        rule.waitForIdle()

        assertEquals("the caption is wired", 1, rule.onAllNodes(hasTestTag("call.caption"), useUnmergedTree = true).fetchSemanticsNodes().size)
    }
}
