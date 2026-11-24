package app.signsetu.data.session

/** When the retention sweep takes a session: the one rule the sweep, the app and the tests share.
 *
 * The owner's setting is in hours, and 0 means "keep everything until the officer deletes it". A session is idle from
 * its end; while it is open, from its last row; and a session with no row yet from its start. Exactly on the boundary it
 * is kept — the first millisecond past it, it goes. */
object Retention {

    const val HOUR_MS = 3_600_000L

    /** The moment a session last had anything happen, which is what its window counts from. */
    fun idleSince(startedAt: Long, endedAt: Long?, lastRowAt: Long?): Long = endedAt ?: lastRowAt ?: startedAt

    /** The cutoff a sweep deletes below. Only asked when the window is a real one. */
    fun cutoff(now: Long, retentionHours: Int): Long = now - retentionHours * HOUR_MS

    /** Whether a session idle since [idleSince] has left its window by [now]. On the boundary it has not. */
    fun expired(idleSince: Long, now: Long, retentionHours: Int): Boolean =
        retentionHours > 0 && idleSince < cutoff(now, retentionHours)
}
