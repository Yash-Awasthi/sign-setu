package app.signsetu

import app.signsetu.ui.avatar.AdultBuild
import app.signsetu.ui.avatar.ChildBuild
import app.signsetu.ui.avatar.Gesture
import app.signsetu.ui.avatar.HandShape
import app.signsetu.ui.avatar.Side
import app.signsetu.ui.avatar.Timeline
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
    private val gestures = json.decodeFromString<GesturesFile>(File(content, "gestures.json").readText()).gestures

    @Test
    fun everyGestureUsesKnownAnchorsAndShapes() {
        for (g in gestures) {
            assertTrue("${g.id}: keys out of order", g.keys.zipWithNext().all { (a, b) -> a.t < b.t })
            for (k in g.keys) for (h in listOfNotNull(k.R, k.L)) {
                assertTrue("${g.id}: unknown shape ${h.shape}", h.shape in HandShape.ALL)
                assertTrue("${g.id}: unknown touch ${h.touch}", h.touch in setOf("wrist", "palm", "tip", "thumb"))
                if (h.at != "rest" && h.at != "wrist_opp") {
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
