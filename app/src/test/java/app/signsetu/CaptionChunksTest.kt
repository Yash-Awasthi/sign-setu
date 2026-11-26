package app.signsetu

import app.signsetu.ui.session.CAPTION_SP
import app.signsetu.ui.session.captionChunks
import app.signsetu.ui.session.chunkFor
import app.signsetu.ui.session.localHighlight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The caption's line breaks and the light moved onto the line drawn: the source of the wrong-word bug. */
class CaptionChunksTest {
    private val sentence = "Do you want water or food before we begin the next question"

    @Test
    fun aShortTextIsOneChunk() {
        assertEquals(listOf(0..4), captionChunks("hello"))
        assertEquals(listOf(0..1), captionChunks("hi"))
    }

    @Test
    fun aLongSentenceBreaksAtWordsWithinTheLimit() {
        val text = "one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty"
        val chunks = captionChunks(text)

        assertTrue("more than one line", chunks.size >= 2)
        chunks.forEach { chunk ->
            assertTrue("not empty", chunk.first <= chunk.last)
            assertTrue("within the limit: " + text.substring(chunk), text.substring(chunk).length <= 32)
            assertTrue("no leading space", !text[chunk.first].isWhitespace())
            assertTrue("no trailing space", !text[chunk.last].isWhitespace())
            if (chunk.last + 1 < text.length) assertTrue("cut at a word boundary: " + text.substring(chunk), text[chunk.last + 1].isWhitespace())
        }
        assertEquals("covers the text with only the spaces between", text, chunks.joinToString(" ") { text.substring(it) })
    }

    @Test
    fun aChunkNeverHoldsANewline() {
        val text = "first line here\nsecond line follows after the first one ends"
        captionChunks(text).forEach { chunk ->
            assertTrue("no newline in " + text.substring(chunk), !text.substring(chunk).contains('\n'))
        }
    }

    @Test
    fun theChunkHoldingTheWordIsChosen() {
        val chunks = captionChunks(sentence)
        val water = sentence.indexOf("water")
        val next = sentence.indexOf("next")
        assertTrue("the word is where the test thinks", water >= 0 && next >= 0)

        val waterChunk = chunkFor(chunks, water..water + 4, sentence)
        val nextChunk = chunkFor(chunks, next..next + 3, sentence)
        assertTrue("water is in the chosen chunk", water in chunks[waterChunk])
        assertTrue("next is in the chosen chunk", next in chunks[nextChunk])
        assertTrue("they are different lines", nextChunk != waterChunk)
        assertEquals("nothing lit means the first line", 0, chunkFor(chunks, null, sentence))
    }

    @Test
    fun theHighlightMovesIntoTheChunk() {
        assertEquals("moved by the chunk start", 5..10, localHighlight(10..20, 15..25))
        assertEquals("clipped at the chunk end", 8..9, localHighlight(0..9, 8..14))
        assertEquals("kept whole inside", 2..4, localHighlight(10..20, 12..14))
        assertNull("a light on another line", localHighlight(10..20, 0..5))
        assertNull("no light at all", localHighlight(10..20, null))
    }

    @Test
    fun anEmptyTextIsNoLinesAndNoCrash() {
        assertTrue(captionChunks("").isEmpty())
        assertEquals("no line to pick", 0, chunkFor(emptyList(), 0..0, ""))
        assertEquals(26f, CAPTION_SP, 0f)
    }
}
