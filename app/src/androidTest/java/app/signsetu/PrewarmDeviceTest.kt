package app.signsetu

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The app loads the content bundle, the landmark model and the sign readers before the first question is asked. */
@RunWith(AndroidJUnit4::class)
class PrewarmDeviceTest {

    @Test
    fun warmUpLeavesContentAndReadersReady() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SignSetuApp
        // Called once more on purpose: a second call must not start another load.
        app.container.warm()
        withTimeout(60_000) { app.container.warmDone.first { it } }
        assertNotNull("the ISL sign reader is loaded after the warm-up", app.container.signReader)
        // The bundle is held in memory, so a load after the warm-up does not read the assets again.
        val started = SystemClock.elapsedRealtime()
        app.container.content.load()
        val ms = SystemClock.elapsedRealtime() - started
        assertTrue("a second content load took $ms ms; the bundle should already be in memory (under 250 ms)", ms < 250)
    }
}
