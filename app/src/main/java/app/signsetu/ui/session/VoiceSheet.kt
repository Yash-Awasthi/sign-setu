package app.signsetu.ui.session

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.signsetu.R
import app.signsetu.net.BrainClient
import app.signsetu.ui.theme.Spacing
import app.signsetu.voice.VoiceCapture
import kotlinx.coroutines.launch

/** Who was speaking when a take was recorded. */
enum class Speaker { OFFICER, SUBJECT }

/** Hold to speak: the officer's words go to the text box to check and send; the person's words go to the officer as
 * the app's reading, never as their answer. Audio stays in memory for the one recognition and is then wiped. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VoiceSheet(brain: BrainClient, officerLang: String, subjectLang: String, onHeard: (Speaker, BrainClient.Heard) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val capture = remember { VoiceCapture(context) }
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var level by remember { mutableFloatStateOf(0f) }
    var granted by remember { mutableStateOf(capture.permitted()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.RECORD_AUDIO) }
    LaunchedEffect(Unit) { while (true) withFrameMillis { level = capture.level } }
    DisposableEffect(capture) { onDispose { capture.clear() } }

    fun finish(who: Speaker) {
        val wav = capture.stop() ?: run { status = R.string.voice_too_short; return }
        busy = true
        status = R.string.voice_working
        scope.launch {
            val heard = brain.recognise(wav, if (who == Speaker.OFFICER) officerLang else subjectLang)
            wav.fill(0)
            busy = false
            if (heard == null) status = R.string.voice_failed else { status = null; onHeard(who, heard) }
        }
    }

    ModalBottomSheet(onDismissRequest = { capture.clear(); onDismiss() }) {
        Column(Modifier.fillMaxWidth().padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(stringResource(R.string.voice_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(if (granted) R.string.voice_hold else R.string.voice_no_permission), style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(progress = { level }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                for (who in Speaker.entries) {
                    val label = stringResource(if (who == Speaker.OFFICER) R.string.voice_me else R.string.voice_them)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (who == Speaker.OFFICER) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.weight(1f).height(96.dp).semantics { contentDescription = label }
                            .pointerInput(granted, busy) {
                                if (!granted || busy) return@pointerInput
                                detectTapGestures(onPress = {
                                    status = if (capture.start()) R.string.voice_listening else R.string.voice_failed
                                    tryAwaitRelease()
                                    finish(who)
                                })
                            },
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(Spacing.md))
                        }
                    }
                }
            }
            status?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
