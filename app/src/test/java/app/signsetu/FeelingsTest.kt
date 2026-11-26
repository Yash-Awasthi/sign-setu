package app.signsetu

import app.signsetu.ui.session.Feeling
import app.signsetu.ui.session.Mood
import app.signsetu.ui.session.PAIN_MAX
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The stage's feeling scale travels in the answer's cue, so the session record keeps the face and the number. */
class FeelingsTest {

    @Test
    fun aFaceAndAPainNumberBecomeOneCueAndComeBack() {
        val feeling = Feeling(Mood.SCARED, 7)
        assertEquals("feel:scared:7", feeling.cue)
        assertEquals(feeling, Feeling.parse(feeling.cue))
        assertEquals("the record's prefix is accepted too", feeling, Feeling.parse("cue:" + feeling.cue))
    }

    @Test
    fun theScaleCannotLeaveItsZeroToTenRange() {
        assertEquals("feel:angry:$PAIN_MAX", Feeling(Mood.ANGRY, 15).cue)
        assertEquals(0, Feeling(Mood.PAIN, -3).cue.substringAfterLast(':').toInt())
        assertEquals(PAIN_MAX, Feeling.parse("cue:feel:sad:99")!!.pain)
        assertEquals(0, Feeling.parse("feel:okay:-1")!!.pain)
    }

    @Test
    fun aCueThatIsNotAFeelingIsRefused() {
        assertNull(Feeling.parse(null))
        assertNull(Feeling.parse(""))
        assertNull(Feeling.parse("cue:body:front:0.5:0.5:chest:left"))
        assertNull("no face", Feeling.parse("feel:7"))
        assertNull("an unknown face", Feeling.parse("feel:happy:7"))
        assertNull("no number", Feeling.parse("feel:sad"))
        assertNull("not a number", Feeling.parse("feel:sad:lots"))
        assertNull("another cue's fields", Feeling.parse("call:mother"))
    }

    @Test
    fun everyFaceIsARealContentCard() {
        val cards = Mood.entries.map { it.card }
        assertEquals("each face names a different card", cards.size, cards.toSet().size)
        for (id in cards) {
            assertTrue("the face's word and picture come from card $id", shipped.cards.containsKey(id))
        }
    }
}
