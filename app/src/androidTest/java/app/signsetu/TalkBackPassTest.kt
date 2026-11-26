package app.signsetu

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.data.content.CAPTION_MODES
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.ContentRepository
import app.signsetu.data.session.Profile
import app.signsetu.data.session.Session
import app.signsetu.ui.components.LocalContentRepository
import app.signsetu.ui.components.LocalOnlineAssistant
import app.signsetu.ui.session.BoardSheet
import app.signsetu.ui.session.FullChildMuteCommunicationBoard
import app.signsetu.ui.session.Mode
import app.signsetu.ui.session.Pending
import app.signsetu.ui.session.RuleSteps
import app.signsetu.ui.session.SessionUi
import app.signsetu.ui.session.Stage
import app.signsetu.ui.theme.SignSetuTheme
import app.signsetu.ui.theme.SubjectTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The spoken pass over the stage: every control has words for TalkBack, the figure announces itself, and the
 * question is read before the answers under it. Nothing here needs sight. */
class TalkBackPassTest {

    private enum class Screen { GREETING, CHOICES, NUMBERS, DRAWING, LANG_FINDER, RULE, LESSON, EXPRESS }

    private data class Step(val screen: Screen, val lang: String)

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var repo: ContentRepository
    private lateinit var content: ContentBundle
    private var step by mutableStateOf(Step(Screen.GREETING, "en"))
    private var boardLang by mutableStateOf("en")

    @Before
    fun load() {
        repo = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SignSetuApp).container.content
        content = runBlocking { repo.load() }
    }

    /** One frame at a time: the character's idle breathing never settles, so the test clock is advanced by hand. */
    private fun settle() {
        repeat(2) { rule.mainClock.advanceTimeByFrame() }
        rule.waitForIdle()
    }

    private val onlineAssistant = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SignSetuApp).container.onlineAssistant

    private fun show() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalContentRepository provides repo, LocalOnlineAssistant provides onlineAssistant) {
                SignSetuTheme {
                    SubjectTheme {
                        key(step) {
                            when (step.screen) {
                                Screen.EXPRESS -> FullChildMuteCommunicationBoard(content, step.lang, null, {}, {})
                                else -> Box(Modifier) { Stage(ui(step), content, tracker = null, cameraOn = false, gestureSpeed = 1f, askModes = CAPTION_MODES, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun ui(step: Step): SessionUi = SessionUi(
        session = Session("test", 0L, if (step.screen == Screen.LESSON) Profile.NOT_SURE else Profile.YOUNG_CHILD, step.lang),
        selectedGroup = "safety",
        sensitiveUnlocked = false,
        pending = when (step.screen) {
            Screen.GREETING, Screen.EXPRESS -> null
            Screen.CHOICES -> ask("h1")
            Screen.NUMBERS -> ask("i2")
            Screen.DRAWING -> Pending.Drawing
            Screen.LANG_FINDER -> Pending.LanguageFinder
            Screen.RULE -> Pending.GroundRules(RuleSteps.lastIndex)
            Screen.LESSON -> ask("a5")
        },
        mode = Mode.SHOW,
        rotated = false,
        answeredCard = null,
        answeredYes = null,
        chosenLanguage = null,
        events = emptyList(),
        reading = null,
        signed = emptyMap(),
    )

    private fun ask(id: String) = Pending.Ask(content.questions.first { it.id == id })

    /** What TalkBack says for a node: its description, then its text. */
    private fun spoken(node: SemanticsNode): String {
        val parts = mutableListOf<String>()
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.let { parts += it }
        node.config.getOrNull(SemanticsProperties.Text)?.let { t -> parts += t.map { s -> s.text } }
        node.config.getOrNull(SemanticsProperties.EditableText)?.let { parts += it.text }
        node.config.getOrNull(SemanticsProperties.StateDescription)?.let { parts += it }
        return parts.filter { it.isNotBlank() }.joinToString(" ").trim()
    }

    private fun walk(node: SemanticsNode, out: MutableList<SemanticsNode>) {
        out += node
        node.children.forEach { walk(it, out) }
    }

    private fun screenNodes(): List<SemanticsNode> = mutableListOf<SemanticsNode>().also { walk(rule.onAllNodes(isRoot()).onLast().fetchSemanticsNode(), it) }

    /** Every control the finger can press has words, so nothing is a silent "button". */
    private fun assertEveryControlSpeaks(where: String) {
        val clickables = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue("$where offers controls to press", clickables.isNotEmpty())
        for (n in clickables) {
            assertTrue("$where: a control at ${n.boundsInRoot} has no words: ${n.config}", spoken(n).isNotBlank())
        }
    }

    private fun assertFigureSpeaks(where: String) {
        val name = rule.activity.getString(R.string.stage_character)
        val nodes = screenNodes()
        assertTrue("$where: the character is one spoken node", nodes.any { n -> n.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(name) == true })
    }

    @Test
    fun everyStageStateInEveryLanguageAnnouncesItself() {
        show()
        settle()
        for (language in listOf("en", "hi", "bn")) {
            for (screen in Screen.entries) {
                step = Step(screen, language)
                settle()
                val where = "$language $screen"
                assertEveryControlSpeaks(where)
                if (screen != Screen.EXPRESS) assertFigureSpeaks(where)
            }
        }
    }

    @Test
    fun everyQuestionIsReadBeforeItsAnswers() {
        show()
        settle()
        for (language in listOf("en", "hi", "bn")) {
            step = Step(Screen.CHOICES, language)
            settle()
            // A young child meets the question first; the officer opens the answers when they ask to see them.
            val showAnswers = rule.activity.getString(R.string.stage_show_answers)
            if (rule.onAllNodes(hasContentDescription(showAnswers)).fetchSemanticsNodes().isNotEmpty()) {
                rule.onNodeWithContentDescription(showAnswers).performClick()
                settle()
            }
            val q = content.questions.first { it.id == "h1" }
            val nodes = screenNodes()
            val questionAt = nodes.indexOfFirst { spoken(it).contains(q.text[language] ?: error("no $language wording")) }
            assertTrue("$language: the question is in the spoken order", questionAt >= 0)
            // A tile announces its own word (ChoiceTile's content description), and the same word is drawn on it.
            fun tileAt(label: String) = nodes.indexOfFirst { n ->
                n.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.trim() == label } == true
            }
            val labels = q.cards.mapNotNull { content.cards[it]?.label?.get(language) }
            val firstCard = labels.map(::tileAt).firstOrNull { it >= 0 }
            assertTrue("$language: one of $labels is an answer tile; spoken descriptions were " +
                nodes.mapNotNull { n -> n.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() }, firstCard != null)
            assertTrue("$language: the question comes before its answers ($questionAt < $firstCard)", questionAt < firstCard!!)
        }
    }

    @Test
    fun thePictureBoardAnnouncesItsCards() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalContentRepository provides repo, LocalOnlineAssistant provides onlineAssistant) {
                SignSetuTheme { SubjectTheme { key(boardLang) { BoardSheet(content, boardLang, emptyList(), setOf("water"), {}, {}, {}) } } }
            }
        }
        settle()
        for (language in listOf("en", "hi", "bn")) {
            boardLang = language
            settle()
            assertEveryControlSpeaks("board $language")
        }
    }
}
