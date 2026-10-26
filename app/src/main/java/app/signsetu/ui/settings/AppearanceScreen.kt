package app.signsetu.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.Alignment
import app.signsetu.ui.theme.Spacing
import app.signsetu.ui.theme.amoled
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.signsetu.R
import app.signsetu.data.settings.ColorTheme
import app.signsetu.data.settings.SettingsStore
import app.signsetu.data.settings.ThemeMode
import app.signsetu.ui.theme.paletteOf
import kotlinx.coroutines.launch

/** Light or dark, pure black for OLED, and the colour theme, kept off the main settings list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(store: SettingsStore, onBack: () -> Unit) {
    val loaded by store.settings.collectAsStateWithLifecycle(initialValue = null)
    val s = loaded ?: return
    val scope = rememberCoroutineScope()
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
                title = { Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Group(R.string.settings_theme_mode) {
                ThemeMode.entries.forEach { mode ->
                    Choice(stringResource(themeName(mode)), s.themeMode == mode) { scope.launch { store.setThemeMode(mode) } }
                }
                // Pure black only changes a dark screen, so the switch shows only while the app is dark.
                if (dark) SwitchItem(R.string.settings_amoled, R.string.settings_amoled_summary, s.amoled) { on -> scope.launch { store.setAmoled(on) } }
            }
            Group(R.string.settings_color_theme) {
                LazyRow(
                    contentPadding = PaddingValues(Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest),
                ) {
                    items(ColorTheme.entries) { theme ->
                        val palette = paletteOf(theme)
                        val scheme = if (!dark) palette.light else if (s.amoled) palette.dark.amoled() else palette.dark
                        ThemePreview(stringResource(colorName(theme)), scheme, s.colorTheme == theme) { scope.launch { store.setColorTheme(theme) } }
                    }
                }
            }
        }
    }
}

/** A small picture of the app in a colour theme: bar, two cards and a navigation bar, as Mihon shows its themes. */
@Composable
private fun ThemePreview(label: String, c: ColorScheme, selected: Boolean, onPick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.width(92.dp).selectable(selected, role = Role.RadioButton, onClick = onPick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Column(
            Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(shape).background(c.background)
                .border(if (selected) 3.dp else 1.dp, if (selected) c.primary else c.outlineVariant, shape)
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.width(36.dp).height(6.dp).clip(CircleShape).background(c.onBackground))
                Box(Modifier.weight(1f))
                Box(Modifier.size(8.dp).clip(CircleShape).background(c.primary))
            }
            repeat(2) {
                Column(
                    Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(6.dp)).background(c.surfaceContainer).padding(5.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Box(Modifier.fillMaxWidth(0.8f).height(4.dp).clip(CircleShape).background(c.onSurface))
                    Box(Modifier.fillMaxWidth(0.5f).height(4.dp).clip(CircleShape).background(c.onSurfaceVariant))
                    Box(Modifier.weight(1f))
                    Box(Modifier.width(26.dp).height(8.dp).clip(CircleShape).background(c.primary))
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(c.surfaceContainerHigh).padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Box(Modifier.width(14.dp).height(6.dp).clip(CircleShape).background(c.secondaryContainer))
                Box(Modifier.size(6.dp).clip(CircleShape).background(c.onSurfaceVariant))
                Box(Modifier.size(6.dp).clip(CircleShape).background(c.onSurfaceVariant))
            }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onPick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { RadioButton(selected = selected, onClick = null) },
        colors = ItemColors,
        modifier = Modifier.selectable(selected, role = Role.RadioButton, onClick = onPick),
    )
}

@StringRes
internal fun themeName(mode: ThemeMode) = when (mode) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

@StringRes
internal fun colorName(theme: ColorTheme) = when (theme) {
    ColorTheme.EMERALD -> R.string.theme_emerald
    ColorTheme.MOCHA -> R.string.theme_mocha
    ColorTheme.CERULEAN -> R.string.theme_cerulean
    ColorTheme.AMETHYST -> R.string.theme_amethyst
    ColorTheme.CONTRAST -> R.string.theme_contrast
    ColorTheme.TEAL_TURQUOISE -> R.string.theme_teal_turquoise
    ColorTheme.TIDAL_WAVE -> R.string.theme_tidal_wave
    ColorTheme.TOKYO_NIGHT -> R.string.theme_tokyo_night
    ColorTheme.YIN_YANG -> R.string.theme_yin_yang
    ColorTheme.YOTSUBA -> R.string.theme_yotsuba
}
