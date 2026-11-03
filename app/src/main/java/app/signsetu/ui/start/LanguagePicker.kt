package app.signsetu.ui.start

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import app.signsetu.ui.components.GuardedAlertDialog
import app.signsetu.ui.theme.Spacing

/** The subject's language: a field that opens a list. A dialog, not a dropdown menu, so seventeen languages
 * never open upward over the header or misplace inside a bottom sheet. With [onUnknown], "Do not know" heads the list. */
@Composable
fun LanguagePicker(
    content: ContentBundle,
    selected: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    unknown: Boolean = false,
    onUnknown: (() -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    val current = content.language(selected)
    val unknownText = stringResource(R.string.start_language_unknown)
    val text = if (unknown) unknownText else current?.let { "${it.name}  ·  ${it.english}" } ?: selected
    Surface(
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClickLabel = label) { open = true }
            .semantics { label?.let { stateDescription = text } },
    ) {
        Row(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_translate), contentDescription = null)
            Column(Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                label?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
    }
    if (open) {
        GuardedAlertDialog(
            onDismissRequest = { open = false },
            title = label?.let { { Text(it) } },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    onUnknown?.let { pick ->
                        item(key = "unknown") {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(MaterialTheme.shapes.small)
                                    .selectable(unknown, role = Role.RadioButton) { pick(); open = false }
                                    .padding(horizontal = Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(unknownText, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                                if (unknown) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    items(content.languages, key = { it.code }) { l ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(MaterialTheme.shapes.small)
                                .selectable(!unknown && l.code == selected, role = Role.RadioButton) { onPick(l.code); open = false }
                                .padding(horizontal = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${l.name}  ·  ${l.english}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            if (!unknown && l.code == selected) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
