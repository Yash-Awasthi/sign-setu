package app.signsetu

import app.signsetu.data.session.EventSeal
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.SessionRepository
import app.signsetu.export.StationExport
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The station's inbox gets one zip that must stand on its own: every row, the files the rows name, and a declared
 * seal that a reader can recompute from the zip alone (README.txt says how). */
class StationExportTest {
    private val dao = MemoryDao()
    private val startedAt = 1_000L
    private var now = startedAt
    private val repo = SessionRepository(dao, clock = { now })

    private val png = byteArrayOf(1, 2, 3, 4)
    private val wav = ByteArray(44 + 2 * 16_000) { (it % 251).toByte() }

    private fun entries(zip: ByteArray): Map<String, ByteArray> {
        val out = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zip)).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                out[entry.name] = input.readBytes()
            }
        }
        return out
    }

    private fun JsonObject.text(key: String): String? {
        val element = this[key] ?: return null
        return if (element is JsonNull) null else element.jsonPrimitive.content
    }

    private fun JsonObject.number(key: String): Long? = text(key)?.toLongOrNull()

    @Test
    fun everyRowAndItsBytesTravelTogetherWithASealThatRecomputes() = runBlocking {
        val id = repo.start(Profile.ISL_USER, "hi")
        now = 2_000
        repo.log(id, EventType.QUESTION_SHOWN, EventSource.PRESET, "en", text = "Are you safe?", cardId = "safe")
        now = 3_000
        repo.log(id, EventType.ANSWER_CARD, EventSource.SUBJECT, "hi", text = "Yes", cardId = "yes")
        now = 4_000
        repo.log(id, EventType.ANSWER_DRAWING, EventSource.SUBJECT, drawingPng = png)
        now = 5_000
        repo.log(id, EventType.ANSWER_SPOKEN, EventSource.SUBJECT, "hi", text = "1.0", audioWav = wav)

        val files = entries(StationExport.build(repo, id)!!)

        assertEquals(
            "one README, the session and the rows",
            setOf("README.txt", "session.json", "events.json"),
            files.keys.filter { '/' !in it }.toSet(),
        )
        assertTrue("the reader is told how the chain is built", files.getValue("README.txt").decodeToString().contains("signsetu-chain-v1"))

        val session = Json.parseToJsonElement(files.getValue("session.json").decodeToString()).jsonObject
        assertEquals("1", session.getValue("format").jsonPrimitive.content)
        assertEquals(id, session.getValue("id").jsonPrimitive.content)
        assertEquals("$startedAt", session.text("startedAt"))
        val seal = session.getValue("seal").jsonObject

        val rows = Json.parseToJsonElement(files.getValue("events.json").decodeToString()).jsonArray.map { it.jsonObject }
        assertEquals(4, rows.size)
        val ids = rows.map { it.number("id")!! }
        assertEquals("rows in the order written", ids.sorted(), ids)
        assertEquals(4L, seal.number("count"))

        // What a verifier rebuilds from the zip: the genesis, then each row's seal over the bytes the zip carries.
        var previous = EventSeal.genesis(id, startedAt)
        val hashes = mutableListOf<String>()
        for (row in rows) {
            val drawing = row.text("drawing")?.let { files.getValue(it) }
            val audio = row.text("audio")?.let { files.getValue(it) }
            val signs = row.text("signs")?.let { files.getValue(it) }
            val expected = EventSeal.seal(
                previous, id, row.number("ts")!!, row.text("type")!!, row.text("source")!!,
                row.text("lang"), row.text("text"), row.text("cardId"), row.text("lexiconId"),
                drawing, row.text("itemId"), row.text("reason"), row.number("shownId"), row.text("via"),
                audio, signs,
            )
            assertEquals("row ${row.number("id")} does not match the zip's own bytes", expected, row.text("hash"))
            previous = expected
            hashes += expected
        }
        assertEquals("the declared head is the last recomputed seal", previous, seal.text("head"))
        assertEquals("the declared root is over the recomputed seals", EventSeal.merkleRoot(hashes), seal.text("root"))
        assertEquals("the signed line is the one a QR code carries", EventSeal.sealLine(id, 4, previous, EventSeal.merkleRoot(hashes)), seal.text("line"))

        // Every file a row names is in the zip, and its bytes are the bytes the seal covers.
        val named = rows.flatMap { listOfNotNull(it.text("drawing"), it.text("audio"), it.text("signs")) }
        assertEquals(2, named.size)
        named.forEach { assertNotNull("missing $it", files[it]) }
        assertArrayEquals(png, files.getValue(rows.single { it.text("type") == EventType.ANSWER_DRAWING.name }.text("drawing")!!))
        assertArrayEquals(wav, files.getValue(rows.single { it.text("type") == EventType.ANSWER_SPOKEN.name }.text("audio")!!))
    }

    @Test
    fun aStationFileNameSaysWhatTheConversationIs() {
        assertEquals("conversation 12345678.zip", StationExport.name(null, "1234567890-abc"))
        assertEquals("conversation 12345678.zip", StationExport.name("   ", "1234567890-abc"))
        assertEquals("FIR 214/2026 12345678.zip", StationExport.name("FIR 214/2026", "1234567890-abc"))
    }
}
