package app.signsetu.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
object Spacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
}

/** Minimum size of a subject-facing card (NFR-20). */
val SubjectCardMin: Dp = 112.dp

val LocalAnswerColors = staticCompositionLocalOf { AnswerLight }

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// System fonts: Android ships Noto for every Indian script, so nothing is bundled.
private val OfficerType = Typography()

// Subject captions are read at arm's length by people who may read slowly: larger and looser than the default scale.
private val SubjectType = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium),
        headlineSmall = headlineSmall.copy(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium),
        titleLarge = titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
        labelLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    )
}

@Composable
fun SignSetuTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAnswerColors provides if (dark) AnswerDark else AnswerLight) {
        MaterialTheme(
            colorScheme = if (dark) OfficerDark else OfficerLight,
            typography = OfficerType,
            shapes = AppShapes,
            content = content,
        )
    }
}

@Composable
fun SubjectTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAnswerColors provides if (dark) AnswerDark else AnswerLight) {
        MaterialTheme(
            colorScheme = if (dark) SubjectDark else SubjectLight,
            typography = SubjectType,
            shapes = AppShapes,
            content = content,
        )
    }
}
