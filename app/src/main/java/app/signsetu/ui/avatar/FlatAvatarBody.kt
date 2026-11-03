package app.signsetu.ui.avatar

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import kotlin.math.atan2
import kotlin.math.hypot

internal fun DrawScope.drawTorso(k: Kit, liftR: Float, liftL: Float) {
    val b = k.build
    val look = k.look
    val c = Build.CENTER
    val sh = b.shoulderHalf
    val sy = b.shoulderY
    val nb = b.neckWidth / 2f + 2f
    // The character's right side is on the viewer's left.
    k.torso.apply {
        rewind()
        moveTo(c - sh - 3f, 262f)
        lineTo(c - sh - 3f, 228f)
        cubicTo(c - sh - 3f, 205f, c - sh - 12f, sy + 60f, c - sh - 12f, sy + 30f)
        lineTo(c - sh - 12f, sy + 6f - liftR)
        quadraticTo(c - sh - 12f, sy - 6f - liftR, c - sh, sy - 6f - liftR)
        quadraticTo(c - sh * 0.55f, sy - 15f - liftR, c - nb, sy - 17f - liftR)
        lineTo(c + nb, sy - 17f - liftL)
        quadraticTo(c + sh * 0.55f, sy - 15f - liftL, c + sh, sy - 6f - liftL)
        quadraticTo(c + sh + 12f, sy - 6f - liftL, c + sh + 12f, sy + 6f - liftL)
        lineTo(c + sh + 12f, sy + 30f)
        cubicTo(c + sh + 12f, sy + 60f, c + sh + 3f, 205f, c + sh + 3f, 228f)
        lineTo(c + sh + 3f, 262f)
        close()
    }
    drawPath(k.torso, k.torsoBrush)
    drawPath(k.torso, look.line, style = Stroke(1.1f), alpha = 0.22f)
    // Soft fold shadows give the top some volume without lines.
    drawOval(look.topShade, Offset(c - sh - 6f, sy + 26f), Size(14f, 60f), alpha = 0.55f)
    drawOval(look.topShade, Offset(c + sh - 8f, sy + 26f), Size(14f, 60f), alpha = 0.55f)
    val w = sh + 3f
    if (k.child) {
        drawRect(look.topShade, Offset(c - w, 236f), Size(w * 2f, 3f))
        drawRect(look.trousers, Offset(c - w, 239f), Size(w * 2f, 23f))
    } else {
        drawRect(look.trousers, Offset(c - w, 241f), Size(w * 2f, 21f))
        drawRect(Color(0xFF2B2F3A), Offset(c - w, 236f), Size(w * 2f, 5f))
        drawRoundRect(look.trim, Offset(c - 3f, 236f), Size(6f, 5f), CornerRadius(1f))
    }
    val collar = sy - 17f - (liftR + liftL) / 2f
    if (k.child) {
        drawArc(k.chest, 0f, 180f, true, Offset(c - 12f, collar - 9f), Size(24f, 18f))
        drawArc(look.trim, 10f, 160f, false, Offset(c - 13f, collar - 10f), Size(26f, 21f), style = Stroke(3.6f, cap = StrokeCap.Round))
    } else {
        k.curve.apply { rewind(); moveTo(c - 10f, collar + 0.5f); lineTo(c, collar + 14f); lineTo(c + 10f, collar + 0.5f) }
        drawPath(k.curve, k.chest)
        drawPath(k.curve, look.trim, style = Stroke(3f, cap = StrokeCap.Round))
    }
}

internal fun DrawScope.drawNeck(k: Kit, dx: Float, dy: Float) {
    val b = k.build
    val c = Build.CENTER + dx
    val w = b.neckWidth
    val top = b.neckTop + dy
    val base = b.shoulderY - 10f + dy
    drawRoundRect(k.look.skinShade, Offset(c - w / 2, top), Size(w, base - top), CornerRadius(4f))
    drawRoundRect(k.look.skin, Offset(c - w / 2 + 1.5f, top), Size(w * 0.62f, base - top), CornerRadius(4f))
    drawOval(k.look.skinShade, Offset(c - w / 2, top - 2f), Size(w, 11f), alpha = 0.9f)
}

internal fun DrawScope.drawHead(k: Kit, pose: Pose, blink: Float) {
    val b = k.build
    val look = k.look
    val child = k.child
    val f = pose.face
    val hx = b.headX
    val hy = b.headY
    val fx = hx + pose.turn * 7f
    val fy = hy + pose.nod * 2.5f

    val earY = hy + if (child) 5f else 3f
    for (s in SIDES) {
        val ex = hx + s * (b.headRx - 1f) - pose.turn * 3f
        drawOval(look.skin, Offset(ex - 6.5f, earY - 8.5f), Size(13f, 17f))
        drawOval(look.skinShade, Offset(ex - 3f + s * 0.5f, earY - 5f), Size(6f, 10f), alpha = 0.7f)
    }
    drawOval(k.headBrush, Offset(hx - b.headRx, hy - b.headRy), Size(b.headRx * 2, b.headRy * 2))
    drawOval(look.line, Offset(hx - b.headRx, hy - b.headRy), Size(b.headRx * 2, b.headRy * 2), style = Stroke(1.1f), alpha = 0.28f)

    drawHair(k, pose.turn)

    // Brows carry the question and worry: ISL facial grammar.
    val browY = fy - (if (child) 15f else 13f) - f.browRaise * 5f + f.browFrown * 2f
    val browGap = if (child) 7f else 6f
    val browLen = if (child) 13f else 12f
    for (s in SIDES) {
        val ix = fx + s * browGap
        val iy = browY - f.browWorry * 4f + f.browFrown * 3.5f
        val ox = fx + s * (browGap + browLen)
        val oy = browY + f.browWorry * 1.5f - f.browFrown * 1f + 1f
        // A soft arch reads as friendly; it flattens as the brows furrow.
        val arch = (if (child) 3.2f else 2.4f) * (1f - f.browFrown)
        k.curve.apply { rewind(); moveTo(ix, iy); quadraticTo((ix + ox) / 2f, minOf(iy, oy) - arch, ox, oy) }
        drawPath(k.curve, look.hair, style = Stroke(if (child) 3.4f else 3.2f, cap = StrokeCap.Round))
    }

    // Eyes: large and warm for the child, simple for the adult.
    val open = (f.eyeOpen * blink * (1f - pose.gaze * 0.18f)).coerceIn(0.05f, 1.2f)
    val eyeY = fy - 1f + pose.gaze * (if (child) 2.2f else 0.6f)
    for (s in SIDES) {
        val ex = fx + s * (if (child) 15f else 13f)
        if (f.eyeSmile > 0.55f || open < 0.18f) {
            k.curve.apply {
                rewind(); moveTo(ex - 6f, eyeY + 1f)
                quadraticTo(ex, eyeY - 6f * (if (open < 0.18f) -0.3f else 1f), ex + 6f, eyeY + 1f)
            }
            drawPath(k.curve, look.iris, style = Stroke(2.6f, cap = StrokeCap.Round))
            continue
        }
        if (child) {
            val rx = 6.2f
            val ry = 7.6f * open
            translate(ex, eyeY) { drawOval(k.irisBrush, Offset(-rx, -ry), Size(rx * 2, ry * 2)) }
            if (open > 0.5f) drawArc(look.hair, 215f, 110f, false, Offset(ex - rx - 0.6f, eyeY - ry - 0.8f), Size(rx * 2 + 1.2f, ry * 2 + 1.2f), style = Stroke(1.2f, cap = StrokeCap.Round))
            drawCircle(Color.White, 2.1f * open.coerceAtMost(1f), Offset(ex + 2.2f, eyeY - 2.6f * open))
            drawCircle(Color.White, 1f * open.coerceAtMost(1f), Offset(ex - 2.2f, eyeY + 2.8f * open))
        } else {
            val rx = 5.2f
            val ry = 3.7f * open
            drawOval(Color(0xFFF6EFE6), Offset(ex - rx, eyeY - ry), Size(rx * 2, ry * 2))
            val irisY = eyeY + 0.3f + pose.gaze * 1.2f
            val ir = minOf(3.4f, ry + 0.2f)
            drawCircle(lerp(look.iris, Color(0xFF8A5A36), 0.35f), ir, Offset(ex, irisY))
            drawCircle(look.iris, ir * 0.55f, Offset(ex, irisY))
            drawCircle(Color.White, 1f, Offset(ex + 1.1f, irisY - 1.2f))
            // An upper lid arc instead of a flat line keeps the adult calm rather than sleepy.
            drawArc(look.hair, 195f, 150f, false, Offset(ex - rx - 0.4f, eyeY - ry - 0.6f), Size(rx * 2 + 0.8f, ry * 2 + 1.2f), style = Stroke(1.7f, cap = StrokeCap.Round))
        }
        if (f.eyeSmile > 0.1f) {
            drawOval(look.skin, Offset(ex - 7f, eyeY + 7.5f - f.eyeSmile * 6f), Size(14f, 8f))
        }
    }

    if (child) {
        drawOval(look.blush, Offset(fx - 31f, fy + 8f), Size(14f, 8f))
        drawOval(look.blush, Offset(fx + 17f, fy + 8f), Size(14f, 8f))
    }
    val noseY = fy + if (child) 8f else 9f
    k.curve.apply { rewind(); moveTo(fx - 2.5f, noseY + 2f); quadraticTo(fx, noseY + 4.5f, fx + 2.5f, noseY + 2f) }
    drawPath(k.curve, look.line, style = Stroke(1.6f, cap = StrokeCap.Round), alpha = 0.7f)
    if (!child) drawLine(look.skinShade, Offset(fx + 1f, noseY - 9f), Offset(fx + 2f, noseY + 1f), 1.6f, StrokeCap.Round)

    drawMouth(k, fx, fy + if (child) 19f else 20f, f)
}

private val SIDES = floatArrayOf(-1f, 1f)

private fun DrawScope.drawHair(k: Kit, turn: Float) {
    val b = k.build
    val look = k.look
    val hx = b.headX + turn * 2f
    val top = b.headY - b.headRy
    val rx = b.headRx
    val hair = k.hair
    hair.rewind()
    if (k.child) {
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
        drawPath(hair, k.hairBrush)
        // A small tuft on top makes the silhouette friendly and unmistakably a child.
        k.tuft.apply {
            rewind()
            moveTo(hx - 2f, top + 2f); quadraticTo(hx + 4f, top - 11f, hx + 12f, top - 6f)
            quadraticTo(hx + 5f, top - 4f, hx + 4f, top + 3f); close()
        }
        drawPath(k.tuft, look.hair)
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
        drawPath(hair, k.hairBrush)
        drawArc(look.hairShine, 200f, 55f, false, Offset(hx - rx * 0.7f, top - 4f), Size(rx * 1.1f, 26f), style = Stroke(2f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawMouth(k: Kit, x: Float, y: Float, f: Face) {
    val look = k.look
    val w = if (k.child) 8.5f else 7.5f
    if (f.mouthRound > 0.5f) {
        drawOval(look.lip, Offset(x - 3.6f, y - 4f), Size(7.2f, 8.5f))
        return
    }
    val s = f.smile
    val corner = y - s * 2.2f
    val p = k.mouth
    p.rewind()
    // The low threshold lets the small mouth movement while signing show.
    if (f.mouthOpen > 0.04f) {
        val open = f.mouthOpen * 9f
        p.moveTo(x - w, corner)
        p.quadraticTo(x, y + s * 3f - 1f, x + w, corner)
        p.quadraticTo(x, y + s * 6.5f + open, x - w, corner)
        p.close()
        drawPath(p, look.lip)
        drawOval(Color(0xFFE58A86), Offset(x - w * 0.45f, y + s * 2f + open * 0.45f), Size(w * 0.9f, open * 0.45f + 1f))
    } else {
        p.moveTo(x - w, corner)
        p.quadraticTo(x, y + s * 6.5f, x + w, corner)
        drawPath(p, look.lip, style = Stroke(2.4f, cap = StrokeCap.Round))
    }
}

/** A bone as a quad tapering from width [wa] at [a] to [wb] at [b]. */
private fun DrawScope.quad(k: Kit, a: Offset, b: Offset, wa: Float, wb: Float, color: Color) {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len = hypot(dx, dy).coerceAtLeast(1e-3f)
    val nx = -dy / len
    val ny = dx / len
    k.quad.apply {
        rewind()
        moveTo(a.x + nx * wa / 2f, a.y + ny * wa / 2f)
        lineTo(b.x + nx * wb / 2f, b.y + ny * wb / 2f)
        lineTo(b.x - nx * wb / 2f, b.y - ny * wb / 2f)
        lineTo(a.x - nx * wa / 2f, a.y - ny * wa / 2f)
        close()
    }
    drawPath(k.quad, color)
}

/** Shoulder to wrist with a recorded or two-bone solved elbow, then the hand at the wrist. [front] marks the hand
 *  drawn over the other one, which gets a contact shadow. */
internal fun DrawScope.drawArm(
    k: Kit, h: HandPose, side: Side, shift: Offset, front: Boolean = false, elbows: LastElbows,
) {
    val b = k.build
    val look = k.look
    val (sx0, sy0) = b.shoulder(side)
    val s = Offset(sx0 + shift.x, sy0 + shift.y)
    val last = if (side == Side.R) elbows.r else elbows.l
    val (ex, ey) = h.elbowAt(s.x, s.y, b, side, last)
    if (side == Side.R) elbows.r = ex to ey else elbows.l = ex to ey
    val e = Offset(ex, ey)
    val w = Offset(h.x, h.y)
    val aw = b.armWidth
    val wrist = 0.62f * 13f * b.handScale
    val angle = if (h.angle.isNaN()) Math.toDegrees(atan2((w.x - e.x).toDouble(), (-(w.y - e.y)).toDouble())).toFloat() else h.angle
    layoutHand(k, h, side)

    // The adult's sleeve is rolled just past the elbow, so most of the forearm shows.
    val fore = if (look.longSleeves) lerpOffset(e, w, 0.15f) else e
    val foreW = aw * 0.95f
    val elbowW = aw
    // Halo first, under everything, so the forearm and hand separate from a dark shirt or backdrop.
    quad(k, fore, w, foreW + 3f, wrist + 3f, k.halo)
    drawCircle(k.halo, wrist / 2f + 1.5f, w)
    // A front hand resting on the other one casts a short shadow on it, so the silhouettes stay apart.
    if (front) translate(1.4f, 1.8f) { handFrame(w, angle, b.handScale) { drawHandShadow(k) } }
    handFrame(w, angle, b.handScale) { drawHandHalo(k) }

    if (look.longSleeves) {
        quad(k, fore, w, foreW + 1.6f, wrist + 1.6f, k.rim)
        drawCircle(k.rim, wrist / 2f + 0.8f, w)
        quad(k, fore, w, foreW, wrist, look.skin)
        drawCircle(look.skin, wrist / 2f, w)
        val cuff = lerpOffset(e, w, 0.22f)
        quad(k, s, e, aw * 1.2f + 4.4f, elbowW + 4.4f, look.topShade)
        drawCircle(look.topShade, elbowW / 2f + 2.2f, e)
        quad(k, e, cuff, elbowW + 4.4f, foreW + 4.4f, look.topShade)
        quad(k, s, e, aw * 1.2f + 2f, elbowW + 2f, k.sleeve)
        drawCircle(k.sleeve, elbowW / 2f + 1f, e)
        quad(k, e, cuff, elbowW + 2f, foreW + 2f, k.sleeve)
        val len = hypot(w.x - e.x, w.y - e.y).coerceAtLeast(1e-3f)
        val band = lerpOffset(e, w, (0.22f - 4f / len).coerceAtLeast(0.05f))
        quad(k, band, cuff, foreW + 4.4f, foreW + 4.4f, look.topShade)
        quad(k, band, cuff, foreW + 3f, foreW + 3f, look.trim)
    } else {
        quad(k, s, e, aw * 1.2f + 2.4f, elbowW + 2.4f, k.rim)
        drawCircle(k.rim, elbowW / 2f + 1.2f, e)
        quad(k, e, w, foreW + 1.6f, wrist + 1.6f, k.rim)
        drawCircle(k.rim, wrist / 2f + 0.8f, w)
        quad(k, s, e, aw * 1.2f, elbowW, look.skin)
        drawCircle(look.skin, elbowW / 2f, e)
        quad(k, e, w, foreW, wrist, look.skin)
        drawCircle(look.skin, wrist / 2f, w)
        val sleeveEnd = lerpOffset(s, e, 0.5f)
        drawLine(look.topShade, s, sleeveEnd, aw * 1.2f + 4f, StrokeCap.Round)
        drawLine(look.top, s, sleeveEnd, aw * 1.2f + 2.5f, StrokeCap.Round)
        drawLine(look.topShade, lerpOffset(s, e, 0.45f), sleeveEnd, aw * 1.2f + 2.5f, StrokeCap.Butt, alpha = 0.5f)
    }

    if (h.prop == "stick") {
        // A walking stick from the fist to the floor, behind the fingers.
        drawLine(Color(0xFF6B4423), Offset(w.x + 1f, w.y - 6f), Offset(w.x + 4f, 262f), 5f, StrokeCap.Round)
        drawLine(Color(0xFF8A5A33), Offset(w.x + 1f, w.y - 6f), Offset(w.x + 4f, 262f), 2f, StrokeCap.Round)
    }
    k.contactStroke = front
    handFrame(w, angle, b.handScale) { drawHand(k, h) }
}

private inline fun DrawScope.handFrame(w: Offset, angle: Float, handScale: Float, crossinline block: DrawScope.() -> Unit) {
    translate(w.x, w.y) {
        rotate(angle, pivot = Offset.Zero) {
            scale(handScale, pivot = Offset.Zero) { block() }
        }
    }
}
