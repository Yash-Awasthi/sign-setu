package app.signsetu.export

import android.content.Context
import android.net.Uri
import android.util.Base64
import app.signsetu.data.session.DeviceSeal
import app.signsetu.data.session.SessionRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
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
    EvidenceIntegrity.checkSeal(payload, { line, sig, key -> DeviceSeal.verify(line, sig, Base64.decode(key, Base64.NO_WRAP)) }, current)
}
