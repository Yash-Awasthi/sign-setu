package app.signsetu.export

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** The cache share folder (`cacheDir/exports`): ordinary files the app lists and deletes itself. */
class FileExportStore(private val dir: File) : ExportStore {
    override fun files(): List<ExportFile> = dir.listFiles().orEmpty().filter { it.isFile }.map {
        ExportFile(it.absolutePath, it.name, it.lastModified())
    }

    override fun delete(file: ExportFile): Boolean = File(file.key).delete() || !File(file.key).exists()
}

/**
 * The plaintext copies the officer saved from the history, under `Documents/SignSetu`. MediaStore is the only way
 * to see them once they leave the app's own storage, and the app owns the rows it inserted.
 */
class MediaStoreExportStore(
    private val context: Context,
    private val root: String = Environment.DIRECTORY_DOCUMENTS + "/SignSetu",
) : ExportStore {
    private val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    override fun files(): List<ExportFile> {
        val out = mutableListOf<ExportFile>()
        out += rows("${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?", arrayOf("$root/%"))
        // The folders are rows too, stored one level up (RELATIVE_PATH "Documents/SignSetu/", DISPLAY_NAME the
        // session's name), so they are listed here: Clear all data must not leave an empty folder named after a
        // conversation in Files. Their date goes through the same fallback as a file's, so a fresh one is not swept.
        out += rows(
            "(${MediaStore.MediaColumns.RELATIVE_PATH} = ? OR ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? OR " +
                "(${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?)) " +
                "AND ${MediaStore.MediaColumns.MIME_TYPE} = ?",
            arrayOf("$root/", "$root/%", "$parent/", name, DIRECTORY_MIME),
        )
        return out.distinctBy { it.key }
    }

    private fun rows(selection: String, args: Array<String>): List<ExportFile> {
        val out = mutableListOf<ExportFile>()
        val columns = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DATE_ADDED,
        )
        context.contentResolver.query(collection, columns, selection, args, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                out += ExportFile(cursor.getLong(0).toString(), cursor.getString(1).orEmpty(), stamp(cursor))
            }
        }
        return out
    }

    /** Deletes every row this app holds in [folder] and the folder's own row: what a retitled session's earlier save
     * leaves behind, plus any pending row a kill left in it. */
    fun deleteFolder(folder: String): Int {
        val path = folder.trimEnd('/') + "/"
        val owner = path.trimEnd('/').substringBeforeLast('/') + "/"
        val leaf = path.trimEnd('/').substringAfterLast('/')
        var deleted = runCatching {
            context.contentResolver.delete(collection, "${MediaStore.MediaColumns.RELATIVE_PATH} = ?", arrayOf(path))
        }.getOrDefault(0)
        deleted += runCatching {
            context.contentResolver.delete(
                collection,
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                arrayOf(owner, leaf),
            )
        }.getOrDefault(0)
        return deleted
    }

    /** DATE_MODIFIED in seconds, or DATE_ADDED when the provider left it NULL, or now: a 0 here reads as 1970 and
     * the next sweep deletes a just-saved export. */
    private fun stamp(cursor: android.database.Cursor): Long {
        val modified = if (cursor.isNull(2)) 0L else cursor.getLong(2)
        val added = if (cursor.isNull(3)) 0L else cursor.getLong(3)
        val seconds = modified.takeIf { it > 0 } ?: added
        return if (seconds > 0) seconds * 1000L else System.currentTimeMillis()
    }

    override fun delete(file: ExportFile): Boolean =
        context.contentResolver.delete(collection, "${MediaStore.MediaColumns._ID} = ?", arrayOf(file.key)) > 0

    private val parent = root.substringBeforeLast('/')
    private val name = root.substringAfterLast('/')

    private companion object {
        const val DIRECTORY_MIME = "vnd.android.document/directory"
    }
}
