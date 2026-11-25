package app.signsetu.ui.session

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** What a drag on the drawing board leaves: a free line, or a shape whose two ends are the drag's ends. */
internal enum class DrawTool { PEN, LINE, RECT, OVAL, ARROW }

/** Points on an oval outline: enough that the joins read as a curve at any phone size. */
private const val OVAL_STEPS = 48

/** How far the arrow's head spans, as a share of the shaft. */
private const val ARROW_HEAD = 0.28f

/** A shape's box must be at least this wide or tall (canvas fractions) or the drag was a tap. */
private const val MIN_SHAPE = 0.01f

/**
 * The lines a drag from the first to the last point of [trail] leaves on the board, in the canvas fractions the
 * strokes are kept in. A pen keeps every point of the gesture; every other tool ignores the middle of the drag and
 * snaps a shape between its two ends, so the same list is what the screen draws, what the PNG gets and what undo takes
 * back.
 */
internal fun drawnLines(tool: DrawTool, trail: List<Offset>): List<List<Offset>> {
    if (trail.isEmpty()) return emptyList()
    if (tool == DrawTool.PEN) return listOf(trail)
    // A tap is not a shape: a few pixels of wobble must not leave a sliver nobody meant to draw.
    if (shapeSpan(trail) < MIN_SHAPE) return emptyList()
    val from = trail.first()
    val to = trail.last()
    return when (tool) {
        DrawTool.PEN -> listOf(trail)
        DrawTool.LINE -> listOf(listOf(from, to))
        DrawTool.RECT -> listOf(listOf(from, Offset(to.x, from.y), to, Offset(from.x, to.y), from))
        DrawTool.OVAL -> listOf(oval(from, to))
        DrawTool.ARROW -> arrow(from, to)
    }
}

private fun oval(from: Offset, to: Offset): List<Offset> {
    val cx = (from.x + to.x) / 2
    val cy = (from.y + to.y) / 2
    val rx = abs(to.x - from.x) / 2
    val ry = abs(to.y - from.y) / 2
    return (0..OVAL_STEPS).map { i ->
        val a = 2 * PI * i / OVAL_STEPS
        Offset(cx + rx * cos(a).toFloat(), cy + ry * sin(a).toFloat())
    }
}

/** A shaft, then two head lines from the tip back at [ARROW_HEAD] of its length on either side. */
private fun arrow(from: Offset, to: Offset): List<List<Offset>> {
    val length = hypot(to.x - from.x, to.y - from.y)
    if (length < 1e-4f) return listOf(listOf(from, to))
    val ux = (to.x - from.x) / length
    val uy = (to.y - from.y) / length
    val reach = length * ARROW_HEAD
    // 30 degrees either side of the shaft, pointing back from the tip.
    val cos30 = cos(Math.toRadians(30.0)).toFloat()
    val sin30 = sin(Math.toRadians(30.0)).toFloat()
    fun head(sign: Float) = Offset(to.x - reach * (ux * cos30 - sign * uy * sin30), to.y - reach * (uy * cos30 + sign * ux * sin30))
    return listOf(listOf(from, to), listOf(to, head(1f)), listOf(to, head(-1f)))
}

/** The longest side of a shape's box in fractions; a shape smaller than this is a tap, not a shape. */
internal fun shapeSpan(trail: List<Offset>): Float {
    if (trail.isEmpty()) return 0f
    val from = trail.first()
    val to = trail.last()
    return max(abs(to.x - from.x), abs(to.y - from.y))
}
