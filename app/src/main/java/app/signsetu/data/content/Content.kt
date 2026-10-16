package app.signsetu.data.content

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.signsetu.ui.avatar.Gesture
import app.signsetu.ui.avatar.chain
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

typealias Localized = Map<String, String>

fun Localized.pick(lang: String): String? = this[lang]

@Immutable @Serializable
data class Group(val id: String, val label: Localized, val sensitive: Boolean = false)

@Immutable @Serializable
data class Question(
    val id: String,
    val group: String,
    val text: Localized,
    val cards: List<String> = emptyList(),
    val lexicon: String? = null,
    /** Character animation; defaults to the lexicon entry's. */
    val gesture: String? = null,
)

@Immutable @Serializable
data class Card(val id: String, val category: String, val label: Localized)

@Immutable @Serializable
data class CardCategory(val id: String, val label: Localized)

@Serializable
enum class BodyLocation { HEAD, FOREHEAD, EYES, NOSE, EAR, FACE, MOUTH, CHIN, CHEST, BELLY, ARM, HANDS, AWAY }

@Serializable
enum class HandAction { TOUCH, STROKE, POINT, CIRCLE, FLAT_HAND, FIST, BOTH_HANDS, MOVE, HEAD_MOVE }

@Immutable @Serializable
data class LexiconEntry(
    val id: String,
    val concept: String,
    val card: String,
    val locations: List<BodyLocation>,
    val actions: List<HandAction>,
    val label: Localized,
    val how: Localized,
)

@Immutable @Serializable
data class Language(val code: String, val name: String, val english: String, val greeting: String) {
    /** The salutation alone ("नमस्ते"), without the language finder's instruction that follows it. */
    val hello: String get() = greeting.split('।', '.', '۔').first().trim()
}

/** One slot filled: the picture, the sign for it, and the whole reviewed sentence, since agreement differs by language. */
@Immutable @Serializable
data class PhraseSlot(val card: String, val sign: String? = null, val text: Localized)

/** A phrase-bank sentence (FR-62): [signs] play in order, with [SLOT] standing for the chosen slot's sign. */
@Immutable @Serializable
data class Phrase(val id: String, val label: Localized, val signs: List<String>, val slots: List<PhraseSlot>) {
    companion object { const val SLOT = "$" }
}

@Serializable private data class PhrasesFile(val phrases: List<Phrase>)
@Serializable private data class TriageFile(val groups: List<Group>, val questions: List<Question>)
@Serializable private data class CardsFile(val categories: List<CardCategory>, val cards: List<Card>)
@Serializable private data class LexiconFile(val entries: List<LexiconEntry>)
@Serializable private data class LanguagesFile(val languages: List<Language>)
@Serializable private data class Manifest(val files: Map<String, String>)
@Serializable private data class GesturesFile(val gestures: List<Gesture>)

@Immutable
data class ContentBundle(
    val groups: List<Group>,
    val questions: List<Question>,
    val categories: List<CardCategory>,
    val cards: Map<String, Card>,
    val lexicon: Map<String, LexiconEntry>,
    val languages: List<Language>,
    val subjectUi: Map<String, Localized>,
    /** Files whose hash did not match the manifest; never shown to a subject (SEC-54). */
    val rejected: Set<String>,
    /** Every verified file in the bundle, relative to `assets/content/`. */
    val files: Set<String> = emptySet(),
    val gestures: Map<String, Gesture> = emptyMap(),
    val phrases: List<Phrase> = emptyList(),
) {
    fun gestureFor(q: Question): Gesture? = (q.gesture ?: q.lexicon)?.let { gestures[it] }

    /** The phrase's signs as one gesture; a sign with no reviewed animation is left out. */
    fun gestureFor(phrase: Phrase, slot: PhraseSlot): Gesture? =
        chain("${phrase.id}:${slot.card}", phrase.signs.mapNotNull { (if (it == Phrase.SLOT) slot.sign else it)?.let(gestures::get) })
            .takeIf { it.keys.isNotEmpty() }

    private fun audio(path: String) = path.takeIf { it in files }
    fun questionAudio(questionId: String, lang: String) = audio("audio/$lang/$questionId.ogg")
    fun greetingAudio(lang: String) = audio("audio/greet/$lang.ogg")
    fun subjectAudio(key: String, lang: String) = audio("audio/$lang/$key.ogg")
    fun phraseAudio(phrase: Phrase, slot: PhraseSlot, lang: String) = audio("audio/$lang/phrase.${phrase.id}.${slot.card}.ogg")

    fun language(code: String): Language? = languages.firstOrNull { it.code == code }
    fun subjectText(key: String, lang: String): String? = subjectUi[key]?.pick(lang)
    fun hasCardImage(id: String) = "cards/$id.webp" in files

    /** A text file failed its check, so the subject could be shown wrong words: no session may start (SEC-54). */
    val damaged: Boolean get() = rejected.any { it.endsWith(".json") && it != "gestures.json" }

    /** Gesture lookup: both location and action match first, then location only, then action only. */
    fun lookup(location: BodyLocation?, action: HandAction?): List<LexiconEntry> {
        if (location == null && action == null) return emptyList()
        return lexicon.values
            .map { e ->
                val l = location != null && location in e.locations
                val a = action != null && action in e.actions
                e to (if (l) 2 else 0) + (if (a) 1 else 0)
            }
            .filter { (_, score) -> score > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }
}

class ContentRepository(private val assets: AssetManager) {
    private val json = Json { ignoreUnknownKeys = true }
    private var bundle: ContentBundle? = null
    private val images = LruCache<String, ImageBitmap>(48)

    suspend fun load(): ContentBundle = bundle ?: withContext(Dispatchers.IO) {
        val manifest = json.decodeFromString<Manifest>(read("manifest.json"))
        val rejected = manifest.files.filter { (path, hash) -> sha256(path) != hash }.keys
        // A file that fails its check is never parsed: no content beats wrong content (SEC-54).
        val triage = verified("triage.json", rejected) ?: TriageFile(emptyList(), emptyList())
        val cards = verified("cards.json", rejected) ?: CardsFile(emptyList(), emptyList())
        val lexicon = verified("lexicon.json", rejected) ?: LexiconFile(emptyList())
        val languages = verified("languages.json", rejected) ?: LanguagesFile(emptyList())
        val subjectUi = verified<Map<String, Localized>>("subject_ui.json", rejected).orEmpty()
        val gestures = verified<GesturesFile>("gestures.json", rejected)?.gestures.orEmpty()
        val phrases = verified<PhrasesFile>("phrases.json", rejected)?.phrases.orEmpty()
        ContentBundle(
            groups = triage.groups,
            questions = triage.questions,
            categories = cards.categories,
            cards = cards.cards.associateBy { it.id },
            lexicon = lexicon.entries.associateBy { it.id },
            languages = languages.languages,
            subjectUi = subjectUi,
            rejected = rejected,
            files = manifest.files.keys - rejected,
            gestures = gestures.associateBy { it.id },
            phrases = phrases,
        ).also { bundle = it }
    }

    suspend fun cardImage(id: String): ImageBitmap? {
        images.get(id)?.let { return it }
        if (bundle?.hasCardImage(id) == false) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                assets.open("content/cards/$id.webp").use { BitmapFactory.decodeStream(it) }.asImageBitmap()
            }.getOrNull()?.also { images.put(id, it) }
        }
    }

    private inline fun <reified T> verified(name: String, rejected: Set<String>): T? =
        if (name in rejected) null else json.decodeFromString<T>(read(name))

    private fun read(name: String): String = assets.open("content/$name").bufferedReader().use { it.readText() }

    private fun sha256(path: String): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        assets.open("content/$path").use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()
}
