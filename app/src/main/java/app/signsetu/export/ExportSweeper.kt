package app.signsetu.export

/** One file an export sweep may delete. [key] is whatever the store needs to delete it. */
data class ExportFile(
    val key: String,
    val name: String,
    val modifiedAtMs: Long,
) {
    /** A half-written file: it is never worth keeping, however new. */
    val part: Boolean get() = name.endsWith(".part")
}

/** A place plaintext exports are written. The sweeper needs only to list and delete. */
interface ExportStore {
    fun files(): List<ExportFile>

    /** True when the file is gone afterwards. */
    fun delete(file: ExportFile): Boolean
}

/** Deletes export copies that are past their lifetime: cached PDFs and the copies saved to shared storage. */
object ExportSweeper {
    /** A PDF in the cache is only needed while the share sheet hands it over. */
    const val CACHE_MAX_AGE_MS = 15 * 60 * 1000L

    /**
     * Deletes every file in [store] older than [maxAgeMs], and every part file. A non-positive age clears all, which
     * is what Clear all data asks for; [Long.MAX_VALUE] keeps everything, for retention 0 (keep until deleted).
     */
    fun sweep(store: ExportStore, nowMs: Long, maxAgeMs: Long): Int {
        val files = runCatching { store.files() }.getOrDefault(emptyList())
        var deleted = 0
        for (file in files) {
            if (!file.part && maxAgeMs > 0 && nowMs - file.modifiedAtMs < maxAgeMs) continue
            if (runCatching { store.delete(file) }.getOrDefault(false)) deleted++
        }
        return deleted
    }
}
