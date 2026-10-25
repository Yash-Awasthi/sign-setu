package app.signsetu.ui.session

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Answers that ask the officer to stop and look: three short pulses. */
private val AttentionCards = setOf("stop", "break", "dont_know", "dont_understand", "wrong")

/** Vibration felt by whoever holds the phone: one long for Yes, two for No, three short for a request, a tick otherwise. */
internal fun buzzPattern(answeredYes: Boolean?, answeredCard: String?): LongArray? = when {
    answeredYes == true -> longArrayOf(0, 400)
    answeredYes == false -> longArrayOf(0, 120, 120, 120)
    answeredCard in AttentionCards -> longArrayOf(0, 80, 100, 80, 100, 80)
    answeredCard != null -> longArrayOf(0, 30)
    else -> null
}

internal fun buzz(context: Context, pattern: LongArray) {
    val vibrator = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
    if (vibrator?.hasVibrator() == true) vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
}
