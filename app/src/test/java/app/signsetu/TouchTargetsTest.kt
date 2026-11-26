package app.signsetu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The 48 dp touch floor and the one-handed reach band, checked on the JVM against the layout code
 * instead of a screenshot or a phone.
 *
 * The floor. A tap node's touch bounds are widened to `ViewConfiguration.minimumTouchTargetSize`,
 * which [app.signsetu.ui.theme.SubjectTouchTarget] raises to 56 dp on the person's screens; Material
 * 3 controls read the same number from `LocalMinimumInteractiveComponentSize`. No floor rescues a
 * chain that fixes its own width or height under 48 dp: the fixed size clips the widened bounds back
 * down. This test walks every interactive chain in the in-session surfaces and fails on one, unless a
 * `minimumInteractiveComponentSize()` further out already reserves the floor for a denser visual.
 *
 * The reach band. On the reference phone the arm's-length measurement used, 411 x 914 dp, a thumb
 * sweeping from the bottom covers roughly the lower two thirds of the screen. Every action the
 * person takes mid-session, answers and tools, therefore lives in one bottom-anchored column, inset
 * from both sides and lifted clear of the pinned need row. The top strip holds chrome only: the call
 * header, the camera window and the Stop corner. Whether that band truly lands under the thumb for a
 * given hand and phone is a question for the phone; it is not claimed here.
 */
class TouchTargetsTest {

    private val appSrc = File("src/main/java/app/signsetu")
    private val session = File(appSrc, "ui/session")

    private val inSession = listOf(
        session,
        File(appSrc, "ui/components"),
        File(appSrc, "ui/pair"),
    )

    private val floor = 48.0

    /** Every dp [app.signsetu.ui.theme.Space] offers, read from the token file. */
    private val space: Map<String, Double> = run {
        val body = File(appSrc, "ui/theme/Tokens.kt").readText()
            .substringAfter("object Space {")
            .substringBefore("\n}")
        Regex("""val (\w+) = ([\d.]+)\.dp""").findAll(body)
            .associate { it.groupValues[1] to it.groupValues[2].toDouble() }
    }

    /** Named dp constants the in-session files declare, such as `NEED_ROW_HEIGHT = Space.s84`. */
    private val named: Map<String, String> = run {
        val map = mutableMapOf<String, String>()
        ktFiles(inSession).forEach { file ->
            Regex("""(?:internal |private |const )?val (\w+)\s*=\s*(Space\.\w+|[A-Z_]+|[\d.]+\.dp)""")
                .findAll(file.readText())
                .forEach { m -> map[m.groupValues[1]] = m.groupValues[2] }
        }
        map
    }

    private data class Site(
        val where: String,
        val what: String,
        val short: List<String>,
        val unresolved: List<String>,
    )

    private fun ktFiles(dirs: List<File>): List<File> =
        dirs.flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .sortedBy { it.path }

    /** Dp parameters and their declared defaults, so `.height(height)` resolves to a real number. */
    private val paramDefaults: Map<String, List<String>> = run {
        val map = mutableMapOf<String, MutableList<String>>()
        ktFiles(inSession).forEach { file ->
            Regex("""(\w+):\s*Dp\s*=\s*([^,\n)]+)""").findAll(file.readText())
                .forEach { m -> map.getOrPut(m.groupValues[1]) { mutableListOf() } += m.groupValues[2] }
        }
        map
    }

    /** A `Space.x`, a `12.dp`, a named constant, a sum, a conditional, or a Dp parameter's default. */
    private fun resolve(expr: String, depth: Int = 0): Double? {
        if (depth > 6) return null
        val e = expr.trim().trimEnd(',').trim()
        named[e]?.let { return resolve(it, depth + 1) }
        Regex("""^Space\.(\w+)$""").find(e)?.let { return space[it.groupValues[1]] }
        Regex("""^([\d.]+)\.dp$""").find(e)?.let { return it.groupValues[1].toDoubleOrNull() }
        Regex("""^if\s*\(.*\)\s*(.+?)\s+else\s+(.+)$""").find(e)?.let { m ->
            val taken = listOf(m.groupValues[1], m.groupValues[2]).map { resolve(it, depth + 1) }
            return if (taken.all { it != null }) taken.filterNotNull().min() else null
        }
        if (e.contains('+')) return e.split('+').sumOf { part -> resolve(part, depth + 1) ?: return null }
        paramDefaults[e]?.mapNotNull { resolve(it, depth + 1) }?.minOrNull()?.let { return it }
        return null
    }

    /** The argument text of a call whose `(` sits at [open], nested parentheses included. */
    private fun callArgs(text: String, open: Int): String {
        var depth = 0
        var i = open
        while (i < text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return text.substring(open + 1, i)
                }
            }
            i++
        }
        return text.substring(open + 1)
    }

    private fun head(text: String, callStart: Int): String {
        val open = text.indexOf('(', callStart)
        return if (open < 0) "" else callArgs(text, open)
    }

    /** Start of the call whose argument list holds [at]. */
    private fun argHost(text: String, at: Int): Int {
        var depth = 0
        var i = at - 1
        while (i >= 0) {
            when (text[i]) {
                ')' -> depth++
                '(' -> if (depth == 0) {
                    var s = i - 1
                    while (s >= 0 && (text[s].isLetterOrDigit() || text[s] == '_' || text[s] == '.')) s--
                    return s + 1
                } else depth--
            }
            i--
        }
        return -1
    }

    /**
     * Start of the call that owns the lambda holding [at]. A modifier inside a call's argument list
     * belongs to that call; a node inside a block belongs to the call whose lambda opens the block.
     */
    private fun hostBlock(text: String, at: Int): Int {
        var depth = 0
        var i = at - 1
        while (i >= 0) {
            when (text[i]) {
                '}' -> depth++
                '{' -> if (depth == 0) {
                    var k = i - 1
                    while (k >= 0 && text[k].isWhitespace()) k--
                    if (k < 0 || text[k] != ')') return i
                    var d = 1
                    var j = k - 1
                    while (j >= 0 && d > 0) {
                        if (text[j] == ')') d++
                        if (text[j] == '(') d--
                        j--
                    }
                    var s = j
                    while (s >= 0 && (text[s].isLetterOrDigit() || text[s] == '_' || text[s] == '.')) s--
                    return s + 1
                } else depth--
            }
            i--
        }
        return -1
    }

    /** Index of the `}` that closes the trailing lambda of the call starting at [callStart]. */
    private fun blockEnd(text: String, callStart: Int): Int {
        val open = text.indexOf('(', callStart)
        if (open < 0) return callStart
        var depth = 0
        var i = open
        while (i < text.length && depth >= 0) {
            when (text[i]) {
                '(' -> depth++
                ')' -> depth--
            }
            if (depth == 0) break
            i++
        }
        if (i >= text.length) return text.length
        var k = i + 1
        while (k < text.length && text[k].isWhitespace()) k++
        if (k >= text.length || text[k] != '{') return i
        var braces = 0
        for (j in k until text.length) {
            when (text[j]) {
                '{' -> braces++
                '}' -> {
                    braces--
                    if (braces == 0) return j
                }
            }
        }
        return text.length
    }

    /**
     * Sizes a self-enforcing widget is handed as its own bounds: the `modifier` chain and an explicit
     * height, width or size argument. A lambda the widget draws inside is the child's business.
     */
    private fun sizeArguments(args: String): Pair<List<String>, List<String>> {
        val short = mutableListOf<String>()
        val unresolved = mutableListOf<String>()
        val sizeCall = Regex("""\.(height|width|size|heightIn|widthIn|sizeIn)\(""")
        topLevel(args).forEach { part ->
            val eq = part.indexOf('=')
            if (eq < 0) return@forEach
            val key = part.substring(0, eq).trim()
            if (key !in setOf("modifier", "height", "width", "size", "minHeight", "minWidth")) return@forEach
            val value = part.substring(eq + 1).trim()
            if (key == "modifier" || sizeCall.containsMatchIn(value)) {
                val (shortInChain, unresolvedInChain) = shortSizes(value)
                short += shortInChain
                unresolved += unresolvedInChain
            } else {
                val v = resolve(value)
                when {
                    v == null -> unresolved += "$key = $value"
                    v < floor -> short += "$key = $value (${v} dp)"
                }
            }
        }
        return short to unresolved
    }

    /** The arguments of a call split on the commas that sit outside a nested call, lambda or index. */
    private fun topLevel(args: String): List<String> {
        val out = mutableListOf<String>()
        var depth = 0
        var start = 0
        args.forEachIndexed { i, c ->
            when (c) {
                '(', '{', '[' -> depth++
                ')', '}', ']' -> depth--
                ',' -> if (depth == 0) {
                    out += args.substring(start, i)
                    start = i + 1
                }
            }
        }
        out += args.substring(start)
        return out
    }

    /**
     * Fixed sizes and maximum sizes in a modifier chain that sit under the floor. A size declared
     * after `minimumInteractiveComponentSize()` is excused: that outer node reserves the floor, so the
     * dense visual inside keeps its size while the tap bounds stay 48 dp or more.
     */
    private fun shortSizes(text: String): Pair<List<String>, List<String>> {
        val short = mutableListOf<String>()
        val unresolved = mutableListOf<String>()
        val excusedFrom = text.indexOf("minimumInteractiveComponentSize()")
        fun check(label: String, expr: String, at: Int) {
            val value = resolve(expr)
            when {
                value == null -> unresolved += "$label = $expr"
                value < floor && (excusedFrom < 0 || at < excusedFrom) -> short += "$label = $expr (${value} dp)"
            }
        }
        Regex("""\.(height|width|size|requiredHeight|requiredWidth|requiredSize)\(""").findAll(text).forEach { m ->
            callArgs(text, m.range.last).split(',').map { it.trim() }.filter { it.isNotEmpty() }
                .forEach { arg -> check(m.groupValues[1], arg, m.range.first) }
        }
        Regex("""\.(heightIn|widthIn|sizeIn)\(""").findAll(text).forEach { m ->
            callArgs(text, m.range.last).split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { part ->
                val eq = part.indexOf('=')
                if (eq > 0 && part.substring(0, eq).trim() == "max") check("max", part.substring(eq + 1).trim(), m.range.first)
            }
        }
        return short to unresolved
    }

    /**
     * Every modifier chain in a file, as its own text: from the `Modifier`/`modifier` the chain hangs
     * off, through the calls that follow it, stopping where the node's content or a sibling starts.
     */
    private fun chains(text: String): List<Pair<Int, String>> {
        val lines = text.lines()
        val out = mutableListOf<Pair<Int, String>>()
        val token = Regex("""\b(?:Modifier|modifier)\b""")
        var i = 0
        while (i < lines.size) {
            val trimmed = lines[i].trimStart()
            val at = token.find(lines[i])?.range?.first
                ?: if (trimmed.startsWith(".")) lines[i].indexOf('.') else null
            if (at == null) {
                i++
                continue
            }
            var depth = 0
            val sb = StringBuilder(lines[i].substring(at))
            fun braces(s: String) = s.count { it == '{' } - s.count { it == '}' }
            depth += braces(sb.toString())
            var j = i
            while (depth <= 0 && j + 1 < lines.size && lines[j + 1].trimStart().startsWith(".")) {
                j++
                sb.append('\n').append(lines[j].trim())
                depth += braces(lines[j])
            }
            out += lines.subList(0, i).sumOf { it.length + 1 } + at to sb.toString()
            i = j + 1
        }
        return out
    }

    private fun audit(): List<Site> {
        val sites = mutableListOf<Site>()
        val interactive = Regex("""\.(clickable|selectable|toggleable|combinedClickable|runnable)\(""")
        val widget = Regex(
            """\b(Button|TextButton|OutlinedButton|FilledTonalButton|ElevatedButton|IconButton|FilledIconButton|""" +
                """OutlinedIconButton|FloatingActionButton|FilterChip|AssistChip|InputChip|Switch|Checkbox|""" +
                """RadioButton|Slider|PressableSurface|SmallAnswer|StagePrimaryAction|StageToolBar)\(""",
        )
        ktFiles(inSession).forEach { file ->
            val text = file.readText()
            val path = file.path.replace('\\', '/').substringAfter("src/main/java/app/signsetu/")
            fun lineOf(offset: Int) = text.substring(0, offset).count { it == '\n' } + 1

            chains(text).forEach { (offset, chain) ->
                if (!interactive.containsMatchIn(chain)) return@forEach
                val (short, unresolved) = shortSizes(chain)
                sites += Site("$path:${lineOf(offset)}", "tap " + chain.substringAfter('\n').trim().take(60), short, unresolved)
            }

            widget.findAll(text).forEach { m ->
                val name = m.groupValues[1]
                val args = head(text, m.range.first)
                val (short, unresolved) = sizeArguments(args)
                sites += Site(
                    "$path:${lineOf(m.range.first)}",
                    "$name(" + args.replace(Regex("""\s+"""), " ").trim().take(60),
                    short,
                    unresolved,
                )
            }
        }
        return sites.sortedBy { it.where }
    }

    @Test
    fun everyInSessionTapKeepsThe48DpFloor() {
        val theme = File(appSrc, "ui/theme/Theme.kt").readText()
        val subject = Regex("""val SubjectTouchTarget = ([\d.]+)\.dp""").find(theme)?.groupValues?.get(1)?.toDoubleOrNull()
        assertTrue("SubjectTouchTarget is missing from Theme.kt", subject != null)
        assertTrue("the person's floor is $subject dp, under the 48 dp requirement", subject!! >= floor)
        assertTrue(
            "the person's screens no longer widen the tap bounds",
            theme.contains(Regex("""override val minimumTouchTargetSize =\s*DpSize\(\s*SubjectTouchTarget,\s*SubjectTouchTarget\s*\)""")),
        )
        assertTrue(
            "the person's screens no longer raise Material's interactive floor",
            theme.contains("LocalMinimumInteractiveComponentSize provides SubjectTouchTarget"),
        )

        val sites = audit()
        val taps = sites.filter { it.what.startsWith("tap ") }
        println("TOUCH AUDIT: ${taps.size} interactive chains, ${sites.size} checked sites, in ui/session, ui/components, ui/pair")
        sites.groupBy { it.where.substringBefore(':') }.toSortedMap()
            .forEach { (file, list) -> println("  %-30s %3d".format(file, list.size)) }
        sites.filter { it.short.isNotEmpty() || it.unresolved.isNotEmpty() }.forEach {
            println("  ${it.where}  ${it.what}  ${it.short.joinToString()} ${it.unresolved.joinToString()}")
        }

        val short = sites.flatMap { site -> site.short.map { "${site.where} ${site.what}: $it" } }
        assertTrue(
            "taps that fix a size, or a maximum, under ${floor.toInt()} dp, so the widened touch bounds are clipped:\n" +
                short.joinToString("\n"),
            short.isEmpty(),
        )
        val unresolved = sites.flatMap { site -> site.unresolved.map { "${site.where} ${site.what}: $it" } }
        assertTrue(
            "sizes this check cannot read, so the floor is unproven for them:\n" + unresolved.joinToString("\n"),
            unresolved.isEmpty(),
        )
        assertTrue("the scan found only ${taps.size} interactive chains; the in-session tree is missing", taps.size >= 20)
        assertTrue("the scan found only ${sites.size} in-session sites to check", sites.size >= 140)
    }

    @Test
    fun theFloorCheckBites() {
        fun short(chain: String) = shortSizes(chain).first

        assertEquals("a 44 dp tap must be reported", 1, short(".height(Space.s44).clickable(role = Role.Button) { onOpen() }").size)
        assertEquals("a 36 dp width must be reported", 1, short(".width(Space.s36).toggleable(value = on) { }").size)
        assertEquals("a maximum under the floor must be reported", 1, short(".heightIn(min = Space.s48, max = Space.s44).clickable { }").size)
        assertEquals(
            "a fixed size outside the floor's node still clips the target",
            1,
            short(".height(Space.s36).minimumInteractiveComponentSize().clickable { }").size,
        )
        assertTrue("a minimum is not a maximum", short(".heightIn(min = Space.s56).clickable { }").isEmpty())
        assertTrue("an exactly 48 dp tap is allowed", short(".height(Space.s48).clickable { }").isEmpty())
        assertTrue(
            "a dense visual inside the reserved floor is not clipped",
            short(".minimumInteractiveComponentSize().height(Space.s36).clickable { }").isEmpty(),
        )
    }

    @Test
    fun everyPersonActionHostSitsInTheBottomBand() {
        val src = File(session, "VideoCallStage.kt").readText()
        val answers = src.indexOf("testTag(StagePaths.ANSWERS)")
        val tools = src.indexOf("testTag(StagePaths.TOOLS)")
        assertTrue("the answers or the tools left the stage", answers > 0 && tools > answers)

        // The band: the one bottom-aligned column that holds both the answers and the tools.
        val band = Regex("""\bColumn\(""").findAll(src).map { it.range.first }
            .filter { head(src, it).contains("Alignment.BottomCenter") }
            .firstOrNull { it < answers && blockEnd(src, it) > tools }
        assertTrue("no single bottom-aligned column holds the answers and the tools", band != null)
        val bandHead = head(src, band!!)
        val needRow = resolve(named["NEED_ROW_HEIGHT"] ?: "") ?: 0.0
        assertTrue("the need row's height is not declared", needRow >= floor)

        val inset = Regex("""bottom\s*=\s*([\w.\s+]+?)\s*,\s*(?:start|end)\s*=""").find(bandHead)
            ?.groupValues?.get(1)?.let { resolve(it) }
        assertTrue("the band has no bottom inset above the pinned need row: $bandHead", inset != null)
        assertTrue("the band's bottom inset ($inset dp) does not clear the need row ($needRow dp)", inset!! >= needRow)
        assertTrue(
            "the band lost a side inset, so a thumb has to reach across the glass",
            bandHead.contains("start = Space.lg") && bandHead.contains("end = Space.lg"),
        )

        // The pin: the need row is its own bottom-aligned node, inset from both sides.
        val needAt = src.indexOf("NeedRow(")
        val need = callArgs(src, src.indexOf('(', needAt))
        assertTrue("the pinned need row is not bottom-anchored: $need", need.contains("Alignment.BottomCenter"))
        assertTrue("the need row lost its side inset", need.contains("start = Space.md") && need.contains("end = Space.md"))

        // The tool sheet's pull handle, the affordance that opens the rest of the toolset.
        val handle = hostBlock(src, src.indexOf("SlidePanelHandle("))
        assertTrue("no host above the pull handle", handle >= 0)
        assertTrue("the pull handle is not bottom-anchored: ${head(src, handle)}", head(src, handle).contains("Alignment.BottomCenter"))

        // Arithmetic of the band on the reference phone, 411 x 914 dp: the lowest action row's top
        // edge, once the inset, the tool bar and the tallest answer row are taken off the height.
        val toolBarArgs = head(src, src.indexOf("StageToolBar("))
        val toolBar = 2 * (Regex("""vertical = (Space\.\w+)""").find(toolBarArgs)?.groupValues?.get(1)?.let { resolve(it) } ?: 0.0) +
            (Regex("""\.heightIn\(min = (Space\.\w+)\)""").find(File(session, "StageTools.kt").readText())
                ?.groupValues?.get(1)?.let { resolve(it) } ?: 0.0)
        val gap = Regex("""Arrangement\.spacedBy\((Space\.\w+)\)""").find(src.substring(answers))?.groupValues?.get(1)
            ?.let { resolve(it) } ?: 0.0
        val answerRow = Regex("""height = (Space\.\w+)""").findAll(src.substring(band, blockEnd(src, band)))
            .mapNotNull { resolve(it.groupValues[1]) }.maxOrNull() ?: 0.0
        val window = 914.0
        val lowestTop = window - inset - toolBar - gap - answerRow
        val fraction = lowestTop / window
        println(
            "REACH BAND: window $window dp, inset $inset dp, tool bar $toolBar dp, gap $gap dp, answer row $answerRow dp" +
                " -> the lowest action starts at ${"%.1f".format(fraction * 100)}% of the height",
        )
        assertTrue("the tool bar or the answer row did not resolve ($toolBar dp, $answerRow dp)", toolBar > floor && answerRow > floor)
        assertTrue(
            "the lowest action row sits at ${"%.1f".format(fraction * 100)}% of a 914 dp window, outside the band a thumb reaches",
            fraction in 0.60..0.90,
        )

        // The top strip is chrome only: no answer or tool host may live inside a top-aligned block.
        Regex("""\.align\(Alignment\.Top\w+\)""").findAll(src).forEach { m ->
            val host = argHost(src, m.range.first)
            assertTrue("a top-aligned modifier with no host at ${m.value}", host >= 0)
            val end = blockEnd(src, host)
            val leaked = listOf("StagePaths.ANSWERS", "StagePaths.TOOLS", "SlidePanelHandle(", "NeedRow(")
                .filter { src.indexOf(it) in (host + 1) until end }
            assertTrue("${m.value} holds ${leaked.joinToString()} in the top strip, out of a thumb's reach", leaked.isEmpty())
        }
    }
}
