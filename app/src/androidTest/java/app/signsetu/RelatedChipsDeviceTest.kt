package app.signsetu

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.data.content.CAPTION_MODES
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.Question
import app.signsetu.data.session.Profile
import app.signsetu.data.session.Session
import app.signsetu.sign.SignGuess
import app.signsetu.sign.SignSegment
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

/** The preset chips appear from the words already on the call, typed or read, with no tap. */
@RunWith(AndroidJUnit4::class)
class RelatedChipsDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SignSetuApp
    private lateinit var content: ContentBundle

    @Before
    fun load() {
        content = runBlocking { app.container.content.load() }
    }

    private fun ui(pending: Pending? = null, reading: List<SignSegment>? = null) = SessionUi(
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
        reading = reading,
        signed = emptyMap(),
    )

    private fun showStage(
        pending: Pending? = null,
        reading: List<SignSegment>? = null,
        onCard: (String) -> Unit = {},
        onNeed: (String) -> Unit = {},
    ) {
        val ui = ui(pending, reading)
        rule.setContent {
            CompositionLocalProvider(LocalContentRepository provides app.container.content, LocalOnlineAssistant provides app.container.onlineAssistant) {
                SignSetuTheme {
                    SubjectTheme {
                        Box(Modifier) {
                            Stage(
                                ui, content, tracker = null, cameraOn = false, gestureSpeed = 1f, askModes = CAPTION_MODES,
                                onCard = onCard, onYesNo = {}, onLanguage = {}, onDrawing = {}, onWritten = {},
                                onSignFrame = {}, onStartSigning = {}, onDoneSigning = {}, onOfficer = null, onNeed = onNeed,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun waterQuestion() = Pending.Ask(Question(id = "row250", group = "safety", text = mapOf("en" to "Do you want water?")))

    private fun hasChip(id: String) = hasTestTag("stage.chip." + id)

    @Test
    fun aQuestionAboutWaterShowsTheChipWithoutATap() {
        showStage(pending = waterQuestion())

        rule.onNode(hasChip("water"), useUnmergedTree = true).assertExists()
    }

    @Test
    fun tappingTheChipAnswersWaterOnce() {
        val answered = mutableListOf<String>()
        showStage(
            pending = waterQuestion(),
            onCard = { answered += it },
            onNeed = { answered += it },
        )

        rule.onNode(hasChip("water"), useUnmergedTree = true).performClick()
        rule.waitForIdle()

        assertEquals(listOf("water"), answered)
    }

    @Test
    fun aCallWithoutARelatedWordShowsNoChips() {
        showStage()

        assertEquals(0, rule.onAllNodes(hasTestTag("stage.chips"), useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun aSignReadAsFoodShowsTheFoodChip() {
        showStage(reading = listOf(SignSegment(frames = emptyList(), guesses = listOf(SignGuess("food", 1f)))))

        rule.onNode(hasChip("food"), useUnmergedTree = true).assertExists()
    }
}
