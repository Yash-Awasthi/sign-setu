package app.signsetu

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import app.signsetu.data.settings.ColorTheme
import app.signsetu.ui.theme.amoled
import app.signsetu.ui.theme.paletteOf
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** WCAG contrast for every text pair in every theme, in the light scheme, the dark scheme and the OLED dark
 * scheme: 4.5:1 for text, 3:1 for the outlines that carry a control's edge. The table this prints is published
 * in docs/26-theme-contrast.md, so a palette change that lowers a ratio fails here. */
class ContrastTest {

    private fun channel(v: Float): Double = if (v <= 0.03928f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)

    private fun luminance(c: Color): Double = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun ratio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** A translucent colour drawn over an opaque one, as the eye receives it. */
    private fun over(fg: Color, bg: Color): Color {
        val a = fg.alpha
        return Color(fg.red * a + bg.red * (1 - a), fg.green * a + bg.green * (1 - a), fg.blue * a + bg.blue * (1 - a))
    }

    private data class Pair(val name: String, val fg: Color, val bg: Color, val min: Double)

    private fun textPairs(s: ColorScheme) = listOf(
        Pair("onBackground/background", s.onBackground, s.background, 4.5),
        Pair("onSurface/surface", s.onSurface, s.surface, 4.5),
        Pair("onSurfaceVariant/surfaceVariant", s.onSurfaceVariant, s.surfaceVariant, 4.5),
        Pair("onSurfaceVariant/surface", s.onSurfaceVariant, s.surface, 4.5),
        Pair("onSurface/surfaceContainer", s.onSurface, s.surfaceContainer, 4.5),
        Pair("onPrimary/primary", s.onPrimary, s.primary, 4.5),
        Pair("onPrimaryContainer/primaryContainer", s.onPrimaryContainer, s.primaryContainer, 4.5),
        Pair("onSecondary/secondary", s.onSecondary, s.secondary, 4.5),
        Pair("onSecondaryContainer/secondaryContainer", s.onSecondaryContainer, s.secondaryContainer, 4.5),
        Pair("onTertiary/tertiary", s.onTertiary, s.tertiary, 4.5),
        Pair("onTertiaryContainer/tertiaryContainer", s.onTertiaryContainer, s.tertiaryContainer, 4.5),
        Pair("onError/error", s.onError, s.error, 4.5),
        Pair("onErrorContainer/errorContainer", s.onErrorContainer, s.errorContainer, 4.5),
        Pair("inverseOnSurface/inverseSurface", s.inverseOnSurface, s.inverseSurface, 4.5),
    )

    private fun outlinePairs(s: ColorScheme) = listOf(
        Pair("outline/surface", s.outline, s.surface, 3.0),
        Pair("outlineVariant/surface", s.outlineVariant, s.surface, 1.0),
    )

    private fun schemes(): List<Triple<String, String, ColorScheme>> = ColorTheme.entries.flatMap { theme ->
        val p = paletteOf(theme)
        listOf(
            Triple(theme.name, "light", p.light),
            Triple(theme.name, "dark", p.dark),
            Triple(theme.name, "oled", p.dark.amoled()),
        )
    }

    @Test
    fun everyTextPairReachesItsRatioInEveryThemeAndMode() {
        val rows = mutableListOf<String>()
        val failures = mutableListOf<String>()
        for ((theme, mode, scheme) in schemes()) {
            for (pair in textPairs(scheme) + outlinePairs(scheme)) {
                val r = ratio(pair.fg, pair.bg)
                val line = "$theme $mode ${pair.name} %.2f:1 (min %.1f)".format(r, pair.min)
                rows += line
                if (r < pair.min) failures += line
            }
        }
        println("CONTRAST TABLE")
        rows.forEach(::println)
        assertTrue("pairs under their ratio:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun theStageBandTextIsReadableOnItsBand() {
        val failures = mutableListOf<String>()
        for (theme in ColorTheme.entries) {
            val p = paletteOf(theme)
            for ((mode, ink) in listOf("light" to p.inkLight, "dark" to p.inkDark)) {
                val band = (ink.brush as SolidColor).value
                val name = ratio(ink.onBand, band)
                val muted = ratio(over(ink.onBandMuted, band), band)
                println("$theme $mode band onBand %.2f:1, onBandMuted %.2f:1".format(name, muted))
                if (name < 4.5) failures += "$theme $mode onBand %.2f".format(name)
                if (muted < 4.5) failures += "$theme $mode onBandMuted %.2f".format(muted)
            }
        }
        assertTrue("band text under 4.5:1:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun theDarkStageCallColoursStayReadable() {
        val failures = mutableListOf<String>()
        for (theme in ColorTheme.entries) {
            val c = paletteOf(theme).call
            for (pair in listOf(
                Pair("text/bg", c.text, c.bg, 4.5),
                Pair("muted/bg", c.muted, c.bg, 4.5),
                Pair("accent/bg", c.accent, c.bg, 4.5),
                // accentSoft is drawn as text on the call background (VideoCallStage rights notice).
                Pair("accentSoft/bg", c.accentSoft, c.bg, 4.5),
            )) {
                val r = ratio(pair.fg, pair.bg)
                println("$theme call ${pair.name} %.2f:1".format(r))
                if (r < pair.min) failures += "$theme call ${pair.name} %.2f".format(r)
            }
        }
        assertTrue("stage colours under 4.5:1:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
