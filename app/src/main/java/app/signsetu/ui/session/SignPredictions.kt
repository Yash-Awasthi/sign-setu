package app.signsetu.ui.session

import app.signsetu.ui.theme.motion
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.SessionEvent
import app.signsetu.BuildConfig
import app.signsetu.R
import app.signsetu.camera.verifiedAsset
import app.signsetu.gloss.DebugRecorder
import app.signsetu.gloss.Gender
import app.signsetu.gloss.GlossLattice
import app.signsetu.gloss.IntentMatch
import app.signsetu.gloss.Phrasebook
import app.signsetu.gloss.Rung
import app.signsetu.assistant.ModelStore
import app.signsetu.gloss.RERANK_SYSTEM
import app.signsetu.gloss.faithful
import app.signsetu.gloss.parseOrder
import app.signsetu.gloss.rerankPrompt
import app.signsetu.ui.components.LocalSentenceHelper
import app.signsetu.gloss.ladder
import app.signsetu.sign.SENTENCE_SYSTEM
import app.signsetu.sign.SentenceGuess
import app.signsetu.sign.SignSegment
import app.signsetu.sign.parseSentences
import app.signsetu.sign.sentencePrompt
import app.signsetu.ui.components.LocalOnlineAssistant
import app.signsetu.ui.theme.Spacing

private const val PREDICTIONS = 4

/** The latest machine reading still waiting on the officer: no answer, and no reading of the officer's, has come since. */
internal fun pendingReading(events: List<SessionEvent>): SessionEvent? {
    val i = events.indexOfLast { it.type == EventType.ANSWER_SIGNED && it.source == EventSource.MACHINE }
    if (i < 0) return null
    return events[i].takeIf { events.drop(i + 1).none { it.type in AnswerEvents } }
}

/** What the app may say about a reading (docs/15-gloss-to-text.md): the phrasebook's rung for the lattice, with the
 * question on screen as evidence, and the online assistant's sentences when it is switched on, each one checked so it
 * holds no word that was not signed. */
internal class Reading(
    val rung: Rung?,
    val online: List<SentenceGuess>,
    val alternatives: List<SentenceGuess> = emptyList(),
    /** The imported sentence helper's first choice when it differs from the rung's sentence. */
    val helperPick: SentenceGuess? = null,
)

@Composable
internal fun rememberReading(
    eventId: Long,
    segments: List<SignSegment>,
    pending: Pending?,
    content: ContentBundle,
    names: Collection<String>,
    details: Collection<String>,
    gender: Gender = Gender.UNKNOWN,
): Reading {
    val context = LocalContext.current
    val book by produceState<Phrasebook?>(null) { value = withContext(Dispatchers.IO) { loadPhrasebook(context) } }
    val question = (pending as? Pending.Ask)?.question?.id
    // Keyed on the head movement too: a nod or shake just after the window updates the reading.
    val alternatives = remember { mutableListOf<SentenceGuess>() }
    val rung = remember(eventId, book, segments.firstOrNull()?.windowHead, gender) {
        book?.let { b ->
            GlossLattice.of(segments, BuildConfig.VERSION_NAME).let { l ->
                val matches = b.match(l, question)
                ladder(l, matches, b, gender).also { r ->
                    // The other intents that pass the check, for the officer to pick when the rung's is not it (docs/15-gloss-to-text.md).
                    // Under the word strip no sentence is shown, so the best of them is offered too.
                    val shown = (r as? Rung.Sentence)?.match ?: (r as? Rung.FixWords)?.match
                    alternatives.clear()
                    alternatives += matches.filter { it != shown && faithful(it, l) && it.posterior >= ALTERNATIVE_FLOOR }.take(2)
                        .map { m -> SentenceGuess(segments.mapIndexed { i, s -> m.fills.firstOrNull { it.token == i }?.label ?: s.word ?: "?" }, b.english(m)) }
                    // Debug builds only: the lattice and the rung, the start of the gloss-to-text decision log.
                    if (BuildConfig.DEBUG) android.util.Log.d("SignSetuGloss", "q=$question rung=${r::class.simpleName} lattice=${l.toJson()}")
                    DebugRecorder.decision(eventId, "rung", "${r::class.simpleName}: ${(r as? Rung.Sentence)?.en ?: (r as? Rung.FixWords)?.en ?: ""}")
                }
            }
        }
    }
    val cloud = LocalOnlineAssistant.current
    val guesses = segments.map { it.candidates.ifEmpty { it.guesses.orEmpty() }.take(ONLINE_GUESSES) }
    val caption = describeItem(pending, content, "en").caption
    // A sure full sentence needs no second opinion; anything less may get one.
    val online by produceState(emptyList<SentenceGuess>(), eventId, rung) {
        if (rung != null && rung !is Rung.Sentence && guesses.any { it.isNotEmpty() }) {
            value = cloud.ask(SENTENCE_SYSTEM, sentencePrompt(caption, guesses), names, 250, details)?.let { parseSentences(it, guesses) }.orEmpty()
        }
    }
    // The imported helper only reorders checked sentences, and only when the ladder is unsure.
    val helper = LocalSentenceHelper.current
    val helperPick by produceState<SentenceGuess?>(null, eventId, rung) {
        val unsure = rung is Rung.FixWords || rung is Rung.WordStrip
        if (helper == null || !unsure || helper.state() != ModelStore.State.READY) return@produceState
        val first = (rung as? Rung.FixWords)?.let { SentenceGuess(segments.mapIndexed { i, s -> it.match.fills.firstOrNull { f -> f.token == i }?.label ?: s.word ?: "?" }, it.en) }
        val pool = listOfNotNull(first) + alternatives.toList()
        if (pool.size < 2) return@produceState
        val signs = segments.map { it.candidates.ifEmpty { it.guesses.orEmpty() }.take(3).map { g -> g.word } }
        val reply = helper.use { it.chat(RERANK_SYSTEM, rerankPrompt(caption, signs, pool.map { g -> g.text }), HELPER_TOKENS) }
        val pick = reply?.let { parseOrder(it, pool.size) }?.firstOrNull()?.let(pool::get)
        value = pick?.takeIf { it != first }
    }
    return Reading(rung, online, alternatives.toList(), helperPick)
}

/** The bundled phrasebook, memory-mapped once its file matches the manifest (it is stored uncompressed, like every
 * file the detection pipeline reads); null without it, and readings fall back to the word strip. */
private fun loadPhrasebook(context: android.content.Context): Phrasebook? = runCatching {
    verifiedAsset(context, PHRASEBOOK)!!.let { b -> Phrasebook(ByteArray(b.remaining()).also { b.duplicate().get(it) }.decodeToString()) }
}.onFailure { if (BuildConfig.DEBUG) android.util.Log.w("SignSetuGloss", "phrasebook not loaded", it) }.getOrNull()

private const val PHRASEBOOK = "intents.json"

/** An intent below this share of the posterior is not worth offering as an alternative. */
private const val ALTERNATIVE_FLOOR = 0.1f

/** The helper answers with a short list of numbers. */
private const val HELPER_TOKENS = 16

/** Guesses per sign sent online: enough to recover a misread, few enough to keep the prompt short. */
private const val ONLINE_GUESSES = 6

/** The reading as the ladder allows: a full sentence (English and Hindi) to accept with one tap, the same with the
 * doubtful signs marked, or nothing but the signs; "sign again" when too little was read. Under it, the reader's top
 * guesses for each sign: the officer taps one per sign, which hides the rest, and tapping it again takes it back.
 * Once every sign with guesses has one, the reading is logged once as the officer's; the record is append-only, so
 * half-made picks never reach it. The subject is never asked to confirm. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SignPredictions(
    eventId: Long,
    segments: List<SignSegment>,
    onPick: (words: List<String>, sentence: String?) -> Unit,
    modifier: Modifier = Modifier,
    reading: Reading = Reading(null, emptyList()),
    /** A camera read (nod, point, fingers…) has no signs: its text is the one reading to accept. */
    cameraText: String? = null,
    /** The officer types what was signed when no guess is right (the pencil). */
    onCorrect: ((String) -> Unit)? = null,
) {
    val picked = remember(eventId) { mutableStateMapOf<Int, String>() }
    var typing by remember(eventId) { mutableStateOf(false) }
    var typed by remember(eventId) { mutableStateOf("") }
    val guesses = segments.map { s -> s.guesses.orEmpty().take(PREDICTIONS).map { it.word } }
    val rung = reading.rung
    if (segments.isEmpty() && cameraText != null) {
        SuggestionChip(onClick = { onPick(listOf(cameraText), cameraText) }, label = { Text(stringResource(R.string.reading_camera, cameraText)) }, modifier = modifier.padding(horizontal = Spacing.lg))
        return
    }
    if (guesses.all { it.isEmpty() } && rung == null) return
    // The words the sentence stands on, one per sign, so the record keeps them beside the sentence.
    fun wordsOf(m: IntentMatch) = segments.mapIndexed { i, s -> m.fills.firstOrNull { it.token == i }?.label ?: s.word ?: "?" }
    // A long account reads as many signs; the guesses scroll so the conversation above stays in view.
    Column(modifier.fillMaxWidth().animateContentSize(motion(spring())).heightIn(max = 220.dp).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg, vertical = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (picked.isEmpty()) when (rung) {
            is Rung.Sentence -> SentenceCard(rung.en, rung.hi, null, rung.confirm) { onPick(wordsOf(rung.match), rung.en) }
            is Rung.FixWords -> SentenceCard(rung.en, rung.hi, rung.doubtful.mapNotNull { segments.getOrNull(it)?.word }, rung.confirm) { onPick(wordsOf(rung.match), rung.en) }
            Rung.SignAgain -> Text(stringResource(R.string.reading_sign_again), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            else -> Unit
        }
        if (picked.isEmpty()) reading.helperPick?.let { g ->
            SuggestionChip(onClick = { onPick(g.words, g.text) }, label = { Text(stringResource(R.string.reading_helper_pick, g.text), style = MaterialTheme.typography.bodyMedium) })
        }
        if (picked.isEmpty() && rung !is Rung.Sentence) reading.alternatives.filter { it != reading.helperPick }.forEach { g ->
            SuggestionChip(onClick = { onPick(g.words, g.text) }, label = { Text(g.text, style = MaterialTheme.typography.bodyMedium) })
        }
        if (picked.isEmpty()) reading.online.forEach { g ->
            SuggestionChip(onClick = { onPick(g.words, g.text) }, label = { Text(g.text, style = MaterialTheme.typography.bodyMedium) })
        }
        guesses.forEachIndexed { i, words ->
            if (words.isEmpty()) return@forEachIndexed
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val choice = picked[i]
                (if (choice != null) listOf(choice) else words).forEach { w ->
                    FilterChip(
                        selected = choice == w,
                        onClick = {
                            if (choice == w) picked.remove(i) else picked[i] = w
                            if (guesses.indices.all { n -> guesses[n].isEmpty() || n in picked }) {
                                onPick(segments.mapIndexed { n, s -> picked[n] ?: s.word ?: "?" }, null)
                            }
                        },
                        label = { Text(w.replace('_', ' ')) },
                    )
                }
            }
        }
        if (onCorrect != null && segments.isNotEmpty()) {
            if (!typing) {
                IconButton(onClick = { typing = true }) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.reading_correct)) }
            } else {
                OutlinedTextField(
                    value = typed, onValueChange = { typed = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.reading_correct)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (typed.isNotBlank()) { onCorrect(typed); typing = false } }),
                )
            }
        }
    }
}

/** One sentence to accept with a tap: English over Hindi, the signs to check under it, and a note when it is risky. */
@Composable
private fun SentenceCard(en: String, hi: String, check: List<String>?, confirm: Boolean, onUse: () -> Unit) {
    OutlinedCard(onClick = onUse, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(en, style = MaterialTheme.typography.titleSmall)
            Text(hi, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!check.isNullOrEmpty()) Text(stringResource(R.string.reading_check, check.joinToString(", ") { it.replace('_', ' ') }), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            if (confirm) Text(stringResource(R.string.reading_confirm), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
        }
    }
}
