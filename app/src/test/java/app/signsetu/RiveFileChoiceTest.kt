package app.signsetu

import app.signsetu.ui.avatar.RiveCharacter
import app.signsetu.ui.avatar.RiveCharacter.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which copy of avatar/signer.riv is shown, decided from sizes alone so a dropped file is never read just to see
 *  whether it is there. The private copy is a department's adb drop and is capped; the assets copy is the build's. */
class RiveFileChoiceTest {

    private val cap = RiveCharacter.MAX_RIV_BYTES

    @Test
    fun aPrivateFileUnderTheCapIsUsed() {
        assertEquals(Source.PRIVATE, RiveCharacter.source(512L * 1024, assetExists = true))
        assertEquals(Source.PRIVATE, RiveCharacter.source(512L * 1024, assetExists = false))
        assertTrue(RiveCharacter.usable(cap))
    }

    @Test
    fun aPrivateFileOverTheCapFallsBackToTheApkCopy() {
        assertEquals(Source.ASSET, RiveCharacter.source(cap + 1, assetExists = true))
        assertFalse(RiveCharacter.usable(cap + 1))
    }

    @Test
    fun aPrivateFileOverTheCapWithNoApkCopyShowsNothing() {
        assertEquals(Source.NONE, RiveCharacter.source(Long.MAX_VALUE, assetExists = false))
    }

    @Test
    fun aMissingPrivateFileFallsBackToTheApkCopy() {
        assertEquals(Source.ASSET, RiveCharacter.source(null, assetExists = true))
        assertFalse(RiveCharacter.usable(null))
    }

    @Test
    fun nothingAnywhereIsAbsent() {
        assertEquals(Source.NONE, RiveCharacter.source(null, assetExists = false))
        assertEquals(Source.NONE, RiveCharacter.source(cap + 1, assetExists = false))
    }
}
