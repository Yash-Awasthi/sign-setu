package app.signsetu.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import app.signsetu.R
import app.signsetu.data.content.ContentRepository
import app.signsetu.ui.theme.LocalAnswerColors
import app.signsetu.ui.theme.Spacing
import app.signsetu.ui.theme.SubjectCardMin

val LocalContentRepository = staticCompositionLocalOf<ContentRepository> { error("ContentRepository not provided") }

@Composable
fun CardImage(cardId: String, modifier: Modifier = Modifier) {
    val repo = LocalContentRepository.current
    val image by produceState<ImageBitmap?>(null, cardId) { value = repo.cardImage(cardId) }
    Box(modifier, contentAlignment = Alignment.Center) {
        image?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

/** A subject-facing picture card. Large, labelled, and selectable; selection shows a check, not just a colour. */
@Composable
fun PictureCard(
    cardId: String,
    label: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    minSize: Dp = SubjectCardMin,
) {
    val answer = LocalAnswerColors.current
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = minSize)
            .semantics {
                role = Role.Button
                this.selected = selected
                label?.let { contentDescription = it }
            },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = if (selected) BorderStroke(4.dp, answer.selection) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = if (selected) 0.dp else 1.dp,
    ) {
        Box {
            Column(
                Modifier.padding(Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                CardImage(cardId, Modifier.fillMaxWidth().aspectRatio(1f))
                if (label != null) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                }
            }
            if (selected) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = answer.selection,
                    modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.sm).size(28.dp),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(4.dp))
                }
            }
        }
    }
}

/** Yes on the left with a check, No on the right with a cross: position, icon and colour all differ. */
@Composable
fun AnswerButtons(
    yesLabel: String?,
    noLabel: String?,
    onYes: () -> Unit,
    onNo: () -> Unit,
    modifier: Modifier = Modifier,
    chosen: Boolean? = null,
) {
    val answer = LocalAnswerColors.current
    // When the subject language has no word for Yes or No, the buttons are icon-only; screen readers still get a name.
    val yesName = yesLabel ?: stringResource(R.string.show_yes)
    val noName = noLabel ?: stringResource(R.string.show_no)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        AnswerButton(yesLabel, yesName, Icons.Filled.Check, answer.yesContainer, answer.onYesContainer, chosen == true, onYes, Modifier.weight(1f))
        AnswerButton(noLabel, noName, Icons.Filled.Close, answer.noContainer, answer.onNoContainer, chosen == false, onNo, Modifier.weight(1f))
    }
}

@Composable
private fun AnswerButton(
    label: String?,
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    chosen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 96.dp).semantics { contentDescription = name; role = Role.Button },
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = content,
        border = if (chosen) BorderStroke(4.dp, content) else null,
    ) {
        Row(
            Modifier.padding(Spacing.lg),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp))
            if (label != null) {
                Text(label, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = Spacing.md))
            }
        }
    }
}
