package app.signsetu.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.runtime.produceState
import android.content.ClipData
import android.os.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

/** Settings, Local AI brain. One row, with a switch like the online assistant's: off, the phone works alone; on, it
 * connects to the station computer (pairing first when none is paired). While on, the station's services are listed
 * under it: those it has are on unless the officer keeps one on the phone; those it lacks are dimmed and off. */
@Composable
internal fun BrainItems(brain: BrainClient, off: Set<String>, onOff: (Set<String>) -> Unit, onLink: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var pairing by remember { mutableStateOf(false) }
    var linkAfterPair by remember { mutableStateOf(false) }
    val paired = remember(version) { brain.pairing() }
    val station by brain.state.collectAsStateWithLifecycle()
    val net = app.signsetu.ui.components.rememberLocalNetwork { ok ->
        if (ok) onLink(true) else Toast.makeText(context, R.string.station_needs_local, Toast.LENGTH_LONG).show()
    }
    val on = paired != null && station.on
    val summary = when {
        paired == null -> stringResource(R.string.brain_not_paired)
        !station.on -> stringResource(R.string.brain_link_off)
        station.online -> stringResource(R.string.brain_link_online, station.offered.orEmpty().count { it in Listed && it.key !in off }, Listed.size)
        else -> stringResource(R.string.brain_link_connecting)
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.brain_title)) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(summary)
                Text(stringResource(R.string.brain_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        colors = ItemColors,
        trailingContent = { Switch(checked = on, onCheckedChange = null) },
        modifier = Modifier.toggleable(on, role = Role.Switch) { want ->
            when {
                !want -> onLink(false)
                paired == null -> { linkAfterPair = true; pairing = true }
                !net.granted -> net.request()
                else -> onLink(true)
            }
        },
    )
    if (on) Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(bottom = 6.dp)) {
        Listed.forEach { f ->
            val has = station.online && station.offered?.contains(f) == true
            val sent = has && f.key !in off
            ServiceRow(stringResource(featureName(f)), has, sent) {
                when {
                    has -> onOff(if (sent) off + f.key else off - f.key)
                    !station.online -> Toast.makeText(context, R.string.brain_feature_connect_first, Toast.LENGTH_SHORT).show()
                    else -> Toast.makeText(context, R.string.brain_feature_missing, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    if (paired != null) {
        Item(stringResource(R.string.brain_paired_with), label(paired)) { pairing = true }
        Item(stringResource(R.string.brain_forget), "", destructive = true) { onLink(false); brain.forget(); version++ }
    }
    PhoneIdItem()
    if (pairing) {
        var url by remember { mutableStateOf(paired?.ticket ?: paired?.url ?: "") }
        var token by remember { mutableStateOf("") }
        var pin by remember { mutableStateOf(paired?.pin ?: "") }
        var failed by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { pairing = false; linkAfterPair = false },
            title = { Text(stringResource(R.string.brain_pair)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.brain_pair_help))
                    OutlinedTextField(url, { url = it.trim() }, singleLine = true, label = { Text(stringResource(R.string.brain_url)) })
                    OutlinedTextField(token, { token = it.trim() }, singleLine = true, visualTransformation = PasswordVisualTransformation(), label = { Text(stringResource(R.string.brain_token)) })
                    // A ticket names the brain's key itself; only a LAN address needs the certificate pin.
                    if (url.startsWith("https://")) OutlinedTextField(pin, { pin = it.trim() }, singleLine = true, label = { Text(stringResource(R.string.brain_pin)) })
                    if (failed) Text(stringResource(R.string.brain_pair_failed))
                    if (failed && url.startsWith("endpoint")) app.signsetu.net.IrohBrain.lastError?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                val pair = {
                    busy = true
                    scope.launch {
                        val ok = brain.pair(url, token, pin)
                        busy = false
                        failed = !ok
                        if (ok) { pairing = false; version++; if (linkAfterPair) { linkAfterPair = false; onLink(true) } }
                    }
                    Unit
                }
                // On Android 17 the station on this Wi-Fi is unreachable until local network access is allowed.
                val net = app.signsetu.ui.components.rememberLocalNetwork { ok -> if (ok) pair() else failed = true }
                TextButton(enabled = !busy, onClick = { if (net.granted) pair() else net.request() }) { Text(stringResource(R.string.brain_pair_go)) }
            },
            dismissButton = { TextButton(onClick = { pairing = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

/** This phone's iroh endpoint ID, which a relay's `[access]` allowlist names (docs/16-relay.md); a tap copies it. */
@Composable
private fun PhoneIdItem() {
    val id by produceState<String?>(null) { value = app.signsetu.net.IrohBrain.phoneId() }
    val shown = id ?: return
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val label = stringResource(R.string.brainui_phone_id)
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(shown, fontFamily = FontFamily.Monospace)
                Text(stringResource(R.string.brainui_phone_id_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        colors = ItemColors,
        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.brainui_phone_id_copy)) {
            scope.launch {
                clipboard.setClipEntry(ClipData.newPlainText(label, shown).toClipEntry())
                // Android 13 and later confirm a copy themselves.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, R.string.brainui_phone_id_copied, Toast.LENGTH_SHORT).show()
            }
        },
    )
}

/** One of the station's services, nested under the Station computer row: indented, compact, with
 * its text dimmed (never the row: the card would show through as a band) when the station lacks it. */
@Composable
private fun ServiceRow(name: String, has: Boolean, sent: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).toggleable(value = sent, role = Role.Switch) { onClick() }
            .padding(start = 40.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).alpha(if (has) 1f else 0.38f))
        Switch(checked = sent, onCheckedChange = null, enabled = has, modifier = Modifier.scale(0.8f))
    }
}

/** The services listed with a switch. Sign order for typed text is not: the phone's "Sign in ISL order" decides
 * whether typed text is reordered at all, and the station's order is used for it whenever the station has one.
 * Planning auto mode's next item follows the language model's switch. */
private val Listed = BrainClient.Feature.entries - BrainClient.Feature.GLOSS - BrainClient.Feature.PLAN

private fun featureName(f: BrainClient.Feature) = when (f) {
    BrainClient.Feature.ASR -> R.string.brain_feature_asr
    BrainClient.Feature.ISL -> R.string.brain_feature_isl
    BrainClient.Feature.LLM -> R.string.brain_feature_llm
    BrainClient.Feature.GLOSS -> R.string.brain_title
    BrainClient.Feature.ARCHIVE -> R.string.brain_feature_archive
    BrainClient.Feature.PLAN -> R.string.brain_feature_llm
}

@Composable
private fun label(p: BrainClient.Pairing): String = if (p.viaIroh) stringResource(R.string.brain_via_iroh, p.ticket!!.take(12)) else p.url
