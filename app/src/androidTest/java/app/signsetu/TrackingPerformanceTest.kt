package app.signsetu

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.camera.FocusWindow
import app.signsetu.camera.HolisticTracker
import app.signsetu.camera.YuvFrames
import app.signsetu.camera.fromCrop
import app.signsetu.camera.FrameConverter
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer

/** Real-time tracking cost per camera frame on this device: luma gate, YUV to RGBA (whole frame and focus window)
 * and the landmark tracker, over a real signer's clip played as camera frames. The budget is a 30 fps camera
 * (33 ms a frame); the emulator is far slower than a phone, so the assertion only catches a broken pipeline. */
@RunWith(AndroidJUnit4::class)
class TrackingPerformanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext

    @Test
    fun aSignersClipIsTrackedWithinTheFrameBudget() {
        assertTrue("native frame library", YuvFrames.loaded)
        val tracker = HolisticTracker(app).apply { warm() }
        val luma = ArrayList<Double>(); val full = ArrayList<Double>(); val focus = ArrayList<Double>(); val track = ArrayList<Double>()
        val window = FocusWindow()
        var last: app.signsetu.camera.LandmarkFrame? = null
        val prev = ByteArray(32 * 32); val grid = ByteArray(32 * 32); val out = FloatArray(6)
        frames("husband") { ms, bmp ->
            val w = bmp.width and 1.inv(); val h = bmp.height and 1.inv()
            val (y, u, v) = i420(bmp, w, h)
            luma += time { YuvFrames.luma(y, w, w, h, 0, prev, grid, 32, 32, 10, out) }
            val whole = createBitmap(w, h)
            full += time { YuvFrames.convert(y, u, v, w, w / 2, 1, w, h, 0, 0, 0, w, h, whole) }
            val f = window.update(last, null)
            val crop = FrameConverter.Crop((f.left * w).toInt() and 1.inv(), (f.top * h).toInt() and 1.inv(), ((f.width * w).toInt() and 1.inv()).coerceAtLeast(2), ((f.height * h).toInt() and 1.inv()).coerceAtLeast(2), w, h)
            val part = createBitmap(crop.w, crop.h)
            focus += time { YuvFrames.convert(y, u, v, w, w / 2, 1, w, h, 0, crop.x, crop.y, crop.w, crop.h, part) }
            track += time { last = tracker.track(part, ms)?.fromCrop(crop)?.copy(aspect = w.toFloat() / h) }
            whole.recycle(); part.recycle()
        }
        tracker.close()
        fun line(name: String, xs: List<Double>) = "%s p50 %.1f ms p95 %.1f ms".format(name, pct(xs, 50), pct(xs, 95))
        val total = luma.indices.map { luma[it] + focus[it] + track[it] }
        Log.i(TAG, listOf(line("luma", luma), line("convert whole", full), line("convert focus", focus), line("tracker", track), line("per frame", total)).joinToString(" | ") + " | ${total.size} frames")
        assertTrue("a frame takes under 1 s even on an emulator", pct(total, 95) < 1000)
        assertTrue("YUV work stays a small share of a frame", pct(luma, 95) + pct(focus, 95) < pct(track, 50))
    }

    private fun time(block: () -> Unit): Double { val t = SystemClock.elapsedRealtimeNanos(); block(); return (SystemClock.elapsedRealtimeNanos() - t) / 1e6 }

    private fun pct(xs: List<Double>, p: Int) = xs.sorted().let { it[((it.size - 1) * p / 100.0).toInt()] }

    /** A bitmap as the camera would deliver it: I420 planes, full-range BT.601. */
    private fun i420(b: Bitmap, w: Int, h: Int): Triple<ByteBuffer, ByteBuffer, ByteBuffer> {
        val px = IntArray(w * h).also { b.getPixels(it, 0, w, 0, 0, w, h) }
        val y = ByteBuffer.allocateDirect(w * h); val u = ByteBuffer.allocateDirect(w * h / 4); val v = ByteBuffer.allocateDirect(w * h / 4)
        for (j in 0 until h) for (i in 0 until w) {
            val c = px[j * w + i]; val r = (c shr 16) and 255; val g = (c shr 8) and 255; val bl = c and 255
            y.put(j * w + i, (0.299 * r + 0.587 * g + 0.114 * bl).toInt().coerceIn(0, 255).toByte())
            if (j % 2 == 0 && i % 2 == 0) {
                val k = (j / 2) * (w / 2) + i / 2
                u.put(k, (128 - 0.168736 * r - 0.331264 * g + 0.5 * bl).toInt().coerceIn(0, 255).toByte())
                v.put(k, (128 + 0.5 * r - 0.418688 * g - 0.081312 * bl).toInt().coerceIn(0, 255).toByte())
            }
        }
        return Triple(y, u, v)
    }

    private fun frames(word: String, onFrame: (Long, Bitmap) -> Unit) {
        val file = File(app.cacheDir, "$word.mp4")
        instrumentation.context.assets.open("islrtc/$word.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
        val r = MediaMetadataRetriever().apply { setDataSource(file.path) }
        try {
            val count = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)!!.toInt()
            val frameMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong().toDouble() / count
            for (i in 0 until count) {
                val f = r.getFrameAtIndex(i)!!
                val scale = 640f / maxOf(f.width, f.height)
                val s = if (scale < 1) Bitmap.createScaledBitmap(f, (f.width * scale).toInt(), (f.height * scale).toInt(), true) else f
                onFrame((i * frameMs).toLong(), s)
                if (s !== f) s.recycle()
                f.recycle()
            }
        } finally { r.release(); file.delete() }
    }

    private companion object { const val TAG = "TrackingPerformance" }
}
