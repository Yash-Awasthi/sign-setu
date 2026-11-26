package app.signsetu

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.Question
import app.signsetu.data.session.Profile
import app.signsetu.data.session.Role
import app.signsetu.ui.session.SuggestionStrip
import app.signsetu.ui.session.nextQuestions
import app.signsetu.ui.theme.SignSetuTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The strip offers the next questions for the selected role, one tap each, and nothing when there is nothing. */
@RunWith(AndroidJUnit4::class)
class SuggestionStripDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SignSetuApp
    private lateinit var content: ContentBundle
    private val profile = Profile.NOT_SURE

    @Before
    fun load() {
        content = runBlocking { app.container.content.load() }
    }

    private fun show(questions: List<Question>, onAsk: (Question) -> Unit = {}) {
        rule.setContent {
            SignSetuTheme { SuggestionStrip(questions, "en", onAsk = onAsk) }
        }
        rule.waitForIdle()
    }

    private fun chips() = rule.onAllNodes(
        SemanticsMatcher("suggestion chip") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("stage.suggestion.") == true
        },
        useUnmergedTree = true,
    ).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }

    /** A group with three or more questions this role may be asked, so the strip fills without topping up. */
    private fun fullGroup(role: Role): String =
        content.groupsFor(role).map { it.id }.first { content.questionsIn(it, role, profile).size >= 3 }

    @Test
    fun threeDistinctSuggestionsAndNoneAlreadyAsked() {
        val group = fullGroup(Role.VICTIM)
        val first = nextQuestions(content, group, Role.VICTIM, profile, emptySet())
        val asked = setOf(first.first().id)
        val strip = nextQuestions(content, group, Role.VICTIM, profile, asked)
        show(strip)

        assertEquals("three suggestions", 3, strip.size)
        val tags = chips()
        assertEquals("three chips drawn", 3, tags.size)
        assertEquals("distinct chips", 3, tags.toSet().size)
        assertTrue("no asked id", strip.none { it.id in asked })
        assertTrue("the asked chip is not drawn", tags.none { it == "stage.suggestion." + asked.first() })
    }

    @Test
    fun tappingAChipAsksThatQuestionOnce() {
        val group = fullGroup(Role.VICTIM)
        val strip = nextQuestions(content, group, Role.VICTIM, profile, emptySet())
        val asked = mutableListOf<Question>()
        show(strip) { asked += it }

        val first = strip.first()
        rule.onNode(hasTestTag("stage.suggestion." + first.id), useUnmergedTree = true).performClick()
        rule.waitForIdle()

        assertEquals("one ask", listOf(first), asked)
    }

    @Test
    fun theRoleDecidesWhatIsOffered() {
        // "talk" has enough for the accused, so every chip must be askable in that group.
        val talk = nextQuestions(content, "talk", Role.ACCUSED, profile, emptySet())
        val askable = content.questionsIn("talk", Role.ACCUSED, profile).map { it.id }.toSet()
        assertEquals("three for the accused", 3, talk.size)
        assertTrue("all askable for the accused", talk.all { it.id in askable })

        // In the shipped content a role-restricted item never sits among a group's first three, so the first row is
        // the same for both roles; that fact is asserted, not assumed.
        content.groups.forEach { g ->
            assertEquals(
                "first row for " + g.id,
                nextQuestions(content, g.id, Role.VICTIM, profile, emptySet()).map { it.id },
                nextQuestions(content, g.id, Role.ACCUSED, profile, emptySet()).map { it.id },
            )
        }

        // Ask past those shared three and the victim-only school items surface, while an accused is never offered them.
        val shared = nextQuestions(content, "school", Role.VICTIM, profile, emptySet()).map { it.id }.toSet()
        val victimOnly = content.questionsIn("school", Role.VICTIM, profile).filter { it.roles.isNotEmpty() }.map { it.id }.toSet()
        assertTrue("the school group keeps victim-only items", victimOnly.isNotEmpty())
        val victimNext = nextQuestions(content, "school", Role.VICTIM, profile, shared)
        val accusedNext = nextQuestions(content, "school", Role.ACCUSED, profile, shared)
        assertTrue("a victim is offered them", victimNext.any { it.id in victimOnly })
        assertTrue("an accused is not", accusedNext.none { it.id in victimOnly })
        assertNotEquals("the roles diverge once the shared three are asked", victimNext.map { it.id }, accusedNext.map { it.id })
    }

    @Test
    fun nothingToSuggestDrawsNothing() {
        show(emptyList())

        assertEquals("no strip", 0, rule.onAllNodes(hasTestTag("stage.suggestions"), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertTrue("no chips", chips().isEmpty())
    }
}
