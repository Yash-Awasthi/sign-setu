package app.signsetu.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import app.signsetu.ui.components.SubjectSpeech
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Plays a recorded answer straight from memory: the WAV's PCM goes to an AudioTrack, so the person's voice is never
 * written to a file to be played. One at a time; a new play stops the last. */
object WavPlayer {
    private var track: AudioTrack? = null
    private var focus: AudioFocusRequest? = null
    private var manager: AudioManager? = null

    /** Plays 16-bit PCM WAV; other formats are skipped, never thrown. */
    @Synchronized
    fun play(context: Context, wav: ByteArray) {
        stop()
        // Muted: the person's speech stays off, a replay of their recorded answer included.
        if (SubjectSpeech.muted.value) return
        if (wav.size <= 44 || String(wav, 0, 4, Charsets.US_ASCII) != "RIFF" || String(wav, 8, 4, Charsets.US_ASCII) != "WAVE") return
        val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        var fmt = -1
        var data = -1
        var dataSize = 0
        // Chunks are walked, not assumed at 44: a WAV with a LIST or extended fmt chunk would play its header as noise.
        var at = 12
        while (at + 8 <= wav.size && (fmt < 0 || data < 0)) {
            val id = String(wav, at, 4, Charsets.US_ASCII)
            val size = b.getInt(at + 4)
            // A streamed WAV leaves the data size unset (0xFFFFFFFF): the data runs to the end.
            if (id == "data") { data = at + 8; dataSize = if (size < 0) wav.size - data else minOf(size, wav.size - data); break }
            if (size < 0) break
            if (id == "fmt " && size >= 16 && at + 24 <= wav.size) fmt = at + 8
            val next = at.toLong() + 8 + size + (size and 1)
            if (next > wav.size) break
            at = next.toInt()
        }
        if (fmt < 0 || data < 0) return
        val channels = b.getShort(fmt + 2).toInt()
        val rate = b.getInt(fmt + 4)
        val bits = b.getShort(fmt + 14).toInt()
        if (channels !in 1..2 || rate !in 4_000..192_000 || bits != 16) return
        // Whole frames only: a trailing half sample makes a static track reject the write.
        val frame = 2 * channels
        val pcm = wav.copyOfRange(data, data + dataSize / frame * frame)
        if (pcm.isEmpty()) return
        // Focus, transient: a recorded answer is like one spoken sentence, not a music session. A call or another
        // player that holds focus wins; no answer plays over it.
        val am = context.applicationContext.getSystemService(AudioManager::class.java) ?: return
        val audio = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(audio)
            .setOnAudioFocusChangeListener { change -> if (change <= 0) stop() }
            .build()
        val granted = runCatching { am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED }.getOrDefault(false)
        if (!shouldPlay(granted, SubjectSpeech.muted.value)) {
            if (granted) runCatching { am.abandonAudioFocusRequest(request) }
            return
        }
        manager = am
        focus = request
        val t = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(audio)
                .setAudioFormat(
                    AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate)
                        .setChannelMask(if (channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO).build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size)
                .build()
        }.onFailure { Log.w("WavPlayer", "no audio track", it) }.getOrNull()
        if (t == null) { pcm.fill(0); abandonFocus(); return }
        // Set before playback: a marker that fires at once still finds its own track, and any late callback from
        // an earlier take can tell it is stale.
        track = t
        // Frees the track when the take ends: otherwise it is only released by the next play, and a session that
        // plays one answer holds a static AudioTrack for the rest of the process.
        val frames = pcm.size / frame
        if (frames > 0) runCatching {
            t.setNotificationMarkerPosition(frames)
            t.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                // Only this take's own marker may stop it: a stale one has already been released by play's stop().
                override fun onMarkerReached(track: AudioTrack?) = releaseIfCurrent(t)
                override fun onPeriodicNotification(track: AudioTrack?) = Unit
            })
        }
        val ok = runCatching { (t.write(pcm, 0, pcm.size) >= 0).also { if (it) t.play() } }.getOrDefault(false)
        pcm.fill(0)
        if (!ok) { track = null; t.release(); abandonFocus(); return }
    }

    @Synchronized
    fun stop() {
        track?.let { runCatching { it.stop() }; runCatching { it.release() } }
        track = null
        abandonFocus()
    }

    @Synchronized
    private fun abandonFocus() {
        focus?.let { request -> manager?.let { am -> runCatching { am.abandonAudioFocusRequest(request) } } }
        focus = null
        manager = null
    }

    /** Releases [fired] only while it is still the track in use; a stale callback leaves the new take alone. */
    @Synchronized
    private fun releaseIfCurrent(fired: AudioTrack) {
        if (!stillCurrent(fired, track)) return
        track = null
        runCatching { fired.stop() }
        runCatching { fired.release() }
        abandonFocus()
    }

    /** Whether a take may start: audio focus must be granted and the person's speech must not be muted. Kept pure so
     * the JVM can test the decision; AudioManager and AudioTrack themselves are device-only. */
    internal fun shouldPlay(focused: Boolean, muted: Boolean) = focused && !muted

    /** The decision [releaseIfCurrent] makes, kept pure so the JVM can test it without an AudioTrack. */
    internal fun stillCurrent(fired: Any?, current: Any?) = fired === current
}
