package app.signsetu.ui.avatar

import android.content.Context
import app.signsetu.BuildConfig
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * assets/avatar, served from a copy in the app cache. The APK stores the page and the model deflated (4,490,834 B
 * for 11,506,037 B in app-standard-debug.apk), and every process start inflated them again; a plain file in the
 * cache is read straight, so a later start does no inflate. The copy is keyed by the app version, so an update
 * never serves the previous version's bytes, and each file is written under .part and renamed, so a process killed
 * mid-write leaves no short file to serve.
 */
internal class AvatarAssets(
    /** Where this version's copies live: `<cacheDir>/avatar-assets/<versionCode>`. */
    private val dir: File,
    /** Opens a file under `assets/`, by its path from there: `avatar/model.vrm`. */
    private val open: (String) -> InputStream,
) {
    /** Times the APK was opened for these bytes; a second start must leave this at the first start's count. */
    var apkReads = 0
        private set
    var bytesFromApk = 0L
        private set
    var bytesFromCache = 0L
        private set
    var copyNanos = 0L
        private set

    /** The file's bytes: the cache's copy when one is complete, else a copy made from the APK now; null if absent. */
    fun stream(path: String): InputStream? {
        val copy = File(dir, path)
        if (copy.isFile) {
            val length = copy.length()
            // A zero-length file is a copy that never finished; the .part name stops that, this is the belt.
            if (length > 0L) {
                bytesFromCache += length
                return copy.inputStream()
            }
        }
        apkReads++
        val bytes = runCatching { open(path).use { it.readBytes() } }.getOrNull() ?: return null
        bytesFromApk += bytes.size
        val started = System.nanoTime()
        runCatching {
            val parent = copy.parentFile ?: throw IOException("no parent")
            parent.mkdirs()
            val part = File(parent, copy.name + ".part")
            part.writeBytes(bytes)
            if (!part.renameTo(copy)) throw IOException("rename failed")
        }
        copyNanos += System.nanoTime() - started
        return bytes.inputStream()
    }

    companion object {
        private const val ROOT = "avatar-assets"

        @Volatile private var app: AvatarAssets? = null

        /** The process's cache, made on first request; a new process finds the previous one's copy on disk. */
        fun of(context: Context): AvatarAssets = app ?: synchronized(this) {
            app ?: AvatarAssets(
                dirFor(context.applicationContext.cacheDir, BuildConfig.VERSION_CODE),
                { path -> context.applicationContext.assets.open(path) },
            ).also { app = it }
        }

        /** This version's directory, with every other version's copy removed: stale bytes are never served. */
        internal fun dirFor(cacheDir: File, version: Int): File {
            val root = File(cacheDir, ROOT)
            root.listFiles()?.forEach { if (it.name != version.toString()) it.deleteRecursively() }
            return File(root, version.toString())
        }
    }
}
