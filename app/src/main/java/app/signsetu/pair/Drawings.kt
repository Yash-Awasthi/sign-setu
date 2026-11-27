package app.signsetu.pair

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream

private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
private val IHDR = "IHDR".toByteArray()

/** Officer side: refuses oversized bounds before decoding, then keeps only a PNG this phone encoded itself. */
fun reencodeRemotePng(bytes: ByteArray): ByteArray? {
    val (width, height) = pngSize(bytes) ?: return null
    // The declared size, not the decoder's own bounds pass: a bogus header is refused before any allocation.
    if (!drawingBoundsOk(width, height)) return null
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    return try { png(bitmap) } finally { bitmap.recycle() }
}

/** The width and height a PNG declares in its IHDR chunk, read in plain Kotlin so a rogue payload never reaches
 * the decoder; null for anything that is not a PNG. */
internal fun pngSize(bytes: ByteArray): Pair<Int, Int>? {
    if (bytes.size < 24 || !bytes.copyOf(8).contentEquals(PNG_SIGNATURE)) return null
    if (!bytes.copyOfRange(12, 16).contentEquals(IHDR)) return null
    fun be(i: Int) = ((bytes[i].toInt() and 0xFF) shl 24) or ((bytes[i + 1].toInt() and 0xFF) shl 16) or
        ((bytes[i + 2].toInt() and 0xFF) shl 8) or (bytes[i + 3].toInt() and 0xFF)
    return be(16) to be(20)
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
