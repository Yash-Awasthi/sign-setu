package app.signsetu.camera

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.core.graphics.createBitmap
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/** The arms and hands of [seen] as a PNG, the stick figure kept with a camera read. */
fun renderSkeletonPng(seen: LandmarkFrame, width: Int = 360): ByteArray = renderSheetPng(listOf(listOf(seen)), emptyList(), width)

/** Every sign of a signed answer as a strip of stick figures from its start to its end, one row per sign in signing
 * order, each row headed by the word read for it: the motion kept in the record with every signed answer. Null when
 * no sign has a body in view. */
fun renderSignsPng(signs: List<List<LandmarkFrame>>, words: List<String> = emptyList(), steps: Int = SIGN_STEPS, panelWidth: Int = 150): ByteArray? {
    val rows = signs.mapIndexedNotNull { i, frames -> signSteps(frames, steps).takeIf { it.isNotEmpty() }?.let { it to (words.getOrNull(i) ?: "?") } }
    if (rows.isEmpty()) return null
    return renderSheetPng(rows.map { it.first }, rows.mapIndexed { n, (_, w) -> "${n + 1}. $w" }, panelWidth)
}

/** Frames spread evenly over the part of one sign where a hand is in view, so the strip shows its whole movement. */
internal fun signSteps(frames: List<LandmarkFrame>, steps: Int): List<LandmarkFrame> {
    val seen = frames.filter { it.hands.isNotEmpty() }.ifEmpty { frames.filter { it.pose != null } }
    if (seen.size <= steps) return seen
    return List(steps) { i -> seen[(i * (seen.size - 1).toFloat() / (steps - 1)).roundToInt()] }
}

private const val SIGN_STEPS = 6

private fun renderSheetPng(rows: List<List<LandmarkFrame>>, labels: List<String>, panelWidth: Int): ByteArray {
    // Kept to the frame's own shape, so arms are not stretched.
    val aspect = rows.first().first().aspect.coerceIn(0.3f, 3f)
    val panelHeight = (panelWidth / aspect).toInt()
    val cols = rows.maxOf { it.size }
    val gap = if (cols > 1 || rows.size > 1) 4 else 0
    val labelHeight = if (labels.isEmpty()) 0 else (panelWidth / 5f).toInt()
    val rowHeight = labelHeight + panelHeight
    val bitmap = createBitmap(cols * panelWidth + (cols - 1) * gap, rows.size * rowHeight + (rows.size - 1) * gap)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.rgb(18, 26, 32))
    val back = Paint().apply { color = android.graphics.Color.rgb(30, 42, 51) }
    val bone = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(255, 197, 107)
        strokeWidth = panelWidth / 50f
        strokeCap = Paint.Cap.ROUND
    }
    val joint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = labelHeight * 0.7f; isFakeBoldText = true }
    rows.forEachIndexed { r, frames ->
        val rowTop = r * (rowHeight + gap).toFloat()
        labels.getOrNull(r)?.let { canvas.drawText(it, 4f, rowTop + labelHeight * 0.78f, label) }
        frames.forEachIndexed { c, seen ->
            val left = c * (panelWidth + gap).toFloat()
            val top = rowTop + labelHeight
            canvas.drawRect(left, top, left + panelWidth, top + panelHeight, back)
            fun at(p: Offset) = Offset(left + p.x * panelWidth, top + p.y * panelHeight)
            fun line(a: Offset, b: Offset) { val p = at(a); val q = at(b); canvas.drawLine(p.x, p.y, q.x, q.y, bone) }
            fun dot(p: Offset, radius: Float) { val o = at(p); canvas.drawCircle(o.x, o.y, radius, joint) }
            seen.pose?.takeIf { it.size >= 33 }?.let { pose ->
                for ((i, j) in ArmBones) line(pose[i], pose[j])
                for (i in 11..16) dot(pose[i], panelWidth / 45f)
            }
            for (hand in seen.hands) {
                if (hand.size < 21) continue
                for ((i, j) in HandBones) line(hand[i], hand[j])
                for (p in hand) dot(p, panelWidth / 70f)
            }
        }
    }
    return try {
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }.toByteArray()
    } finally {
        bitmap.recycle()
    }
}

