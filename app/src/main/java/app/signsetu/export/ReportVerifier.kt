package app.signsetu.export

import android.content.Context
import android.net.Uri
import android.util.Base64
import app.signsetu.data.session.DeviceSeal
import app.signsetu.data.session.SessionRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Opens an exported report with its passphrase and checks its seal: the signature with the key the seal carries,
 * and, when the session is still on this phone, the record as it stands now. Null when the file cannot be opened. */
suspend fun checkReport(context: Context, uri: Uri, passphrase: String, repo: SessionRepository?): EvidenceIntegrity.SealCheck? = withContext(Dispatchers.IO) {
    PDFBoxResourceLoader.init(context)
    val payload = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input, passphrase).use { it.documentInformation.getCustomMetadataValue(SessionReport.SEAL_KEY) }
        }
    }.getOrNull() ?: return@withContext null
    val sessionId = payload.split('|').getOrNull(1)
    val current = sessionId?.let { repo?.currentSeal(it)?.line }
    val check = EvidenceIntegrity.checkSeal(payload, { line, sig, key -> DeviceSeal.verify(line, sig, Base64.decode(key, Base64.NO_WRAP)) }, current)
    // Stored seals can stand while a row under them was edited: with the session still here, its rows are re-walked.
    if (check == EvidenceIntegrity.SealCheck.MATCHES_DEVICE && repo != null && sessionId != null) {
        val session = repo.session(sessionId).first()
        if (session != null && !EvidenceIntegrity.verifyChain(session, repo.eventsNow(sessionId), { repo.audio(it) }) { repo.drawing(it) }.intact) {
            return@withContext EvidenceIntegrity.SealCheck.RECORD_ALTERED
        }
    }
    check
}
