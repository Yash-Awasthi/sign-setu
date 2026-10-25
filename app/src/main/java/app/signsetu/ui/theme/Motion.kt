package app.signsetu.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** False when the officer chose Reduce animations or the phone has its own animations turned off. Motion that only
 * decorates is skipped then; a progress ring the officer needs to see still runs. */
val LocalMotion = staticCompositionLocalOf { true }

/** [spec] while motion is on, an instant change otherwise. */
@Composable
fun <T> motion(spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> = if (LocalMotion.current) spec else snap()

fun systemAnimationsOn(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
