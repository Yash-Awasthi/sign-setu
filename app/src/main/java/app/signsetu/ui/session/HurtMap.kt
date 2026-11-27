package app.signsetu.ui.session

import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.signsetu.R
import java.util.Locale
import app.signsetu.ui.theme.Space
import app.signsetu.ui.theme.Radius
import app.signsetu.ui.theme.StageTones
import app.signsetu.ui.theme.StageScheme

enum class BodySide(val key: String) { FRONT("hurt_front"), BACK("hurt_back") }

/** A part of the body map; [label] names it in the record. */
enum class BodyPart(val label: Int) {
    HEAD(R.string.part_head), FACE(R.string.part_face), NECK(R.string.part_neck), SHOULDER(R.string.part_shoulder),
    CHEST(R.string.part_chest), BELLY(R.string.part_belly), PRIVATE(R.string.part_private), ARM(R.string.part_arm),
    HAND(R.string.part_hand), LEG(R.string.part_leg), FOOT(R.string.part_foot), UPPER_BACK(R.string.part_upper_back),
    LOWER_BACK(R.string.part_lower_back), BOTTOM(R.string.part_bottom),
}

/** A touch on the map answers as the pain card; where it hurts travels in the answer's cue. */
const val HURT_CARD = "pain"

/** Where the person touched: the side shown, the point in the figure's own 0..1 box, the part found there, and
 * whether it is on the person's own left (mirrored on the front view, as a picture of someone facing you is). */
data class BodyTouch(val side: BodySide, val x: Float, val y: Float, val part: BodyPart, val personsLeft: Boolean?) {
    /** The record's cue: side, point to 3 decimals, part and the person's side, e.g. "body:front:0.512:0.420:chest:left". */
    val cue: String get() = "body:%s:%.3f:%.3f:%s:%s".format(Locale.ROOT, side.name.lowercase(), x, y, part.name.lowercase(), when (personsLeft) { true -> "left"; false -> "right"; null -> "middle" })

    companion object {
        fun parse(cue: String?): BodyTouch? {
            val f = cue?.removePrefix("cue:")?.split(':')?.takeIf { it.size == 6 && it[0] == "body" } ?: return null
            return runCatching {
                BodyTouch(BodySide.valueOf(f[1].uppercase()), f[2].toFloat(), f[3].toFloat(), BodyPart.valueOf(f[4].uppercase()),
                    when (f[5]) { "left" -> true; "right" -> false; else -> null })
            }.getOrNull()
        }
    }
}

// Zones in fractions of a 1:2 box; the figure is symmetric, so one list serves both halves.
private val CENTRE = listOf(
    BodyPart.HEAD to Rect(0.38f, 0.00f, 0.62f, 0.07f),
    BodyPart.FACE to Rect(0.38f, 0.07f, 0.62f, 0.13f),
    BodyPart.NECK to Rect(0.43f, 0.13f, 0.57f, 0.17f),
    BodyPart.CHEST to Rect(0.30f, 0.17f, 0.70f, 0.33f),
    BodyPart.BELLY to Rect(0.30f, 0.33f, 0.70f, 0.47f),
    BodyPart.PRIVATE to Rect(0.36f, 0.47f, 0.64f, 0.55f),
)
private val SIDES = listOf(
    BodyPart.SHOULDER to Rect(0.18f, 0.16f, 0.30f, 0.22f),
    BodyPart.ARM to Rect(0.12f, 0.22f, 0.29f, 0.46f),
    BodyPart.HAND to Rect(0.06f, 0.46f, 0.24f, 0.55f),
    BodyPart.LEG to Rect(0.31f, 0.55f, 0.50f, 0.92f),
    BodyPart.FOOT to Rect(0.27f, 0.92f, 0.50f, 1.00f),
)
private val BACK_CENTRE = mapOf(BodyPart.FACE to BodyPart.HEAD, BodyPart.CHEST to BodyPart.UPPER_BACK, BodyPart.BELLY to BodyPart.LOWER_BACK, BodyPart.PRIVATE to BodyPart.BOTTOM)

/** The part's box on the figure, for the close-up. A paired part is the side actually touched: [near] is the
 *  touch's x, so the viewer-right copy is the mirror of the one in the list. */
internal fun bodyBox(part: BodyPart, near: Float = 0f): Rect? {
    val r = (CENTRE + SIDES).firstOrNull { it.first == part }?.second ?: return null
    val paired = SIDES.any { it.first == part }
    return if (paired && near > 0.5f) Rect(1f - r.right, r.top, 1f - r.left, r.bottom) else r
}

/** The part under ([x], [y]) on [side], or null off the figure. */
internal fun bodyHit(side: BodySide, x: Float, y: Float): BodyTouch? {
    val p = Offset(x, y)
    CENTRE.firstOrNull { it.second.contains(p) }?.let { (part, _) ->
        val shown = if (side == BodySide.BACK) BACK_CENTRE[part] ?: part else part
        // The private zone and the bottom are one place each; the trunk is split by the midline.
        val lr = if (part == BodyPart.HEAD || part == BodyPart.FACE || part == BodyPart.NECK || part == BodyPart.PRIVATE) null else (x > 0.5f) == (side == BodySide.FRONT)
        return BodyTouch(side, x, y, shown, lr)
    }
    val mirrored = Offset(1f - x, y)
    for ((part, r) in SIDES) {
        val left = r.contains(p)
        if (left || r.contains(mirrored)) {
            // On the front view the person's left is on the viewer's right.
            val personsLeft = if (side == BodySide.FRONT) !left else left
            return BodyTouch(side, x, y, part, personsLeft)
        }
    }
    return null
}

/** The index of the mark a close-up corrects: the last touch on the close-up's own part and side, not the last mark
 *  in the list, which may belong to the other side. -1 when that close-up has no mark yet. */
internal fun markToRefine(marks: List<BodyTouch>, part: BodyPart, side: BodySide): Int =
    marks.indexOfLast { it.part == part && it.side == side }

/** The body for a frightened person to show where it hurts: front and back, any point on the figure. Every touch
 * is kept with its exact place, so the record shows where the finger went, not only a named part. A part too small to
 * hit exactly (hand, foot, chest, belly) opens a close-up under the figure, where the same finger lands on the exact
 * point that is then recorded. [subjectText] gives the sides in the person's language ("hurt_front", "hurt_back");
 * each chip also draws its side, for a person who cannot read. */
@Composable
fun HurtMap(onTouch: (BodyTouch) -> Unit, modifier: Modifier = Modifier, subjectText: (String) -> String? = { null }) {
    var side by rememberSaveable { mutableStateOf(BodySide.FRONT) }
    var zoom by remember { mutableStateOf<Pair<BodyPart, Rect>?>(null) }
    // A close-up belongs to the side it was opened on; the other view must not keep it, nor correct its mark.
    LaunchedEffect(side) { zoom = null }
    val marks = remember { mutableStateListOf<BodyTouch>() }
    val ink = StageScheme.onSurfaceVariant
    val skin = StageTones.bodySkin
    val mark = StageTones.bodyMark
    val description = stringResource(R.string.hurt_map)
    val touch by rememberUpdatedState(onTouch)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.s6)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            for (s in BodySide.entries) {
                FilterChip(side == s, { side = s }, { Text(subjectText(s.key) ?: stringResource(if (s == BodySide.FRONT) R.string.hurt_front else R.string.hurt_back)) }, shape = RoundedCornerShape(Radius.r14),
                    leadingIcon = { Canvas(Modifier.size(Space.s18)) { drawSideGlyph(s, ink, skin) } })
            }
        }
        Canvas(
            Modifier.weight(1f).aspectRatio(0.5f, matchHeightConstraintsFirst = true).padding(Space.xs)
                .semantics { contentDescription = description }
                .pointerInput(side) {
                    detectTapGestures { p ->
                        bodyHit(side, p.x / size.width, p.y / size.height)?.let { t ->
                            marks += t
                            touch(t)
                            zoom = if (t.part in ZOOM_PARTS) bodyBox(t.part, t.x)?.let { t.part to it } else null
                        }
                    }
                },
        ) {
            drawBody(side, ink, skin)
            marks.filter { it.side == side }.forEach { t ->
                val c = Offset(t.x * size.width, t.y * size.height)
                drawCircle(mark.copy(alpha = 0.25f), Space.s14.toPx(), c)
                drawCircle(mark, Space.s6.toPx(), c)
            }
        }
        zoom?.let { (part, box) ->
            ZoomPad(part, box, marks.lastOrNull { it.part == part && it.side == side }?.let { Offset(it.x, it.y) }, onPick = { zx, zy ->
                val p = zoomPoint(box, zx, zy)
                bodyHit(side, p.x, p.y)?.let { refined ->
                    // The close-up corrects the coarse touch: one place per part, so the record keeps the exact point.
                    val at = markToRefine(marks, part, side)
                    if (at >= 0) marks.removeAt(at)
                    marks += refined
                    touch(refined)
                }
            })
        }
    }
}

/** The part enlarged to fill a wide, low pad: a tap here lands on the exact point of the figure, which is what the
 * record keeps. [mark] is the touch already placed, shown so a correction is visible. */
@Composable
private fun ZoomPad(part: BodyPart, box: Rect, mark: Offset?, onPick: (Float, Float) -> Unit) {
    val ink = StageScheme.onSurfaceVariant
    val skin = StageTones.bodySkin
    val markColor = StageTones.bodyMark
    val name = stringResource(R.string.hurt_zoom, stringResource(part.label))
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(part.label), style = MaterialTheme.typography.labelLarge)
        Canvas(
            Modifier.fillMaxWidth().height(Space.s104).padding(Space.xs)
                .semantics { contentDescription = name }
                .pointerInput(part) { detectTapGestures { p -> onPick(p.x / size.width, p.y / size.height) } },
        ) {
            drawRoundRect(Color.White, Offset.Zero, Size(size.width, size.height), CornerRadius(Space.lg.toPx()))
            drawZoomPart(part, ink, skin)
            mark?.let { m ->
                val f = zoomFraction(box, m)
                drawCircle(markColor.copy(alpha = 0.25f), Space.s14.toPx(), Offset(f.x * size.width, f.y * size.height))
                drawCircle(markColor, Space.s6.toPx(), Offset(f.x * size.width, f.y * size.height))
            }
        }
    }
}

/** A head and shoulders: seen from the front with eyes and a mouth, from the back as hair only. */
private fun DrawScope.drawSideGlyph(side: BodySide, ink: Color, skin: Color) {
    val w = size.width
    val head = Offset(w / 2, w * 0.3f)
    val r = w * 0.22f
    drawRoundRect(skin, Offset(w * 0.18f, w * 0.58f), Size(w * 0.64f, w * 0.42f), CornerRadius(w * 0.2f))
    drawRoundRect(ink, Offset(w * 0.18f, w * 0.58f), Size(w * 0.64f, w * 0.42f), CornerRadius(w * 0.2f), style = Stroke(w * 0.07f))
    if (side == BodySide.FRONT) {
        drawCircle(skin, r, head)
        drawCircle(ink, r, head, style = Stroke(w * 0.07f))
        drawCircle(ink, w * 0.035f, Offset(w * 0.42f, w * 0.27f))
        drawCircle(ink, w * 0.035f, Offset(w * 0.58f, w * 0.27f))
        drawLine(ink, Offset(w * 0.43f, w * 0.38f), Offset(w * 0.57f, w * 0.38f), w * 0.05f)
    } else {
        drawCircle(ink, r, head)
    }
}

private fun DrawScope.drawBody(side: BodySide, ink: Color, skin: Color) {
    val w = size.width
    val h = size.height
    val line = Stroke(Space.xxs.toPx())
    fun part(r: Rect, round: Float) {
        val tl = Offset(r.left * w, r.top * h)
        val sz = Size(r.width * w, r.height * h)
        drawRoundRect(skin, tl, sz, CornerRadius(round * w))
        drawRoundRect(ink, tl, sz, CornerRadius(round * w), style = line)
    }
    drawOval(skin, Offset(0.38f * w, 0f), Size(0.24f * w, 0.13f * h))
    drawOval(ink, Offset(0.38f * w, 0f), Size(0.24f * w, 0.13f * h), style = line)
    part(Rect(0.43f, 0.125f, 0.57f, 0.17f), 0.02f)
    part(Rect(0.28f, 0.165f, 0.72f, 0.55f), 0.1f)
    for (mirror in listOf(false, true)) {
        fun m(r: Rect) = if (mirror) Rect(1 - r.right, r.top, 1 - r.left, r.bottom) else r
        part(m(Rect(0.14f, 0.18f, 0.28f, 0.47f)), 0.07f)
        part(m(Rect(0.08f, 0.46f, 0.22f, 0.54f)), 0.07f)
        part(m(Rect(0.32f, 0.54f, 0.49f, 0.93f)), 0.08f)
        part(m(Rect(0.28f, 0.92f, 0.49f, 0.99f)), 0.05f)
    }
    if (side == BodySide.FRONT) {
        // A face on the front only, so the two views are told apart at a glance.
        drawCircle(ink, 0.012f * w, Offset(0.45f * w, 0.055f * h))
        drawCircle(ink, 0.012f * w, Offset(0.55f * w, 0.055f * h))
        drawArc(ink, 20f, 140f, false, Offset(0.45f * w, 0.07f * h), Size(0.1f * w, 0.03f * h), style = line)
    } else {
        drawLine(ink, Offset(0.5f * w, 0.19f * h), Offset(0.5f * w, 0.47f * h), Space.s1_5.toPx())
    }
}
