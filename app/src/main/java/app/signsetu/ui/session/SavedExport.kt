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
import app.signsetu.export.ExportSweeper
import app.signsetu.export.MediaStoreExportStore
import app.signsetu.export.SessionReport
import app.signsetu.export.StationExport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** A saved session written to the phone from the history: Documents/SignSetu/<name>/ holding the conversation's zip
 * (as the station gets it) and its PDF report. Saving again replaces both. Returns the folder, or null when it could
 * not be written. */
internal suspend fun saveToPhone(
    context: Context,
    repo: SessionRepository,
    content: ContentBundle,
    officerLang: String,
    report: SessionReport,
    session: Session,
): String? = try {
    // The seal it is saved at: the "i" shows while the session still has that seal.
    val head = session.rootHash ?: repo.head(session.id) ?: return null
    val zip = StationExport.build(repo, session.id) ?: return null
    val pdf = savedReport(context, repo, content, officerLang, report, session.id) ?: return null
    val name = folderName(session)
    val folder = "${android.os.Environment.DIRECTORY_DOCUMENTS}/SignSetu/$name"
    val ok = withContext(Dispatchers.IO) {
        try {
            val zipOk = write(context, folder, "$name.zip", "application/zip", zip)
            var pdfOk = false
            try {
                pdfOk = zipOk && write(context, folder, "$name report.pdf", "application/pdf", pdf.readBytes())
            } finally {
                // A save is both files or neither: a zip whose report failed would be a plaintext copy of the
                // conversation that the history row never marks, so nothing else would ever remove it. A kill
                // between the two writes is the one window left open: the whole zip stays unmarked until the next
                // save or a clear-all, and the pair cannot be made atomic through MediaStore.
                if (zipOk && !pdfOk) deleteRow(context, folder, "$name.zip")
            }
            pdfOk
        } finally {
            zip.fill(0)
            pdf.delete()
        }
    }
    if (!ok) return null
    // A retitled session saved again leaves its earlier folder holding a copy the app no longer points at.
    folderToSweep(session.phoneFolder, folder)?.let { old ->
        withContext(Dispatchers.IO) { MediaStoreExportStore(context).deleteFolder(old) }
    }
    repo.markPhoneExport(session.id, folder, head)
    folder
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failed: Throwable) {
    // The caller shows saved_download_failed on null; a throw here would take the app down instead.
    null
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

/** The title and the session id's start, as on the station, without characters a file name cannot hold. The name
 * becomes a MediaStore folder and file, whose limit is 255 bytes; the report's " report.pdf" is added to it. */
internal fun folderName(session: Session): String =
    clampUtf8(StationExport.name(session.title, session.id).removeSuffix(".zip").replace(Regex("""[\\/:*?"<>|]"""), "_"), FOLDER_MAX_BYTES).trim()

/** MediaStore allows 255 bytes per name; the report adds " report.pdf" and the zip adds ".zip". */
private const val FOLDER_MAX_BYTES = 200

/** [text] cut to at most [maxBytes] of UTF-8, never between the two chars of a code point. */
internal fun clampUtf8(text: String, maxBytes: Int): String {
    if (text.toByteArray(Charsets.UTF_8).size <= maxBytes) return text
    val out = StringBuilder(text.length)
    var bytes = 0
    var i = 0
    while (i < text.length) {
        val codePoint = text.codePointAt(i)
        val unit = String(Character.toChars(codePoint))
        val size = unit.toByteArray(Charsets.UTF_8).size
        if (bytes + size > maxBytes) break
        bytes += size
        out.append(unit)
        i += Character.charCount(codePoint)
    }
    return out.toString()
}

/** Writes one file into the shared Documents through MediaStore, replacing this app's earlier file of that name. The
 * new bytes go into a pending row under a `.part` name and are only named and shown once the stream has closed, so a
 * failure or a kill never costs the officer the copy they already had. If the provider cannot give our row the old
 * row's name while that row still exists, it is freed and the name set again. */
private fun write(context: Context, folder: String, name: String, mime: String, bytes: ByteArray): Boolean {
    val resolver = context.contentResolver
    val files = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    purgeStalePending(context, folder)
    var previous = rowsNamed(context, folder, name)
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, pendingName(name))
        put(MediaStore.MediaColumns.MIME_TYPE, mime)
        put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/")
        // Hidden from Files and from other apps, and never scanned, until the bytes are all in.
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri = resolver.insert(files, values) ?: return false
    var ok = runCatching { resolver.openOutputStream(uri)?.use { it.write(bytes) } != null }.getOrDefault(false)
    if (ok) {
        val whole = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }
        ok = runCatching { resolver.update(uri, whole, null, null) > 0 }.getOrDefault(false)
    }
    // The old copy still holds the name, so the provider may keep ours as "name (1).pdf". Free the name and set it
    // again; a kill right here leaves that whole copy under the provider's name, which is listed and swept by
    // retention, never a leak the app cannot see. A second update that fails leaves the row whole and visible.
    if (ok && displayNameOf(context, uri) != name) {
        previous.forEach { id -> runCatching { resolver.delete(files, "${MediaStore.MediaColumns._ID} = ?", arrayOf(id)) } }
        previous = emptyList()
        runCatching {
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, name) }, null, null)
        }
    }
    if (!ok) {
        // A failed write leaves no empty or half-written file behind claiming to be the report; the row is ours.
        runCatching { resolver.delete(uri, null, null) }
        return false
    }
    // The copy being replaced goes only now that its successor is whole and named.
    previous.forEach { id -> runCatching { resolver.delete(files, "${MediaStore.MediaColumns._ID} = ?", arrayOf(id)) } }
    return true
}

/** The name the provider actually gave the row at [uri], after it may have renamed it to avoid a duplicate. */
private fun displayNameOf(context: Context, uri: android.net.Uri): String? {
    var name: String? = null
    runCatching {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) name = cursor.getString(0)
        }
    }
    return name
}


/** The name a row carries while its bytes are still arriving: the sweeper and the purge both know `.part`. */
internal fun pendingName(name: String) = "$name.part"

/** A pending row older than the grace is a save that died mid-write, not one in progress. */
internal fun stalePending(nowMs: Long, modifiedAtMs: Long) = nowMs - modifiedAtMs >= ExportSweeper.PART_GRACE_MS

/** The stored folder of an earlier save that the new one replaces, or null when there is none or it is this one. */
internal fun folderToSweep(stored: String?, folder: String): String? =
    stored?.trimEnd('/')?.takeIf { it.isNotEmpty() && it != folder.trimEnd('/') }

/** Removes this app's row of that name, so a save that could not write one of its two files leaves neither. */
private fun deleteRow(context: Context, folder: String, name: String) {
    runCatching {
        context.contentResolver.delete(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf("$folder/", name),
        )
    }
}

/** The `_ID`s of this app's rows of that name, the copy a write replaces. */
private fun rowsNamed(context: Context, folder: String, name: String): List<String> {
    val out = mutableListOf<String>()
    runCatching {
        context.contentResolver.query(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf("$folder/", name),
            null,
        )?.use { cursor -> while (cursor.moveToNext()) out += cursor.getLong(0).toString() }
    }
    return out
}

/** Deletes this app's pending rows under [folder] that a kill left behind; a live write is inside the grace. */
private fun purgeStalePending(context: Context, folder: String) {
    val resolver = context.contentResolver
    val files = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val now = System.currentTimeMillis()
    val stale = mutableListOf<String>()
    runCatching {
        resolver.query(
            files,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.DATE_MODIFIED),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.IS_PENDING} = 1",
            arrayOf("$folder/"),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val added = if (cursor.isNull(1)) 0L else cursor.getLong(1) * 1000L
                val modified = if (cursor.isNull(2)) 0L else cursor.getLong(2) * 1000L
                val at = maxOf(added, modified)
                // Our own inserts always carry a date; a pending row with none died before its write began.
                if (at == 0L || stalePending(now, at)) stale += cursor.getLong(0).toString()
            }
        }
    }
    stale.forEach { id -> runCatching { resolver.delete(files, "${MediaStore.MediaColumns._ID} = ?", arrayOf(id)) } }
}

