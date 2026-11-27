package app.signsetu

import android.content.ContextWrapper
import androidx.lifecycle.viewModelScope
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.SessionRepository
import app.signsetu.export.SessionReport
import app.signsetu.pair.AnswerBody
import app.signsetu.ui.session.Mode
import app.signsetu.ui.session.Pending
import app.signsetu.ui.session.SOMETHING_ELSE
import app.signsetu.ui.session.SYSTEM_RUN_ENDED
import app.signsetu.ui.session.SessionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Answers from the second screen move the question run and mark only the item they answer; a log that loses the race
 * with the end is refused; the opening advances once per tap; an answer held for confirmation survives the seal. */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionLinkRunTest {
    private val content = shipped
    private val vms = mutableListOf<SessionViewModel>()

    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun unmain() {
        vms.forEach { it.viewModelScope.coroutineContext.job.cancel() }
        vms.clear()
        Dispatchers.resetMain()
    }

    private fun TestScope.waitFor(what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 30_000
        while (true) {
            advanceUntilIdle()
            if (cond()) return
            if (System.currentTimeMillis() > end) fail("timed out waiting for $what")
            Thread.sleep(5)
        }
    }

    private suspend fun TestScope.opened(back: Boolean = true, dao: MemoryDao = MemoryDao()): Pair<SessionViewModel, SessionRepository> {
        val repo = SessionRepository(dao, clock = { 1_000L })
        val vm = SessionViewModel(repo.start(Profile.ISL_USER, "hi"), repo, content, "en", SessionReport(ContextWrapper(null))).also { vms += it }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.ui.collect {} }
        waitFor("the opening on the stage") { vm.ui.value?.onStage != null }
        if (back) {
            vm.returnToOfficer()
            waitFor("the officer back") { vm.local.value.mode == Mode.OFFICER }
        }
        return vm to repo
    }

    private fun TestScope.showItem(vm: SessionViewModel, id: String): Long {
        val before = vm.local.value.itemId
        vm.prepare(Pending.Ask(content.question(id)!!))
        vm.show()
        waitFor("$id on the stage") { vm.local.value.itemId != null && vm.local.value.itemId != before }
        return vm.local.value.itemId!!
    }

    @Test
    fun aRunAnsweredFromTheSecondScreenReachesItsLastQuestion() = runTest {
        val (vm, _) = opened()
        vm.paired = true
        val sensitive = content.groups.filter { it.sensitive }.map { it.id }.toSet()
        val asks = content.questions.filterNot { it.group in sensitive }.take(2).map { Pending.Ask(it) }
        assertEquals("two questions to run", 2, asks.size)
        vm.showRun(asks)
        waitFor("the first question") { vm.local.value.itemId != null }
        val first = vm.local.value.itemId!!

        vm.logRemote(first, 0, AnswerBody.YesNo(true))
        waitFor("the second question") { vm.local.value.itemId != null && vm.local.value.itemId != first }
        val second = vm.local.value.itemId!!
        assertEquals(asks[1].question.id, (vm.shown[second] as Pending.Ask).question.id)

        vm.logRemote(second, 0, AnswerBody.Card("stop"))
        advanceUntilIdle()

        val ended = vm.ui.value!!.events.mapNotNull { it.text }.filter { it.startsWith(SYSTEM_RUN_ENDED) }
        assertEquals("both questions were asked", listOf("${SYSTEM_RUN_ENDED}2/2"), ended)
    }

    @Test
    fun anAnswerToAnotherItemLeavesTheItemOnStageUnanswered() = runTest {
        val (vm, _) = opened()
        val a = showItem(vm, "d2")
        val b = showItem(vm, "d3")
        assertNotEquals(a, b)

        vm.logRemote(a, 0, AnswerBody.Card("yes"))

        assertNull("the item on stage took no answer", vm.local.value.answeredCard)
        assertEquals(b, vm.local.value.itemId)
    }

    @Test
    fun aLogThatLostTheRaceWithTheEndIsRefused() = runTest {
        val dao = MemoryDao()
        val repo = SessionRepository(dao, clock = { 1_000L })
        val id = repo.start(Profile.ISL_USER, "hi")

        // Hold the seal's lock so both calls queue on it: end first, then the log that read the flag before end set it.
        repo.sealing.lock()
        val end = async { repo.end(id, "t") }
        val log = async { repo.log(id, EventType.NOTE, EventSource.OFFICER, text = "late") }
        runCurrent()
        repo.sealing.unlock()

        assertEquals("a log that lost the race is refused", -1L, log.await())
        end.await()
        assertNull("nothing was inserted after the seal", dao.rows.value.firstOrNull { it.text == "late" })
    }

    @Test
    fun aDoubleTapOnTheOpeningRulePresentsOneStep() = runTest {
        val dao = MemoryDao()
        val (vm, _) = opened(back = false, dao = dao)
        assertTrue(vm.local.value.pending is Pending.GroundRules)
        val before = dao.rows.value.count { it.type == EventType.QUESTION_SHOWN.name }

        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        vm.nextRule()
        vm.nextRule()
        advanceUntilIdle()

        val after = dao.rows.value.count { it.type == EventType.QUESTION_SHOWN.name }
        assertEquals("one rule step for one tap", 1, after - before)
    }

    @Test
    fun aNewRunDropsTheOldRunsPendingStep() = runTest {
        val (vm, _) = opened()
        vm.paired = true
        val sensitive = content.groups.filter { it.sensitive }.map { it.id }.toSet()
        val pool = content.questions.filterNot { it.group in sensitive }
        val old = pool.take(2).map { Pending.Ask(it) }
        val fresh = pool.drop(2).take(2).map { Pending.Ask(it) }
        assertEquals("two runs of two", 2, fresh.size)

        vm.showRun(old)
        runCurrent()
        val a1 = vm.local.value.itemId!!
        // Answering A1 leaves this run's 3 s step job waiting for A2.
        vm.logRemote(a1, 0, AnswerBody.YesNo(true))

        // A fresh run is sent before that step fires: its own first item is what the person sees.
        vm.showRun(fresh)
        runCurrent()
        val b1 = vm.local.value.itemId!!
        assertNotEquals(a1, b1)
        assertEquals(fresh[0].question.id, (vm.shown[b1] as Pending.Ask).question.id)

        // The old step's delay passes; it must not present anything of run B.
        advanceTimeBy(4_000)
        runCurrent()
        assertEquals("the fresh run's first item stays on the stage", b1, vm.local.value.itemId)
        assertEquals(fresh[0].question.id, (vm.local.value.onStage as Pending.Ask).question.id)
        assertTrue("run A's next item never appears", vm.shown.values.none { (it as? Pending.Ask)?.question?.id == old[1].question.id })
        assertTrue("run B's second item is not shown early", vm.shown.values.none { (it as? Pending.Ask)?.question?.id == fresh[1].question.id })

        // The 3 s pace for a normal answer is kept: answering B1 brings B2, not the drawing pad or nothing.
        vm.logRemote(b1, 0, AnswerBody.YesNo(true))
        advanceTimeBy(3_001)
        runCurrent()
        assertEquals(fresh[1].question.id, (vm.local.value.onStage as Pending.Ask).question.id)
    }

    @Test
    fun anUnconfirmedGivenIsKeptBeforeTheSeal() = runTest {
        val (vm, repo) = opened()
        val item = showItem(vm, "d2")

        vm.logRemote(item, 0, AnswerBody.Given("d2", "WRITTEN", confirmed = false, value = "home"))
        assertTrue("still waiting for its confirmation", vm.ui.value!!.events.none { it.type == EventType.ANSWER_TEXT })

        var ended = false
        vm.endSession("t") { ended = true }
        waitFor("the session ended") { ended }

        val row = repo.eventsNow(vm.sessionId).firstOrNull { it.type == EventType.ANSWER_TEXT && it.itemId == "d2" }
        assertNotNull("the held answer is in the sealed record", row)
        assertEquals("given:WRITTEN:0", row!!.reason)
    }

    @Test
    fun aLanguageAnswerFromTheSecondScreenSettlesAndPresentsTheOpening() = runTest {
        val dao = MemoryDao()
        val (vm, repo) = opened(dao = dao)
        vm.prepare(Pending.LanguageFinder)
        vm.show()
        waitFor("the language grid") { vm.local.value.itemId != null }
        val item = vm.local.value.itemId!!

        vm.logRemote(item, 0, AnswerBody.Language("hi"))
        advanceTimeBy(5_001)
        runCurrent()

        assertEquals("one pick, one row", 1, dao.rows.value.count { it.type == EventType.LANGUAGE_CHOSEN.name })
        assertEquals("the session switched to it", "hi", repo.session(vm.sessionId).first()?.subjectLang)
        assertEquals("the opening came up in it", "s1", (vm.local.value.onStage as Pending.Ask).question.id)
    }

    @Test
    fun aSomethingElseAnswerFromTheSecondScreenFollowsTheOfficersStage() = runTest {
        val (vm, _) = opened()
        // h1 offers more pictures than the stage shows, so "something else" brings the ones not yet seen.
        val wide = content.question("h1")!!
        assertTrue("h1 has pictures held back", wide.cards.size > 4)
        val first = showItem(vm, "h1")
        val shown = (vm.local.value.onStage as Pending.Ask).question.cards
        vm.logRemote(first, 0, AnswerBody.Card(SOMETHING_ELSE))
        advanceUntilIdle()
        val narrowed = (vm.local.value.onStage as Pending.Ask).question.cards
        assertEquals("the pictures not yet seen", wide.cards.filterNot { it in shown }.sorted(), narrowed.filter { it != SOMETHING_ELSE }.sorted())

        // h2 holds one picture, so nothing is left to offer: the drawing pad comes up, as it does on this phone.
        val second = showItem(vm, "h2")
        vm.logRemote(second, 0, AnswerBody.Card(SOMETHING_ELSE))
        advanceUntilIdle()
        assertEquals(Pending.Drawing, vm.local.value.onStage)
    }
}
