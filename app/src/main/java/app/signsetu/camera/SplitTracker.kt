package app.signsetu.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.geometry.Offset
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import java.nio.ByteBuffer
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.hypot

private const val HANDS = "models/hand_landmarker.task"
private const val POSE = "models/pose_landmarker_lite.task"
private const val FACE = "models/face_landmarker.task"
private const val PROBE = "models/holistic_probe.jpg"

/** Mouth shapes kept from the face model's 52 blendshapes, in this order in [LandmarkFrame.mouth]: the evidence for
 * mouthing and mouth gestures (docs/10). */
val MOUTH_SHAPES = listOf(
    "jawOpen", "mouthClose", "mouthFunnel", "mouthPucker", "mouthSmileLeft", "mouthSmileRight", "mouthStretchLeft",
    "mouthStretchRight", "mouthRollLower", "mouthRollUpper", "mouthShrugLower", "mouthShrugUpper", "mouthPressLeft",
    "mouthPressRight", "mouthLowerDownLeft", "mouthLowerDownRight", "mouthUpperUpLeft", "mouthUpperUpRight", "cheekPuff",
)

/** The split tracker on 64-bit phones that carry its three models, Holistic otherwise (and as its fallback). */
fun landmarkTracker(context: Context): LandmarkTracker? {
    val app = context.applicationContext
    val models = app.assets.list("content/models").orEmpty().toSet()
    val split = android.os.Build.SUPPORTED_64_BIT_ABIS.isNotEmpty() && listOf(HANDS, POSE, FACE).all { it.removePrefix("models/") in models }
    return if (split) SplitTracker(app) { holisticTracker(app) } else holisticTracker(app)
}

/** Hands, body and face as three MediaPipe tasks instead of one Holistic graph: hands on every frame, body and face on
 * alternate frames (each reused for the frame between), each on the GPU with its own CPU fallback. The face task adds
 * head angles, so nods and shakes are read from rotation, and mouth blendshapes. Same topology as Holistic, so the
 * readers see the same features. */
class SplitTracker(private val context: Context, private val fallback: () -> LandmarkTracker?) : LandmarkTracker {
    /** Holistic, used for every frame if the hand or body task cannot open on this phone at all: the readers need
     * both (the body sets each sign's centre and scale). */
    private val instead: LandmarkTracker? by lazy { fallback() }
    private val lock = Any()
    private var hands: HandLandmarker? = null
    private var pose: PoseLandmarker? = null
    private var face: FaceLandmarker? = null
    private var opened = false
    private var last = Long.MIN_VALUE
    private var frameNo = 0L
    private var lastPose: List<Offset>? = null
    private var lastFace: FaceRead? = null
    internal val delegates = HashMap<String, Delegate>()

    private class FaceRead(val points: List<Offset>, val yaw: Float?, val pitch: Float?, val mouth: List<Float>?)

    override fun warm() {
        open()
        if (hands == null || pose == null) instead?.warm()
    }

    private fun open() = synchronized(lock) {
        if (opened) return
        opened = true
        // The photo need not show hands: the hand task only has to run on it.
        hands = hands ?: task(HANDS, { it.detectForVideo(probe(), 0); true }) { m, d ->
            HandLandmarker.createFromOptions(context, HandLandmarker.HandLandmarkerOptions.builder().setBaseOptions(base(m, d))
                .setRunningMode(RunningMode.VIDEO).setNumHands(2)
                // As for Holistic: 0.5 drops small or fast hands; the readers' own scores filter the weak ones.
                .setMinHandDetectionConfidence(0.3f).setMinHandPresenceConfidence(0.3f).setMinTrackingConfidence(0.3f).build())
        }
        pose = pose ?: task(POSE, { it.detectForVideo(probe(), 0).landmarks().isNotEmpty() }) { m, d ->
            PoseLandmarker.createFromOptions(context, PoseLandmarker.PoseLandmarkerOptions.builder().setBaseOptions(base(m, d))
                .setRunningMode(RunningMode.VIDEO).setNumPoses(1)
                .setMinPoseDetectionConfidence(0.3f).setMinPosePresenceConfidence(0.3f).setMinTrackingConfidence(0.3f).build())
        }
        face = face ?: task(FACE, { it.detectForVideo(probe(), 0).faceLandmarks().isNotEmpty() }) { m, d ->
            FaceLandmarker.createFromOptions(context, FaceLandmarker.FaceLandmarkerOptions.builder().setBaseOptions(base(m, d))
                .setRunningMode(RunningMode.VIDEO).setNumFaces(1)
                .setMinFaceDetectionConfidence(0.3f).setMinFacePresenceConfidence(0.3f).setMinTrackingConfidence(0.3f)
                .setOutputFaceBlendshapes(true).setOutputFacialTransformationMatrixes(true).build())
        }
    }

    private fun base(model: ByteBuffer, d: Delegate) = BaseOptions.builder().setModelAssetBuffer(model).setDelegate(d).build()

    private fun probe(): MPImage = BitmapImageBuilder(context.assets.open("content/$PROBE").use(BitmapFactory::decodeStream)).build()

    /** The task on the GPU, kept only if it finds the person in the bundled photo (some GPUs run the graph yet find
     * nothing), else the CPU; once a task has failed on the GPU it stays on the CPU. Null when its file is missing or
     * fails the manifest check. */
    private fun <T : AutoCloseable> task(path: String, finds: (T) -> Boolean, make: (ByteBuffer, Delegate) -> T): T? {
        val model = verifiedAsset(context, path) ?: return null
        val order = if (delegates[path] == Delegate.CPU) listOf(Delegate.CPU) else listOf(Delegate.GPU, Delegate.CPU)
        for (d in order) {
            val t = runCatching { make(model, d) }.onFailure { Log.w("SplitTracker", "$path on $d", it) }.getOrNull() ?: continue
            if (d == Delegate.GPU && !runCatching { finds(t) }.getOrDefault(false)) { runCatching { t.close() }; continue }
            delegates[path] = d
            // The probe used timestamp 0 on this graph; video mode only needs later frames to move forward.
            return t
        }
        return null
    }

    override fun track(frame: Bitmap, timestampMs: Long): LandmarkFrame? = synchronized(lock) {
        // Video mode rejects a timestamp that does not move forward.
        if (timestampMs <= last) return null
        open()
        val h = hands?.takeIf { pose != null } ?: return instead?.track(frame, timestampMs)
        last = timestampMs
        val image = BitmapImageBuilder(frame).build()
        frameNo++
        if (frameNo % 2 == 0L || lastPose == null) lastPose = runOr(POSE) { pose?.detectForVideo(image, timestampMs)?.landmarks()?.firstOrNull()?.points() }
        if (frameNo % 2 == 1L || lastFace == null) lastFace = runOr(FACE) { face?.let { readFace(it, image, timestampMs) } }
        val r = runOr(HANDS) { h.detectForVideo(image, timestampMs) } ?: return null
        val (left, right) = assignHands(r.landmarks().map { it.points() }, r.handedness().map { it.firstOrNull()?.categoryName() }, lastPose)
        val f = lastFace
        LandmarkFrame(timestampMs, left, right, f?.points, lastPose, yaw = f?.yaw, pitch = f?.pitch, mouth = f?.mouth)
    }

    private fun readFace(model: FaceLandmarker, image: MPImage, ms: Long): FaceRead? {
        val r = model.detectForVideo(image, ms)
        val points = r.faceLandmarks().firstOrNull()?.points() ?: return null
        val m = r.facialTransformationMatrixes().orElse(null)?.firstOrNull()
        val shapes = r.faceBlendshapes().orElse(null)?.firstOrNull()?.associate { it.categoryName() to it.score() }
        val angles = m?.let(::headAngles)
        return FaceRead(points, angles?.first, angles?.second, shapes?.let { s -> MOUTH_SHAPES.map { s[it] ?: 0f } })
    }

    /** A task that throws on the GPU is reopened on the CPU for the next frame; this frame goes without it. */
    private fun <T> runOr(path: String, run: () -> T?): T? = try {
        run()
    } catch (e: RuntimeException) {
        if (delegates[path] == Delegate.GPU) {
            Log.w("SplitTracker", "$path failed on the GPU, reopening on the CPU", e)
            reopenOnCpu(path)
        }
        null
    }

    private fun reopenOnCpu(path: String) {
        delegates[path] = Delegate.CPU
        when (path) {
            HANDS -> { hands?.close(); hands = null }
            POSE -> { pose?.close(); pose = null }
            FACE -> { face?.close(); face = null }
        }
        opened = false
        open()
    }

    override fun close() = synchronized(lock) {
        hands?.close(); pose?.close(); face?.close()
        if (opened && (hands == null || pose == null)) instead?.close()
        hands = null; pose = null; face = null
        opened = false
        last = Long.MIN_VALUE
    }
}

private fun List<NormalizedLandmark>.points(): List<Offset> = map { Offset(it.x(), it.y()) }

/** The subject's own left and right hands. With a body in view each hand goes to the nearer wrist (pose 15 is the
 * subject's left, 16 the right), as Holistic assigns them; without one, the hand model's own label is used. Its labels
 * assume a mirrored selfie, and these frames are not mirrored, so "Left" is the subject's right. */
internal fun assignHands(found: List<List<Offset>>, labels: List<String?>, pose: List<Offset>?): Pair<List<Offset>?, List<Offset>?> {
    val hands = found.filter { it.size == 21 }
    if (hands.isEmpty()) return null to null
    val lw = pose?.getOrNull(15)
    val rw = pose?.getOrNull(16)
    if (lw != null && rw != null) {
        fun d(h: List<Offset>, w: Offset) = hypot(h[0].x - w.x, h[0].y - w.y)
        if (hands.size == 1) return hands[0].let { h -> if (d(h, lw) <= d(h, rw)) h to null else null to h }
        val (a, b) = hands[0] to hands[1]
        return if (d(a, lw) + d(b, rw) <= d(a, rw) + d(b, lw)) a to b else b to a
    }
    var left: List<Offset>? = null
    var right: List<Offset>? = null
    hands.forEachIndexed { i, h -> if (labels.getOrNull(i) == "Left") right = right ?: h else left = left ?: h }
    return left to right
}

/** Yaw (turn about the vertical axis) and pitch (about the ear-to-ear axis), in degrees, from the face model's 4x4
 * transform in column-major order. For the small angles of a nod or shake the result is the same if the matrix comes
 * row-major, so a layout mix-up cannot swap nodding and shaking. */
internal fun headAngles(m: FloatArray): Pair<Float, Float> =
    Math.toDegrees(asin((-m[2]).coerceIn(-1f, 1f).toDouble())).toFloat() to Math.toDegrees(atan2(m[6].toDouble(), m[10].toDouble())).toFloat()
