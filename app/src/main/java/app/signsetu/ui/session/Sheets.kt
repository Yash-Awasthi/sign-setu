package app.signsetu.ui.session

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height
import app.signsetu.ui.components.BrandButton
import app.signsetu.ui.theme.LocalStageColors
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.signsetu.R
import androidx.compose.runtime.snapshots.SnapshotStateList
import app.signsetu.data.content.BodyLocation
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.HandAction
import app.signsetu.data.content.LexiconEntry
import app.signsetu.data.content.Phrase
import app.signsetu.data.content.PhraseSlot
import app.signsetu.ui.components.CardImage
import app.signsetu.ui.components.PictureCard
import app.signsetu.ui.start.LanguagePicker
import app.signsetu.ui.theme.Spacing
import app.signsetu.ui.components.FilterObscuredTouches
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.toMutableStateList
import app.signsetu.sign.SignSegment
import app.signsetu.sign.SkeletonPlayer
import app.signsetu.sign.rememberReplayState
import kotlin.math.roundToInt

private const val FAVOURITES = "*favourites"

/** Keeps a picked or typed list across rotation and process death. */
internal fun <T : Any> stateListSaver() = listSaver<SnapshotStateList<T>, T>(save = { it.toList() }, restore = { it.toMutableStateList() })

/** Fills a phrase-bank sentence: the officer picks what it is about. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhraseSheet(phrase: Phrase, content: ContentBundle, lang: String, onPick: (PhraseSlot) -> Unit, onDismiss: () -> Unit) {
    OfficerSheet(onDismiss) {
        Column(Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.lg)) {
            SheetTitle(phrase.label.get(lang) ?: phrase.id)
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                contentPadding = PaddingValues(top = Spacing.md),
            ) {
                items(phrase.slots, key = { it.card }) { slot ->
                    PictureCard(slot.card, content.cards[slot.card]?.label?.get(lang), onClick = { onPick(slot) }, minSize = 104.dp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardSheet(
    content: ContentBundle,
    lang: String,
    initial: List<String>,
    favourites: Set<String>,
    onFavourite: (String) -> Unit,
    onUse: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    onDraft: (List<String>) -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    // Favourites open first once the officer has starred any.
    var category by rememberSaveable { mutableStateOf(if (favourites.isNotEmpty()) FAVOURITES else null) }
    val chosen = rememberSaveable(saver = stateListSaver()) { initial.toMutableStateList() }
    val cards = content.cards.values.filter { c ->
        (category == null || c.category == category || (category == FAVOURITES && c.id in favourites)) &&
            (query.isBlank() || c.label.values.any { it.contains(query.trim(), ignoreCase = true) })
    }
    OfficerSheet({ onDraft(chosen.toList()); onDismiss() }) {
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
                item {
                    FilterChip(
                        selected = category == FAVOURITES,
                        onClick = { category = FAVOURITES },
                        label = { Text(stringResource(R.string.board_favourites)) },
                        leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
                item { FilterChip(selected = category == null, onClick = { category = null }, label = { Text(stringResource(R.string.board_all)) }) }
                items(content.categories, key = { it.id }) { c ->
                    FilterChip(selected = category == c.id, onClick = { category = c.id }, label = { Text(c.label.get(lang) ?: c.id) })
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.weight(1f),
            ) {
                items(cards, key = { it.id }) { card ->
                    Box {
                        PictureCard(
                            cardId = card.id,
                            label = card.label.get(lang),
                            selected = card.id in chosen,
                            onClick = { if (card.id in chosen) chosen.remove(card.id) else chosen.add(card.id) },
                            minSize = 104.dp,
                        )
                        val starred = card.id in favourites
                        IconToggleButton(
                            checked = starred,
                            onCheckedChange = { onFavourite(card.id) },
                            modifier = Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 6.dp).size(48.dp),
                        ) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = stringResource(R.string.board_favourite),
                                // Both at least 3:1 on the picture well.
                                tint = if (starred) Color(0xFFB86E00) else Color(0xFF8A7760),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
            BrandButton(
                onClick = { onUse(chosen.toList()) },
                enabled = chosen.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = Spacing.md).height(52.dp),
            ) { Text(stringResource(R.string.board_use, chosen.size)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GestureSheet(content: ContentBundle, lang: String, onPick: (LexiconEntry, List<LexiconEntry>) -> Unit, onDismiss: () -> Unit) {
    var location by rememberSaveable { mutableStateOf<BodyLocation?>(null) }
    var action by rememberSaveable { mutableStateOf<HandAction?>(null) }
    val results = content.lookup(location, action)
    OfficerSheet(onDismiss) {
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
                            headlineContent = { Text(entry.label.get(lang) ?: entry.id) },
                            supportingContent = { Text(entry.how.get(lang) ?: entry.how["en"].orEmpty()) },
                            leadingContent = { GesturePreview(entry) },
                            trailingContent = { TextButton(onClick = { onPick(entry, results) }) { Text(stringResource(R.string.gl_show_back)) } },
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
fun ChangeSheet(content: ContentBundle, subjectLang: String, onLanguage: (String) -> Unit, onDismiss: () -> Unit) {
    OfficerSheet(onDismiss, expanded = false) {
        Column(Modifier.padding(horizontal = Spacing.lg).navigationBarsPadding().padding(bottom = Spacing.xl)) {
            SheetTitle(stringResource(R.string.start_language))
            LanguagePicker(content, subjectLang, onPick = onLanguage)
        }
    }
}

/** Every officer sheet: overlay touches filtered, and Material's 32 dp drag handle in a 48 dp touch area. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficerSheet(onDismiss: () -> Unit, expanded: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = expanded),
        dragHandle = { Box(Modifier.sizeIn(minWidth = 48.dp), contentAlignment = Alignment.Center) { BottomSheetDefaults.DragHandle() } },
        content = {
            FilterObscuredTouches()
            TextButton(onClick = onDismiss, modifier = Modifier.padding(horizontal = Spacing.sm)) { Text(stringResource(R.string.back)) }
            content()
        },
    )
}

@Composable
fun SheetTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(bottom = Spacing.lg).semantics { heading() },
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

/** Replays a signed answer slowly, sign by sign, so the officer can accept a guess or type the word for each
 * sign. Without a sign reader every sign starts blank: the officer transcribes by watching. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SignedSheet(
    segments: List<SignSegment>,
    onAccept: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    onAsk: ((List<String>) -> Unit)? = null,
    canAsk: (List<String>) -> Boolean = { true },
    draft: List<String>? = null,
    onDraft: (List<String>) -> Unit = {},
) {
    val replay = rememberReplayState(segments)
    val words = rememberSaveable(segments, saver = stateListSaver()) {
        (draft?.takeIf { it.size == segments.size } ?: segments.map { s -> s.word?.takeIf { it != "?" }.orEmpty() }).toMutableStateList()
    }
    val colors = MaterialTheme.colorScheme
    OfficerSheet({ onDraft(words.toList()); onDismiss() }) {
        LazyColumn(
            Modifier.fillMaxHeight(0.92f).imePadding(),
            contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item { SheetTitle(stringResource(R.string.log_signed)) }
            item { SkeletonPlayer(replay, Modifier.fillMaxWidth()) }
            if (segments.all { it.guesses.isNullOrEmpty() }) {
                item { Text(stringResource(R.string.signed_manual), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
            }
            item { Text(stringResource(R.string.signed_teach), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
            itemsIndexed(segments) { i, s ->
                val current = replay.segment == i
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (current) colors.surfaceContainerHigh else colors.surfaceContainerLow,
                    border = if (current) BorderStroke(2.dp, colors.primary) else null,
                ) {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val name = stringResource(R.string.signed_sign, i + 1)
                            TextButton(onClick = { replay.play(i) }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(name, modifier = Modifier.padding(start = Spacing.xs))
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            s.guesses.orEmpty().forEach { g ->
                                FilterChip(selected = words[i] == g.word, onClick = { words[i] = g.word }, label = { Text("${g.word}  ${(g.score * 100).roundToInt()}%") })
                            }
                        }
                        OutlinedTextField(
                            value = words[i],
                            onValueChange = { words[i] = it },
                            placeholder = { Text(stringResource(R.string.signed_type)) },
                            singleLine = true,
                            // the keyboard should not learn from what is transcribed about a subject.
                            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item {
                BrandButton(
                    onClick = { onAccept(words.map { it.trim().ifEmpty { "?" } }) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(stringResource(R.string.signed_accept)) }
            }
            // Only the subject's own pick makes a reading their answer.
            onAsk?.let { ask ->
                item {
                    val possible = canAsk(words.toList())
                    OutlinedButton(onClick = { ask(words.toList()) }, enabled = possible, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.signed_ask)) }
                    Text(stringResource(if (possible) R.string.signed_ask_hint else R.string.signed_ask_none), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
                }
            }
        }
    }
}

/** The picture a gesture stands for; the character signs it once it is shown back. */
@Composable
private fun GesturePreview(entry: LexiconEntry) {
    Surface(shape = RoundedCornerShape(16.dp), color = LocalStageColors.current.skyTop, modifier = Modifier.size(72.dp)) {
        CardImage(entry.card, Modifier.padding(10.dp))
    }
}
