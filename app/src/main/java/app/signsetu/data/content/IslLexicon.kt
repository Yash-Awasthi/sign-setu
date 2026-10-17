package app.signsetu.data.content

import androidx.compose.runtime.Immutable
import app.signsetu.ui.avatar.Gesture
import app.signsetu.ui.avatar.Key
import kotlinx.serialization.Serializable

/** One sign retargeted from a recorded INCLUDE signer (tools/isl/retarget.py); [rec] names the recording. */
@Immutable @Serializable
data class IslSign(
    val id: String,
    val word: String,
    val hi: String = "",
    val category: String,
    val rec: String,
    /** A word with another everyday meaning ("fall", "light"): offered in the dictionary, never for typed text. */
    val homonym: Boolean = false,
    val keys: List<Key>,
) {
    val gesture: Gesture get() = Gesture(id, keys)
}

/** isl_signs.json: draft until Deaf review, like every sign. */
@Immutable @Serializable
data class IslSigns(
    val source: String = "",
    val categories: Map<String, Localized> = emptyMap(),
    val gestures: List<IslSign> = emptyList(),
)

const val DRAFT_CATEGORY = "Draft"

/**
 * The sign recorded from a signer for a typed or read word: the word itself, else its concept's reader label
 * (sign_words.json), preferring the label written exactly as typed ("mobile" is cell phone, "phone" telephone).
 */
fun ContentBundle.recordedSign(word: String, entry: WordEntry?): Gesture? {
    isl.gestures.firstOrNull { it.word == word && !it.homonym }?.let { return it.gesture }
    entry ?: return null
    val labels = signWords.filterValues { it in entry.w }
    val label = labels.entries.firstOrNull { it.value == word }?.key ?: labels.keys.firstOrNull()
    return label?.let { gestures["isl.$it"] }
}

/**
 * The one word lookup the app uses (D-29): with Signs on, a sign recorded from a signer, then an authored sign, then
 * the everyday gesture; with Gestures on, the gesture. Null means the word is fingerspelled or shown as a picture.
 */
fun ContentBundle.motionFor(word: String, entry: WordEntry?, modes: Set<SignMode>): Gesture? {
    val signs = SignMode.SIGNS in modes
    return (if (signs) recordedSign(word, entry) else null)
        ?: entry?.sign?.takeIf { signs }?.let(gestures::get)
        ?: entry?.gesture?.takeIf { signs || SignMode.GESTURES in modes }?.let(gestures::get)
}

/** True when a run of typed words is one entry: an ISL sign ("thank you") or a vocabulary phrase ("how many"). */
fun ContentBundle.isPhrase(words: String, modes: Set<SignMode>): Boolean =
    words in this.words || (SignMode.SIGNS in modes && isl.gestures.any { it.word == words && !it.homonym })

/** A reader label signed back for the subject to confirm (FR-86): its own recorded sign, else the lexicon's. */
fun ContentBundle.signBack(label: String): Gesture? =
    gestures["isl.$label"] ?: planText(signWords[label] ?: label, setOf(SignMode.SIGNS)).gesture

/** One sign in the officer's ISL dictionary. [forms] are every written form it is found by. */
@Immutable
data class DictionaryEntry(
    val gestureId: String,
    val en: String,
    val hi: String,
    val category: String,
    /** Retargeted from a recorded signer, as opposed to a hand-authored draft. */
    val recorded: Boolean,
    val rec: String? = null,
    val forms: List<String> = emptyList(),
)

private fun String.isDevanagari() = any { it in 'ऀ'..'ॿ' }

/** Recorded signs by INCLUDE category, then the authored word signs as drafts. Letters and digits are left out. */
fun ContentBundle.dictionary(): List<DictionaryEntry> {
    val byForm = words
    val recorded = isl.gestures.map { s ->
        val concept = signWords[s.word]?.let(byForm::get)?.w.orEmpty()
        DictionaryEntry(s.id, s.word, s.hi, s.category, true, s.rec, listOf(s.word, s.hi) + concept)
    }
    val authored = words.values.distinct().filter { it.sign?.startsWith("sign.") == true && it.sign in gestures }
        .groupBy { it.sign!! }
        .map { (id, entries) ->
            val forms = entries.flatMap { it.w }
            DictionaryEntry(id, forms.first { !it.isDevanagari() }, forms.firstOrNull { it.isDevanagari() }.orEmpty(), DRAFT_CATEGORY, false, forms = forms)
        }
    return recorded + authored.sortedBy { it.en }
}

/** Entries in [category] (all when null) whose English, Hindi or any written form contains [query]. */
fun List<DictionaryEntry>.search(query: String, category: String? = null): List<DictionaryEntry> {
    val q = query.trim().lowercase()
    return filter { e -> (category == null || e.category == category) && (q.isEmpty() || e.forms.any { q in it.lowercase() }) }
}
