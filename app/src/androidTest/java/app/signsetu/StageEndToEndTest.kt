package app.signsetu

import android.Manifest
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.camera.LandmarkFrame
import app.signsetu.camera.LandmarkTracker
import app.signsetu.camera.SkeletonOverlay
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.SessionDatabase
import app.signsetu.data.session.SessionRepository
import app.signsetu.data.settings.AppSettings
import app.signsetu.ui.SessionRoute
import app.signsetu.ui.components.LocalOnlineAssistant
import app.signsetu.ui.components.LocalContentRepository
import app.signsetu.ui.theme.SignSetuTheme
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The real session screen on a device: an encrypted database, the bundled content and the emulator's camera,
 * with only the landmark model replaced. */
@RunWith(AndroidJUnit4::class)
class StageEndToEndTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as SignSetuApp
    private lateinit var db: SessionDatabase

    /** A face whose nose bobs while [nodding], and a body, so the overlay analyses at its usual rate. */
    private class NoddingTracker : LandmarkTracker {
        @Volatile var nodding = false
        @Volatile var frames = 0
        override fun track(frame: Bitmap, timestampMs: Long): LandmarkFrame {
            val face = MutableList(468) { Offset(0.5f, 0.5f) }
            face[33] = Offset(0.4f, 0.4f)
            face[263] = Offset(0.6f, 0.4f)
            face[1] = Offset(0.5f, if (nodding && frames % 2 == 0) 0.62f else 0.5f)
            frames++
            return LandmarkFrame(timestampMs, face = face, pose = List(33) { Offset(0.5f, 0.6f) })
        }
    }

    @Before
    fun setUp() {
        shell("settings put global animator_duration_scale 0")
        instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.CAMERA)
        System.loadLibrary("sqlcipher")
        db = Room.inMemoryDatabaseBuilder(app, SessionDatabase::class.java)
            .openHelperFactory(SupportOpenHelperFactory(ByteArray(32) { 7 }))
            .build()
    }

    @After
    fun tearDown() {
        // The session pins the screen; a pinned task cannot be finished, so the rule could not close the activity.
        shell("am task lock stop")
        SkeletonOverlay.on.value = true
        db.close()
        shell("settings put global animator_duration_scale 1")
    }

    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).close()

    private fun str(id: Int) = app.getString(id)

    private fun count(matcher: SemanticsMatcher) = rule.onAllNodes(matcher).fetchSemanticsNodes().size

    /** Waits on the wall clock and moves the frame clock along with it: the camera feed and the character's frame
     * loops never let an auto-advancing clock report the stage idle (ComposeNotIdle). */
    private fun until(timeout: Long = 15_000, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeout
        var last = SystemClock.uptimeMillis()
        while (!condition()) {
            val now = SystemClock.uptimeMillis()
            if (now > end) {
                rule.onRoot(useUnmergedTree = true).printToLog("E2E")
                throw AssertionError("condition not met within $timeout ms")
            }
            rule.mainClock.advanceTimeBy(maxOf(now - last, 16L))
            last = now
            SystemClock.sleep(10)
        }
    }

    private fun waitFor(matcher: SemanticsMatcher, timeout: Long = 15_000) = until(timeout) { count(matcher) > 0 }

    /** Taps once the node is there and enabled, then lets the screen recompose. */
    private fun tap(matcher: SemanticsMatcher) {
        waitFor(matcher and isEnabled())
        rule.onNode(matcher).performClick()
        rule.mainClock.advanceTimeBy(100)
    }

    @Test
    fun aQuestionTheNodAndTheRecordOnTheRealScreens() {
        val container = app.container
        val content = runBlocking { container.content.load() }
        val repo = SessionRepository(db.dao())
        val id = runBlocking { repo.start(Profile.NOT_SURE, "en") }
        fun answers() = runBlocking { repo.eventsNow(id) }.filter { it.type == EventType.ANSWER_CARD }
        val tracker = NoddingTracker()
        val settings = AppSettings(firstRunDone = true)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalContentRepository provides container.content, LocalOnlineAssistant provides container.onlineAssistant) {
                SignSetuTheme {
                    SessionRoute(id, repo, content, settings, container.report, tracker, null, null, {}, {}, container, null, null)
                }
            }
        }

        // The session opens on the stage with the ground rules; the officer takes the phone back by the handle.
        val handle = hasContentDescription(str(R.string.show_officer))
        waitFor(handle)
        rule.onNode(handle).performTouchInput { longClick(durationMillis = 1_500) }

        // 1. The officer finds "Does it hurt anywhere?" by searching the More page and shows it.
        val h1 = content.question("h1")!!
        tap(hasText(str(R.string.officer_tab_more)))
        waitFor(hasSetTextAction())
        rule.onNode(hasSetTextAction()).performTextInput("hurt")
        tap(hasText(h1.text.getValue("en")))
        tap(hasContentDescription(str(R.string.officer_show)))

        // 2. On the stage the subject taps two pictures; neither follows a gesture.
        val pictures = h1.cards.associateWith { hasContentDescription(content.cards.getValue(it).label.get("en")!!) }
        // While the character signs, the answers wait folded under a strip the person can tap.
        val strip = hasContentDescription(str(R.string.stage_show_answers))
        until { pictures.values.any { count(it) == 1 } || count(strip) > 0 }
        if (count(strip) > 0) tap(strip)
        until { pictures.values.any { count(it) == 1 } }
        // A where-question has no Yes or No; several pictures may be true, and each tap is logged.
        val shown = pictures.entries.filter { count(it.value) == 1 }.take(2)
        assertEquals(2, shown.size)
        shown.forEach { tap(it.value) }
        until(5_000) { answers().size == 2 }

        // 3. A nod on camera goes to the officer as the app's reading, never to the person and never as their answer.
        fun reads() = runBlocking { repo.eventsNow(id) }.filter { it.type == EventType.ANSWER_SIGNED && it.via == "nod" }
        until { tracker.frames > 10 }
        assertEquals("nothing is read before the nod", 0, reads().size)
        tracker.nodding = true
        until(40_000) { reads().isNotEmpty() }
        tracker.nodding = false
        assertEquals("yes", reads().first().cardId)
        assertEquals("a reading is not an answer", shown.map { it.key }, answers().map { it.cardId })
        assertEquals(listOf(null, null), answers().map { it.reason })

        // 4. Back on the officer pane: the pictures are graded Said (one row, or two when the taps are far apart), the nod is the app's reading.
        rule.onNode(handle).performTouchInput { longClick(durationMillis = 1_500) }
        waitFor(hasText(str(R.string.officer_tab_more)))
        waitFor(hasText(str(R.string.grade_machine_read)))
        assertTrue(rule.onAllNodesWithText(str(R.string.grade_said)).fetchSemanticsNodes().size in 1..2)

        // 5. Skeleton off from the officer menu: the stage's camera keeps running, the picture stays and only the overlay goes.
        tap(hasContentDescription(str(R.string.officer_more)))
        tap(hasText(str(R.string.officer_skeleton_show)))
        assertFalse(SkeletonOverlay.on.value)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.mainClock.advanceTimeBy(500)
        // Back closes the menu on the More page; the water quick question lives on Transcribe.
        tap(hasText(str(R.string.officer_tab_transcribe)))
        val water = content.question("n1")!!.text.getValue("en")
        tap(hasText(water))
        tap(hasContentDescription(str(R.string.officer_show)))
        waitFor(handle)
        val seen = tracker.frames
        until { tracker.frames > seen + 5 }
        // Leave the screen before the rule closes the activity, so the camera and frame loops are gone.
        rule.runOnUiThread { rule.activity.setContent {} }
        rule.mainClock.advanceTimeBy(500)
    }
}
