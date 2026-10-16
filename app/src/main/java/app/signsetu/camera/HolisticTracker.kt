package app.signsetu.camera

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.geometry.Offset
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.holisticlandmarker.HolisticLandmarker
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.security.MessageDigest

private const val MODEL = "models/holistic_landmarker.task"

/** The bundled holistic model on 64-bit phones; 32-bit phones ship without its native library and keep a plain mirror. */
fun holisticTracker(context: Context): LandmarkTracker? =
    if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty() && context.assets.list("content/models")?.contains("holistic_landmarker.task") == true) {
        HolisticTracker(context.applicationContext)
    } else null

/**
 * MediaPipe Holistic on the CPU in video mode (D-25). The model is checked against the content manifest and loaded on
 * the first frame, on the analysis thread; if the check fails the tracker reports nothing (SEC-54).
 */
class HolisticTracker(private val context: Context) : LandmarkTracker {
    private var lastTimestampMs = Long.MIN_VALUE
    private val landmarker = lazy { verifiedModel()?.let { runCatching { open(it) }.getOrNull() } }

    override fun track(frame: Bitmap, timestampMs: Long): LandmarkFrame? {
        // Video mode rejects a timestamp that does not move forward.
        if (timestampMs <= lastTimestampMs) return null
        val model = landmarker.value ?: return null
        lastTimestampMs = timestampMs
        val r = model.detectForVideo(BitmapImageBuilder(frame).build(), timestampMs)
        return landmarkFrame(timestampMs, r.leftHandLandmarks(), r.rightHandLandmarks(), r.faceLandmarks(), r.poseLandmarks())
    }

    override fun close() {
        if (landmarker.isInitialized()) landmarker.value?.close()
    }

    private fun open(model: ByteBuffer): HolisticLandmarker = HolisticLandmarker.createFromOptions(
        context,
        HolisticLandmarker.HolisticLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetBuffer(model).setDelegate(Delegate.CPU).build())
            .setRunningMode(RunningMode.VIDEO)
            .build(),
    )

    private fun verifiedModel(): ByteBuffer? = runCatching {
        val manifest = context.assets.open("content/manifest.json").bufferedReader().use { it.readText() }
        val expected = Json.parseToJsonElement(manifest).jsonObject["files"]?.jsonObject?.get(MODEL)?.jsonPrimitive?.content
        val buffer = context.assets.openFd("content/$MODEL").use { ByteBuffer.allocateDirect(it.length.toInt()) }
        Channels.newChannel(context.assets.open("content/$MODEL")).use { while (buffer.hasRemaining() && it.read(buffer) >= 0) Unit }
        buffer.flip()
        val actual = MessageDigest.getInstance("SHA-256").apply { update(buffer.duplicate()) }.digest()
            .joinToString("") { "%02x".format(it) }
        buffer.takeIf { expected != null && actual == expected }
    }.getOrNull()
}

/**
 * MediaPipe Holistic assigns hands from the pose, which names the person's own sides, so on an upright unmirrored
 * frame its left hand is the subject's own left hand (seen on the right of the picture). Empty lists mean not seen.
 */
internal fun landmarkFrame(
    timestampMs: Long,
    leftHand: List<NormalizedLandmark>,
    rightHand: List<NormalizedLandmark>,
    face: List<NormalizedLandmark>,
    pose: List<NormalizedLandmark>,
) = LandmarkFrame(timestampMs, leftHand.points(), rightHand.points(), face.points(), pose.points())

private fun List<NormalizedLandmark>.points(): List<Offset>? = takeIf { it.isNotEmpty() }?.map { Offset(it.x(), it.y()) }
