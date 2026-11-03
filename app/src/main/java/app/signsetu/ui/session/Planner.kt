package app.signsetu.ui.session

import app.signsetu.assistant.ModelStore
import app.signsetu.assistant.OnlineAssistant
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.Phase
import app.signsetu.data.content.Question
import app.signsetu.data.content.unsignedWords
import app.signsetu.data.session.EventType
import app.signsetu.data.session.SessionEvent
import app.signsetu.net.BrainClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** A step the planner found: bank item [id], or [say], a sentence a model wrote that passed [refusal]; the cue [word]
 * the person's answer met, the model's short [why], and [by] whom: "words" when the answer named the item outright,
 * else the model ("station gemma3:1b", "phone", "gateway"). [caption] is [say] in the person's language, from the
 * station's translator; null leaves it in English beside the signing. */
data class Plan(val id: String, val word: String, val by: String, val say: String? = null, val why: String = "", val caption: String? = null)

/** A bank item auto mode may propose now, with the words that show it is wanted: its pictures' names in English for the
 * models ([cues]), and in the person's own language for matching what they typed or said ([local]). */
data class Cued(val q: Question, val cues: List<String>, val local: List<String> = emptyList())

/** What the planner reads: the conversation as JSON turns (counselHistory), the item last shown and the person's answer
 * to it in plain words, the items allowed, best first, and what must be masked before the gateway. [lang] is the
 * person's language: the station translates their answer for its model and a written line back. */
class PlanAsk(val turns: String, val asked: String, val answer: String, val items: List<Cued>, val names: List<String> = emptyList(), val details: List<String> = emptyList(), val lang: String = "en") {
    val words: List<String> get() = items.flatMap { it.cues }.distinct()
}

fun interface PlanRung { suspend fun plan(ask: PlanAsk, timeoutMs: Long): Plan? }

/** The person's answer meets an item's cue word outright, or failing that a model names which cue word it means, tried
 * on each rung in turn within what is left of [budgetMs]. A model only ever names a word: the item is the first allowed
 * one carrying it, so nothing outside the bank reaches the person, and no answer leaves the bank's order. */
class Planner(private val rungs: List<Pair<Long, PlanRung>>, private val refuse: (String) -> String? = { "no check" }, private val budgetMs: Long = PLAN_BUDGET_MS) {
    suspend fun plan(ask: PlanAsk): Plan? {
        matchWords(ask)?.let { return it }
        val end = System.nanoTime() / 1_000_000 + budgetMs
        for ((limit, rung) in rungs) {
            val left = minOf(limit, end - System.nanoTime() / 1_000_000)
            if (left < MIN_RUNG_MS) break
            val p = try {
                withTimeoutOrNull(left + GRACE_MS) { rung.plan(ask, left) }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            if (p == null) continue
            if (p.say != null && refuse(p.say) == null) return p
            if (p.say == null && ask.items.any { it.q.id == p.id }) return p
        }
        return null
    }
}

/** Station, then this phone's model, then the gateway (masked, and only once the officer allowed session data off the phone).
 * The phone's model only names cue words; the station's and the gateway's may also write a sentence, checked by [refusal]. */
fun planLadder(content: ContentBundle, brain: BrainClient, models: ModelStore, cloud: OnlineAssistant, scope: CoroutineScope, cloudAllowed: () -> Boolean) = Planner(listOf(
    STATION_MS to PlanRung { ask, ms ->
        brain.plan(ask.turns, ask.items.map { BrainClient.PlanItem(it.q.id, it.q.text["en"] ?: it.q.id, it.cues) }, ms.toInt(), ask.names, ask.details, ask.lang)
            ?.let { Plan(it.id ?: FREE_ID, it.word, "station ${it.model}".trim(), it.say, it.why, it.caption) }
    },
    PHONE_MS to PlanRung { ask, ms ->
        if (models.state() != ModelStore.State.READY) return@PlanRung null
        // The native call cannot be interrupted; it finishes on its own while the ladder moves on.
        val reply = scope.async { models.use { it.chat(PLAN_PROMPT, planUser(ask), PLAN_TOKENS) } }
        withTimeoutOrNull(ms) { reply.await() }?.let { byWord(ask, wordOf(it), "phone") }
    },
    GATEWAY_MS to PlanRung { ask, ms ->
        if (!cloudAllowed() || !cloud.sendsSessionData()) return@PlanRung null
        cloud.ask(PLAN_WRITE_PROMPT, writeUser(ask), ask.names, WRITE_TOKENS, ask.details, timeoutMs = ms.toInt(), retry = false, viaStation = false)
            ?.let { written(ask, it, "gateway") }
    },
), refuse = content::refusal)

/** The gateway refuses a system prompt that is not in app_prompts.json; the station's copy is brain/brain/plan.py. */
const val PLAN_PROMPT = "A frightened child at a police station gave an answer. Which one word from the list is closest to what the " +
    "answer says they need or feel? If none is close, reply none. Reply with JSON only: {\"word\": \"<one word from the list or none>\"}"

/** The gateway refuses a system prompt that is not in app_prompts.json; the station's copy is WRITE_PROMPT in brain/brain/plan.py. */
const val PLAN_WRITE_PROMPT = "You plan the next step of a welfare conversation between a police officer and a person who may be a frightened child. " +
    "They answer with pictures, yes or no, writing or sign language. The conversation so far is a JSON array of turns: " +
    "\"officer\" is what was shown to them, \"user\" is their answer; a signed answer is a machine's unconfirmed reading. " +
    "Choose one: an item from the list, when one fits what they just showed; or one short new sentence about their comfort or needs " +
    "(water, food, toilet, rest, cold, feeling unwell, a trusted person, a break). A new sentence has at most 10 simple everyday words " +
    "and is never about what happened, violence, the body, touching, secrets or who did something; never asks why; never suggests " +
    "a person, place, act or detail they have not given; never asks for names, numbers or addresses; never promises anything. " +
    "Reply with JSON only: {\"id\": \"<item id>\", \"why\": \"<at most 12 words>\"} or {\"say\": \"<sentence>\", \"why\": \"<at most 12 words>\"}"

internal fun writeUser(ask: PlanAsk) = "Conversation: ${ask.turns}\nItems:\n" + ask.items.joinToString("\n") { "${it.q.id}: ${it.q.text["en"] ?: it.q.id}" }

/** A written reply: an allowed item, or a sentence for [Planner] to check. */
internal fun written(ask: PlanAsk, reply: String, by: String): Plan? = runCatching {
    val o = Json.parseToJsonElement(Regex("""\{.*\}""", RegexOption.DOT_MATCHES_ALL).find(reply)?.value ?: return null).jsonObject
    val why = o["why"]?.jsonPrimitive?.contentOrNull.orEmpty().take(MAX_WHY)
    val id = o["id"]?.jsonPrimitive?.contentOrNull?.trim()
    val say = o["say"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    when {
        id != null && ask.items.any { it.q.id == id } -> Plan(id, "", by, why = why)
        say != null -> Plan(FREE_ID, "", by, say.split(Spaces).joinToString(" "), why)
        else -> null
    }
}.getOrNull()

/** Why a sentence a model wrote may not go to the person, or null when it may: it must be short and plain, stay off
 * what happened, violence, the body and the people involved, ask for no personal details (the bank's items record
 * those as fields), ask no why or leading question, and every word must have a sign of its own, so the character never
 * fingerspells it. "Did you eat today?" asks about the person's own needs; "Did you go there?" points past them. */
fun ContentBundle.refusal(say: String): String? {
    val words = WordRe.findAll(say.lowercase()).map { it.value }.toList()
    val body = cards.values.filter { it.category == BODY_CATEGORY }.flatMap { listOfNotNull(it.id, it.label["en"]?.lowercase()) }.toSet()
    return when {
        words.isEmpty() || words.size > MAX_SAY_WORDS -> "length"
        say.any { it.isDigit() } || '\n' in say -> "form"
        say.count { it in ".?!" } > 2 -> "sentences"
        words.any { it in Unsafe || it in body } -> "topic"
        words.any { it in Personal } -> "personal details"
        words.first() in Leading && words.any { it in PointsAway } -> "leading"
        else -> unsignedWords(say).takeIf { it.isNotEmpty() }?.let { "unsigned: ${it.joinToString()}" }
    }
}

/** A sentence a model wrote, as an item auto mode can propose, log and show like a bank one. */
fun writtenItem(say: String, caption: String?, lang: String, phase: Phase?) = Question(
    id = FREE_ID + ":" + Integer.toHexString(say.hashCode()), group = WRITTEN_GROUP, phase = phase, signed = true,
    text = mapOf("en" to say) + (caption?.takeIf { lang != "en" }?.let { mapOf(lang to it) } ?: emptyMap()),
)

fun Question.isWritten() = group == WRITTEN_GROUP

internal fun planUser(ask: PlanAsk) = "List: ${ask.words.joinToString(", ")}\nQuestion shown: ${ask.asked}\nAnswer: ${ask.answer}"

internal fun wordOf(reply: String): String? = runCatching {
    val o = Regex("""\{.*\}""", RegexOption.DOT_MATCHES_ALL).find(reply)?.value ?: return null
    Json.parseToJsonElement(o).jsonObject["word"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()
}.getOrNull()

internal fun byWord(ask: PlanAsk, word: String?, by: String): Plan? =
    word?.let { w -> ask.items.firstOrNull { w in it.cues }?.let { Plan(it.q.id, w, by) } }

/** The answer names a picture outright, in English or in the person's language ("पानी" is water); no model, no translation.
 * Marks count as part of a word, so a Devanagari name matches only whole. */
internal fun matchWords(ask: PlanAsk): Plan? {
    val said = ask.answer.lowercase()
    for (item in ask.items) for (cue in item.cues + item.local) {
        if (Regex("""(?<![\p{L}\p{M}\p{N}])""" + Regex.escape(cue) + """(?![\p{L}\p{M}\p{N}])""").containsMatchIn(said)) return Plan(item.q.id, cue, "words")
    }
    return null
}

/** The names of [q]'s pictures, without yes, no, numbers and catch-alls, which say nothing about a need. */
fun cues(content: ContentBundle, q: Question): List<String> = cueCards(content, q).flatMap { listOfNotNull(it.id.replace('_', ' '), it.label["en"]?.lowercase()) }.distinct()

/** The same pictures' names in [lang] and in Hindi, which most people here can read and type. */
fun localCues(content: ContentBundle, q: Question, lang: String): List<String> =
    cueCards(content, q).flatMap { c -> setOf(lang, "hi").filter { it != "en" }.mapNotNull { c.label[it]?.lowercase() } }.filter { it.length >= 2 }.distinct()

private fun cueCards(content: ContentBundle, q: Question) = q.cards.mapNotNull { id -> content.cards[id]?.takeIf { it.category !in PlainCategories && id !in PlainCards } }

/** Urgent: auto mode stops and the officer takes over. Distress: nothing more goes on the stage without the officer's tap. */
enum class Cue { URGENT, DISTRESS }

fun cueOf(e: SessionEvent): Cue? {
    val words = (answerWords(e, null) + listOfNotNull(e.cardId)).flatMap { it.lowercase().split(NonWord) }.toSet()
    return when {
        words.any { it in UrgentWords } -> Cue.URGENT
        words.any { it in DistressWords } -> Cue.DISTRESS
        else -> null
    }
}

/** What the person gave since the item last shown: [words] in plain English, [unsure] when any of it was an unsure
 * answer or a sign the reader could not read, [at] when the last of it came. Null when nothing came yet. */
class LastAnswer(val asked: String, val words: String, val unsure: Boolean, val cue: Cue?, val at: Long)

fun lastAnswer(events: List<SessionEvent>, content: ContentBundle): LastAnswer? {
    val shown = events.indexOfLast { it.type == EventType.QUESTION_SHOWN }
    if (shown < 0) return null
    val answers = events.drop(shown + 1).filter { it.type in AnswerEvents }
    if (answers.isEmpty()) return null
    val row = events[shown]
    val asked = row.itemId?.let(content::question)?.text?.get("en") ?: row.text.orEmpty()
    val unsure = answers.any { it.isUnsure() || it.type == EventType.ANSWER_SIGNED && signedWords(it.text)?.contains('…') != false }
    val cues = answers.mapNotNull(::cueOf)
    val cue = if (Cue.URGENT in cues) Cue.URGENT else cues.firstOrNull()
    return LastAnswer(asked, answers.flatMap { answerWords(it, content) }.joinToString(" "), unsure, cue, answers.last().ts)
}

private fun answerWords(e: SessionEvent, content: ContentBundle?): List<String> = when (e.type) {
    EventType.ANSWER_SIGNED -> listOfNotNull(signedWords(e.text)?.replace("…", ""))
    EventType.ANSWER_TEXT -> listOfNotNull(e.text)
    EventType.ANSWER_CARD, EventType.ANSWER_GESTURE, EventType.REQUEST ->
        listOfNotNull(e.cardId?.takeIf { it !in PlainCards && it !in AnswerCards }?.let { id -> content?.cards?.get(id)?.label?.get("en") ?: id.replace('_', ' ') })
    else -> emptyList()
}

/** The log row of a planned step: who found it, the word, the answer it read and the time from that answer to the proposal. */
data class PlanLog(val by: String, val word: String, val input: String, val ms: Long)

const val PLAN_PREFIX = "plan:"

fun planReason(p: Plan, input: String, ms: Long) = PLAN_PREFIX + buildJsonObject {
    put("by", p.by); put("word", p.word.ifEmpty { p.why }); put("input", input.take(MAX_INPUT)); put("ms", ms)
    if (p.say != null) put("written", true)
}.toString()

fun decodePlan(reason: String?): PlanLog? = reason?.takeIf { it.startsWith(PLAN_PREFIX) }?.let {
    runCatching {
        val o = Json.parseToJsonElement(it.removePrefix(PLAN_PREFIX)).jsonObject
        PlanLog(o.getValue("by").jsonPrimitive.content, o.getValue("word").jsonPrimitive.content, o["input"]?.jsonPrimitive?.content.orEmpty(), o["ms"]?.jsonPrimitive?.longOrNull ?: 0L)
    }.getOrNull()
}

private const val PLAN_BUDGET_MS = 10_000L
private const val STATION_MS = 4_000L
private const val PHONE_MS = 6_000L
private const val GATEWAY_MS = 6_000L
private const val MIN_RUNG_MS = 500L
private const val GRACE_MS = 1_000L
private const val PLAN_TOKENS = 24
private const val WRITE_TOKENS = 120
private const val MAX_WHY = 120
private const val MAX_SAY_WORDS = 12
private const val BODY_CATEGORY = "body"
const val FREE_ID = "written"
private const val WRITTEN_GROUP = "written"
private val WordRe = Regex("""[\p{L}\p{N}']+""")
private val Spaces = Regex("""\s+""")
/** Off limits for a sentence a model writes: the incident, violence, touching, secrets, blame, promises and the people involved. */
private val Unsafe = setOf(
    "why", "who", "whom", "whose", "happened", "happen", "happening", "touch", "touched", "touching", "hit", "hitting", "beat", "beaten",
    "slap", "kick", "punch", "push", "hurt", "hurting", "hurts", "pain", "injury", "blood", "kill", "knife", "gun", "weapon", "attack",
    "abuse", "abused", "rape", "sex", "sexual", "naked", "clothes", "undress", "private", "secret", "secrets", "kiss", "bad", "wrong",
    "fault", "blame", "lie", "lying", "truth", "promise", "safe", "body", "he", "she", "him", "her", "his", "man", "woman", "uncle",
    "aunty", "aunt", "stranger", "boy", "girl", "police", "arrest", "jail", "punish",
)
/** A question opening on these asks about something past and invites a yes; it is leading once it points past the
 * person's own needs, to someone else, a place, a time or a thing ([PointsAway]). */
private val Leading = setOf("did", "was", "were", "has", "had", "have", "didn't", "wasn't", "weren't", "hasn't", "hadn't", "haven't")
private val PointsAway = setOf(
    "they", "them", "their", "someone", "somebody", "anyone", "anybody", "people", "person", "it", "that", "this", "there", "then",
    "before", "yesterday", "night", "last", "go", "went", "gone", "come", "came", "see", "saw", "seen", "take", "took", "taken",
    "give", "gave", "given", "say", "said", "tell", "told", "do", "done", "make", "made", "home", "house", "room", "car", "place",
)
/** The bank asks for these with its own items and records the answers as personal details; a written sentence does not. */
private val Personal = setOf("name", "number", "mobile", "phone", "address", "village", "city", "district", "state", "school", "class", "teacher", "age", "old", "live", "lives")
private const val MAX_INPUT = 200
private val NonWord = Regex("""[^\p{L}\p{N}]+""")
private val PlainCategories = setOf("answers", "numbers")
private val PlainCards = setOf("okay", "person", "draw", "other", "something_else", "nothing")
private val AnswerCards = setOf("yes", "no", "dont_know", "dont_understand", "stop", "break", "wrong")
private val UrgentWords = setOf("help", "knife", "attack", "injury", "blood", "hit", "kill", "danger", "weapon", "gun")
private val DistressWords = setOf("scared", "afraid", "fear", "sad", "cry", "crying", "angry", "pain", "hurt", "sick", "fever")
