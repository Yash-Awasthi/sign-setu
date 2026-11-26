package app.signsetu

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
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
import app.signsetu.ui.session.StagePaths
import app.signsetu.ui.session.StageTool
import app.signsetu.ui.session.StageToolSheet
import app.signsetu.ui.theme.SignSetuTheme
import app.signsetu.ui.theme.SubjectTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/** One sheet holds the whole toolset; the bar opens it and the stage keeps its three paths. */
@RunWith(AndroidJUnit4::class)
class StageToolSheetDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SignSetuApp
    private lateinit var content: ContentBundle

    @Before
    fun load() {
        content = runBlocking { app.container.content.load() }
    }

    private fun ui() = SessionUi(
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

    private fun showStage(onLanguage: (String) -> Unit = {}) {
        val ui = ui()
        rule.setContent {
            CompositionLocalProvider(LocalContentRepository provides app.container.content, LocalOnlineAssistant provides app.container.onlineAssistant) {
                SignSetuTheme {
                    SubjectTheme {
                        Box(Modifier) {
                            Stage(
                                ui, content, tracker = null, cameraOn = false, gestureSpeed = 1f, askModes = CAPTION_MODES,
                                onCard = {}, onYesNo = {}, onLanguage = onLanguage, onDrawing = {}, onWritten = {},
                                onSignFrame = {}, onStartSigning = {}, onDoneSigning = {}, onOfficer = null,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun showSheet(
        initial: StageTool,
        cards: List<String> = emptyList(),
        onLanguage: (String) -> Unit = {},
        onCard: (String) -> Unit = {},
        onDraw: (() -> Unit)? = null,
        onWrite: (() -> Unit)? = null,
        onExpress: () -> Unit = {},
    ) {
        rule.setContent {
            CompositionLocalProvider(LocalContentRepository provides app.container.content, LocalOnlineAssistant provides app.container.onlineAssistant) {
                SignSetuTheme {
                    SubjectTheme {
                        StageToolSheet(
                            open = true, initial = initial, content = content, lang = "en", pictureCards = cards,
                            onLanguage = onLanguage, onCard = onCard, onDraw = onDraw, onWrite = onWrite,
                            onExpress = onExpress, onDismiss = {},
                        )
                    }
                }
            }
        }
    }

    private fun revealControls() {
        rule.onRoot().performTouchInput { click() }
        rule.waitForIdle()
    }

    private fun openTool(tool: StageTool) {
        revealControls()
        rule.onNode(hasTestTag("stage.tool." + tool.key), useUnmergedTree = true).performClick()
        rule.waitForIdle()
    }

    private fun count(matcher: SemanticsMatcher) = rule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun pathTags(): Set<String> =
        rule.onAllNodes(SemanticsMatcher("any node") { true }, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .mapNotNull { node -> runCatching { node.config[SemanticsProperties.TestTag] }.getOrNull() }
            .filter { it.startsWith("stage.path.") }
            .toSet()

    @Test
    fun languageOpensTheWholeToolsetOnOneScreen() {
        showStage()
        openTool(StageTool.LANGUAGE)

        assertEquals("one sheet", 1, count(hasTestTag("stage.sheet")))
        StageTool.entries.forEach { tool ->
            assertEquals("section " + tool.key, 1, count(hasTestTag("stage.sheet." + tool.key)))
        }
    }

    @Test
    fun pickingALanguageReturnsItsCode() {
        var picked: String? = null
        showStage(onLanguage = { picked = it })
        val code = content.languages.first().code
        openTool(StageTool.LANGUAGE)

        assertNull("nothing picked yet", picked)
        rule.onNode(hasTestTag("stage.sheet.lang." + code), useUnmergedTree = true).performClick()
        rule.waitForIdle()
        assertEquals("the tapped code comes back", code, picked)
    }

    @Test
    fun picturesSectionShowsTheItemsCards() {
        val cards = content.cards.values.filter { it.label?.get("en") != null }.take(2).map { it.id }
        val tapped = AtomicInteger()
        showSheet(StageTool.PICTURES, cards = cards, onCard = { tapped.incrementAndGet() })

        assertEquals("one pictures section", 1, count(hasTestTag("stage.sheet.pictures")))
        cards.forEach { id ->
            val label = content.cards[id]?.label?.get("en")
            assertEquals("card on screen: " + id, 1, count(hasContentDescription(label.orEmpty())))
        }
        rule.onNode(hasContentDescription(content.cards.getValue(cards.first()).label.getValue("en")), useUnmergedTree = true).performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals("a picture tap answers its card", 1, tapped.get())
    }

    @Test
    fun drawingAndTypingSectionsRunTheirTools() {
        val draws = AtomicInteger()
        val writes = AtomicInteger()
        showSheet(StageTool.DRAWING, onDraw = { draws.incrementAndGet() }, onWrite = { writes.incrementAndGet() })

        assertEquals("one drawing section", 1, count(hasTestTag("stage.sheet.drawing")))
        assertEquals("one typing section", 1, count(hasTestTag("stage.sheet.typing")))
        rule.onNode(hasTestTag("stage.sheet.drawing.action"), useUnmergedTree = true).performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(1, draws.get())
        rule.onNode(hasTestTag("stage.sheet.typing.action"), useUnmergedTree = true).performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(1, writes.get())
    }

    @Test
    fun closingTheSheetLeavesTheStageUnchanged() {
        showStage()
        openTool(StageTool.LANGUAGE)
        rule.onNode(hasTestTag("stage.sheet.close"), useUnmergedTree = true).performClick()
        rule.waitForIdle()

        assertEquals("the sheet is gone", 0, count(hasTestTag("stage.sheet")))
        revealControls()
        assertEquals("one primary action still", 1, count(hasTestTag("stage.primary")))
        assertEquals("the same three paths", StagePaths.all.toSet(), pathTags())
    }
}
