package app.signsetu

import app.signsetu.data.settings.OFFICER_ID_MAX
import app.signsetu.data.settings.storedGestureSpeed
import app.signsetu.data.settings.storedOfficerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Settings are written once and read after the process is gone, so every value is put through the same rule on the
 * way in and on the way out. The store itself needs Android; the rules that keep a stored value harmless do not. */
class SettingsNormalizerTest {
    @Test
    fun anOfficerIdCannotAddAFieldToTheProvenanceRow() {
        // The provenance row is comma-led, so a comma in the id would forge a field in the sealed first row.
        val trimmed = storedOfficerId("  1234, constable  ")
        assertFalse("no comma survives", trimmed.contains(','))
        assertEquals("1234 constable", trimmed)
        assertEquals("no control characters", "AB12", storedOfficerId("A\u0007B\u000012"))
        assertEquals("capped", OFFICER_ID_MAX, storedOfficerId("7".repeat(OFFICER_ID_MAX + 20)).length)
        assertEquals("empty stays empty", "", storedOfficerId("   "))
    }

    @Test
    fun aSigningSpeedIsAlwaysAPlayableNumber() {
        assertEquals("normal speed for a value that is not a number", 1f, storedGestureSpeed(Float.NaN))
        assertEquals(1f, storedGestureSpeed(Float.POSITIVE_INFINITY))
        assertEquals(1f, storedGestureSpeed(Float.NEGATIVE_INFINITY))
        assertEquals("below the offered range", 0.25f, storedGestureSpeed(0f))
        assertEquals("above the offered range", 1.5f, storedGestureSpeed(9f))
        assertEquals("a real choice is kept", 0.75f, storedGestureSpeed(0.75f))
    }
}
