package app.signsetu.ui.settings

import androidx.compose.runtime.remember
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import app.signsetu.assistant.ModelStore
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.signsetu.R
import kotlinx.coroutines.launch

/** The words one model import uses: its state, the import and the removal. */
internal enum class ModelLabels(
    /** Headline once installed, and while not: the offline assistant's row is its import action until then. */
    @StringRes val title: Int, @StringRes val emptyTitle: Int,
    @StringRes val ready: Int, @StringRes val missing: Int, @StringRes val memory: Int, @StringRes val unsupported: Int,
    @StringRes val importing: Int, @StringRes val bad: Int, @StringRes val remove: Int,
) {
    ASSISTANT(
        R.string.settings_assistant, R.string.settings_assistant_import,
        R.string.settings_assistant_ready, R.string.settings_assistant_missing, R.string.settings_assistant_memory, R.string.settings_assistant_unsupported,
        R.string.settings_assistant_importing, R.string.settings_assistant_bad, R.string.settings_assistant_remove,
    ),
    SENTENCE(
        R.string.settings_sentence_helper, R.string.settings_sentence_helper,
        R.string.settings_sentence_ready, R.string.settings_sentence_missing, R.string.settings_assistant_memory, R.string.settings_assistant_unsupported,
        R.string.settings_assistant_importing, R.string.settings_assistant_bad, R.string.settings_sentence_remove,
    ),
}

/** One model file's state with its own import and removal. The department hands out the file; it is copied in and
 * checked against the store's known hashes, never downloaded. */
@Composable
internal fun ModelImport(models: ModelStore, labels: ModelLabels) {
    val scope = rememberCoroutineScope()
    var modelState by remember { mutableStateOf(models.state()) }
    var importing by remember { mutableStateOf(false) }
    var badModel by remember { mutableStateOf(false) }
    val pickModel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importing = true
            badModel = false
            scope.launch {
                badModel = !models.import(uri)
                modelState = models.state()
                importing = false
            }
        }
    }
    val missing = modelState == ModelStore.State.NOT_INSTALLED
    val status = when {
        importing -> labels.importing
        badModel -> labels.bad
        else -> when (modelState) {
            ModelStore.State.READY -> labels.ready
            ModelStore.State.NOT_INSTALLED -> labels.missing
            ModelStore.State.TOO_LITTLE_MEMORY -> labels.memory
            ModelStore.State.UNSUPPORTED -> labels.unsupported
        }
    }
    val canImport = !importing && missing
    ListItem(
        headlineContent = { Text(stringResource(if (missing) labels.emptyTitle else labels.title)) },
        supportingContent = {
            Text(
                stringResource(status),
                style = MaterialTheme.typography.bodySmall,
                color = if (badModel) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = ItemColors,
        modifier = if (canImport) Modifier.clickable(role = Role.Button) { pickModel.launch(arrayOf("*/*")) } else Modifier,
    )
    if (!importing && modelState == ModelStore.State.READY) {
        Item(stringResource(labels.remove), "", destructive = true) {
            scope.launch { models.remove(); modelState = models.state() }
        }
    }
}
