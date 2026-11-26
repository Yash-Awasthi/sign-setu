package app.signsetu.ui.session

import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.Question
import app.signsetu.data.content.SignMode
import app.signsetu.data.session.Profile
import app.signsetu.ui.theme.LocalInk
import app.signsetu.ui.theme.Space
import androidx.compose.ui.text.input.VisualTransformation
import app.signsetu.ui.theme.Radius

/** What Send will show, with what the subject will get. */
@Composable
internal fun Ready(pending: Pending, content: ContentBundle, officerLang: String, subjectLang: String, profile: Profile, onClear: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        modifier = Modifier.padding(horizontal = Space.lg).padding(bottom = Space.sm).fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = Space.md, top = Space.sm, bottom = Space.sm), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.chat_ready), style = MaterialTheme.typography.labelSmall, color = colors.primary)
                Text(describe(pending, content, officerLang), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (pending is Pending.Ask) MediaSummary(pending.question, content, subjectLang, profile)
                if (pending is Pending.Ask && content.isRights(pending.question)) {
                    Text(stringResource(R.string.rights_notice_officer), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Space.xs, end = Space.sm))
                }
            }
            IconButton(onClick = onClear) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel)) }
        }
    }
}

@Composable
internal fun Composer(
    text: String,
    onText: (String) -> Unit,
    onPictures: () -> Unit,
    canSend: Boolean,
    onSend: () -> Unit,
    onRead: (() -> Unit)?,
    onVoice: (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val colors = MaterialTheme.colorScheme
    val ink = LocalInk.current
    Row(Modifier.fillMaxWidth().padding(start = Space.sm, end = Space.md, bottom = Space.md), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPictures, modifier = Modifier.size(Space.s48)) {
            Surface(shape = CircleShape, color = colors.secondaryContainer, contentColor = colors.onSecondaryContainer, modifier = Modifier.size(Space.s40)) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(if (text.isEmpty()) R.string.action_board else R.string.officer_add_pictures), modifier = Modifier.padding(Space.sm))
            }
        }
        Surface(shape = RoundedCornerShape(Radius.r24), color = colors.surfaceContainerHigh, modifier = Modifier.weight(1f).padding(horizontal = Space.xs)) {
            BasicTextField(
                value = text,
                onValueChange = onText,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                // the keyboard should not learn or suggest from what is typed about a subject.
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                cursorBrush = SolidColor(colors.primary),
                visualTransformation = visualTransformation,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth().heightIn(min = Space.s48).padding(horizontal = Space.lg, vertical = Space.s13),
                decorationBox = { field ->
                    if (text.isEmpty()) Text(stringResource(R.string.quick_hint), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    field()
                },
            )
        }
        if (text.isEmpty()) onVoice?.let { voice ->
            IconButton(onClick = voice, modifier = Modifier.size(Space.s48)) { Icon(painterResource(R.drawable.ic_mic), contentDescription = stringResource(R.string.voice_title)) }
        }
        // Reading signs is the other half of the conversation, so it sits beside Send; typed text goes first.
        onRead?.let { read ->
            val name = stringResource(R.string.officer_read_signs)
            Surface(
                onClick = read,
                shape = Radius.pill,
                color = colors.tertiaryContainer,
                contentColor = colors.onTertiaryContainer,
                modifier = Modifier.padding(start = Space.xs).height(Space.s48).widthIn(min = Space.s48).semantics { contentDescription = name },
            ) {
                Row(Modifier.padding(horizontal = Space.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(painterResource(R.drawable.ic_hand), contentDescription = null, modifier = Modifier.size(Space.s22))
                    if (text.isEmpty()) Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.padding(start = Space.s6))
                }
            }
        }
        Box(
            Modifier
                .padding(start = Space.xs)
                .size(Space.s48)
                .clip(CircleShape)
                .background(if (canSend) ink.brush else SolidColor(colors.surfaceContainerHighest))
                .clickable(enabled = canSend, role = Role.Button, onClickLabel = stringResource(R.string.officer_show), onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.officer_show), tint = if (canSend) Color.White else colors.onSurfaceVariant)
        }
    }
}

@Composable
internal fun CategoryChip(label: String, selected: Boolean, locked: Boolean, onLongClick: (() -> Unit)? = null, longClickLabel: String? = null, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = Radius.pill,
        color = if (selected) colors.primary else colors.surfaceContainerHigh,
        contentColor = if (selected) colors.onPrimary else colors.onSurfaceVariant,
        modifier = Modifier.minimumInteractiveComponentSize().height(Space.s36).clip(Radius.pill).runnable(onClick, onLongClick, longClickLabel).semantics { this.selected = selected },
    ) {
        Row(Modifier.padding(horizontal = Space.s14), verticalAlignment = Alignment.CenterVertically) {
            if (locked) Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.padding(end = Space.xs).size(Space.s14))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun AssistChip(thinking: Boolean, onClick: () -> Unit) {
    val ink = LocalInk.current
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .height(Space.xxl)
            .clip(Radius.pill)
            .background(ink.brush)
            .clickable(enabled = !thinking, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.md),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (thinking) CircularProgressIndicator(Modifier.size(Space.s14), color = Color.White, strokeWidth = Space.xxs)
            else Icon(Icons.Filled.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(Space.s14))
            Text(stringResource(if (thinking) R.string.ai_working else R.string.ai_suggest), style = MaterialTheme.typography.labelMedium, color = Color.White, modifier = Modifier.padding(start = Space.xs))
        }
    }
}

/** A machine caption shown before Send, always marked as unchecked; [meaning] is the assistant's English back. */
@Composable
internal fun AssistantCard(caption: String, meaning: String?, languageName: String, onDiscard: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer,
        modifier = Modifier.padding(horizontal = Space.lg).padding(bottom = Space.sm).fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = Space.md, top = Space.sm, bottom = Space.sm), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(stringResource(R.string.ai_caption, languageName), style = MaterialTheme.typography.labelSmall, color = colors.tertiary)
                Text(caption, style = MaterialTheme.typography.titleSmall)
                if (meaning != null) {
                    Text(stringResource(R.string.ai_meaning), style = MaterialTheme.typography.labelSmall, color = colors.primary)
                    Text(meaning, style = MaterialTheme.typography.bodyMedium)
                }
            }
            IconButton(onClick = onDiscard) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel)) }
        }
    }
}

@Composable
internal fun ModeChip(label: String, on: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = Radius.pill,
        color = if (on) colors.primary else colors.surfaceContainerHigh,
        contentColor = if (on) colors.onPrimary else colors.onSurfaceVariant,
        modifier = Modifier.minimumInteractiveComponentSize().height(Space.xxl).semantics { selected = on },
    ) {
        Row(Modifier.padding(horizontal = Space.md), verticalAlignment = Alignment.CenterVertically) {
            if (on) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.padding(end = Space.xs).size(Space.s14))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** What the subject will get for this question; anything missing is named before the officer shows it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MediaSummary(question: Question, content: ContentBundle, subjectLang: String, profile: Profile) {
    val items = buildList {
        add(R.string.media_caption to (question.text.get(subjectLang) != null))
        if (profile != Profile.ISL_USER) add(R.string.media_audio to (content.questionAudio(question.id, subjectLang) != null))
        add(R.string.media_gesture to (content.gestureFor(question) != null))
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s6), verticalArrangement = Arrangement.spacedBy(Space.xs), modifier = Modifier.padding(top = Space.xs)) {
        items.forEach { (label, present) ->
            val name = stringResource(label)
            Surface(
                shape = Radius.pill,
                color = if (present) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = if (present) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Row(Modifier.padding(horizontal = Space.s10, vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (present) Icons.Filled.Check else Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(Space.s14))
                    Text(
                        if (present) name else stringResource(R.string.media_missing, name),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = Space.xs),
                    )
                }
            }
        }
    }
}

internal val ModeLabels = listOf(
    SignMode.SIGNS to R.string.mode_signs,
    SignMode.GESTURES to R.string.mode_gestures,
    SignMode.SPELL to R.string.mode_spell,
    SignMode.PICTURES to R.string.mode_pictures,
)
