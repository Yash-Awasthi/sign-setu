package app.signsetu.ui.pair

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.signsetu.AppContainer
import app.signsetu.R
import app.signsetu.pair.Abort
import app.signsetu.pair.LinkRole
import app.signsetu.pair.LinkState
import app.signsetu.pair.Peer
import app.signsetu.pair.StageLink
import app.signsetu.pair.StageTransports
import app.signsetu.ui.session.OfficerSheet
import app.signsetu.ui.session.SheetTitle
import app.signsetu.ui.theme.Spacing
import kotlinx.coroutines.flow.flowOf

private fun bluetoothOn(context: Context) = context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true

private fun granted(context: Context, perms: Array<String>) =
    perms.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

/** Officer side: pick a device paired in system settings, then type the code it shows (D-26). */
@Composable
fun SecondScreenSheet(container: AppContainer, onDismiss: () -> Unit) {
    val transport = container.pairTransport ?: return
    val context = LocalContext.current
    val perms = remember { StageTransports.permissions(context) }
    var allowed by remember { mutableStateOf(granted(context, perms)) }
    var on by remember { mutableStateOf(bluetoothOn(context)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { allowed = granted(context, perms) }
    val enable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { on = bluetoothOn(context) }
    LaunchedEffect(Unit) { if (!allowed) ask.launch(perms) }
    val link by container.link.collectAsStateWithLifecycle()
    val officer = link?.takeIf { it.role == LinkRole.OFFICER }
    val state = officer?.state?.collectAsStateWithLifecycle()?.value
    LaunchedEffect(state) { if (state == LinkState.Live) onDismiss() }
    val peers by remember(allowed, on) { if (allowed && on) transport.peers() else flowOf(emptyList()) }.collectAsStateWithLifecycle(emptyList())

    OfficerSheet(onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg).navigationBarsPadding().padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SheetTitle(stringResource(R.string.second_screen_title))
            Text(stringResource(R.string.second_screen_intro), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.notice_aid), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.second_screen_dept_only), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                state == LinkState.Connecting -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.second_screen_connecting))
                }
                state is LinkState.Code -> if (state.check != null) {
                    CheckValue(state.check)
                    Text(stringResource(R.string.second_screen_check), style = MaterialTheme.typography.bodyMedium)
                } else {
                    CodeEntry(officer)
                }
                else -> {
                    (state as? LinkState.Failed)?.let { f ->
                        val reason = when (f.abort) {
                            null -> R.string.second_screen_unreachable
                            Abort.VERSION -> R.string.second_screen_version
                            else -> R.string.second_screen_failed
                        }
                        Text(stringResource(reason), color = MaterialTheme.colorScheme.error)
                    }
                    when {
                        !allowed -> {
                            Text(stringResource(R.string.second_screen_permission))
                            Button(onClick = { ask.launch(perms) }) { Text(stringResource(R.string.ok)) }
                        }
                        !on -> Button(onClick = { enable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }) { Text(stringResource(R.string.second_screen_open_bluetooth)) }
                        peers.isEmpty() -> Text(stringResource(R.string.second_screen_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> peers.forEach { p -> PeerRow(p) { container.pairWith(p) } }
                    }
                    Text(stringResource(R.string.second_screen_not_listed), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) { Text(stringResource(R.string.second_screen_open_bluetooth)) }
                }
            }
        }
    }
}

@Composable
private fun PeerRow(peer: Peer, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        ListItem(headlineContent = { Text(peer.label) })
    }
}

/** The officer never sees the code here: typing what the other screen shows proves it is the device in front of them. */
@Composable
private fun CodeEntry(link: StageLink) {
    var typed by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    Text(stringResource(R.string.second_screen_enter_code), style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = typed,
        onValueChange = { v ->
            typed = v.filter(Char::isDigit).take(6)
            if (typed.length == 6) {
                val entry = typed
                link.submitCode(entry) { ok -> if (!ok) { wrong = true; typed = "" } }
            } else {
                wrong = false
            }
        },
        singleLine = true,
        isError = wrong,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
    if (wrong) Text(stringResource(R.string.second_screen_wrong_code), color = MaterialTheme.colorScheme.error)
}
