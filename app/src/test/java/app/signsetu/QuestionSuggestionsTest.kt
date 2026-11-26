package app.signsetu

import app.signsetu.data.content.Question
import app.signsetu.ui.session.threeSuggestions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure pick behind the suggestion strip: asked ids out, content order kept, three at most. */
class QuestionSuggestionsTest {
    private fun q(id: String) = Question(id = id, group = "safety", text = mapOf("en" to "Question $id"))

    private val a = q("a")
    private val b = q("b")
    private val c = q("c")
    private val d = q("d")

    @Test
    fun threeAtMost() {
        assertEquals(listOf("a", "b", "c"), threeSuggestions(listOf(a, b, c, d), emptySet()).map { it.id })
    }

    @Test
    fun askedIdsNeverComeBack() {
        assertEquals(listOf("b", "d"), threeSuggestions(listOf(a, b, c, d), setOf("a", "c")).map { it.id })
        assertTrue(threeSuggestions(listOf(a, b, c, d), setOf("a", "b", "c", "d")).isEmpty())
    }

    @Test
    fun contentOrderIsKept() {
        assertEquals(listOf("d", "b", "a"), threeSuggestions(listOf(d, b, a, c), emptySet()).map { it.id })
    }

    @Test
    fun fewerWhenFewerExist() {
        assertEquals(listOf("a"), threeSuggestions(listOf(a), emptySet()).map { it.id })
        assertEquals(listOf("a", "b"), threeSuggestions(listOf(a, b), emptySet()).map { it.id })
    }

    @Test
    fun emptyInputGivesNothing() {
        assertTrue(threeSuggestions(emptyList(), emptySet()).isEmpty())
    }

    @Test
    fun noRoomMeansNothing() {
        assertTrue(threeSuggestions(listOf(a, b, c), emptySet(), limit = 0).isEmpty())
        assertTrue(threeSuggestions(listOf(a, b, c), emptySet(), limit = -2).isEmpty())
    }

    @Test
    fun aSmallerLimitCaps() {
        assertEquals(listOf("a", "b"), threeSuggestions(listOf(a, b, c), emptySet(), limit = 2).map { it.id })
    }

    @Test
    fun theSameIdIsOfferedOnce() {
        assertEquals(listOf("a", "b"), threeSuggestions(listOf(a, a, b, b), emptySet()).map { it.id })
    }
}
