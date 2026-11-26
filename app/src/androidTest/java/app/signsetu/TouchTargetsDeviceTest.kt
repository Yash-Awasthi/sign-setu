package app.signsetu

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.data.content.CAPTION_MODES
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.session.Profile
import app.signsetu.data.session.Session
import app.signsetu.data.session.SessionDatabase
import app.signsetu.data.session.SessionRepository
import app.signsetu.ui.components.LocalContentRepository
import app.signsetu.ui.components.LocalOnlineAssistant
import app.signsetu.ui.session.FullChildMuteCommunicationBoard
import app.signsetu.ui.session.Mode
import app.signsetu.ui.session.OfficerPane
import app.signsetu.ui.session.Pending
import app.signsetu.ui.session.RuleSteps
import app.signsetu.ui.session.SessionUi
import app.signsetu.ui.session.SessionViewModel
import app.signsetu.ui.session.Stage
import app.signsetu.ui.theme.SignSetuTheme
import app.signsetu.ui.theme.SubjectTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The session surfaces are measured, not eyeballed: every clickable, toggleable or focusable node's touch
 * bounds, read from the semantics tree and converted with the device density, is at least 48 dp in both
 * directions. The person's screens also answer one reach rule, stated exactly at the assertion.
 */
@RunWith(AndroidJUnit4::class)
class TouchTargetsDeviceTest {

    private enum class Screen {
        GREETING, CHOICES, NUMBERS, DRAWING, LANG_FINDER, RULE, LESSON, PAUSE, EXPRESS, CALL_CHROME,
    }

    private data class Step(val screen: Screen, val lang: String = "en")

    private data class Control(
        val screen: String,
        val name: String,
        val tag: String,
        val w: Float,
        val h: Float,
        val cx: Float,
        val cy: Float,
        val scrolls: Boolean,
    )

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as SignSetuApp
    private val density get() = app.resources.displayMetrics.density
    private lateinit var content: ContentBundle
    private var step by mutableStateOf(Step(Screen.GREETING))

    @Before
    fun load() {
        content = runBlocking { app.container.content.load() }
    }

    private fun settle() {
        // Two frames compose the change; the rest finishes the sheet and panel enter animations.
        repeat(2) { rule.mainClock.advanceTimeByFrame() }
        rule.mainClock.advanceTimeBy(700L)
        rule.waitForIdle()
    }

    private fun show() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalContentRepository provides app.container.content, LocalOnlineAssistant provides app.container.onlineAssistant) {
                SignSetuTheme {
                    SubjectTheme {
                        key(step) {
                            if (step.screen == Screen.EXPRESS) {
                                FullChildMuteCommunicationBoard(content, step.lang, null, {}, {})
                            } else {
                                Box(Modifier) {
                                    Stage(
                                        ui(step), content, tracker = null, cameraOn = false, gestureSpeed = 1f,
                                        askModes = CAPTION_MODES, {}, {}, {}, {}, {}, {}, {}, {}, {},
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun ask(id: String) = Pending.Ask(content.questions.first { it.id == id })

    private fun ui(step: Step): SessionUi = SessionUi(
        session = Session("test", 0L, if (step.screen == Screen.LESSON) Profile.YOUNG_CHILD else Profile.NOT_SURE, step.lang),
        selectedGroup = "safety",
        sensitiveUnlocked = false,
        pending = when (step.screen) {
            Screen.GREETING, Screen.EXPRESS, Screen.CALL_CHROME -> null
            Screen.CHOICES -> ask("h1")
            Screen.NUMBERS -> ask("i2")
            Screen.DRAWING -> Pending.Drawing
            Screen.LANG_FINDER -> Pending.LanguageFinder
            Screen.RULE -> Pending.GroundRules(RuleSteps.lastIndex)
            Screen.LESSON -> ask("a5")
            // The break layer covers the stage; its own Ready button is the only way on.
            Screen.PAUSE -> ask("h1")
        },
        mode = Mode.SHOW,
        rotated = false,
        answeredCard = if (step.screen == Screen.PAUSE) "break" else null,
        answeredYes = null,
        chosenLanguage = null,
        events = emptyList(),
        reading = null,
        signed = emptyMap(),
    )

    private fun isAction(node: SemanticsNode): Boolean =
        node.config.getOrNull(SemanticsActions.OnClick) != null ||
            node.config.getOrNull(SemanticsActions.OnLongClick) != null ||
            node.config.getOrNull(SemanticsProperties.ToggleableState) != null ||
            node.config.getOrNull(SemanticsActions.RequestFocus) != null

    private fun nameOf(node: SemanticsNode): String = (
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
            ?: node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
            ?: node.config.getOrNull(SemanticsProperties.EditableText)?.text
            ?: node.config.getOrNull(SemanticsProperties.TestTag)
            ?: "?"
        ).trim().replace('\n', ' ')

    /** Every action in the current composition, in dp, with its centre as a fraction of the window. */
    private fun controls(screen: String): List<Control> {
        val root = rule.onAllNodes(isRoot(), useUnmergedTree = true).onLast().fetchSemanticsNode()
        val width = root.size.width.toFloat()
        val height = root.size.height.toFloat()
        val out = mutableListOf<Control>()
        fun walk(node: SemanticsNode, scrolls: Boolean) {
            val scrollsHere = scrolls ||
                node.config.getOrNull(SemanticsActions.ScrollBy) != null ||
                node.config.getOrNull(SemanticsActions.ScrollToIndex) != null
            if (isAction(node)) {
                val b = node.touchBoundsInRoot
                if (b.width > 0f && b.height > 0f) {
                    out += Control(
                        screen = screen,
                        name = nameOf(node),
                        tag = node.config.getOrNull(SemanticsProperties.TestTag) ?: "",
                        w = b.width / density,
                        h = b.height / density,
                        cx = b.center.x / width,
                        cy = b.center.y / height,
                        scrolls = scrollsHere,
                    )
                }
            }
            node.children.forEach { walk(it, scrollsHere) }
        }
        walk(root, false)
        return out
    }

    private fun report(label: String, rows: List<Control>) {
        println("TOUCH " + label)
        rows.sortedWith(compareBy({ it.screen }, { it.cy })).forEach {
            println("  %-10s %-26s %7.1f x %7.1f dp  at %5.1f%% x %5.1f%%  %s%s".format(
                it.screen, it.name.take(26), it.w, it.h, it.cx * 100, it.cy * 100, it.tag, if (it.scrolls) " [scroll]" else "",
            ))
        }
    }

    private fun measurePersonScreens(): List<Control> {
        show()
        settle()
        val rows = mutableListOf<Control>()
        for (screen in Screen.entries) {
            step = Step(screen)
            settle()
            if (screen == Screen.CALL_CHROME) {
                // The call chrome is up only after a touch, as it appears for a person in the call.
                rule.onRoot().performTouchInput { click() }
                settle()
            }
            rows += controls(screen.name)
        }
        return rows
    }

    @Test
    fun everyInSessionActionHoldsItsTouchFloor() {
        val rows = measurePersonScreens()
        val counts = Screen.entries.map { s -> s.name + "=" + rows.count { it.screen == s.name } }
        report("PERSON", rows)
        println("TOUCH PERSON COUNTS " + counts.joinToString(" "))
        println("TOUCH PERSON TOTAL " + rows.size)

        val small = rows.filter { it.w < 48f - 0.05f || it.h < 48f - 0.05f }
        println("TOUCH PERSON UNDER 48 DP " + small.size)
        small.forEach { println("  UNDER " + it.screen + " | " + it.name + " | " + it.tag + " | %.1f x %.1f".format(it.w, it.h)) }
        assertTrue(
            "in-session actions under 48 dp in one direction (" + small.size + " of " + rows.size + "):\n" +
                small.joinToString("\n") { "${it.screen}: ${it.name} (${it.tag}) ${"%.1f".format(it.w)} x ${"%.1f".format(it.h)} dp" },
            small.isEmpty(),
        )
    }

    @Test
    fun everyPersonActionSitsInTheReachRule() {
        val rows = measurePersonScreens()
        report("REACH", rows)
        println("TOUCH REACH TOTAL " + rows.size)
        // reach rule: on ci34 (411 x 914 dp) every action on the person's in-session screens has its touch
        // centre inside the window inset 16 dp from the left, right and bottom edges, and the centre lies
        // either above 25% of the height (the chrome pinned to the top strip), inside a scrollable list
        // (the thumb scrolls it down), or below 30% of the height (the bottom 70%); nothing is left between.
        val edge = 16f / 411f
        val bad = rows.filter {
            it.cx < edge || it.cx > 1f - edge || it.cy > 1f - 16f / 914f ||
                (it.cy >= 0.25f && it.cy < 0.30f && !it.scrolls)
        }
        println("TOUCH REACH BAD " + bad.size)
        bad.forEach { println("  BAD " + it.screen + " | " + it.name + " | " + it.tag + " | %.1f%% x %.1f%%".format(it.cx * 100, it.cy * 100)) }
        assertTrue(
            "actions outside the reach rule (" + bad.size + " of " + rows.size + "):\n" +
                bad.joinToString("\n") { "${it.screen}: ${it.name} (${it.tag}) at ${"%.1f".format(it.cx * 100)}% x ${"%.1f".format(it.cy * 100)}%" },
            bad.isEmpty(),
        )
    }

    @Test
    fun officerActionsHoldTheirTouchFloor() {
        val db = SessionDatabase.open(app, ByteArray(32) { (it + 41).toByte() }, fileName = "touch-targets-test.db")
        val repo = SessionRepository(db.dao())
        var watcher: CoroutineScope? = null
        try {
            val id = runBlocking { repo.start(Profile.NOT_SURE, "en") }
            lateinit var vm: SessionViewModel
            val ui = runBlocking {
                val container = app.container
                val loaded = container.content.load()
                instrumentation.runOnMainSync { vm = SessionViewModel(id, repo, loaded, "en", container.report) }
                watcher = CoroutineScope(Dispatchers.Default).apply { launch { vm.ui.collect {} } }
                withTimeout(10_000) { vm.ui.first { it != null }!! }
            }
            rule.setContent {
                CompositionLocalProvider(LocalContentRepository provides app.container.content, LocalOnlineAssistant provides app.container.onlineAssistant) {
                    SignSetuTheme {
                        OfficerPane(ui, vm, "en", emptySet(), {}, null, null, {}, {}, historyOff = false)
                    }
                }
            }
            rule.waitForIdle()
            val pane = controls("OFFICER")
            // The second page carries the question groups, phrases and tools; it is reached on the officer's tap.
            rule.onNodeWithText(app.getString(R.string.officer_tab_more)).performClick()
            rule.waitForIdle()
            val more = controls("OFFICER_MORE")
            // The wrap-up dialog is the last surface of the session; its buttons answer the officer too.
            rule.onNode(hasTestTag("officer.end")).performClick()
            rule.waitForIdle()
            val wrap = controls("WRAP_UP")
            report("OFFICER", pane + more + wrap)
            println("TOUCH OFFICER COUNTS OFFICER=" + pane.size + " OFFICER_MORE=" + more.size + " WRAP_UP=" + wrap.size)
            println("TOUCH OFFICER TOTAL " + (pane.size + more.size + wrap.size))
            val rows = pane + more + wrap
            val small = rows.filter { it.w < 48f - 0.05f || it.h < 48f - 0.05f }
            println("TOUCH OFFICER UNDER 48 DP " + small.size)
            small.forEach { println("  UNDER " + it.screen + " | " + it.name + " | " + it.tag + " | %.1f x %.1f".format(it.w, it.h)) }
            assertTrue(
                "officer actions under 48 dp (" + small.size + " of " + rows.size + "):\n" +
                    small.joinToString("\n") { "${it.screen}: ${it.name} (${it.tag}) ${"%.1f".format(it.w)} x ${"%.1f".format(it.h)} dp" },
                small.isEmpty(),
            )
        } finally {
            watcher?.cancel()
            db.close()
            app.deleteDatabase("touch-targets-test.db")
        }
    }
}