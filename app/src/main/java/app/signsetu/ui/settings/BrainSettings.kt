package app.signsetu.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.signsetu.R
import app.signsetu.net.BrainClient
import kotlinx.coroutines.launch

/** Settings, Local AI brain: pair with the station computer by its address, token and certificate pin, see that it
 * answers and how fast, or forget it. */
@Composable
internal fun BrainItems(brain: BrainClient) {
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    var pairing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<BrainClient.Status?>(null) }
    var checked by remember { mutableStateOf(false) }
    val paired = remember(version) { brain.pairing() }
    LaunchedEffect(version) { checked = false; status = if (paired != null) brain.status() else null; checked = true }
    val summary = when {
        paired == null -> stringResource(R.string.brain_not_paired)
        !checked -> stringResource(R.string.brain_checking)
        status == null -> stringResource(R.string.brain_unreachable, paired.url)
        else -> stringResource(R.string.brain_ok, paired.url, status!!.ms.toInt(), stringResource(if (status!!.llm) R.string.brain_llm_on else R.string.brain_llm_off))
    }
    Item(stringResource(R.string.brain_title), summary, note = stringResource(R.string.brain_note)) { pairing = true }
    if (paired != null) Item(stringResource(R.string.brain_forget), "", destructive = true) { brain.forget(); version++ }
    if (pairing) {
        var url by remember { mutableStateOf(paired?.url ?: "https://") }
        var token by remember { mutableStateOf("") }
        var pin by remember { mutableStateOf(paired?.pin ?: "") }
        var failed by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { pairing = false },
            title = { Text(stringResource(R.string.brain_pair)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.brain_pair_help))
                    OutlinedTextField(url, { url = it.trim() }, singleLine = true, label = { Text(stringResource(R.string.brain_url)) })
                    OutlinedTextField(token, { token = it.trim() }, singleLine = true, visualTransformation = PasswordVisualTransformation(), label = { Text(stringResource(R.string.brain_token)) })
                    OutlinedTextField(pin, { pin = it.trim() }, singleLine = true, label = { Text(stringResource(R.string.brain_pin)) })
                    if (failed) Text(stringResource(R.string.brain_pair_failed))
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        val ok = brain.pair(url, token, pin)
                        busy = false
                        failed = !ok
                        if (ok) { pairing = false; version++ }
                    }
                }) { Text(stringResource(R.string.brain_pair_go)) }
            },
            dismissButton = { TextButton(onClick = { pairing = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}
