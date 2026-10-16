package app.signsetu.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCase
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
 * nothing is captured, recorded or saved (D-20). The camera stops when this leaves the screen.
 * [placeholder] shows instead when there is no permission or no camera.
 */
@Composable
fun CameraMirror(
    tracker: HandTracker?,
    modifier: Modifier = Modifier,
    handColor: Color = Color(0xFFFFC56B),
    placeholder: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var request by remember { mutableStateOf<SurfaceRequest?>(null) }
    var hands by remember { mutableStateOf<List<TrackedHand>>(emptyList()) }
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
                    .build()
                    .apply {
                        setAnalyzer(executor) { proxy ->
                            proxy.use {
                                val upright = upright(it.toBitmap(), it.imageInfo.rotationDegrees)
                                frameAspect = upright.width.toFloat() / upright.height
                                hands = runCatching { t.detect(upright, it.imageInfo.timestamp / 1_000_000) }.getOrDefault(emptyList())
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
        if (hands.isNotEmpty()) {
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
                for (hand in hands) {
                    if (hand.points.size < 21) continue
                    for ((a, b) in HandBones) drawLine(handColor, map(hand.points[a]), map(hand.points[b]), stroke, StrokeCap.Round)
                    for (p in hand.points) drawCircle(Color.White, stroke * 0.9f, map(p))
                }
            }
        }
    }
}

private fun upright(bitmap: Bitmap, degrees: Int): Bitmap {
    if (degrees == 0) return bitmap
    val m = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true).also { if (it !== bitmap) bitmap.recycle() }
}
