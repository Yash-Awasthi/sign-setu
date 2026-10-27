package app.signsetu.gloss

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.exp
import kotlin.math.ln

/** One intent of intents.json (tools/content/build_intents.py). */
data class Intent(val id: String, val pattern: List<String>, val questions: Set<String>, val en: String, val hi: String, val risk: Boolean)

/** A filled slot: what fills it, from which token. */
data class Filled(val slot: String, val label: String, val token: Int, val p: Float)

/** An intent aligned to a lattice. [logScore] is the alignment's log probability plus the question prior;
 * [posterior] is its share among every intent that matched. */
data class IntentMatch(val intent: Intent, val logScore: Float, val fills: List<Filled>, val used: Set<Int>, val posterior: Float = 0f)

enum class Gender { UNKNOWN, MASCULINE, FEMININE }

/** The reviewed phrasebook: intents as data, matched against the whole lattice (docs/10-gloss-to-text.md, Phase 4). */
class Phrasebook(json: String) {
    val intents: List<Intent>
    private val slots: Map<String, Map<String, JsonObject>>

    init {
        val o = Json.parseToJsonElement(json).jsonObject
        slots = o.getValue("slots").jsonObject.mapValues { (_, v) -> v.jsonObject.mapValues { it.value.jsonObject } }
        intents = o.getValue("intents").jsonArray.map { e ->
            val i = e.jsonObject
            Intent(
                i.getValue("id").jsonPrimitive.content,
                i.getValue("pattern").jsonArray.map { it.jsonPrimitive.content },
                i.getValue("questions").jsonArray.map { it.jsonPrimitive.content }.toSet(),
                i.getValue("en").jsonPrimitive.content, i.getValue("hi").jsonPrimitive.content,
                i["risk"]?.jsonPrimitive?.boolean == true,
            )
        }
    }

    /** Every intent that can align with [lattice], best first, with posteriors over them. [question] is the triage id on
     * screen, if any: intents that answer it share [ON_QUESTION] of the prior. */
    fun match(lattice: GlossLattice, question: String?): List<IntentMatch> {
        val answering = intents.count { question != null && question in it.questions }
        val raw = intents.mapNotNull { intent ->
            val prior = when {
                answering == 0 -> 1f / intents.size
                question in intent.questions -> ON_QUESTION / answering
                else -> (1 - ON_QUESTION) / (intents.size - answering)
            }
            align(intent, lattice)?.let { it.copy(logScore = it.logScore + ln(prior)) }
        }
        if (raw.isEmpty()) return emptyList()
        val top = raw.maxOf { it.logScore }
        val z = raw.sumOf { exp((it.logScore - top).toDouble()) }.toFloat()
        return raw.map { it.copy(posterior = exp(it.logScore - top) / z) }.sortedByDescending { it.logScore }
    }

    /** Dynamic program over pattern items and tokens: a token can fill an item (its candidate's log probability), be
     * skipped as an extra segment, or an item can go unsigned (free if optional, else a missed sign). Head items
     * (@nod, @shake) look at the whole answer instead. Null when a required head movement is absent. */
    internal fun align(intent: Intent, lattice: GlossLattice): IntentMatch? {
        val toks = lattice.tokens
        val items = intent.pattern.filter { item ->
            if (!item.startsWith("@")) return@filter true
            val seen = toks.any { if (item.removeSuffix("?") == "@nod") it.nonManual.headNod > 0.5f else it.nonManual.headShake > 0.5f }
            if (!seen && !item.endsWith("?")) return null
            false
        }
        val m = items.size
        val n = toks.size
        class Cell(val score: Float, val back: Int, val fill: Filled?)
        val dp = Array(m + 1) { arrayOfNulls<Cell>(n + 1) }
        dp[0][0] = Cell(0f, -1, null)
        for (i in 0..m) for (j in 0..n) {
            val here = dp[i][j] ?: continue
            fun offer(ii: Int, jj: Int, s: Float, back: Int, fill: Filled?) {
                val c = dp[ii][jj]
                if (c == null || s > c.score) dp[ii][jj] = Cell(s, back, fill)
            }
            if (j < n) offer(i, j + 1, here.score + INSERT, 0, null)
            if (i < m) {
                val optional = items[i].endsWith("?")
                offer(i + 1, j, here.score + if (optional) 0f else DELETE, 1, null)
                if (j < n) fit(items[i], toks[j])?.let { f -> offer(i + 1, j + 1, here.score + ln(f.p.coerceAtLeast(1e-4f)), 2, f) }
            }
        }
        val end = dp[m][n] ?: return null
        // A head shake is negation; an intent that leaves it unexplained reads the answer the wrong way round.
        val unexplained = if (intent.pattern.none { it.startsWith("@shake") } && toks.any { it.nonManual.headShake > 0.5f }) UNEXPLAINED_SHAKE else 0f
        // Walk back for the fills and the tokens used.
        val fills = ArrayList<Filled>()
        var i = m
        var j = n
        var c: Cell = end
        while (i > 0 || j > 0) {
            when (c.back) {
                0 -> j--
                1 -> i--
                else -> { c.fill?.let(fills::add); i--; j-- }
            }
            c = dp[i][j] ?: break
        }
        val required = items.count { !it.endsWith("?") }
        if (required > 0 && fills.none()) return null
        return IntentMatch(intent, end.score + unexplained, fills.reversed(), fills.map { it.token }.toSet())
    }

    /** The best reading of [token] that fits [item], or null. */
    private fun fit(item: String, token: GlossToken): Filled? {
        val name = item.removeSuffix("?")
        val slot = name.removePrefix("$").takeIf { name.startsWith("$") }
        return when {
            slot == "NAME" -> token.candidates.firstOrNull()?.takeIf { token.type == TokenType.SPELL }?.let { Filled(slot, it.label, token.id, it.p) }
            slot == "NUM" -> token.candidates.firstOrNull()?.takeIf { token.type == TokenType.NUM }?.let { Filled(slot, it.label.filter(Char::isDigit), token.id, it.p) }
            slot != null -> token.candidates.firstOrNull { it.label in slots[slot].orEmpty() }?.let { Filled(slot, it.label, token.id, it.p) }
            token.type == TokenType.SPELL || token.type == TokenType.NUM -> null
            else -> name.split('|').toSet().let { words -> token.candidates.firstOrNull { it.label in words } }?.let { Filled(name, it.label, token.id, it.p) }
        }
    }

    /** The intent's English sentence with its slots filled. */
    fun english(m: IntentMatch): String = render(m.intent.en, m, "en", Gender.UNKNOWN)

    /** The Hindi sentence; masculine/feminine pairs stay "a/b" while the speaker's gender is unknown. */
    fun hindi(m: IntentMatch, gender: Gender = Gender.UNKNOWN): String = render(m.intent.hi, m, "hi", gender)

    private fun render(template: String, m: IntentMatch, lang: String, gender: Gender): String {
        val bySlot = m.fills.associateBy { it.slot }
        val out = SLOT.replace(template) { r ->
            val (slot, path, sep) = Triple(r.groupValues[1], r.groupValues[2], r.groupValues[3])
            val f = bySlot[slot] ?: return@replace ""
            val fields = path.split('.').filter { it.isNotEmpty() }
            val cap = "cap" in fields
            val field = fields.firstOrNull { it != "cap" } ?: lang
            val value = when (slot) {
                "NAME", "NUM" -> f.label.replaceFirstChar(Char::uppercase)
                else -> slots[slot]?.get(f.label)?.get(field)?.jsonPrimitive?.content ?: f.label
            }
            (if (cap) value.replaceFirstChar(Char::uppercase) else value) + sep
        }
        val gendered = if (lang != "hi" || gender == Gender.UNKNOWN) out
            else PAIR.replace(out) { if (gender == Gender.MASCULINE) it.groupValues[1] else it.groupValues[2] }
        return gendered.replace(Regex("\\s+"), " ").trim().replaceFirstChar(Char::uppercase)
    }

    companion object {
        /** Calibration knobs, log probabilities: an extra segment and a missed sign (the segmenter's measured rates
         * belong here once docs/10 Phase 2.5 has them). */
        private const val INSERT = -2.3f   // ln 0.1
        private const val DELETE = -3.0f   // ln 0.05
        private const val UNEXPLAINED_SHAKE = -1.6f   // ln 0.2
        /** The share of the prior that the intents answering the question on screen split between them. */
        private const val ON_QUESTION = 0.8f
        private val SLOT = Regex("\\{([A-Z]+)((?:\\.[a-z_]+)*)([^}A-Za-z_.]*)\\}")
        private val PAIR = Regex("(\\p{L}[\\p{L}\\p{M}]*)/(\\p{L}[\\p{L}\\p{M}]*)")
    }
}
