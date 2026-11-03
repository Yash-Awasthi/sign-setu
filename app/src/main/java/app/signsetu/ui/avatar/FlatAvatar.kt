package app.signsetu.ui.avatar

import app.signsetu.data.content.Clip
import app.signsetu.data.content.Gesture
import app.signsetu.data.content.QuestionKind
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class Character { CHILD, ADULT }

/** The part of the 200-unit character space a view shows, in character units. */
internal enum class FlatFrame(val left: Float, val top: Float, val width: Float, val height: Float) {
    /** To the hips: both hands stay inside at rest, at waist level and flat on the head. */
    Body(-10f, 0f, 220f, 258f),
    Head(0f, 0f, 200f, 200f);

    companion object {
        fun of(view: AvatarView) = if (view == AvatarView.Head) Head else Body
    }
}


/** Colours of one character. Clothes are plain and regionally neutral. */
internal class Look(
    val skin: Color, val skinShade: Color, val line: Color, val outline: Color,
    val hair: Color, val hairShine: Color,
    val top: Color, val topShade: Color, val trim: Color, val trousers: Color,
    val iris: Color, val lip: Color, val blush: Color,
    val longSleeves: Boolean,
)

private val ChildLook = Look(
    skin = Color(0xFFC98D5E), skinShade = Color(0xFFAE7345), line = Color(0xFF7C4A27), outline = Color(0xFF4A2A14),
    hair = Color(0xFF2A1F1B), hairShine = Color(0xFF4A3A33),
    top = Color(0xFF2E9C93), topShade = Color(0xFF227E77), trim = Color(0xFFF6BE4B), trousers = Color(0xFF3D4A6B),
    iris = Color(0xFF3A2416), lip = Color(0xFF8C3A32), blush = Color(0x44F07C64),
    longSleeves = false,
)

private val AdultLook = Look(
    skin = Color(0xFFBC7E52), skinShade = Color(0xFF9F6842), line = Color(0xFF6E4123), outline = Color(0xFF3E2412),
    hair = Color(0xFF1E1816), hairShine = Color(0xFF3A302C),
    top = Color(0xFF3B4A6E), topShade = Color(0xFF2A3654), trim = Color(0xFF8394C2), trousers = Color(0xFF2B3448),
    iris = Color(0xFF2E1D12), lip = Color(0xFF7E3A33), blush = Color(0x22F07C64),
    longSleeves = true,
)

/** The drawn character, for phones where the 3D one cannot run: plays [gesture] or a recorded [clip] once each time
 * [playKey] changes. Speed below 1 plays slowly. With [idle] the character breathes and blinks; the system "remove
 * animations" setting turns that off. */
@Composable
internal fun FlatAvatar(
    gesture: Gesture?,
    playKey: Any?,
    modifier: Modifier = Modifier,
    speed: Float = 1f,
    idle: Boolean = true,
    atSeconds: Float? = null,
    glance: Boolean = false,
    view: AvatarView = AvatarView.Body,
    question: QuestionKind? = null,
    clip: Clip? = null,
    character: Character = Character.ADULT,
    onFinished: () -> Unit = {},
) {
    val frame = FlatFrame.of(view)
    val child = character == Character.CHILD
    val build = if (child) ChildBuild else AdultBuild
    val look = if (child) ChildLook else AdultLook
    val timeline = remember(gesture, build) { Timeline(gesture, build) }
    // A recorded clip plays in place of the keyframes, as on the 3D character.
    val length = clip?.seconds ?: timeline.duration
    val clock = remember { mutableFloatStateOf(0f) }
    val idleClock = remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current
    val still = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val finished by rememberUpdatedState(onFinished)
    val gaze = remember { Animatable(0f) }
    // The elbow drawn last frame, so a two-bone solve keeps its bend instead of flipping across the shoulder line.
    val elbows = remember { LastElbows() }

    LaunchedEffect(playKey, timeline, speed, atSeconds) {
        gaze.snapTo(0f)
        clock.floatValue = atSeconds ?: 0f
        if (atSeconds != null) return@LaunchedEffect
        if (length > 0f) {
            val start = withFrameNanos { it }
            while (clock.floatValue < length) {
                withFrameNanos { now -> clock.floatValue = (now - start) / 1e9f * speed }
            }
            finished()
        }
        if (glance) gaze.animateTo(1f, tween(durationMillis = 500, delayMillis = 300))
    }
    val idleOn = idle && !still && atSeconds == null
    if (idleOn) {
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            // 30 fps idle cadence provides fluid breathing, micro-expressions and eye darts
            var last = start
            while (true) withFrameNanos { now ->
                if (now - last >= IDLE_FRAME_NS) {
                    idleClock.floatValue = (now - start) / 1e9f
                    last = now
                }
            }
        }
    }

    Spacer(
        modifier.drawWithCache {
            val kit = Kit(build, look, child)
            onDrawBehind {
                // State is read here, in the draw phase, so animation frames redraw without recomposing.
                val g = gaze.value
                val t = clock.floatValue
                val d = length
                val playing = d > 0f && t < d
                val e = if (d > 0f) envelope(t, d) else 0f
                var pose = if (clip != null) clipPose(clip, t, build) else timeline.pose(t)
                if (g > 0f) pose = pose.copy(nod = pose.nod + g * 0.5f, gaze = g)
                if (question != null && e > 0f) pose = questionFace(pose, question, e)
                if (playing && !still) {
                    val f = pose.face
                    if (f.mouthOpen < 0.2f && f.mouthRound < 0.5f) {
                        val talk = 0.07f * (0.5f + 0.5f * sin(t * 2f * PI.toFloat() * 2.3f)) * e
                        val extraSmile = 0.05f * (0.5f + 0.5f * cos(t * 2f * PI.toFloat() * 1.5f)) * e
                        pose = pose.copy(face = f.copy(mouthOpen = f.mouthOpen + talk, smile = f.smile + extraSmile))
                    }
                }
                val blink = when {
                    still -> 1f
                    playing && atSeconds == null -> blinkAt(t)
                    idleOn -> blinkAt(idleClock.floatValue)
                    else -> 1f
                }
                drawCharacter(kit, frame, pose, if (idleOn) idleClock.floatValue else 0f, t, blink, !still, elbows)
            }
        },
    )
}

private const val IDLE_FRAME_NS = 33_333_333L

/** 0 to 1 over the lead-in, 1 while the gesture plays, back to 0 over the lead-out. */
private fun envelope(t: Float, duration: Float) =
    (t / Timeline.LEAD_IN).coerceIn(0f, 1f) * ((duration - t) / Timeline.LEAD_OUT).coerceIn(0f, 1f)

private fun questionFace(p: Pose, kind: QuestionKind, e: Float): Pose {
    val f = p.face
    return when (kind) {
        QuestionKind.YES_NO -> p.copy(
            face = f.copy(browRaise = maxOf(f.browRaise, 0.85f * e), eyeOpen = f.eyeOpen * (1f + 0.1f * e)),
            nod = p.nod - 0.15f * e,
        )
        QuestionKind.WH -> p.copy(
            face = f.copy(browFrown = maxOf(f.browFrown, 0.75f * e), browRaise = f.browRaise * (1f - e), eyeOpen = f.eyeOpen * (1f - 0.12f * e)),
            nod = p.nod + 0.12f * e,
            tilt = p.tilt + 2f * e,
        )
    }
}

/** Everything the draw reuses from frame to frame. Brushes are in character or hand units, so they stay valid
 * under the draw's translate, rotate and scale; paths are rewound and rebuilt each frame. */
internal class Kit(val build: Build, val look: Look, val child: Boolean) {
    val torsoBrush = Brush.verticalGradient(
        listOf(lerp(look.top, Color.White, 0.14f), look.top, look.topShade), startY = build.shoulderY - 20f, endY = 240f,
    )
    val headBrush = Brush.radialGradient(
        listOf(lerp(look.skin, Color.White, 0.13f), look.skin, lerp(look.skin, look.skinShade, 0.7f)),
        center = Offset(build.headX - build.headRx * 0.28f, build.headY - build.headRy * 0.32f),
        radius = build.headRy * 1.35f,
    )
    val hairBrush = (build.headY - build.headRy - if (child) 20f else 19f).let { top ->
        Brush.verticalGradient(listOf(look.hairShine, look.hair, look.hair), startY = top, endY = build.headY)
    }
    /** Child iris, drawn translated to the eye centre. */
    val irisBrush = Brush.radialGradient(listOf(lerp(look.iris, Color(0xFF9A6A45), 0.55f), look.iris), center = Offset(0f, 2.6f), radius = 6.2f * 1.3f)
    /** Back of the hand, darker towards the wrist; in hand units. */
    val backBrush = Brush.verticalGradient(listOf(look.skin, look.skin, lerp(look.skin, look.skinShade, 0.45f)), startY = -12f, endY = 0f)
    val palmTone = lerp(look.skin, Color(0xFFF3CFAE), 0.45f)
    val sleeve = lerp(look.top, Color.White, 0.1f)
    val chest = lerp(look.skin, look.skinShade, 0.45f)
    val knuckle = lerp(look.skin, Color.White, 0.18f)
    val nail = lerp(look.skin, Color(0xFFFFE9DA), 0.55f)
    val pad = lerp(palmTone, Color.White, 0.15f)
    val halo = Color.White.copy(alpha = 0.35f)
    val rim = look.outline.copy(alpha = 0.9f)
    /** The shadow a front hand casts on the one it touches, so two skins never merge into one shape. */
    val shadow = look.outline.copy(alpha = 0.3f)

    val torso = Path()
    val hair = Path()
    val tuft = Path()
    val curve = Path()
    val mouth = Path()
    val quad = Path()

    /** Hand segments, finger (index to pinky, then thumb) by segment: ax, ay, bx, by, width, bend, shade. */
    val seg = FloatArray(5 * 3 * SEG)
    var frontal = false
    var palmFacing = false
    var thumbSide = 1f
    var pw = 13f
    var nearThumb = false
    var palmSide = 1f
    /** Front hand on the other one: thicker, fully opaque outline so the two skins stay apart. */
    var contactStroke = false
}

/** Elbows as drawn on the previous frame, per side, in character space. */
internal class LastElbows {
    var r: Pair<Float, Float>? = null
    var l: Pair<Float, Float>? = null
}

private fun DrawScope.drawCharacter(
    k: Kit, view: FlatFrame, still: Pose, time: Float, gestureT: Float, blink: Float, secondary: Boolean,
    elbows: LastElbows,
) {
    val b = k.build
    // A resting body is never quite still: a slow sway, a drifting head and now and then a glance aside.
    val pose = still.copy(
        lean = still.lean + sin(time * 2f * PI.toFloat() / 6.5f) * 0.06f,
        tilt = still.tilt + sin(time * 2f * PI.toFloat() / 8.3f) * 1.4f,
        turn = still.turn + glanceAt(time),
    )
    val unit = size.height / view.height
    val ox = (size.width - view.width * unit) / 2f - view.left * unit
    val oy = -view.top * unit
    val breath = sin(time * 2f * PI.toFloat() / 3.8f) * 0.7f
    val shiver = if (pose.shiver > 0f) sin(gestureT * 55f) * pose.shiver * 1.2f else 0f
    var liftR = pose.shrug * 7f - breath * 0.5f
    var liftL = liftR
    var lean = pose.lean
    var tilt = pose.tilt
    if (secondary) {
        // A raised hand lifts its own shoulder, and the body leans a little towards where the hands are.
        liftR += 3f * ((b.shoulderY - pose.r.y) / 90f).coerceIn(0f, 1f)
        liftL += 3f * ((b.shoulderY - pose.l.y) / 90f).coerceIn(0f, 1f)
        val dl = 0.04f * (pose.r.x + pose.l.x - 200f) / 100f
        lean += dl
        tilt -= 1.5f * dl
    }

    clipRect {
    translate(ox + shiver, oy) {
        scale(unit, unit, pivot = Offset.Zero) {
            val bodyDy = breath * 0.4f + pose.hunch * 6f
            // The neck goes under the collar so its base is hidden.
            drawNeck(k, lean * 7f, bodyDy)
            translate(lean * 6f, bodyDy) { drawTorso(k, liftR, liftL) }
            val headDx = lean * 8f + pose.turn * 2f
            val headDy = bodyDy + pose.nod * 6f + pose.hunch * 10f
            translate(headDx, headDy) {
                rotate(tilt + pose.hunch * 8f, pivot = Offset(b.headX, b.neckTop + 4f)) {
                    drawHead(k, pose, blink)
                }
            }
            // The hand placed on the other one draws last, in front, and casts a shadow on the one behind.
            val shiftR = Offset(lean * 6f, bodyDy - liftR)
            val shiftL = Offset(lean * 6f, bodyDy - liftL)
            if (pose.rFront) {
                drawArm(k, pose.l, Side.L, shiftL, elbows = elbows)
                drawArm(k, pose.r, Side.R, shiftR, front = pose.handsTouch, elbows = elbows)
            } else {
                drawArm(k, pose.r, Side.R, shiftR, elbows = elbows)
                drawArm(k, pose.l, Side.L, shiftL, front = pose.handsTouch, elbows = elbows)
            }
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

/** 1 open, 0 shut. One blink per 4 s slot at a hashed offset, so gaps vary from 2.6 to 5.4 s; slot 0 blinks at once. */
private fun blinkAt(time: Float): Float {
    val n = (time / 4f).toInt()
    val offset = if (n == 0) 0f else (((n * 1103515245 + 12345) ushr 16) % 1000) / 1000f * 1.4f
    val p = time - n * 4f - offset
    return when {
        p < 0f || p >= 0.13f -> 1f
        p < 0.05f -> 1f - p / 0.05f
        else -> (p - 0.05f) / 0.08f
    }
}

