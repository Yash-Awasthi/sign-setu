package app.signsetu

import app.signsetu.data.content.Phrase
import app.signsetu.ui.avatar.AdultBuild
import app.signsetu.ui.avatar.ChildBuild
import app.signsetu.ui.avatar.Gesture
import app.signsetu.ui.avatar.HandShape
import app.signsetu.ui.avatar.Side
import app.signsetu.ui.avatar.Timeline
import app.signsetu.ui.avatar.chain
import app.signsetu.ui.avatar.point
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.SignMode
import app.signsetu.data.content.WordEntry
import app.signsetu.data.content.planText
import app.signsetu.data.content.spelling
import app.signsetu.ui.avatar.solveElbow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.hypot

class AvatarTest {

    @Serializable private data class GesturesFile(val gestures: List<Gesture>)

    private val content = File("src/main/assets/content")
    private val json = Json { ignoreUnknownKeys = true }
    private val gestures = listOf("gestures.json", "signs.json").flatMap { json.decodeFromString<GesturesFile>(File(content, it).readText()).gestures }

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
            for (code in slot.text.keys) {
                assertTrue("${p.id}/${slot.card}: no $code audio", File(content, "audio/$code/phrase.${p.id}.${slot.card}.ogg").exists())
            }
        }
    }

    @Test
    fun everyGestureUsesKnownAnchorsAndShapes() {
        for (g in gestures) {
            assertTrue("${g.id}: keys out of order", g.keys.zipWithNext().all { (a, b) -> a.t < b.t })
            for (k in g.keys) for (h in listOfNotNull(k.R, k.L)) {
                assertTrue("${g.id}: unknown shape ${h.shape}", h.shape in HandShape.ALL)
                assertTrue("${g.id}: unknown touch ${h.touch}", h.touch in setOf("wrist", "palm", "tip", "thumb"))
                if (h.at != "rest" && h.at !in Timeline.OTHER_HAND) {
                    for (build in listOf(ChildBuild, AdultBuild)) {
                        assertNotNull("${g.id}: unknown anchor ${h.at}", build.anchor(h.at, Side.R))
                    }
                }
            }
        }
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
        val ids = gestures.map { it.id }.toSet()
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
    }

    @Test
    fun hindiIsSpelledInLatinLetters() {
        assertEquals("pani", spelling("पानी"))
        assertEquals("ghar", spelling("घर"))
        assertEquals("madad", spelling("मदद"))
        assertEquals("bachcha", spelling("बच्चा"))
        assertEquals("rang", spelling("रंग"))
        assertEquals("zamin", spelling("ज़मीन"))
        assertEquals("ram", spelling("Ram!"))
    }

    @Test
    fun typedTextIsSignedWordByWord() {
        val bundle = ContentBundle(
            groups = emptyList(), questions = emptyList(), categories = emptyList(), cards = emptyMap(), lexicon = emptyMap(),
            languages = emptyList(), subjectUi = emptyMap(), rejected = emptySet(),
            gestures = gestures.associateBy { it.id },
            words = json.decodeFromString<WordsFile>(File(content, "words.json").readText()).words.flatMap { e -> e.w.map { it to e } }.toMap(),
        )
        val text = "Is Ravi your brother?"
        val plan = bundle.planText(text, setOf(SignMode.GESTURES, SignMode.SPELL, SignMode.PICTURES))
        // "Is" is a grammar word, "Ravi" is spelled, "your" and "brother" have gestures, and "?" adds the asking face.
        assertEquals(listOf("Ravi", "your", "brother"), plan.beats.map { text.substring(it.range) })
        assertEquals("ravi", plan.beats[0].letters)
        assertEquals("brother", plan.beats[2].card)
        assertTrue(plan.beats.zipWithNext().all { (a, b) -> a.end <= b.start + 1e-4f })
        val statement = bundle.planText(text.removeSuffix("?"), setOf(SignMode.GESTURES, SignMode.SPELL, SignMode.PICTURES))
        assertEquals(bundle.gestures.getValue("ask").keys.size, plan.gesture!!.keys.size - statement.gesture!!.keys.size)
        // With everything off but pictures, a word with a picture still holds the stage for a moment.
        val still = bundle.planText("water", setOf(SignMode.PICTURES))
        assertEquals("water", still.beats.single().card)
        assertTrue(still.beats.single().end > 0f)
        assertEquals(null, bundle.planText("Ravi", setOf(SignMode.GESTURES)).gesture)
        assertEquals(listOf("ravi"), bundle.planText("Ravi's", setOf(SignMode.SPELL)).beats.map { it.letters })
        // With Signs on, a word that has only an everyday gesture plays the gesture rather than being spelled.
        assertEquals(listOf(null), bundle.planText("hungry", setOf(SignMode.SIGNS, SignMode.SPELL)).beats.map { it.letters })
    }

    @Test
    fun aHandCanBePlacedOnTheOtherHandsFingertip() {
        val letter = gestures.single { it.id == "letter.e" }
        for (build in listOf(ChildBuild, AdultBuild)) {
            val pose = Timeline(letter, build).pose(Timeline.LEAD_IN + 0.2f)
            val target = pose.l.point("index", Side.L, build.handScale)
            val tip = pose.r.point("index", Side.R, build.handScale)
            assertEquals("right index tip meets left index tip", 0f, hypot(target.first - tip.first, target.second - tip.second), 3f)
        }
    }

    @Test
    fun elbowKeepsBothBonesAtLength() {
        val (ex, ey) = solveElbow(61f, 140f, 90f, 80f, 38f, 35f, Side.R, 1f)
        assertEquals(38f, hypot(ex - 61f, ey - 140f), 0.05f)
        assertEquals(35f, hypot(90f - ex, 80f - ey), 0.05f)
        // "Out" bends the right elbow away from the body's midline (the viewer's left).
        assertTrue(ex < 75f)
    }

    @Test
    fun gesturesStartAndEndAtRest() {
        val rest = Timeline.restPose(ChildBuild)
        for (g in gestures) {
            val t = Timeline(g, ChildBuild)
            for (pose in listOf(t.pose(0f), t.pose(t.duration))) {
                assertEquals("${g.id} right hand x", rest.r.x, pose.r.x, 0.01f)
                assertEquals("${g.id} right hand y", rest.r.y, pose.r.y, 0.01f)
                assertEquals("${g.id} left hand x", rest.l.x, pose.l.x, 0.01f)
            }
        }
    }
}
