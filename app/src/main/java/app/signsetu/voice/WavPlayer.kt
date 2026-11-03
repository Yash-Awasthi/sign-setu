package app.signsetu.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Plays a recorded answer straight from memory: the WAV's PCM goes to an AudioTrack, so the person's voice is never
 * written to a file to be played. One at a time; a new play stops the last. */
object WavPlayer {
    private var track: AudioTrack? = null

    /** Plays 16-bit PCM WAV; other formats are skipped, never thrown. */
    @Synchronized
    fun play(wav: ByteArray) {
        stop()
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
        val t = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(
                    AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate)
                        .setChannelMask(if (channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO).build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size)
                .build()
        }.onFailure { Log.w("WavPlayer", "no audio track", it) }.getOrNull()
        if (t == null) { pcm.fill(0); return }
        val ok = runCatching { (t.write(pcm, 0, pcm.size) >= 0).also { if (it) t.play() } }.getOrDefault(false)
        pcm.fill(0)
        if (!ok) { t.release(); return }
        track = t
    }

    @Synchronized
    fun stop() {
        track?.let { runCatching { it.stop() }; it.release() }
        track = null
    }
}
