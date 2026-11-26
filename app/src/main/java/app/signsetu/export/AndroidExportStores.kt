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
        val columns = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )
        context.contentResolver
            .query(collection, columns, "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?", arrayOf("$root/%"), null)
            ?.use { cursor ->
                while (cursor.moveToNext()) {
                    out += ExportFile(cursor.getLong(0).toString(), cursor.getString(1).orEmpty(), cursor.getLong(2) * 1000L)
                }
            }
        return out
    }

    override fun delete(file: ExportFile): Boolean =
        context.contentResolver.delete(collection, "${MediaStore.MediaColumns._ID} = ?", arrayOf(file.key)) > 0
}
