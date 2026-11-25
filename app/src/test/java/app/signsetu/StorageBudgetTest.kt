package app.signsetu

import androidx.compose.ui.geometry.Offset
import app.signsetu.camera.LandmarkFrame
import app.signsetu.data.settings.AppSettings
import app.signsetu.export.StationExport
import app.signsetu.sign.NARRATIVE_LIMIT_MS
import app.signsetu.sign.SIGN_LIMIT_MS
import app.signsetu.voice.VoiceCapture
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What one session costs on disk, measured from the formats the app itself writes, and the retention windows it must
 * stay inside: the documented budget is docs/23-storage-budget.md. Sizes are the real serialisers, not estimates. */
class StorageBudgetTest {

    /** The documented ceiling for the app's own data on the phone. */
    private val budget = 1_000_000_000L

    /** The busiest station the budget plans for: one conversation every half hour, around the clock. */
    private val sessionsPerHour = 2L

    private val framesPerSecond = 30

    /** A sign window as the camera captures it: both hands (21 points), the pose (33 points), 30 frames a second. */
    private fun window(seconds: Int): List<LandmarkFrame> = (0 until seconds * framesPerSecond).map { i ->
        val phase = i.toFloat() / framesPerSecond
        fun hand(dx: Float) = List(21) { p ->
            Offset(0.45f + dx + 0.08f * sin((phase * 2 * PI + p * 0.3).toFloat()), 0.40f + 0.10f * sin((phase * 1.5 * PI + p * 0.2).toFloat()))
        }
        fun pose() = List(33) { p ->
            Offset(0.50f + 0.10f * sin((phase + p * 0.1).toFloat()), 0.30f + 0.20f * sin((phase * 0.5f + p * 0.05f).toFloat()))
        }
        LandmarkFrame(
            timestampMs = (i * 1_000L) / framesPerSecond,
            leftHand = hand(-0.05f),
            rightHand = hand(0.05f),
            pose = pose(),
            aspect = 0.5625f,
            browsUp = i % 60 == 0,
            mouthOpen = i % 90 == 0,
            yaw = 4f * sin(phase.toDouble()).toFloat(),
            pitch = 2f * sin(phase.toDouble()).toFloat(),
        )
    }

    /** A drawing as the stage writes it: a full-size sheet with a few strokes, PNG at full quality. */
    private fun drawing(): Int {
        val image = BufferedImage(1080, 1440, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = java.awt.Color(0xFA, 0xF7, 0xEF)
        g.fillRect(0, 0, 1080, 1440)
        g.color = java.awt.Color(0x22, 0x22, 0x22)
        g.stroke = java.awt.BasicStroke(8f)
        for (s in 0 until 12) {
            val path = java.awt.geom.Path2D.Float()
            path.moveTo(100f + s * 60, 200f)
            for (p in 1..20) path.lineTo(100f + s * 60 + 20f * sin(p.toDouble()).toFloat(), 200f + p * 55f)
            g.draw(path)
        }
        g.dispose()
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return out.size()
    }

    @Test
    fun oneDocumentedWorstCaseSessionFitsTheCeiling() {
        val narrative = StationExport.signsJson(listOf(window(NARRATIVE_LIMIT_MS.toInt() / 1000)), listOf("narrative")).size
        val answer = StationExport.signsJson(listOf(window(SIGN_LIMIT_MS.toInt() / 1000)), listOf("answer")).size
        val audio = VoiceCapture.wav(ByteArray(VoiceCapture.MAX_SECONDS * VoiceCapture.RATE * 2)).size
        val sheet = drawing()
        val rows = 400 * 512
        val worst = narrative + audio + sheet + rows
        println("storage: narrative landmarks $narrative B, answer $answer B, audio ${audio} B, drawing $sheet B, rows $rows B, worst session $worst B")
        assertTrue("a sign window is stored gzipped and capped at 1.8 MB, measured $narrative", narrative in 1..1_800_000)
        assertTrue("a 20-second answer is smaller than a narrative, measured $answer", answer in 1..narrative)
        assertEquals("a 60-second take is PCM at the recorder's own rate", 44 + VoiceCapture.MAX_SECONDS * VoiceCapture.RATE * 2, audio)
        assertTrue("the worst session stays under the 8 MB documented per-session ceiling, measured $worst", worst <= 8_000_000)
    }

    @Test
    fun everyRetentionWindowOfferedKeepsThatRateOfSessionsInsideTheBudget() {
        val worst = StationExport.signsJson(listOf(window(NARRATIVE_LIMIT_MS.toInt() / 1000)), listOf("narrative")).size +
            VoiceCapture.wav(ByteArray(VoiceCapture.MAX_SECONDS * VoiceCapture.RATE * 2)).size + drawing() + 400 * 512
        for (hours in AppSettings.RETENTION_CHOICES.filter { it > 0 }) {
            val kept = hours * sessionsPerHour * worst
            println("storage: ${hours}h window keeps up to ${hours * sessionsPerHour} worst-case sessions = $kept B")
            assertTrue("$hours h at $sessionsPerHour sessions an hour is $kept B, over the ${budget} B budget", kept <= budget)
        }
        val typical = StationExport.signsJson(listOf(window(5)), listOf("answer")).size + VoiceCapture.wav(ByteArray(8 * VoiceCapture.RATE * 2)).size + 3 * drawing() + 40 * 512
        println("storage: typical session $typical B")
        assertTrue("a typical session is small, measured $typical", typical < 1_500_000)
    }
}
