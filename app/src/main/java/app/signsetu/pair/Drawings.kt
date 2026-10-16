package app.signsetu.pair

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream

/** Officer side: refuses oversized bounds before decoding, then keeps only a PNG this phone encoded itself. */
fun reencodeRemotePng(bytes: ByteArray): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (!drawingBoundsOk(bounds.outWidth, bounds.outHeight)) return null
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    return try { png(bitmap) } finally { bitmap.recycle() }
}

/** Stage side: halves the drawing until it fits a frame; null if it never does. */
fun fitDrawing(bytes: ByteArray): ByteArray? {
    if (bytes.size <= DRAWING_CAP) return bytes
    var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    repeat(4) {
        val smaller = bitmap.scale((bitmap.width / 2).coerceAtLeast(1), (bitmap.height / 2).coerceAtLeast(1))
        bitmap.recycle()
        bitmap = smaller
        val out = png(bitmap)
        if (out.size <= DRAWING_CAP) return out.also { bitmap.recycle() }
    }
    bitmap.recycle()
    return null
}

private fun png(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
