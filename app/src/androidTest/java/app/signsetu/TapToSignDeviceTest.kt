package app.signsetu

import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.test.core.app.ActivityScenario
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToLong

/**
 * The row asks for numbers, not a threshold: how long from the officer's tap (the item put on the stage) to the first
 * frame drawn with the new item. Three screens, five runs each, the first run of each screen discarded as warm-up.
 * Frames come from the real window: the wrapper's draw pass runs on the main thread after each composition, so the
 * timestamp is the frame the subject would see, not a test clock. Every run is also written to logcat under "TapToSign".
 */
@RunWith(AndroidJUnit4::class)
class TapToSignDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as SignSetuApp
    private lateinit var content: ContentBundle

    /** The item state for one run; every run uses a different item, so no earlier composition is reused. */
    private data class Screen(val name: String, val items: List<Pending>)

    @Test
    fun tapToFirstFrameOnThreeScreens() {
        content = runBlocking { app.container.content.load() }
        val bundle = content
        val screens = listOf(
            Screen("question", listOf("s2", "h2", "h4", "n1", "n2").map { id ->
                Pending.Ask(bundle.questions.first { it.id == id })
            }),
            Screen("pictures", listOf(
                listOf("mother", "father", "brother"),
                listOf("doctor", "medicine", "rest"),
                listOf("water", "food", "toilet"),
                listOf("home", "school", "person"),
                listOf("head", "stomach", "arm", "leg"),
            ).map { cards -> Pending.Cards(cards) }),
            Screen("typed", listOf(
                "You want water", "You need rest", "You feel safe", "You are hurt", "You are okay",
            ).map { line -> Pending.FreeText(line, emptyList(), CAPTION_MODES) }),
        )

        val ui = mutableStateOf(uiFor(null))
        val run = mutableIntStateOf(0)
        val startedAt = AtomicLong(0L)
        val first = AtomicLong(0L)
        val draws = AtomicLong(0L)

        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        var activity: ComponentActivity? = null
        scenario.onActivity { activity = it }
        try {
            instrumentation.runOnMainSync {
                activity!!.setContent {
                    SignSetuTheme {
                        SubjectTheme {
                            CompositionLocalProvider(
                                LocalContentRepository provides app.container.content,
                                LocalOnlineAssistant provides app.container.onlineAssistant,
                            ) {
                                key(run.intValue) {
                                    Box(Modifier.fillMaxSize().drawWithContent {
                                        draws.incrementAndGet()
                                        drawContent()
                                    }) {
                                        Stage(
                                            ui = ui.value,
                                            content = bundle,
                                            tracker = null,
                                            cameraOn = false,
                                            gestureSpeed = 1f,
                                            askModes = CAPTION_MODES,
                                            onCard = {},
                                            onYesNo = {},
                                            onLanguage = {},
                                            onDrawing = {},
                                            onWritten = {},
                                            onSignFrame = {},
                                            onStartSigning = {},
                                            onDoneSigning = {},
                                            onOfficer = null,
                                        )
                                        // A node created by the same recomposition that puts the item on the stage is drawn
                                        // in that frame, and a fresh node's draw always runs: it marks the frame the
                                        // subject first sees the item, whereas the parent's draw is not re-run for children.
                                        val onCall = ui.value.pending
                                        if (onCall != null) {
                                            key(onCall) {
                                                Box(Modifier.fillMaxSize().drawWithContent {
                                                    if (first.get() == 0L) {
                                                        val now = SystemClock.uptimeMillis()
                                                        if (startedAt.get() != 0L && now >= startedAt.get()) first.set(now)
                                                    }
                                                    drawContent()
                                                })
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Thread.sleep(500)
            // The first composition builds the whole call (and its character); only then does a run mean anything.
            assertTrue("the stage drew its first frame", awaitDraws(draws, 1L, 60_000))

            val kept = LinkedHashMap<String, MutableList<Long>>()
            screens.forEach { screen ->
                val runs = ArrayList<Long>()
                screen.items.forEachIndexed { index, item ->
                    // Fresh Stage on the greeting before the tap, so every run starts from the same state.
                    val before = draws.get()
                    instrumentation.runOnMainSync {
                        run.intValue = run.intValue + 1
                        ui.value = uiFor(null)
                    }
                    assertTrue(
                        "the fresh stage drew before the tap for ${screen.name} run ${index + 1}",
                        awaitDraws(draws, before + 1, 30_000),
                    )
                    first.set(0L)
                    startedAt.set(0L)
                    instrumentation.runOnMainSync {
                        startedAt.set(SystemClock.uptimeMillis())
                        ui.value = uiFor(item)
                    }
                    val drawn = awaitFirst(first, 15_000)
                    if (drawn == 0L) Log.i(TAG, "TapToSign no frame for ${screen.name} run ${index + 1} draws=${draws.get()}")
                    assertTrue("a frame was drawn for ${screen.name} run ${index + 1}", drawn > 0L)
                    val ms = (drawn - startedAt.get()).coerceAtLeast(1L)
                    assertTrue("a positive measurement for ${screen.name} run ${index + 1}", ms > 0L)
                    Log.i(TAG, "TapToSign screen=${screen.name} run=${index + 1} warmup=${if (index == 0) 1 else 0} ms=$ms")
                    println("TapToSign screen=${screen.name} run=${index + 1} warmup=${if (index == 0) 1 else 0} ms=$ms")
                    if (index == 0) return@forEachIndexed
                    runs += ms
                }
                assertEquals("four measured runs of ${screen.name}", RUNS - 1, runs.size)
                kept[screen.name] = runs
                val median = medianOf(runs)
                val max = runs.max()
                Log.i(TAG, "TapToSign screen=${screen.name} runs=${runs.size} median=${median}ms max=${max}ms")
                println("TapToSign screen=${screen.name} runs=${runs.size} median=${median}ms max=${max}ms")
            }

            val worst = kept.map { (name, runs) -> name to medianOf(runs) }.sortedByDescending { it.second }.take(3)
            val line = "TapToSign worst: " + worst.mapIndexed { i, (name, ms) -> "${i + 1}) $name ${ms}ms" }.joinToString(" ")
            Log.i(TAG, line)
            println(line)
            assertEquals("all three screens measured", 3, kept.size)
        } finally {
            instrumentation.runOnMainSync { activity?.finish() }
            scenario.close()
        }
    }

    private fun uiFor(pending: Pending?) = SessionUi(
        session = Session("measure", 0L, Profile.NOT_SURE, "en"),
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

    /** Wait until at least [atLeast] frames have been drawn, or the timeout passes. */
    private fun awaitDraws(draws: AtomicLong, atLeast: Long, timeoutMs: Long): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (draws.get() < atLeast && SystemClock.uptimeMillis() < deadline) Thread.sleep(5)
        return draws.get() >= atLeast
    }

    /** Wait until a frame drawn after the tap has been recorded, or the timeout passes. */
    private fun awaitFirst(first: AtomicLong, timeoutMs: Long): Long {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (first.get() == 0L && SystemClock.uptimeMillis() < deadline) Thread.sleep(5)
        return first.get()
    }

    private fun medianOf(values: List<Long>): Long {
        val sorted = values.sorted()
        return if (sorted.size % 2 == 1) sorted[sorted.size / 2]
        else ((sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0).roundToLong()
    }

    private companion object {
        const val TAG = "TapToSign"
        const val RUNS = 5
    }
}
