package app.signsetu

import app.signsetu.ui.components.OfficerAuth
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Turning a security switch off is forced, so it asks even when the "Ask for fingerprint" switch is off. */
class OfficerAuthTest {

    @Test
    fun turningAProtectionOffAsksEvenWithTheFingerprintSwitchOff() {
        assertTrue(OfficerAuth.needsCredential(required = false, force = true, deviceSecure = true, testUnlocked = false))
    }

    @Test
    fun nothingIsAskedWhenTheSwitchIsOffAndNothingIsForced() {
        assertFalse(OfficerAuth.needsCredential(required = false, force = false, deviceSecure = true, testUnlocked = false))
    }

    @Test
    fun theFingerprintSwitchOnAsks() {
        assertTrue(OfficerAuth.needsCredential(required = true, force = false, deviceSecure = true, testUnlocked = false))
        assertTrue(OfficerAuth.needsCredential(required = true, force = true, deviceSecure = true, testUnlocked = false))
    }

    @Test
    fun aPhoneWithoutASecureLockIsNeverAsked() {
        assertFalse(OfficerAuth.needsCredential(required = true, force = false, deviceSecure = false, testUnlocked = false))
        assertFalse(OfficerAuth.needsCredential(required = true, force = true, deviceSecure = false, testUnlocked = false))
        assertFalse(OfficerAuth.needsCredential(required = false, force = true, deviceSecure = false, testUnlocked = false))
    }

    @Test
    fun anUnlockedTestBuildNeverAsks() {
        assertFalse(OfficerAuth.needsCredential(required = true, force = true, deviceSecure = true, testUnlocked = true))
    }
}
