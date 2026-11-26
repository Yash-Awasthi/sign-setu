package app.signsetu

import app.signsetu.data.session.EventSeal
import app.signsetu.export.EvidenceIntegrity
import app.signsetu.export.EvidenceIntegrity.SealCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The QR code on a report's seal page is the whole check: the seal line, the printed-rows digest, the signature and
 * the key. Whoever scans it must get the same answer the phone gives, so the payload and the checker are tested as a
 * round trip. */
class SealCheckTest {
    private val line = EventSeal.sealLine("session-1", 3, "a".repeat(64), "b".repeat(64))
    private val payload = EvidenceIntegrity.qrPayload(line, "1234-5678-9abc-def0", "SIG-1", "KEY-1")

    private val good: (String, String, String) -> Boolean = { l, s, k -> l == line && s == "SIG-1" && k == "KEY-1" }

    private fun check(code: String, verify: (String, String, String) -> Boolean = good, current: String? = line) =
        EvidenceIntegrity.checkSeal(code, verify, current)

    @Test
    fun theCodeCarriesTheSealTheDigestTheSignatureAndTheKeyInThatOrder() {
        val f = payload.split('|')
        assertEquals("SS1|session-1|3|${"a".repeat(64)}|${"b".repeat(64)}", f.take(5).joinToString("|"))
        assertEquals("1234-5678-9abc-def0", f[5])
        assertEquals("SIG-1", f[6])
        assertEquals("KEY-1", f[7])
        assertEquals(8, f.size)
    }

    @Test
    fun aGoodSignatureMatchesTheDeviceOnlyWhileTheRecordIsUnchanged() {
        assertEquals("the same record on this phone", SealCheck.MATCHES_DEVICE, check(payload))
        assertEquals("no record here to compare with", SealCheck.SIGNATURE_VALID, check(payload, current = null))
        assertEquals(
            "a different record under a good signature",
            SealCheck.DIFFERS_FROM_DEVICE,
            check(payload, current = EventSeal.sealLine("session-1", 4, "c".repeat(64), "b".repeat(64))),
        )
    }

    @Test
    fun aBadSignatureIsNeverAcceptedEvenForTheSameLine() {
        assertEquals(SealCheck.SIGNATURE_INVALID, check(payload, { _, _, _ -> false }))
        assertEquals(SealCheck.SIGNATURE_INVALID, check(payload, { _, _, _ -> false }, current = null))
    }

    @Test
    fun anUnsignedSealSaysUnsignedAndNeverValid() {
        val unsigned = EvidenceIntegrity.qrPayload(line, "1234", "-", "-")
        assertEquals(SealCheck.UNSIGNED, check(unsigned, current = line))
        assertEquals(SealCheck.UNSIGNED, check(unsigned, current = null))
        assertEquals(SealCheck.DIFFERS_FROM_DEVICE, check(unsigned, current = EventSeal.sealLine("other", 1, "d", "e")))
    }

    @Test
    fun aPayloadOfTheWrongShapeIsMalformedAndItsSignatureIsNeverTried() {
        var verified = false
        val verify: (String, String, String) -> Boolean = { _, _, _ -> verified = true; true }
        val cases = listOf(
            "",
            "SS1|session-1|3",
            payload + "|extra",
            payload.replaceFirst("SS1", "SS2"),
        )
        cases.forEach { code -> assertEquals("for ${code.take(24)}", SealCheck.MALFORMED, check(code, verify)) }
        assertFalse("a malformed code must not reach the signature check", verified)
    }
}
