package app.signsetu.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

/** Plays recorded speech from the content bundle. One player per subject screen; never plays unless asked. */
class SpeechPlayer(private val context: Context, private val intact: (String) -> Boolean = { true }) {    private val player = ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
            true,
        )
    }

    /** [path] is relative to the content bundle, e.g. `audio/hi/n1.ogg`. */
    fun play(path: String, speed: Float = 1f) {
        // Muted, or a recording that fails its manifest check: nothing is played.
        if (SubjectSpeech.muted.value || !intact(path)) return
        // A typed line talking through the phone's voice must not run under the recording.
        voice.peek()?.stop()
        player.setPlaybackSpeed(speed)
        player.setMediaItem(MediaItem.fromUri("asset:///content/$path"))
        player.prepare()
        player.play()
    }

    /** Speech made on the fly for typed text; held in one cache file that release() deletes. */
    fun playBytes(audio: ByteArray, speed: Float = 1f) {
        if (SubjectSpeech.muted.value) return
        voice.peek()?.stop()
        val file = java.io.File(context.cacheDir, SPOKEN).apply { writeBytes(audio) }
        player.setPlaybackSpeed(speed)
        player.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
        player.prepare()
        player.play()
    }

    private val voice = LazyResource { OfflineVoice(context) }

    /** Typed text with no recording and no online voice: the phone's own Hindi or English voice, if it has one.
     * True when it is said now or kept until the voice starts; the next recorded clip stops it. */
    fun speakOffline(text: String, lang: String?, speed: Float = 1f): Boolean {
        player.stop()
        if (SubjectSpeech.muted.value) return false
        return voice.get().speak(text, lang, speed)
    }

    fun stop() { player.stop(); voice.peek()?.stop() }

    fun release() {
        player.release()
        voice.take()?.shutdown()
        java.io.File(context.cacheDir, SPOKEN).delete()
    }
}

@Composable
fun rememberSpeechPlayer(): SpeechPlayer {
    val context = LocalContext.current.applicationContext
    val repo = LocalContentRepository.current
    val player = remember { SpeechPlayer(context, repo::intact) }
    DisposableEffect(player) { onDispose { player.release() } }
    // Backgrounding does not dispose the stage; speech would otherwise keep playing behind another app.
    StopOnBackground { player.stop() }
    return player
}

private const val SPOKEN = "spoken.mp3"
