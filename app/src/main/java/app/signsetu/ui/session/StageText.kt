package app.signsetu.ui.session

import app.signsetu.data.content.CAPTION_MODES
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.Gesture
import app.signsetu.data.content.SignMode
import app.signsetu.data.content.TOKEN
import app.signsetu.data.content.TextPlan
import app.signsetu.data.content.WordBeat
import app.signsetu.data.content.chain
import app.signsetu.data.content.planText
import app.signsetu.data.content.signBack

/** the reader's top labels a signed answer's confirm or say-back signs back before asking. */
fun readBackLabels(ui: SessionUi): List<String> {
    val confirm = ui.confirm?.takeIf { it.queue.isNotEmpty() }
    val sayBack = ui.pending as? Pending.FreeText
    return when {
        confirm != null -> listOfNotNull(confirm.read.firstOrNull())
        sayBack?.ref != null -> ui.signed[sayBack.ref].orEmpty().mapNotNull { it.guesses?.firstOrNull()?.word }
        else -> emptyList()
    }
}


/** A No to a reading of the subject's own answer gives them their own way to say it again, never a dead end. */
internal fun reopensInput(pending: Pending?, yes: Boolean) =
    !yes && !pending.asksChoice && (pending is Pending.ShowBack || (pending is Pending.FreeText && pending.ref != null))

/** A reopened input holds the item until the subject submits it; the next run step would close it. */
internal fun advancesRun(pending: Pending?, yes: Boolean) = !reopensInput(pending, yes)

/** What one shown item puts on the stage. */
internal class StageItem(
    val caption: String?,
    val held: String?,
    val choices: List<String>,
    val numbers: Boolean,
    val canDraw: Boolean,
)

internal fun describeItem(pending: Pending?, content: ContentBundle, lang: String): StageItem {
    val (caption, cards) = when (pending) {
        is Pending.Ask -> listOfNotNull(pending.again?.let { content.subjectText(it, lang) }, pending.question.text.get(lang)).joinToString(" ") to pending.question.cards
        is Pending.FreeText -> (pending.caption ?: pending.text) to pending.cardIds
        is Pending.Cards -> null to pending.cardIds
        is Pending.ShowBack -> content.subjectText("is_this_it", lang) to pending.choices.takeIf { pending.asksChoice }.orEmpty()
        is Pending.PhraseAsk -> pending.slot.text.get(lang) to listOf(pending.slot.card)
        is Pending.GroundRules -> ruleStep(pending)?.let { ruleText(content, it.key, lang) } to listOfNotNull(ruleStep(pending)?.card)
        else -> null to emptyList()
    }
    val pictures = cards.filter { it != "draw" && it in content.cards }
    val numbers = pictures.isNotEmpty() && pictures.all { it.matches(NUMBER_CARD) }
    val held = when {
        pending is Pending.ShowBack && !pending.asksChoice -> pending.entry.card
        pending !is Pending.Cards && pictures.size == 1 -> pictures.first()
        else -> null
    }
    val choices = if (held != null) emptyList() else pictures.take(if (numbers) 10 else 6)
    return StageItem(caption, held, choices, numbers, "draw" in cards)
}

/** A recorded signer for typed text, or for a question whose English wording was recorded. */
internal fun recordedClip(pending: Pending?, content: ContentBundle) = when (pending) {
    is Pending.FreeText -> content.clipFor(pending.text)
    is Pending.Ask -> pending.question.text["en"]?.let(content::clipFor)
    is Pending.GroundRules -> ruleStep(pending)?.let { ruleText(content, it.key, "en") }?.let(content::clipFor)
    else -> null
}

internal fun speechFor(pending: Pending?, content: ContentBundle, lang: String): String? = when (pending) {
    is Pending.Ask -> content.questionAudio(pending.question.id, lang)
    is Pending.ShowBack -> content.subjectAudio("is_this_it", lang)
    is Pending.PhraseAsk -> content.phraseAudio(pending.phrase, pending.slot, lang)
    is Pending.GroundRules -> ruleStep(pending)?.let { ruleAudio(content, it.key, lang) }
    else -> null
}

private val NUMBER_CARD = Regex("n\\d+")

/** A line the stage shows itself: [shown] in the subject's language (null when the hint stands in), signed from
 * [english]; [plan], when set, is already in [shown]'s positions. */
internal data class CaptionLine(val shown: String?, val english: String, val plan: TextPlan? = null)

/** [english] planned, each word's range moved onto the word of [shown] it lights. A word with nothing to light in
 * [shown] is not signed at all, so the hands only ever move for a lit word. */
internal fun linePlan(
    content: ContentBundle, english: String, shown: String, modes: Set<SignMode>,
    /** Where in [shown] the signed wording starts: a repeated question carries a lead-in line first. */
    from: Int = 0, again: Boolean = true,
): TextPlan {
    val plan = content.planText(english, modes)
    if (shown == english) return plan
    val part = shown.substring(from)
    val lit = plan.beats.map { b ->
        val word = english.substring(b.range)
        val end = part.trimEnd()
        val mark = end.lastIndex.takeIf { word.length == 1 && word[0] in QUESTION_MARKS && end.lastOrNull() in QUESTION_MARKS }
        b to (mark?.let { it..it } ?: captionRange(content, word, part))?.let { it.first + from..it.last + from }
    }
    if (lit.all { it.second != null } || !again) return plan.copy(beats = lit.map { (b, r) -> b.copy(range = r ?: IntRange.EMPTY) })
    val kept = lit.filter { it.second != null }.sortedBy { it.first.range.first }.joinToString(" ") { english.substring(it.first.range) }
    return if (kept.isBlank()) TextPlan(null, emptyList()) else linePlan(content, kept, shown, modes, from, again = false)
}

/** Read words signed back as they were read, then "Is this it?" from its wording; the caption is the words and the
 * question, each lit while it plays. */
internal fun readBackLine(content: ContentBundle, labels: List<String>, lang: String): CaptionLine? {
    val signs = labels.mapNotNull { l -> content.signBack(l)?.takeIf { it.keys.isNotEmpty() }?.let { l to it } }
    if (signs.isEmpty()) return null
    val words = signs.joinToString(" ") { it.first }
    val askEn = content.subjectText("is_this_it", "en").orEmpty()
    val shown = "$words… ${content.subjectText("is_this_it", lang) ?: askEn}"
    val parts = mutableListOf<Gesture>()
    val beats = mutableListOf<WordBeat>()
    var t = 0f
    var at = 0
    for ((word, g) in signs) {
        val end = t + g.keys.maxOf { it.t }
        beats += WordBeat(at until at + word.length, null, null, t, end)
        parts += g
        t = end + 0.3f
        at += word.length + 1
    }
    val ask = linePlan(content, askEn, shown.substring(words.length + 2), CAPTION_MODES)
    ask.gesture?.let { g ->
        parts += g
        val base = words.length + 2
        beats += ask.beats.map { b -> b.copy(range = if (b.range.isEmpty()) b.range else b.range.first + base..b.range.last + base, start = b.start + t, end = b.end + t) }
    }
    return CaptionLine(shown, "$words… $askEn", TextPlan(chain("read_back", parts), beats))
}

private val QUESTION_MARKS = setOf('?', '؟', '？')

/** The range in [caption] of the word that means [english]: a caption word written in the same words.json entry. */
internal fun captionRange(content: ContentBundle, english: String, caption: String): IntRange? {
    val forms = content.words[english.lowercase()]?.w?.map { it.lowercase() }?.toSet() ?: return null
    return TOKEN.findAll(caption).firstOrNull { it.value.lowercase() in forms }?.range
}

/** Which unit of a spelled word the letter tile shows [t] seconds into the plan's gesture, before its lead-in:
 * one akshara at a time, left to right. -1 when the beat only holds a picture. */
internal fun spelledIndex(beat: WordBeat, t: Float): Int {
    val n = beat.letters?.size ?: return -1
    if (n == 0) return -1
    // A zero-length beat cannot be divided; show its first unit rather than crashing on the range.
    if (beat.end <= beat.start) return 0
    val progress = ((t - beat.start) / (beat.end - beat.start)).coerceIn(0f, 1f)
    return (progress * n).toInt().coerceIn(0, n - 1)
}
