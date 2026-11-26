package app.signsetu

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every English resource has a Hindi copy. A missing one does not fail the build, it silently falls back. */
class StringParityTest {

    private fun entries(dir: File, tag: String): Map<String, String> {
        val factory = DocumentBuilderFactory.newInstance()
        val out = mutableMapOf<String, String>()
        for (file in dir.listFiles { f -> f.isFile && f.name.endsWith(".xml") }.orEmpty()) {
            val root = factory.newDocumentBuilder().parse(file).documentElement
            val nodes = root.childNodes
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                if (node.nodeName != tag) continue
                val name = node.attributes?.getNamedItem("name")?.nodeValue ?: continue
                out[name] = node.textContent.orEmpty()
            }
        }
        return out
    }

    private val english = entries(File("src/main/res/values"), "string")
    private val hindi = entries(File("src/main/res/values-hi"), "string")
    private val englishPlurals = entries(File("src/main/res/values"), "plurals")
    private val hindiPlurals = entries(File("src/main/res/values-hi"), "plurals")

    @Test
    fun every_english_string_has_a_hindi_copy() {
        assertEquals(emptySet<String>(), english.keys - hindi.keys)
    }

    @Test
    fun every_english_plural_has_a_hindi_copy() {
        assertEquals(emptySet<String>(), englishPlurals.keys - hindiPlurals.keys)
    }

    @Test
    fun every_string_keeps_the_same_placeholders() {
        val placeholder = Regex("%(?:\\d+\\$)?[.\\d]*[a-zA-Z]")
        for ((id, text) in english) {
            val expected = placeholder.findAll(text).map { it.value }.toSet()
            val actual = placeholder.findAll(hindi[id].orEmpty()).map { it.value }.toSet()
            assertEquals("placeholders in $id", expected, actual)
        }
    }
}
