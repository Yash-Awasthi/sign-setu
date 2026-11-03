package app.signsetu.ui.avatar

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/* A 2D upper-body rig shared by both characters. Character space is 200 x 200,
 * y down, facing the viewer; gestures.json keyframes are character-relative, so both play the same file. */

enum class Side(val sign: Float) { R(-1f), L(1f) }

/** Finger curls run from 0 (straight) to 1 (fully bent); `thumbAcross` moves the thumb from beside the hand over the palm. */
data class HandShape(
    val thumb: Float, val index: Float, val middle: Float, val ring: Float, val pinky: Float,
    val thumbAcross: Float = 0f, val spread: Float = 0f,
) {
    val curls get() = floatArrayOf(thumb, index, middle, ring, pinky)

    companion object {
        val ALL: Map<String, HandShape> = mapOf(
            "RELAX" to HandShape(0.25f, 0.2f, 0.28f, 0.34f, 0.4f, 0.2f),
            "FLAT" to HandShape(0f, 0f, 0f, 0f, 0f, 0.15f),
            "OPEN" to HandShape(0f, 0f, 0f, 0f, 0f, 0f, 1f),
            "FIST" to HandShape(0.55f, 1f, 1f, 1f, 1f, 1f),
            "POINT" to HandShape(0.55f, 0f, 1f, 1f, 1f, 1f),
            "TWO" to HandShape(0.55f, 0f, 0f, 1f, 1f, 1f, 0.6f),
            "THREE" to HandShape(0.55f, 0f, 0f, 0f, 1f, 1f, 0.4f),
            "FOUR" to HandShape(0.9f, 0f, 0f, 0f, 0f, 1f, 0.35f),
            "PINKY" to HandShape(0.55f, 1f, 1f, 1f, 0f, 1f),
            "THUMB" to HandShape(0f, 1f, 1f, 1f, 1f, 0f),
            "Y" to HandShape(0f, 1f, 1f, 1f, 0f, 0f, 0.4f),
            "BUNCH" to HandShape(0.45f, 0.5f, 0.5f, 0.5f, 0.52f, 0.7f, -1f),
            "CUP" to HandShape(0.3f, 0.35f, 0.35f, 0.38f, 0.4f, 0.35f, -0.3f),
            "PINCH" to HandShape(0.45f, 0.55f, 1f, 1f, 1f, 0.6f),
            "CLAW" to HandShape(0.35f, 0.6f, 0.6f, 0.62f, 0.65f, 0.3f, 0.3f),
            "SALUTE" to HandShape(0.1f, 0f, 0f, 0f, 0f, 0.6f, -0.2f),
            "WALK_A" to HandShape(0.55f, 0f, 0.45f, 1f, 1f, 1f, 0.4f),
            "WALK_B" to HandShape(0.55f, 0.45f, 0f, 1f, 1f, 1f, 0.4f),
            "RUB" to HandShape(0.7f, 0.55f, 0.55f, 0.6f, 0.65f, 0.9f, -0.6f),
            "HOOK" to HandShape(0.55f, 0.5f, 1f, 1f, 1f, 1f),
        )
    }
}

/** Facial controls, each roughly 0 to 1; presets in [Face.PRESETS]. */
data class Face(
    val browRaise: Float = 0f,
    val browWorry: Float = 0f,
    val browFrown: Float = 0f,
    val eyeOpen: Float = 1f,
    val eyeSmile: Float = 0f,
    val smile: Float = 0.35f,
    val mouthOpen: Float = 0f,
    val mouthRound: Float = 0f,
) {
    companion object {
        val PRESETS = mapOf(
            "neutral" to Face(),
            "smile" to Face(smile = 0.8f, eyeSmile = 0.25f),
            "happy" to Face(smile = 1f, eyeSmile = 0.6f, mouthOpen = 0.35f, browRaise = 0.2f),
            "ask" to Face(browRaise = 0.85f, smile = 0.2f, mouthOpen = 0.15f, eyeOpen = 1.1f),
            "gentle" to Face(browWorry = 0.45f, smile = 0.45f),
            "concern" to Face(browWorry = 0.8f, smile = -0.25f),
            "wince" to Face(browFrown = 0.8f, eyeOpen = 0.45f, smile = -0.5f, mouthOpen = 0.1f),
            "sleepy" to Face(eyeOpen = 0.1f, smile = 0.3f, browRaise = -0.2f),
            "round" to Face(mouthRound = 0.8f, mouthOpen = 0.4f, browRaise = 0.3f),
            "cold" to Face(browWorry = 0.6f, eyeOpen = 0.6f, smile = -0.3f, mouthOpen = 0.12f),
            "hot" to Face(browWorry = 0.3f, eyeOpen = 0.7f, mouthOpen = 0.3f, smile = 0f),
            // Courtesy and care: the faces that carry a greeting, thanks and an apology as much as the hands do.
            "grateful" to Face(smile = 0.75f, eyeSmile = 0.3f, browWorry = 0.15f),
            "reassure" to Face(smile = 0.5f, eyeSmile = 0.15f, browWorry = 0.25f, eyeOpen = 1.05f),
            "sorry" to Face(browWorry = 0.7f, smile = -0.2f, eyeOpen = 0.9f),
            "sad" to Face(browWorry = 0.85f, smile = -0.45f, eyeOpen = 0.85f),
            "confused" to Face(browRaise = 0.5f, browFrown = 0.35f, smile = 0f, mouthRound = 0.2f),
        )
    }

    fun lerp(o: Face, f: Float) = Face(
        lerp(browRaise, o.browRaise, f), lerp(browWorry, o.browWorry, f), lerp(browFrown, o.browFrown, f),
        lerp(eyeOpen, o.eyeOpen, f), lerp(eyeSmile, o.eyeSmile, f), lerp(smile, o.smile, f),
        lerp(mouthOpen, o.mouthOpen, f), lerp(mouthRound, o.mouthRound, f),
    )
}

/** A resolved hand: wrist position in character space and everything needed to draw it. */
data class HandPose(
    val x: Float, val y: Float,
    val shape: HandShape,
    val roll: Float,
    /** Screen angle in degrees, NaN to follow the forearm. */
    val angle: Float,
    val elbowOut: Float,
    val prop: String? = null,
    /** Recorded elbow minus shoulder on screen, NaN to solve it from [elbowOut]. */
    val elbowX: Float = Float.NaN,
    val elbowY: Float = Float.NaN,
)

/** The elbow for the (possibly shifted) shoulder [sx], [sy]: recorded if the keys carry it, else the two-bone
 * solve. */
fun HandPose.elbowAt(
    sx: Float, sy: Float, build: Build, side: Side, prefer: Pair<Float, Float>? = null,
): Pair<Float, Float> = if (elbowX.isNaN()) {
    solveElbow(sx, sy, x, y, build.upperArm, build.foreArm, side, elbowOut, prefer)
} else {
    (sx + elbowX) to (sy + elbowY)
}

/** A point on this hand in character space: "wrist", "palm", "thumb" or a fingertip ("index" to "pinky"). */
fun HandPose.point(part: String, side: Side, handScale: Float): Pair<Float, Float> {
    val pw = 13f
    val thumbSide = if (roll > 0f) -side.sign else side.sign
    val curls = shape.curls
    val (hx, hy) = when (part) {
        "wrist" -> 0f to 0f
        "palm" -> 0f to -6f
        "thumb" -> {
            val a = Math.toRadians((thumbSide * (52f - shape.thumbAcross * 112f)).toDouble())
            val len = 8.8f * (1f - 0.4f * curls[0])
            (thumbSide * (pw / 2f - 0.5f) + sin(a).toFloat() * len) to (-3f - cos(a).toFloat() * len)
        }
        else -> {
            val k = listOf("index", "middle", "ring", "pinky").indexOf(part).coerceAtLeast(0)
            val len = floatArrayOf(9.2f, 10.2f, 9.6f, 7.7f)[k] * (1f - 0.8f * curls[k + 1])
            // Seen from the side the fingers stack on the hand's centre line; the extra 1.5 is the rounded tip.
            val across = if (abs(roll) < 0.35f) 0f else thumbSide * (pw / 2f - 1.8f - k * (pw - 3.6f) / 3f)
            across to (-12f - len)
        }
    }
    val a = Math.toRadians((if (angle.isNaN()) 0f else angle).toDouble())
    val rx = (hx * cos(a) - hy * sin(a)).toFloat() * handScale
    val ry = (hx * sin(a) + hy * cos(a)).toFloat() * handScale
    return (x + rx) to (y + ry)
}

data class Pose(
    val r: HandPose, val l: HandPose,
    val face: Face,
    val tilt: Float, val nod: Float, val turn: Float,
    val lean: Float, val shrug: Float, val hunch: Float, val shiver: Float,
    /** 0 looks ahead, 1 looks down at the answers below the character. */
    val gaze: Float = 0f,
    /** The right hand is placed on the left one, so it draws in front. */
    val rFront: Boolean = false,
    /** One hand is placed on the other, so the front one casts a contact shadow. */
    val handsTouch: Boolean = false,
)

/** Proportions and anchor points of one character. */
class Build(
    val headX: Float, val headY: Float, val headRx: Float, val headRy: Float,
    val neckTop: Float, val neckWidth: Float,
    val shoulderY: Float, val shoulderHalf: Float,
    val upperArm: Float, val foreArm: Float, val armWidth: Float,
    val handScale: Float,
    private val anchors: Map<String, Pair<Float, Float>>,
) {
    /** Sided anchors ("cheek", "ear", "shoulder") are listed for the right side and mirrored for the left. */
    fun anchor(name: String, side: Side): Pair<Float, Float>? {
        val opposite = name.endsWith("_opp")
        val base = name.removeSuffix("_opp")
        val (x, y) = anchors[base] ?: return null
        val s = if (opposite) (if (side == Side.R) Side.L else Side.R) else side
        return if (base in SIDED) (CENTER + (x - CENTER) * (s.sign / Side.R.sign)) to y else x to y
    }

    fun shoulder(side: Side) = (CENTER + side.sign * shoulderHalf) to shoulderY
    fun rest(side: Side) = (CENTER + side.sign * (shoulderHalf - 6f)) to 206f

    companion object {
        const val CENTER = 100f
        val SIDED = setOf("cheek", "ear", "shoulder", "temple", "side", "eye", "hip", "arm", "nose_side", "hair_side")
    }
}

val ChildBuild = Build(
    headX = 100f, headY = 76f, headRx = 40f, headRy = 42f,
    neckTop = 110f, neckWidth = 17f,
    shoulderY = 140f, shoulderHalf = 39f,
    upperArm = 38f, foreArm = 35f, armWidth = 12f,
    handScale = 1.85f,
    anchors = mapOf(
        "hairline" to (100f to 40f), "forehead" to (100f to 56f), "eye" to (84f to 74f), "nose" to (100f to 85f),
        "nose_side" to (95f to 86f), "mouth" to (100f to 99f), "chin" to (100f to 114f), "cheek" to (78f to 92f),
        "ear" to (58f to 80f), "temple" to (66f to 62f), "hair_side" to (64f to 48f), "head_top" to (100f to 30f),
        "chest" to (100f to 156f), "belly" to (100f to 188f), "shoulder" to (61f to 140f), "side" to (56f to 176f),
        "front" to (100f to 168f), "arm" to (58f to 166f), "hip" to (70f to 196f), "low" to (100f to 196f),
    ),
)

val AdultBuild = Build(
    headX = 100f, headY = 70f, headRx = 31f, headRy = 37f,
    neckTop = 104f, neckWidth = 17f,
    shoulderY = 136f, shoulderHalf = 44f,
    upperArm = 41f, foreArm = 38f, armWidth = 13f,
    handScale = 2.0f,
    anchors = mapOf(
        "hairline" to (100f to 38f), "forehead" to (100f to 52f), "eye" to (88f to 67f), "nose" to (100f to 78f),
        "nose_side" to (96f to 80f), "mouth" to (100f to 91f), "chin" to (100f to 104f), "cheek" to (84f to 84f),
        "ear" to (68f to 72f), "temple" to (73f to 58f), "hair_side" to (72f to 46f), "head_top" to (100f to 33f),
        "chest" to (100f to 154f), "belly" to (100f to 188f), "shoulder" to (56f to 136f), "side" to (52f to 178f),
        "front" to (100f to 166f), "arm" to (53f to 168f), "hip" to (66f to 196f), "low" to (100f to 196f),
    ),
)

