package app.signsetu.ui.session

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import app.signsetu.ui.components.PictureCard
import app.signsetu.ui.theme.Spacing

/** One side sheet with the whole toolset on a single screen: language, pictures, drawing, emoji and typing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StageToolSheet(
    open: Boolean,
    initial: StageTool?,
    content: ContentBundle,
    lang: String,
    pictureCards: List<String>,
    onLanguage: (String) -> Unit,
    onCard: (String) -> Unit,
    onDraw: (() -> Unit)?,
    onWrite: (() -> Unit)?,
    onExpress: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!open) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("stage.sheet")
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(stringResource(R.string.stage_sheet_title), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("stage.sheet.close")) {
                Text(stringResource(R.string.stage_sheet_close))
            }

            SheetSection(StageTool.LANGUAGE, initial) {
                SectionLabel(stringResource(R.string.stage_sheet_language))
                content.languages.forEach { language ->
                    Surface(
                        onClick = { onLanguage(language.code) },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("stage.sheet.lang." + language.code),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(language.name, style = MaterialTheme.typography.bodyLarge)
                            Text(language.english, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            SheetSection(StageTool.PICTURES, initial) {
                SectionLabel(stringResource(R.string.stage_sheet_pictures))
                pictureCards.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        row.forEach { card ->
                            PictureCard(card, content.cards[card]?.label?.get(lang), onClick = { onCard(card) }, modifier = Modifier.weight(1f), minSize = 96.dp)
                        }
                    }
                }
            }

            SheetSection(StageTool.DRAWING, initial) {
                SectionLabel(stringResource(R.string.stage_sheet_drawing))
                if (onDraw != null) {
                    SheetButton(stringResource(R.string.stage_sheet_drawing), "stage.sheet.drawing.action", onDraw)
                } else {
                    Text(stringResource(R.string.stage_sheet_drawing_unavailable), style = MaterialTheme.typography.bodyMedium)
                }
            }

            SheetSection(StageTool.EMOJI, initial) {
                SectionLabel(stringResource(R.string.stage_sheet_emoji))
                SheetButton(stringResource(R.string.stage_sheet_express), "stage.sheet.express", onExpress)
            }

            SheetSection(StageTool.TYPING, initial) {
                SectionLabel(stringResource(R.string.stage_sheet_typing))
                if (onWrite != null) {
                    SheetButton(stringResource(R.string.stage_sheet_typing), "stage.sheet.typing.action", onWrite)
                } else {
                    Text(stringResource(R.string.stage_sheet_typing_unavailable), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** One of the five groups, tagged so it can be counted on the one screen. */
@Composable
private fun SheetSection(tool: StageTool, initial: StageTool?, content: @Composable () -> Unit) {
    val highlighted = tool == initial
    Surface(
        color = if (highlighted) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().testTag("stage.sheet." + tool.key),
    ) {
        Column(Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            content()
        }
    }
}

@Composable
private fun SheetButton(label: String, tag: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag),
    ) {
        Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Start)
        }
    }
}
