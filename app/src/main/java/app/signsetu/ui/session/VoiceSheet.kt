package app.signsetu.ui.session

import android.Manifest
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
import app.signsetu.ui.theme.Spacing
import app.signsetu.voice.VoiceCapture

/** Hold while the person speaks: their voice is kept in the session record, encrypted and sealed like every answer,
 * and, when a station brain answers, its text is added as the app's reading. Recording needs the consent logged
 * first, for this session; the officer's own speech is never recorded (the keyboard's microphone types for them). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VoiceSheet(consented: Boolean, onConsent: () -> Unit, onRecorded: (ByteArray) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val capture = remember { VoiceCapture(context) }
    var status by remember { mutableStateOf<Int?>(null) }
    var level by remember { mutableFloatStateOf(0f) }
    val mic = app.signsetu.ui.components.rememberPermission(Manifest.permission.RECORD_AUDIO, askNow = consented)
    val granted = mic.granted
    LaunchedEffect(Unit) { while (true) withFrameMillis { level = capture.level } }
    DisposableEffect(capture) { onDispose { capture.clear() } }

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
            val label = stringResource(R.string.voice_them)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth().height(96.dp).semantics { contentDescription = label }
                    .pointerInput(granted) {
                        if (!granted) return@pointerInput
                        detectTapGestures(onPress = {
                            status = if (capture.start()) R.string.voice_listening else R.string.voice_failed
                            tryAwaitRelease()
                            val wav = capture.stop()
                            if (wav == null) status = R.string.voice_too_short else { status = R.string.voice_saved; onRecorded(wav) }
                        })
                    },
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(Spacing.md))
                }
            }
            status?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
