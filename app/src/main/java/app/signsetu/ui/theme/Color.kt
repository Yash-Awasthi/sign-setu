package app.signsetu.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Officer side: deep teal ink on warm paper. Fixed palette (no dynamic colour) so every phone looks the same in
// training and in the field.
internal val OfficerLight = lightColorScheme(
    primary = Color(0xFF1C6E74),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDEDEA),
    onPrimaryContainer = Color(0xFF00201F),
    inversePrimary = Color(0xFF86D4D3),
    secondary = Color(0xFF8A5A00),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDDA8),
    onSecondaryContainer = Color(0xFF2C1800),
    tertiary = Color(0xFF9C4434),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDAD3),
    onTertiaryContainer = Color(0xFF3E0500),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFFBF6EF),
    onBackground = Color(0xFF1F1B16),
    surface = Color(0xFFFBF6EF),
    onSurface = Color(0xFF1F1B16),
    surfaceVariant = Color(0xFFEFE3D3),
    onSurfaceVariant = Color(0xFF4F4539),
    outline = Color(0xFF817567),
    outlineVariant = Color(0xFFD7C9B8),
    inverseSurface = Color(0xFF34302A),
    inverseOnSurface = Color(0xFFF8EFE6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F0E6),
    surfaceContainer = Color(0xFFF2EADF),
    surfaceContainerHigh = Color(0xFFECE4D8),
    surfaceContainerHighest = Color(0xFFE6DDD0),
)

internal val OfficerDark = darkColorScheme(
    primary = Color(0xFF86D4D3),
    onPrimary = Color(0xFF003736),
    primaryContainer = Color(0xFF00504F),
    onPrimaryContainer = Color(0xFFCDEDEA),
    inversePrimary = Color(0xFF1C6E74),
    secondary = Color(0xFFFFB94C),
    onSecondary = Color(0xFF482A00),
    secondaryContainer = Color(0xFF673E00),
    onSecondaryContainer = Color(0xFFFFDDA8),
    tertiary = Color(0xFFFFB4A6),
    onTertiary = Color(0xFF5F160A),
    tertiaryContainer = Color(0xFF7E2B1F),
    onTertiaryContainer = Color(0xFFFFDAD3),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF16130F),
    onBackground = Color(0xFFEAE1D7),
    surface = Color(0xFF16130F),
    onSurface = Color(0xFFEAE1D7),
    surfaceVariant = Color(0xFF4F4539),
    onSurfaceVariant = Color(0xFFD3C4B3),
    outline = Color(0xFF9C8F7F),
    outlineVariant = Color(0xFF4F4539),
    inverseSurface = Color(0xFFEAE1D7),
    inverseOnSurface = Color(0xFF34302A),
    surfaceContainerLowest = Color(0xFF110E0A),
    surfaceContainerLow = Color(0xFF1F1B16),
    surfaceContainer = Color(0xFF231F1A),
    surfaceContainerHigh = Color(0xFF2E2924),
    surfaceContainerHighest = Color(0xFF39342E),
)

/** The officer header band: always deep ink, in both themes, so the officer side is never mistaken for the stage. */
@Immutable
data class InkColors(val band: Color, val onBand: Color, val onBandMuted: Color, val chip: Color, val chipSelected: Color, val onChipSelected: Color)

internal val Ink = InkColors(
    band = Color(0xFF143C44),
    onBand = Color(0xFFF4FBFA),
    onBandMuted = Color(0xFFA9C9CA),
    chip = Color(0xFF1F4E57),
    chipSelected = Color(0xFFFFC56B),
    onChipSelected = Color(0xFF2C1800),
)

/** The subject stage: a warm, calm room for the character. Stays warm at night, only dimmer. */
@Immutable
data class StageColors(
    val skyTop: Color,
    val skyBottom: Color,
    val ground: Color,
    val glow: Color,
    val bubble: Color,
    val onBubble: Color,
    val window: Color,
    val windowFrame: Color,
    val onWindow: Color,
    val handle: Color,
    val onHandle: Color,
    /** Wordmark and icons drawn straight on the backdrop. */
    val title: Color,
)

internal val StageLight = StageColors(
    skyTop = Color(0xFFFFDDBE),
    skyBottom = Color(0xFFFFF3E6),
    ground = Color(0xFFF6D2AE),
    glow = Color(0xFFFFE9A8),
    bubble = Color(0xFFFFFFFF),
    onBubble = Color(0xFF2B2118),
    window = Color(0xFF2E2A33),
    windowFrame = Color(0xFFFFFFFF),
    onWindow = Color(0xFFF5EFE8),
    handle = Color(0x33000000),
    onHandle = Color(0xFF5B4C3E),
    title = Color(0xFF143C44),
)

internal val StageDark = StageColors(
    skyTop = Color(0xFF4A3830),
    skyBottom = Color(0xFF2C221D),
    ground = Color(0xFF5A4336),
    glow = Color(0xFF6B5134),
    bubble = Color(0xFFFFF6EC),
    onBubble = Color(0xFF2B2118),
    window = Color(0xFF1A1719),
    windowFrame = Color(0xFF6B5A4E),
    onWindow = Color(0xFFF5EFE8),
    handle = Color(0x33FFFFFF),
    onHandle = Color(0xFFE7D8C8),
    title = Color(0xFFF6E7D6),
)

/** Behind every symbol, in both themes: the symbols have dark outlines drawn for a light ground. */
val PictureWell = Color(0xFFFFF8EF)

/** Answer colours: also told apart by icon and position, never by colour alone (NFR-21). */
@Immutable
data class AnswerColors(
    val yes: Color,
    val onYes: Color,
    val yesContainer: Color,
    val onYesContainer: Color,
    val no: Color,
    val onNo: Color,
    val noContainer: Color,
    val onNoContainer: Color,
    val unsure: Color,
    val onUnsure: Color,
    val pause: Color,
    val onPause: Color,
    val selection: Color,
    val onSelection: Color,
)

internal val AnswerLight = AnswerColors(
    yes = Color(0xFF1F7F54),
    onYes = Color(0xFFFFFFFF),
    yesContainer = Color(0xFFD5F2E2),
    onYesContainer = Color(0xFF0B3D25),
    no = Color(0xFFC4513F),
    onNo = Color(0xFFFFFFFF),
    noContainer = Color(0xFFFFDDD5),
    onNoContainer = Color(0xFF4A110A),
    unsure = Color(0xFFE9E4FB),
    onUnsure = Color(0xFF2C2461),
    pause = Color(0xFFFFE7BF),
    onPause = Color(0xFF3D2700),
    selection = Color(0xFF1C6E74),
    onSelection = Color(0xFFFFFFFF),
)

internal val AnswerDark = AnswerColors(
    yes = Color(0xFF3FAF7B),
    onYes = Color(0xFF00210F),
    yesContainer = Color(0xFF0E4E31),
    onYesContainer = Color(0xFFD5F2E2),
    no = Color(0xFFE07A66),
    onNo = Color(0xFF3E0500),
    noContainer = Color(0xFF6E2418),
    onNoContainer = Color(0xFFFFDDD5),
    unsure = Color(0xFF3A3170),
    onUnsure = Color(0xFFE9E4FB),
    pause = Color(0xFF5C3F00),
    onPause = Color(0xFFFFE7BF),
    selection = Color(0xFF86D4D3),
    onSelection = Color(0xFF003736),
)
