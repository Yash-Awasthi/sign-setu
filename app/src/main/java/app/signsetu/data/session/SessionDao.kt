package app.signsetu.data.session

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

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
    suspend fun insertSealed(event: EventEntity, drawing: DrawingEntity?, audio: AudioEntity? = null, landmarks: LandmarksEntity? = null): Long {
        val drawingId = drawing?.let { insertDrawing(it) }
        val audioId = audio?.let { insertAudio(it) }
        val landmarksId = landmarks?.let { insertLandmarks(it) }
        return insertEvent(event.copy(drawingId = drawingId, audioId = audioId, landmarksId = landmarksId))
    }

    @Insert suspend fun insertLandmarks(landmarks: LandmarksEntity): Long

    @Query("SELECT json FROM landmarks WHERE id = :id")
    suspend fun landmarks(id: Long): ByteArray?

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

    @Query("UPDATE session SET endedAt = NULL, savedThrough = (SELECT MAX(id) FROM event WHERE sessionId = :id) WHERE id = :id")
    suspend fun reopen(id: String)

    @Query("UPDATE session SET savedThrough = NULL WHERE id = :id")
    suspend fun clearSavedThrough(id: String)

    @Query("UPDATE session SET stationFile = :file, stationHead = :head WHERE id = :id")
    suspend fun setStationExport(id: String, file: String, head: String)

    @Query("UPDATE session SET phoneFolder = :folder, phoneHead = :head WHERE id = :id")
    suspend fun setPhoneExport(id: String, folder: String, head: String)

    /** Rows written after [after], with what they hold: a reopened session's unsaved part. */
    @Transaction
    suspend fun discardAfter(sessionId: String, after: Long) {
        deleteDrawingsAfter(sessionId, after)
        deleteAudioAfter(sessionId, after)
        deleteLandmarksAfter(sessionId, after)
        deleteEventsAfter(sessionId, after)
    }

    @Query("DELETE FROM drawing WHERE id IN (SELECT drawingId FROM event WHERE sessionId = :sessionId AND id > :after AND drawingId IS NOT NULL)")
    suspend fun deleteDrawingsAfter(sessionId: String, after: Long)

    @Query("DELETE FROM audio WHERE id IN (SELECT audioId FROM event WHERE sessionId = :sessionId AND id > :after AND audioId IS NOT NULL)")
    suspend fun deleteAudioAfter(sessionId: String, after: Long)

    @Query("DELETE FROM landmarks WHERE id IN (SELECT landmarksId FROM event WHERE sessionId = :sessionId AND id > :after AND landmarksId IS NOT NULL)")
    suspend fun deleteLandmarksAfter(sessionId: String, after: Long)

    @Query("DELETE FROM event WHERE sessionId = :sessionId AND id > :after")
    suspend fun deleteEventsAfter(sessionId: String, after: Long)

    @Query("SELECT * FROM session WHERE endedAt IS NOT NULL ORDER BY endedAt DESC")
    fun saved(): Flow<List<SessionEntity>>

    /** A session is idle from its end, or while open from its last row; only one idle past [cutoff] goes. */
    @Query("DELETE FROM session WHERE COALESCE(endedAt, (SELECT MAX(ts) FROM event WHERE event.sessionId = session.id), startedAt) < :cutoff")
    suspend fun deleteIdleBefore(cutoff: Long): Int
}
