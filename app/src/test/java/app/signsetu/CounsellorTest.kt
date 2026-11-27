package app.signsetu

import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.Session
import app.signsetu.data.session.SessionEvent
import app.signsetu.ui.session.Counsel
import app.signsetu.ui.session.CounselLine
import app.signsetu.ui.session.Counsellor
import app.signsetu.ui.session.Mode
import app.signsetu.ui.session.SHOWN_AI
import app.signsetu.ui.session.SessionUi
import app.signsetu.ui.session.counselHistory
import app.signsetu.ui.session.parseCounselLine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CounsellorTest {

    private fun ev(id: Long, type: EventType, text: String? = null, card: String? = null, reason: String? = null, source: EventSource = EventSource.SUBJECT, shown: Long? = null) =
        SessionEvent(id, id, type, source, "hi", text, card, null, null, reason = reason, shownId = shown)

    private fun ui(events: List<SessionEvent>, mode: Mode = Mode.OFFICER) = SessionUi(
        Session("s", 0, Profile.ISL_USER, "hi"), "", false, null, mode, false, null, null, null, events, null, emptyMap(),
    )

    @Test
    fun repliesBecomeOneShortLineWithItsCaption() {
        assertEquals(
            CounselLine("Hello, I am here to help. Are you comfortable?", "नमस्ते, मैं मदद के लिए हूँ। क्या आप ठीक हैं?", false),
            parseCounselLine("""Sure! {"say": "Hello, I am here to help. Are you comfortable? Who hurt you?", "caption": "नमस्ते, मैं मदद के लिए हूँ। क्या आप ठीक हैं?", "handoff": false}"""),
        )
        assertTrue(parseCounselLine("""{"say": "You are safe now.", "handoff": true}""")!!.handoff)
        assertEquals("Take your time.", parseCounselLine("Take your time.")!!.say)
        assertNull(parseCounselLine("   "))
    }

    @Test
    fun theHistoryIsJsonTurnsThatNameWhoSpoke() {
        val events = listOf(
            ev(1, EventType.QUESTION_SHOWN, "I am a police officer.", source = EventSource.PRESET),
            ev(2, EventType.QUESTION_SHOWN, "Hello, I am a helper.\nनमस्ते", reason = SHOWN_AI, source = EventSource.MACHINE),
            ev(3, EventType.ANSWER_CARD, card = "water"),
            ev(4, EventType.ANSWER_TEXT, "my uncle"),
        )
        val turns = Json.parseToJsonElement(counselHistory(events, shipped)).jsonArray.map { it.jsonObject }
        assertEquals(listOf("officer", "assistant", "user", "user"), turns.map { it.getValue("role").jsonPrimitive.content })
        assertEquals(listOf("I am a police officer.", "Hello, I am a helper.", "Water", "my uncle"), turns.map { it.getValue("text").jsonPrimitive.content })
    }

    @Test
    fun anAnswerGivenWhileTheLineWasWrittenDoesNotAnswerIt() = runTest {
        val flow = MutableStateFlow<SessionUi?>(ui(emptyList()))
        val sent = mutableListOf<String>()
        val c = Counsellor(this, flow, send = { sent += it.say; 1L }, holdMs = 1_000)
        val late = ui(listOf(ev(5, EventType.ANSWER_CARD, card = "yes", shown = 99)))
        c.start("system", write = { _, _ -> flow.value = late; """{"say": "Line."}""" }, transcript = { "" })
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf("Line."), sent)
        assertEquals(Counsel.Waiting, c.counsel.value)
        c.takeOver()
    }

    @Test
    fun aLineWaitsForTheOfficerThenTheAnswerStartsTheNextTurnAndStopHandsBack() = runTest {
        val flow = MutableStateFlow<SessionUi?>(ui(emptyList()))
        val sent = mutableListOf<String>()
        var mine = 0L
        val c = Counsellor(this, flow, send = { sent += it.say; ++mine }, holdMs = 6_000)
        var n = 0
        c.start("system", write = { _, _ -> """{"say": "Line ${++n}."}""" }, transcript = { "" })
        runCurrent()
        assertTrue(c.counsel.value is Counsel.Holding)
        c.skip()
        runCurrent()
        assertEquals("Line 2.", (c.counsel.value as Counsel.Holding).line.say)
        // The officer can send at once; left alone, the line still goes out when the hold passes.
        assertTrue(sent.isEmpty())
        advanceTimeBy(6_500)
        runCurrent()
        assertEquals(listOf("Line 2."), sent)
        assertEquals(Counsel.Waiting, c.counsel.value)
        flow.value = ui(listOf(ev(10, EventType.ANSWER_CARD, card = "yes", shown = 1)))
        advanceTimeBy(900)
        runCurrent()
        c.sendNow()
        runCurrent()
        assertEquals(listOf("Line 2.", "Line 3."), sent)
        flow.value = ui(listOf(ev(10, EventType.ANSWER_CARD, card = "yes", shown = 1), ev(11, EventType.ANSWER_CARD, card = "stop", shown = 2)))
        runCurrent()
        assertEquals(Counsel.Paused(Counsel.Why.STOP), c.counsel.value)
        c.takeOver()
        assertEquals(Counsel.Off, c.counsel.value)
    }

    @Test
    fun aLineWaitsWhileTheOfficersItemIsOnTheStage() = runTest {
        val officer = ev(1, EventType.QUESTION_SHOWN, "Are you hurt?", source = EventSource.PRESET)
        val flow = MutableStateFlow<SessionUi?>(ui(listOf(officer), mode = Mode.SHOW))
        val sent = mutableListOf<String>()
        val c = Counsellor(this, flow, send = { sent += it.say; 1L }, holdMs = 1_000)
        c.start("system", write = { _, _ -> """{"say": "Hello."}""" }, transcript = { "" })
        advanceTimeBy(10_000)
        runCurrent()
        assertTrue("the officer's item is not closed", sent.isEmpty())
        assertTrue("the line stays held", c.counsel.value is Counsel.Holding)
        assertEquals(listOf(officer), flow.value!!.events)
        c.takeOver()
    }

    @Test
    fun anAnswerToAnotherItemDoesNotAnswerTheCounsellorsLine() = runTest {
        val flow = MutableStateFlow<SessionUi?>(ui(emptyList()))
        val sent = mutableListOf<String>()
        val c = Counsellor(this, flow, send = { sent += it.say; sent.size.toLong() }, holdMs = 1_000)
        var n = 0
        c.start("system", write = { _, _ -> """{"say": "Line ${++n}."}""" }, transcript = { "" })
        advanceTimeBy(1_500)
        runCurrent()
        assertEquals(listOf("Line 1."), sent)
        assertEquals(Counsel.Waiting, c.counsel.value)

        flow.value = ui(listOf(ev(5, EventType.ANSWER_CARD, card = "yes", shown = 99)))
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals("an answer to another row gets no next line", listOf("Line 1."), sent)
        assertEquals(Counsel.Waiting, c.counsel.value)

        flow.value = ui(listOf(ev(5, EventType.ANSWER_CARD, card = "yes", shown = 99), ev(6, EventType.ANSWER_CARD, card = "yes", shown = 1)))
        advanceTimeBy(2_500)
        runCurrent()
        assertEquals("its own answer starts the next turn", listOf("Line 1.", "Line 2."), sent)
        c.takeOver()
    }

    @Test
    fun aLineTheStageDidNotTakeIsHeldNotWaiting() = runTest {
        val flow = MutableStateFlow<SessionUi?>(ui(emptyList()))
        var calls = 0
        val c = Counsellor(this, flow, send = { calls++; if (calls < 3) null else 7L }, holdMs = 1_000)
        c.start("system", write = { _, _ -> """{"say": "Line."}""" }, transcript = { "" })
        advanceTimeBy(1_400)
        runCurrent()
        assertEquals("the line was tried again", 2, calls)
        assertTrue("it waits in hand, not on an answer", c.counsel.value is Counsel.Holding)

        advanceTimeBy(1_500)
        runCurrent()
        assertEquals(Counsel.Waiting, c.counsel.value)
        c.takeOver()
    }
}
