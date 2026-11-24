package app.signsetu

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.data.session.Profile
import app.signsetu.data.session.SessionDatabase
import app.signsetu.data.session.SessionRepository
import app.signsetu.ui.components.LocalContentRepository
import app.signsetu.ui.components.LocalOnlineAssistant
import app.signsetu.ui.components.SubjectSpeech
import app.signsetu.ui.session.OfficerPane
import app.signsetu.ui.session.SessionUi
import app.signsetu.ui.session.SessionViewModel
import app.signsetu.ui.theme.SignSetuTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The owner's rule: the mute control for the person's speech is on the officer's own surface, never behind a menu
 * ("not deep hidden to the policeman"). Renders that surface with no menu opened and mutes from there. */
@RunWith(AndroidJUnit4::class)
class OfficerMuteButtonDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as SignSetuApp
    private lateinit var db: SessionDatabase
    private lateinit var repo: SessionRepository
    private lateinit var watcher: CoroutineScope
    private lateinit var id: String

    private fun str(id: Int) = app.getString(id)

    @Before
    fun open() {
        db = SessionDatabase.open(app, ByteArray(32) { (it + 11).toByte() }, fileName = "mute-test.db")
        repo = SessionRepository(db.dao())
        id = runBlocking { repo.start(Profile.NOT_SURE, "en") }
        SubjectSpeech.set(false)
    }

    @After
    fun close() {
        SubjectSpeech.set(false)
        if (this::watcher.isInitialized) watcher.cancel()
        db.close()
        app.deleteDatabase("mute-test.db")
    }

    @Test
    fun theMuteButtonIsOnTheOfficerSurfaceAndMutesTheSpeech() = runBlocking<Unit> {
        val container = app.container
        val content = runBlocking { container.content.load() }
        lateinit var vm: SessionViewModel
        instrumentation.runOnMainSync { vm = SessionViewModel(id, repo, content, "en", container.report) }
        watcher = CoroutineScope(Dispatchers.Default).apply { launch { vm.ui.collect {} } }
        val ui: SessionUi = withTimeout(10_000) { vm.ui.first { it != null }!! }

        rule.setContent {
            CompositionLocalProvider(LocalContentRepository provides container.content, LocalOnlineAssistant provides container.onlineAssistant) {
                SignSetuTheme {
                    OfficerPane(ui, vm, "en", emptySet(), {}, null, null, {}, {}, historyOff = false)
                }
            }
        }
        rule.waitForIdle()

        // The button is on the surface as it opens: no menu was opened to find it, and the menu's own rows are absent.
        val mute = hasContentDescription(str(R.string.speech_mute))
        rule.onNode(mute).assertExists()
        assertFalse("the More menu is not open", rule.onAllNodes(hasContentDescription(str(R.string.officer_sign_language))).fetchSemanticsNodes().isNotEmpty())
        assertFalse("nothing is muted to begin with", SubjectSpeech.muted.value)

        rule.onNode(mute).performClick()
        rule.waitForIdle()
        assertTrue("the mute takes effect at once", SubjectSpeech.muted.value)
        rule.onNode(hasContentDescription(str(R.string.speech_unmute))).assertExists()

        rule.onNode(hasContentDescription(str(R.string.speech_unmute))).performClick()
        rule.waitForIdle()
        assertFalse("the same button brings it back", SubjectSpeech.muted.value)
        rule.onNode(mute).assertExists()
    }
}
