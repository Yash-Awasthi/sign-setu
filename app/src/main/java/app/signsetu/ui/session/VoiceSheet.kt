package app.signsetu.ui.session

import android.Manifest
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.signsetu.R
import app.signsetu.net.BrainClient
import app.signsetu.ui.theme.Spacing
import app.signsetu.voice.VoiceCapture
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Tap, and the person speaks: their voice is kept in the session record, encrypted and sealed like every answer.
 * With a station brain paired, their words appear as they talk and the take stops by itself when they finish; the
 * whole take is then transcribed again, and that text is added as the app's reading. Recording needs the consent
 * logged first, for this session; the officer's own speech is never recorded (the keyboard's microphone types for
 * them). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VoiceSheet(
    consented: Boolean,
    brain: BrainClient?,
    lang: String?,
    onConsent: () -> Unit,
    onRecorded: (ByteArray) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val capture = remember { VoiceCapture(context) }
    var status by remember { mutableStateOf<Int?>(null) }
    var level by remember { mutableFloatStateOf(0f) }
    var recording by remember { mutableStateOf(false) }
    var heard by remember { mutableStateOf<BrainClient.Live?>(null) }
    val mic = app.signsetu.ui.components.rememberPermission(Manifest.permission.RECORD_AUDIO, askNow = consented)
    val granted = mic.granted
    LaunchedEffect(Unit) { while (true) withFrameMillis { level = capture.level } }
    DisposableEffect(capture) { onDispose { capture.clear() } }

    fun finish() {
        if (!recording) return
        recording = false
        val wav = capture.stop()
        if (wav == null) status = R.string.voice_too_short else { status = R.string.voice_saved; onRecorded(wav) }
    }

    LaunchedEffect(recording) {
        if (!recording) return@LaunchedEffect
        val b = brain?.takeIf { it.can(BrainClient.Feature.ASR) }
        var id = b?.liveStart(lang)
        try {
            while (recording && capture.recording) {
                delay(LIVE_MS)
                val piece = capture.fresh()
                if (b != null && id != null) {
                    // A slow reply leaves more audio waiting; it goes in pieces the brain accepts.
                    var at = 0
                    do {
                        val part = piece.copyOfRange(at, minOf(piece.size, at + PIECE_BYTES))
                        b.liveFeed(id, part)?.let { heard = it }
                        at += part.size
                        part.fill(0)
                    } while (at < piece.size && b.can(BrainClient.Feature.ASR))
                    // A station that stopped answering (BrainClient counts the misses) is not asked again in this take.
                    if (!b.can(BrainClient.Feature.ASR)) id = null
                }
                piece.fill(0)
                if (heard?.end == true) break
            }
            finish() // the person stopped, or the take reached its length cap
        } finally {
            id?.let { withContext(NonCancellable) { b?.liveStop(it) } }
        }
    }

    ModalBottomSheet(onDismissRequest = { capture.clear(); onDismiss() }) {
        Column(Modifier.fillMaxWidth().padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(stringResource(R.string.voice_title), style = MaterialTheme.typography.titleMedium)
            if (!consented) {
                Text(stringResource(R.string.voice_consent), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    androidx.compose.material3.Button(onClick = onConsent) { Text(stringResource(R.string.voice_consent_yes)) }
                    androidx.compose.material3.OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                }
                return@Column
            }
            Text(stringResource(if (granted) R.string.voice_hold else R.string.voice_no_permission), style = MaterialTheme.typography.bodyMedium)
            if (!granted) app.signsetu.ui.components.PermissionButton(mic)
            LinearProgressIndicator(progress = { level }, modifier = Modifier.fillMaxWidth())
            val label = stringResource(if (recording) R.string.voice_stop else R.string.voice_them)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (recording) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth().height(96.dp).semantics { contentDescription = label }
                    .clickable(enabled = granted, role = Role.Button) {
                        if (recording) finish()
                        else if (capture.start()) { heard = null; status = R.string.voice_listening; recording = true }
                        else status = R.string.voice_failed
                    },
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(Spacing.md))
                }
            }
            heard?.takeIf { it.text.isNotBlank() || it.tentative.isNotBlank() }?.let { h ->
                Text(stringResource(R.string.voice_live_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val muted = MaterialTheme.colorScheme.onSurfaceVariant
                Text(
                    buildAnnotatedString {
                        append(h.text)
                        if (h.tentative.isNotBlank()) withStyle(SpanStyle(color = muted)) { append((if (h.text.isBlank()) "" else " ") + h.tentative) }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            status?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** How often the words heard so far are asked for while the person talks. */
private const val LIVE_MS = 400L
/** One second of 16 kHz 16-bit audio, half of what the brain takes in one piece. */
private const val PIECE_BYTES = 32_000
