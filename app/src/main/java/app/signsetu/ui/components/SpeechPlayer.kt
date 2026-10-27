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
class SpeechPlayer(private val context: Context, private val intact: (String) -> Boolean = { true }) {
    private val player = ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
            true,
        )
    }

    /** [path] is relative to the content bundle, e.g. `audio/hi/n1.ogg`. */
    fun play(path: String, speed: Float = 1f) {
        // A recording that fails its manifest check is never played.
        if (!intact(path)) return
        player.setPlaybackSpeed(speed)
        player.setMediaItem(MediaItem.fromUri("asset:///content/$path"))
        player.prepare()
        player.play()
    }

    /** Speech made on the fly for typed text; held in one cache file that release() deletes. */
    fun playBytes(audio: ByteArray, speed: Float = 1f) {
        val file = java.io.File(context.cacheDir, SPOKEN).apply { writeBytes(audio) }
        player.setPlaybackSpeed(speed)
        player.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
        player.prepare()
        player.play()
    }

    fun stop() = player.stop()

    fun release() {
        player.release()
        java.io.File(context.cacheDir, SPOKEN).delete()
    }
}

@Composable
fun rememberSpeechPlayer(): SpeechPlayer {
    val context = LocalContext.current.applicationContext
    val repo = LocalContentRepository.current
    val player = remember { SpeechPlayer(context, repo::intact) }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

private const val SPOKEN = "spoken.mp3"
