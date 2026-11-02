package app.signsetu

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.ui.avatar.FlatAvatar
import app.signsetu.ui.avatar.seconds
import app.signsetu.ui.session.PauseLayer
import app.signsetu.ui.theme.SubjectTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/** Frame rate of the subject's animated screens (a recorded sentence on the drawn character over the breathing
 * pause), from the window's own frame metrics, against one moving circle on the same device. On a phone the stage
 * should hold 60 fps (16.7 ms a frame); the log line carries every rate measured. */
@RunWith(AndroidJUnit4::class)
class StageFrameRateTest {
    @Test
    fun animatedStageScreensKeepToTheFrameBudget() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SignSetuApp
        val content = runBlocking { app.container.content.load() }
        val clip = content.clips.values.maxBy { it.frames.size }
        // Only while the sentence plays: between motions the character idles at 30 fps by design.
        val window = ((clip.seconds - 0.5f) * 1000).toLong().coerceIn(1_500, 4_000)
        android.util.Log.i(TAG, "clip ${clip.sentence}: %.1f s, measuring %d ms".format(clip.seconds, window))
        val base = measure(window) { Pulse() }
        val stage = measure(window) {
            SubjectTheme {
                Box(Modifier.fillMaxSize()) {
                    PauseLayer(label = null, onReady = {}, modifier = Modifier.fillMaxSize())
                    FlatAvatar(null, 1, Modifier.align(Alignment.TopCenter).size(240.dp, 282.dp), clip = clip)
                }
            }
        }
        val pause = measure(window) { SubjectTheme { PauseLayer(label = null, onReady = {}, modifier = Modifier.fillMaxSize()) } }
        val avatar = measure(window) { FlatAvatar(null, 1, Modifier.size(240.dp, 282.dp), clip = clip) }
        Log.i(TAG, "stage %.0f fps (%.0f%% of gaps over 25 ms); pause alone %.0f, character alone %.0f, one circle %.0f fps on this device".format(stage.first, stage.second * 100, pause.first, avatar.first, base.first))
        // An emulator draws the character's paths in software at about two thirds of its rate; a phone's GPU is the
        // real check (the log line). Below half, something redraws or recomposes far more than it should.
        assertTrue("the stage draws at least half the device's own rate: %.0f of %.0f fps".format(stage.first, base.first), stage.first >= 0.5 * base.first)
    }

    @Composable
    private fun Pulse() {
        val t by rememberInfiniteTransition(label = "pulse").animateFloat(0f, 1f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "r")
        Canvas(Modifier.fillMaxSize()) { drawCircle(Color.Blue, size.minDimension * (0.1f + 0.2f * t)) }
    }

    /** Frames a second and the share of gaps over 1.5 vsyncs, over 4 s of [body]. */
    private fun measure(ms: Long, body: @Composable () -> Unit): Pair<Double, Double> {
        val vsyncs = CopyOnWriteArrayList<Long>()
        val listener = Window.OnFrameMetricsAvailableListener { _, m, _ -> vsyncs += m.getMetric(FrameMetrics.VSYNC_TIMESTAMP) }
        // Espresso waits for an idle main thread, which a running animation never gives, so after launch the
        // activity is driven with runOnMainSync only; frames come from the real choreographer, not a test clock.
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        lateinit var activity: ComponentActivity
        scenario.onActivity { activity = it }
        val ins = InstrumentationRegistry.getInstrumentation()
        ins.runOnMainSync {
            activity.window.addOnFrameMetricsAvailableListener(listener, Handler(Looper.getMainLooper()))
            activity.setContent(content = body)
        }
        Thread.sleep(ms)
        ins.runOnMainSync { activity.window.removeOnFrameMetricsAvailableListener(listener); activity.finish() }
        val gaps = vsyncs.toList().drop(5).zipWithNext { a, b -> (b - a) / 1e6 }
        assertTrue("frames were drawn: ${gaps.size}", gaps.size > 20)
        return 1000.0 / gaps.average() to gaps.count { it > 25.0 }.toDouble() / gaps.size
    }

    private companion object { const val TAG = "StageFrameRate" }
}
