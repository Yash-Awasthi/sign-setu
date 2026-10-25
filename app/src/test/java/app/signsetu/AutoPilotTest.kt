package app.signsetu

import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.Kind
import app.signsetu.data.content.Question
import app.signsetu.data.session.EventEntity
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.Role
import app.signsetu.data.session.SessionRepository
import app.signsetu.ui.session.AutoKey
import app.signsetu.ui.session.AutoPilot
import app.signsetu.ui.session.Conv
import app.signsetu.ui.session.PauseCause
import app.signsetu.ui.session.Pending
import app.signsetu.ui.session.reportMode
import app.signsetu.data.session.SessionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoPilotTest {

    private val bank = ContentBundle(groups = shipped.groups, questions = shipped.questions)

    /** The view model's side of auto mode, reduced to what the pilot calls. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private inner class Phone(private val test: TestScope) {
        // Not backgroundScope: advanceUntilIdle does not wait for background work.
        val scope = CoroutineScope(StandardTestDispatcher(test.testScheduler))
        val dao = MemoryDao()
        val repo = SessionRepository(dao, clock = { dao.rows.value.size.toLong() })
        var showing = false
        private var shownId: Long? = null
        private var id = ""
        lateinit var pilot: AutoPilot

        var signing = false

        suspend fun start(handsOff: Boolean = false) {
            id = repo.start(Profile.NOT_SURE, "hi", Role.VICTIM)
            pilot = AutoPilot(id, repo, bank, "en", scope, signing = { signing }, handsOff = handsOff, present = { q, lang -> present(q, lang) })
            settle()
        }

        /** Moves the clock without running to the end of it, which hands-off mode would never reach. */
        fun tick(ms: Long) { test.testScheduler.advanceTimeBy(ms); test.testScheduler.runCurrent() }

        fun shownCount(item: String) = rows.count { it.type == EventType.QUESTION_SHOWN.name && it.itemId == item }

        /** Lets hands-off mode run until it puts a question that expects an answer on the stage. */
        fun runToQuestion(): String {
            repeat(400) {
                tick(1_000)
                val last = rows.lastOrNull { it.type == EventType.QUESTION_SHOWN.name }?.itemId
                if (last != null && bank.question(last)?.kind != Kind.STATEMENT) return last
            }
            error("no question reached the stage")
        }

        fun answerSigned() {
            val shown = rows.last { it.type == EventType.QUESTION_SHOWN.name }.id
            test.testScheduler.runCurrent()
            kotlinx.coroutines.runBlocking { repo.log(id, EventType.ANSWER_SIGNED, EventSource.MACHINE, "en", "pain", shownId = shown) }
            tick(1)
        }

        private suspend fun present(q: Question, lang: String) {
            shownId = repo.log(id, EventType.QUESTION_SHOWN, EventSource.PRESET, lang, q.text["en"], itemId = q.id)
            showing = true
        }

        suspend fun manualShow(questionId: String) {
            val q = bank.question(questionId)!!
            pilot.onManualShow(Pending.Ask(q))
            present(q, "hi")
            settle()
        }

        suspend fun answer(card: String) {
            repo.log(id, EventType.ANSWER_CARD, EventSource.SUBJECT, "en", card, cardId = card, shownId = shownId)
            settle()
        }

        fun back() {
            showing = false
            pilot.onReturn()
            settle()
        }

        fun settle() = test.advanceUntilIdle()
        val conv get() = pilot.conv.value
        val proposed get() = (conv as? Conv.Proposed)?.p?.q?.id
        val rows get() = dao.rows.value
        fun act(block: AutoPilot.() -> Unit) { pilot.block(); settle() }

        /** Approves s1 and the four ground rules, leaving the first welfare question proposed. */
        fun throughGroundRules() {
            act { setAuto(true) }
            repeat(5) { act { approve() } }
            back()
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun phone(handsOff: Boolean = false, block: suspend Phone.() -> Unit) = runTest {
        val phone = Phone(this)
        try {
            phone.start(handsOff)
            phone.block()
        } finally {
            phone.scope.cancel()
        }
    }

    @Test
    fun anUnansweredQuestionIsRepeatedOnceThenSkipped() = phone(handsOff = true) {
        pilot.setAuto(true); tick(1)
        val q = runToQuestion()
        assertEquals(1, shownCount(q))
        tick(10_500)
        assertEquals(2, shownCount(q))
        tick(10_500)
        assertEquals("no_answer", rows.last { it.type == EventType.SKIPPED.name }.reason)
        assertEquals(2, shownCount(q))
    }

    @Test
    fun handsStillSigningHoldBackTheRepeat() = phone(handsOff = true) {
        pilot.setAuto(true); tick(1)
        val q = runToQuestion()
        signing = true
        tick(60_000)
        assertEquals(1, shownCount(q))
        assertTrue(rows.none { it.type == EventType.SKIPPED.name && it.reason == "no_answer" })
    }

    @Test
    fun aSignedAnswerWaitsFiveSecondsForMore() = phone(handsOff = true) {
        pilot.setAuto(true); tick(1)
        val q = runToQuestion()
        val shownBefore = rows.count { it.type == EventType.QUESTION_SHOWN.name }
        answerSigned()
        tick(4_000)
        assertEquals(shownBefore, rows.count { it.type == EventType.QUESTION_SHOWN.name })
        tick(1_500)
        assertEquals(shownBefore + 1, rows.count { it.type == EventType.QUESTION_SHOWN.name })
        assertTrue(shownCount(q) == 1)
    }

    @Test
    fun anUnsureAnswerIsNeverProposedAgain() = phone {
        throughGroundRules()
        assertEquals("s2", proposed)
        act { approve() }
        answer("dont_know")
        assertEquals("s3", proposed)
    }

    @Test
    fun approvalIsLoggedBetweenSuggestionAndShowing() = phone {
        act { setAuto(true) }
        act { approve() }
        val s1 = rows.filter { it.itemId == "s1" }.map { it.type }
        assertEquals(listOf(EventType.SUGGESTED.name, EventType.APPROVED.name, EventType.QUESTION_SHOWN.name), s1)
        val i = rows.indexOfFirst { it.type == EventType.APPROVED.name }
        assertEquals(EventType.QUESTION_SHOWN.name, rows[i + 1].type)
    }

    @Test
    fun skippingAndChoosingSomethingElseAreBothLogged() = phone {
        act { setAuto(true) }
        act { skip() }
        assertEquals("officer", rows.last { it.type == EventType.SKIPPED.name }.reason)
        val before = rows.size
        val proposedNow = proposed!!
        manualShow("n1")
        val added = rows.drop(before)
        assertEquals(listOf(EventType.SKIPPED.name, EventType.QUESTION_SHOWN.name), added.map { it.type })
        assertEquals("officer_chose_other" to proposedNow, added[0].reason to added[0].itemId)
    }

    @Test
    fun stopPausesAndASecondStopEndsAuto() = phone {
        throughGroundRules()
        act { approve() }
        answer("stop")
        assertEquals(Conv.Paused(PauseCause.STOP), conv)
        back()
        act { resume() }
        assertEquals("k3", proposed)
        act { approve() }
        answer("stop")
        assertEquals(Conv.Off, conv)
        assertTrue(rows.any { it.text == AutoKey.ENDED_STOP })
    }

    @Test
    fun aBreakRequestPausesAuto() = phone {
        throughGroundRules()
        act { approve() }
        answer("dont_know")
        act { approve() }
        answer("dont_know")
        assertEquals("k1", proposed)
        back()
        act { approve() }
        answer("yes")
        assertEquals(Conv.Paused(PauseCause.BREAK), conv)
        assertTrue(rows.any { it.text == AutoKey.BREAK })
    }

    @Test
    fun askingAboutEventsByHandTurnsAutoOff() = phone {
        act { setAuto(true) }
        manualShow("x1")
        assertEquals(Conv.Off, conv)
        assertTrue(rows.any { it.text == AutoKey.MODE_MANUAL_SUBSTANTIVE })
    }

    @Test
    fun aTypedQuestionTurnsAutoOffAndTheReportSaysSo() = phone {
        act { setAuto(true) }
        assertEquals(R.string.report_mode_auto, reportMode(rows.map(::event)))
        pilot.onManualShow(Pending.FreeText("What did he do?", emptyList()))
        settle()
        assertEquals(Conv.Off, conv)
        assertEquals(R.string.report_mode_auto_then_manual, reportMode(rows.map(::event)))
    }

    private fun event(e: EventEntity) = SessionEvent(e.id, e.ts, EventType.valueOf(e.type), EventSource.valueOf(e.source), e.lang, e.text, e.cardId, e.lexiconId, e.drawingId)

    @Test
    fun changingTheRoleDropsTheProposal() = phone {
        act { setAuto(true) }
        act { onRoleChanged() }
        assertEquals("role_changed", rows.last { it.type == EventType.SKIPPED.name }.reason)
    }

    @Test
    fun aClosedDatabaseTurnsAutoOffInsteadOfCrashing() = phone {
        act { setAuto(true) }
        dao.closed = true
        act { skip() }
        assertEquals(Conv.Off, conv)
    }
}
