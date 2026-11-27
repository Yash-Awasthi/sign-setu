package app.signsetu.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.os.Process
import android.util.Log
import android.util.Size
import app.signsetu.BuildConfig
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
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import app.signsetu.ui.components.PermissionButton
import app.signsetu.ui.components.rememberPermission
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as Extent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.math.max

/** Whether the traced skeleton is drawn over the camera picture; the picture itself always shows. */
object SkeletonOverlay {
    val on = mutableStateOf(true)
}

fun hasCameraPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** A live mirror of the subject's hands. Only Preview and, when a [tracker] is supplied, ImageAnalysis are
 * bound: no image is captured, recorded or saved. */
@Composable
fun CameraMirror(
    tracker: LandmarkTracker?,
    modifier: Modifier = Modifier,
    onFrame: ((LandmarkFrame) -> Unit)? = null,
    handColor: Color = Color(0xFFFFC56B),
    showFace: Boolean = false,
    showSkeleton: Boolean = true,
    /** Crop to fill the view instead of letterboxing, for the full-screen self view. */
    fill: Boolean = false,
    /** A nod or shake, on the analysis thread; the stage treats it as a proposal, never an answer. */
    onHead: ((Head) -> Unit)? = null,
    /** A body part pointed at, as a card id; also only a proposal. */
    onPoint: ((String) -> Unit)? = null,
    onStopPalm: (() -> Unit)? = null,
    onShrug: (() -> Unit)? = null,
    /** A courtesy held on the analysis thread (namaste, a wave, thanks, a bow); the stage treats it as a proposal. */
    onEveryday: ((Everyday) -> Unit)? = null,
    /** Ask for the camera as soon as this shows, when it is not yet allowed; a button asks again either way. */
    askPermission: Boolean = true,
    placeholder: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var request by remember { mutableStateOf<SurfaceRequest?>(null) }
    var analysis by remember { mutableStateOf<ImageAnalysis?>(null) }
    var shown by remember { mutableStateOf<LandmarkFrame?>(null) }
    // The picture the tracker read, drawn under its own skeleton so the two always match.
    var picture by remember { mutableStateOf<ImageBitmap?>(null) }
    val frameSink by rememberUpdatedState(onFrame)
    val headSink by rememberUpdatedState(onHead)
    val pointSink by rememberUpdatedState(onPoint)
    val stopSink by rememberUpdatedState(onStopPalm)
    val shrugSink by rememberUpdatedState(onShrug)
    val everydaySink by rememberUpdatedState(onEveryday)
    var front by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    val camera = rememberPermission(Manifest.permission.CAMERA, askNow = askPermission)
    val permitted = camera.granted
    if (permitted) {
        // CameraX binds and sets surfaces on the main thread only; an effect resumes wherever its dispatcher says.
        LaunchedEffect(owner, tracker) { withContext(Dispatchers.Main.immediate) {
            // Opening the camera waits for the stage's first frame, so the question appears first.
            withFrameNanos { }
            // A new tracker or lifecycle retries a camera that failed before.
            failed = false
            // A camera service that fails to start, or a camera another app holds, shows the placeholder instead of crashing.
            val (provider, selector) = try {
                val provider = ProcessCameraProvider.awaitInstance(context)
                provider to when {
                    provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA.also { front = true }
                    provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA.also { front = false }
                    else -> { failed = true; return@withContext }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
                return@withContext
            }
            val preview = Preview.Builder().setResolutionSelector(PREVIEW_RESOLUTION).build().apply { setSurfaceProvider { request = it } }
            val frames = tracker?.let {
                ImageAnalysis.Builder()
                    // YUV straight from the camera: luma alone gates and judges a frame, and only the focus window is
                    // turned upright and converted to colour, in one native pass.
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setResolutionSelector(ANALYSIS_RESOLUTION)
                    .build()
            }
            val useCases = listOfNotNull<UseCase>(preview, frames)
            try {
                provider.bindToLifecycle(owner, selector, *useCases.toTypedArray())
                analysis = frames
                awaitCancellation()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
            } finally {
                analysis = null
                request = null
                provider.unbind(*useCases.toTypedArray())
            }
        } }
    }

    val analysing = onFrame != null
    // One ring per screen, not per effect restart: a rebind or a tracker change would otherwise allocate three new
    // bitmaps (about 5 MB) that only the collector frees.
    val converter = remember { FrameConverter(ring = 3) }
    DisposableEffect(converter) { onDispose { converter.close() } }
    DisposableEffect(analysis, tracker, analysing) {
        val a = analysis
        if (a == null || tracker == null) return@DisposableEffect onDispose { shown = null; picture = null }
        var lastPerson: Long? = null
        val pipeline = FramePipeline()
        val blur = BlurMeter()
        val gate = LumaGate()
        // Hands are read on this thread, so it runs ahead of ordinary work.
        val executor = Executors.newSingleThreadExecutor { r -> Thread { Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY); r.run() } }
        var lastMs: Long? = null
        // Heard on the analysis thread, so the analyzer reads it without a lock. Severe heat slows the idle mirror first.
        var hot = false
        val power = context.getSystemService(PowerManager::class.java)
        val thermal = PowerManager.OnThermalStatusChangedListener { hot = it >= PowerManager.THERMAL_STATUS_SEVERE }
        power?.addThermalStatusListener(executor, thermal)
        a.setAnalyzer(executor) { proxy ->
            proxy.use {
                val ms = it.imageInfo.timestamp / 1_000_000
                // An empty room outside a sign window is checked about three times a second, to save battery and heat.
                val rot = it.imageInfo.rotationDegrees
                val luma = gate.read(it, rot)
                val idle = lastPerson.let { seenAt -> seenAt == null || ms - seenAt > IDLE_AFTER_MS }
                // Movement in an empty room wakes the tracker at once instead of on the next slow poll.
                val woken = luma != null && luma.energy > LumaGate.WAKE_ENERGY
                val interval = if (analysing) ANALYSIS_INTERVAL_MS else if ((idle && !woken) || hot) IDLE_INTERVAL_MS else PREVIEW_INTERVAL_MS
                if (!analysisDue(lastMs, ms, interval)) return@setAnalyzer
                lastMs = ms
                // Always the whole frame: a crop that follows the signer made the tracker lose and misplace the body.
                val crop = converter.crop(it, rot, Focus.FULL)
                val bitmap = converter.convert(it, rot, crop)
                val aspect = crop.uprightW.toFloat() / crop.uprightH
                // Picture quality is judged only while a sign is being read; elsewhere it costs nothing.
                val blurry = analysing && (luma?.let { l -> blur.judge(l.sharpness) } ?: blur.blurry(bitmap))
                val frame = runCatching { tracker.track(bitmap, ms)?.fromCrop(crop)?.let { raw -> pipeline.mark(raw, aspect, blurry) } }.getOrNull()
                if (frame?.pose != null) lastPerson = ms
                shown = frame
                // Only at full rate: at the idle rate the picture would stutter.
                picture = if (interval < IDLE_INTERVAL_MS) bitmap.asImageBitmap() else null
                frame?.let { f -> frameSink?.invoke(f) }
                // Inside a sign window open hands and pointing are signs, not answers.
                if (!analysing) {
                    frame?.head?.let { h -> headSink?.invoke(h) }
                    frame?.pointsAt?.let { c -> pointSink?.invoke(c) }
                    if (frame?.stopPalm == true) stopSink?.invoke()
                    if (frame?.shrug == true) shrugSink?.invoke()
                    frame?.everyday?.let { g -> everydaySink?.invoke(g) }
                }
            }
        }
        onDispose {
            power?.removeThermalStatusListener(thermal)
            a.clearAnalyzer()
            executor.shutdown()
            shown = null
            picture = null
        }
    }

    val surface = request
    if (!permitted || failed || surface == null) {
        Box(modifier) {
            placeholder()
            if (!permitted) PermissionButton(camera, Modifier.align(Alignment.BottomCenter).padding(12.dp))
        }
        return
    }
    val labels = rememberTextMeasurer()
    // The skeleton's paths and strokes, held for the life of this mirror: the overlay draws in the draw phase once per
    // analysed frame, so building them there would churn the heap and the native path behind each Path.
    val ink = remember { SkeletonInk() }
    // No new picture for a while: the tracker slowed or stopped, so the live picture shows again.
    // Watched outside composition, so a new picture each frame redraws the canvas only.
    LaunchedEffect(Unit) { snapshotFlow { picture }.collectLatest { if (it != null) { delay(FRESH_NS / 1_000_000); picture = null } } }
    Box(modifier) {
        // TextureView-backed, so the rounded window around it clips the picture. Fit: the subject sees all the camera sees.
        CameraXViewfinder(surface, Modifier.fillMaxSize(), implementationMode = ImplementationMode.EMBEDDED, contentScale = if (fill) ContentScale.Crop else ContentScale.Fit)
        // Landmarks are read in the draw phase only, so each analysed frame redraws this canvas without recomposing.
        Canvas(Modifier.fillMaxSize()) {
            if (!showSkeleton || !SkeletonOverlay.on.value) return@Canvas
            // No body in view while signing is expected: a faint head and shoulders shows where to stand, without words.
            if (analysing && shown?.pose == null) {
                val c = Offset(size.width / 2, size.height * 0.38f)
                val r = size.minDimension * 0.12f
                val faint = Color.White.copy(alpha = 0.35f)
                ink.fit(size.minDimension)
                drawCircle(faint, r, c, style = ink.guide)
                drawPath(ink.guidePath.apply {
                    reset()
                    moveTo(c.x - r * 2.6f, size.height * 0.85f)
                    quadraticTo(c.x - r * 2.4f, c.y + r * 1.6f, c.x, c.y + r * 1.4f)
                    quadraticTo(c.x + r * 2.4f, c.y + r * 1.6f, c.x + r * 2.6f, size.height * 0.85f)
                }, faint, style = ink.guide)
            }
            // The tracked picture covers the live one while it keeps coming, so the skeleton sits on the very hands it
            // was read from; when the tracker slows to save power, the live picture shows again.
            picture?.let { drawTracked(it, front, fill) }
            val seen = shown ?: return@Canvas
            drawLandmarks(seen, front, analysing, showFace, handColor, labels, fill, ink)
        }
    }
}

/** A tracked picture older than this means the tracker has slowed down; the live picture shows instead. */
private const val FRESH_NS = 150_000_000L

/** [image] where the live picture would be: same fit or crop, mirrored like the front camera's preview. */
private fun DrawScope.drawTracked(image: ImageBitmap, front: Boolean, fill: Boolean) {
    val aspect = image.width.toFloat() / image.height
    val w = if (fill) maxOf(size.width, size.height * aspect) else minOf(size.width, size.height * aspect)
    val h = w / aspect
    val left = (size.width - w) / 2f
    val top = (size.height - h) / 2f
    clipRect {
        scale(if (front) -1f else 1f, 1f, pivot = Offset(size.width / 2f, size.height / 2f)) {
            drawImage(image, dstOffset = IntOffset(left.toInt(), top.toInt()), dstSize = IntSize(w.toInt(), h.toInt()), filterQuality = FilterQuality.Low)
        }
    }
}

/** Everything the skeleton overlay reuses from frame to frame. Paths are reset and rebuilt, strokes are rebuilt only
 * when the canvas resizes, and the hand lists and point buffer are refilled, so a drawn frame allocates nothing. */
internal class SkeletonInk {
    /** One path per face outline, in [FaceOutlines.all] order: an outline is drawn again only through its own path. */
    val facePath: List<Path> = List(FaceOutlines.all.size) { Path() }

    /** The faint head and shoulders shown while a body is missing. */
    val guidePath = Path()

    /** Wide enough for the longest face outline, as x,y pairs. */
    val scratch = FloatArray(FaceOutlines.all.maxOf { it.size } * 2)

    /** The hands of a frame with their side tags, refilled each frame instead of rebuilt. */
    val handTags = ArrayList<String>(2)
    val handPoints = ArrayList<List<Offset>>(2)

    var face: Stroke = Stroke(1f); private set
    var thin: Stroke = Stroke(1f); private set
    var thick: Stroke = Stroke(1f); private set
    var guide: Stroke = Stroke(1f); private set
    private var minDimension = Float.NaN

    /** The strokes for a canvas whose shorter side is [minDimension]; cheap after the first call for a given size. */
    fun fit(minDimension: Float) {
        if (minDimension == this.minDimension) return
        this.minDimension = minDimension
        val stroke = max(4f, minDimension / 90f)
        face = Stroke(stroke * 0.5f)
        thin = Stroke(stroke * 0.6f)
        thick = Stroke(stroke * 0.9f)
        guide = Stroke(minDimension / 80f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f)))
    }

    /** [line] through [face] as a path, [crop] and [mirrored] as the overlay draws it: the same instance every call. */
    fun outlinePath(line: List<Int>, face: List<Offset>, frameAspect: Float, viewW: Float, viewH: Float, mirrored: Boolean, crop: Boolean): Path {
        val path = facePath[FaceOutlines.all.indexOf(line)].apply { reset() }
        val n = outlinePoints(line, face, frameAspect, viewW, viewH, mirrored, crop, scratch)
        var i = 0
        while (i < n) {
            if (i == 0) path.moveTo(scratch[0], scratch[1]) else path.lineTo(scratch[i], scratch[i + 1])
            i += 2
        }
        return path
    }
}

/** The mapped points of the face [line] through [face], as x,y pairs in [out]; returns how many were written. Fills a
 * supplied buffer so a frame builds no list per outline, and is pure so a test can check the geometry. */
internal fun outlinePoints(line: List<Int>, face: List<Offset>, frameAspect: Float, viewW: Float, viewH: Float, mirrored: Boolean, crop: Boolean, out: FloatArray): Int {
    var n = 0
    for (i in line.indices) {
        val p = overlayPoint(face[line[i]], frameAspect, viewW, viewH, mirrored, crop = crop)
        out[n++] = p.x
        out[n++] = p.y
    }
    return n
}

/** The traced skeleton, brows and lips of [seen] over a frame drawn Fit and centred; the camera overlay and its device test both draw this. */
internal fun DrawScope.drawLandmarks(seen: LandmarkFrame, front: Boolean, analysing: Boolean, showFace: Boolean, handColor: Color, labels: TextMeasurer, fill: Boolean = false, ink: SkeletonInk = SkeletonInk()) {
    fun map(p: Offset) = overlayPoint(p, seen.aspect, size.width, size.height, front, crop = fill)
    ink.fit(size.minDimension)
    val a = map(Offset.Zero)
    val b = map(Offset(1f, 1f))
    val stroke = max(4f, size.minDimension / 90f)
    // A wrist at or past the frame edge lights that edge, a wordless "bring your hands into view", only while signing is expected.
    if (analysing) seen.pose?.takeIf { it.size >= 33 }?.let { pose ->
        val bar = stroke * 2f
        val left = minOf(a.x, b.x); val right = max(a.x, b.x)
        for (w in listOf(pose[15], pose[16])) {
            val e = outOfView(w) ?: continue
            val (x, y) = when (e) {
                Edge.Top -> Offset(left, a.y) to Extent(right - left, bar)
                Edge.Bottom -> Offset(left, b.y - bar) to Extent(right - left, bar)
                // The front camera is mirrored, so the frame's left edge shows on the right.
                Edge.Left -> (if (front) Offset(right - bar, a.y) else Offset(left, a.y)) to Extent(bar, b.y - a.y)
                Edge.Right -> (if (front) Offset(left, a.y) else Offset(right - bar, a.y)) to Extent(bar, b.y - a.y)
            }
            drawRect(handColor.copy(alpha = 0.8f), x, y)
        }
    }
    // Points beyond the frame edge would otherwise sit in the letterbox band as stray dots.
    clipRect(minOf(a.x, b.x), a.y, max(a.x, b.x), b.y) {
        seen.face?.takeIf { it.size >= 468 }?.let { face ->
            fun outline(line: List<Int>, color: Color, style: Stroke) =
                drawPath(ink.outlinePath(line, face, seen.aspect, size.width, size.height, front, fill), color, style = style)
            if (showFace) for (line in FaceOutlines.all) outline(line, Color.White.copy(alpha = 0.5f), ink.face)
            // Brows and lips carry facial grammar; they light up when raised or open.
            val brow = if (seen.browsUp) handColor else Color.White.copy(alpha = 0.7f)
            outline(FaceOutlines.leftBrow, brow, if (seen.browsUp) ink.thick else ink.thin)
            outline(FaceOutlines.rightBrow, brow, if (seen.browsUp) ink.thick else ink.thin)
            outline(FaceOutlines.lips, if (seen.mouthOpen) handColor else Color.White.copy(alpha = 0.7f), if (seen.mouthOpen) ink.thick else ink.thin)
        }
        seen.pose?.takeIf { it.size >= 33 }?.let { pose ->
            for ((i, j) in ArmBones) drawLine(handColor.copy(alpha = 0.6f), map(pose[i]), map(pose[j]), stroke * 1.6f, StrokeCap.Round)
            for (i in 11..16) drawCircle(handColor, stroke * 1.2f, map(pose[i]))
        }
        labelledHands(seen, ink.handTags, ink.handPoints)
        for (n in ink.handPoints.indices) {
            val label = ink.handTags[n]
            val hand = ink.handPoints[n]
            for ((i, j) in HandBones) drawLine(handColor, map(hand[i]), map(hand[j]), stroke * 1.3f, StrokeCap.Round)
            for (p in hand) { drawCircle(Color.Black.copy(alpha = 0.5f), stroke * 1.4f, map(p)); drawCircle(Color.White, stroke * 1.1f, map(p)) }
            drawText(labels, label, map(hand[0]) + Offset(stroke * 1.5f, stroke), HandLabelStyle)
        }
        // A wordless mark of the gesture just read, for subjects who cannot read the caption.
        val cue = when {
            seen.head == Head.Nod -> Color(0xFF22C55E) to "✓"
            seen.head == Head.Shake -> Color(0xFFEF4444) to "✕"
            seen.stopPalm -> Color(0xFFF59E0B) to "✋"
            else -> null
        }
        cue?.let { (color, glyph) ->
            drawCircle(color, stroke * 3f, Offset(size.width / 2, size.height * 0.12f))
            drawText(labels, glyph, Offset(size.width / 2 - stroke * 1.2f, size.height * 0.12f - stroke * 1.8f), CueStyle)
        }
    }
    // Debug builds show the raw cue values for calibrating on a real phone: adb shell setprop log.tag.SignSetuTuning DEBUG
    if (BuildConfig.DEBUG && Log.isLoggable("SignSetuTuning", Log.DEBUG)) drawText(labels, tuningLine(seen), Offset(8f, 8f), TuningStyle)
}

private val HandLabelStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
private val CueStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
private val TuningStyle = TextStyle(color = Color.Yellow, fontSize = 11.sp, background = Color.Black.copy(alpha = 0.5f))

internal enum class Edge { Top, Bottom, Left, Right }

/** The frame edge a pose point has reached (within 3%) or passed, in unmirrored frame terms. */
internal fun outOfView(p: Offset): Edge? = when {
    p.y > 0.97f -> Edge.Bottom
    p.y < 0.03f -> Edge.Top
    p.x < 0.03f -> Edge.Left
    p.x > 0.97f -> Edge.Right
    else -> null
}

/** The subject's own sides, as Holistic names them: on the mirrored front camera their left hand shows on the left. */
internal fun labelledHands(frame: LandmarkFrame): List<Pair<String, List<Offset>>> {
    val tags = ArrayList<String>(2)
    val hands = ArrayList<List<Offset>>(2)
    labelledHands(frame, tags, hands)
    return List(tags.size) { tags[it] to hands[it] }
}

/** Fills [tags] and [hands] with the frame's hands, so the draw phase refills two lists instead of building them. */
internal fun labelledHands(frame: LandmarkFrame, tags: MutableList<String>, hands: MutableList<List<Offset>>) {
    tags.clear()
    hands.clear()
    frame.leftHand?.takeIf { it.size >= 21 }?.let { tags += "L"; hands += it }
    frame.rightHand?.takeIf { it.size >= 21 }?.let { tags += "R"; hands += it }
}

/** Where a normalised landmark lands in a [viewW] x [viewH] view showing the frame letterboxed (Fit) and
 * centred. */
internal fun overlayPoint(p: Offset, frameAspect: Float, viewW: Float, viewH: Float, mirrored: Boolean, rotation: Int = 0, crop: Boolean = false): Offset {
    val (x, y) = when (rotation.mod(360)) {
        90 -> 1f - p.y to p.x
        180 -> 1f - p.x to 1f - p.y
        270 -> p.y to 1f - p.x
        else -> p.x to p.y
    }
    val aspect = if (rotation.mod(180) == 0) frameAspect else 1f / frameAspect
    val w = if (crop) maxOf(viewW, viewH * aspect) else minOf(viewW, viewH * aspect)
    val h = w / aspect
    return Offset((viewW - w) / 2f + (if (mirrored) 1f - x else x) * w, (viewH - h) / 2f + y * h)
}

// About 33 frames a second inside a sign window, 30 in preview, 4 in an empty room.
private const val ANALYSIS_INTERVAL_MS = 30L
private const val PREVIEW_INTERVAL_MS = 33L
private const val IDLE_INTERVAL_MS = 250L
private const val IDLE_AFTER_MS = 10_000L

/** A sentinel such as Long.MIN_VALUE overflows the subtraction and would skip every frame, so the first one is null. */
internal fun analysisDue(lastMs: Long?, ms: Long, interval: Long = ANALYSIS_INTERVAL_MS) = lastMs == null || ms - lastMs >= interval
// Preview and analysis share one tall 16:9 shape, so the skeleton lines up and a portrait window shows the whole frame.
private val PREVIEW_RESOLUTION = ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY).build()

private val ANALYSIS_RESOLUTION = ResolutionSelector.Builder()
    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
    .setResolutionStrategy(ResolutionStrategy(Size(848, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
    .build()

/** Mouth, brow, pointing, palm, shrug and thumb readings of one frame, as tuned by the constants in LandmarkTracker.kt. */
internal fun tuningLine(f: LandmarkFrame): String {
    val (mouth, brows) = faceCues(f) ?: (Float.NaN to Float.NaN)
    val thumbs = f.hands.mapNotNull { thumb(it, f.aspect) }
    return "mouth %.2f brow %.2f%s%s | point %s palm %s shrug %s thumb %s".format(
        mouth, brows, if (f.browsUp) " UP" else "", if (f.mouthOpen) " OPEN" else "",
        region(f) ?: "-", raisedPalm(f), shrug(f), thumbs.joinToString().ifEmpty { "-" },
    )
}
