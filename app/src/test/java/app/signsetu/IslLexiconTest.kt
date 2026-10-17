package app.signsetu

import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.DRAFT_CATEGORY
import app.signsetu.data.content.IslSign
import app.signsetu.data.content.IslSigns
import app.signsetu.data.content.Question
import app.signsetu.data.content.SignMode
import app.signsetu.data.content.WordEntry
import app.signsetu.data.content.dictionary
import app.signsetu.data.content.motionFor
import app.signsetu.data.content.planText
import app.signsetu.data.content.search
import app.signsetu.data.content.signBack
import app.signsetu.ui.avatar.AdultBuild
import app.signsetu.ui.avatar.ChildBuild
import app.signsetu.ui.avatar.Gesture
import app.signsetu.ui.avatar.HandKey
import app.signsetu.ui.avatar.HandShape
import app.signsetu.ui.avatar.Key
import app.signsetu.ui.avatar.Timeline
import app.signsetu.ui.avatar.handShape
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IslLexiconTest {

    @Test
    fun measuredShapesParseAndClamp() {
        assertEquals(HandShape.ALL["FIST"], handShape("FIST"))
        assertEquals(HandShape(0.1f, 0.2f, 0.95f, 1f, 1f, 0.5f, -0.3f), handShape("c:10,20,95,100,100,50,-30"))
        assertEquals(HandShape(0f, 1f, 0f, 0f, 0f, 0f, 1f), handShape("c:-5,140,0,0,0,0,200"))
        for (bad in listOf("c:1,2,3", "c:a,0,0,0,0,0,0", "c:", "SPOON")) assertNull(bad, handShape(bad))
    }

    @Test
    fun aMeasuredShapePlaysOnTheRig() {
        val g = Gesture("isl.x", listOf(Key(0f, R = HandKey("chest", shape = "c:0,100,100,100,100,0,0")), Key(0.5f)))
        val pose = Timeline(g, AdultBuild).pose(Timeline.LEAD_IN)
        assertEquals(1f, pose.r.shape.index, 1e-4f)
        assertEquals(0f, pose.r.shape.thumb, 1e-4f)
    }

    private fun gesture(id: String) = Gesture(id, listOf(Key(0f, R = HandKey("chest")), Key(0.4f)))
    private fun isl(word: String, homonym: Boolean = false) =
        IslSign("isl.$word", word, category = "People", rec = "People/$word/MVI_1", homonym = homonym, keys = gesture("isl.$word").keys)

    private val water = WordEntry(listOf("water", "pani"), sign = "sign.water", gesture = "water.cup")
    private val phone = WordEntry(listOf("phone", "mobile"), sign = "phone.ear", gesture = "phone.ear")
    private val small = ContentBundle(
        groups = emptyList(), questions = emptyList(), categories = emptyList(), cards = emptyMap(), lexicon = emptyMap(),
        languages = emptyList(), subjectUi = emptyMap(), rejected = emptySet(),
        gestures = listOf("sign.water", "water.cup", "phone.ear", "isl.telephone", "isl.cell phone", "isl.thank you", "isl.fall", "isl.teacher")
            .associateWith(::gesture),
        words = listOf(water, phone).flatMap { e -> e.w.map { it to e } }.toMap(),
        isl = IslSigns(gestures = listOf(isl("telephone"), isl("cell phone"), isl("thank you"), isl("fall", homonym = true), isl("teacher"))),
        signWords = mapOf("telephone" to "phone", "cell phone" to "mobile"),
    )
    private val signs = setOf(SignMode.SIGNS)

    @Test
    fun recordedSignsComeFirstThenAuthoredThenGestureThenSpelling() {
        assertEquals("isl.teacher", small.motionFor("teacher", null, signs)?.id)
        assertEquals("sign.water", small.motionFor("water", water, signs)?.id)
        assertEquals("water.cup", small.motionFor("water", water, setOf(SignMode.GESTURES))?.id)
        assertNull(small.motionFor("teacher", null, setOf(SignMode.GESTURES)))
        assertNull(small.motionFor("ravi", null, signs))
    }

    @Test
    fun aConceptPrefersTheLabelWrittenAsTyped() {
        assertEquals("isl.cell phone", small.motionFor("mobile", phone, signs)?.id)
        assertEquals("isl.telephone", small.motionFor("phone", phone, signs)?.id)
    }

    @Test
    fun homonymsAndMultiWordSignsAreHandled() {
        assertNull(small.motionFor("fall", null, signs))
        val text = "Thank you, teacher"
        val plan = small.planText(text, signs)
        assertEquals(listOf("Thank you", "teacher"), plan.beats.map { text.substring(it.range) })
        assertEquals(listOf("isl.thank you", "isl.teacher"), plan.motions)
    }

    @Test
    fun dictionaryFindsRecordedAndDraftSignsInEitherLanguage() {
        val dict = small.dictionary()
        assertTrue(dict.single { it.gestureId == "sign.water" }.let { !it.recorded && it.category == DRAFT_CATEGORY })
        assertEquals(listOf("sign.water"), dict.search("PANI").map { it.gestureId })
        assertEquals(listOf("isl.teacher"), dict.search("teach", "People").map { it.gestureId })
        assertTrue(dict.search("teach", DRAFT_CATEGORY).isEmpty())
    }

    // The real content: every lesson question signs, every reader label signs back, every motion stays in frame.

    @Serializable private data class GesturesFile(val gestures: List<Gesture>)
    @Serializable private data class WordsFile(val words: List<WordEntry>)
    @Serializable private data class SignWordsFile(val words: Map<String, String>)

    private val content = File("src/main/assets/content")
    private val json = Json { ignoreUnknownKeys = true }
    private val islFile = json.decodeFromString<IslSigns>(File(content, "isl_signs.json").readText())
    private val authored = listOf("gestures.json", "signs.json").flatMap { json.decodeFromString<GesturesFile>(File(content, it).readText()).gestures }
    private val bundle = ContentBundle(
        groups = emptyList(), questions = emptyList(), categories = emptyList(), cards = emptyMap(), lexicon = emptyMap(),
        languages = emptyList(), subjectUi = emptyMap(), rejected = emptySet(),
        gestures = (authored + islFile.gestures.map { it.gesture }).associateBy { it.id },
        words = json.decodeFromString<WordsFile>(File(content, "words.json").readText()).words.flatMap { e -> e.w.map { it to e } }.toMap(),
        isl = islFile,
        signWords = json.decodeFromString<SignWordsFile>(File(content, "sign_words.json").readText()).words,
    )

    @Test
    fun everyIncludeWordIsRetargetedWithAttribution() {
        val labels = json.parseToJsonElement(File(content, "models/sign_reader.json").readText()).jsonObject["labels"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertTrue(islFile.source.contains("CC BY 4.0"))
        assertEquals(labels.toSet(), islFile.gestures.map { it.word }.toSet())
        for (s in islFile.gestures) {
            assertTrue("${s.id}: no recording", s.rec.count { it == '/' } == 2)
            assertTrue("${s.id}: no category name", s.category in islFile.categories)
            assertTrue("${s.id}: no Hindi", s.hi.isNotBlank())
        }
        // A word read from the camera can be signed back for the subject to confirm.
        for (label in labels) assertNotNull("cannot sign back $label", bundle.signBack(label))
    }

    @Test
    fun lessonQuestionsSignInIslOrderWithoutSpelling() {
        fun motions(text: String) = bundle.planText(text, setOf(SignMode.SIGNS)).motions
        assertEquals(listOf("isl.you", "sign.name", "sign.what"), motions("What is your name?"))
        assertEquals(listOf("isl.you", "sign.name", "sign.what"), motions("आपका नाम क्या है?"))
        assertEquals(listOf("isl.you", "isl.family", "sign.how_many"), motions("How many in your family?"))
        assertEquals("sign.where", motions("Where do you live?").last())
        val lesson = listOf(
            "name", "age", "mobile", "number", "father", "mother", "family", "brother", "sister", "live", "where", "village", "city",
            "district", "state", "near", "school", "class", "teacher", "like", "help", "water", "understand", "again", "thank you",
            "home", "go", "how", "you", "what", "how many", "namaste", "please",
        )
        for (w in lesson) assertTrue("$w is not signed", motions(w).isNotEmpty())
    }

    @Test
    fun aQuestionWithoutAGestureIsSignedThroughTheLexicon() {
        val q = Question("p1", "identity", mapOf("en" to "What is your name?"))
        assertEquals(bundle.planText("What is your name?", setOf(SignMode.SIGNS)).gesture, bundle.gestureFor(q))
        assertEquals("namaste", bundle.gestureFor(q.copy(gesture = "namaste"))?.id)
    }

    @Test
    fun everySignStaysInFrameAndAuthoredSignsKeepEachHandOnItsSide() {
        // Hugging yourself crosses the arms on purpose; recorded signers cross where the sign does.
        val crossing = setOf("cold.hug")
        for (g in bundle.gestures.values) for (build in listOf(ChildBuild, AdultBuild)) {
            val timeline = Timeline(g, build)
            val used = { side: (Key) -> HandKey? -> g.keys.any { side(it)?.at?.let { at -> at != "rest" } == true } }
            val r = used { it.R }
            val l = used { it.L }
            var s = Timeline.LEAD_IN
            while (s <= Timeline.LEAD_IN + g.keys.maxOf { it.t } + 1e-4f) {
                val p = timeline.pose(s)
                if (r) assertTrue("${g.id} right hand leaves the frame at $s", p.r.x in 4f..196f && p.r.y in 4f..196f)
                if (l) assertTrue("${g.id} left hand leaves the frame at $s", p.l.x in 4f..196f && p.l.y in 4f..196f)
                if (r && l && !g.id.startsWith("isl.") && g.id !in crossing) assertTrue("${g.id} crosses at $s", p.r.x <= p.l.x + 4f)
                s += 0.05f
            }
        }
    }
}
