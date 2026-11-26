package app.signsetu

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
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
import app.signsetu.data.content.Question
import app.signsetu.data.session.Profile
import app.signsetu.data.session.Session
import app.signsetu.ui.components.LocalContentRepository
import app.signsetu.ui.components.LocalOnlineAssistant
import app.signsetu.ui.session.DONT_UNDERSTAND
import app.signsetu.ui.session.Mode
import app.signsetu.ui.session.Pending
import app.signsetu.ui.session.SessionUi
import app.signsetu.ui.session.Stage
import app.signsetu.ui.session.StagePaths
import app.signsetu.ui.session.StageTool
import app.signsetu.ui.session.StageToolBar
import app.signsetu.ui.theme.SignSetuTheme
import app.signsetu.ui.theme.SubjectTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/** The stage as one layout: one primary action, one tool bar, and at most three paths. */
@RunWith(AndroidJUnit4::class)
class StageToolsDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SignSetuApp
    private lateinit var content: ContentBundle

    @Before
    fun load() {
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

    private fun showStage(pending: Pending? = null) {
        val ui = ui(pending)
        rule.setContent {
            CompositionLocalProvider(LocalContentRepository provides app.container.content, LocalOnlineAssistant provides app.container.onlineAssistant) {
                SignSetuTheme {
                    SubjectTheme {
                        Box(Modifier) {
                            Stage(ui, content, tracker = null, cameraOn = false, gestureSpeed = 1f, askModes = CAPTION_MODES, {}, {}, {}, {}, {}, {}, {}, {}, onOfficer = null)
                        }
                    }
                }
            }
        }
    }

    /** A touch brings the faded controls up, as a subject's tap does on the call. */
    private fun revealControls() {
        rule.onRoot().performTouchInput { click() }
        rule.waitForIdle()
    }

    private fun count(matcher: SemanticsMatcher) = rule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().size

    /** Every distinct path tag on the stage: the row must expose exactly the three groups, never a fourth. */
    private fun pathTags(): Set<String> =
        rule.onAllNodes(SemanticsMatcher("any node") { true }, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .mapNotNull { node -> runCatching { node.config[SemanticsProperties.TestTag] }.getOrNull() }
            .filter { it.startsWith("stage.path.") }
            .toSet()

    private fun label(card: String): String = content.subjectText(card, "en") ?: app.getString(
        when (card) {
            "dont_know" -> R.string.stage_dont_know
            DONT_UNDERSTAND -> R.string.stage_dont_understand
            else -> R.string.stage_wrong
        },
    )

    @Test
    fun onePrimaryActionAndExactlyThreePaths() {
        showStage()
        revealControls()

        assertEquals("one primary action", 1, count(hasTestTag("stage.primary")))
        assertEquals("one tool bar", 1, count(hasTestTag("stage.toolbar")))
        assertEquals("answers, tools and exit, and no fourth path", StagePaths.all.toSet(), pathTags())
    }

    @Test
    fun everyToolIsShownAndTapped() {
        val taps = HashMap<StageTool, AtomicInteger>()
        StageTool.entries.forEach { taps[it] = AtomicInteger() }
        rule.setContent {
            SignSetuTheme {
                SubjectTheme {
                    Box(Modifier) {
                        StageToolBar(selected = null, available = StageTool.entries.toSet(), onSelect = { taps.getValue(it).incrementAndGet() })
                    }
                }
            }
        }

        assertEquals("one tool bar", 1, count(hasTestTag("stage.toolbar")))
        StageTool.entries.forEach { tool ->
            val button = rule.onNode(hasTestTag("stage.tool." + tool.key), useUnmergedTree = true)
            button.assertExists()
            button.performClick()
            rule.waitForIdle()
            assertEquals("one tap on " + tool.key, 1, taps.getValue(tool).get())
        }
    }

    @Test
    fun moreAnswersRevealsTheTrio() {
        showStage(Pending.Ask(Question(id = "row248", group = "safety", text = mapOf("en" to "Are you okay?"))))
        revealControls()

        val trio = listOf("dont_know", DONT_UNDERSTAND, "wrong").map(::label)
        assertEquals("the trio waits behind More", 0, trio.sumOf { count(hasContentDescription(it)) })

        rule.onNode(hasTestTag("stage.more"), useUnmergedTree = true).assertExists().performClick()
        rule.waitForIdle()

        trio.forEach { assertEquals("shown after More: " + it, 1, count(hasContentDescription(it))) }
    }
}
