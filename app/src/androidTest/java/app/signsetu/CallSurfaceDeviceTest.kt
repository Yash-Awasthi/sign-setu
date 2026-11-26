package app.signsetu

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
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
import app.signsetu.ui.session.Pending
import app.signsetu.ui.session.SessionUi
import app.signsetu.ui.session.Stage
import app.signsetu.ui.theme.SignSetuTheme
import app.signsetu.ui.theme.SubjectTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The call reads as a call: participant tiles on a tap, a caption that stays, controls that fade. */
@RunWith(AndroidJUnit4::class)
class CallSurfaceDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as SignSetuApp
    private lateinit var content: ContentBundle

    @Before
    fun load() {
        // cameraOn draws the real self view; the permission is granted so no system dialog covers the call.
        instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.CAMERA)
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

    private fun showStage() {
        val ui = ui()
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
    }

    private fun count(tag: String) = rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

    /** A tap on the call, as a subject gives it, brings the faded chrome up. */
    private fun revealControls() {
        rule.onRoot().performTouchInput { click() }
        rule.waitForIdle()
    }

    @Test
    fun theCallOpensOnTheAvatarAndCaptionAlone() {
        showStage()
        rule.waitForIdle()

        assertEquals("no chrome before a touch", 0, count("call.header"))
        assertEquals("the caption is on the call from the start", 1, count("call.caption"))
    }

    @Test
    fun aTapBringsTheCallChrome() {
        showStage()
        revealControls()

        assertEquals("header up", 1, count("call.header"))
        assertEquals("the avatar tile", 1, count("call.tile.avatar"))
        assertEquals("the person tile", 1, count("call.tile.person"))
        assertEquals("the controls are up", 1, count("call.controls"))

        val avatarName = app.getString(R.string.call_avatar_name)
        val personName = app.getString(R.string.call_person_name)
        assertEquals(1, rule.onAllNodes(hasTestTag("call.tile.avatar") and hasContentDescription(avatarName), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(1, rule.onAllNodes(hasTestTag("call.tile.person") and hasContentDescription(personName), useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun theChromeFadesWhileTheCaptionStays() {
        rule.mainClock.autoAdvance = false
        showStage()
        rule.mainClock.advanceTimeBy(100)
        assertEquals("no header before a touch", 0, count("call.header"))

        // A touch brings the tiles up; the caption was there all along.
        rule.onRoot().performTouchInput { click() }
        rule.mainClock.advanceTimeBy(200)
        assertEquals("header after a touch", 1, count("call.header"))
        assertEquals("controls after a touch", 1, count("call.controls"))
        assertEquals("the caption", 1, count("call.caption"))

        // Past the idle window the chrome leaves and the call itself does not.
        rule.mainClock.advanceTimeBy(6_000)
        assertEquals("header faded", 0, count("call.header"))
        assertEquals("controls faded", 0, count("call.controls"))
        assertEquals("the caption stays on the call", 1, count("call.caption"))
    }

    @Test
    fun theTwoTilesNameTwoPeople() {
        showStage()
        revealControls()

        val avatarName = app.getString(R.string.call_avatar_name)
        val personName = app.getString(R.string.call_person_name)
        assertNotEquals("the two participants are named differently", avatarName, personName)

        val names = rule.onAllNodes(
            SemanticsMatcher("participant tile") {
                it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("call.tile.") == true
            },
            useUnmergedTree = true,
        ).fetchSemanticsNodes().map { it.config[SemanticsProperties.ContentDescription].joinToString(" ") }

        assertEquals("two tiles", 2, names.size)
        assertEquals("two distinct people", setOf(avatarName, personName), names.toSet())
    }
}
