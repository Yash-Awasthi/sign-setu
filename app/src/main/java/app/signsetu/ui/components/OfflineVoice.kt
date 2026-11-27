package app.signsetu.ui.components

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** The phone's own text-to-speech for Hindi and English, used where no recording exists and nothing may go online.
 * It picks an installed voice that needs no network, the best quality first, so a typed line never leaves the phone. */
class OfflineVoice(context: Context) {
    private var tts: TextToSpeech? = null
    private val gate = SpeechGate { request -> say(request) }

    init {
        // Every stage builds a fresh voice, so a line typed at once reaches speak() before this listener runs.
        tts = TextToSpeech(context.applicationContext) { gate.initialized(it == TextToSpeech.SUCCESS) }
    }

    /** False when the phone has no offline voice for the text's language; the caller then stays silent.
     * True also when the voice is still starting: the line is kept and said as soon as it is up. */
    fun speak(text: String, lang: String? = null, rate: Float = 1f): Boolean =
        gate.speak(SpeechGate.Request(text, lang, rate))

    private fun say(request: SpeechGate.Request): Boolean {
        val tts = tts ?: return false
        val locale = localeFor(request.text, request.lang)
        val voice = runCatching { tts.voices }.getOrNull().orEmpty()
            .filter { it.locale.language == locale.language && !it.isNetworkConnectionRequired && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features }
            .maxByOrNull { it.quality - it.latency }
        if (voice != null) tts.voice = voice else if (tts.setLanguage(locale) < TextToSpeech.LANG_AVAILABLE) return false
        tts.setSpeechRate(request.rate)
        return tts.speak(request.text, TextToSpeech.QUEUE_FLUSH, null, "line") == TextToSpeech.SUCCESS
    }

    fun stop() {
        gate.stop()
        if (gate.started) tts?.stop()
    }

    fun shutdown() {
        gate.stop()
        tts?.shutdown()
        tts = null
    }

    companion object {
        /** [lang] is the text's own language when known; unknown, Devanagari reads as Hindi and the rest as English. */
        fun localeFor(text: String, lang: String?): Locale = when {
            lang != null && lang != "en" -> Locale.forLanguageTag("$lang-IN")
            lang == null && text.any { it.code in 0x0900..0x097F } -> Locale.forLanguageTag("hi-IN")
            else -> Locale.forLanguageTag("en-IN")
        }
    }
}

/** The bookkeeping of [OfflineVoice] without an engine, so the JVM tests can hold it to its table: a line asked for
 * before the voice is up is kept, newest only, and said once from the init callback; stop drops it. */
internal class SpeechGate(private val say: (Request) -> Boolean = { true }) {
    data class Request(val text: String, val lang: String?, val rate: Float)

    @Volatile private var up = false
    private var held: Request? = null

    /** True once the engine has reported success, the only moment the kept line may be said. */
    val started: Boolean get() = up

    /** The line waiting for the engine to start, if any. */
    val pending: Request? get() = held

    /** True when the line is being said now or is kept for the start of the voice; false for blank text. */
    fun speak(request: Request): Boolean {
        if (request.text.isBlank()) return false
        if (up) {
            held = null
            say(request)
            return true
        }
        held = request
        return true
    }

    /** The engine is up or it failed: a kept line is said once and dropped either way. */
    fun initialized(success: Boolean) {
        up = success
        val line = held.also { held = null }
        if (success && line != null) say(line)
    }

    fun stop() { held = null }
}
