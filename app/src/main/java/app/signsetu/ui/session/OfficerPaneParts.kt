package app.signsetu.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.SignMode
import app.signsetu.data.session.Profile
import app.signsetu.ui.components.CardImage
import app.signsetu.ui.theme.LocalAnswerColors
import app.signsetu.ui.theme.Spacing

@Composable
internal fun StopBanner(onOk: () -> Unit) {
    val answer = LocalAnswerColors.current
    Surface(shape = MaterialTheme.shapes.large, color = answer.pause, contentColor = answer.onPause, shadowElevation = 4.dp, modifier = Modifier.padding(top = Spacing.md).semantics { liveRegion = LiveRegionMode.Polite }) {
        Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            CardImage("stop", Modifier.size(40.dp))
            Column(Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                Text(stringResource(R.string.officer_stop_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.officer_stop_body), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onOk, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.ok), color = answer.onPause) }
        }
    }
}

internal fun typedModes(modes: List<SignMode>, islReorder: Boolean) = if (islReorder) modes.toSet() else modes.toSet() + SignMode.TYPED_ORDER

@Composable
internal fun describe(p: Pending, content: ContentBundle, lang: String): String = when (p) {
    is Pending.Ask -> p.question.text.get(lang) ?: p.question.id
    is Pending.FreeText -> if (p.cardIds.isEmpty()) p.text else "${p.text}  ·  ${pluralStringResource(R.plurals.officer_pictures, p.cardIds.size, p.cardIds.size)}"
    is Pending.Cards -> p.cardIds.joinToString { content.cards[it]?.label?.get(lang) ?: it }
    is Pending.ShowBack -> "${stringResource(R.string.officer_gesture)}: ${p.entry.label.get(lang) ?: p.entry.id}"
    is Pending.PhraseAsk -> p.slot.text.get(lang) ?: p.slot.card
    Pending.LanguageFinder -> stringResource(R.string.officer_find_language)
    Pending.Drawing -> stringResource(R.string.officer_draw)
    is Pending.GroundRules -> stringResource(R.string.officer_rules)
}

/** Profiles that sign get ISL signs and spelling; everyone else gets everyday gestures. Both get pictures. */
internal fun defaultModes(profile: Profile) =
    if (profile == Profile.ISL_USER) listOf(SignMode.SIGNS, SignMode.SPELL, SignMode.PICTURES) else listOf(SignMode.GESTURES, SignMode.PICTURES)

@Composable
internal fun AslExperimentalBanner(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.xs),
    ) {
        Row(
            Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.asl_experimental_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.asl_experimental_advisory),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

enum class BackAction { FIRST_TAB, ASK_LEAVE, LEAVE }

/** What Back does on the officer page: step to the first tab, ask before dropping a second screen, or just leave. */
fun backAction(page: Int, secondScreenShowing: Boolean) = when {
    page != 0 -> BackAction.FIRST_TAB
    secondScreenShowing -> BackAction.ASK_LEAVE
    else -> BackAction.LEAVE
}
