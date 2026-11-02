package app.signsetu.data.session

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteDatabase

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

@Dao
interface SessionDao {
    @Insert suspend fun insertSession(session: SessionEntity)

    @Query("SELECT * FROM session WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun openSession(): SessionEntity?

    @Query("SELECT * FROM session WHERE id = :id")
    fun session(id: String): Flow<SessionEntity?>

    @Query("UPDATE session SET subjectLang = :lang WHERE id = :id")
    suspend fun setSubjectLang(id: String, lang: String)

    @Query("UPDATE session SET profile = :profile WHERE id = :id")
    suspend fun setProfile(id: String, profile: String)

    @Query("UPDATE session SET role = :role WHERE id = :id")
    suspend fun setRole(id: String, role: String)

    @Insert suspend fun insertEvent(event: EventEntity): Long

    /** A drawing and the row that names it are written together or not at all, so a crash leaves no orphan. */
    @Transaction
    suspend fun insertSealed(event: EventEntity, drawing: DrawingEntity?, audio: AudioEntity? = null): Long {
        val drawingId = drawing?.let { insertDrawing(it) }
        val audioId = audio?.let { insertAudio(it) }
        return insertEvent(event.copy(drawingId = drawingId, audioId = audioId))
    }

    @Insert suspend fun insertAudio(audio: AudioEntity): Long

    @Query("SELECT wav FROM audio WHERE id = :id")
    suspend fun audio(id: Long): ByteArray?

    @Query("SELECT hash FROM event WHERE sessionId = :sessionId AND hash IS NOT NULL ORDER BY id")
    suspend fun seals(sessionId: String): List<String>

    @Query("UPDATE session SET rootHash = :root, rootSig = :sig WHERE id = :id")
    suspend fun setRoot(id: String, root: String?, sig: String?)

    @Query("SELECT hash FROM event WHERE sessionId = :sessionId AND hash IS NOT NULL ORDER BY id DESC LIMIT 1")
    suspend fun lastHash(sessionId: String): String?

    @Query("SELECT startedAt FROM session WHERE id = :id")
    suspend fun startedAt(id: String): Long?

    @Query("SELECT * FROM event WHERE sessionId = :sessionId ORDER BY ts, id")
    fun events(sessionId: String): Flow<List<EventEntity>>

    @Insert suspend fun insertDrawing(drawing: DrawingEntity): Long

    @Query("SELECT png FROM drawing WHERE id = :id")
    suspend fun drawing(id: Long): ByteArray?

    @Query("DELETE FROM session WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("UPDATE session SET endedAt = :endedAt, title = :title WHERE id = :id")
    suspend fun endSession(id: String, endedAt: Long, title: String)

    @Query("UPDATE session SET endedAt = NULL WHERE id = :id")
    suspend fun reopen(id: String)

    @Query("SELECT * FROM session WHERE endedAt IS NOT NULL ORDER BY endedAt DESC")
    fun saved(): Flow<List<SessionEntity>>

    /** A session is idle from its end, or while open from its last row; only one idle past [cutoff] goes. */
    @Query("DELETE FROM session WHERE COALESCE(endedAt, (SELECT MAX(ts) FROM event WHERE event.sessionId = session.id), startedAt) < :cutoff")
    suspend fun deleteIdleBefore(cutoff: Long): Int
}

@Database(entities = [SessionEntity::class, EventEntity::class, DrawingEntity::class, AudioEntity::class], version = 7, exportSchema = true)
abstract class SessionDatabase : RoomDatabase() {
    abstract fun dao(): SessionDao

    companion object {
        const val FILE_NAME = "session.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE session ADD COLUMN role TEXT NOT NULL DEFAULT 'VICTIM'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE event ADD COLUMN itemId TEXT")
                db.execSQL("ALTER TABLE event ADD COLUMN reason TEXT")
                db.execSQL("ALTER TABLE event ADD COLUMN shownId INTEGER")
                db.execSQL("ALTER TABLE event ADD COLUMN via TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE session ADD COLUMN title TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE event ADD COLUMN hash TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE session ADD COLUMN rootHash TEXT")
                db.execSQL("ALTER TABLE session ADD COLUMN rootSig TEXT")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE event ADD COLUMN audioId INTEGER")
                db.execSQL("CREATE TABLE IF NOT EXISTS `audio` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` TEXT NOT NULL, `wav` BLOB NOT NULL, FOREIGN KEY(`sessionId`) REFERENCES `session`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_sessionId` ON `audio` (`sessionId`)")
            }
        }

        fun open(context: Context, key: ByteArray, onDestructiveMigration: () -> Unit = {}, fileName: String = FILE_NAME): SessionDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, SessionDatabase::class.java, fileName)
                .openHelperFactory(SupportOpenHelperFactory(key))
                // Purged rows are overwritten in place, and no write-ahead log keeps old pages around.
                .setJournalMode(JournalMode.TRUNCATE)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                // A downgrade drops sessions rather than failing every unlock; the officer is told (onDestructiveMigration).
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        db.query("PRAGMA secure_delete = ON").use { it.moveToFirst() }
                    }

                    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                        super.onDestructiveMigration(db)
                        onDestructiveMigration()
                    }
                })
                .build()
        }
    }
}
