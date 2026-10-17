package app.signsetu.data.content

import androidx.compose.runtime.Immutable
import app.signsetu.ui.avatar.Gesture
import app.signsetu.ui.avatar.Key
import app.signsetu.ui.avatar.QuestionKind
import app.signsetu.ui.avatar.chain
import kotlinx.serialization.Serializable

/** What the character does with typed text; the officer turns each one on or off. */
enum class SignMode { SIGNS, GESTURES, SPELL, PICTURES }

/** One vocabulary entry from words.json: every written form of a word, and what the character does for it. */
@Immutable @Serializable
data class WordEntry(val w: List<String>, val sign: String? = null, val gesture: String? = null, val card: String? = null)

/** One typed word as played: [start] and [end] are seconds into the plan's gesture, before its lead-in. */
@Immutable
data class WordBeat(val range: IntRange, val card: String?, val letters: String?, val start: Float, val end: Float)

@Immutable
data class TextPlan(val gesture: Gesture?, val beats: List<WordBeat>, val motions: List<String> = emptyList())

private const val GAP = 0.3f
private val TOKEN = Regex("[\\p{L}\\p{M}\\p{N}]+(?:['’]\\p{L}+)?")

/** Grammar words carry no sign of their own; spelling them out would only slow the sentence down. */
private val FILLER = setOf(
    "a", "an", "the", "is", "are", "am", "was", "were", "be", "to", "of", "do", "does", "did", "for", "and",
    "है", "हैं", "था", "थी", "थे", "का", "की", "के", "को", "में", "से", "ने", "तो", "ही", "भी", "और",
    "hai", "hain", "tha", "thi", "the", "ka", "ki", "ke", "ko", "mein", "se", "ne", "to", "bhi", "aur",
)

private val TIME_SIGNS = setOf("sign.today", "sign.yesterday", "sign.tomorrow", "sign.now", "sign.morning", "sign.night", "sign.day", "sign.time")
private val WHEN = setOf("when", "kab", "कब")

/** ISL order: time first, then the rest as typed, then negation, then the question word. */
private val QUESTION_SIGNS = setOf("ask", "sign.what", "sign.where", "sign.how", "sign.how_many")

private fun islRank(word: String, entry: WordEntry?) = when {
    entry?.sign in QUESTION_SIGNS || word in WHEN -> 3
    entry?.sign == "no.shake" -> 2
    entry?.sign in TIME_SIGNS -> 0
    else -> 1
}

/** A still beat that keeps a picture on screen for a word with no sign. */
private val PAUSE = Gesture("pause", listOf(Key(0f), Key(0.6f)))

/**
 * Turns free text into one gesture, word by word (FR-13 as amended by D-22): a sign or gesture where the vocabulary
 * has one, otherwise fingerspelling, with Hindi spelled in Latin letters. A question mark adds the asking face.
 */
fun ContentBundle.planText(text: String, modes: Set<SignMode>): TextPlan {
    val parts = mutableListOf<Gesture>()
    val beats = mutableListOf<WordBeat>()
    var t = 0f
    fun add(g: Gesture) {
        parts += g
        t += g.keys.maxOf { it.t } + GAP
    }
    // "Ravi's" plays as Ravi: a possessive or contraction keeps only its stem.
    val tokens = phrases(TOKEN.findAll(text).map { it.range to it.value.lowercase().split('\'', '’').first() }.toList(), modes)
    // Hindi "do" (give) ends its sentence; anywhere else it is the English helper word.
    fun grammar(i: Int) = tokens[i].second in FILLER && i != tokens.lastIndex
    fun entryOf(i: Int) = tokens[i].second.let { w -> words[w]?.takeIf { !grammar(i) } }
    val order = tokens.indices.let { all -> if (SignMode.SIGNS in modes) all.sortedBy { islRank(tokens[it].second, entryOf(it)) } else all }
    for (i in order) {
        val (range, word) = tokens[i]
        val entry = entryOf(i)
        val start = t
        val card = if (SignMode.PICTURES in modes) entry?.card ?: word.toIntOrNull()?.takeIf { it in 1..10 }?.let { "n$it" } else null
        val motion = if (grammar(i)) null else motionFor(word, entry, modes)
        var letters: String? = null
        when {
            motion != null -> add(motion)
            SignMode.SPELL in modes && word !in FILLER -> {
                letters = spelling(word).filter { gestures.containsKey(letterId(it)) }
                letters.forEach { add(gestures.getValue(letterId(it))) }
            }
            card != null -> add(PAUSE)
        }
        if (t > start) beats += WordBeat(range, card, letters?.ifEmpty { null }, start, t)
    }
    if (text.trimEnd().endsWith("?") && parts.none { it.id in QUESTION_SIGNS }) gestures["ask"]?.let(::add)
    return TextPlan(chain("text", parts, GAP).takeIf { it.keys.isNotEmpty() }, beats, parts.map { it.id })
}

/** Joins runs of up to three words that the lexicon knows as one ("thank you", "how many"), longest first. */
private fun ContentBundle.phrases(tokens: List<Pair<IntRange, String>>, modes: Set<SignMode>): List<Pair<IntRange, String>> {
    val out = mutableListOf<Pair<IntRange, String>>()
    var i = 0
    while (i < tokens.size) {
        val n = (minOf(3, tokens.size - i) downTo 2).firstOrNull { n -> isPhrase(tokens.subList(i, i + n).joinToString(" ") { it.second }, modes) } ?: 1
        out += (tokens[i].first.first..tokens[i + n - 1].first.last) to tokens.subList(i, i + n).joinToString(" ") { it.second }
        i += n
    }
    return out
}

/** The letters to fingerspell: Latin as typed, Devanagari transliterated. */
fun spelling(word: String): String =
    (if (word.any { it in 'ऀ'..'ॿ' }) devanagariToLatin(word) else word).lowercase().filter { it in 'a'..'z' || it.isDigit() }

private val WH_WORDS = setOf(
    "what", "where", "who", "whom", "whose", "when", "why", "how", "which",
    "kya", "kahan", "kaun", "kab", "kyon", "kyun", "kaise", "kitna", "kitne", "kitni",
    "क्या", "कहाँ", "कहां", "कौन", "कब", "क्यों", "कैसे", "कितना", "कितने", "कितनी", "किसका", "किसने",
)

/** The question face for typed text: none unless it ends in a question mark, then wh- or yes-no. */
fun questionKind(text: String): QuestionKind? {
    val t = text.trimEnd()
    if (!t.endsWith("?") && !t.endsWith("؟")) return null
    val words = TOKEN.findAll(t.lowercase()).map { it.value }.toList()
    // A leading "kya" is the Hindi yes-no particle, not "what".
    val rest = if (words.firstOrNull() in setOf("kya", "क्या")) words.drop(1) else words
    return if (rest.any { it in WH_WORDS }) QuestionKind.WH else QuestionKind.YES_NO
}

private fun letterId(c: Char) = if (c.isDigit()) "digit.$c" else "letter.$c"

private val CONSONANTS = mapOf(
    'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n", 'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh",
    'ञ' to "n", 'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n", 'त' to "t", 'थ' to "th", 'द' to "d",
    'ध' to "dh", 'न' to "n", 'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m", 'य' to "y", 'र' to "r",
    'ल' to "l", 'व' to "v", 'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h",
)
private val NUKTA = mapOf('ड' to "r", 'ढ' to "rh", 'ज' to "z", 'फ' to "f", 'क' to "q", 'ख' to "kh", 'ग' to "g")
private val VOWELS = mapOf(
    'अ' to "a", 'आ' to "a", 'इ' to "i", 'ई' to "i", 'उ' to "u", 'ऊ' to "u", 'ए' to "e", 'ऐ' to "ai", 'ओ' to "o",
    'औ' to "au", 'ऋ' to "ri",
)
private val SIGNS = mapOf(
    'ा' to "a", 'ि' to "i", 'ी' to "i", 'ु' to "u", 'ू' to "u", 'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au",
    'ृ' to "ri", 'ं' to "n", 'ँ' to "n", 'ः' to "h",
)
private const val VIRAMA = '्'
private const val NUKTA_SIGN = '़'

/**
 * Hindi in the Latin letters Deaf signers fingerspell (पानी to pani). The inherent vowel is dropped at the end of a
 * word, as Hindi speech drops it.
 * ponytail: word-final schwa deletion only; medial deletion (समझना as samjhna) needs a syllable model if reviewers ask.
 */
fun devanagariToLatin(word: String): String = buildString {
    var i = 0
    while (i < word.length) {
        val c = word[i]
        val nukta = word.getOrNull(i + 1) == NUKTA_SIGN
        val consonant = (if (nukta) NUKTA[c] else null) ?: CONSONANTS[c]
        when {
            consonant != null -> {
                append(consonant)
                if (nukta) i++
                val next = word.getOrNull(i + 1)
                val vowelSign = next != null && next in SIGNS && next !in "ंँः"
                val ends = next == null || next !in 'ऀ'..'ॿ'
                if (!vowelSign && next != VIRAMA && !ends) append('a')
            }
            else -> append(VOWELS[c] ?: SIGNS[c] ?: if (c in 'ऀ'..'ॿ') "" else c.toString())
        }
        i++
    }
}
