package app.signsetu.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import app.signsetu.R
import app.signsetu.data.session.SessionRepository
import app.signsetu.export.EvidenceIntegrity.SealCheck
import app.signsetu.export.checkReport
import kotlinx.coroutines.launch

/** Settings, Records: pick an exported report, type its passphrase, and see whether its seal holds. */
@Composable
internal fun ReportCheckItem(repo: SessionRepository?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var file by remember { mutableStateOf<Uri?>(null) }
    var pass by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<Int?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { file = it; pass = ""; result = null }
    Item(stringResource(R.string.check_report), stringResource(R.string.check_report_summary)) { pick.launch(arrayOf("application/pdf")) }
    val chosen = file ?: return
    AlertDialog(
        onDismissRequest = { file = null },
        title = { Text(stringResource(R.string.check_report)) },
        text = {
            val r = result
            if (r != null) Text(stringResource(r))
            else OutlinedTextField(pass, { pass = it.uppercase() }, singleLine = true, visualTransformation = PasswordVisualTransformation(), label = { Text(stringResource(R.string.check_report_pass)) })
        },
        confirmButton = {
            if (result == null) TextButton(onClick = {
                scope.launch {
                    result = when (checkReport(context, chosen, pass.trim(), repo)) {
                        null -> R.string.check_report_unopened
                        SealCheck.MATCHES_DEVICE -> R.string.check_report_matches
                        SealCheck.SIGNATURE_VALID -> R.string.check_report_valid
                        SealCheck.SIGNATURE_INVALID -> R.string.check_report_invalid
                        SealCheck.UNSIGNED -> R.string.check_report_unsigned
                        SealCheck.DIFFERS_FROM_DEVICE -> R.string.check_report_differs
                        SealCheck.RECORD_ALTERED -> R.string.check_report_altered
                        SealCheck.MALFORMED -> R.string.check_report_no_seal
                    }
                }
            }) { Text(stringResource(R.string.check_report_go)) }
            else TextButton(onClick = { file = null }) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = { if (result == null) TextButton(onClick = { file = null }) { Text(stringResource(android.R.string.cancel)) } },
    )
}
