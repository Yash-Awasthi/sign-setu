package app.signsetu.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.signsetu.R
import app.signsetu.data.content.BodyLocation
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.HandAction
import app.signsetu.data.content.LexiconEntry
import app.signsetu.data.content.pick
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.SessionEvent
import app.signsetu.ui.components.CardImage
import app.signsetu.ui.components.PictureCard
import app.signsetu.ui.start.LanguagePicker
import app.signsetu.ui.start.profileTitle
import app.signsetu.ui.theme.Spacing
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardSheet(content: ContentBundle, lang: String, initial: List<String>, onUse: (List<String>) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val chosen = remember { mutableStateListOf<String>().apply { addAll(initial) } }
    val cards = content.cards.values.filter { c ->
        (category == null || c.category == category) &&
            (query.isBlank() || c.label.values.any { it.contains(query.trim(), ignoreCase = true) })
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(0.92f).padding(horizontal = Spacing.lg)) {
            SheetTitle(stringResource(R.string.board_title))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.board_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), contentPadding = PaddingValues(vertical = Spacing.md)) {
                item { FilterChip(selected = category == null, onClick = { category = null }, label = { Text(stringResource(R.string.board_all)) }) }
                items(content.categories, key = { it.id }) { c ->
                    FilterChip(selected = category == c.id, onClick = { category = c.id }, label = { Text(c.label.pick(lang) ?: c.id) })
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.weight(1f),
            ) {
                items(cards, key = { it.id }) { card ->
                    PictureCard(
                        cardId = card.id,
                        label = card.label.pick(lang),
                        selected = card.id in chosen,
                        onClick = { if (card.id in chosen) chosen.remove(card.id) else chosen.add(card.id) },
                        minSize = 104.dp,
                    )
                }
            }
            Button(
                onClick = { onUse(chosen.toList()) },
                enabled = chosen.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = Spacing.md).heightIn(min = 52.dp),
            ) { Text(stringResource(R.string.board_use, chosen.size)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GestureSheet(content: ContentBundle, lang: String, onPick: (LexiconEntry) -> Unit, onDismiss: () -> Unit) {
    var location by rememberSaveable { mutableStateOf<BodyLocation?>(null) }
    var action by rememberSaveable { mutableStateOf<HandAction?>(null) }
    val results = content.lookup(location, action)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(
            Modifier.fillMaxHeight(0.92f),
            contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item { SheetTitle(stringResource(R.string.gl_title)) }
            item { SectionLabel(stringResource(R.string.gl_where)) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    BodyMap(location, onSelect = { location = it }, selectedLabel = location?.let { stringResource(it.label()) })
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    BodyLocation.entries.forEach { l ->
                        FilterChip(selected = location == l, onClick = { location = if (location == l) null else l }, label = { Text(stringResource(l.label())) })
                    }
                }
            }
            item { SectionLabel(stringResource(R.string.gl_what)) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    HandAction.entries.forEach { a ->
                        FilterChip(selected = action == a, onClick = { action = if (action == a) null else a }, label = { Text(stringResource(a.label())) })
                    }
                }
            }
            if (location != null || action != null) {
                item { SectionLabel(stringResource(R.string.gl_results)) }
                if (results.isEmpty()) {
                    item { Text(stringResource(R.string.gl_none), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(results, key = { it.id }) { entry ->
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        ListItem(
                            headlineContent = { Text(entry.label.pick(lang) ?: entry.id) },
                            supportingContent = { Text(entry.how.pick(lang) ?: entry.how["en"].orEmpty()) },
                            leadingContent = { CardImage(entry.card, Modifier.size(48.dp)) },
                            trailingContent = { TextButton(onClick = { onPick(entry) }) { Text(stringResource(R.string.gl_show_back)) } },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogSheet(events: List<SessionEvent>, onAddNote: (String) -> Unit, onDismiss: () -> Unit) {
    var note by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(0.92f).padding(horizontal = Spacing.lg).imePadding()) {
            SheetTitle(stringResource(R.string.officer_log))
            if (events.isEmpty()) {
                Text(stringResource(R.string.log_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            } else {
                LazyColumn(Modifier.weight(1f), reverseLayout = true) {
                    items(events.withQuestions().asReversed(), key = { it.first.id }) { (e, question) ->
                        EventRow(e, question)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text(stringResource(R.string.log_note_hint)) },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { onAddNote(note); note = "" },
                    enabled = note.isNotBlank(),
                    modifier = Modifier.padding(start = Spacing.sm),
                ) { Text(stringResource(R.string.log_add)) }
            }
        }
    }
}

/** Pairs each answer with the question shown before it, so the log reads "Water? Yes" rather than a bare "Yes". */
fun List<SessionEvent>.withQuestions(): List<Pair<SessionEvent, String?>> {
    var question: String? = null
    return map { e ->
        when (e.type) {
            EventType.QUESTION_SHOWN -> question = e.text
            EventType.SYSTEM -> question = null
            else -> Unit
        }
        e to if (e.type in AnswerEvents) question else null
    }
}

val AnswerEvents = setOf(EventType.ANSWER_CARD, EventType.ANSWER_DRAWING, EventType.ANSWER_GESTURE, EventType.ANSWER_UNCLEAR, EventType.LANGUAGE_CHOSEN)

@Composable
fun EventRow(e: SessionEvent, question: String? = null, modifier: Modifier = Modifier) {
    ListItem(
        overlineContent = {
            Text("${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(e.ts))}  ·  ${stringResource(e.type.label())}")
        },
        headlineContent = {
            Text(
                when (e.text) {
                    SYSTEM_LANGUAGE_FINDER -> stringResource(R.string.officer_find_language)
                    SYSTEM_DRAWING -> stringResource(R.string.officer_draw)
                    else -> e.text ?: "—"
                },
            )
        },
        supportingContent = question?.takeIf { it != e.text }?.let { q -> { Text(q, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        leadingContent = e.cardId?.let { id -> { CardImage(id, Modifier.size(40.dp)) } },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeSheet(
    content: ContentBundle,
    profile: Profile,
    subjectLang: String,
    onProfile: (Profile) -> Unit,
    onLanguage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = Spacing.lg).navigationBarsPadding().padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SheetTitle(stringResource(R.string.officer_change))
            Column(Modifier.selectableGroup()) {
                Profile.entries.forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .selectable(selected = p == profile, onClick = { onProfile(p) }, role = Role.RadioButton),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = p == profile, onClick = null)
                        Text(stringResource(profileTitle(p)), modifier = Modifier.padding(start = Spacing.md))
                    }
                }
            }
            SectionLabel(stringResource(R.string.start_language))
            LanguagePicker(content, subjectLang, onPick = onLanguage)
        }
    }
}

@Composable
fun SheetTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(bottom = Spacing.md).semantics { heading() },
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.semantics { heading() },
    )
}

fun BodyLocation.label() = when (this) {
    BodyLocation.HEAD -> R.string.loc_head
    BodyLocation.FOREHEAD -> R.string.loc_forehead
    BodyLocation.EYES -> R.string.loc_eyes
    BodyLocation.NOSE -> R.string.loc_nose
    BodyLocation.EAR -> R.string.loc_ear
    BodyLocation.FACE -> R.string.loc_face
    BodyLocation.MOUTH -> R.string.loc_mouth
    BodyLocation.CHIN -> R.string.loc_chin
    BodyLocation.CHEST -> R.string.loc_chest
    BodyLocation.BELLY -> R.string.loc_belly
    BodyLocation.ARM -> R.string.loc_arm
    BodyLocation.HANDS -> R.string.loc_hands
    BodyLocation.AWAY -> R.string.loc_away
}

fun HandAction.label() = when (this) {
    HandAction.TOUCH -> R.string.act_touch
    HandAction.STROKE -> R.string.act_stroke
    HandAction.POINT -> R.string.act_point
    HandAction.CIRCLE -> R.string.act_circle
    HandAction.FLAT_HAND -> R.string.act_flat
    HandAction.FIST -> R.string.act_fist
    HandAction.BOTH_HANDS -> R.string.act_both
    HandAction.MOVE -> R.string.act_move
    HandAction.HEAD_MOVE -> R.string.act_head
}

fun EventType.label() = when (this) {
    EventType.QUESTION_SHOWN -> R.string.log_shown
    EventType.ANSWER_CARD -> R.string.log_answer
    EventType.ANSWER_DRAWING -> R.string.log_drawing
    EventType.ANSWER_GESTURE -> R.string.log_gesture
    EventType.ANSWER_UNCLEAR -> R.string.log_unclear
    EventType.LANGUAGE_CHOSEN -> R.string.log_language
    EventType.NOTE -> R.string.log_note
    EventType.SYSTEM -> R.string.log_system
}
