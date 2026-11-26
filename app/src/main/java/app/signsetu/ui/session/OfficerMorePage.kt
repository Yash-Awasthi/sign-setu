package app.signsetu.ui.session

import androidx.compose.foundation.border
import app.signsetu.assistant.Suggestion
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import app.signsetu.ui.components.OfficerCard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.signsetu.R
import app.signsetu.data.content.Group
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.Phrase
import app.signsetu.data.content.Question
import app.signsetu.data.content.SignLanguage
import app.signsetu.data.session.Profile
import app.signsetu.ui.components.CardImage
import app.signsetu.ui.theme.PictureWell
import app.signsetu.ui.theme.Space
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Phone
import app.signsetu.data.session.EventType
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.window.DialogProperties
import app.signsetu.ui.help.HelpScreen
import app.signsetu.data.content.Localized
import app.signsetu.ui.theme.TypeSize
import app.signsetu.ui.theme.Radius

internal enum class Tool { GESTURE, BOARD, DRAW, LANGUAGE, ATTACH, RULES, DICTIONARY }

/** Every other way to ask, and the session's record: one tap from the main page. Picking something returns there. */
@Composable
internal fun MorePage(
    ui: SessionUi,
    content: ContentBundle,
    officerLang: String,
    onGroup: (Group) -> Unit,
    onQuestion: (Question) -> Unit,
    onPhrase: (Phrase) -> Unit,
    onAddToRun: (Question) -> Unit,
    onTool: (Tool) -> Unit,
    onSecondScreen: (() -> Unit)?,
    /** Kept for callers; ending a session is the officer header's one End control now, not this page. */
    @Suppress("UNUSED_PARAMETER") onFinish: () -> Unit = {},
    onCounsel: (() -> Unit)?,
    modifier: Modifier,
    auto: AutoPilot? = null,
) {
    val pending = ui.pending
    val subjectLang = ui.session.subjectLang
    val wide = LocalDensity.current.fontScale > 1.3f
    var help by rememberSaveable { mutableStateOf(false) }
    if (help) {
        Dialog(onDismissRequest = { help = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize()) { HelpScreen(onBack = { help = false }, startTopic = "call") }
        }
    }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(vertical = Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        auto?.let { AutoPanel(it, ui, content, officerLang, showSwitch = true) }
        SectionLabel(stringResource(R.string.more_questions), Modifier.padding(horizontal = Space.lg))
        var query by rememberSaveable { mutableStateOf("") }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.more_search)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = if (query.isEmpty()) null else { { IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.more_search_clear)) } } },
            singleLine = true,
            shape = RoundedCornerShape(Radius.r28),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.lg),
        )
        val groups = content.groupsFor(ui.session.role, ui.juvenile)
        // Search-only since 0.5.1; a category chip is still a way in when the officer does not know the wording.
        if (query.isBlank()) {
            SectionLabel(stringResource(R.string.more_browse), Modifier.padding(horizontal = Space.lg).padding(top = Space.sm))
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = Space.lg),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                groups.forEach { g ->
                    val label = g.label.get(officerLang) ?: g.id
                    key(g.id) {
                        Surface(
                            shape = RoundedCornerShape(Radius.r20),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.minimumInteractiveComponentSize().clip(RoundedCornerShape(Radius.r20)).clickable(role = Role.Button) { query = label },
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = Space.s14, vertical = Space.sm),
                            )
                        }
                    }
                }
            }
        }
        val words = query.trim().lowercase().split(' ').filter { it.isNotEmpty() }
        // A word matches a question's text in any language, or the name of its category.
        fun hit(vararg texts: Localized?) = words.all { w -> texts.any { t -> t?.values?.any { it.lowercase().contains(w) } == true } }
        Column {
            val asked = remember(ui.events) { ui.events.filter { it.type == EventType.QUESTION_SHOWN }.associateBy({ it.id }, { it.itemId }) }
            val askedIds = remember(asked) { asked.values.filterNotNull().toSet() }
            val answeredIds = remember(ui.events, asked) { ui.events.filter { it.type in AnswerEvents }.mapNotNull { e -> e.shownId?.let(asked::get) }.toSet() }
            val phrases = if (words.isEmpty()) emptyList() else content.phrases.filter { p -> hit(p.label, *p.slots.map { it.text }.toTypedArray()) }
            val questions = if (words.isEmpty()) emptyList()
                else groups.flatMap { g -> content.questionsIn(g.id, ui.session.role, ui.session.profile, ui.juvenile).filter { hit(it.text, g.label) }.map { g to it } }
            Column(Modifier.padding(horizontal = Space.lg, vertical = Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                if (words.isNotEmpty() && phrases.isEmpty() && questions.isEmpty()) {
                    Text(stringResource(R.string.more_search_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                phrases.forEach { p -> key(p.id) {
                    QuestionRow(
                        cardId = p.slots.firstOrNull()?.card,
                        title = p.label.get(officerLang) ?: p.id,
                        caption = p.label.get(subjectLang)?.takeIf { subjectLang != officerLang },
                        hasGesture = true,
                        hasAudio = ui.session.profile != Profile.ISL_USER && p.slots.any { content.phraseAudio(p, it, subjectLang) != null },
                        selected = (pending as? Pending.PhraseAsk)?.phrase?.id == p.id,
                        status = null,
                        onClick = { onPhrase(p) },
                    )
                } }
                questions.forEach { (g, q) -> key(q.id) {
                    QuestionRow(
                        cardId = q.cards.firstOrNull(),
                        title = q.text.get(officerLang) ?: q.text.values.first(),
                        caption = q.text.get(subjectLang)?.takeIf { subjectLang != officerLang },
                        missing = if (q.text.get(subjectLang) == null) stringResource(R.string.officer_caption_missing, content.language(subjectLang)?.english ?: subjectLang) else null,
                        hasGesture = content.gestureFor(q) != null,
                        hasAudio = content.questionAudio(q.id, subjectLang) != null && ui.session.profile != Profile.ISL_USER,
                        selected = (pending as? Pending.Ask)?.question?.id == q.id,
                        status = when (q.id) { in answeredIds -> Asked.ANSWERED; in askedIds -> Asked.ASKED; else -> null },
                        onClick = { if (g.sensitive && !ui.sensitiveUnlocked) onGroup(g) else onQuestion(q) },
                        onLongClick = { onAddToRun(q) },
                    )
                } }
            }
        }
        SectionLabel(stringResource(R.string.more_tools), Modifier.padding(horizontal = Space.lg))
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.sm), horizontalArrangement = Arrangement.SpaceBetween) {
            ToolButton(painterResource(R.drawable.ic_hand), stringResource(R.string.action_gesture), pending is Pending.ShowBack) { onTool(Tool.GESTURE) }
            ToolButton(painterResource(R.drawable.ic_grid), stringResource(R.string.action_board), pending is Pending.Cards) { onTool(Tool.BOARD) }
            ToolButton(rememberVectorPainter(Icons.Filled.Create), stringResource(R.string.action_draw), pending == Pending.Drawing) { onTool(Tool.DRAW) }
            ToolButton(painterResource(R.drawable.ic_translate), stringResource(R.string.action_language), pending == Pending.LanguageFinder) { onTool(Tool.LANGUAGE) }
        }
        SectionLabel(stringResource(R.string.more_session), Modifier.padding(horizontal = Space.lg).padding(top = Space.md))
        OfficerCard(Modifier.padding(horizontal = Space.lg), padding = Space.none) {
            onCounsel?.let { MoreRow(rememberVectorPainter(Icons.Filled.Face), stringResource(R.string.ai_counsellor_start), null, first = true, onClick = it) }
            MoreRow(rememberVectorPainter(Icons.Filled.Search), stringResource(if (ui.signLanguage == SignLanguage.ASL) R.string.asl_dictionary else R.string.isl_dictionary), null, first = onCounsel == null) { onTool(Tool.DICTIONARY) }
            onSecondScreen?.let { MoreRow(rememberVectorPainter(Icons.Filled.Phone), stringResource(R.string.second_screen), null, onClick = it) }
            MoreRow(rememberVectorPainter(Icons.Filled.Refresh), stringResource(R.string.officer_rules), null) { onTool(Tool.RULES) }
            MoreRow(rememberVectorPainter(Icons.Filled.Info), stringResource(R.string.stage_help), null) { help = true }
        }
    }
}

@Composable
private fun MoreRow(icon: Painter, label: String, badge: String?, first: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    if (!first) HorizontalDivider(Modifier.padding(start = Space.s56), color = colors.outlineVariant)
    Row(
        Modifier.fillMaxWidth().heightIn(min = Space.s56).clickable(role = Role.Button, onClick = onClick).padding(horizontal = Space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(Space.xl))
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(start = Space.lg))
        badge?.let { Badge(Modifier.padding(end = Space.sm)) { Text(it) } }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(Space.s20))
    }
}

internal enum class Asked { ASKED, ANSWERED }

/** A prepared question or phrase as a full-width row, with whether it was asked or answered in this session. */
@Composable
private fun QuestionRow(
    cardId: String?,
    title: String,
    caption: String?,
    hasGesture: Boolean,
    hasAudio: Boolean,
    selected: Boolean,
    status: Asked?,
    onClick: () -> Unit,
    missing: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val statusText = status?.let { stringResource(if (it == Asked.ANSWERED) R.string.more_answered else R.string.more_asked) }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (selected) colors.primaryContainer else colors.surfaceContainerLowest,
        border = if (selected) BorderStroke(Space.xxs, colors.primary) else BorderStroke(Space.hair, colors.outlineVariant),
        modifier = Modifier.fillMaxWidth().heightIn(min = Space.s64)
            .clip(MaterialTheme.shapes.large).runnable(onClick, onLongClick, onLongClick?.let { stringResource(R.string.run_add) })
            .semantics { this.selected = selected; statusText?.let { stateDescription = it } },
    ) {
        Row(Modifier.padding(horizontal = Space.md, vertical = Space.s10), verticalAlignment = Alignment.CenterVertically) {
            cardId?.let {
                Surface(shape = RoundedCornerShape(Radius.r10), color = PictureWell, modifier = Modifier.padding(end = Space.md).size(Space.s40)) {
                    CardImage(it, Modifier.padding(Space.xs))
                }
            }
            Column(Modifier.weight(1f).padding(end = Space.md), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                when {
                    missing != null -> Text(missing, style = MaterialTheme.typography.bodySmall, color = colors.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    caption != null -> Text(caption, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    !hasGesture && !hasAudio -> Text(stringResource(R.string.officer_media_missing), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
                }
            }
            if (hasGesture) MediaDot(painterResource(R.drawable.ic_hand), stringResource(R.string.officer_has_gesture))
            if (hasAudio) MediaDot(painterResource(R.drawable.ic_volume), stringResource(R.string.officer_has_audio))
            statusText?.let {
                val done = status == Asked.ANSWERED
                Surface(shape = Radius.pill, color = if (done) colors.primary else colors.secondaryContainer, modifier = Modifier.padding(start = Space.s6)) {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = if (done) colors.onPrimary else colors.onSecondaryContainer, modifier = Modifier.padding(horizontal = Space.sm, vertical = Space.s3))
                }
            }
        }
    }
}

/** A prepared question or phrase: its picture, its words, and what the subject will get. */
@Composable
internal fun Suggestion(
    cardId: String?,
    title: String,
    caption: String?,
    hasGesture: Boolean,
    hasAudio: Boolean,
    selected: Boolean,
    wide: Boolean,
    onClick: () -> Unit,
    missing: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (selected) colors.primaryContainer else colors.surfaceContainerLowest,
        border = if (selected) BorderStroke(Space.xxs, colors.primary) else BorderStroke(Space.hair, colors.outlineVariant),
        modifier = Modifier.width(if (wide) Space.s272 else Space.s200).heightIn(min = if (wide) Space.s148 else Space.s104)
            .clip(MaterialTheme.shapes.large).runnable(onClick, onLongClick, onLongClick?.let { stringResource(R.string.run_add) })
            .semantics { this.selected = selected },
    ) {
        Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(Radius.r10), color = PictureWell, modifier = Modifier.size(Space.s30)) {
                    cardId?.let { CardImage(it, Modifier.padding(Space.s3)) }
                }
                Spacer(Modifier.weight(1f))
                if (hasGesture) MediaDot(painterResource(R.drawable.ic_hand), stringResource(R.string.officer_has_gesture))
                if (hasAudio) MediaDot(painterResource(R.drawable.ic_volume), stringResource(R.string.officer_has_audio))
                if (selected) {
                    Surface(shape = CircleShape, color = colors.primary, modifier = Modifier.padding(start = Space.xs).size(Space.s20)) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.padding(Space.s3))
                    }
                }
            }
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            when {
                missing != null -> Text(missing, style = MaterialTheme.typography.bodySmall, color = colors.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                caption != null -> Text(caption, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                !hasGesture && !hasAudio -> Text(stringResource(R.string.officer_media_missing), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun MediaDot(painter: Painter, name: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.padding(start = Space.xs).size(Space.s22).semantics { contentDescription = name }) {
        Icon(painter, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(Space.xs))
    }
}

@Composable
private fun ToolButton(icon: Painter, label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .widthIn(min = Space.s64)
            .clip(RoundedCornerShape(Radius.r16))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Space.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = if (selected) colors.primary else colors.secondaryContainer,
            contentColor = if (selected) colors.onPrimary else colors.onSecondaryContainer,
            modifier = Modifier.size(Space.s48),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null) }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            textAlign = TextAlign.Center,
            autoSize = TextAutoSize.StepBased(minFontSize = TypeSize.s9, maxFontSize = TypeSize.s12),
            modifier = Modifier.padding(top = Space.xs).width(Space.s80),
        )
    }
}
