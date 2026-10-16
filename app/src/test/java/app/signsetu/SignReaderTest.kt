package app.signsetu

import androidx.compose.ui.geometry.Offset
import app.signsetu.camera.LandmarkFrame
import app.signsetu.sign.LandmarkSignReader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

class SignReaderTest {
    private val models = File("src/main/assets/content/models")
    private val fixture = Json.parseToJsonElement(javaClass.getResource("/sign_reader_fixture.json")!!.readText()).jsonObject
    private val reader = LandmarkSignReader(
        File(models, "sign_reader.json").readText(),
        File(models, "sign_reader.bin").readBytes(),
    )
    private val aspect = fixture.getValue("aspect").jsonPrimitive.float

    private fun points(e: JsonElement?): List<Offset>? = (e as? JsonArray)?.map { it.jsonPrimitive.float }?.chunked(2) { Offset(it[0], it[1]) }

    private val cases = fixture.getValue("cases").jsonArray.map { c ->
        val frames = c.jsonObject.getValue("frames").jsonArray.mapIndexed { i, f ->
            val o = f.jsonObject
            LandmarkFrame(i * 33L, leftHand = points(o["left"]), rightHand = points(o["right"]), pose = points(o["pose"]), aspect = aspect)
        }
        frames to c.jsonObject.getValue("logits").jsonArray.map { it.jsonPrimitive.float }
    }

    @Test
    fun logitsMatchPython() {
        for ((frames, expected) in cases) {
            val logits = reader.logits(frames)!!
            assertEquals(expected.size, logits.size)
            val worst = expected.indices.maxOf { abs(expected[it] - logits[it]) }
            assertTrue("max logit difference $worst", worst < 1e-3f)
        }
    }

    @Test
    fun guessesAreTopFiveProbabilities() {
        val guesses = reader.read(cases[0].first)
        assertEquals(5, guesses.size)
        assertTrue(guesses.zipWithNext().all { (a, b) -> a.score >= b.score })
        assertTrue(guesses.sumOf { it.score.toDouble() } <= 1.0001)
        assertTrue(reader.read(listOf(LandmarkFrame(0, rightHand = List(21) { Offset(0.5f, 0.5f) }))).isEmpty())
    }

    @Test
    fun forwardPassTime() {
        val frames = cases[0].first
        repeat(20) { reader.logits(frames) }
        val runs = 100
        val start = System.nanoTime()
        repeat(runs) { reader.logits(frames) }
        println("sign reader forward pass: ${(System.nanoTime() - start) / runs / 1000} us")
    }
}
