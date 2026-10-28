package app.signsetu.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.statusBarsPadding
import app.signsetu.ui.theme.Spacing
import app.signsetu.ui.theme.LocalInk
import app.signsetu.ui.theme.Baloo
import app.signsetu.R
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Arrangement

/** A placeholder block with a moving highlight. The phase is read at draw time, so it never recomposes. */
@Composable
fun Modifier.shimmer(): Modifier {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val light = MaterialTheme.colorScheme.surfaceContainerLowest
    val phase = rememberInfiniteTransition(label = "shimmer")
        .animateFloat(-1f, 2f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    return background(base).drawWithContent {
        val x = size.width * phase.value
        drawRect(Brush.horizontalGradient(listOf(base, light, base), startX = x - size.width / 2, endX = x + size.width / 2))
    }
}

@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, height: Int = 56) {
    Box(modifier.fillMaxWidth().height(height.dp).clipRounded().shimmer())
}

private fun Modifier.clipRounded() = this.then(Modifier.clip(RoundedCornerShape(16.dp)))

/** Shown while content or the database opens, laid out as the start screen so nothing jumps when it arrives:
 * the ink header with the name in the middle, where the start screen's intro picks it up. */
@Composable
fun ScreenSkeleton() {
    val ink = LocalInk.current
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        Surface(color = Color.Transparent, contentColor = ink.onBand, shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp), shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.background(ink.brush).statusBarsPadding().height(230.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.app_name), fontFamily = Baloo, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.graphicsLayer { scaleX = 1.6f; scaleY = 1.6f })
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = Spacing.lg).padding(top = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SkeletonBlock(height = 88)
            SkeletonBlock(Modifier.fillMaxWidth(0.4f), height = 24)
            SkeletonBlock(height = 64)
        }
        SkeletonBlock(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md), height = 56)
    }
}
