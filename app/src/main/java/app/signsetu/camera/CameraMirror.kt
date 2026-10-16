package app.signsetu.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.awaitCancellation
import java.util.concurrent.Executors
import kotlin.math.max

fun hasCameraPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/**
 * A live mirror of the subject's hands. Only Preview and, when a [tracker] is supplied, ImageAnalysis are bound:
 * no image is captured, recorded or saved (D-20). Each frame's landmarks go to [onFrame] for sign capture (D-25).
 * The camera stops when this leaves the screen. [placeholder] shows instead when there is no permission or no camera.
 */
@Composable
fun CameraMirror(
    tracker: LandmarkTracker?,
    modifier: Modifier = Modifier,
    onFrame: ((LandmarkFrame) -> Unit)? = null,
    handColor: Color = Color(0xFFFFC56B),
    placeholder: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var request by remember { mutableStateOf<SurfaceRequest?>(null) }
    var shown by remember { mutableStateOf<LandmarkFrame?>(null) }
    val frameSink by rememberUpdatedState(onFrame)
    var frameAspect by remember { mutableStateOf(3f / 4f) }
    var front by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    val permitted = remember { hasCameraPermission(context) }

    if (permitted) {
        LaunchedEffect(owner, tracker) {
            // Opening the camera waits for the stage's first frame, so the question appears first (NFR-02).
            withFrameNanos { }
            val provider = ProcessCameraProvider.awaitInstance(context)
            val selector = when {
                provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA.also { front = true }
                provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA.also { front = false }
                else -> { failed = true; return@LaunchedEffect }
            }
            val preview = Preview.Builder().build().apply { setSurfaceProvider { request = it } }
            val executor = Executors.newSingleThreadExecutor()
            val analysis = tracker?.let { t ->
                ImageAnalysis.Builder()
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    // About 640x480 at 15 frames a second is enough for signing and keeps a mid-range phone cool.
                    .setResolutionSelector(ANALYSIS_RESOLUTION)
                    .build()
                    .apply {
                        var lastMs = Long.MIN_VALUE
                        setAnalyzer(executor) { proxy ->
                            proxy.use {
                                val ms = it.imageInfo.timestamp / 1_000_000
                                if (ms - lastMs < ANALYSIS_INTERVAL_MS) return@setAnalyzer
                                lastMs = ms
                                val upright = upright(it.toBitmap(), it.imageInfo.rotationDegrees)
                                val aspect = upright.width.toFloat() / upright.height
                                frameAspect = aspect
                                val frame = runCatching { t.track(upright, ms)?.copy(aspect = aspect) }.getOrNull()
                                shown = frame
                                frame?.let { f -> frameSink?.invoke(f) }
                                upright.recycle()
                            }
                        }
                    }
            }
            val useCases = listOfNotNull<UseCase>(preview, analysis)
            try {
                provider.bindToLifecycle(owner, selector, *useCases.toTypedArray())
                awaitCancellation()
            } catch (e: IllegalArgumentException) {
                failed = true
            } finally {
                provider.unbind(*useCases.toTypedArray())
                executor.shutdown()
            }
        }
    }

    val surface = request
    if (!permitted || failed || surface == null) {
        Box(modifier) { placeholder() }
        return
    }
    Box(modifier) {
        // TextureView-backed, so the rounded window around it clips the picture.
        CameraXViewfinder(surface, Modifier.fillMaxSize(), implementationMode = ImplementationMode.EMBEDDED)
        val seen = shown
        if (seen != null) {
            // The face is not drawn: a calmer picture for a child; its points still reach [onFrame].
            Canvas(Modifier.fillMaxSize()) {
                // Centre-crop the frame into the window, mirrored like the preview for the front camera.
                val viewAspect = size.width / size.height
                val scale = if (frameAspect > viewAspect) size.height else size.width / frameAspect
                val w = scale * frameAspect
                val h = scale
                val ox = (size.width - w) / 2f
                val oy = (size.height - h) / 2f
                fun map(p: Offset) = Offset(ox + (if (front) 1f - p.x else p.x) * w, oy + p.y * h)
                val stroke = max(4f, size.minDimension / 90f)
                seen.pose?.takeIf { it.size >= 33 }?.let { pose ->
                    for ((a, b) in ArmBones) drawLine(handColor.copy(alpha = 0.6f), map(pose[a]), map(pose[b]), stroke * 1.6f, StrokeCap.Round)
                }
                for (hand in seen.hands) {
                    if (hand.size < 21) continue
                    for ((a, b) in HandBones) drawLine(handColor, map(hand[a]), map(hand[b]), stroke, StrokeCap.Round)
                    for (p in hand) drawCircle(Color.White, stroke * 0.9f, map(p))
                }
            }
        }
    }
}

// Just under 1/15 s, so a 30 fps camera's jitter does not halve the rate.
private const val ANALYSIS_INTERVAL_MS = 60L
private val ANALYSIS_RESOLUTION = ResolutionSelector.Builder()
    .setResolutionStrategy(ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
    .build()

private fun upright(bitmap: Bitmap, degrees: Int): Bitmap {
    if (degrees == 0) return bitmap
    val m = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true).also { if (it !== bitmap) bitmap.recycle() }
}
