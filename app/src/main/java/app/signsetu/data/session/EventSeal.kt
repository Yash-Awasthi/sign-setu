package app.signsetu.data.session

import java.security.MessageDigest

/** The hash chain over a session's log, sealed as each row is written: every row's hash covers the previous row's
 * hash, its own fields and the bytes of its drawing, so an edited, inserted or removed row breaks every later hash. */
object EventSeal {
    fun genesis(sessionId: String, startedAt: Long): String = sha256Hex("signsetu-chain-v1" + field(sessionId) + field(startedAt.toString()))

    fun seal(
        previous: String,
        sessionId: String,
        ts: Long,
        type: String,
        source: String,
        lang: String?,
        text: String?,
        cardId: String?,
        lexiconId: String?,
        drawing: ByteArray?,
        itemId: String?,
        reason: String?,
        shownId: Long?,
        via: String?,
    ): String = sha256Hex(
        previous + field(sessionId) + field(ts.toString()) + field(type) + field(source) + field(lang) + field(text) +
            field(cardId) + field(lexiconId) + field(drawing?.let(::sha256Hex)) + field(itemId) + field(reason) +
            field(shownId?.toString()) + field(via),
    )

    /** Merkle root over [seals] in write order (RFC 6962 hashing: 0x00 before a leaf, 0x01 before a node), so one
     * row can later be shown to belong to the record by its path alone, without the other rows. */
    fun merkleRoot(seals: List<String>): String {
        if (seals.isEmpty()) return sha256Hex(ByteArray(0))
        var level = seals.map { digest(byteArrayOf(0) + it.hexToByteArray()) }
        while (level.size > 1) level = level.chunked(2).map { if (it.size == 1) it[0] else digest(byteArrayOf(1) + it[0] + it[1]) }
        return level[0].toHexString()
    }

    /** The bytes the device key signs for a session: what a verifier rebuilds from the QR code. */
    fun sealLine(sessionId: String, count: Int, head: String, root: String) = "SS1|$sessionId|$count|$head|$root"

    private fun digest(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b)

    // Length-prefixed, with null apart from empty, so no two different rows encode the same.
    private fun field(value: String?) = if (value == null) "|-" else "|${value.length}:$value"

    fun sha256Hex(text: String): String = sha256Hex(text.toByteArray(Charsets.UTF_8))

    fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHexString()
}
