package app.signsetu.ui.settings

import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.ListItemDefaults
import app.signsetu.ui.components.OfficerCard
import app.signsetu.ui.session.SectionLabel
import androidx.compose.foundation.layout.ColumnScope
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import kotlin.math.roundToInt
import androidx.compose.material3.Switch
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.signsetu.R
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import app.signsetu.data.settings.InterpreterApp
import app.signsetu.ui.theme.Spacing

@StringRes
internal fun appName(app: InterpreterApp) = when (app) {
    InterpreterApp.NONE -> R.string.settings_not_set
    InterpreterApp.WHATSAPP -> R.string.settings_whatsapp
    InterpreterApp.SANDES -> R.string.settings_sandes
}

@Composable
internal fun Group(@StringRes title: Int, content: @Composable ColumnScope.() -> Unit) {
    SectionLabel(stringResource(title), Modifier.padding(start = Spacing.xxl, end = Spacing.lg, top = Spacing.xl, bottom = Spacing.sm))
    OfficerCard(Modifier.padding(horizontal = Spacing.lg), padding = 0.dp) {
        val divider = MaterialTheme.colorScheme.outlineVariant
        // Opaque items 1 dp apart over a line drawn from 16 dp: inset dividers without one per call site.
        Column(
            Modifier.drawBehind { drawRect(divider, topLeft = Offset(Spacing.lg.toPx(), 0f)) },
            verticalArrangement = Arrangement.spacedBy(1.dp),
            content = content,
        )
    }
}

@Composable
internal fun SpeedItem(speed: Float, onChange: (Float) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_gesture_speed, speed)) },
        supportingContent = {
            Slider(
                value = speed,
                onValueChange = { onChange((it * 4).roundToInt() / 4f) },
                valueRange = 0.25f..1.5f,
                steps = 4,
            )
        },
        colors = ItemColors,
    )
}

@Composable
internal fun SwitchItem(@StringRes title: Int, @StringRes summary: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(summary)) },
        colors = ItemColors,
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(checked, role = Role.Switch, onValueChange = onChange),
    )
}

@Composable
internal fun LanguageItem(@StringRes title: Int, @StringRes summary: Int, @StringRes badge: Int, warn: Boolean) {
    val colors = MaterialTheme.colorScheme
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(summary)) },
        colors = ItemColors,
        trailingContent = {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (warn) colors.errorContainer else colors.primaryContainer,
                contentColor = if (warn) colors.onErrorContainer else colors.onPrimaryContainer,
            ) {
                Text(stringResource(badge), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        },
    )
}

internal val ItemColors @Composable get() = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)

@Composable
internal fun Item(title: String, summary: String, note: String? = null, destructive: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    ListItem(
        headlineContent = { Text(title, color = if (destructive) colors.error else colors.onSurface) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (summary.isNotEmpty()) Text(summary)
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
            }
        },
        colors = ItemColors,
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}
