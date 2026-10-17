package app.signsetu.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Size
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.ui.layout.ContentScale
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.core.graphics.createBitmap
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.awaitCancellation
import java.util.concurrent.Executors
import kotlin.math.max

fun hasCameraPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/**
 * A live mirror of the subject's hands. Only Preview and, when a [tracker] is supplied, ImageAnalysis are bound:
 * no image is captured, recorded or saved (D-20). Frames are analysed only while [onFrame] is set, inside a sign window
 * (D-25); the analysis use case stays bound, so opening a window never restarts the camera. Each frame's landmarks go
 * to [onFrame] for sign capture. The camera stops when this leaves the screen. [placeholder] shows instead when there
 * is no permission or no camera.
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
    var analysis by remember { mutableStateOf<ImageAnalysis?>(null) }
    var shown by remember { mutableStateOf<LandmarkFrame?>(null) }
    val frameSink by rememberUpdatedState(onFrame)
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
            val preview = Preview.Builder().setResolutionSelector(PREVIEW_RESOLUTION).build().apply { setSurfaceProvider { request = it } }
            val frames = tracker?.let {
                ImageAnalysis.Builder()
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    // CameraX turns the frame upright into its own reused buffer, cheaper than a bitmap rotate per frame.
                    .setOutputImageRotationEnabled(true)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setResolutionSelector(ANALYSIS_RESOLUTION)
                    .build()
            }
            val useCases = listOfNotNull<UseCase>(preview, frames)
            try {
                provider.bindToLifecycle(owner, selector, *useCases.toTypedArray())
                analysis = frames
                awaitCancellation()
            } catch (e: IllegalArgumentException) {
                failed = true
            } finally {
                analysis = null
                provider.unbind(*useCases.toTypedArray())
            }
        }
    }

    val analysing = onFrame != null
    DisposableEffect(analysis, tracker, analysing) {
        val a = analysis
        if (a == null || tracker == null || !analysing) return@DisposableEffect onDispose { shown = null }
        val executor = Executors.newSingleThreadExecutor()
        var lastMs: Long? = null
        var reused: Bitmap? = null
        a.setAnalyzer(executor) { proxy ->
            proxy.use {
                val ms = it.imageInfo.timestamp / 1_000_000
                if (!analysisDue(lastMs, ms)) return@setAnalyzer
                lastMs = ms
                val plane = it.planes[0]
                val packed = plane.pixelStride == 4 && plane.rowStride == it.width * 4
                val bitmap = if (packed) {
                    (reused?.takeIf { b -> b.width == it.width && b.height == it.height }
                        ?: createBitmap(it.width, it.height).also { b -> reused?.recycle(); reused = b })
                        .apply { plane.buffer.rewind(); copyPixelsFromBuffer(plane.buffer) }
                } else {
                    it.toBitmap()
                }
                val aspect = bitmap.width.toFloat() / bitmap.height
                val frame = runCatching { tracker.track(bitmap, ms)?.copy(aspect = aspect) }.getOrNull()
                if (!packed) bitmap.recycle()
                shown = frame
                frame?.let { f -> frameSink?.invoke(f) }
            }
        }
        onDispose {
            a.clearAnalyzer()
            executor.execute { reused?.recycle() }
            executor.shutdown()
            shown = null
        }
    }

    val surface = request
    if (!permitted || failed || surface == null) {
        Box(modifier) { placeholder() }
        return
    }
    Box(modifier) {
        // TextureView-backed, so the rounded window around it clips the picture. Fit: the subject sees all the camera sees.
        CameraXViewfinder(surface, Modifier.fillMaxSize(), implementationMode = ImplementationMode.EMBEDDED, contentScale = ContentScale.Fit)
        // Landmarks are read in the draw phase only, so each analysed frame redraws this canvas without recomposing.
        Canvas(Modifier.fillMaxSize()) {
            val seen = shown ?: return@Canvas
            val frameAspect = seen.aspect
            // The face is not drawn: a calmer picture for a child; its points still reach [onFrame].
            // Fit the frame into the window like the preview, mirrored for the front camera.
            val viewAspect = size.width / size.height
            val scale = if (frameAspect > viewAspect) size.width / frameAspect else size.height
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

// Up to 30 fps: fast signing blurs between frames at 15. Analysis runs only inside a sign window, so heat is bounded.
private const val ANALYSIS_INTERVAL_MS = 30L

/** A sentinel such as Long.MIN_VALUE overflows the subtraction and would skip every frame, so the first one is null. */
internal fun analysisDue(lastMs: Long?, ms: Long) = lastMs == null || ms - lastMs >= ANALYSIS_INTERVAL_MS
// Preview and analysis share one tall 16:9 shape, so the skeleton lines up and a portrait window shows the whole frame.
private val PREVIEW_RESOLUTION = ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY).build()

private val ANALYSIS_RESOLUTION = ResolutionSelector.Builder()
    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
    .setResolutionStrategy(ResolutionStrategy(Size(640, 360), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
    .build()

/** The shape of the upright camera frame; windows that show the camera use it so nothing is cropped or letterboxed. */
const val CAMERA_ASPECT = 9f / 16f
