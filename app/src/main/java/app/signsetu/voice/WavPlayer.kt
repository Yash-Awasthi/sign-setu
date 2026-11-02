package app.signsetu.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Plays a recorded answer straight from memory: the WAV's PCM goes to an AudioTrack, so the person's voice is never
 * written to a file to be played. One at a time; a new play stops the last. */
object WavPlayer {
    private var track: AudioTrack? = null

    @Synchronized
    fun play(wav: ByteArray) {
        stop()
        val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        if (wav.size <= 44 || String(wav, 0, 4, Charsets.US_ASCII) != "RIFF") return
        val channels = b.getShort(22).toInt()
        val rate = b.getInt(24)
        val pcm = wav.copyOfRange(44, wav.size)
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate)
                    .setChannelMask(if (channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO).build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size)
            .build()
        t.write(pcm, 0, pcm.size)
        pcm.fill(0)
        t.play()
        track = t
    }

    @Synchronized
    fun stop() {
        track?.let { runCatching { it.stop() }; it.release() }
        track = null
    }
}
