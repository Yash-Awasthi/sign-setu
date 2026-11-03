package app.signsetu.ui.session

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.session.Session
import app.signsetu.data.session.SessionRepository
import app.signsetu.export.SessionReport
import app.signsetu.export.StationExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** A saved session written to the phone from the history: Documents/SignSetu/<name>/ holding the conversation's zip
 * (as the station gets it) and its encrypted PDF report. Saving again replaces both. Returns the folder and the PDF's
 * passphrase, or null when it could not be written. */
internal suspend fun saveToPhone(
    context: Context,
    repo: SessionRepository,
    content: ContentBundle,
    officerLang: String,
    report: SessionReport,
    session: Session,
): Pair<String, String>? {
    // The seal it is saved at: the "i" shows while the session still has that seal.
    val head = session.rootHash ?: repo.head(session.id) ?: return null
    val zip = StationExport.build(repo, session.id) ?: return null
    val (pdf, passphrase) = savedReport(context, repo, content, officerLang, report, session.id) ?: return null
    val name = folderName(session)
    val folder = "${android.os.Environment.DIRECTORY_DOCUMENTS}/SignSetu/$name"
    val ok = withContext(Dispatchers.IO) {
        try {
            write(context, folder, "$name.zip", "application/zip", zip) &&
                write(context, folder, "$name report.pdf", "application/pdf", pdf.readBytes())
        } finally {
            zip.fill(0)
            pdf.delete()
        }
    }
    if (!ok) return null
    repo.markPhoneExport(session.id, folder, head)
    return folder to passphrase
}

/** The report a finished session's Export makes, built here without opening the session on screen. */
private suspend fun savedReport(context: Context, repo: SessionRepository, content: ContentBundle, officerLang: String, report: SessionReport, id: String) =
    withContext(Dispatchers.Main) {
        val store = ViewModelStore()
        try {
            val vm = ViewModelProvider.create(store, viewModelFactory { initializer { SessionViewModel(id, repo, content, officerLang, report, quiet = true) } })[SessionViewModel::class]
            val ui = withTimeoutOrNull(10_000) { vm.ui.filterNotNull().first() } ?: return@withContext null
            val res = context.resources
            val by = shownBy(ui.events)
            val idle = notActedOn(ui.events)
            vm.exportReport(
                reportText(res, ui, content),
                recordDetail(res, content, officerLang, ui.events) { eventDetail(res, it, by, idle) },
                detailsSection(res, content, officerLang, ui.events),
            )
        } finally {
            store.clear()
        }
    }

/** The title and the session id's start, as on the station, without characters a file name cannot hold. */
internal fun folderName(session: Session) = StationExport.name(session.title, session.id).removeSuffix(".zip").replace(Regex("""[\\/:*?"<>|]"""), "_").trim()

/** Writes one file into the shared Documents through MediaStore, replacing this app's earlier file of that name. */
private fun write(context: Context, folder: String, name: String, mime: String, bytes: ByteArray): Boolean {
    val resolver = context.contentResolver
    val files = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    resolver.delete(
        files,
        "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
        arrayOf("$folder/", name),
    )
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, mime)
        put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/")
    }
    val uri = resolver.insert(files, values) ?: return false
    return runCatching { resolver.openOutputStream(uri)?.use { it.write(bytes) } != null }.getOrDefault(false)
}
