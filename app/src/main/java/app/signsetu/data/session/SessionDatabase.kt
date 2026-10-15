package app.signsetu.data.session

import android.content.Context
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
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteDatabase

// No table has a field for a subject's name, address, phone number or photograph (02-architecture §6).

@Entity(tableName = "session")
data class SessionEntity(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val endedAt: Long?,
    val profile: String,
    val subjectLang: String,
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

    @Insert suspend fun insertEvent(event: EventEntity): Long

    @Query("SELECT * FROM event WHERE sessionId = :sessionId ORDER BY ts, id")
    fun events(sessionId: String): Flow<List<EventEntity>>

    @Insert suspend fun insertDrawing(drawing: DrawingEntity): Long

    @Query("SELECT png FROM drawing WHERE id = :id")
    suspend fun drawing(id: Long): ByteArray?

    @Query("DELETE FROM session WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("DELETE FROM session WHERE startedAt < :cutoff")
    suspend fun deleteStartedBefore(cutoff: Long): Int

    @Query("DELETE FROM session")
    suspend fun deleteAll()
}

@Database(entities = [SessionEntity::class, EventEntity::class, DrawingEntity::class], version = 1, exportSchema = true)
abstract class SessionDatabase : RoomDatabase() {
    abstract fun dao(): SessionDao

    companion object {
        const val FILE_NAME = "session.db"

        fun open(context: Context, key: ByteArray): SessionDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, SessionDatabase::class.java, FILE_NAME)
                .openHelperFactory(SupportOpenHelperFactory(key))
                // Purged rows are overwritten in place, and no write-ahead log keeps old pages around (SEC-08).
                .setJournalMode(JournalMode.TRUNCATE)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        db.query("PRAGMA secure_delete = ON").use { it.moveToFirst() }
                    }
                })
                .build()
        }
    }
}
