package app.signsetu.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import androidx.compose.ui.geometry.Offset
import androidx.core.graphics.createBitmap
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A region of the upright frame, normalised to 0..1 on both axes. */
data class Focus(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    fun contains(o: Focus) = o.left >= left && o.top >= top && o.right <= right && o.bottom <= bottom

    companion object { val FULL = Focus(0f, 0f, 1f, 1f) }
}

/** What the Y plane alone says about a frame, before any colour is computed. */
data class LumaStats(val energy: Float, val sharpness: Float, val moved: Focus?)

/** Native YUV_420_888 handling: luma statistics and a fused crop, rotate and RGBA convert in one pass. */
object YuvFrames {
    val loaded: Boolean by lazy { runCatching { System.loadLibrary("frames") }.isSuccess }

    @JvmStatic external fun convert(
        y: ByteBuffer, u: ByteBuffer, v: ByteBuffer, yRow: Int, uvRow: Int, uvPix: Int,
        w: Int, h: Int, rot: Int, cx: Int, cy: Int, cw: Int, ch: Int, out: Bitmap,
    ): Boolean

    @JvmStatic external fun luma(
        y: ByteBuffer, yRow: Int, w: Int, h: Int, rot: Int, prev: ByteArray, grid: ByteArray, gw: Int, gh: Int, moved: Int, out: FloatArray,
    )
}

/** Block-mean luma of the last frame against this one: a pixel-level motion energy that costs about 1/50 of a
 * tracker call, so an empty room is watched at full rate and the tracker wakes on the first movement. */
internal class LumaGate(private val gw: Int = 32, private val gh: Int = 32) {
    private var prev = ByteArray(gw * gh)
    private var grid = ByteArray(gw * gh)
    private val out = FloatArray(6)
    private var primed = false

    fun read(image: ImageProxy, rot: Int): LumaStats? {
        if (!YuvFrames.loaded) return null
        val y = image.planes[0]
        YuvFrames.luma(y.buffer, y.rowStride, image.width, image.height, rot, prev, grid, gw, gh, MOVED_LEVEL, out)
        prev = grid.also { grid = prev }
        val first = !primed
        primed = true
        if (first) return LumaStats(0f, out[1], null)
        val box = if (out[2] < 0f) null else Focus(out[2], out[3], out[4], out[5])
        return LumaStats(out[0], out[1], box)
    }

    companion object {
        /** Luma steps a block must change by to count as moved; sensor noise in a dim room stays under it. */
        const val MOVED_LEVEL = 10
        /** Mean block change that means someone moved in view. */
        const val WAKE_ENERGY = 1.6f
    }
}

/** The part of the frame the tracker is shown during a sign window: the signer's reach, from the last landmarks and
 * the moving blocks, held still until the body leaves it, so the tracker's own smoothing sees a steady picture.
 * The window keeps the frame's aspect, so landmarks map back by a scale and an offset only. */
internal class FocusWindow {
    var current: Focus = Focus.FULL
        private set
    private var lost = 0
    /** A window was chosen for the body now in view; until it leaves, the window only grows. */
    private var settled = false

    fun update(last: LandmarkFrame?, moved: Focus?): Focus {
        val want = last?.let { reach(it) }
        if (want == null) {
            // A few frames without a body are a turned head or a blur; then the whole frame is searched again.
            if (++lost > LOST_FRAMES) { current = Focus.FULL; settled = false }
            return current
        }
        lost = 0
        val target = moved?.let { union(want, it) } ?: want
        // Only grown, never shrunk or slid: each move costs the tracker its hold on the hands for a frame or two.
        if (!settled) { current = fit(target); settled = true }
        else if (!current.contains(target)) current = fit(union(current, target))
        return current
    }

    companion object {
        const val LOST_FRAMES = 6
        /** Larger than this, the crop saves little and the full frame is used. */
        const val MAX_SHARE = 0.8f

        /** Shoulders out by an arm's reach, from above the head to the hips; hands are added where seen. */
        fun reach(f: LandmarkFrame): Focus? {
            val pose = f.pose ?: return null
            if (pose.size < 25) return null
            val ls = pose[11]
            val rs = pose[12]
            // Shoulder span in width units; height units differ by the frame's aspect.
            val span = kotlin.math.abs(ls.x - rs.x).coerceAtLeast(0.05f)
            val spanY = span * f.aspect
            val cx = (ls.x + rs.x) / 2
            val top = min(pose[0].y, min(ls.y, rs.y)) - spanY * 1.1f
            val bottom = max((pose[23].y + pose[24].y) / 2, max(ls.y, rs.y) + spanY * 1.2f)
            var box = Focus(cx - span * 1.9f, top, cx + span * 1.9f, bottom)
            for (hand in f.hands) for (p in hand) box = union(box, Focus(p.x - 0.04f, p.y - 0.04f * f.aspect, p.x + 0.04f, p.y + 0.04f * f.aspect))
            return box
        }

        fun union(a: Focus, b: Focus) = Focus(min(a.left, b.left), min(a.top, b.top), max(a.right, b.right), max(a.bottom, b.bottom))

        /** Grows [f] to the frame's own shape and clamps it inside; a window near the whole frame becomes the whole frame. */
        fun fit(f: Focus): Focus {
            val side = max(f.width, f.height).coerceIn(0.3f, 1f)
            if (side > MAX_SHARE) return Focus.FULL
            val cx = ((f.left + f.right) / 2).coerceIn(side / 2, 1 - side / 2)
            val cy = ((f.top + f.bottom) / 2).coerceIn(side / 2, 1 - side / 2)
            return Focus(cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2)
        }
    }
}

/** Turns camera frames in YUV_420_888 into upright RGBA bitmaps of the focus window only, converting colour last. */
/** [ring] bitmaps are written in turn, so one shown on screen is not overwritten while it is drawn. */
internal class FrameConverter(ring: Int = 1) {
    private val bitmaps = arrayOfNulls<Bitmap>(ring)
    private var next = 0

    private fun target(w: Int, h: Int): Bitmap {
        val b = bitmaps[next]?.takeIf { it.width == w && it.height == h } ?: createBitmap(w, h).also { bitmaps[next] = it }
        next = (next + 1) % bitmaps.size
        return b
    }

    /** Pixel crop in upright coordinates, rounded to even sizes so chroma lines up. */
    class Crop(val x: Int, val y: Int, val w: Int, val h: Int, val uprightW: Int, val uprightH: Int)

    fun crop(image: ImageProxy, rot: Int, focus: Focus): Crop {
        val uw = if (rot == 90 || rot == 270) image.height else image.width
        val uh = if (rot == 90 || rot == 270) image.width else image.height
        val x = (focus.left * uw).roundToInt().coerceIn(0, uw - 2) and 1.inv()
        val y = (focus.top * uh).roundToInt().coerceIn(0, uh - 2) and 1.inv()
        val w = ((focus.width * uw).roundToInt() and 1.inv()).coerceIn(2, uw - x)
        val h = ((focus.height * uh).roundToInt() and 1.inv()).coerceIn(2, uh - y)
        return Crop(x, y, w, h, uw, uh)
    }

    /** The bitmap comes back to the caller again after [ring] frames; it must not be kept longer. */
    fun convert(image: ImageProxy, rot: Int, c: Crop): Bitmap {
        if (YuvFrames.loaded) {
            val b = target(c.w, c.h)
            val (yp, up, vp) = image.planes
            if (YuvFrames.convert(yp.buffer, up.buffer, vp.buffer, yp.rowStride, up.rowStride, up.pixelStride,
                    image.width, image.height, rot, c.x, c.y, c.w, c.h, b)) return b
        }
        // No native library: CameraX converts, then one matrix pass turns and crops.
        val raw = image.toBitmap()
        val m = Matrix().apply { postRotate(rot.toFloat()) }
        val upright = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        if (upright !== raw) raw.recycle()
        val out = if (c.w == upright.width && c.h == upright.height) upright else Bitmap.createBitmap(upright, c.x, c.y, c.w, c.h).also { upright.recycle() }
        return target(c.w, c.h).also { android.graphics.Canvas(it).drawBitmap(out, 0f, 0f, null); out.recycle() }
    }

    /** Never recycled here: the screen may still be drawing one; the collector frees them. */
    fun close() { bitmaps.fill(null) }
}

/** Maps landmarks the tracker found in a crop back to the whole upright frame. */
internal fun LandmarkFrame.fromCrop(c: FrameConverter.Crop): LandmarkFrame {
    if (c.w == c.uprightW && c.h == c.uprightH) return this
    val sx = c.w.toFloat() / c.uprightW
    val sy = c.h.toFloat() / c.uprightH
    val ox = c.x.toFloat() / c.uprightW
    val oy = c.y.toFloat() / c.uprightH
    fun List<Offset>?.map() = this?.map { Offset(ox + it.x * sx, oy + it.y * sy) }
    return copy(leftHand = leftHand.map(), rightHand = rightHand.map(), face = face.map(), pose = pose.map(), aspect = c.uprightW.toFloat() / c.uprightH)
}
