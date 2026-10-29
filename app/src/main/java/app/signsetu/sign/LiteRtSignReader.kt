package app.signsetu.sign

import app.signsetu.camera.LandmarkFrame
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp

/** The format 3 reader (tools/ml/models.py, export_litert.py): raw landmarks in, features computed inside the LiteRT
 * graph, so Python and the phone cannot drift. Input (1, frames, RAW) as models.py lays it out, plus the aspect. */
class LiteRtSignReader(spec: String, model: ByteArray, override val words: Map<String, String> = emptyMap(),
    override val cards: Map<String, List<String>> = emptyMap(),
) : SignReader {
    @Serializable
    private class Spec(
        val format: Int, val frames: Int, val labels: List<String>, val temperature: Float = 1f,
        val conformal: Conformal? = null, @SerialName("face_points") val facePoints: List<Int>,
        @SerialName("spell_odds") val spellOdds: Float? = null,
    )

    private val s = SPEC.decodeFromString<Spec>(spec)
    override val labels: List<String> = s.labels
    override val conformal: Conformal? = s.conformal
    override val spellOdds: Float? = s.spellOdds
    private val interpreter = Interpreter(ByteBuffer.allocateDirect(model.size).order(ByteOrder.nativeOrder()).also { it.put(model); it.rewind() })
    // onnx2tf may reorder the two inputs; the raw one is told apart by its width.
    private val rawInput = (0 until interpreter.inputTensorCount).first { interpreter.getInputTensor(it).shape().last() == RAW }

    init {
        require(s.format == 3 && s.facePoints.size * 2 == RAW - 154) { "format" }
        require(interpreter.getOutputTensor(0).shape().last() == labels.size) { "labels" }
    }

    override fun read(frames: List<LandmarkFrame>): List<SignGuess> {
        val z = logits(frames) ?: return emptyList()
        val top = z.max()
        val e = FloatArray(z.size) { exp((z[it] - top) / s.temperature) }
        val sum = e.sum()
        return e.indices.sortedByDescending { e[it] }.map { SignGuess(labels[it], e[it] / sum) }
    }

    /** Raw class scores, or null when no frame shows the shoulders (the graph centres on them). */
    @Synchronized
    fun logits(frames: List<LandmarkFrame>): FloatArray? {
        if (frames.none { (it.pose?.size ?: 0) > 16 }) return null
        val raw = Array(1) { rawFrames(frames, s.frames, s.facePoints) }
        val aspect = arrayOf(floatArrayOf(frames.first().aspect))
        val out = Array(1) { FloatArray(labels.size) }
        val inputs: Array<Any> = if (rawInput == 0) arrayOf(raw, aspect) else arrayOf(aspect, raw)
        interpreter.runForMultipleInputsOutputs(inputs, mapOf(0 to out))
        return out[0]
    }

    override fun close() = interpreter.close()
}

private val SPEC = Json { ignoreUnknownKeys = true }

/** Values per frame: two hands and the pose as (x, y), the kept face points, then four presence flags. */
private const val RAW = 42 + 42 + 66 + 64 + 4

/** [t] frames picked as features.resample_index picks them, each laid out as models.py's raw frame: MediaPipe image
 * points, zeros where a part is missing. */
internal fun rawFrames(frames: List<LandmarkFrame>, t: Int, facePoints: List<Int>): Array<FloatArray> {
    val last = frames.size - 1
    return Array(t) { j ->
        val f = frames[(j * last * 2 + (t - 1)) / (2 * (t - 1))]
        val row = FloatArray(RAW)
        fun put(at: Int, pts: List<androidx.compose.ui.geometry.Offset>) = pts.forEachIndexed { i, p -> row[at + 2 * i] = p.x; row[at + 2 * i + 1] = p.y }
        f.leftHand?.takeIf { it.size == 21 }?.let { put(0, it); row[RAW - 4] = 1f }
        f.rightHand?.takeIf { it.size == 21 }?.let { put(42, it); row[RAW - 3] = 1f }
        f.pose?.takeIf { it.size >= 33 }?.let { put(84, it.subList(0, 33)); row[RAW - 2] = 1f }
        f.face?.takeIf { face -> facePoints.all { it < face.size } }?.let { face -> put(150, facePoints.map { face[it] }); row[RAW - 1] = 1f }
        row
    }
}
