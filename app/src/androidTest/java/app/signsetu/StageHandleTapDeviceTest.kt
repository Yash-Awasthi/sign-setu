package app.signsetu

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.data.content.CAPTION_MODES
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.session.Profile
import app.signsetu.data.session.Session
import app.signsetu.ui.components.LocalContentRepository
import app.signsetu.ui.components.LocalOnlineAssistant
import app.signsetu.ui.session.Mode
import app.signsetu.ui.session.SessionUi
import app.signsetu.ui.session.Stage
import app.signsetu.ui.theme.SignSetuTheme
import app.signsetu.ui.theme.SubjectTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/** The owner's rule: no timed hold to reach the officer's messages. One tap on the stage's chat button opens them. */
@RunWith(AndroidJUnit4::class)
class StageHandleTapDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun oneTapOnTheChatButtonReachesTheOfficer() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SignSetuApp
        val taps = AtomicInteger(0)
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
        val content: ContentBundle = runBlocking { app.container.content.load() }
        rule.setContent {
            CompositionLocalProvider(LocalContentRepository provides app.container.content, LocalOnlineAssistant provides app.container.onlineAssistant) {
                SignSetuTheme {
                    SubjectTheme {
                        Box(Modifier) {
                            Stage(
                                ui, content, tracker = null, cameraOn = false, gestureSpeed = 1f, askModes = CAPTION_MODES,
                                {}, {}, {}, {}, {}, {}, {}, {},
                                onOfficer = { taps.incrementAndGet() },
                            )
                        }
                    }
                }
            }
        }

        val handle = rule.onNode(hasContentDescription(rule.activity.getString(R.string.show_officer)))
        handle.assertExists()
        // A plain click action, no long press left in the semantics: a hold is what the owner asked to remove.
        val actions = handle.fetchSemanticsNode().config
        assertTrue("the chat button answers a click", SemanticsActions.OnClick in actions)
        assertEquals("nothing is measured before the tap", 0, taps.get())

        handle.performClick()
        rule.waitForIdle()
        assertEquals("one tap is enough", 1, taps.get())
    }
}
