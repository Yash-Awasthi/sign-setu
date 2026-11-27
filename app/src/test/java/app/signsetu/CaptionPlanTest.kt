package app.signsetu

import app.signsetu.data.content.CAPTION_MODES
import app.signsetu.data.content.TOKEN
import app.signsetu.data.content.planText
import app.signsetu.ui.session.RuleSteps
import app.signsetu.ui.session.linePlan
import app.signsetu.ui.session.ruleText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A caption words.json cannot read still signs: the English plan plays and only the light is lost. */
class CaptionPlanTest {

    /** words.json holds Latin and Devanagari forms, so a Tamil or Urdu caption lights nothing but must still sign. */
    @Test
    fun aTamilOrUrduCaptionStillSignsTheWholeQuestion() {
        val q = shipped.questions.first { it.text["en"] != null && it.text["ta"] != null && it.text["ur"] != null }
        val english = q.text.getValue("en")
        val en = shipped.planText(english, CAPTION_MODES)
        assertNotNull("the English wording signs", en.gesture)
        assertTrue("the English wording has words", en.beats.isNotEmpty())

        for (lang in listOf("ta", "ur")) {
            val shown = q.text.getValue(lang)
            assertFalse("$lang has no word words.json knows", shown.lightsAWord())
            val plan = linePlan(shipped, english, shown, CAPTION_MODES)
            assertNotNull("$lang still signs", plan.gesture)
            assertEquals("$lang covers every sign of the English plan", en.beats.size, plan.beats.size)
            plan.beats.zip(en.beats).forEach { (sign, beat) ->
                assertEquals("$lang keeps the sign's timing", beat.start, sign.start, 0f)
                assertEquals("$lang keeps the sign's end", beat.end, sign.end, 0f)
                assertTrue("$lang lights no word", sign.range.isEmpty())
            }
        }
    }

    @Test
    fun aHindiCaptionStillLightsItsMatchingWords() {
        val pair = shipped.questions.mapNotNull { q ->
            q.text["en"]?.let { en -> q.text["hi"]?.let { hi -> en to hi } }
        }.first { (en, hi) -> linePlan(shipped, en, hi, CAPTION_MODES).beats.any { !it.range.isEmpty() } }
        val (english, hindi) = pair

        val plan = linePlan(shipped, english, hindi, CAPTION_MODES)
        assertNotNull("Hindi still signs", plan.gesture)
        val lit = plan.beats.filter { !it.range.isEmpty() }
        assertTrue("Hindi lights a word", lit.isNotEmpty())
        lit.forEach { b ->
            val word = hindi.substring(b.range).lowercase()
            assertTrue("\"$word\" is a caption word with a words.json entry", word in shipped.words)
        }
    }

    @Test
    fun aLanguageWithNoWordMapNeverLosesThePlan() {
        val keys = RuleSteps.map { it.key } + listOf("okay", "thanks")
        var checked = 0
        for (lang in listOf("ta", "ur")) {
            for (key in keys) {
                val english = ruleText(shipped, key, "en")
                val shown = ruleText(shipped, key, lang)
                assertNotNull("$key has English words", english)
                assertNotNull("$key has $lang words", shown)
                assertFalse("$key $lang lights nothing", shown!!.lightsAWord())
                val plan = linePlan(shipped, english!!, shown, CAPTION_MODES)
                assertNotNull("$key still signs in $lang", plan.gesture)
                assertTrue("$key $lang lights nothing", plan.beats.all { it.range.isEmpty() })
                checked++
            }
        }
        assertEquals("every rule and stage line, in both languages", keys.size * 2, checked)
    }

    private fun String.lightsAWord() = TOKEN.findAll(this).any { it.value.lowercase() in shipped.words }
}
