package app.signsetu.ui.session

import android.app.Activity
import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.Question
import app.signsetu.data.content.pick
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.SessionEvent
import app.signsetu.export.ReportText
import app.signsetu.ui.avatar.MiniFace
import app.signsetu.ui.components.CardImage
import app.signsetu.ui.start.profileTitle
import app.signsetu.ui.theme.LocalAnswerColors
import app.signsetu.ui.theme.LocalInk
import app.signsetu.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date

private enum class Sheet { BOARD_SHOW, BOARD_ATTACH, GESTURE, LOG, CHANGE, TYPE }

/**
 * The officer's side of the phone: everything secondary to the stage. One screen, no page scroll: groups on top,
 * a handful of questions, other ways to ask, the latest answer, and the Show button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficerPane(
    ui: SessionUi,
    vm: SessionViewModel,
    officerLang: String,
    cameraOn: Boolean,
    onCameraOn: (Boolean) -> Unit,
    favourites: Set<String>,
    onFavourite: (String) -> Unit,
    onCallInterpreter: (() -> Unit)?,
    onShare: (File) -> Unit,
    onEndSession: () -> Unit,
) {
    val content = vm.content
    val ink = LocalInk.current
    val subjectLang = ui.session.subjectLang
    val subjectLanguage = content.language(subjectLang)
    var sheet by rememberSaveable { mutableStateOf<Sheet?>(null) }
    var menu by remember { mutableStateOf(false) }
    var askSensitive by remember { mutableStateOf(false) }
    var askEnd by remember { mutableStateOf(false) }
    var export by remember { mutableStateOf<ExportState?>(null) }
    var stopSeen by rememberSaveable { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    val res = LocalContext.current.resources
    val attached = remember { mutableStateListOf<String>() }
    var ownText by rememberSaveable { mutableStateOf("") }

    val answers = ui.events.withQuestions().filter { it.first.type in AnswerEvents }
    val latest = answers.lastOrNull()
    // A request to stop stays on screen until the officer acknowledges it or shows something new.
    val lastStop = ui.events.lastOrNull { it.type == EventType.QUESTION_SHOWN || it.cardId == "stop" }?.takeIf { it.cardId == "stop" }

    // The ink header needs light status-bar icons; the rest of the app uses dark ones.
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val before = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { before?.let { controller.isAppearanceLightStatusBars = it } }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Surface(color = ink.band, contentColor = ink.onBand, shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)) {
            Column(Modifier.statusBarsPadding().padding(bottom = Spacing.md)) {
                Row(Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClickLabel = stringResource(R.string.officer_change)) { sheet = Sheet.CHANGE }
                            .padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MiniFace(characterFor(ui.session.profile), 44.dp, Color(0xFFFFDDBE))
                        Column(Modifier.padding(start = Spacing.md)) {
                            Text(stringResource(profileTitle(ui.session.profile)), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                subjectLanguage?.let { "${it.name} · ${it.english}" } ?: subjectLang,
                                style = MaterialTheme.typography.bodySmall,
                                color = ink.onBandMuted,
                                maxLines = 1,
                            )
                        }
                    }
                    onCallInterpreter?.let { call ->
                        IconButton(onClick = call) { Icon(Icons.Filled.Call, contentDescription = stringResource(R.string.officer_call)) }
                    }
                    IconButton(onClick = { sheet = Sheet.LOG }) {
                        BadgedBox(badge = { if (answers.isNotEmpty()) Badge { Text("${answers.size}") } }) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.officer_log))
                        }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.officer_more)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.officer_change)) },
                                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                                onClick = { menu = false; sheet = Sheet.CHANGE },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.officer_rotate)) },
                                leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                                trailingIcon = { Checkbox(checked = ui.rotated, onCheckedChange = null) },
                                onClick = { vm.toggleRotation() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.officer_camera)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_videocam), contentDescription = null) },
                                trailingIcon = { Switch(checked = cameraOn, onCheckedChange = null) },
                                onClick = { onCameraOn(!cameraOn) },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.officer_end), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; askEnd = true },
                            )
                        }
                    }
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    contentPadding = PaddingValues(horizontal = Spacing.lg),
                    modifier = Modifier.padding(top = Spacing.sm),
                ) {
                    items(content.groups, key = { it.id }) { g ->
                        val locked = g.sensitive && !ui.sensitiveUnlocked
                        GroupChip(
                            label = g.label.pick(officerLang) ?: g.id,
                            selected = ui.selectedGroup == g.id,
                            locked = locked,
                            onClick = { if (locked) askSensitive = true else vm.selectGroup(g.id) },
                        )
                    }
                }
            }
        }

        // At large font sizes (NFR-22) the panel becomes one scrolling column; otherwise it fits one screen.
        val largeText = LocalDensity.current.fontScale > 1.3f
        val questions = content.questions.filter { it.group == ui.selectedGroup }
        val actions: @Composable () -> Unit = {
            ActionButton(painterResource(R.drawable.ic_hand), stringResource(R.string.action_gesture), wide = largeText) { sheet = Sheet.GESTURE }
            ActionButton(painterResource(R.drawable.ic_grid), stringResource(R.string.action_board), wide = largeText) { sheet = Sheet.BOARD_SHOW }
            ActionButton(rememberVectorPainter(Icons.Filled.Create), stringResource(R.string.action_draw), ui.pending == Pending.Drawing, largeText) {
                vm.prepare(Pending.Drawing)
            }
            ActionButton(painterResource(R.drawable.ic_translate), stringResource(R.string.action_language), ui.pending == Pending.LanguageFinder, largeText) {
                vm.prepare(Pending.LanguageFinder)
            }
            ActionButton(painterResource(R.drawable.ic_keyboard), stringResource(R.string.action_type), ui.pending is Pending.FreeText, largeText) { sheet = Sheet.TYPE }
        }
        Column(Modifier.weight(1f).padding(horizontal = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            AnimatedVisibility(lastStop != null && lastStop.id != stopSeen, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                StopBanner(onOk = { stopSeen = lastStop?.id })
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (largeText) 1 else 2),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                contentPadding = PaddingValues(top = Spacing.md, bottom = if (largeText) Spacing.md else 0.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(questions, key = { it.id }) { q ->
                    QuestionTile(
                        question = q,
                        content = content,
                        officerLang = officerLang,
                        subjectLang = subjectLang,
                        profile = ui.session.profile,
                        selected = (ui.pending as? Pending.Ask)?.question?.id == q.id,
                        onSelect = { vm.prepare(Pending.Ask(q)) },
                    )
                }
                if (largeText) {
                    item(span = { GridItemSpan(maxLineSpan) }) { AskAnotherWay(scrolls = true, actions) }
                    latest?.let { (e, question) ->
                        item(span = { GridItemSpan(maxLineSpan) }) { LatestAnswer(e, question, onClick = { sheet = Sheet.LOG }) }
                    }
                }
            }
            if (!largeText) {
                AskAnotherWay(scrolls = false, actions)
                latest?.let { (e, question) -> LatestAnswer(e, question, onClick = { sheet = Sheet.LOG }) }
            }
        }

        ShowBar(ui.pending, content, officerLang, subjectLang, ui.session.profile, onShow = vm::show, onClear = { vm.prepare(null) })
    }

    when (sheet) {
        Sheet.BOARD_SHOW -> BoardSheet(content, officerLang, emptyList(), favourites, onFavourite, onUse = { vm.prepare(Pending.Cards(it)); sheet = null }, onDismiss = { sheet = null })
        Sheet.BOARD_ATTACH -> BoardSheet(content, officerLang, attached.toList(), favourites, onFavourite, onUse = { attached.clear(); attached.addAll(it); sheet = Sheet.TYPE }, onDismiss = { sheet = Sheet.TYPE })
        Sheet.GESTURE -> GestureSheet(content, officerLang, onPick = { vm.prepare(Pending.ShowBack(it)); sheet = null }, onDismiss = { sheet = null })
        Sheet.LOG -> LogSheet(ui.events, onAddNote = vm::addNote, onDismiss = { sheet = null })
        Sheet.CHANGE -> ChangeSheet(content, ui.session.profile, subjectLang, onProfile = vm::setProfile, onLanguage = vm::setSubjectLang, onDismiss = { sheet = null })
        Sheet.TYPE -> TypeSheet(
            text = ownText,
            onText = { ownText = it },
            attached = attached,
            content = content,
            lang = officerLang,
            onAddPictures = { sheet = Sheet.BOARD_ATTACH },
            onUse = { vm.prepare(Pending.FreeText(ownText.trim(), attached.toList())); sheet = null },
            onDismiss = { sheet = null },
        )
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
private fun GroupChip(label: String, selected: Boolean, locked: Boolean, onClick: () -> Unit) {
    val ink = LocalInk.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) ink.chipSelected else ink.chip,
        contentColor = if (selected) ink.onChipSelected else ink.onBand,
        modifier = Modifier.height(40.dp).semantics { this.selected = selected },
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (locked) Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.padding(end = 4.dp).size(14.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun QuestionTile(
    question: Question,
    content: ContentBundle,
    officerLang: String,
    subjectLang: String,
    profile: Profile,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val caption = question.text.pick(subjectLang)
    val hasGesture = content.gestureFor(question) != null
    val hasAudio = content.questionAudio(question.id, subjectLang) != null && profile != Profile.ISL_USER
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(22.dp),
        color = if (selected) colors.primaryContainer else colors.surfaceContainerLowest,
        border = if (selected) BorderStroke(2.dp, colors.primary) else BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.6f)),
        shadowElevation = if (selected) 0.dp else 1.dp,
        modifier = Modifier.fillMaxWidth().heightIn(min = 104.dp).semantics { this.selected = selected },
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = colors.surfaceContainer, modifier = Modifier.size(34.dp)) {
                    question.cards.firstOrNull()?.let { CardImage(it, Modifier.padding(4.dp)) }
                }
                Spacer(Modifier.weight(1f))
                // What the subject will get, so a missing gesture or recording is never a surprise (FR-17).
                if (hasGesture) MediaDot(painterResource(R.drawable.ic_hand), stringResource(R.string.officer_has_gesture))
                if (hasAudio) MediaDot(painterResource(R.drawable.ic_volume), stringResource(R.string.officer_has_audio))
                if (selected) {
                    Surface(shape = CircleShape, color = colors.primary, modifier = Modifier.padding(start = 4.dp).size(22.dp)) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.padding(3.dp))
                    }
                }
            }
            Text(
                question.text.pick(officerLang) ?: question.text.values.first(),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            when {
                caption == null -> Text(
                    stringResource(R.string.officer_caption_missing, content.language(subjectLang)?.english ?: subjectLang),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.tertiary,
                    maxLines = 2,
                )
                subjectLang != officerLang -> Text(caption, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                !hasGesture && !hasAudio -> Text(stringResource(R.string.officer_media_missing), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MediaDot(painter: Painter, name: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.padding(start = 4.dp).size(24.dp).semantics { contentDescription = name }) {
        Icon(painter, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(4.dp))
    }
}

@Composable
private fun AskAnotherWay(scrolls: Boolean, actions: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(stringResource(R.string.officer_ask_another), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (scrolls) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) { actions() }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { actions() }
        }
    }
}

@Composable
private fun ActionButton(icon: Painter, label: String, selected: Boolean = false, wide: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .width(if (wide) 112.dp else 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (selected) colors.primary else colors.secondaryContainer,
            contentColor = if (selected) colors.onPrimary else colors.onSecondaryContainer,
            modifier = Modifier.size(52.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null) }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = if (wide) 2 else 1,
            textAlign = TextAlign.Center,
            autoSize = if (wide) null else TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.sp),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun StopBanner(onOk: () -> Unit) {
    val answer = LocalAnswerColors.current
    Surface(shape = RoundedCornerShape(20.dp), color = answer.pause, contentColor = answer.onPause, modifier = Modifier.padding(top = Spacing.md)) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            CardImage("stop", Modifier.size(40.dp))
            Column(Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                Text(stringResource(R.string.officer_stop_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.officer_stop_body), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onOk) { Text(stringResource(R.string.ok), color = answer.onPause) }
        }
    }
}

@Composable
private fun LatestAnswer(e: SessionEvent, question: String?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val answerColors = LocalAnswerColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceContainerLowest,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = Spacing.md, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            val tint = when (e.cardId) {
                "yes" -> answerColors.yesContainer
                "no" -> answerColors.noContainer
                else -> colors.surfaceContainer
            }
            Surface(shape = RoundedCornerShape(12.dp), color = tint, modifier = Modifier.size(40.dp)) {
                e.cardId?.let { CardImage(it, Modifier.padding(5.dp)) }
            }
            Column(Modifier.weight(1f).padding(start = Spacing.md)) {
                Text(
                    "${stringResource(R.string.officer_latest)} · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(e.ts))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    e.text ?: stringResource(e.type.label()),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                question?.takeIf { it != e.text }?.let {
                    Text(stringResource(R.string.officer_in_reply, it), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ShowBar(
    pending: Pending?,
    content: ContentBundle,
    officerLang: String,
    subjectLang: String,
    profile: Profile,
    onShow: () -> Unit,
    onClear: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surfaceContainer, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), modifier = Modifier.padding(top = Spacing.md)) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 40.dp)) {
                Text(
                    pending?.let { describe(it, content, officerLang) } ?: stringResource(R.string.officer_nothing_pending),
                    style = if (pending == null) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall,
                    color = if (pending == null) colors.onSurfaceVariant else colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (pending != null) {
                    IconButton(onClick = onClear) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel)) }
                }
            }
            if (pending is Pending.Ask) MediaSummary(pending.question, content, subjectLang, profile)
            Button(
                onClick = onShow,
                enabled = pending != null,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs).height(60.dp),
            ) {
                Text(stringResource(R.string.officer_show), style = MaterialTheme.typography.titleMedium)
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.padding(start = Spacing.sm))
            }
        }
    }
}

/** What the subject will get for this question; anything missing is named before the officer shows it (FR-17). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MediaSummary(question: Question, content: ContentBundle, subjectLang: String, profile: Profile) {
    val items = buildList {
        add(R.string.media_caption to (question.text.pick(subjectLang) != null))
        if (profile != Profile.ISL_USER) add(R.string.media_audio to (content.questionAudio(question.id, subjectLang) != null))
        add(R.string.media_gesture to (content.gestureFor(question) != null))
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
        items.forEach { (label, present) ->
            val name = stringResource(label)
            Surface(
                shape = RoundedCornerShape(50),
                color = if (present) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = if (present) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (present) Icons.Filled.Check else Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                    Text(
                        if (present) name else stringResource(R.string.media_missing, name),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TypeSheet(
    text: String,
    onText: (String) -> Unit,
    attached: List<String>,
    content: ContentBundle,
    lang: String,
    onAddPictures: () -> Unit,
    onUse: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.xxl), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SheetTitle(stringResource(R.string.officer_own_question))
            OutlinedTextField(
                value = text,
                onValueChange = onText,
                placeholder = { Text(stringResource(R.string.officer_own_hint)) },
                minLines = 2,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            if (attached.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    attached.forEach { id ->
                        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                CardImage(id, Modifier.size(24.dp))
                                Text(content.cards[id]?.label?.pick(lang) ?: id, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(onClick = onAddPictures, modifier = Modifier.weight(1f).height(52.dp)) {
                    Icon(painterResource(R.drawable.ic_grid), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.officer_add_pictures), modifier = Modifier.padding(start = Spacing.sm))
                }
                Button(onClick = onUse, enabled = text.isNotBlank(), modifier = Modifier.weight(1f).height(52.dp)) {
                    Text(stringResource(R.string.officer_use))
                }
            }
        }
    }
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
