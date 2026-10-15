package app.signsetu

import app.signsetu.data.content.BodyLocation
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.HandAction
import app.signsetu.data.content.LexiconEntry
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.SessionEvent
import app.signsetu.data.settings.whatsappNumber
import app.signsetu.ui.session.withQuestions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {

    private fun entry(id: String, locations: List<BodyLocation>, actions: List<HandAction>) =
        LexiconEntry(id, id, "card", locations, actions, mapOf("en" to id), mapOf("en" to ""))

    private val bundle = ContentBundle(
        groups = emptyList(), questions = emptyList(), categories = emptyList(), cards = emptyMap(),
        lexicon = listOf(
            entry("mother.bindi", listOf(BodyLocation.FOREHEAD), listOf(HandAction.TOUCH)),
            entry("police.salute", listOf(BodyLocation.FOREHEAD), listOf(HandAction.FLAT_HAND)),
            entry("eat.fingers", listOf(BodyLocation.MOUTH), listOf(HandAction.TOUCH)),
            entry("home.roof", listOf(BodyLocation.AWAY), listOf(HandAction.BOTH_HANDS)),
        ).associateBy { it.id },
        languages = emptyList(), subjectUi = emptyMap(), rejected = emptySet(),
    )

    @Test
    fun lookupRanksFullMatchThenLocationThenAction() {
        val ids = bundle.lookup(BodyLocation.FOREHEAD, HandAction.TOUCH).map { it.id }
        assertEquals(listOf("mother.bindi", "police.salute", "eat.fingers"), ids)
    }

    @Test
    fun lookupWithNothingSelectedReturnsNothing() {
        assertTrue(bundle.lookup(null, null).isEmpty())
    }

    private var nextId = 0L
    private fun ev(type: EventType, text: String?) =
        SessionEvent(nextId++, nextId, type, EventSource.SUBJECT, null, text, null, null, null)

    @Test
    fun answersPairWithTheQuestionShownBeforeThem() {
        val paired = listOf(
            ev(EventType.QUESTION_SHOWN, "Water?"),
            ev(EventType.ANSWER_CARD, "Yes"),
            ev(EventType.SYSTEM, "language_finder"),
            ev(EventType.LANGUAGE_CHOSEN, "Bengali"),
            ev(EventType.NOTE, "calm"),
        ).withQuestions()
        assertEquals("Water?", paired[1].second)
        assertNull("a language choice is not an answer to the last question", paired[3].second)
        assertNull("notes are never paired", paired[4].second)
    }

    @Test
    fun whatsappNumbersBecomeInternational() {
        assertEquals("919876543210", whatsappNumber("98765 43210"))
        assertEquals("919876543210", whatsappNumber("09876543210"))
        assertEquals("919876543210", whatsappNumber("+91-98765-43210"))
        assertEquals("447700900123", whatsappNumber("+44 7700 900123"))
        assertNull(whatsappNumber("Interpreter desk"))
        assertNull(whatsappNumber("12345"))
    }
}
