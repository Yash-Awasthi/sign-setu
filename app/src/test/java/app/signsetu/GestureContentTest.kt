package app.signsetu

import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.Gesture
import app.signsetu.data.content.HandKey
import app.signsetu.data.content.Key
import app.signsetu.data.content.Phrase
import app.signsetu.data.content.QuestionKind
import app.signsetu.data.content.SignMode
import app.signsetu.data.content.WordEntry
import app.signsetu.data.content.chain
import app.signsetu.data.content.planText
import app.signsetu.data.content.questionKind
import app.signsetu.data.content.spelling
import app.signsetu.ui.session.spelledIndex
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureContentTest {

    @Serializable private data class GesturesFile(val gestures: List<Gesture>)

    private val content = ASSETS
    private val json = Json { ignoreUnknownKeys = true }
    private val gestures = listOf("gestures.json", "signs.json").flatMap { json.decodeFromString<GesturesFile>(File(content, it).readText()).gestures }
    private val recorded = json.decodeFromString<GesturesFile>(File(content, "isl_signs.json").readText()).gestures

    @Serializable private data class PhrasesFile(val phrases: List<Phrase>)

    @Test
    fun everyPhraseSignsWithReviewedGesturesInOrder() {
        val byId = gestures.associateBy { it.id }
        val phrases = json.decodeFromString<PhrasesFile>(File(content, "phrases.json").readText()).phrases
        for (p in phrases) for (slot in p.slots) {
            val ids = p.signs.map { if (it == Phrase.SLOT) slot.sign else it }
            ids.forEach { assertNotNull("${p.id}/${slot.card}: no gesture $it", byId[it]) }
            val chained = chain(p.id, ids.map { byId.getValue(it!!) })
            assertTrue("${p.id}/${slot.card}: keys out of order", chained.keys.zipWithNext().all { (a, b) -> a.t < b.t })
            assertEquals(ids.sumOf { byId.getValue(it!!).keys.size }, chained.keys.size)
            // Phrases are voiced per language or not at all; English and Hindi always are.
            val voiced = slot.text.keys.filter { it in setOf("en", "hi") || File(content, "audio/$it").list()?.any { f -> f.startsWith("phrase.") } == true }
            for (code in voiced) {
                assertTrue("${p.id}/${slot.card}: no $code audio", File(content, "audio/$code/phrase.${p.id}.${slot.card}.ogg").exists())
            }
        }
    }

    @Test
    fun aPictureBeatHoldsThePreviousPoseInsteadOfDroppingToRest() {
        val sign = gestures.first { it.keys.last().R != null }
        val pause = Gesture("pause", listOf(Key(0f), Key(0.6f)))
        val chained = chain("text", listOf(sign, pause))
        val held = chained.keys.takeLast(2)
        held.forEach { assertEquals(sign.keys.maxBy { it.t }.R, it.R) }
    }

    @Test
    fun everyLexiconEntryAndQuestionHasAGesture() {
        val ids = gestures.map { it.id }.toSet()
        val lexicon = json.parseToJsonElement(File(content, "lexicon.json").readText()).jsonObject["entries"]!!.jsonArray
        for (e in lexicon) {
            val id = e.jsonObject["id"]!!.jsonPrimitive.content
            assertTrue("lexicon $id has no gesture", id in ids)
        }
        val questions = json.parseToJsonElement(File(content, "triage.json").readText()).jsonObject["questions"]!!.jsonArray
        for (q in questions) {
            val o = q.jsonObject
            val ref = (o["gesture"] ?: o["lexicon"])?.jsonPrimitive?.content ?: continue
            assertTrue("question ${o["id"]} refers to missing gesture $ref", ref in ids)
        }
    }

    @Serializable private data class WordsFile(val words: List<WordEntry>)

    @Test
    fun everyVocabularyWordPointsAtRealSignsAndPictures() {
        // words.json links both authored signs (signs.json) and recorded ISLRTC signs (isl_signs.json).
        val ids = (gestures + recorded).map { it.id }.toSet()
        val cards = json.parseToJsonElement(File(content, "cards.json").readText()).jsonObject["cards"]!!.jsonArray
            .map { it.jsonObject["id"]!!.jsonPrimitive.content }.toSet()
        val words = json.decodeFromString<WordsFile>(File(content, "words.json").readText()).words
        val forms = words.flatMap { it.w }
        assertEquals("a written form is listed twice", forms.size, forms.toSet().size)
        for (e in words) {
            assertTrue("${e.w.first()}: forms must be lower case", e.w.all { it == it.lowercase() })
            listOfNotNull(e.sign, e.gesture).forEach { assertTrue("${e.w.first()}: no gesture $it", it in ids) }
            e.card?.let { assertTrue("${e.w.first()}: no card $it", it in cards) }
        }
        for (c in 'a'..'z') assertTrue("no letter $c", "letter.$c" in ids)
        for (d in '0'..'9') assertTrue("no digit $d", "digit.$d" in ids)
        for (ak in listOf("अ", "क", "प", "आ", "न", "क्ष", "त्र", "ज्ञ", "अं", "अः")) assertTrue("no letter.hi.$ak", "letter.hi.$ak" in ids)
    }

    @Test
    fun hindiIsSpelledAsAkshara() {
        assertEquals(listOf("प", "आ", "न", "ई"), spelling("पानी"))
        assertEquals(listOf("घ", "र"), spelling("घर"))
        assertEquals(listOf("म", "द", "द"), spelling("मदद"))
        assertEquals(listOf("ब", "च", "च", "आ"), spelling("बच्चा"))
        assertEquals(listOf("र", "न", "ग"), spelling("रंग"))
        assertEquals(listOf("ज", "म", "ई", "न"), spelling("ज़मीन"))
        assertEquals(listOf("2", "0", "2", "6"), spelling("२०२६"))
        assertEquals(listOf("r", "a", "m"), spelling("Ram!"))
        assertEquals(listOf("श", "इ", "क्ष", "क"), spelling("शिक्षक"))
        assertEquals(listOf("अं"), spelling("अं"))
        assertEquals(listOf("letter.hi.प", "letter.hi.आ", "letter.hi.न", "letter.hi.ई"), bundle.planText("पानी", setOf(SignMode.SPELL)).motions)
        assertEquals(listOf("letter.hi.श", "letter.hi.इ", "letter.hi.क्ष", "letter.hi.क"), bundle.planText("शिक्षक", setOf(SignMode.SPELL)).motions)
        assertEquals(listOf("श", "इ", "क्ष", "क"), bundle.planText("शिक्षक", setOf(SignMode.SPELL)).beats.single().letters)
    }

    @Test
    fun theLetterTileLightsOneAksharaAtATime() {
        // पानी is four signs; शिक्षक is श, इ, क्ष, क because the conjunct counts as one akshara.
        for ((word, units) in listOf("पानी" to listOf("प", "आ", "न", "ई"), "शिक्षक" to listOf("श", "इ", "क्ष", "क"))) {
            val beat = bundle.planText(word, setOf(SignMode.SPELL)).beats.single()
            assertEquals(units, beat.letters)
            // A sample in the middle of each unit lights that unit: none skipped, none out of order.
            val lit = units.indices.map { i ->
                spelledIndex(beat, beat.start + (i + 0.5f) / units.size * (beat.end - beat.start))
            }
            assertEquals(units.indices.toList(), lit)
            assertEquals(0, spelledIndex(beat, beat.start))
            assertEquals(units.lastIndex, spelledIndex(beat, beat.end - 1e-4f))
        }
        // A beat that only holds a picture has no tile to advance.
        val picture = bundle.planText("brother", setOf(SignMode.PICTURES)).beats.single()
        assertEquals(null, picture.letters)
        assertEquals(-1, spelledIndex(picture, picture.start))
    }

    private val bundle by lazy {
        ContentBundle(
            gestures = gestures.associateBy { it.id },
            words = json.decodeFromString<WordsFile>(File(content, "words.json").readText()).words.flatMap { e -> e.w.map { it to e } }.toMap(),
        )
    }

    private fun signed(text: String, modes: Set<SignMode> = setOf(SignMode.SIGNS)) =
        bundle.planText(text, modes).beats.map { text.substring(it.range).lowercase() }

    @Test
    fun signsFollowIslOrder() {
        assertEquals(listOf("your", "mother", "where"), signed("Where is your mother?"))
        assertEquals(listOf("i", "hungry", "not"), signed("I am not hungry"))
        assertEquals("yesterday", signed("Did he hit you yesterday?", setOf(SignMode.SIGNS, SignMode.SPELL)).first())
        assertEquals(listOf("kal", "tum", "kahan"), signed("tum kal kahan the?"))
        // Gestures alone keep the typed order, and Hindi "do" gives only at the end of a sentence.
        assertEquals(listOf("where", "your", "mother"), signed("Where is your mother?", setOf(SignMode.GESTURES)))
        assertEquals(listOf("paani", "do"), signed("paani do"))
        assertEquals(listOf("you", "know"), signed("do you know"))
        // Each beat still lights its own typed word, so highlighting follows the reordered signs.
        val text = "where is your mother"
        val beats = bundle.planText(text, setOf(SignMode.SIGNS)).beats
        assertEquals(text.indexOf("where"), beats.last().range.first)
        assertTrue(beats.zipWithNext().all { (a, b) -> a.end <= b.start + 1e-4f })
    }

    @Test
    fun typedTextIsSignedWordByWord() {
        val text = "Is Ravi your brother?"
        val plan = bundle.planText(text, setOf(SignMode.GESTURES, SignMode.SPELL, SignMode.PICTURES))
        // "Is" is a grammar word, "Ravi" is spelled, "your" and "brother" have gestures, and "?" adds the asking face.
        assertEquals(listOf("Ravi", "your", "brother"), plan.beats.map { text.substring(it.range) })
        assertEquals(listOf("r", "a", "v", "i"), plan.beats[0].letters)
        assertEquals("brother", plan.beats[2].card)
        assertTrue(plan.beats.zipWithNext().all { (a, b) -> a.end <= b.start + 1e-4f })
        val statement = bundle.planText(text.removeSuffix("?"), setOf(SignMode.GESTURES, SignMode.SPELL, SignMode.PICTURES))
        assertEquals(bundle.gestures.getValue("ask").keys.size, plan.gesture!!.keys.size - statement.gesture!!.keys.size)
        // With everything off but pictures, a word with a picture still holds the stage for a moment.
        val still = bundle.planText("water", setOf(SignMode.PICTURES))
        assertEquals("water", still.beats.single().card)
        assertTrue(still.beats.single().end > 0f)
        assertEquals(null, bundle.planText("Ravi", setOf(SignMode.GESTURES)).gesture)
        assertEquals(listOf(listOf("r", "a", "v", "i")), bundle.planText("Ravi's", setOf(SignMode.SPELL)).beats.map { it.letters })
        // With Signs on, a word that has only an everyday gesture plays the gesture rather than being spelled.
        assertEquals(listOf(null), bundle.planText("hungry", setOf(SignMode.SIGNS, SignMode.SPELL)).beats.map { it.letters })
    }

    @Test
    fun questionsGetTheirFace() {
        assertEquals(QuestionKind.YES_NO, questionKind("Are you hurt?"))
        assertEquals(QuestionKind.WH, questionKind("Where is your mother?"))
        assertEquals(QuestionKind.YES_NO, questionKind("क्या आपको चोट लगी है?"))
        assertEquals(QuestionKind.WH, questionKind("आप कहाँ रहते हैं?"))
        assertEquals(QuestionKind.WH, questionKind("kya tum kahan rehte ho?"))
        assertEquals(null, questionKind("Ravi"))
    }



    @Test
    fun aFollowingWordStartsFromTheHandTheLastOneEndedOn() {
        val first = Gesture("one", listOf(Key(0f, R = HandKey(at = "chest", shape = "FLAT")), Key(0.4f, R = HandKey(at = "chest", shape = "FLAT"))))
        val second = Gesture("two", listOf(Key(0f), Key(0.4f, R = HandKey(at = "front", shape = "POINT"))))
        val chained = chain("both", listOf(first, second))
        val end = chained.keys.first { it.t > 0.4f }
        // The second word opens on the first word's hand, not on the rest pose.
        assertEquals(first.keys.last().R, end.R)
        assertNotEquals(HandKey(), end.R)
    }

    @Test
    fun theFirstWordOfASentenceStillStartsFromRest() {
        val only = Gesture("one", listOf(Key(0f), Key(0.4f, R = HandKey(at = "front"))))
        val chained = chain("single", listOf(only))
        assertEquals(HandKey(), chained.keys.first().R)
    }
}
