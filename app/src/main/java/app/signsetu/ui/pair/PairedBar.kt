package app.signsetu.ui.pair

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import app.signsetu.ui.session.clockTime
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalResources
import app.signsetu.data.session.EventType
import app.signsetu.ui.session.signedDisplay
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import app.signsetu.pair.LinkState
import app.signsetu.ui.session.AnswerEvents
import app.signsetu.ui.session.Mode
import app.signsetu.ui.session.SessionUi
import app.signsetu.ui.session.describe
import app.signsetu.ui.theme.Space
import app.signsetu.ui.theme.StageTones

/** Above the officer's page while paired: the device, what is on it, the last answer, and any alert. */
@Composable
fun PairedBar(pane: PairedPane, ui: SessionUi, content: ContentBundle, officerLang: String) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        val s = pane.status
        val low = s != null && s.battery < 15 && !s.charging
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (low) StageTones.lowBattery else colors.secondaryContainer,
            contentColor = if (low) StageTones.onLowBattery else colors.onSecondaryContainer,
            modifier = Modifier.fillMaxWidth().heightIn(min = Space.s48),
        ) {
            Row(Modifier.padding(horizontal = Space.md), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Phone, contentDescription = null, modifier = Modifier.size(Space.s20))
                Text(
                    if (s != null) stringResource(R.string.second_screen_chip, pane.label, s.battery) else pane.label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = Space.sm),
                )
            }
        }
        when {
            pane.unpinned -> Alert(stringResource(R.string.second_screen_unpinned), stringResource(R.string.second_screen_use_this) to pane.onUseThisPhone)
            pane.state is LinkState.Ended -> Alert(stringResource(R.string.second_screen_ended), stringResource(R.string.second_screen_use_this) to pane.onUseThisPhone)
            pane.state == LinkState.Lost -> Alert(
                stringResource(R.string.second_screen_lost),
                stringResource(R.string.second_screen_reconnect) to pane.onReconnect,
                stringResource(R.string.second_screen_use_this) to pane.onUseThisPhone,
            )
        }
        if (pane.breakPending) Alert(stringResource(R.string.second_screen_break), stringResource(R.string.second_screen_break_given) to pane.onBreakGiven)
        // The stage reported it could not resolve or show the item; the last real reading stays in the chip above.
        pane.lastError?.let { Alert(stringResource(R.string.link_display_not_confirmed)) }
        pane.askedAt?.let {
            Text(stringResource(R.string.second_screen_asked_before, clockTime(it)), style = MaterialTheme.typography.bodySmall, color = colors.tertiary)
        }
        if (ui.mode == Mode.SHOW) {
            Surface(shape = MaterialTheme.shapes.medium, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                Row(Modifier.fillMaxWidth().padding(start = Space.md), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = Space.sm)) {
                        Text(
                            stringResource(R.string.second_screen_on, ui.onStage?.let { describe(it, content, officerLang) } ?: stringResource(R.string.second_screen_idle)),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val res = LocalResources.current
                        ui.events.lastOrNull { it.via != null && it.type in AnswerEvents }?.let { e -> if (e.type == EventType.ANSWER_SIGNED) signedDisplay(res, e.text) else e.text }?.let {
                            Text(stringResource(R.string.second_screen_last_answer, it), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    TextButton(onClick = pane.onDone) { Text(stringResource(R.string.second_screen_done)) }
                }
            }
        }
    }
}

@Composable
private fun Alert(text: String, vararg actions: Pair<String, () -> Unit>) {
    val colors = MaterialTheme.colorScheme
    // Announced as it appears: a lost or ended second screen must reach an officer using TalkBack.
    Surface(shape = MaterialTheme.shapes.medium, color = colors.errorContainer, contentColor = colors.onErrorContainer, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(Modifier.fillMaxWidth().padding(start = Space.md, end = Space.xs, top = Space.sm)) {
            Text(text, style = MaterialTheme.typography.titleSmall)
            Row(Modifier.align(Alignment.End)) {
                actions.forEach { (label, onClick) -> TextButton(onClick = onClick) { Text(label, color = colors.onErrorContainer) } }
            }
        }
    }
}
