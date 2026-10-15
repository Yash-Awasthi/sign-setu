package app.signsetu.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Officer panel: cool, quiet teal. Fixed palette (no dynamic colour) so every phone looks the same in training and in the field.
internal val OfficerLight = lightColorScheme(
    primary = Color(0xFF1B5E6B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC9E8EE),
    onPrimaryContainer = Color(0xFF062A31),
    secondary = Color(0xFF4D6268),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3E5EA),
    onSecondaryContainer = Color(0xFF0A1F24),
    tertiary = Color(0xFF7A5A1E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF6DFB8),
    onTertiaryContainer = Color(0xFF2A1A00),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF7FAFB),
    onBackground = Color(0xFF171D1E),
    surface = Color(0xFFF7FAFB),
    onSurface = Color(0xFF171D1E),
    surfaceVariant = Color(0xFFDBE4E6),
    onSurfaceVariant = Color(0xFF3F484A),
    outline = Color(0xFF6F797B),
    outlineVariant = Color(0xFFBFC8CA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F4F5),
    surfaceContainer = Color(0xFFEBEFF0),
    surfaceContainerHigh = Color(0xFFE5E9EA),
    surfaceContainerHighest = Color(0xFFDFE3E4),
)

internal val OfficerDark = darkColorScheme(
    primary = Color(0xFF8FD0DD),
    onPrimary = Color(0xFF00363F),
    primaryContainer = Color(0xFF004E5A),
    onPrimaryContainer = Color(0xFFC9E8EE),
    secondary = Color(0xFFB4CAD0),
    onSecondary = Color(0xFF1F343A),
    secondaryContainer = Color(0xFF354A50),
    onSecondaryContainer = Color(0xFFD3E5EA),
    tertiary = Color(0xFFE8C27F),
    onTertiary = Color(0xFF422C00),
    tertiaryContainer = Color(0xFF5E4206),
    onTertiaryContainer = Color(0xFFF6DFB8),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF0F1415),
    onBackground = Color(0xFFDEE3E5),
    surface = Color(0xFF0F1415),
    onSurface = Color(0xFFDEE3E5),
    surfaceVariant = Color(0xFF3F484A),
    onSurfaceVariant = Color(0xFFBFC8CA),
    outline = Color(0xFF899294),
    outlineVariant = Color(0xFF3F484A),
    surfaceContainerLowest = Color(0xFF0A0F10),
    surfaceContainerLow = Color(0xFF171D1E),
    surfaceContainer = Color(0xFF1B2122),
    surfaceContainerHigh = Color(0xFF252B2C),
    surfaceContainerHighest = Color(0xFF303637),
)

// Subject panel: warm paper tones, deliberately different from the officer panel so the two states are never confused.
internal val SubjectLight = lightColorScheme(
    primary = Color(0xFF6B4F2A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF3DFC1),
    onPrimaryContainer = Color(0xFF261900),
    secondary = Color(0xFF6A5D4D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF2E1CC),
    onSecondaryContainer = Color(0xFF231A0E),
    background = Color(0xFFFBF7F1),
    onBackground = Color(0xFF1F1B16),
    surface = Color(0xFFFBF7F1),
    onSurface = Color(0xFF1F1B16),
    surfaceVariant = Color(0xFFEEE3D6),
    onSurfaceVariant = Color(0xFF4D4539),
    outline = Color(0xFF7F7567),
    outlineVariant = Color(0xFFE3D9CC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F1E9),
    surfaceContainer = Color(0xFFF2EBE2),
    surfaceContainerHigh = Color(0xFFECE5DC),
    surfaceContainerHighest = Color(0xFFE6E0D6),
)

internal val SubjectDark = darkColorScheme(
    primary = Color(0xFFE3C291),
    onPrimary = Color(0xFF3F2D0B),
    primaryContainer = Color(0xFF574020),
    onPrimaryContainer = Color(0xFFF3DFC1),
    secondary = Color(0xFFD6C4AE),
    onSecondary = Color(0xFF3A2F21),
    secondaryContainer = Color(0xFF524536),
    onSecondaryContainer = Color(0xFFF2E1CC),
    background = Color(0xFF1C1914),
    onBackground = Color(0xFFECE1D6),
    surface = Color(0xFF1C1914),
    onSurface = Color(0xFFECE1D6),
    surfaceVariant = Color(0xFF4A443B),
    onSurfaceVariant = Color(0xFFD1C5B6),
    outline = Color(0xFF9A8F81),
    outlineVariant = Color(0xFF4A443B),
    surfaceContainerLowest = Color(0xFF16130F),
    surfaceContainerLow = Color(0xFF221E19),
    surfaceContainer = Color(0xFF26221C),
    surfaceContainerHigh = Color(0xFF312C26),
    surfaceContainerHighest = Color(0xFF3C3730),
)

/** Answer colours. Yes and No also differ by icon and position, never by colour alone (NFR-21). */
data class AnswerColors(
    val yesContainer: Color,
    val onYesContainer: Color,
    val noContainer: Color,
    val onNoContainer: Color,
    val selection: Color,
)

internal val AnswerLight = AnswerColors(
    yesContainer = Color(0xFFD4F1E0),
    onYesContainer = Color(0xFF0B3D25),
    noContainer = Color(0xFFFADCD5),
    onNoContainer = Color(0xFF4A120A),
    selection = Color(0xFF1D6B46),
)

internal val AnswerDark = AnswerColors(
    yesContainer = Color(0xFF0F5132),
    onYesContainer = Color(0xFFD4F1E0),
    noContainer = Color(0xFF7A2A1E),
    onNoContainer = Color(0xFFFADCD5),
    selection = Color(0xFF8ED8B0),
)
