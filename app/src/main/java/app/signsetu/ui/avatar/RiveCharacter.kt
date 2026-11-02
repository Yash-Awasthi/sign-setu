package app.signsetu.ui.avatar

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Rive
import app.signsetu.data.content.Clip
import app.signsetu.data.content.Gesture
import java.io.File

/* A Rive character driven by the same rig as the drawn one: every frame the rig's pose (from keyframes or a recorded
 * clip) is written to the file's state machine as number inputs, so a designer's .riv only binds those inputs to its
 * bones and blend states (docs/05-avatar-and-gestures.md, "Rive character"). The app ships no .riv; a department copies
 * one to the app's files as avatar/signer.riv, or a build adds it to assets/avatar/. */

internal object RiveCharacter {
    const val FILE = "avatar/signer.riv"
    const val MACHINE = "Signer"

    /** Hand shapes in the order the file's handR and handL inputs number them; measured shapes snap to the nearest. */
    val SHAPES: List<String> = HandShape.ALL.keys.toList()

    private var started = false

    fun fileIn(context: Context): ByteArray? =
        File(context.filesDir, FILE).takeIf { it.isFile }?.readBytes()
            ?: runCatching { context.assets.open(FILE).use { it.readBytes() } }.getOrNull()

    /** The runtime's native library loads only when a Rive character is first shown, never at app start. */
    fun start(context: Context) {
        if (!started) { Rive.init(context.applicationContext); started = true }
    }

    fun nearestShape(s: HandShape): Int {
        var best = 0
        var bestD = Float.MAX_VALUE
        SHAPES.forEachIndexed { i, name ->
            val o = HandShape.ALL.getValue(name)
            val d = o.curls.zip(s.curls).sumOf { (a, b) -> ((a - b) * (a - b)).toDouble() }.toFloat() +
                (o.thumbAcross - s.thumbAcross).let { it * it } + 0.5f * (o.spread - s.spread).let { it * it }
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    /** The inputs written each frame, in character space (200 wide, y down). */
    fun write(view: RiveAnimationView, p: Pose) {
        fun n(name: String, v: Float) = runCatching { view.setNumberState(MACHINE, name, v) }
        for ((side, h) in listOf("R" to p.r, "L" to p.l)) {
            n("wrist${side}X", h.x); n("wrist${side}Y", h.y)
            n("hand$side", nearestShape(h.shape).toFloat())
            n("roll$side", h.roll)
            n("angle$side", if (h.angle.isNaN()) 0f else h.angle)
            if (!h.elbowX.isNaN()) { n("elbow${side}X", h.elbowX); n("elbow${side}Y", h.elbowY) }
        }
        n("tilt", p.tilt); n("nod", p.nod); n("turn", p.turn); n("lean", p.lean); n("shrug", p.shrug)
        n("browRaise", p.face.browRaise); n("browFrown", p.face.browFrown); n("smile", p.face.smile); n("mouthOpen", p.face.mouthOpen)
    }
}

@Composable
internal fun RiveCharacter(
    gesture: Gesture?,
    playKey: Any?,
    modifier: Modifier,
    speed: Float,
    idle: Boolean,
    atSeconds: Float?,
    view: AvatarView,
    clip: Clip?,
    onFinished: () -> Unit,
    onReady: () -> Unit,
) {
    val context = LocalContext.current
    val bytes = remember { RiveCharacter.fileIn(context) } ?: return
    val still = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val rive = remember {
        RiveCharacter.start(context)
        RiveAnimationView(context).apply { setRiveBytes(bytes, stateMachineName = RiveCharacter.MACHINE, autoplay = true) }
    }
    val finished by rememberUpdatedState(onFinished)
    val ready by rememberUpdatedState(onReady)
    DisposableEffect(rive) { onDispose { rive.stop() } }
    LaunchedEffect(rive) { ready() }
    LaunchedEffect(view) { runCatching { rive.setBooleanState(RiveCharacter.MACHINE, "headView", view == AvatarView.Head) } }
    LaunchedEffect(idle, still) { runCatching { rive.setBooleanState(RiveCharacter.MACHINE, "idle", idle && !still) } }
    val timeline = remember(gesture) { Timeline(gesture, AdultBuild) }
    LaunchedEffect(playKey, timeline, clip, speed, atSeconds) {
        val length = clip?.seconds ?: timeline.duration
        fun pose(t: Float) = if (clip != null) clipPose(clip, t, AdultBuild) else timeline.pose(t)
        if (atSeconds != null) { RiveCharacter.write(rive, pose(atSeconds)); return@LaunchedEffect }
        if (length <= 0f) { RiveCharacter.write(rive, Timeline.restPose(AdultBuild)); return@LaunchedEffect }
        val start = withFrameNanos { it }
        var t = 0f
        while (t < length) withFrameNanos { now -> t = (now - start) / 1e9f * speed; RiveCharacter.write(rive, pose(t)) }
        finished()
    }
    AndroidView(factory = { rive }, modifier = modifier)
}
