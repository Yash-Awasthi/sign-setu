package app.signsetu

import app.signsetu.data.settings.interpreterUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class InterpreterCallTest {
    private val sources = File("src/main/java").walk().filter { it.extension == "kt" }.associateWith { it.readLines() }

    /** The interpreter is called by hand only: the call is handed out solely as a button's click, never invoked by
     * code (low confidence, failures or timeouts), and the app opens only from a confirmation dialog's button. */
    @Test
    fun theCallStartsOnlyFromTheButtonAndItsConfirmation() {
        for ((file, lines) in sources) for ((i, line) in lines.withIndex()) {
            val where = "${file.name}:${i + 1}: $line"
            assertTrue(where, !Regex("""\b(onCallInterpreter|interpreterCall)\s*(\?\.invoke|\.invoke|\()""").containsMatchIn(line))
            if (Regex("""onCallInterpreter\?\.let \{ call ->""").containsMatchIn(line)) {
                assertTrue(where, "onClick = call" in lines.subList(i, minOf(i + 3, lines.size)).joinToString(" "))
            }
        }
        val openers = sources.flatMap { (f, lines) -> lines.mapIndexedNotNull { i, l -> if (Regex("""\b(callInterpreter|launchSandes)\(it""").containsMatchIn(l) || "::launchSandes" in l) "${f.name}:${i + 1}: $l" else null } }
        assertEquals(openers.joinToString("\n"), 2, openers.size)
        openers.forEach { assertTrue(it, "confirmButton" in it || "activity?.let(::launchSandes)" in it) }
    }

    /** Only an https link or a phone number may be saved or opened; every other scheme is refused. */
    @Test
    fun onlyHttpsLinksAndNumbersOpen() {
        assertEquals("https://relay.example.in/call?id=7", interpreterUrl(" https://relay.example.in/call?id=7 "))
        assertEquals("https://wa.me/919876543210", interpreterUrl("+91 98765 43210"))
        for (bad in listOf("intent://call#Intent;scheme=tel;end", "file:///sdcard/x.html", "javascript:alert(9876543210)",
            "http://relay.example.in", "tel:9876543210", "content://x/9876543210", "https://", "https://a b.in", "HTTPS://x.in@evil")) {
            assertNull(bad, interpreterUrl(bad))
        }
    }
}
