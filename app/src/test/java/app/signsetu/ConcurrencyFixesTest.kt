package app.signsetu

import android.content.ContextWrapper
import androidx.lifecycle.viewModelScope
import app.signsetu.camera.LandmarkFrame
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.SessionEvent
import app.signsetu.data.session.SessionRepository
import app.signsetu.export.SessionReport
import app.signsetu.pair.AnswerBody
import app.signsetu.sign.SignWindow
import app.signsetu.ui.session.Pending
import app.signsetu.ui.session.SessionViewModel
import app.signsetu.ui.session.SignReading
import app.signsetu.ui.session.within
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** One answer per item, the item the person saw, nothing written after the seal, no window after a wipe or an end,
 * and no model request left waiting after its budget. Each test below failed before its fix (see docs/36). */
@OptIn(ExperimentalCoroutinesApi::class)
class ConcurrencyFixesTest {
    private val content = shipped
    private val vms = mutableListOf<SessionViewModel>()

    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun unmain() {
        // Cancel, do not join: the test scope's scheduler is already stopped, so a join would never return and the
        // whole suite would hang in this teardown (seen with a 1002 s thread parked here).
        vms.forEach { it.viewModelScope.coroutineContext.job.cancel() }
        vms.clear()
        Dispatchers.resetMain()
    }

    private fun TestScope.waitFor(what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 30_000
        while (true) {
            runCurrent()
            if (cond()) return
            if (System.currentTimeMillis() > end) fail("timed out waiting for $what")
            Thread.sleep(5)
        }
    }

    private fun SessionViewModel.events(): List<SessionEvent> = ui.value?.events.orEmpty()

    private suspend fun TestScope.opened(dao: MemoryDao = MemoryDao()): Pair<SessionViewModel, SessionRepository> {
        var clock = 1_000L
        val repo = SessionRepository(dao, clock = { clock += 1_000; clock })
        val vm = SessionViewModel(repo.start(Profile.ISL_USER, "hi"), repo, content, "en", SessionReport(ContextWrapper(null))).also { vms += it }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.ui.collect {} }
        waitFor("the opening on the stage") { vm.ui.value?.onStage != null }
        vm.returnToOfficer()
        return vm to repo
    }

    private fun TestScope.askShown(vm: SessionViewModel): Long {
        vm.prepare(Pending.Ask(content.question("d2")!!))
        vm.show()
        waitFor("the item shown") { vm.ui.value?.itemId != null }
        return vm.local.value.itemId!!
    }

    @Test
    fun eachTapOnOneItemIsItsOwnAnswer() = runTest {
        val (vm, _) = opened()
        val shown = askShown(vm)
        // Two taps on the same item, before the first row has landed, are two answers; the pair flow keeps both and a
        // resend from one link is refused by its sequence number instead (PairTest).
        vm.answerCard("water")
        vm.answerCard("food")
        runCurrent()

        val rows = vm.events().filter { it.shownId == shown && it.type in setOf(EventType.ANSWER_CARD, EventType.ANSWER_GESTURE) }
        assertEquals("each tap is its own answer", 2, rows.size)
        assertEquals(listOf("water", "food"), rows.map { it.cardId })
    }

    @Test
    fun aTapKeepsTheItemThatWasOnScreen() = runTest {
        // The answer coroutine is queued rather than run inline, so the next Show can land before it starts.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, _) = opened()
        val shown = askShown(vm)

        vm.answerCard("water")
        // Exactly what present() does when the officer shows the next item: the id moves on.
        vm.local.update { it.copy(itemId = shown + 99) }
        advanceUntilIdle()

        val row = vm.events().single { it.type == EventType.ANSWER_CARD }
        assertEquals("the tap belongs to the item the person saw", shown, row.shownId)
    }

    @Test
    fun anAnswerAfterTheEndIsRefusedAndTheRootIsUnchanged() = runTest {
        val (vm, repo) = opened()
        val shown = askShown(vm)
        var ended = false
        vm.endSession("t") { ended = true }
        waitFor("the session ended") { ended }
        val seal = repo.currentSeal(vm.sessionId)!!
        val rows = repo.eventsNow(vm.sessionId).size

        val kept = vm.logRemote(shown, 0, AnswerBody.Card("water", null))
        runCurrent()

        assertFalse("an ended session takes no answer", kept)
        assertEquals("no row after the seal", rows, repo.eventsNow(vm.sessionId).size)
        assertEquals("the root still covers the whole record", seal.count, repo.currentSeal(vm.sessionId)!!.count)
    }

    @Test
    fun aWipedReaderTakesNoFrames() {
        val signs = SignReading(CoroutineScope(Dispatchers.Unconfined), content, null, {}, { _, _ -> })
        signs.start(SignWindow.ANSWER, emptySet())
        signs.wipe()
        val before = signs.lastFrameMs

        signs.onFrame(LandmarkFrame(4_242))

        assertEquals("no frame is recorded after the wipe", before, signs.lastFrameMs)
    }

    @Test
    fun noWindowOpensAfterTheSessionEnds() = runTest {
        val (vm, _) = opened()
        var ended = false
        vm.endSession("t") { ended = true }
        waitFor("the session ended") { ended }

        vm.startReading()

        assertNull("a finished session opens no sign window", vm.ui.value?.signWindow)
    }

    /** A phone-model request waits on the model's lock; when its share of the budget runs out it must stop waiting. */
    @Test
    fun aRungThatBreaksItsBudgetLeavesNoWaiter() = runTest {
        val lock = Mutex()
        lock.lock()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var entered = false
        val work = scope.async { lock.withLock { entered = true } }

        assertNull("the budget ran out", work.within(10))
        lock.unlock()

        assertFalse("a rung that lost the timeout must not take the lock later", entered)
    }
}
