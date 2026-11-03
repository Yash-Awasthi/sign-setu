package app.signsetu.ui

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import android.content.ClipData
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.signsetu.ui.theme.Spacing
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import app.signsetu.data.settings.AppSettings
import app.signsetu.data.settings.interpreterUrl
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.signsetu.R
import app.signsetu.ui.components.GuardedAlertDialog

private const val SANDES_PACKAGE = "in.nic.gimkerala"

/** the only text the Sandes handoff puts on the clipboard is the interpreter's contact. */
fun sanitizeSandesHandoffClipboard(contact: String): String = contact.trim()

@Composable
internal fun SandesHandoffDialog(
    contact: String,
    onLaunch: () -> Unit,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.sandes_contact_copied)

    GuardedAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Call, contentDescription = null) },
        title = { Text(stringResource(R.string.sandes_handoff_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    stringResource(R.string.sandes_handoff_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(Spacing.md)) {
                        Text(
                            stringResource(R.string.sandes_contact_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            contact,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedButton(
                    onClick = {
                        // Only the configured contact reaches the clipboard, never session or transcript data.
                        scope.launch {
                            clipboard.setClipEntry(ClipData.newPlainText(null, sanitizeSandesHandoffClipboard(contact)).toClipEntry())
                            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                        }
                    },
                ) {
                    Text(stringResource(R.string.sandes_copy_contact))
                }
                Button(onClick = onLaunch) {
                    Text(stringResource(R.string.sandes_launch))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

internal fun launchSandes(activity: Activity) {
    val intent = activity.packageManager.getLaunchIntentForPackage(SANDES_PACKAGE)
    if (intent != null) unpinFor(activity)
    val opened = intent != null && runCatching { activity.startActivity(intent) }.isSuccess
    if (!opened) {
        repinAfterFailedOpen(activity)
        val name = activity.getString(R.string.settings_sandes)
        Toast.makeText(activity, activity.getString(R.string.call_missing, name), Toast.LENGTH_LONG).show()
    }
}

/** Opens WhatsApp at the interpreter's number; no session data goes with it. Sandes goes through its dialog. */
internal fun callInterpreter(activity: Activity, settings: AppSettings) {
    val name = activity.getString(R.string.settings_whatsapp)
    val intent = interpreterUrl(settings.interpreterContact)?.let { Intent(Intent.ACTION_VIEW, it.toUri()) }
    if (intent != null) unpinFor(activity)
    val opened = intent != null && runCatching { activity.startActivity(intent) }.isSuccess
    if (!opened) {
        repinAfterFailedOpen(activity)
        Toast.makeText(activity, activity.getString(R.string.call_missing, name), Toast.LENGTH_LONG).show()
    }
}
