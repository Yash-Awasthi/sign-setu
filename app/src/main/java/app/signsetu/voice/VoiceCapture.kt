package app.signsetu.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.NoiseSuppressor
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Speech held in memory for one recognition: 16 kHz mono 16-bit PCM, the format Whisper takes without resampling.
 * Nothing is written to storage; [wav] hands the bytes over once and [clear] wipes them. */
class VoiceCapture(private val context: Context) {
    @Volatile var level = 0f
        private set
    @Volatile private var running = false
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private val pcm = ByteArrayOutputStream()

    fun permitted() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running || !permitted()) return false
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        // VOICE_RECOGNITION: no automatic gain, the input speech models are trained on.
        val r = runCatching { AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, RATE)) }.getOrNull()
            ?.takeIf { it.state == AudioRecord.STATE_INITIALIZED } ?: return false
        if (NoiseSuppressor.isAvailable()) runCatching { NoiseSuppressor.create(r.audioSessionId)?.enabled = true }
        synchronized(pcm) { pcm.reset() }
        record = r
        running = true
        r.startRecording()
        thread = Thread({
            val buf = ShortArray(RATE / 10)
            val bytes = ByteBuffer.allocate(buf.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            while (running) {
                val n = r.read(buf, 0, buf.size)
                if (n <= 0) continue
                var sum = 0.0
                bytes.clear()
                for (i in 0 until n) { sum += buf[i] * buf[i].toDouble(); bytes.putShort(buf[i]) }
                level = (sqrt(sum / n) / 6000.0).coerceIn(0.0, 1.0).toFloat()
                synchronized(pcm) {
                    if (pcm.size() < MAX_SECONDS * RATE * 2) pcm.write(bytes.array(), 0, n * 2) else running = false
                }
            }
        }, "voice").apply { start() }
        return true
    }

    /** Stops and returns the take as a WAV, or null when it is shorter than half a second. */
    fun stop(): ByteArray? {
        running = false
        thread?.join(500)
        thread = null
        record?.run { runCatching { stop() }; release() }
        record = null
        level = 0f
        val data = synchronized(pcm) { pcm.toByteArray().also { pcm.reset() } }
        if (data.size < RATE) { data.fill(0); return null }
        return wav(data).also { data.fill(0) }
    }

    fun clear() { stop()?.fill(0) }

    companion object {
        const val RATE = 16_000
        const val MAX_SECONDS = 60

        fun wav(pcm: ByteArray): ByteArray = ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(RATE); putInt(RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(pcm.size); put(pcm)
        }.array()
    }
}
