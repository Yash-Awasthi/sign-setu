package app.signsetu.data.session

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// No table has a field for a subject's name, address, phone number or photograph. The
// officer's own name for a saved session is free text and may hold one; it is encrypted like every row.

@Entity(tableName = "session")
data class SessionEntity(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val endedAt: Long?,
    val profile: String,
    val subjectLang: String,
    // The column default only fills rows from before roles existed; new sessions always write their role.
    @ColumnInfo(defaultValue = "VICTIM") val role: String = "NOT_KNOWN",
    /** The officer's name for a finished session; null while it is open. */
    val title: String? = null,
    /** Merkle root over the session's seals when it was last ended, and the device key's signature over it. */
    val rootHash: String? = null,
    val rootSig: String? = null,
    /** While a saved session is open again: the last row it had when saved, so new rows can be discarded. */
    val savedThrough: Long? = null,
    /** Where the session was last sent on the station (file name and full path) and saved on this phone (folder),
     * each with the hash-chain head it covered: unchanged while the head is the same. */
    val stationFile: String? = null,
    val stationHead: String? = null,
    val phoneFolder: String? = null,
    val phoneHead: String? = null,
)

@Entity(
    tableName = "event",
    foreignKeys = [ForeignKey(SessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")],
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val ts: Long,
    val type: String,
    val source: String,
    val lang: String?,
    val text: String?,
    val cardId: String?,
    val lexiconId: String?,
    val drawingId: Long?,
    val itemId: String? = null,
    val reason: String? = null,
    val shownId: Long? = null,
    val via: String? = null,
    /** EventSeal over the previous row's hash and this row; null on rows written before sealing existed. */
    val hash: String? = null,
    /** The person's recorded speech for this row (AudioEntity); null on every other row. */
    val audioId: Long? = null,
    /** The raw landmarks the app read this row's signs from (LandmarksEntity); null on every other row. */
    val landmarksId: Long? = null,
)

@Entity(
    tableName = "drawing",
    foreignKeys = [ForeignKey(SessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")],
)
class DrawingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val png: ByteArray,
)

/** The person's speech as 16 kHz mono 16-bit WAV, recorded only while the officer held the button and only after
 * consent was logged; encrypted with the database and deleted with its session. */
@Entity(
    tableName = "audio",
    foreignKeys = [ForeignKey(SessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")],
)
class AudioEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val wav: ByteArray,
)

/** The hand and body landmarks of one sign window, as StationExport.signsJson writes them: biometric, so encrypted
 * with the database, sealed with the row that names it, and deleted with its session. */
@Entity(
    tableName = "landmarks",
    foreignKeys = [ForeignKey(SessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")],
)
class LandmarksEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val json: ByteArray,
)
