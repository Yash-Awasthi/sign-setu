package app.signsetu.ui.session

import app.signsetu.data.content.SignLanguage
import app.signsetu.data.session.DeviceSeal
import app.signsetu.data.session.Session
import app.signsetu.data.session.SessionEvent
import app.signsetu.data.session.SessionRepository
import app.signsetu.export.EvidenceIntegrity
import app.signsetu.export.ReportRow
import app.signsetu.export.ReportSeal
import app.signsetu.export.ReportSection
import app.signsetu.export.ReportText
import app.signsetu.export.SessionReport
import app.signsetu.export.recordDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Builds the encrypted report from everything logged so far; returns the file and its one-time passphrase. */
internal suspend fun SessionRepository.exportSession(
    sessionId: String,
    report: SessionReport,
    session: Session,
    signLanguage: SignLanguage,
    text: ReportText,
    section: ReportSection?,
    aiSummary: String?,
    rowsOf: (List<SessionEvent>) -> List<ReportRow>,
): Pair<File, String> {
    // Rows, digest and chain all come from the database as it is now, so the printed rows are the ones certified.
    val events = eventsNow(sessionId)
    val rows = rowsOf(events)
    val chain = EvidenceIntegrity.verifyChain(session, events, { audio(it) }, { landmarks(it) }) { drawing(it) }
    val digest = recordDigest(rows)
    val seal = currentSeal(sessionId)
    val key = withContext(Dispatchers.Default) { DeviceSeal.publicKey() }
    val fingerprint = key?.let { java.security.MessageDigest.getInstance("SHA-256").digest(it).toHexString() }
    val certificate = EvidenceIntegrity.generateSection63Certificate(
        session = session,
        events = events,
        chain = chain,
        recordDigest = digest,
        signLanguage = signLanguage,
        root = seal?.root,
        keyFingerprint = fingerprint,
        signed = seal?.sig != null,
    )
    val keyText = key?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }
    val printed = seal?.let { s ->
        ReportSeal(
            EvidenceIntegrity.qrPayload(s.line, digest, s.sig, keyText),
            listOf("Seal line: ${s.line}", "Record digest: $digest", "Entries sealed: ${s.count}", "Signature (ECDSA P-256, base64): ${s.sig ?: "none"}",
                "Device public key (X.509, base64): ${keyText ?: "none"}", "Check: rebuild the seal line from the QR code's first five fields and verify the signature with the key."),
        )
    }
    val clips = events.mapNotNull { e -> e.audioId?.let { id -> audio(id)?.let { "audio-$id.wav" to it } } }
    return report.export(rows, text, { drawing(it) }, section, aiSummary, certificate, chain.short, printed, clips)
}
