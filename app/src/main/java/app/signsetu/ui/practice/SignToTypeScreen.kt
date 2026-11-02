package app.signsetu.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.signsetu.R
import app.signsetu.camera.CameraMirror
import app.signsetu.camera.LandmarkFrame
import app.signsetu.camera.LandmarkTracker
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.glossToText
import app.signsetu.sign.FeatureRing
import app.signsetu.sign.LIVE_PEEK_MS
import app.signsetu.sign.LetterCommitter
import app.signsetu.sign.MotionGate
import app.signsetu.sign.SignCapture
import app.signsetu.sign.SignGuess
import app.signsetu.sign.SignReader
import app.signsetu.sign.SignWindow
import app.signsetu.sign.decideRead
import app.signsetu.sign.glossOf
import app.signsetu.sign.handEnergy
import app.signsetu.ui.components.OfflineVoice
import app.signsetu.ui.theme.Spacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One word of the sentence: the reader's top 3 for a sign (the officer picks among them) or a spelled word. */
internal class Word(val choices: List<SignGuess>, picked: Int = 0) {
    var picked by mutableStateOf(picked)
    val text get() = choices.getOrNull(picked)?.word ?: "?"
}

/** Sign to type, outside any session: the person signs to the camera, each closed sign becomes a word and a held
 * handshape a letter, and the sentence builds up below in English and Hindi. Practice only: nothing is logged, kept
 * or sent, and everything is gone when the screen closes. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SignToTypeScreen(content: ContentBundle, tracker: LandmarkTracker?, reader: SignReader?, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val voice = remember { OfflineVoice(context) }
    DisposableEffect(voice) { onDispose { voice.shutdown() } }
    val words = remember { mutableStateListOf<Word>() }
    var live by remember { mutableStateOf<List<SignGuess>>(emptyList()) }
    var spelling by remember { mutableStateOf("") }
    var on by remember { mutableStateOf(false) }
    val engine = remember(reader) { Engine(reader, content, scope, { w -> words += w }, { live = it }, { spelling = it }) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.sign_to_type)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(stringResource(R.string.sign_to_type_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CameraMirror(
                tracker = tracker,
                modifier = Modifier.fillMaxWidth().height(300.dp),
                onFrame = if (on) engine::frame else null,
                placeholder = { Text(stringResource(R.string.sign_to_type_no_camera)) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(onClick = { if (on) engine.stop() else engine.start(); on = !on }, enabled = tracker != null && reader != null) {
                    Text(stringResource(if (on) R.string.sign_to_type_stop else R.string.sign_to_type_start))
                }
                OutlinedButton(onClick = { words.clear(); spelling = ""; engine.clearLetters() }) { Text(stringResource(R.string.sign_to_type_clear)) }
            }
            if (live.isNotEmpty()) Text(
                stringResource(R.string.reading_live, live.joinToString("  ·  ") { "${it.word.replace('_', ' ')} ${(it.score * 100).toInt()}%" }),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (spelling.isNotEmpty()) Text(stringResource(R.string.reading_spelled, spelling.uppercase()), style = MaterialTheme.typography.titleSmall)
            // Each word cycles through its top 3 on a tap, and a long sentence wraps.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                words.forEach { w ->
                    FilterChip(selected = w.picked > 0, onClick = { w.picked = (w.picked + 1) % w.choices.size.coerceAtLeast(1) }, label = { Text(w.text.replace('_', ' ')) })
                }
            }
            val gloss = words.flatMap { w -> glossOf(w.choices.getOrNull(w.picked), reader?.words.orEmpty()) }
            if (gloss.any { it != "?" }) {
                val draft = glossToText(gloss, content.words)
                Text(draft.en, style = MaterialTheme.typography.titleMedium)
                Text(draft.hi, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedButton(onClick = { voice.speak(draft.en, "en") }) { Text(stringResource(R.string.sign_to_type_say_en)) }
                    OutlinedButton(onClick = { voice.speak(draft.hi, "hi") }) { Text(stringResource(R.string.sign_to_type_say_hi)) }
                }
            }
        }
    }
}

/** The reading chain of a session's sign window, held in memory for this screen only. */
internal class Engine(
    private val reader: SignReader?,
    private val content: ContentBundle,
    private val scope: CoroutineScope,
    private val onWord: (Word) -> Unit,
    private val onLive: (List<SignGuess>) -> Unit,
    private val onSpelling: (String) -> Unit,
) {
    private var capture: SignCapture? = null
    private val ring = FeatureRing()
    private val gate = MotionGate()
    private val letters = LetterCommitter()
    private var prev: LandmarkFrame? = null
    private var lastPeek = 0L
    private var lastLetter = 0L
    @Volatile private var busy = false

    fun start() = synchronized(this) { capture = SignCapture(SignWindow.NARRATIVE); ring.clear(); gate.reset() }

    fun stop() {
        val c = synchronized(this) { capture.also { capture = null } } ?: return
        c.finish().forEach(::read)
        scope.launch { onLive(emptyList()) }
    }

    fun clearLetters() = synchronized(this) { letters.clear() }

    /** On the analysis thread. */
    fun frame(f: LandmarkFrame) {
        val r = reader ?: return
        val closed: List<List<LandmarkFrame>>
        val peekWord: List<LandmarkFrame>?
        val peekLetter: List<LandmarkFrame>?
        synchronized(this) {
            val c = capture ?: return
            closed = c.add(f)
            val moving = gate.update(handEnergy(prev, f).total)
            prev = f
            ring.push(f)
            val hands = f.leftHand != null || f.rightHand != null
            if (!hands || moving) letters.see(f.timestampMs, null)
            val free = !busy
            peekWord = if (free && moving && f.timestampMs - lastPeek >= LIVE_PEEK_MS && ring.size >= 8) ring.frames().also { lastPeek = f.timestampMs } else null
            peekLetter = if (free && peekWord == null && !moving && hands && f.timestampMs - lastLetter >= 150 && ring.size >= 8) ring.frames(8).also { lastLetter = f.timestampMs } else null
            if (peekWord != null || peekLetter != null) busy = true
            if (c.full) capture = SignCapture(SignWindow.NARRATIVE)
        }
        closed.forEach(::read)
        if (peekWord != null || peekLetter != null) scope.launch(Dispatchers.Default) {
            try {
                if (peekWord != null) {
                    val top = runCatching { r.read(peekWord) }.getOrNull().orEmpty().sortedByDescending { it.score }.take(3)
                    withContext(Dispatchers.Main) { onLive(top) }
                } else if (peekLetter != null) {
                    val g = runCatching { r.letter(peekLetter) }.getOrNull()
                    val committed = synchronized(this@Engine) { letters.see(peekLetter.last().timestampMs, g) }
                    if (committed != null) { val s = synchronized(this@Engine) { letters.letters.toString() }; withContext(Dispatchers.Main) { onSpelling(s) } }
                }
            } finally { busy = false }
        }
    }

    private fun read(frames: List<LandmarkFrame>) {
        val r = reader ?: return
        scope.launch(Dispatchers.Default) {
            val all = runCatching { r.read(frames) }.getOrNull().orEmpty().sortedByDescending { it.score }
            val seg = decideRead(frames, all, null, emptySet(), r.words, content.words, r.cards, r.conformal)
            val choices = seg.guesses.orEmpty().ifEmpty { return@launch }
            withContext(Dispatchers.Main) { onWord(Word(choices)) }
        }
    }
}
