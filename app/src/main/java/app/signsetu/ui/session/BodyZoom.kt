package app.signsetu.ui.session

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.signsetu.ui.theme.Space

/** The parts too small or too crowded on the whole figure to point at exactly; each gets a close-up. */
internal val ZOOM_PARTS = setOf(BodyPart.HAND, BodyPart.FOOT, BodyPart.CHEST, BodyPart.BELLY)

/** Where in the figure's 0..1 box a tap at ([x], [y]) of the close-up lands: the close-up is [box] enlarged. */
internal fun zoomPoint(box: Rect, x: Float, y: Float): Offset =
    Offset(box.left + x.coerceIn(0f, 1f) * box.width, box.top + y.coerceIn(0f, 1f) * box.height)

/** A point of the figure in the close-up's own 0..1 coordinates, for drawing its mark on the enlarged part. */
internal fun zoomFraction(box: Rect, p: Offset): Offset =
    Offset(((p.x - box.left) / box.width).coerceIn(0f, 1f), ((p.y - box.top) / box.height).coerceIn(0f, 1f))

/** The part alone, drawn to fill the pad's 0..1 box: enough of its shape that the finger knows where it is. */
internal fun DrawScope.drawZoomPart(part: BodyPart, line: Color, fill: Color) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = Space.xxs.toPx())
    fun box(l: Float, t: Float, r: Float, b: Float, round: Float) {
        val tl = Offset(l * w, t * h)
        val sz = Size((r - l) * w, (b - t) * h)
        drawRoundRect(fill, tl, sz, CornerRadius(round * w))
        drawRoundRect(line, tl, sz, CornerRadius(round * w), style = stroke)
    }
    when (part) {
        BodyPart.HAND -> {
            // Four fingers over a palm, and a thumb out to the side.
            box(0.34f, 0.12f, 0.46f, 0.52f, 0.03f)
            box(0.45f, 0.06f, 0.57f, 0.52f, 0.03f)
            box(0.56f, 0.12f, 0.68f, 0.52f, 0.03f)
            box(0.30f, 0.42f, 0.72f, 0.92f, 0.08f)
            box(0.12f, 0.48f, 0.31f, 0.72f, 0.06f)
        }
        BodyPart.FOOT -> {
            box(0.34f, 0.22f, 0.66f, 0.92f, 0.10f)
            for (x in listOf(0.34f, 0.46f, 0.58f)) drawCircle(fill, 0.055f * w, Offset((x + 0.06f) * w, 0.18f * h))
            for (x in listOf(0.34f, 0.46f, 0.58f)) drawCircle(line, 0.055f * w, Offset((x + 0.06f) * w, 0.18f * h), style = stroke)
        }
        BodyPart.CHEST -> {
            box(0.18f, 0.16f, 0.82f, 0.94f, 0.10f)
            drawLine(line, Offset(0.50f * w, 0.20f * h), Offset(0.24f * w, 0.34f * h), Space.xxs.toPx())
            drawLine(line, Offset(0.50f * w, 0.20f * h), Offset(0.76f * w, 0.34f * h), Space.xxs.toPx())
            drawLine(line, Offset(0.50f * w, 0.34f * h), Offset(0.50f * w, 0.86f * h), Space.s1_5.toPx())
        }
        BodyPart.BELLY -> {
            box(0.22f, 0.12f, 0.78f, 0.94f, 0.12f)
            drawCircle(line, 0.03f * w, Offset(0.50f * w, 0.52f * h), style = stroke)
            drawLine(line, Offset(0.30f * w, 0.40f * h), Offset(0.70f * w, 0.40f * h), 1f)
            drawLine(line, Offset(0.30f * w, 0.66f * h), Offset(0.70f * w, 0.66f * h), 1f)
        }
        else -> Unit
    }
}
