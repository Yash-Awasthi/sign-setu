package app.signsetu

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.ContentRepository
import app.signsetu.data.session.Profile
import app.signsetu.data.session.Session
import app.signsetu.ui.components.LocalContentRepository
import app.signsetu.ui.session.BoardSheet
import app.signsetu.ui.session.Mode
import app.signsetu.ui.session.Pending
import app.signsetu.ui.session.SessionUi
import app.signsetu.ui.session.Stage
import app.signsetu.ui.theme.SignSetuTheme
import app.signsetu.ui.theme.SubjectTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Accessibility Test Framework checks (labels, contrast, target size) on every stage state and the picture board, in both themes (AT-01). */
@RunWith(Parameterized::class)
class StageAccessibilityTest(private val dark: Boolean) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "dark={0}")
        fun themes() = listOf(false, true)
    }

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var repo: ContentRepository
    private lateinit var content: ContentBundle

    @Before
    fun load() {
        // The character's idle breathing never settles; with animations off (a setting the app honours) the UI goes idle.
        shell("settings put global animator_duration_scale 0")
        repo = ContentRepository(InstrumentationRegistry.getInstrumentation().targetContext.assets)
        content = runBlocking { repo.load() }
        rule.enableAccessibilityChecks()
    }

    @After
    fun restore() = shell("settings put global animator_duration_scale 1")

    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).close()
    }

    private fun show(body: @Composable () -> Unit) {
        rule.setContent { CompositionLocalProvider(LocalContentRepository provides repo) { SignSetuTheme(dark = dark, content = body) } }
        // A sheet opens its own window; the last root is the one on top.
        rule.onAllNodes(isRoot()).onLast().tryPerformAccessibilityChecks()
    }

    private fun stage(pending: Pending?, profile: Profile = Profile.YOUNG_CHILD) {
        val ui = SessionUi(
            session = Session("test", 0L, profile, "hi"),
            selectedGroup = "safety",
            sensitiveUnlocked = false,
            pending = pending,
            mode = Mode.SHOW,
            rotated = false,
            answeredCard = null,
            answeredYes = null,
            chosenLanguage = null,
            events = emptyList(),
        )
        show {
            SubjectTheme {
                Stage(ui, content, tracker = null, cameraOn = false, largeCaptions = false, gestureSpeed = 1f, {}, {}, {}, {}, {})
            }
        }
    }

    private fun ask(id: String) = Pending.Ask(content.questions.first { it.id == id })

    @Test fun greeting() = stage(null)

    @Test fun questionWithPictureHeldUp() = stage(ask("s2"))

    @Test fun questionWithChoices() = stage(ask("h1"))

    @Test fun numberPad() = stage(ask("i2"))

    @Test fun signsForAdult() = stage(ask("s1"), Profile.ISL_USER)

    @Test fun phrase() = content.phrases.first().let { stage(Pending.PhraseAsk(it, it.slots.first())) }

    @Test fun languageFinder() = stage(Pending.LanguageFinder, Profile.OTHER_LANGUAGE)

    @Test fun drawing() = stage(Pending.Drawing)

    @Test
    fun pictureBoard() = show { BoardSheet(content, "en", emptyList(), setOf("water"), {}, {}, {}) }
}
