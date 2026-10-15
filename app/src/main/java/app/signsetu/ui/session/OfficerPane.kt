package app.signsetu.ui.session

import android.content.res.Resources
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.window.DialogProperties
import app.signsetu.data.session.SessionEvent
import app.signsetu.export.ReportText
import java.text.DateFormat
import java.util.Date
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.Question
import app.signsetu.data.content.pick
import app.signsetu.ui.components.CardImage
import app.signsetu.ui.start.profileTitle
import app.signsetu.ui.theme.Spacing

private enum class Sheet { BOARD_SHOW, BOARD_ATTACH, GESTURE, LOG, CHANGE }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OfficerPane(
    ui: SessionUi,
    vm: SessionViewModel,
    officerLang: String,
    onCallInterpreter: (() -> Unit)?,
    onShare: (File) -> Unit,
    onEndSession: () -> Unit,
) {
    val content = vm.content
    val subjectLang = ui.session.subjectLang
    val subjectLanguage = content.language(subjectLang)
    var sheet by rememberSaveable { mutableStateOf<Sheet?>(null) }
    var menu by remember { mutableStateOf(false) }
    var askSensitive by remember { mutableStateOf(false) }
    var askEnd by remember { mutableStateOf(false) }
    var export by remember { mutableStateOf<ExportState?>(null) }
    val scope = rememberCoroutineScope()
    val res = LocalContext.current.resources
    var ownText by rememberSaveable { mutableStateOf("") }
    val attached = remember { mutableStateListOf<String>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(Modifier.clickable(onClickLabel = stringResource(R.string.officer_change)) { sheet = Sheet.CHANGE }) {
                        Text(stringResource(profileTitle(ui.session.profile)), style = MaterialTheme.typography.titleMedium)
                        Text(
                            subjectLanguage?.let { "${it.name} · ${it.english}" } ?: subjectLang,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    onCallInterpreter?.let { call ->
                        IconButton(onClick = call) {
                            Icon(Icons.Filled.Call, contentDescription = stringResource(R.string.officer_call))
                        }
                    }
                    IconButton(onClick = { sheet = Sheet.LOG }) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.officer_log))
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.officer_more))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.officer_change)) },
                                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                                onClick = { menu = false; sheet = Sheet.CHANGE },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.officer_rotate)) },
                                leadingIcon = { Checkbox(checked = ui.rotated, onCheckedChange = null) },
                                onClick = { vm.toggleRotation() },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.officer_end), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; askEnd = true },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = { ShowBar(ui.pending, content, officerLang, onShow = vm::show, onClear = { vm.prepare(null) }) },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                ) {
                    items(content.groups, key = { it.id }) { g ->
                        val locked = g.sensitive && !ui.sensitiveUnlocked
                        FilterChip(
                            selected = ui.selectedGroup == g.id,
                            onClick = { if (locked) askSensitive = true else vm.selectGroup(g.id) },
                            label = { Text(g.label.pick(officerLang) ?: g.id) },
                            leadingIcon = if (locked) {
                                { Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }

            val noCaptions = content.questions.none { it.text.pick(subjectLang) != null }
            if (noCaptions) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    ) {
                        Text(
                            stringResource(R.string.officer_caption_missing, subjectLanguage?.english ?: subjectLang),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(Spacing.md),
                        )
                    }
                }
            }

            items(content.questions.filter { it.group == ui.selectedGroup }, key = { it.id }) { q ->
                QuestionRow(
                    question = q,
                    officerLang = officerLang,
                    subjectLang = subjectLang,
                    subjectLanguageName = subjectLanguage?.english ?: subjectLang,
                    flagMissing = !noCaptions,
                    selected = (ui.pending as? Pending.Ask)?.question?.id == q.id,
                    onSelect = { vm.prepare(Pending.Ask(q)) },
                )
            }

            item {
                FlowRow(
                    Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    QuickAction(Icons.Filled.Face, stringResource(R.string.officer_gestured)) { sheet = Sheet.GESTURE }
                    QuickAction(Icons.Filled.Search, stringResource(R.string.officer_board)) { sheet = Sheet.BOARD_SHOW }
                    QuickAction(Icons.Filled.Create, stringResource(R.string.officer_draw)) { vm.prepare(Pending.Drawing) }
                    QuickAction(Icons.Filled.Place, stringResource(R.string.officer_find_language)) { vm.prepare(Pending.LanguageFinder) }
                }
            }

            item {
                Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionLabel(stringResource(R.string.officer_own_question))
                    OutlinedTextField(
                        value = ownText,
                        onValueChange = { ownText = it },
                        placeholder = { Text(stringResource(R.string.officer_own_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (attached.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            attached.forEach { id ->
                                InputChip(
                                    selected = false,
                                    onClick = { attached.remove(id) },
                                    label = { Text(content.cards[id]?.label?.pick(officerLang) ?: id) },
                                    avatar = { CardImage(id, Modifier.size(24.dp)) },
                                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        TextButton(onClick = { sheet = Sheet.BOARD_ATTACH }) { Text(stringResource(R.string.officer_add_pictures)) }
                        Button(
                            onClick = { vm.prepare(Pending.FreeText(ownText.trim(), attached.toList())) },
                            enabled = ownText.isNotBlank(),
                        ) { Text(stringResource(R.string.officer_use)) }
                    }
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = Spacing.sm)) }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SectionLabel(stringResource(R.string.officer_answers))
                    TextButton(onClick = { sheet = Sheet.LOG }) { Text(stringResource(R.string.officer_see_all)) }
                }
            }
            val answers = ui.events.withQuestions().filter { it.first.type in AnswerEvents }.takeLast(5).asReversed()
            if (answers.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.officer_no_answers),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    )
                }
            }
            items(answers, key = { "a${it.first.id}" }) { (e, question) -> EventRow(e, question) }
        }
    }

    when (sheet) {
        Sheet.BOARD_SHOW -> BoardSheet(content, officerLang, emptyList(), onUse = { vm.prepare(Pending.Cards(it)); sheet = null }, onDismiss = { sheet = null })
        Sheet.BOARD_ATTACH -> BoardSheet(content, officerLang, attached.toList(), onUse = { attached.clear(); attached.addAll(it); sheet = null }, onDismiss = { sheet = null })
        Sheet.GESTURE -> GestureSheet(content, officerLang, onPick = { vm.prepare(Pending.ShowBack(it)); sheet = null }, onDismiss = { sheet = null })
        Sheet.LOG -> LogSheet(ui.events, onAddNote = vm::addNote, onDismiss = { sheet = null })
        Sheet.CHANGE -> ChangeSheet(content, ui.session.profile, subjectLang, onProfile = vm::setProfile, onLanguage = vm::setSubjectLang, onDismiss = { sheet = null })
        null -> Unit
    }

    if (askSensitive) {
        AlertDialog(
            onDismissRequest = { askSensitive = false },
            icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            title = { Text(stringResource(R.string.sensitive_title)) },
            text = { Text(stringResource(R.string.sensitive_body)) },
            confirmButton = {
                TextButton(onClick = { askSensitive = false; vm.unlockSensitive(); vm.selectGroup("sensitive") }) {
                    Text(stringResource(R.string.sensitive_confirm))
                }
            },
            dismissButton = { TextButton(onClick = { askSensitive = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (askEnd) {
        AlertDialog(
            onDismissRequest = { askEnd = false },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.end_title)) },
            text = { Text(stringResource(R.string.end_body)) },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(modifier = Modifier.fillMaxWidth(), onClick = {
                    askEnd = false
                    export = ExportState.Working
                    scope.launch {
                        export = runCatching { vm.exportReport(reportText(res, ui, content)) { eventDetail(res, it) } }
                            .fold({ (file, pass) -> ExportState.Ready(file, pass) }, { ExportState.Failed })
                    }
                }) { Text(stringResource(R.string.end_export)) }
                OutlinedButton(onClick = { askEnd = false; onEndSession() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.end_no_export), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = { askEnd = false }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }
    export?.let { state ->
        ExportDialog(
            state = state,
            onShare = { (state as? ExportState.Ready)?.let { onShare(it.file) } },
            onFinish = { (state as? ExportState.Ready)?.file?.delete(); export = null; onEndSession() },
            onDismiss = { export = null },
        )
    }
}

@Composable
private fun QuestionRow(
    question: Question,
    officerLang: String,
    subjectLang: String,
    subjectLanguageName: String,
    flagMissing: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val caption = question.text.pick(subjectLang)
    val colors = MaterialTheme.colorScheme
    ListItem(
        headlineContent = { Text(question.text.pick(officerLang) ?: question.text.values.first()) },
        supportingContent = when {
            caption == null && flagMissing -> {
                { Text(stringResource(R.string.officer_caption_missing, subjectLanguageName), color = colors.tertiary) }
            }
            caption == null -> null
            subjectLang != officerLang -> {
                { Text(caption) }
            }
            else -> null
        },
        leadingContent = question.cards.firstOrNull()?.let { id ->
            {
                Surface(shape = MaterialTheme.shapes.small, color = colors.surfaceContainerLowest) {
                    CardImage(id, Modifier.padding(4.dp).size(36.dp))
                }
            }
        },
        trailingContent = { RadioButton(selected = selected, onClick = null) },
        colors = ListItemDefaults.colors(containerColor = if (selected) colors.primaryContainer else colors.surface),
        modifier = Modifier.selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
    )
}

@Composable
private fun QuickAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
        modifier = Modifier.heightIn(min = 48.dp),
    )
}

@Composable
private fun ShowBar(pending: Pending?, content: ContentBundle, officerLang: String, onShow: () -> Unit, onClear: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(if (pending == null) R.string.officer_nothing_pending else R.string.officer_ready),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    pending?.let {
                        Text(
                            describe(it, content, officerLang),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (pending != null) {
                    IconButton(onClick = onClear) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel)) }
                }
            }
            Button(
                onClick = onShow,
                enabled = pending != null,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm).heightIn(min = 56.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Text(stringResource(R.string.officer_show), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = Spacing.sm))
            }
        }
    }
}

@Composable
private fun describe(p: Pending, content: ContentBundle, lang: String): String = when (p) {
    is Pending.Ask -> p.question.text.pick(lang) ?: p.question.id
    is Pending.FreeText -> if (p.cardIds.isEmpty()) p.text else "${p.text}  ·  ${stringResource(R.string.officer_pictures, p.cardIds.size)}"
    is Pending.Cards -> p.cardIds.joinToString { content.cards[it]?.label?.pick(lang) ?: it }
    is Pending.ShowBack -> "${stringResource(R.string.officer_gesture)}: ${p.entry.label.pick(lang) ?: p.entry.id}"
    Pending.LanguageFinder -> stringResource(R.string.officer_find_language)
    Pending.Drawing -> stringResource(R.string.officer_draw)
}

private sealed interface ExportState {
    data object Working : ExportState
    data class Ready(val file: File, val passphrase: String) : ExportState
    data object Failed : ExportState
}

private fun reportText(res: Resources, ui: SessionUi, content: ContentBundle): ReportText {
    val time = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    val language = content.language(ui.session.subjectLang)
    return ReportText(
        title = res.getString(R.string.report_title),
        disclaimer = res.getString(R.string.report_disclaimer),
        header = listOf(
            res.getString(R.string.report_started) to time.format(Date(ui.session.startedAt)),
            res.getString(R.string.report_exported) to time.format(Date()),
            res.getString(R.string.report_profile) to res.getString(profileTitle(ui.session.profile)),
            res.getString(R.string.report_language) to (language?.let { "${it.english} (${it.name})" } ?: ui.session.subjectLang),
        ),
        time = res.getString(R.string.report_time),
        event = res.getString(R.string.report_event),
        detail = res.getString(R.string.report_detail),
        inReplyTo = res.getString(R.string.report_in_reply),
        page = { n, total -> res.getString(R.string.report_page, n, total) },
        eventLabel = { res.getString(it.label()) },
        formatTime = { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) },
    )
}

private fun eventDetail(res: Resources, e: SessionEvent): String = when {
    e.text == SYSTEM_LANGUAGE_FINDER -> res.getString(R.string.officer_find_language)
    e.text == SYSTEM_DRAWING -> res.getString(R.string.officer_draw)
    e.drawingId != null -> res.getString(R.string.report_drawing)
    else -> e.text.orEmpty()
}

@Composable
private fun ExportDialog(state: ExportState, onShare: () -> Unit, onFinish: () -> Unit, onDismiss: () -> Unit) {
    when (state) {
        ExportState.Working -> AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    CircularProgressIndicator(Modifier.size(32.dp))
                    Text(stringResource(R.string.export_working))
                }
            },
            confirmButton = {},
        )
        is ExportState.Ready -> AlertDialog(
            // The password is shown once; the dialog closes only through its own buttons.
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            title = { Text(stringResource(R.string.export_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(stringResource(R.string.export_body))
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.fillMaxWidth().padding(Spacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                stringResource(R.string.export_password),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                state.passphrase,
                                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                                maxLines = 1,
                                autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 28.sp),
                                // Read out character by character, as the officer would.
                                modifier = Modifier.semantics { contentDescription = state.passphrase.toCharArray().joinToString(" ") },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = onShare) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.export_share), modifier = Modifier.padding(start = Spacing.sm))
                }
            },
            dismissButton = {
                TextButton(onClick = onFinish) { Text(stringResource(R.string.export_finish), color = MaterialTheme.colorScheme.error) }
            },
        )
        ExportState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.export_failed)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
        )
    }
}
