package app.signsetu

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.data.content.CAPTION_MODES
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.Question
import app.signsetu.data.session.Profile
import app.signsetu.data.session.Session
import app.signsetu.ui.components.LocalContentRepository
import app.signsetu.ui.components.LocalOnlineAssistant
import app.signsetu.ui.session.Mode
import app.signsetu.ui.session.NEED_IDS
import app.signsetu.ui.session.Pending
import app.signsetu.ui.session.SessionUi
import app.signsetu.ui.session.Stage
import app.signsetu.ui.session.StagePaths
import app.signsetu.ui.theme.SignSetuTheme
import app.signsetu.ui.theme.SubjectTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The six needs sit at the bottom of every state: never faded away, never buried by a panel. */
@RunWith(AndroidJUnit4::class)
class NeedRowDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SignSetuApp
    private lateinit var content: ContentBundle

    @Before
    fun load() {
        content = runBlocking { app.container.content.load() }
    }

    private fun ui(pending: Pending?) = SessionUi(
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

    private fun showStage(pending: Pending?, onNeed: (String) -> Unit = {}) {
        val ui = ui(pending)
        rule.setContent {
            CompositionLocalProvider(LocalContentRepository provides app.container.content, LocalOnlineAssistant provides app.container.onlineAssistant) {
                SignSetuTheme {
                    SubjectTheme {
                        Box(Modifier) {
                            Stage(
                                ui, content, tracker = null, cameraOn = false, gestureSpeed = 1f, askModes = CAPTION_MODES,
                                onCard = {}, onYesNo = {}, onLanguage = {}, onDrawing = {}, onWritten = {},
                                onSignFrame = {}, onStartSigning = {}, onDoneSigning = {}, onOfficer = null, onNeed = onNeed,
                            )
                        }
                    }
                }
            }
        }
    }

    /** A question with pictures, so the stage opens its picture panel and stops composing its bottom bar. */
    private fun pictureQuestion() = Pending.Ask(
        Question(id = "row251", group = "safety", text = mapOf("en" to "What do you need?"), cards = listOf("water", "food")),
    )

    private fun spokenQuestion() = Pending.Ask(Question(id = "row251b", group = "safety", text = mapOf("en" to "Are you okay?")))

    private fun count(matcher: androidx.compose.ui.test.SemanticsMatcher) =
        rule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun assertAllSixTiles() {
        NEED_IDS.forEach { id -> assertEquals("tile " + id, 1, count(hasTestTag("stage.need." + id))) }
    }

    @Test
    fun theRowAndAllSixTilesAreOnEveryCall() {
        showStage(pictureQuestion())

        assertEquals("one need row", 1, count(hasTestTag("stage.needs")))
        assertAllSixTiles()
    }

    @Test
    fun theRowOutlivesTheFadingControls() {
        rule.mainClock.autoAdvance = false
        showStage(spokenQuestion())
        rule.mainClock.advanceTimeBy(100)

        // A touch brings the answers group up; the need row was already there.
        rule.onRoot().performTouchInput { click() }
        rule.mainClock.advanceTimeBy(200)
        assertEquals("answers up after a touch", 1, count(hasTestTag(StagePaths.ANSWERS)))
        assertAllSixTiles()

        // Six seconds later the answers group is gone again; the need row is not.
        rule.mainClock.advanceTimeBy(6_000)
        assertEquals("answers faded", 0, count(hasTestTag(StagePaths.ANSWERS)))
        assertEquals("the need row stays", 1, count(hasTestTag("stage.needs")))
        assertAllSixTiles()
    }

    @Test
    fun tappingFamilySendsItOnce() {
        val asked = mutableListOf<String>()
        showStage(spokenQuestion(), onNeed = { asked += it })

        rule.onNode(hasTestTag("stage.need.family"), useUnmergedTree = true).performClick()
        rule.waitForIdle()

        assertEquals(listOf("family"), asked)
    }

    @Test
    fun anOpenPanelDoesNotBuryTheRowOrItsOwnButtons() {
        showStage(pictureQuestion())

        assertEquals("the need row is still there", 1, count(hasTestTag("stage.needs")))
        assertAllSixTiles()

        // The picture panel opens folded, as a strip; opening it keeps the row and makes its tiles reachable.
        val strip = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.stage_show_answers)
        rule.onNode(hasContentDescription(strip), useUnmergedTree = true).assertExists().performClick()
        rule.waitForIdle()

        assertEquals("the panel is open, not folded", 1, count(hasTestTag("stage.needs"))) // still pinned above it
        assertEquals("the panel is open", 0, count(hasTestTag(StagePaths.EXIT)))
        val water = content.cards.getValue("water").label.getValue("en")
        assertEquals("panel tile and need tile", 2, count(hasContentDescription(water)))
        rule.onAllNodes(hasContentDescription(water), useUnmergedTree = true)[0].assertIsDisplayed()
        rule.onAllNodes(hasContentDescription(water), useUnmergedTree = true)[1].assertIsDisplayed().assertHasClickAction()
    }
}
