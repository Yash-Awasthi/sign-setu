package app.signsetu.ui.session

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.LexiconEntry
import app.signsetu.data.content.Phrase
import app.signsetu.data.content.PhraseSlot
import app.signsetu.data.content.Question
import app.signsetu.data.content.SignMode
import app.signsetu.data.content.pick
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.Role
import app.signsetu.data.session.Session
import app.signsetu.data.session.SessionEvent
import app.signsetu.data.session.SessionRepository
import app.signsetu.camera.LandmarkFrame
import app.signsetu.sign.SignCapture
import app.signsetu.sign.SignReader
import app.signsetu.sign.SignSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import app.signsetu.export.ReportRow
import app.signsetu.export.ReportText
import app.signsetu.export.SessionReport
import java.io.File

/** What the officer has prepared but not yet shown (FR-15). */
@Immutable
sealed interface Pending {
    data class Ask(val question: Question) : Pending
    /** [caption] is the assistant's translation the officer accepted (D-24); the character still signs [text]. */
    data class FreeText(
        val text: String,
        val cardIds: List<String>,
        val modes: Set<SignMode> = emptySet(),
        val caption: String? = null,
    ) : Pending
    data class Cards(val cardIds: List<String>) : Pending
    data class ShowBack(val entry: LexiconEntry) : Pending
    data class PhraseAsk(val phrase: Phrase, val slot: PhraseSlot) : Pending
    data object LanguageFinder : Pending
    data object Drawing : Pending
}

enum class Mode { OFFICER, SHOW }

/** Machine keys for SYSTEM events; the log shows them in the officer's language. */
const val SYSTEM_LANGUAGE_FINDER = "language_finder"
const val SYSTEM_DRAWING = "drawing"
const val SYSTEM_READ_SIGNS = "read_signs"

/** The first thing every subject sees: the officer introduces themself (03-ux §7). */
private const val OPENING_QUESTION = "s1"

@Immutable
data class SessionUi(
    val session: Session,
    val selectedGroup: String,
    val sensitiveUnlocked: Boolean,
    val pending: Pending?,
    val mode: Mode,
    val rotated: Boolean,
    /** Card or Yes/No the subject chose for the item currently on screen. */
    val answeredCard: String?,
    val answeredYes: Boolean?,
    val chosenLanguage: String?,
    val events: List<SessionEvent>,
    /** Signs read so far while the subject is signing an answer; null when not reading. */
    val reading: List<SignSegment>?,
    /** Landmark frames of each signed answer, by event id: memory only, never stored or exported (D-25). */
    val signed: Map<Long, List<SignSegment>>,
)

private data class Local(
    val selectedGroup: String = "safety",
    val sensitiveUnlocked: Boolean = false,
    val pending: Pending? = null,
    val mode: Mode = Mode.OFFICER,
    val rotated: Boolean = false,
    val answeredCard: String? = null,
    val answeredYes: Boolean? = null,
    val chosenLanguage: String? = null,
    val reading: List<SignSegment>? = null,
    val signed: Map<Long, List<SignSegment>> = emptyMap(),
)

class SessionViewModel(
    private val sessionId: String,
    private val repo: SessionRepository,
    val content: ContentBundle,
    private val officerLang: String,
    private val report: SessionReport,
    private val reader: SignReader? = null,
) : ViewModel() {

    private val local = MutableStateFlow(Local())
    @Volatile private var capture: SignCapture? = null
    private val reads = mutableListOf<Job>()
    // One read at a time: a model need not be safe to call from several threads.
    private val readDispatcher = Dispatchers.Default.limitedParallelism(1)

    val ui: StateFlow<SessionUi?> = combine(repo.session(sessionId).filterNotNull(), repo.events(sessionId), local) { s, events, l ->
        SessionUi(s, l.selectedGroup, l.sensitiveUnlocked, l.pending, l.mode, l.rotated, l.answeredCard, l.answeredYes, l.chosenLanguage, events, l.reading, l.signed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            val session = repo.session(sessionId).first() ?: return@launch
            // Starting a session is the officer's confirmation of its opening: the stage greets the person first,
            // or, for someone who speaks another language, lets them find it (FR-63).
            if (repo.events(sessionId).first().isEmpty()) {
                val opening = if (session.profile == Profile.OTHER_LANGUAGE) {
                    Pending.LanguageFinder
                } else {
                    content.questions.firstOrNull { it.id == OPENING_QUESTION }?.let { Pending.Ask(it) }
                }
                opening?.let { present(it, session.subjectLang) }
            }
        }
    }

    fun selectGroup(id: String) = local.update { it.copy(selectedGroup = id) }
    fun unlockSensitive() = local.update { it.copy(sensitiveUnlocked = true) }
    fun prepare(pending: Pending?) = local.update { it.copy(pending = pending) }
    fun toggleRotation() = local.update { it.copy(rotated = !it.rotated) }

    fun setProfile(profile: Profile) = viewModelScope.launch { repo.setProfile(sessionId, profile) }
    fun setSubjectLang(lang: String) = viewModelScope.launch { repo.setSubjectLang(sessionId, lang) }

    // The open group may not exist for the new role (sensitive is victim-only), so fall back to the shared first one.
    fun setRole(role: Role) = viewModelScope.launch {
        local.update { it.copy(selectedGroup = Local().selectedGroup) }
        repo.setRole(sessionId, role)
    }

    /** Officer confirms: the item is logged, then the subject panel takes the screen. */
    fun show() {
        val state = ui.value ?: return
        val pending = local.value.pending ?: return
        ShowTiming.start()
        viewModelScope.launch { present(pending, state.session.subjectLang) }
    }

    /** Shows what is prepared, if anything, and reads the subject's signed answer (D-25). */
    fun readSigns() {
        val state = ui.value ?: return
        val pending = local.value.pending
        ShowTiming.start()
        viewModelScope.launch {
            if (pending != null) {
                present(pending, state.session.subjectLang)
            } else {
                repo.log(sessionId, EventType.SYSTEM, EventSource.OFFICER, text = SYSTEM_READ_SIGNS)
                local.update { it.copy(mode = Mode.SHOW, answeredCard = null, answeredYes = null, chosenLanguage = null) }
            }
            startReading()
        }
    }

    fun startReading() {
        if (capture != null) return
        capture = SignCapture()
        local.update { it.copy(reading = emptyList()) }
    }

    /** Every tracked camera frame, on the analysis thread. */
    fun onSignFrame(frame: LandmarkFrame) {
        val c = capture ?: return
        val reachedCap = synchronized(c) {
            val wasFull = c.full
            c.add(frame).forEach(::readSegment)
            !wasFull && c.full
        }
        if (reachedCap) viewModelScope.launch { finishReading() }
    }

    private fun readSegment(frames: List<LandmarkFrame>) {
        val segment = SignSegment(frames)
        local.update { it.copy(reading = it.reading?.plus(segment)) }
        reads += viewModelScope.launch(readDispatcher) {
            val guesses = reader?.let { r -> runCatching { r.read(frames) }.getOrNull() }.orEmpty().sortedByDescending { it.score }.take(3)
            val read = SignSegment(frames, guesses)
            local.update { l -> l.copy(reading = l.reading?.map { if (it === segment) read else it }) }
        }
    }

    /** The subject is done signing: the words read are logged as the app's reading, never as confirmed (FR-66). */
    fun finishReading() {
        val c = capture ?: return
        capture = null
        val pending = synchronized(c) {
            c.finish().forEach(::readSegment)
            reads.toList().also { reads.clear() }
        }
        viewModelScope.launch {
            pending.joinAll()
            val segments = local.value.reading.orEmpty()
            local.update { it.copy(reading = null) }
            if (segments.isNotEmpty()) logSigned(segments, segments.map { it.word ?: "?" }, EventSource.SUBJECT, "en")
        }
    }

    /** The officer's reading of a signed answer, logged beside the app's so the record shows both. */
    fun acceptSigned(eventId: Long, words: List<String>) {
        val segments = local.value.signed[eventId] ?: return
        viewModelScope.launch { logSigned(segments, words, EventSource.OFFICER, officerLang) }
    }

    private suspend fun logSigned(segments: List<SignSegment>, words: List<String>, source: EventSource, lang: String) {
        repo.log(sessionId, EventType.ANSWER_SIGNED, source, lang, text = words.joinToString(" "))
        val id = repo.events(sessionId).first().last { it.type == EventType.ANSWER_SIGNED }.id
        local.update { it.copy(signed = it.signed + (id to segments)) }
    }

    override fun onCleared() {
        capture = null
        local.update { it.copy(reading = null, signed = emptyMap()) }
    }

    private suspend fun present(pending: Pending, lang: String) {
        local.update { it.copy(pending = pending) }
        when (pending) {
            is Pending.Ask -> repo.log(
                sessionId, EventType.QUESTION_SHOWN, EventSource.PRESET, lang,
                text = pending.question.text.pick(officerLang), lexiconId = pending.question.lexicon,
            )
            // A machine-made caption is logged next to the officer's own words, so the record shows both.
            is Pending.FreeText -> repo.log(sessionId, EventType.QUESTION_SHOWN, EventSource.TEXT, lang, text = listOfNotNull(pending.text, pending.caption).joinToString("\n"))
            is Pending.Cards -> repo.log(
                sessionId, EventType.QUESTION_SHOWN, EventSource.BOARD, lang,
                text = pending.cardIds.joinToString { id -> content.cards[id]?.label?.pick(officerLang) ?: id },
            )
            is Pending.ShowBack -> repo.log(
                sessionId, EventType.QUESTION_SHOWN, EventSource.SHOW_BACK, lang,
                text = pending.entry.label.pick(officerLang), cardId = pending.entry.card, lexiconId = pending.entry.id,
            )
            is Pending.PhraseAsk -> repo.log(
                sessionId, EventType.QUESTION_SHOWN, EventSource.PHRASE, lang,
                text = pending.slot.text.pick(officerLang), cardId = pending.slot.card,
            )
            Pending.LanguageFinder -> repo.log(sessionId, EventType.SYSTEM, EventSource.OFFICER, text = SYSTEM_LANGUAGE_FINDER)
            Pending.Drawing -> repo.log(sessionId, EventType.SYSTEM, EventSource.OFFICER, text = SYSTEM_DRAWING)
        }
        local.update { it.copy(mode = Mode.SHOW, answeredCard = null, answeredYes = null, chosenLanguage = null) }
    }

    fun answerCard(cardId: String) {
        local.update { it.copy(answeredCard = cardId, answeredYes = null) }
        viewModelScope.launch {
            repo.log(
                sessionId, EventType.ANSWER_CARD, EventSource.SUBJECT, officerLang,
                text = content.cards[cardId]?.label?.pick(officerLang), cardId = cardId,
            )
        }
    }

    fun answerYesNo(yes: Boolean) {
        val pending = local.value.pending
        local.update { it.copy(answeredYes = yes, answeredCard = null) }
        viewModelScope.launch {
            if (pending is Pending.ShowBack) {
                // A gesture meaning counts only when the subject confirms it (FR-66).
                if (yes) {
                    repo.log(sessionId, EventType.ANSWER_GESTURE, EventSource.SUBJECT, officerLang, text = pending.entry.label.pick(officerLang), cardId = pending.entry.card, lexiconId = pending.entry.id)
                } else {
                    repo.log(sessionId, EventType.ANSWER_UNCLEAR, EventSource.SUBJECT, officerLang, text = pending.entry.label.pick(officerLang), cardId = pending.entry.card, lexiconId = pending.entry.id)
                }
            } else {
                val id = if (yes) "yes" else "no"
                repo.log(sessionId, EventType.ANSWER_CARD, EventSource.SUBJECT, officerLang, text = content.cards[id]?.label?.pick(officerLang), cardId = id)
            }
        }
    }

    fun chooseLanguage(code: String) {
        local.update { it.copy(chosenLanguage = code) }
        viewModelScope.launch {
            repo.log(sessionId, EventType.LANGUAGE_CHOSEN, EventSource.SUBJECT, code, text = content.language(code)?.english)
        }
    }

    /** The subject wrote their own answer on the stage; it is logged as typed, never interpreted. */
    fun answerText(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { repo.log(sessionId, EventType.ANSWER_TEXT, EventSource.SUBJECT, ui.value?.session?.subjectLang, text = text.trim()) }
    }

    fun submitDrawing(png: ByteArray) {
        viewModelScope.launch {
            repo.log(sessionId, EventType.ANSWER_DRAWING, EventSource.SUBJECT, drawingPng = png)
            // The subject still holds the phone; only the officer handle, with re-authentication, leaves Show (FR-61).
            local.update { it.copy(pending = null) }
        }
    }

    /** Called after the officer re-authenticates (FR-61). */
    fun returnToOfficer() {
        finishReading()
        val l = local.value
        viewModelScope.launch {
            val pending = l.pending
            if (pending is Pending.ShowBack && l.answeredYes == null) {
                repo.log(sessionId, EventType.ANSWER_UNCLEAR, EventSource.SUBJECT, officerLang, text = pending.entry.label.pick(officerLang), cardId = pending.entry.card, lexiconId = pending.entry.id)
            }
            l.chosenLanguage?.let { repo.setSubjectLang(sessionId, it) }
            local.update { it.copy(mode = Mode.OFFICER, pending = null) }
        }
    }

    fun addNote(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { repo.log(sessionId, EventType.NOTE, EventSource.OFFICER, officerLang, text = text.trim()) }
    }

    suspend fun drawing(id: Long): ByteArray? = repo.drawing(id)

    /** Builds the encrypted report from everything logged so far; returns the file and its one-time passphrase. */
    suspend fun exportReport(text: ReportText, detail: (SessionEvent) -> String): Pair<File, String> {
        val rows = (ui.value?.events ?: emptyList()).withQuestions().map { (e, q) -> ReportRow(e, q, detail(e)) }
        return report.export(rows, text) { repo.drawing(it) }
    }

    fun endSession(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.purge(sessionId)
            onDone()
        }
    }
}
