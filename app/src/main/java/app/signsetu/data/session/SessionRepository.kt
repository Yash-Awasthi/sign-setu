package app.signsetu.data.session

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

enum class Profile { YOUNG_CHILD, ISL_USER, OTHER_LANGUAGE, NOT_SURE }

enum class EventType { QUESTION_SHOWN, ANSWER_CARD, ANSWER_DRAWING, ANSWER_GESTURE, ANSWER_UNCLEAR, LANGUAGE_CHOSEN, NOTE, SYSTEM }

enum class EventSource { PRESET, TEXT, BOARD, SHOW_BACK, SUBJECT, OFFICER }

@Immutable
data class Session(val id: String, val startedAt: Long, val profile: Profile, val subjectLang: String)

@Immutable
data class SessionEvent(
    val id: Long,
    val ts: Long,
    val type: EventType,
    val source: EventSource,
    val lang: String?,
    val text: String?,
    val cardId: String?,
    val lexiconId: String?,
    val drawingId: Long?,
)

class SessionRepository(private val dao: SessionDao, private val clock: () -> Long = System::currentTimeMillis) {

    suspend fun openSession(): Session? = dao.openSession()?.toModel()

    fun session(id: String): Flow<Session?> = dao.session(id).map { it?.toModel() }

    suspend fun start(profile: Profile, subjectLang: String): String {
        val id = UUID.randomUUID().toString()
        dao.insertSession(SessionEntity(id, clock(), null, profile.name, subjectLang))
        return id
    }

    suspend fun setSubjectLang(id: String, lang: String) = dao.setSubjectLang(id, lang)
    suspend fun setProfile(id: String, profile: Profile) = dao.setProfile(id, profile.name)

    fun events(sessionId: String): Flow<List<SessionEvent>> = dao.events(sessionId).map { list -> list.map { it.toModel() } }

    /** Persisted before the interface reflects it (FR-34). */
    suspend fun log(
        sessionId: String,
        type: EventType,
        source: EventSource,
        lang: String? = null,
        text: String? = null,
        cardId: String? = null,
        lexiconId: String? = null,
        drawingPng: ByteArray? = null,
    ) {
        val drawingId = drawingPng?.let { dao.insertDrawing(DrawingEntity(sessionId = sessionId, png = it)) }
        dao.insertEvent(EventEntity(0, sessionId, clock(), type.name, source.name, lang, text, cardId, lexiconId, drawingId))
    }

    suspend fun drawing(id: Long): ByteArray? = dao.drawing(id)

    /** Ending a session purges it; there is no archive (FR-06). */
    suspend fun purge(sessionId: String) = dao.deleteSession(sessionId)

    suspend fun purgeExpired(retentionHours: Int): Int = dao.deleteStartedBefore(clock() - retentionHours * 3_600_000L)

    private fun SessionEntity.toModel() = Session(id, startedAt, Profile.valueOf(profile), subjectLang)

    private fun EventEntity.toModel() = SessionEvent(
        id, ts, EventType.valueOf(type), EventSource.valueOf(source), lang, text, cardId, lexiconId, drawingId,
    )
}
