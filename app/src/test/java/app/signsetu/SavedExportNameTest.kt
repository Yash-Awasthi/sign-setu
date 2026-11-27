package app.signsetu

import app.signsetu.data.session.Profile
import app.signsetu.data.session.Session
import app.signsetu.ui.session.clampUtf8
import app.signsetu.ui.session.folderName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The saved folder's name becomes a MediaStore folder and file, both capped at 255 bytes; the report's
 * " report.pdf" is appended to it, so the name itself keeps under 200 bytes. */
class SavedExportNameTest {
    private val id = "12345678-90ab-cdef"
    private fun session(title: String?) = Session(id = id, startedAt = 1_000L, profile = Profile.ISL_USER, subjectLang = "hi", title = title)

    // A broken cut would leave a lone surrogate; encoding and decoding catches it, and so does the byte count.
    private fun assertWholeCodePoints(name: String) {
        assertEquals("the name survives a UTF-8 round trip", name, String(name.toByteArray(Charsets.UTF_8), Charsets.UTF_8))
        assertTrue("UTF-8 bytes: ${name.toByteArray(Charsets.UTF_8).size}", name.toByteArray(Charsets.UTF_8).size <= 200)
        assertTrue("no broken char at the end", !name.last().isHighSurrogate())
    }

    @Test
    fun anEightyEmojiTitleFitsTheName() {
        val name = folderName(session("😀".repeat(80)))
        assertWholeCodePoints(name)
        assertEquals("as many whole emoji as fit, then nothing else", 50, name.codePointCount(0, name.length))
    }

    @Test
    fun anEightyDevanagariTitleFitsTheName() {
        val name = folderName(session("अ".repeat(80)))
        assertWholeCodePoints(name)
        assertEquals("80 Devanagari chars are 240 bytes, so 66 fit", 66, name.codePointCount(0, name.length))
    }

    @Test
    fun aShortTitleKeepsItsSessionId() {
        assertEquals("FIR 214_2026 12345678", folderName(session("FIR 214/2026")))
        assertEquals("conversation 12345678", folderName(session(null)))
        assertEquals("no path or file characters", "a_b_c 12345678", folderName(session("a:b?c")))
    }

    @Test
    fun theClampCutsOnlyBetweenCodePoints() {
        assertEquals("ab", clampUtf8("abc", 2))
        assertEquals("😀", clampUtf8("😀😀", 4))
        assertEquals("", clampUtf8("😀", 3))
        assertEquals("abc", clampUtf8("abc", 200))
    }
}
