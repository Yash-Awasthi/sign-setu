package app.signsetu.ui.start

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.mutableStateListOf
import app.signsetu.net.BrainClient
import app.signsetu.ui.session.stateListSaver
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import app.signsetu.ui.session.clockDate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.signsetu.R
import app.signsetu.data.session.Session
import app.signsetu.data.session.SessionRepository
import app.signsetu.ui.components.GuardedAlertDialog
import app.signsetu.ui.components.OfficerAuth
import app.signsetu.ui.components.OfficerCard
import app.signsetu.ui.theme.Spacing
import kotlinx.coroutines.launch

private const val SAVED_SHOWN = 5

/** The two ways a saved session leaves this phone, as the history row offers them. */
class SavedActions(
    /** Writes Documents/SignSetu/<name>/ with the zip and the PDF; the folder and the PDF's passphrase, or null. */
    val download: suspend (Session) -> Pair<String, String>?,
    /** Sends the zip to the station; where the station saved it, or null. [progress] is 0 to 1. */
    val upload: suspend (Session, (Float) -> Unit) -> String?,
)

private val DownloadIcon = materialIcon("Download") {
    materialPath {
        moveTo(19f, 9f); horizontalLineToRelative(-4f); verticalLineTo(3f); horizontalLineTo(9f); verticalLineToRelative(6f); horizontalLineTo(5f); lineToRelative(7f, 7f); close()
        moveTo(5f, 18f); verticalLineToRelative(2f); horizontalLineToRelative(14f); verticalLineToRelative(-2f); close()
    }
}
private val UploadIcon = materialIcon("Upload") {
    materialPath {
        moveTo(9f, 16f); horizontalLineToRelative(6f); verticalLineToRelative(-6f); horizontalLineToRelative(4f); lineToRelative(-7f, -7f); lineToRelative(-7f, 7f); horizontalLineToRelative(4f); close()
        moveTo(5f, 18f); horizontalLineToRelative(14f); verticalLineToRelative(2f); horizontalLineTo(5f); close()
    }
}

/** What the info dialog shows: where a session was sent or saved, and the way to do it again. */
private data class Where(val session: Session, val station: Boolean, val place: String)

/** Finished sessions under the officer's names for them. A tap opens one. Each row saves it to this phone (the zip
 * and the PDF report) or sends it to the station; a small "i" marks a copy that still holds the whole record, and
 * tapping it shows where that copy is. A long press chooses sessions to delete. */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
internal fun SavedSessions(
    repo: SessionRepository,
    station: BrainClient.Station,
    onOpen: (String) -> Unit,
    actions: SavedActions,
) {
    // Null until Room answers, so the first pass does not clear a selection restored after rotation.
    val loaded by repo.saved().collectAsStateWithLifecycle<List<Session>?>(null)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val chosen = rememberSaveable(saver = stateListSaver()) { mutableStateListOf<String>() }
    var deleting by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var where by remember { mutableStateOf<Where?>(null) }
    var savedHere by remember { mutableStateOf<Pair<String, String>?>(null) }
    var all by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(loaded) { loaded?.let { list -> chosen.retainAll(list.map { it.id }.toSet()) } }
    val saved = loaded ?: return
    if (saved.isEmpty()) return
    val choosing = chosen.isNotEmpty()
    BackHandler(enabled = choosing) { chosen.clear() }

    fun upload(s: Session) {
        when {
            !station.online -> Toast.makeText(context, R.string.saved_send_offline, Toast.LENGTH_LONG).show()
            station.offered?.contains(BrainClient.Feature.ARCHIVE) != true -> Toast.makeText(context, R.string.saved_send_no_inbox, Toast.LENGTH_LONG).show()
            !station.uses(BrainClient.Feature.ARCHIVE) -> Toast.makeText(context, R.string.saved_send_off, Toast.LENGTH_LONG).show()
            else -> {
                busy = s.id
                progress = 0f
                scope.launch {
                    val path = actions.upload(s) { progress = it }
                    busy = null
                    if (path != null) where = Where(s, station = true, place = path)
                    else Toast.makeText(context, R.string.saved_send_one_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun download(s: Session) {
        busy = s.id
        progress = 0f
        scope.launch {
            val r = actions.download(s)
            busy = null
            if (r != null) savedHere = r else Toast.makeText(context, R.string.saved_download_failed, Toast.LENGTH_LONG).show()
        }
    }

    OfficerCard {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (choosing) stringResource(R.string.saved_chosen, chosen.size) else stringResource(R.string.saved_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (choosing) {
                IconButton(onClick = { deleting = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.saved_delete_chosen), tint = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = { chosen.clear() }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel)) }
            }
        }
        (if (all || choosing) saved else saved.take(SAVED_SHOWN)).forEach { s ->
            key(s.id) {
                val picked = s.id in chosen
                // A copy holds the whole record while the session's seal is the one it was made at.
                val sent = s.rootHash != null && s.stationHead == s.rootHash && s.stationFile != null
                val kept = s.rootHash != null && s.phoneHead == s.rootHash && s.phoneFolder != null
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        .combinedClickable(
                            onClick = { if (choosing) { if (picked) chosen.remove(s.id) else chosen.add(s.id) } else onOpen(s.id) },
                            onLongClick = { if (!picked) chosen.add(s.id) },
                            onLongClickLabel = stringResource(R.string.saved_choose),
                        )
                        .semantics { if (choosing) selected = picked },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (choosing) Checkbox(checked = picked, onCheckedChange = null, modifier = Modifier.padding(end = Spacing.sm))
                    Column(Modifier.weight(1f)) {
                        Text(s.title.orEmpty(), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(clockDate(s.endedAt ?: s.startedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!choosing) {
                        if (busy == s.id) {
                            androidx.compose.material3.CircularProgressIndicator(progress = { progress.coerceAtLeast(0.05f) }, modifier = Modifier.padding(12.dp).size(24.dp))
                        } else {
                            DoneButton(DownloadIcon, stringResource(R.string.saved_download), kept, enabled = busy == null) {
                                if (kept) where = Where(s, station = false, place = s.phoneFolder) else download(s)
                            }
                            // Shown only while the station is connected and takes conversations: nothing to fail otherwise.
                            if (station.uses(BrainClient.Feature.ARCHIVE)) DoneButton(UploadIcon, stringResource(R.string.saved_send), sent, enabled = busy == null) {
                                if (sent) where = Where(s, station = true, place = s.stationFile) else upload(s)
                            }
                        }
                    }
                }
            }
        }
        if (!choosing && saved.size > SAVED_SHOWN) {
            TextButton(onClick = { all = !all }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (all) stringResource(R.string.saved_fewer) else stringResource(R.string.saved_all, saved.size))
            }
        }
    }
    where?.let { w ->
        val name = w.place.substringAfterLast('\\').substringAfterLast('/')
        GuardedAlertDialog(
            onDismissRequest = { where = null },
            icon = { Icon(Icons.Filled.Info, contentDescription = null) },
            title = { Text(stringResource(if (w.station) R.string.saved_where_station else R.string.saved_where_phone)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(stringResource(R.string.saved_where_name), style = MaterialTheme.typography.labelMedium)
                    androidx.compose.foundation.text.selection.SelectionContainer { Text(name, style = MaterialTheme.typography.bodyMedium) }
                    Text(stringResource(R.string.saved_where_path), style = MaterialTheme.typography.labelMedium)
                    androidx.compose.foundation.text.selection.SelectionContainer { Text(w.place, style = MaterialTheme.typography.bodyMedium) }
                }
            },
            confirmButton = { TextButton(onClick = { where = null }) { Text(stringResource(android.R.string.ok)) } },
            dismissButton = if (w.station && !station.uses(BrainClient.Feature.ARCHIVE)) null else ({
                TextButton(onClick = { where = null; if (w.station) upload(w.session) else download(w.session) }) {
                    Text(stringResource(if (w.station) R.string.saved_send_again else R.string.saved_save_again))
                }
            }),
        )
    }
    savedHere?.let { (folder, pass) ->
        GuardedAlertDialog(
            onDismissRequest = { savedHere = null },
            icon = { Icon(Icons.Filled.Info, contentDescription = null) },
            title = { Text(stringResource(R.string.saved_where_phone)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    androidx.compose.foundation.text.selection.SelectionContainer { Text(folder, style = MaterialTheme.typography.bodyMedium) }
                    Text(stringResource(R.string.saved_passphrase), style = MaterialTheme.typography.labelMedium)
                    androidx.compose.foundation.text.selection.SelectionContainer { Text(pass, style = MaterialTheme.typography.titleMedium) }
                }
            },
            confirmButton = { TextButton(onClick = { savedHere = null }) { Text(stringResource(android.R.string.ok)) } },
        )
    }
    if (deleting) {
        val ids = chosen.toList()
        GuardedAlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(context.resources.getQuantityString(R.plurals.saved_delete_chosen_title, ids.size, ids.size)) },
            text = { Text(stringResource(R.string.saved_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    OfficerAuth.confirm(activity, { scope.launch { ids.forEach { repo.purge(it) }; chosen.clear() } })
                }) { Text(stringResource(R.string.saved_delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** A row's save or send button; [done] adds a small "i": a copy that still holds the whole record exists. */
@Composable
private fun DoneButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, done: Boolean, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        androidx.compose.material3.BadgedBox(badge = {
            if (done) androidx.compose.material3.Badge(containerColor = MaterialTheme.colorScheme.primary) {
                Text("i", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }) { Icon(icon, contentDescription = label) }
    }
}
