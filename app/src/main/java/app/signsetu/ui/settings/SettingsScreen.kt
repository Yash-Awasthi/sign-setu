package app.signsetu.ui.settings

import androidx.compose.runtime.remember
import app.signsetu.ui.components.OfficerAuth
import app.signsetu.assistant.ModelStore
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import android.app.Activity
import app.signsetu.data.settings.OfficerLocale
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.signsetu.BuildConfig
import app.signsetu.R
import androidx.compose.ui.text.input.PasswordVisualTransformation
import app.signsetu.assistant.AiProbe
import app.signsetu.net.UrlHttp
import app.signsetu.ui.components.LocalOnlineAssistant
import app.signsetu.data.settings.AppSettings
import app.signsetu.data.settings.InterpreterApp
import app.signsetu.data.settings.SettingsStore
import app.signsetu.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import app.signsetu.ui.components.GuardedAlertDialog

private enum class Dialog { APP, CONTACT, RETENTION, CLEAR, LICENCES, LANGUAGE, CLOUD, AIKEY, DEVELOPER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(store: SettingsStore, models: ModelStore, sentenceModel: ModelStore, onBack: () -> Unit, onClearAll: () -> Unit, onPractice: () -> Unit, onSignToType: (() -> Unit)? = null, onAppearance: () -> Unit, onCallInterpreter: (() -> Unit)?, onRecordSigns: (() -> Unit)? = null, repo: app.signsetu.data.session.SessionRepository? = null, onHelp: (() -> Unit)? = null) {
    val loaded by store.settings.collectAsStateWithLifecycle(initialValue = null)
    val s = loaded ?: return
    val scope = rememberCoroutineScope()
    val activity = androidx.activity.compose.LocalActivity.current
    val context = LocalContext.current
    var dialog by rememberSaveable { mutableStateOf<Dialog?>(null) }
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    val notSet = stringResource(R.string.settings_not_set)
    val cloud = LocalOnlineAssistant.current
    var enrolled by remember { mutableStateOf(cloud.enrolled) }
    // The saved developer service changes what the assistant is; the screen reads it again after a save or a removal.
    var devVersion by remember { mutableIntStateOf(0) }
    val available = remember(devVersion, s.devAiUrl) { cloud.available }
    val viaGateway = remember(devVersion) { cloud.viaGateway }
    val probe = remember { AiProbe(UrlHttp()) }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall) },
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
            onHelp?.let { help ->
                Group(R.string.shell_help_group) {
                    Item(stringResource(R.string.shell_help_title), stringResource(R.string.shell_help_summary), onClick = help)
                }
            }
            Group(R.string.practice_title) {
                Item(stringResource(R.string.practice_title), stringResource(R.string.practice_settings_summary), onClick = onPractice)
                onSignToType?.let { Item(stringResource(R.string.sign_to_type), stringResource(R.string.sign_to_type_summary), onClick = it) }
                onRecordSigns?.let { Item(stringResource(R.string.record_title), stringResource(R.string.record_settings_summary), onClick = it) }
            }
            Group(R.string.settings_interpreter) {
                Item(stringResource(R.string.settings_interpreter_app), stringResource(appName(s.interpreterApp))) { dialog = Dialog.APP }
                if (s.interpreterApp != InterpreterApp.NONE) {
                    Item(stringResource(R.string.settings_interpreter_contact), s.interpreterContact.ifBlank { notSet }) { dialog = Dialog.CONTACT }
                    onCallInterpreter?.let { call -> Item(stringResource(R.string.settings_interpreter_test), stringResource(R.string.settings_interpreter_test_summary), onClick = call) }
                }
            }
            Group(R.string.settings_security) {
                // Turning a protection off needs the officer, so a subject holding the phone cannot.
                val guard = { on: Boolean, set: suspend (Boolean) -> Any -> if (on) scope.launch { set(true) } else OfficerAuth.confirm(activity, { scope.launch { set(false) } }) }
                SwitchItem(R.string.settings_pin_app, R.string.settings_pin_app_summary, s.pinApp) { guard(it, store::setPinApp) }
                SwitchItem(R.string.settings_block_screenshots, R.string.settings_block_screenshots_summary, s.blockScreenshots) { guard(it, store::setBlockScreenshots) }
                SwitchItem(R.string.settings_app_lock, R.string.settings_app_lock_summary, s.appLock) { guard(it, store::setAppLock) }
            }
            Group(R.string.settings_stage) {
                SwitchItem(R.string.settings_camera, R.string.settings_camera_summary, s.cameraEnabled) { on -> scope.launch { store.setCameraEnabled(on) } }
                SpeedItem(s.gestureSpeed) { v -> scope.launch { store.setGestureSpeed(v) } }
                SwitchItem(R.string.settings_isl_reorder, if (s.islReorder) R.string.settings_isl_reorder_on else R.string.settings_isl_reorder_off, s.islReorder) { on -> scope.launch { store.setIslReorder(on) } }
                // On is the 3D character, off the drawn one, lighter and used by itself where 3D cannot run.
                val threeD = s.avatarStyle != "FLAT"
                SwitchItem(R.string.settings_character_3d, if (threeD) R.string.settings_character_3d_on else R.string.settings_character_3d_off, threeD) { on -> scope.launch { store.setAvatarStyle(if (on) "AUTO" else "FLAT") } }
            }
            Group(R.string.settings_online) {
                if (available) ListItem(
                    headlineContent = { Text(stringResource(if (viaGateway) R.string.settings_online_assistant else R.string.settings_online_assistant_direct)) },
                    supportingContent = { Text(stringResource(if (viaGateway) R.string.settings_online_assistant_summary else R.string.settings_online_assistant_summary_direct)) },
                    colors = ItemColors,
                    trailingContent = { Switch(checked = s.onlineAssistant, onCheckedChange = null) },
                    modifier = Modifier.toggleable(s.onlineAssistant, role = Role.Switch) { on ->
                        if (on) dialog = Dialog.CLOUD else scope.launch { store.setOnlineAssistant(false) }
                    },
                )
                if (s.onlineAssistant) {
                    if (viaGateway) Item(stringResource(R.string.settings_online_assistant_key), if (enrolled) stringResource(R.string.settings_online_assistant_enrolled) else notSet) { dialog = Dialog.AIKEY }
                    else if (cloud.hasGateway) Item(stringResource(R.string.cloud_notice_custom), "${s.devAiModel} · ${s.devAiUrl.removePrefix("https://")}") { dialog = Dialog.DEVELOPER }
                }
                if (!cloud.hasGateway) Item(
                    stringResource(R.string.settings_developer_mode),
                    if (s.devAiUrl.isBlank()) stringResource(R.string.settings_developer_summary) else "${s.devAiModel} · ${s.devAiUrl.removePrefix("https://")}",
                ) { dialog = Dialog.DEVELOPER }
                app.signsetu.ui.components.LocalBrain.current?.let { b ->
                    BrainItems(b, s.stationOff, onOff = { off -> scope.launch { store.setStationOff(off) } }, onLink = { on -> b.setOn(on); scope.launch { store.setStationLink(on) } })
                }
            }
            Group(R.string.settings_offline) {
                ModelImport(models, ModelLabels.ASSISTANT)
                ModelImport(sentenceModel, ModelLabels.SENTENCE)
            }
            Group(R.string.settings_records) {
                Item(
                    stringResource(R.string.settings_retention),
                    if (s.retentionHours == 0) stringResource(R.string.settings_keep) else pluralStringResource(R.plurals.settings_hours, s.retentionHours, s.retentionHours),
                    note = stringResource(R.string.settings_retention_note),
                ) { dialog = Dialog.RETENTION }
                ReportCheckItem(repo)
                Item(stringResource(R.string.settings_clear), stringResource(R.string.settings_clear_summary), destructive = true) {
                    dialog = Dialog.CLEAR
                }
            }
            Group(R.string.settings_about) {
                Item(stringResource(R.string.settings_theme), "${stringResource(themeName(s.themeMode))} · ${stringResource(colorName(s.colorTheme))}", onClick = onAppearance)
                Item(stringResource(R.string.settings_app_language), stringResource(languageName(OfficerLocale.get(context)))) { dialog = Dialog.LANGUAGE }
                Item(stringResource(R.string.settings_licences), stringResource(R.string.settings_licences_summary)) { dialog = Dialog.LICENCES }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.app_name)) },
                    colors = ItemColors,
                    supportingContent = {
                        val built = LocalDate.ofEpochDay(BuildConfig.BUILD_EPOCH_DAY).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, built))
                            // the notice appears in About as well as on first run.
                            Text(stringResource(R.string.notice_aid), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                )
                LanguageItem(R.string.settings_isl_status, R.string.settings_isl_status_summary, R.string.settings_isl_badge, warn = false)
                LanguageItem(R.string.settings_asl_status, R.string.settings_asl_status_summary, R.string.asl_experimental_badge, warn = true)
            }
            Spacer(Modifier.height(Spacing.xl))
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
            label = { if (it == 0) stringResource(R.string.settings_keep) else pluralStringResource(R.plurals.settings_hours, it, it) },
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
        Dialog.CLOUD -> GuardedAlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(if (viaGateway) R.string.cloud_notice_title else R.string.cloud_notice_title_direct)) },
            text = { Text(stringResource(if (viaGateway) R.string.cloud_notice_body else R.string.cloud_notice_body_direct)) },
            confirmButton = {
                TextButton(onClick = { scope.launch { store.setOnlineAssistant(true) }; dialog = if (viaGateway && !enrolled) Dialog.AIKEY else null }) {
                    Text(stringResource(R.string.cloud_notice_accept))
                }
            },
            dismissButton = {
                Row {
                    // The gateway or the officer's own OpenAI-compatible service, chosen in the same dialog.
                    TextButton(onClick = { scope.launch { store.setOnlineAssistant(true) }; dialog = Dialog.DEVELOPER }) { Text(stringResource(R.string.cloud_notice_custom)) }
                    TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
        Dialog.AIKEY -> {
            // Not saveable: the access key must not outlive the dialog in saved instance state.
            var key by remember { mutableStateOf("") }
            GuardedAlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(stringResource(R.string.settings_online_assistant_key)) },
                text = {
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = { TextButton(onClick = { scope.launch { enrolled = cloud.enroll(key) || enrolled; dialog = null } }, enabled = key.isNotBlank()) { Text(stringResource(R.string.save)) } },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } },
            )
        }
        Dialog.DEVELOPER -> DeveloperAiDialog(
            probe = probe,
            initialUrl = s.devAiUrl.ifBlank { BuildConfig.ASSIST_URL },
            initialModel = s.devAiModel.ifBlank { BuildConfig.ASSIST_MODEL },
            loadKey = cloud::customKey,
            onSave = { url, model, key ->
                val stored = cloud.saveCustom(url, model, key)
                if (stored) { scope.launch { store.setDevAi(url, model) }; devVersion++ }
                stored
            },
            onClear = { cloud.clearCustom(); scope.launch { store.setDevAi("", "") }; devVersion++ },
            onDismiss = { dialog = null },
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
