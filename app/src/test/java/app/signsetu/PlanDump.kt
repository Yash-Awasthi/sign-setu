package app.signsetu

import app.signsetu.data.content.Gesture
import app.signsetu.data.content.SignMode
import app.signsetu.data.content.planText
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Writes each recorded sentence as the character would sign it from typed text (isl_signs.json format), so the
 * review renderer can play it beside the sentence's own clip. Runs only when PLAN_DUMP_OUT names the output file:
 *
 *     PLAN_DUMP_OUT=plans.json gradlew :app:testStandardDebugUnitTest --tests app.signsetu.PlanDump
 */
class PlanDump {
    @Test
    fun dump() {
        val out = System.getenv("PLAN_DUMP_OUT")
        assumeTrue(out != null)
        // The modes a signer's profile types with by default.
        val modes = setOf(SignMode.SIGNS, SignMode.SPELL, SignMode.PICTURES)
        val gestures = shipped.clips.values.mapNotNull { c ->
            shipped.planText(c.sentence, modes).gesture?.let { Gesture(c.sentence, it.keys) }
        }
        val json = Json { encodeDefaults = false }
        val list = json.encodeToJsonElement(ListSerializer(Gesture.serializer()), gestures)
        File(out!!).writeText(buildJsonObject { put("source", "planText"); put("gestures", list) }.toString())
        println("plans: ${gestures.size} of ${shipped.clips.size} sentences -> $out")
    }
}
