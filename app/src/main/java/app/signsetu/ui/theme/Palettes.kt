package app.signsetu.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import app.signsetu.data.settings.ColorTheme

// Officer-side colour themes. Each has a light and a dark scheme; every text pair was checked for contrast
// (body text 7:1 on background, other text 4.5:1, outlines 3:1). Fixed colours, never dynamic, so every phone
// looks the same in training and in the field.

/** The dark call stage always uses its theme's dark scheme, whatever the officer's light or dark setting. */
@Immutable
data class CallColors(
    val bg: Color, val surface: Color, val line: Color, val muted: Color, val text: Color,
    val accent: Color, val accentDeep: Color, val accentSoft: Color, val ink: Color,
)

@Immutable
class Palette(val light: ColorScheme, val dark: ColorScheme, val inkLight: InkColors, val inkDark: InkColors) {
    val call = CallColors(
        bg = dark.background, surface = dark.surfaceContainer, line = dark.outlineVariant, muted = dark.onSurfaceVariant,
        text = dark.onBackground, accent = dark.primary, accentDeep = dark.primaryContainer,
        accentSoft = dark.onPrimaryContainer, ink = (inkDark.brush as SolidColor).value,
    )
}

private fun ink(c: Color) = InkColors(onBand = Color.White, onBandMuted = Color(0xBFFFFFFF), brush = SolidColor(c))

/** A borrowed scheme; white band text needs a darker band, so the ink is its primary taken toward black. */
internal fun mihon(light: ColorScheme, dark: ColorScheme) =
    Palette(light, dark, ink(lerp(light.primary, Color.Black, 0.35f)), ink(lerp(dark.primary, Color.Black, 0.6f)))

/** Dark scheme on pure black for OLED screens; containers stay just off black so scrolled content still separates. */
internal fun ColorScheme.amoled() = copy(
    background = Color.Black, surface = Color.Black, surfaceVariant = Color(0xFF0C0C0C),
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF070707), surfaceContainer = Color(0xFF0C0C0C),
    surfaceContainerHigh = Color(0xFF131313), surfaceContainerHighest = Color(0xFF1B1B1B),
)

private val EmeraldPalette = Palette(
    light = lightColorScheme(
        primary = Color(0xFF3A6E58),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD9E8DF),
        onPrimaryContainer = Color(0xFF0E2A1C),
        inversePrimary = Color(0xFF7CC4A0),
        secondary = Color(0xFF5E5632),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFEAE2C6),
        onSecondaryContainer = Color(0xFF2E2A14),
        tertiary = Color(0xFFA4452B),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFADBCB),
        onTertiaryContainer = Color(0xFF4E1B0C),
        error = Color(0xFFB3261E),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
        background = Color(0xFFF7F1E6),
        onBackground = Color(0xFF1F1B16),
        surface = Color(0xFFFAF6EF),
        onSurface = Color(0xFF1F1B16),
        surfaceVariant = Color(0xFFE8E2D7),
        onSurfaceVariant = Color(0xFF5B5750),
        outline = Color(0xFF827D76),
        outlineVariant = Color(0xFFD4CFC5),
        inverseSurface = Color(0xFF302C27),
        inverseOnSurface = Color(0xFFF7F1E6),
        surfaceContainerLowest = Color(0xFFFDFBF8),
        surfaceContainerLow = Color(0xFFFAF6EF),
        surfaceContainer = Color(0xFFEEE8DE),
        surfaceContainerHigh = Color(0xFFE4DED3),
        surfaceContainerHighest = Color(0xFFD7D1C7),
    ),
    dark = darkColorScheme(
        primary = Color(0xFF7CC4A0),
        onPrimary = Color(0xFF00391C),
        primaryContainer = Color(0xFF1E4C37),
        onPrimaryContainer = Color(0xFFC9E6D5),
        inversePrimary = Color(0xFF3A6E58),
        secondary = Color(0xFFC8D3B0),
        onSecondary = Color(0xFF253019),
        secondaryContainer = Color(0xFF2A3326),
        onSecondaryContainer = Color(0xFFD9DFC5),
        tertiary = Color(0xFFFFB59A),
        onTertiary = Color(0xFF4A1A0A),
        tertiaryContainer = Color(0xFF6A2D17),
        onTertiaryContainer = Color(0xFFFFDCC8),
        error = Color(0xFFF2B8B5),
        onError = Color(0xFF601410),
        errorContainer = Color(0xFF8C1D18),
        onErrorContainer = Color(0xFFF9DEDC),
        background = Color(0xFF0B0F0D),
        onBackground = Color(0xFFE8EBE6),
        surface = Color(0xFF0B0F0D),
        onSurface = Color(0xFFE8EBE6),
        surfaceVariant = Color(0xFF1B2420),
        onSurfaceVariant = Color(0xFFA9B5AE),
        outline = Color(0xFF6B7A72),
        outlineVariant = Color(0xFF2E3A35),
        inverseSurface = Color(0xFFE8EBE6),
        inverseOnSurface = Color(0xFF1B2420),
        surfaceContainerLowest = Color(0xFF121915),
        surfaceContainerLow = Color(0xFF141B18),
        surfaceContainer = Color(0xFF19221E),
        surfaceContainerHigh = Color(0xFF232E29),
        surfaceContainerHighest = Color(0xFF2D3934),
    ),
    inkLight = ink(Color(0xFF3A6E58)),
    inkDark = ink(Color(0xFF2A6A50)),
)

private val MochaPalette = Palette(
    light = lightColorScheme(
        primary = Color(0xFF5B4632),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFF4D9A0),
        onPrimaryContainer = Color(0xFF3A2A12),
        inversePrimary = Color(0xFFF4D9A0),
        secondary = Color(0xFF6D5A2A),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFF1E4C0),
        onSecondaryContainer = Color(0xFF3A2F10),
        tertiary = Color(0xFF9A5A14),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFF9DDB8),
        onTertiaryContainer = Color(0xFF4A2A05),
        error = Color(0xFFB3261E),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
        background = Color(0xFFFBF5EC),
        onBackground = Color(0xFF2A1F16),
        surface = Color(0xFFFCF8F3),
        onSurface = Color(0xFF2A1F16),
        surfaceVariant = Color(0xFFECE6DD),
        onSurfaceVariant = Color(0xFF655B52),
        outline = Color(0xFF8A8178),
        outlineVariant = Color(0xFFDAD3CA),
        inverseSurface = Color(0xFF3B3027),
        inverseOnSurface = Color(0xFFFBF5EC),
        surfaceContainerLowest = Color(0xFFFEFCF9),
        surfaceContainerLow = Color(0xFFFCF8F3),
        surfaceContainer = Color(0xFFF3ECE3),
        surfaceContainerHigh = Color(0xFFE8E2D9),
        surfaceContainerHighest = Color(0xFFDCD5CC),
    ),
    dark = darkColorScheme(
        primary = Color(0xFFF4D9A0),
        onPrimary = Color(0xFF0A0A0A),
        primaryContainer = Color(0xFF5B4632),
        onPrimaryContainer = Color(0xFFF9EACB),
        inversePrimary = Color(0xFF5B4632),
        secondary = Color(0xFFD8C39A),
        onSecondary = Color(0xFF0A0A0A),
        secondaryContainer = Color(0xFF3B3123),
        onSecondaryContainer = Color(0xFFE8DABF),
        tertiary = Color(0xFFE8A04F),
        onTertiary = Color(0xFF0A0A0A),
        tertiaryContainer = Color(0xFF6A3F10),
        onTertiaryContainer = Color(0xFFFBE0BE),
        error = Color(0xFFF2B8B5),
        onError = Color(0xFF601410),
        errorContainer = Color(0xFF8C1D18),
        onErrorContainer = Color(0xFFF9DEDC),
        background = Color(0xFF15100C),
        onBackground = Color(0xFFEDE3D6),
        surface = Color(0xFF15100C),
        onSurface = Color(0xFFEDE3D6),
        surfaceVariant = Color(0xFF26211C),
        onSurfaceVariant = Color(0xFFA89F95),
        outline = Color(0xFF676059),
        outlineVariant = Color(0xFF38322C),
        inverseSurface = Color(0xFFEDE3D6),
        inverseOnSurface = Color(0xFF26211C),
        surfaceContainerLowest = Color(0xFF1B1612),
        surfaceContainerLow = Color(0xFF201B16),
        surfaceContainer = Color(0xFF26211C),
        surfaceContainerHigh = Color(0xFF312B26),
        surfaceContainerHighest = Color(0xFF3C3630),
    ),
    inkLight = ink(Color(0xFF5B4632)),
    inkDark = ink(Color(0xFF7A5C3E)),
)

private val CeruleanPalette = Palette(
    light = lightColorScheme(
        primary = Color(0xFF00607F),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFC8E8F5),
        onPrimaryContainer = Color(0xFF00222F),
        inversePrimary = Color(0xFF5CC8EE),
        secondary = Color(0xFF4A5B66),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFDDE6EB),
        onSecondaryContainer = Color(0xFF1B2A33),
        tertiary = Color(0xFFB3402A),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFDAD2),
        onTertiaryContainer = Color(0xFF450F05),
        error = Color(0xFFB3261E),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
        background = Color(0xFFF3F7F9),
        onBackground = Color(0xFF12202A),
        surface = Color(0xFFF7FAFB),
        onSurface = Color(0xFF12202A),
        surfaceVariant = Color(0xFFE3E8EB),
        onSurfaceVariant = Color(0xFF515C64),
        outline = Color(0xFF7A8389),
        outlineVariant = Color(0xFFCFD5D8),
        inverseSurface = Color(0xFF24313B),
        inverseOnSurface = Color(0xFFF3F7F9),
        surfaceContainerLowest = Color(0xFFFBFDFD),
        surfaceContainerLow = Color(0xFFF7FAFB),
        surfaceContainer = Color(0xFFEAEEF1),
        surfaceContainerHigh = Color(0xFFDFE4E6),
        surfaceContainerHighest = Color(0xFFD1D7DA),
    ),
    dark = darkColorScheme(
        primary = Color(0xFF5CC8EE),
        onPrimary = Color(0xFF0A0A0A),
        primaryContainer = Color(0xFF004D66),
        onPrimaryContainer = Color(0xFFC8EDFB),
        inversePrimary = Color(0xFF00607F),
        secondary = Color(0xFFB4C4CD),
        onSecondary = Color(0xFF0A0A0A),
        secondaryContainer = Color(0xFF242E34),
        onSecondaryContainer = Color(0xFFD4E0E6),
        tertiary = Color(0xFFFF8F7A),
        onTertiary = Color(0xFF0A0A0A),
        tertiaryContainer = Color(0xFF6B2417),
        onTertiaryContainer = Color(0xFFFFDAD2),
        error = Color(0xFFF2B8B5),
        onError = Color(0xFF601410),
        errorContainer = Color(0xFF8C1D18),
        onErrorContainer = Color(0xFFF9DEDC),
        background = Color(0xFF0A0A0A),
        onBackground = Color(0xFFE7ECEF),
        surface = Color(0xFF0A0A0A),
        onSurface = Color(0xFFE7ECEF),
        surfaceVariant = Color(0xFF1C1C1C),
        onSurfaceVariant = Color(0xFFA0A4A6),
        outline = Color(0xFF5E6061),
        outlineVariant = Color(0xFF2D2E2F),
        inverseSurface = Color(0xFFE7ECEF),
        inverseOnSurface = Color(0xFF1C1C1C),
        surfaceContainerLowest = Color(0xFF111111),
        surfaceContainerLow = Color(0xFF151515),
        surfaceContainer = Color(0xFF1C1C1C),
        surfaceContainerHigh = Color(0xFF272728),
        surfaceContainerHighest = Color(0xFF323333),
    ),
    inkLight = ink(Color(0xFF00607F)),
    inkDark = ink(Color(0xFF00709A)),
)

private val AmethystPalette = Palette(
    light = lightColorScheme(
        primary = Color(0xFF4B1E86),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE5D8FA),
        onPrimaryContainer = Color(0xFF24083F),
        inversePrimary = Color(0xFFC9A9F5),
        secondary = Color(0xFF5A4B73),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE9E1F5),
        onSecondaryContainer = Color(0xFF2A1F3D),
        tertiary = Color(0xFF00697A),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFC4F2F8),
        onTertiaryContainer = Color(0xFF002A32),
        error = Color(0xFFB3261E),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
        background = Color(0xFFF8F5FC),
        onBackground = Color(0xFF1D1626),
        surface = Color(0xFFFAF8FD),
        onSurface = Color(0xFF1D1626),
        surfaceVariant = Color(0xFFE9E5ED),
        onSurfaceVariant = Color(0xFF5A5462),
        outline = Color(0xFF827D88),
        outlineVariant = Color(0xFFD5D1DA),
        inverseSurface = Color(0xFF2F2837),
        inverseOnSurface = Color(0xFFF8F5FC),
        surfaceContainerLowest = Color(0xFFFDFCFE),
        surfaceContainerLow = Color(0xFFFAF8FD),
        surfaceContainer = Color(0xFFEFECF3),
        surfaceContainerHigh = Color(0xFFE4E1E9),
        surfaceContainerHighest = Color(0xFFD7D4DC),
    ),
    dark = darkColorScheme(
        primary = Color(0xFFC9A9F5),
        onPrimary = Color(0xFF0A0A0A),
        primaryContainer = Color(0xFF3B0A6B),
        onPrimaryContainer = Color(0xFFE9DAFC),
        inversePrimary = Color(0xFF4B1E86),
        secondary = Color(0xFFC6B8DD),
        onSecondary = Color(0xFF0A0A0A),
        secondaryContainer = Color(0xFF2A2238),
        onSecondaryContainer = Color(0xFFE2D8F2),
        tertiary = Color(0xFF62E6F5),
        onTertiary = Color(0xFF0A0A0A),
        tertiaryContainer = Color(0xFF004A55),
        onTertiaryContainer = Color(0xFFC4F6FC),
        error = Color(0xFFF2B8B5),
        onError = Color(0xFF601410),
        errorContainer = Color(0xFF8C1D18),
        onErrorContainer = Color(0xFFF9DEDC),
        background = Color(0xFF060509),
        onBackground = Color(0xFFECE6F4),
        surface = Color(0xFF060509),
        onSurface = Color(0xFFECE6F4),
        surfaceVariant = Color(0xFF18171C),
        onSurfaceVariant = Color(0xFFA29EA9),
        outline = Color(0xFF5D5A62),
        outlineVariant = Color(0xFF2B292F),
        inverseSurface = Color(0xFFECE6F4),
        inverseOnSurface = Color(0xFF18171C),
        surfaceContainerLowest = Color(0xFF0D0C10),
        surfaceContainerLow = Color(0xFF121015),
        surfaceContainer = Color(0xFF18171C),
        surfaceContainerHigh = Color(0xFF242228),
        surfaceContainerHighest = Color(0xFF2F2E33),
    ),
    inkLight = ink(Color(0xFF4B1E86)),
    inkDark = ink(Color(0xFF7140B8)),
)

// Black and white with white outlines; colour is left to icons and the primary accent.
private val ContrastScheme = darkColorScheme(
    primary = Color(0xFFFFE14D), onPrimary = Color.Black, primaryContainer = Color.Black, onPrimaryContainer = Color.White,
    inversePrimary = Color(0xFFFFE14D),
    secondary = Color(0xFF7DE3FF), onSecondary = Color.Black, secondaryContainer = Color.Black, onSecondaryContainer = Color.White,
    tertiary = Color(0xFFFFB36B), onTertiary = Color.Black, tertiaryContainer = Color.Black, onTertiaryContainer = Color.White,
    error = Color(0xFFFF6B6B), onError = Color.Black, errorContainer = Color.Black, onErrorContainer = Color.White,
    background = Color.Black, onBackground = Color.White, surface = Color.Black, onSurface = Color.White,
    surfaceVariant = Color.Black, onSurfaceVariant = Color.White, outline = Color.White, outlineVariant = Color.White,
    inverseSurface = Color.White, inverseOnSurface = Color.Black,
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color.Black, surfaceContainer = Color.Black,
    surfaceContainerHigh = Color.Black, surfaceContainerHighest = Color.Black,
)
private val ContrastPalette = Palette(ContrastScheme, ContrastScheme, InkColors(Color.White, Color.White, SolidColor(Color.Black)), InkColors(Color.White, Color.White, SolidColor(Color.Black)))

internal fun paletteOf(theme: ColorTheme): Palette = when (theme) {
    ColorTheme.EMERALD -> EmeraldPalette
    ColorTheme.MOCHA -> MochaPalette
    ColorTheme.CERULEAN -> CeruleanPalette
    ColorTheme.AMETHYST -> AmethystPalette
    ColorTheme.CONTRAST -> ContrastPalette
    ColorTheme.TEAL_TURQUOISE -> MihonTealTurqoise
    ColorTheme.TIDAL_WAVE -> MihonTidalWave
    ColorTheme.TOKYO_NIGHT -> MihonTokyoNight
    ColorTheme.YIN_YANG -> MihonYinYang
    ColorTheme.YOTSUBA -> MihonYotsuba
}
