package app.signsetu.ui.avatar

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

enum class Character { CHILD, ADULT }

/** Colours of one character. Clothes are plain and regionally neutral (05-avatar-and-gestures §4). */
private class Look(
    val skin: Color, val skinShade: Color, val line: Color,
    val hair: Color, val hairShine: Color,
    val top: Color, val topShade: Color, val trim: Color,
    val iris: Color, val lip: Color, val blush: Color,
    val longSleeves: Boolean,
)

private val ChildLook = Look(
    skin = Color(0xFFC98D5E), skinShade = Color(0xFFAE7345), line = Color(0xFF7C4A27),
    hair = Color(0xFF2A1F1B), hairShine = Color(0xFF4A3A33),
    top = Color(0xFF2E9C93), topShade = Color(0xFF227E77), trim = Color(0xFFF6BE4B),
    iris = Color(0xFF3A2416), lip = Color(0xFF8C3A32), blush = Color(0x55F07C64),
    longSleeves = false,
)

private val AdultLook = Look(
    skin = Color(0xFFBC7E52), skinShade = Color(0xFF9F6842), line = Color(0xFF6E4123),
    hair = Color(0xFF1E1816), hairShine = Color(0xFF3A302C),
    top = Color(0xFF33405F), topShade = Color(0xFF232D47), trim = Color(0xFF6577A3),
    iris = Color(0xFF2E1D12), lip = Color(0xFF7E3A33), blush = Color(0x22F07C64),
    longSleeves = true,
)

/**
 * Draws a character and plays [gesture] once each time [playKey] changes. Speed below 1 plays slowly (FR-18).
 * With [idle] the character breathes and blinks; the system "remove animations" setting turns that off.
 * With [glance] the character looks down at the answers once the gesture is over.
 */
@Composable
fun Avatar(
    character: Character,
    gesture: Gesture?,
    playKey: Any?,
    modifier: Modifier = Modifier,
    speed: Float = 1f,
    idle: Boolean = true,
    /** Draws one still frame of the gesture instead of playing it. */
    atSeconds: Float? = null,
    glance: Boolean = false,
    onFinished: () -> Unit = {},
) {
    val build = if (character == Character.CHILD) ChildBuild else AdultBuild
    val look = if (character == Character.CHILD) ChildLook else AdultLook
    val timeline = remember(gesture, build) { Timeline(gesture, build) }
    val clock = remember { mutableFloatStateOf(0f) }
    val idleClock = remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current
    val still = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val finished by rememberUpdatedState(onFinished)
    val gaze = remember { Animatable(0f) }

    LaunchedEffect(playKey, timeline, speed, atSeconds) {
        gaze.snapTo(0f)
        clock.floatValue = atSeconds ?: 0f
        if (atSeconds != null) return@LaunchedEffect
        if (timeline.duration > 0f) {
            val start = withFrameNanos { it }
            while (clock.floatValue < timeline.duration) {
                withFrameNanos { now -> clock.floatValue = (now - start) / 1e9f * speed }
            }
            finished()
        }
        if (glance) gaze.animateTo(1f, tween(durationMillis = 500, delayMillis = 300))
    }
    if (idle && !still && atSeconds == null) {
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            // Breathing and blinking read fine at 20 fps; a redraw every vsync only costs the camera and battery.
            var last = start
            while (true) withFrameNanos { now ->
                if (now - last >= IDLE_FRAME_NS) {
                    idleClock.floatValue = (now - start) / 1e9f
                    last = now
                }
            }
        }
    }

    Canvas(modifier) {
        // State is read here, in the draw phase, so animation frames redraw without recomposing.
        val g = gaze.value
        val pose = timeline.pose(clock.floatValue).let { if (g > 0f) it.copy(nod = it.nod + g * 0.5f, gaze = g) else it }
        drawCharacter(build, look, pose, idleClock.floatValue, character == Character.CHILD)
    }
}

private const val IDLE_FRAME_NS = 50_000_000L

private fun DrawScope.drawCharacter(b: Build, look: Look, still: Pose, time: Float, child: Boolean) {
    // A resting body is never quite still: a slow sway, a drifting head and now and then a glance aside.
    val pose = still.copy(
        lean = still.lean + sin(time * 2f * PI.toFloat() / 6.5f) * 0.06f,
        tilt = still.tilt + sin(time * 2f * PI.toFloat() / 8.3f) * 1.4f,
        turn = still.turn + glanceAt(time),
    )
    val unit = size.height / 200f
    val ox = (size.width - 200f * unit) / 2f
    val breath = sin(time * 2f * PI.toFloat() / 3.8f) * 0.7f
    val shiver = if (pose.shiver > 0f) sin(time * 55f) * pose.shiver * 1.2f else 0f
    val blink = blinkAt(time)

    clipRect {
    translate(ox + shiver, 0f) {
        scale(unit, unit, pivot = Offset.Zero) {
            val bodyDy = breath * 0.4f + pose.hunch * 6f
            val shoulderLift = pose.shrug * 7f - breath * 0.5f
            // The neck goes under the collar so its base is hidden.
            drawNeck(b, look, pose.lean * 7f, bodyDy)
            translate(pose.lean * 6f, bodyDy) { drawTorso(b, look, shoulderLift, child) }
            val headDx = pose.lean * 8f + pose.turn * 2f
            val headDy = bodyDy + pose.nod * 6f + pose.hunch * 10f
            translate(headDx, headDy) {
                rotate(pose.tilt + pose.hunch * 8f, pivot = Offset(b.headX, b.neckTop + 4f)) {
                    drawHead(b, look, pose, blink, child)
                }
            }
            drawArm(b, look, pose.r, Side.R, Offset(pose.lean * 6f, bodyDy - shoulderLift), child)
            drawArm(b, look, pose.l, Side.L, Offset(pose.lean * 6f, bodyDy - shoulderLift), child)
        }
    }
    }
}

private fun glanceAt(time: Float): Float {
    val phase = time % 9f
    if (phase < 5f || phase > 6.4f) return 0f
    val side = if ((time / 9f).toInt() % 2 == 0) 1f else -1f
    return side * 0.28f * sin((phase - 5f) / 1.4f * PI.toFloat())
}

private fun blinkAt(time: Float): Float {
    if (time == 0f) return 1f
    val phase = time % 4.3f
    return if (phase < 0.14f) abs(phase - 0.07f) / 0.07f else 1f
}

private fun DrawScope.drawTorso(b: Build, look: Look, lift: Float, child: Boolean) {
    val c = Build.CENTER
    val sh = b.shoulderHalf
    val top = b.shoulderY - 9f - lift
    val torso = Path().apply {
        moveTo(c - sh - 12f, 214f)
        cubicTo(c - sh - 10f, b.shoulderY + 30f, c - sh - 12f, top + 4f, c - sh + 12f, top)
        lineTo(c + sh - 12f, top)
        cubicTo(c + sh + 12f, top + 4f, c + sh + 10f, b.shoulderY + 30f, c + sh + 12f, 214f)
        close()
    }
    drawPath(torso, Brush.verticalGradient(listOf(lerp(look.top, Color.White, 0.14f), look.top, look.topShade), startY = top, endY = 214f))
    drawPath(torso, look.line, style = Stroke(1.1f), alpha = 0.22f)
    // Soft fold shadows give the top some volume without lines.
    drawOval(look.topShade, Offset(c - sh - 6f, b.shoulderY + 26f), Size(14f, 60f), alpha = 0.55f)
    drawOval(look.topShade, Offset(c + sh - 8f, b.shoulderY + 26f), Size(14f, 60f), alpha = 0.55f)
    val chest = lerp(look.skin, look.skinShade, 0.45f)
    if (child) {
        drawArc(chest, 0f, 180f, true, Offset(c - 15f, top - 11f), Size(30f, 22f))
        drawArc(look.trim, 10f, 160f, false, Offset(c - 16f, top - 13f), Size(32f, 26f), style = Stroke(4.2f, cap = StrokeCap.Round))
    } else {
        val neck = Path().apply {
            moveTo(c - 13f, top + 0.5f); lineTo(c, top + 15f); lineTo(c + 13f, top + 0.5f)
        }
        drawPath(neck, chest)
        drawPath(neck, look.trim, style = Stroke(3.2f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawNeck(b: Build, look: Look, dx: Float, dy: Float) {
    val c = Build.CENTER + dx
    val w = b.neckWidth
    val top = b.neckTop + dy
    val base = b.shoulderY + dy + 2f
    // The neck flares into the shoulders so it reads as part of the body, not a post.
    val neck = Path().apply {
        moveTo(c - w / 2, top)
        lineTo(c - w / 2, base - 14f)
        quadraticTo(c - w / 2, base - 4f, c - w / 2 - 9f, base)
        lineTo(c + w / 2 + 9f, base)
        quadraticTo(c + w / 2, base - 4f, c + w / 2, base - 14f)
        lineTo(c + w / 2, top)
        close()
    }
    drawPath(neck, look.skinShade)
    drawRoundRect(look.skin, Offset(c - w / 2 + 1.5f, top), Size(w * 0.62f, base - top), CornerRadius(4f))
    drawOval(look.skinShade, Offset(c - w / 2, b.neckTop + dy - 2f), Size(w, 11f), alpha = 0.9f)
}

private fun DrawScope.drawHead(b: Build, look: Look, pose: Pose, blink: Float, child: Boolean) {
    val f = pose.face
    val hx = b.headX
    val hy = b.headY
    val fx = hx + pose.turn * 7f
    val fy = hy + pose.nod * 2.5f

    // Ears, then the head shape over them.
    val earY = hy + if (child) 5f else 3f
    for (s in floatArrayOf(-1f, 1f)) {
        val ex = hx + s * (b.headRx - 1f) - pose.turn * 3f
        drawOval(look.skin, Offset(ex - 6.5f, earY - 8.5f), Size(13f, 17f))
        drawOval(look.skinShade, Offset(ex - 3f + s * 0.5f, earY - 5f), Size(6f, 10f), alpha = 0.7f)
    }
    val headLight = Brush.radialGradient(
        listOf(lerp(look.skin, Color.White, 0.13f), look.skin, lerp(look.skin, look.skinShade, 0.7f)),
        center = Offset(hx - b.headRx * 0.28f, hy - b.headRy * 0.32f),
        radius = b.headRy * 1.35f,
    )
    drawOval(headLight, Offset(hx - b.headRx, hy - b.headRy), Size(b.headRx * 2, b.headRy * 2))
    drawOval(look.line, Offset(hx - b.headRx, hy - b.headRy), Size(b.headRx * 2, b.headRy * 2), style = Stroke(1.1f), alpha = 0.28f)

    drawHair(b, look, child, pose.turn)

    // Brows carry the question and worry (ISL facial grammar, 05 §4).
    val browY = fy - (if (child) 15f else 13f) - f.browRaise * 5f + f.browFrown * 2f
    val browGap = if (child) 7f else 6f
    val browLen = if (child) 13f else 12f
    for (s in floatArrayOf(-1f, 1f)) {
        val inner = Offset(fx + s * browGap, browY - f.browWorry * 4f + f.browFrown * 3.5f)
        val outer = Offset(fx + s * (browGap + browLen), browY + f.browWorry * 1.5f - f.browFrown * 1f + 1f)
        // A soft arch reads as friendly; it flattens as the brows furrow.
        val arch = (if (child) 3.2f else 2.4f) * (1f - f.browFrown)
        val brow = Path().apply {
            moveTo(inner.x, inner.y)
            quadraticTo((inner.x + outer.x) / 2f, minOf(inner.y, outer.y) - arch, outer.x, outer.y)
        }
        drawPath(brow, look.hair, style = Stroke(if (child) 3.4f else 3.2f, cap = StrokeCap.Round))
    }

    // Eyes: large and warm for the child, simple for the adult.
    val open = (f.eyeOpen * blink * (1f - pose.gaze * 0.18f)).coerceIn(0.05f, 1.2f)
    val eyeY = fy - 1f + pose.gaze * (if (child) 2.2f else 0.6f)
    for (s in floatArrayOf(-1f, 1f)) {
        val ex = fx + s * (if (child) 15f else 13f)
        if (f.eyeSmile > 0.55f || open < 0.18f) {
            val p = Path().apply {
                moveTo(ex - 6f, eyeY + 1f)
                quadraticTo(ex, eyeY - 6f * (if (open < 0.18f) -0.3f else 1f), ex + 6f, eyeY + 1f)
            }
            drawPath(p, look.iris, style = Stroke(2.6f, cap = StrokeCap.Round))
            continue
        }
        if (child) {
            val rx = 6.2f
            val ry = 7.6f * open
            val iris = Brush.radialGradient(listOf(lerp(look.iris, Color(0xFF9A6A45), 0.55f), look.iris), center = Offset(ex, eyeY + ry * 0.35f), radius = rx * 1.3f)
            drawOval(iris, Offset(ex - rx, eyeY - ry), Size(rx * 2, ry * 2))
            if (open > 0.5f) drawArc(look.hair, 215f, 110f, false, Offset(ex - rx - 0.6f, eyeY - ry - 0.8f), Size(rx * 2 + 1.2f, ry * 2 + 1.2f), style = Stroke(1.2f, cap = StrokeCap.Round))
            drawCircle(Color.White, 2.1f * open.coerceAtMost(1f), Offset(ex + 2.2f, eyeY - 2.6f * open))
            drawCircle(Color.White, 1f * open.coerceAtMost(1f), Offset(ex - 2.2f, eyeY + 2.8f * open))
        } else {
            val rx = 5.2f
            val ry = 3.7f * open
            drawOval(Color(0xFFF6EFE6), Offset(ex - rx, eyeY - ry), Size(rx * 2, ry * 2))
            val irisY = eyeY + 0.3f + pose.gaze * 1.2f
            val ir = minOf(3.2f, ry + 0.2f)
            drawCircle(lerp(look.iris, Color(0xFF8A5A36), 0.35f), ir, Offset(ex, irisY))
            drawCircle(look.iris, ir * 0.55f, Offset(ex, irisY))
            drawCircle(Color.White, 1f, Offset(ex + 1.1f, irisY - 1.2f))
            // An upper lid arc instead of a flat line keeps the adult calm rather than sleepy.
            drawArc(look.hair, 195f, 150f, false, Offset(ex - rx - 0.4f, eyeY - ry - 0.6f), Size(rx * 2 + 0.8f, ry * 2 + 1.2f), style = Stroke(1.5f, cap = StrokeCap.Round))
        }
        if (f.eyeSmile > 0.1f) {
            drawOval(look.skin, Offset(ex - 7f, eyeY + 7.5f - f.eyeSmile * 6f), Size(14f, 8f))
        }
    }

    if (child) {
        drawOval(look.blush, Offset(fx - 31f, fy + 8f), Size(14f, 8f))
        drawOval(look.blush, Offset(fx + 17f, fy + 8f), Size(14f, 8f))
    }
    // Nose.
    val noseY = fy + if (child) 8f else 9f
    val nose = Path().apply {
        moveTo(fx - 2.5f, noseY + 2f); quadraticTo(fx, noseY + 4.5f, fx + 2.5f, noseY + 2f)
    }
    drawPath(nose, look.line, style = Stroke(1.6f, cap = StrokeCap.Round), alpha = 0.7f)
    if (!child) drawLine(look.skinShade, Offset(fx + 1f, noseY - 9f), Offset(fx + 2f, noseY + 1f), 1.6f, StrokeCap.Round)

    drawMouth(fx, fy + if (child) 19f else 20f, f, look, child)
}

private fun DrawScope.drawHair(b: Build, look: Look, child: Boolean, turn: Float) {
    val hx = b.headX + turn * 2f
    val top = b.headY - b.headRy
    val rx = b.headRx
    val hair = Path()
    if (child) {
        hair.apply {
            moveTo(hx - rx - 1f, b.headY + 4f)
            // Control points sit high enough that the curve's crest covers the crown.
            cubicTo(hx - rx - 6f, top - 20f, hx + rx + 6f, top - 20f, hx + rx + 1f, b.headY + 4f)
            // Fringe in soft scallops across the forehead.
            lineTo(hx + rx - 3f, b.headY - 12f)
            quadraticTo(hx + rx - 12f, b.headY - 10f, hx + 14f, b.headY - 20f)
            quadraticTo(hx + 8f, b.headY - 13f, hx - 2f, b.headY - 20f)
            quadraticTo(hx - 10f, b.headY - 13f, hx - 17f, b.headY - 21f)
            quadraticTo(hx - rx + 10f, b.headY - 10f, hx - rx + 3f, b.headY - 12f)
            close()
        }
        drawPath(hair, Brush.verticalGradient(listOf(look.hairShine, look.hair, look.hair), startY = top - 20f, endY = b.headY))
        // A small tuft on top makes the silhouette friendly and unmistakably a child.
        val tuft = Path().apply {
            moveTo(hx - 2f, top + 2f); quadraticTo(hx + 4f, top - 11f, hx + 12f, top - 6f)
            quadraticTo(hx + 5f, top - 4f, hx + 4f, top + 3f); close()
        }
        drawPath(tuft, look.hair)
        drawArc(look.hairShine, 215f, 50f, false, Offset(hx - rx * 0.55f, top + 5f), Size(rx * 0.9f, 22f), style = Stroke(2.6f, cap = StrokeCap.Round))
    } else {
        hair.apply {
            moveTo(hx - rx - 1f, b.headY - 2f)
            cubicTo(hx - rx - 5f, top - 19f, hx + rx + 5f, top - 19f, hx + rx + 1f, b.headY - 2f)
            lineTo(hx + rx - 2f, b.headY - 12f)
            // The front sweeps away from a side part, which reads as a neat grown-up cut.
            quadraticTo(hx + 14f, b.headY - 27f, hx - 7f, b.headY - 23f)
            quadraticTo(hx - rx + 6f, b.headY - 21f, hx - rx + 2f, b.headY - 10f)
            close()
        }
        drawPath(hair, Brush.verticalGradient(listOf(look.hairShine, look.hair, look.hair), startY = top - 19f, endY = b.headY))
        drawArc(look.hairShine, 200f, 55f, false, Offset(hx - rx * 0.7f, top - 4f), Size(rx * 1.1f, 26f), style = Stroke(2f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawMouth(x: Float, y: Float, f: Face, look: Look, child: Boolean) {
    val w = if (child) 8.5f else 7.5f
    if (f.mouthRound > 0.5f) {
        drawOval(look.lip, Offset(x - 3.6f, y - 4f), Size(7.2f, 8.5f))
        return
    }
    val s = f.smile
    val corner = y - s * 2.2f
    val upper = Path().apply { moveTo(x - w, corner); quadraticTo(x, y + s * 6.5f, x + w, corner) }
    if (f.mouthOpen > 0.08f) {
        val open = f.mouthOpen * 9f
        val shape = Path().apply {
            moveTo(x - w, corner)
            quadraticTo(x, y + s * 3f - 1f, x + w, corner)
            quadraticTo(x, y + s * 6.5f + open, x - w, corner)
            close()
        }
        drawPath(shape, look.lip)
        drawOval(Color(0xFFE58A86), Offset(x - w * 0.45f, y + s * 2f + open * 0.45f), Size(w * 0.9f, open * 0.45f + 1f))
    } else {
        drawPath(upper, look.lip, style = Stroke(2.4f, cap = StrokeCap.Round))
    }
}

/** Shoulder to wrist with a two-bone solve, then the hand at the wrist. */
private fun DrawScope.drawArm(b: Build, look: Look, h: HandPose, side: Side, shift: Offset, child: Boolean) {
    val (sx0, sy0) = b.shoulder(side)
    val sx = sx0 + shift.x
    val sy = sy0 + shift.y
    val (ex, ey) = solveElbow(sx, sy, h.x, h.y, b.upperArm, b.foreArm, side, h.elbowOut)
    val s = Offset(sx, sy)
    val e = Offset(ex, ey)
    val w = Offset(h.x, h.y)
    val aw = b.armWidth

    // Both bones get a dark rim so an arm crossing the body or face never melts into it.
    if (look.longSleeves) {
        val sleeve = lerp(look.top, Color.White, 0.1f)
        val cuff = lerpOffset(e, w, 0.78f)
        drawLine(look.line, cuff, w, aw * 0.72f + 1.4f, StrokeCap.Round, alpha = 0.5f)
        drawLine(look.skin, cuff, w, aw * 0.72f, StrokeCap.Round)
        drawLine(look.topShade, s, e, aw + 2.6f, StrokeCap.Round)
        drawLine(look.topShade, e, cuff, aw * 0.92f + 2.6f, StrokeCap.Round)
        drawLine(sleeve, s, e, aw + 0.4f, StrokeCap.Round)
        drawLine(sleeve, e, cuff, aw * 0.92f - 0.6f, StrokeCap.Round)
        drawLine(look.trim, lerpOffset(e, w, 0.72f), cuff, aw * 0.95f, StrokeCap.Butt)
    } else {
        drawLine(look.line, s, e, aw * 0.86f + 1.4f, StrokeCap.Round, alpha = 0.4f)
        drawLine(look.line, e, w, aw * 0.78f + 1.4f, StrokeCap.Round, alpha = 0.4f)
        drawLine(look.skin, s, e, aw * 0.86f, StrokeCap.Round)
        drawLine(look.skin, e, w, aw * 0.78f, StrokeCap.Round)
        // Short sleeve over the top of the upper arm.
        val sleeveEnd = lerpOffset(s, e, 0.55f)
        drawLine(look.topShade, s, sleeveEnd, aw + 4f, StrokeCap.Round)
        drawLine(look.top, s, sleeveEnd, aw + 2.5f, StrokeCap.Round)
        drawLine(look.topShade, lerpOffset(s, e, 0.5f), sleeveEnd, aw + 2.5f, StrokeCap.Butt, alpha = 0.5f)
    }

    if (h.prop == "stick") {
        // A walking stick from the fist to the floor, behind the fingers.
        drawLine(Color(0xFF6B4423), Offset(w.x + 1f, w.y - 6f), Offset(w.x + 4f, 230f), 5f, StrokeCap.Round)
        drawLine(Color(0xFF8A5A33), Offset(w.x + 1f, w.y - 6f), Offset(w.x + 4f, 230f), 2f, StrokeCap.Round)
    }
    val angle = if (h.angle.isNaN()) Math.toDegrees(atan2((w.x - e.x).toDouble(), (-(w.y - e.y)).toDouble())).toFloat() else h.angle
    translate(w.x, w.y) {
        rotate(angle, pivot = Offset.Zero) {
            scale(b.handScale, pivot = Offset.Zero) {
                drawHand(look, h, side)
            }
        }
    }
}

private class Seg(val a: Offset, val b: Offset, val width: Float, val behind: Boolean, val shade: Float)

/**
 * Draws a hand in its own frame: wrist at the origin, fingers towards -y. Seen from the front (palm or back) curled
 * fingers are foreshortened; seen from the side they bend in the plane, which reads better for bunched or cupped hands.
 */
private fun DrawScope.drawHand(look: Look, h: HandPose, side: Side) {
    val roll = h.roll.coerceIn(-1f, 1f)
    val shape = h.shape
    val curls = shape.curls
    if (abs(roll) >= 0.35f) {
        val palmFacing = roll > 0f
        val thumbSide = if (palmFacing) -side.sign else side.sign
        val wf = lerp(0.6f, 1f, ((abs(roll) - 0.35f) / 0.65f).coerceIn(0f, 1f))
        val pw = 13f * wf
        val ph = 12f
        val fingers = mutableListOf<List<Seg>>()
        val lengths = floatArrayOf(9.2f, 10.2f, 9.6f, 7.7f)
        val widths = floatArrayOf(3.3f, 3.4f, 3.2f, 2.8f)
        for (k in 0..3) {
            val bx = thumbSide * (pw / 2f - 1.8f - k * (pw - 3.6f) / 3f)
            val dir = Math.toRadians((shape.spread * (k - 1.5f) * -9f * thumbSide).toDouble()).toFloat()
            val c = curls[k + 1]
            fingers += frontalChain(Offset(bx, -ph + 1.5f), dir, lengths[k], widths[k] * (0.82f + 0.18f * wf), c, palmFacing)
        }
        val thumbDir = Math.toRadians((thumbSide * (52f - shape.thumbAcross * 112f)).toDouble()).toFloat()
        val thumb = frontalChain(Offset(thumbSide * (pw / 2f - 0.5f), -3f), thumbDir, 8.8f, 3.7f, curls[0] * 0.6f, palmFacing, thumb = true)
        val thumbInFront = palmFacing && shape.thumbAcross > 0.5f

        // Palms are lighter than the back of the hand, so palm-out and palm-in read apart at a glance.
        val tone = if (palmFacing) lerp(look.skin, Color(0xFFF1C7A2), 0.38f) else look.skin
        if (!thumbInFront) drawChain(look, thumb, tone)
        fingers.forEach { segs -> drawChain(look, segs.filter { it.behind }, tone) }
        drawRoundRect(look.line, Offset(-pw / 2f - 0.7f, -ph - 0.7f), Size(pw + 1.4f, ph + 1.4f), CornerRadius(4.5f), alpha = 0.55f)
        drawRoundRect(tone, Offset(-pw / 2f, -ph), Size(pw, ph), CornerRadius(4f))
        if (palmFacing) {
            drawArc(look.skinShade, 200f, 110f, false, Offset(-pw * 0.35f, -ph * 0.75f), Size(pw * 0.7f, ph * 0.9f), style = Stroke(0.9f), alpha = 0.8f)
        }
        fingers.reversed().forEach { segs -> drawChain(look, segs.filter { !it.behind }, tone) }
        if (!palmFacing) {
            // Nails mark the back of the hand on straight fingers.
            for (k in 0..3) {
                if (curls[k + 1] < 0.35f) {
                    val tip = fingers[k].last()
                    drawLine(lerp(look.skin, Color.White, 0.32f), lerpOffset(tip.a, tip.b, 0.3f), lerpOffset(tip.a, tip.b, 0.78f), tip.width * 0.5f, StrokeCap.Round)
                }
            }
            // Knuckles of bent fingers.
            for (k in 0..3) {
                if (curls[k + 1] > 0.45f) {
                    val bx = thumbSide * (pw / 2f - 1.8f - k * (pw - 3.6f) / 3f)
                    drawCircle(look.skin, 1.9f, Offset(bx, -ph + 1f))
                    drawCircle(look.line, 1.9f, Offset(bx, -ph + 1f), style = Stroke(0.5f), alpha = 0.5f)
                }
            }
        }
        if (thumbInFront) drawChain(look, thumb, tone)
    } else {
        // Side view: the palm faces the body's midline unless rotated.
        val palmSide = -side.sign
        val nearThumb = roll >= 0f
        val ph = 12f
        drawRoundRect(look.line, Offset(-3.9f, -ph - 0.7f), Size(7.8f, ph + 1.4f), CornerRadius(3.5f), alpha = 0.55f)
        drawRoundRect(look.skin, Offset(-3.2f, -ph), Size(6.4f, ph), CornerRadius(3f))
        val lengths = floatArrayOf(9.2f, 10.2f, 9.6f, 7.7f)
        val order = if (nearThumb) intArrayOf(3, 2, 1, 0) else intArrayOf(0, 1, 2, 3)
        val thumbSegs = sideChain(
            Offset(palmSide * 2.4f, -3.5f),
            Math.toRadians((palmSide * (28f + shape.thumbAcross * 30f)).toDouble()).toFloat(),
            8.6f, 3.5f, curls[0], palmSide, 0.5f, 0f,
        )
        if (!nearThumb) drawChain(look, thumbSegs)
        for ((depth, k) in order.withIndex()) {
            val dir = Math.toRadians((shape.spread * (k - 1.5f) * 4f).toDouble()).toFloat()
            val base = Offset(palmSide * (0.6f - k * 0.35f) * (if (nearThumb) 1f else -1f), -ph + 1.2f + k * 0.8f)
            drawChain(look, sideChain(base, dir, lengths[k], 3.2f, curls[k + 1], palmSide, 1f, (3 - depth) * 0.12f))
        }
        if (nearThumb) drawChain(look, thumbSegs)
    }
}

private fun frontalChain(
    base: Offset, dir: Float, length: Float, width: Float, curl: Float, palmFacing: Boolean, thumb: Boolean = false,
): List<Seg> {
    val fractions = if (thumb) floatArrayOf(0.55f, 0.45f) else floatArrayOf(0.46f, 0.30f, 0.24f)
    val bends = if (thumb) floatArrayOf(40f, 80f) else floatArrayOf(85f, 100f, 72f)
    val ux = sin(dir)
    val uy = -cos(dir)
    var p = base
    var phi = 0f
    return fractions.indices.map { j ->
        phi += bends[j] * curl
        val len = length * fractions[j] * cos(Math.toRadians(phi.toDouble())).toFloat()
        val next = Offset(p.x + ux * len, p.y + uy * len)
        // Folded segments face away from a back-of-hand view, so they go behind the palm.
        val behind = !palmFacing && phi > 95f && !thumb
        Seg(p, next, width * (1f - j * 0.07f), behind, if (phi > 95f) 0.18f else 0f).also { p = next }
    }
}

private fun sideChain(
    base: Offset, dir: Float, length: Float, width: Float, curl: Float, palmSide: Float, scale: Float, shade: Float,
): List<Seg> {
    val fractions = floatArrayOf(0.46f, 0.30f, 0.24f)
    val bends = floatArrayOf(80f, 95f, 70f)
    var p = base
    var a = dir
    return fractions.indices.map { j ->
        a += Math.toRadians((palmSide * bends[j] * curl).toDouble()).toFloat()
        val len = length * fractions[j] * scale
        val next = Offset(p.x + sin(a) * len, p.y - cos(a) * len)
        Seg(p, next, width * (1f - j * 0.07f), false, shade).also { p = next }
    }
}

/** Each finger gets its own outline so neighbouring fingers stay readable. */
private fun DrawScope.drawChain(look: Look, segs: List<Seg>, tone: Color = look.skin) {
    if (segs.isEmpty()) return
    for (s in segs) drawLine(look.line, s.a, s.b, s.width + 1.4f, StrokeCap.Round, alpha = 0.7f)
    for (s in segs) {
        val color = if (s.shade > 0f) lerp(tone, look.skinShade, s.shade.coerceAtMost(1f) * 2.5f) else tone
        drawLine(color, s.a, s.b, s.width, StrokeCap.Round)
    }
}

private fun lerpOffset(a: Offset, b: Offset, f: Float) = Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
