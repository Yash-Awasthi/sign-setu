package app.signsetu.ui.components

import androidx.compose.runtime.mutableStateOf

/** Whether the person hears the caption spoken aloud. The officer's mute button sits on the main surface, never in a
 * menu (owner: "not deep hidden to the policeman"), and every player reads this, so a line already playing stops. */
object SubjectSpeech {
    val muted = mutableStateOf(false)

    fun set(muted: Boolean) { this.muted.value = muted }
}
