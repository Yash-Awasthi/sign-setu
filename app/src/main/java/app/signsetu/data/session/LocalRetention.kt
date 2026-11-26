package app.signsetu.data.session

import java.io.File

/** Bounds on the local copies that are not in the encrypted session store: training landmarks and debug captures. */
object LocalRetention {
    /** A volunteer's training records are kept for a bounded period, not forever. */
    const val CONTRIB_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000

    /** Debug captures are for the next evaluation run, not an archive. */
    const val DEBUG_RECORDING_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    /**
     * Deletes files under [dir] older than [maxAgeMs], plus half-written part files. A non-positive age deletes all.
     * Empty directories left behind are removed. Returns the number of files deleted.
     */
    fun purge(dir: File, nowMs: Long, maxAgeMs: Long): Int {
        if (!dir.isDirectory) return 0
        var deleted = 0
        for (file in dir.walkBottomUp().toList()) {
            if (file == dir) continue
            if (file.isDirectory) {
                if (file.listFiles().isNullOrEmpty()) file.delete()
                continue
            }
            val stale = maxAgeMs <= 0 || nowMs - file.lastModified() >= maxAgeMs
            if ((stale || file.name.endsWith(".part")) && file.delete()) deleted++
        }
        return deleted
    }

    /** Deletes everything under [dir], then the directory itself, for Clear all data. Returns the file count. */
    fun purgeAll(dir: File): Int {
        if (!dir.exists()) return 0
        val files = dir.walkTopDown().count { it.isFile }
        dir.deleteRecursively()
        return files
    }
}
