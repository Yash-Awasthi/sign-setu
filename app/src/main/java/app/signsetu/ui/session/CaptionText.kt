package app.signsetu.ui.session

/** Large enough to read across a room while the character signs. */
internal const val CAPTION_SP = 26f

/**
 * The caption cut into single-line pieces at word boundaries, at most [maxChars] characters each. A word longer than
 * [maxChars] is kept whole rather than cut, so a piece may overrun by that word alone.
 */
internal fun captionChunks(text: String, maxChars: Int = 32): List<IntRange> {
    if (text.isEmpty()) return emptyList()
    val size = maxChars.coerceAtLeast(1)
    val out = ArrayList<IntRange>()
    var start = 0
    while (start < text.length) {
        while (start < text.length && text[start].isWhitespace()) start++
        if (start >= text.length) break
        var stop = start + size
        if (stop < text.length) {
            // Back off to the last boundary inside the window; with none, the one word runs to its own end.
            var cut = stop
            while (cut > start && !text[cut].isWhitespace()) cut--
            stop = if (cut > start) cut else {
                var end = start + size
                while (end < text.length && !text[end].isWhitespace()) end++
                end
            }
        } else {
            stop = text.length
        }
        // A line break always ends the line drawn, however much room is left on it.
        val newline = text.indexOf('\n', start)
        if (newline >= 0 && newline < stop) stop = newline
        out += start until stop
        start = stop
    }
    return out
}

/** The piece holding [highlight]'s first character, or the first piece when nothing is lit there. */
internal fun chunkFor(chunks: List<IntRange>, highlight: IntRange?, text: String): Int {
    if (chunks.isEmpty() || highlight == null || text.isEmpty()) return 0
    val first = highlight.first.coerceIn(0, text.lastIndex)
    val last = highlight.last.coerceIn(0, text.lastIndex)
    if (last < first) return 0
    val i = chunks.indexOfFirst { it.first <= last && first <= it.last }
    return if (i >= 0) i else 0
}

/** [highlight] moved into [chunk]'s own coordinates and clipped to it; null when it does not touch the piece. */
internal fun localHighlight(chunk: IntRange, highlight: IntRange?): IntRange? {
    if (highlight == null) return null
    val first = maxOf(chunk.first, highlight.first)
    val last = minOf(chunk.last, highlight.last)
    return if (first <= last) (first - chunk.first)..(last - chunk.first) else null
}
