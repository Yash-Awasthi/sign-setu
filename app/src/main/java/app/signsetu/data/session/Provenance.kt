package app.signsetu.data.session

/**
 * What the record says about the phone and the officer that produced it. This is a deliberate part of the sealed
 * session: [line] is stored as the session's opening row, printed on the report's certificate page, and written to
 * `session.json` in the station zip. [identifiers] names the values that point back to a person or a device, so every
 * path that leaves the phone can remove them explicitly instead of hoping a pattern match catches them.
 */
data class Provenance(
    val app: String,
    val content: String,
    val deviceKey: String? = null,
    val officer: String? = null,
) {
    /** The line stored as the session's first row, after `SYSTEM_OPENED`. */
    fun line(): String = buildString {
        append("app ").append(app)
        append(", content ").append(content)
        deviceKey?.takeIf { it.isNotBlank() && it != NONE }?.let { append(", device key ").append(it) }
        officer?.takeIf { it.isNotBlank() && it != NONE }?.let { append(", officer ").append(it) }
    }

    /** The values that identify the phone or the officer, so a caller can remove them from anything sent out. */
    fun identifiers(): List<String> = listOfNotNull(
        deviceKey?.takeIf { it.isNotBlank() && it != NONE },
        officer?.takeIf { it.isNotBlank() && it != NONE },
    )

    /** [text] with every identifier removed, for anything that leaves the phone. A standalone provenance line is
     * rebuilt without the two fields, so their labels go too; any other text only has the values replaced, because the
     * labels there are part of the surrounding log line. */
    fun redact(text: String): String {
        val whole = parse(text)
        if (whole != null && whole.line() == text) return whole.copy(deviceKey = null, officer = null).line()
        return identifiers().fold(text) { out, id -> out.replace(id, REDACTED) }
    }

    companion object {
        const val REDACTED = "[redacted]"

        /** Older lines wrote "none" when the phone had no key; it names nothing and must not be treated as a value. */
        private const val NONE = "none"

        private val LINE = Regex("""app (.+?), content ([^,]+)(?:, device key ([^,]+))?(?:, officer (.+))?""")

        /** Reads a provenance line back, or null when [line] is not one. */
        fun parse(line: String): Provenance? {
            val match = LINE.find(line) ?: return null
            val key = match.groupValues[3].takeIf { it.isNotBlank() && it != NONE }
            val officer = match.groupValues[4].takeIf { it.isNotBlank() && it != NONE }
            return Provenance(match.groupValues[1], match.groupValues[2], key, officer)
        }

        /** The provenance carried by [log], when one of its lines holds it. */
        fun inLog(log: String): Provenance? = log.lineSequence().firstNotNullOfOrNull { parse(it) }
    }
}
