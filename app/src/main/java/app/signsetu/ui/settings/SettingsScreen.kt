package app.signsetu.ui.settings

import android.app.Activity
import app.signsetu.data.settings.OfficerLocale
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.signsetu.BuildConfig
import app.signsetu.R
import app.signsetu.data.settings.AppSettings
import app.signsetu.data.settings.InterpreterApp
import app.signsetu.data.settings.SettingsStore
import app.signsetu.data.settings.whatsappNumber
import app.signsetu.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import app.signsetu.ui.components.GuardedAlertDialog

private enum class Dialog { APP, CONTACT, RETENTION, CLEAR, LICENCES, LANGUAGE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(store: SettingsStore, onBack: () -> Unit, onClearAll: () -> Unit) {
    val loaded by store.settings.collectAsStateWithLifecycle(initialValue = null)
    val s = loaded ?: return
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var dialog by rememberSaveable { mutableStateOf<Dialog?>(null) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val notSet = stringResource(R.string.settings_not_set)

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            Header(R.string.settings_interpreter)
            Item(stringResource(R.string.settings_interpreter_app), stringResource(appName(s.interpreterApp))) { dialog = Dialog.APP }
            if (s.interpreterApp != InterpreterApp.NONE) {
                Item(stringResource(R.string.settings_interpreter_contact), s.interpreterContact.ifBlank { notSet }) { dialog = Dialog.CONTACT }
            }

            HorizontalDivider(Modifier.padding(vertical = Spacing.sm))
            Header(R.string.settings_stage)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_camera)) },
                supportingContent = { Text(stringResource(R.string.settings_camera_summary)) },
                trailingContent = { Switch(checked = s.cameraEnabled, onCheckedChange = null) },
                modifier = Modifier.toggleable(s.cameraEnabled, role = Role.Switch) { on -> scope.launch { store.setCameraEnabled(on) } },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_large_captions)) },
                supportingContent = { Text(stringResource(R.string.settings_large_captions_summary)) },
                trailingContent = { Switch(checked = s.largeCaptions, onCheckedChange = null) },
                modifier = Modifier.toggleable(s.largeCaptions, role = Role.Switch) { on -> scope.launch { store.setLargeCaptions(on) } },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_slow_gestures)) },
                supportingContent = { Text(stringResource(R.string.settings_slow_gestures_summary)) },
                trailingContent = { Switch(checked = s.slowGestures, onCheckedChange = null) },
                modifier = Modifier.toggleable(s.slowGestures, role = Role.Switch) { on -> scope.launch { store.setSlowGestures(on) } },
            )

            HorizontalDivider(Modifier.padding(vertical = Spacing.sm))
            Header(R.string.settings_records)
            Item(
                stringResource(R.string.settings_retention),
                pluralStringResource(R.plurals.settings_hours, s.retentionHours, s.retentionHours),
                note = stringResource(R.string.settings_retention_note),
            ) { dialog = Dialog.RETENTION }
            Item(stringResource(R.string.settings_clear), stringResource(R.string.settings_clear_summary), destructive = true) {
                dialog = Dialog.CLEAR
            }

            HorizontalDivider(Modifier.padding(vertical = Spacing.sm))
            Header(R.string.settings_about)
            Item(stringResource(R.string.settings_app_language), stringResource(languageName(OfficerLocale.get(context)))) { dialog = Dialog.LANGUAGE }
            Item(stringResource(R.string.settings_licences), stringResource(R.string.settings_licences_summary)) { dialog = Dialog.LICENCES }
            ListItem(
                headlineContent = { Text(stringResource(R.string.app_name)) },
                supportingContent = {
                    val built = LocalDate.ofEpochDay(BuildConfig.BUILD_EPOCH_DAY).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, built))
                        // FR-52: the notice appears in About as well as on first run.
                        Text(stringResource(R.string.notice_aid), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
            )
        }
    }

    when (dialog) {
        Dialog.APP -> ChoiceDialog(
            title = stringResource(R.string.settings_interpreter_app),
            options = InterpreterApp.entries,
            selected = s.interpreterApp,
            label = { stringResource(appName(it)) },
            onPick = { app ->
                // A WhatsApp number and a Sandes contact name are not interchangeable.
                val contact = if (app == s.interpreterApp) s.interpreterContact else ""
                scope.launch { store.setInterpreter(app, contact) }
                dialog = if (app == InterpreterApp.NONE || contact.isNotBlank()) null else Dialog.CONTACT
            },
            onDismiss = { dialog = null },
        )
        Dialog.CONTACT -> ContactDialog(
            app = s.interpreterApp,
            initial = s.interpreterContact,
            onSave = { scope.launch { store.setInterpreter(s.interpreterApp, it) }; dialog = null },
            onDismiss = { dialog = null },
        )
        Dialog.RETENTION -> ChoiceDialog(
            title = stringResource(R.string.settings_retention),
            options = AppSettings.RETENTION_CHOICES,
            selected = s.retentionHours,
            label = { pluralStringResource(R.plurals.settings_hours, it, it) },
            onPick = { scope.launch { store.setRetentionHours(it) }; dialog = null },
            onDismiss = { dialog = null },
        )
        Dialog.CLEAR -> GuardedAlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.settings_clear_title)) },
            text = { Text(stringResource(R.string.settings_clear_body)) },
            confirmButton = {
                TextButton(onClick = { dialog = null; onClearAll() }) {
                    Text(stringResource(R.string.settings_clear_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } },
        )
        Dialog.LICENCES -> LicencesDialog(onDismiss = { dialog = null })
        Dialog.LANGUAGE -> ChoiceDialog(
            title = stringResource(R.string.settings_app_language),
            options = OfficerLocale.CHOICES,
            selected = OfficerLocale.get(context),
            label = { stringResource(languageName(it)) },
            onPick = { tag ->
                dialog = null
                OfficerLocale.set(context, tag)
                (context as? Activity)?.recreate()
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@StringRes
fun languageName(tag: String) = when (tag) {
    "en" -> R.string.lang_english
    "hi" -> R.string.lang_hindi
    else -> R.string.settings_lang_system
}

@StringRes
private fun appName(app: InterpreterApp) = when (app) {
    InterpreterApp.NONE -> R.string.settings_not_set
    InterpreterApp.WHATSAPP -> R.string.settings_whatsapp
    InterpreterApp.SANDES -> R.string.settings_sandes
}

@Composable
private fun Header(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg, bottom = Spacing.xs)
            .semantics { heading() },
    )
}

@Composable
private fun Item(title: String, summary: String, note: String? = null, destructive: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    ListItem(
        headlineContent = { Text(title, color = if (destructive) colors.error else colors.onSurface) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(summary)
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
            }
        },
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    GuardedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .selectable(selected = option == selected, onClick = { onPick(option) }, role = Role.RadioButton),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(label(option), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Spacing.lg))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ContactDialog(app: InterpreterApp, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial) }
    val phone = app == InterpreterApp.WHATSAPP
    val valid = if (phone) whatsappNumber(value) != null else value.isNotBlank()
    GuardedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_interpreter_contact)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = {
                    Text(stringResource(if (phone) R.string.settings_contact_whatsapp_hint else R.string.settings_contact_sandes_hint))
                },
                isError = phone && value.isNotBlank() && !valid,
                supportingText = if (phone && value.isNotBlank() && !valid) {
                    { Text(stringResource(R.string.settings_contact_invalid)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = if (phone) KeyboardType.Phone else KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(value.trim()) }, enabled = valid) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Names and licences stay in English: they are legal identifiers, not interface text. */
private val LICENCES = listOf(
    "OpenMoji 17.0.0" to "CC BY-SA 4.0. HfG Schwäbisch Gmünd, openmoji.org",
    "Mulberry Symbols" to "CC BY-SA 4.0. Steve Lee, mulberrysymbols.org",
    "Indic Parler-TTS (recorded prompts)" to "Apache 2.0. AI4Bharat, IIT Madras",
    "SQLCipher for Android" to "BSD 3-Clause. Zetetic LLC",
    "PdfBox-Android" to "Apache 2.0. Tom Roush; Apache PDFBox",
    "AndroidX, Jetpack Compose, Media3" to "Apache 2.0. The Android Open Source Project",
    "Kotlin, kotlinx.coroutines, kotlinx.serialization" to "Apache 2.0. JetBrains",
)

@Composable
private fun LicencesDialog(onDismiss: () -> Unit) {
    GuardedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_licences)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                LICENCES.forEach { (name, licence) ->
                    Column {
                        Text(name, style = MaterialTheme.typography.titleSmall)
                        Text(licence, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    "Symbols are converted to bitmaps for this app; the converted symbol set is shared under CC BY-SA 4.0.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
