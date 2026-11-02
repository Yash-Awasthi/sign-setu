package app.signsetu.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.signsetu.R
import app.signsetu.data.settings.ColorTheme

@Immutable
object Spacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
}

val SubjectCardMin: Dp = 96.dp

val LocalAnswerColors = staticCompositionLocalOf { AnswerLight }
val LocalStageColors = staticCompositionLocalOf { StageLight }
val LocalInk = staticCompositionLocalOf { paletteOf(ColorTheme.EMERALD).inkLight }
val LocalCall = staticCompositionLocalOf { paletteOf(ColorTheme.EMERALD).call }

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun baloo(weight: Int) = Font(R.font.baloo2, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** Baloo 2 (SIL OFL) carries Latin and Devanagari in one rounded, friendly face for headings and subject
 * captions. Body text stays on the system font; other Indian scripts fall back to the system Noto fonts. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val Baloo = FontFamily(baloo(400), baloo(500), baloo(600), baloo(700), baloo(800))

private val OfficerType = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontFamily = Baloo, fontWeight = FontWeight.Bold),
        headlineLarge = headlineLarge.copy(fontFamily = Baloo, fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontFamily = Baloo, fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontFamily = Baloo, fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontFamily = Baloo, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontFamily = Baloo, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
        labelLarge = labelLarge.copy(fontFamily = Baloo, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    )
}

// Subject captions are read at arm's length by people who may read slowly: larger and looser than the default scale.
private val SubjectType = OfficerType.copy(
    headlineMedium = TextStyle(fontFamily = Baloo, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = Baloo, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp),
    labelLarge = TextStyle(fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 22.sp),
)

val LocalHighContrast = staticCompositionLocalOf { false }

@Composable
fun SignSetuTheme(dark: Boolean = isSystemInDarkTheme(), theme: ColorTheme = ColorTheme.EMERALD, amoled: Boolean = false, content: @Composable () -> Unit) {
    val palette = paletteOf(theme)
    CompositionLocalProvider(
        LocalAnswerColors provides if (dark) AnswerDark else AnswerLight,
        LocalStageColors provides if (dark) StageDark else StageLight,
        LocalInk provides if (dark) palette.inkDark else palette.inkLight,
        LocalCall provides palette.call,
        LocalHighContrast provides (theme == ColorTheme.CONTRAST),
        // Officer screens keep their dense layout; only the touch area around each target grows to 56 dp.
        LocalViewConfiguration provides wideTouch(LocalViewConfiguration.current),
    ) {
        MaterialTheme(
            colorScheme = if (!dark) palette.light else if (amoled) palette.dark.amoled() else palette.dark,
            typography = OfficerType,
            shapes = AppShapes,
            content = content,
        )
    }
}

/** Smallest touch target on the subject's screens: a child's or a shaking hand misses 48 dp targets. */
val SubjectTouchTarget = 56.dp

/** Custom tap targets are expanded to the view configuration's minimum, Material controls to their own local. */
@Composable
private fun wideTouch(base: ViewConfiguration): ViewConfiguration =
    if (base.minimumTouchTargetSize.width >= SubjectTouchTarget) base
    else remember(base) { object : ViewConfiguration by base { override val minimumTouchTargetSize = DpSize(SubjectTouchTarget, SubjectTouchTarget) } }

/** The stage keeps the light scheme in both themes; only its backdrop dims at night. Every Material control on it
 * reserves at least [SubjectTouchTarget] to touch. */
@Composable
fun SubjectTheme(content: @Composable () -> Unit) {
    val touch = wideTouch(LocalViewConfiguration.current)
    val body: @Composable () -> Unit = {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides SubjectTouchTarget, LocalViewConfiguration provides touch) {
            MaterialTheme(colorScheme = StageScheme, typography = SubjectType, shapes = AppShapes, content = content)
        }
    }
    if (LocalHighContrast.current) {
        CompositionLocalProvider(LocalStageColors provides SunStageColors, LocalAnswerColors provides SunAnswerColors, content = body)
    } else {
        body()
    }
}
