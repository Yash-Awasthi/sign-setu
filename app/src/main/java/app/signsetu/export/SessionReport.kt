package app.signsetu.export

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.withTranslation
import app.signsetu.data.session.EventType
import app.signsetu.data.session.SessionEvent
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom

/** Officer-language text for the report, resolved by the caller from string resources. */
class ReportText(
    val title: String,
    val disclaimer: String,
    val header: List<Pair<String, String>>,
    val time: String,
    val event: String,
    val detail: String,
    val inReplyTo: String,
    val page: (Int, Int) -> String,
    val eventLabel: (EventType) -> String,
    val formatTime: (Long) -> String,
)

class ReportRow(val event: SessionEvent, val question: String?, val detail: String)

/**
 * Builds the session report as an A4 PDF and encrypts it with AES-256 under a one-time passphrase (FR-32, SEC-12).
 * The report opens in any PDF reader; nothing else is needed by the case file or CWC.
 */
class SessionReport(private val context: Context) {

    suspend fun export(rows: List<ReportRow>, text: ReportText, drawing: suspend (Long) -> ByteArray?): Pair<File, String> =
        withContext(Dispatchers.IO) {
            val plain = render(rows, text, drawing)
            val passphrase = newPassphrase()
            PDFBoxResourceLoader.init(context)
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "sign-setu-report.pdf")
            PDDocument.load(plain).use { doc ->
                val owner = ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
                val policy = StandardProtectionPolicy(owner, passphrase, AccessPermission()).apply { encryptionKeyLength = 256 }
                doc.protect(policy)
                doc.save(file)
            }
            plain.fill(0)
            file to passphrase
        }

    private suspend fun render(rows: List<ReportRow>, text: ReportText, drawing: suspend (Long) -> ByteArray?): ByteArray {
        val pdf = PdfDocument()
        val pages = mutableListOf<PdfDocument.Page>()
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 17f; typeface = Typeface.DEFAULT_BOLD }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f }
        val smallPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8.5f; color = 0xFF555555.toInt() }
        val headPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; typeface = Typeface.DEFAULT_BOLD }
        val rule = Paint().apply { color = 0xFFCCCCCC.toInt(); strokeWidth = 0.6f }

        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = 0f

        fun newPage() {
            page?.let { pdf.finishPage(it) }
            val p = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pages.size + 1).create())
            pages += p
            page = p
            canvas = p.canvas
            y = MARGIN
        }

        fun ensure(height: Float) {
            if (canvas == null || y + height > PAGE_H - MARGIN - FOOTER) newPage()
        }

        fun block(s: String, paint: TextPaint, width: Int): StaticLayout =
            StaticLayout.Builder.obtain(s, 0, s.length, paint, width).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

        fun draw(layout: StaticLayout, x: Float, top: Float) {
            canvas!!.withTranslation(x, top) { layout.draw(this) }
        }

        newPage()
        val full = PAGE_W - 2 * MARGIN.toInt()
        listOf(block(text.title, titlePaint, full), block(text.disclaimer, smallPaint, full)).forEach { l ->
            draw(l, MARGIN, y); y += l.height + 8
        }
        text.header.forEach { (k, v) ->
            val l = block("$k: $v", bodyPaint, full)
            draw(l, MARGIN, y); y += l.height + 2
        }
        y += 10

        val colTime = MARGIN
        val colEvent = MARGIN + 52
        val colDetail = MARGIN + 160
        val detailWidth = PAGE_W - MARGIN.toInt() - colDetail.toInt()

        fun headerRow() {
            draw(block(text.time, headPaint, 50), colTime, y)
            draw(block(text.event, headPaint, 104), colEvent, y)
            draw(block(text.detail, headPaint, detailWidth), colDetail, y)
            y += 14
            canvas!!.drawLine(MARGIN, y, PAGE_W - MARGIN, y, rule)
            y += 6
        }
        headerRow()

        for (row in rows) {
            val detail = block(row.detail, bodyPaint, detailWidth)
            val reply = row.question?.takeIf { it != row.detail }?.let { block("${text.inReplyTo}: $it", smallPaint, detailWidth) }
            val eventLayout = block(text.eventLabel(row.event.type), bodyPaint, 104)
            val image = row.event.drawingId?.let { id -> drawing(id)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
            val imageHeight = image?.let { (detailWidth.toFloat() / it.width * it.height).coerceAtMost(260f) } ?: 0f
            val rowHeight = maxOf(detail.height + (reply?.height?.plus(2) ?: 0), eventLayout.height) + imageHeight + 10
            val pageBefore = pages.size
            ensure(rowHeight)
            if (pages.size != pageBefore) headerRow()
            draw(block(text.formatTime(row.event.ts), bodyPaint, 50), colTime, y)
            draw(eventLayout, colEvent, y)
            draw(detail, colDetail, y)
            var inner = y + detail.height
            reply?.let { draw(it, colDetail, inner + 2); inner += it.height + 2 }
            image?.let { bmp ->
                val w = (imageHeight / bmp.height * bmp.width)
                canvas!!.drawBitmap(bmp, null, Rect(colDetail.toInt(), (inner + 4).toInt(), (colDetail + w).toInt(), (inner + 4 + imageHeight).toInt()), null)
                bmp.recycle()
            }
            y += rowHeight
            canvas!!.drawLine(MARGIN, y - 5, PAGE_W - MARGIN, y - 5, rule)
        }
        page?.let { pdf.finishPage(it) }

        // Page numbers need the final count, so they are written after the PDF is laid out, by PdfBox.
        val bytes = ByteArrayOutputStream().use { out -> pdf.writeTo(out); pdf.close(); out.toByteArray() }
        return numberPages(bytes, text)
    }

    private fun numberPages(bytes: ByteArray, text: ReportText): ByteArray {
        PDFBoxResourceLoader.init(context)
        return PDDocument.load(bytes).use { doc ->
            val total = doc.numberOfPages
            val font = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA
            for (i in 0 until total) {
                val label = text.page(i + 1, total).filter { it.code < 128 }.ifBlank { "${i + 1} / $total" }
                com.tom_roush.pdfbox.pdmodel.PDPageContentStream(
                    doc, doc.getPage(i), com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND, true, true,
                ).use { cs ->
                    cs.beginText()
                    cs.setFont(font, 8f)
                    cs.newLineAtOffset(MARGIN, MARGIN / 2)
                    cs.showText(label)
                    cs.endText()
                }
            }
            ByteArrayOutputStream().use { out -> doc.save(out); out.toByteArray() }
        }
    }

    private companion object {
        const val PAGE_W = 595
        const val PAGE_H = 842
        const val MARGIN = 40f
        const val FOOTER = 14f
        // Crockford base32 without look-alike characters; 16 symbols = 80 bits.
        const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

        fun newPassphrase(): String {
            val random = SecureRandom()
            return (1..16).map { ALPHABET[random.nextInt(ALPHABET.length)] }.chunked(4).joinToString("-") { it.joinToString("") }
        }
    }
}
