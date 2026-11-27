package app.signsetu

import androidx.compose.ui.geometry.Offset
import app.signsetu.camera.Head
import app.signsetu.camera.LandmarkFrame
import app.signsetu.sign.SignGuess
import app.signsetu.sign.SignReader
import app.signsetu.sign.SignSegment
import app.signsetu.sign.SignWindow
import app.signsetu.ui.session.SignReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** A segment cut just before the window closes is still read and awaited, and a wipe leaves no face-derived timeline. */
class SignReadingTest {
    private val content = shipped

    /** One hand, moved by [shift] frame widths, with no pose: a sign, never a hand dropping to rest. */
    private fun handFrame(ms: Long, shift: Float) = LandmarkFrame(ms, leftHand = List(21) { i -> Offset(0.3f + shift + i * 0.01f, 0.5f) })

    /** A sign the window cuts on its own: three moving frames, then a pause past [SignWindow.ANSWER]'s 400 ms. */
    private fun cutByTheCamera() = listOf(handFrame(0, 0f), handFrame(100, 0.05f), handFrame(200, 0.1f), handFrame(300, 0.15f), handFrame(800, 0.15f))

    @Test
    fun aSegmentCutJustBeforeCloseStillHasItsReadAwaited() {
        val readStarted = CountDownLatch(1)
        val readMayFinish = CountDownLatch(1)
        val reader = object : SignReader {
            override val labels = listOf("water")
            override fun read(frames: List<LandmarkFrame>): List<SignGuess> {
                readStarted.countDown()
                readMayFinish.await(10, TimeUnit.SECONDS)
                return listOf(SignGuess("water", 0.9f))
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val cut = CountDownLatch(1)
        val release = CountDownLatch(1)
        val placeholders = CopyOnWriteArrayList<SignSegment>()
        val reads = CopyOnWriteArrayList<Pair<SignSegment, SignSegment>>()
        val signs = SignReading(scope, content, reader,
            onSegment = { placeholders += it },
            onRead = { placeholder, read -> reads += placeholder to read })
        // Holds the analysis thread where the bug lived: after the cut, before its read is registered.
        signs.onSegmentCut = { cut.countDown(); release.await(10, TimeUnit.SECONDS) }
        signs.start(SignWindow.ANSWER, emptySet())

        val analysis = thread { cutByTheCamera().forEach(signs::onFrame) }
        assertTrue("the window cut a sign", cut.await(10, TimeUnit.SECONDS))
        val closed = AtomicReference<List<Job>?>(null)
        val closer = thread { closed.set(signs.close()) }
        Thread.sleep(100)
        release.countDown()
        analysis.join(10_000)
        closer.join(10_000)

        val jobs = closed.get()
        assertNotNull("a window was open", jobs)
        assertEquals("the segment cut just before close is awaited", 1, jobs!!.size)
        assertEquals("its placeholder was added", 1, placeholders.size)
        assertNull("the placeholder has no word yet", placeholders.single().guesses)

        readMayFinish.countDown()
        runBlocking { jobs.joinAll() }

        assertTrue("the read ran", readStarted.await(10, TimeUnit.SECONDS))
        val (placeholder, read) = reads.single()
        assertSame("the cut segment got the read", placeholders.single(), placeholder)
        assertEquals("the words hold the read, not \"?\"", "water", read.word)
        scope.cancel()
    }

    @Test
    fun aWipeDropsTheHeadTimeline() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val signs = SignReading(scope, content, null, {}, { _, _ -> })
        signs.onFrame(LandmarkFrame(1_000, head = Head.Nod))
        assertEquals("the nod is kept", 1, signs.headCount())

        signs.wipe()

        assertEquals("a wipe drops every nod and shake", 0, signs.headCount())
        assertTrue("a wiped window answers no head question", signs.headsAround(0, 2_000).isEmpty())
        scope.cancel()
    }
}
