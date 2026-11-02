package app.signsetu.ui.components

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** The phone's own text-to-speech for Hindi and English, used where no recording exists and nothing may go online.
 * It picks an installed voice that needs no network, the best quality first, so a typed line never leaves the phone. */
class OfflineVoice(context: Context) {
    @Volatile private var ready = false
    private val tts = TextToSpeech(context.applicationContext) { ready = it == TextToSpeech.SUCCESS }

    /** False when the phone has no offline voice for the text's language; the caller then stays silent. */
    fun speak(text: String, lang: String? = null, rate: Float = 1f): Boolean {
        if (!ready || text.isBlank()) return false
        val locale = localeFor(text, lang)
        val voice = runCatching { tts.voices }.getOrNull().orEmpty()
            .filter { it.locale.language == locale.language && !it.isNetworkConnectionRequired && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features }
            .maxByOrNull { it.quality - it.latency }
        if (voice != null) tts.voice = voice else if (tts.setLanguage(locale) < TextToSpeech.LANG_AVAILABLE) return false
        tts.setSpeechRate(rate)
        return tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "line") == TextToSpeech.SUCCESS
    }

    fun stop() { if (ready) tts.stop() }
    fun shutdown() = tts.shutdown()

    companion object {
        /** [lang] is the text's own language when known; unknown, Devanagari reads as Hindi and the rest as English. */
        fun localeFor(text: String, lang: String?): Locale = when {
            lang != null && lang != "en" -> Locale.forLanguageTag("$lang-IN")
            lang == null && text.any { it.code in 0x0900..0x097F } -> Locale.forLanguageTag("hi-IN")
            else -> Locale.forLanguageTag("en-IN")
        }
    }
}
