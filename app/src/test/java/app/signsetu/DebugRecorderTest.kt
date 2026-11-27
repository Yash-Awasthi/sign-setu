package app.signsetu

import app.signsetu.gloss.DebugRecorder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** A correction rewrites a window record: it lands whole or the old record stays, and never as a .part. */
class DebugRecorderTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun aCorrectionRoundTripsAndLeavesNoPartBehind() {
        val dir = folder.newFolder("recordings")
        val window = File(dir, "window_7.json").apply { writeText("""{"eventId":7,"lattice":{"words":[]}}""") }
        DebugRecorder.dir = dir
        try {
            DebugRecorder.correction(7, listOf("HELLO", "YOU"), "hello you")

            val saved = Json.parseToJsonElement(window.readText()).jsonObject
            val fix = saved.getValue("correction").jsonObject
            assertEquals(listOf("HELLO", "YOU"), fix.getValue("gloss").jsonArray.map { it.jsonPrimitive.content })
            assertEquals("hello you", fix.getValue("text").jsonPrimitive.content)
            assertTrue("the rest of the window is kept", saved.containsKey("lattice"))
            assertEquals("no part file is left", emptyList<String>(), dir.list()!!.filter { it.endsWith(".part") })
        } finally {
            DebugRecorder.dir = null
        }
    }
}
