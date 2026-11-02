package app.signsetu.ui.avatar

import android.content.Context
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.signsetu.data.content.Clip
import app.signsetu.data.content.Gesture
import app.signsetu.data.content.QuestionKind

/** How the character is drawn: the 3D model, the drawn 2D one, or a Rive file the department supplies. */
enum class AvatarStyle { AUTO, FLAT, RIVE }

/** Process-wide choice and health of the renderers; Settings writes [style], the 3D view reports [vrmFailed]. */
object AvatarRenderer {
    val style = mutableStateOf(AvatarStyle.AUTO)
    /** The 3D view never got ready or its renderer died: the drawn character is used until the app restarts. */
    val vrmFailed = mutableStateOf(false)

    private var webViewChecked: Boolean? = null

    /** False on phones with no usable WebView provider, where constructing one throws. */
    fun webViewAvailable(): Boolean = webViewChecked ?: runCatching { WebView.getCurrentWebViewPackage() != null }.getOrDefault(false).also { webViewChecked = it }

    fun riveAvailable(context: Context): Boolean = RiveCharacter.fileIn(context) != null
}

/** The signing character: plays [gesture], or [clip] when given, each time [playKey] changes; speed below 1 plays
 * slowly; [atSeconds] holds one moment. With [idle] the character breathes and blinks. The renderer follows
 * [AvatarRenderer]: the 3D model by default, the drawn character where 3D cannot run, a Rive character on request. */
@Composable
fun Avatar(
    gesture: Gesture?,
    playKey: Any?,
    modifier: Modifier = Modifier,
    speed: Float = 1f,
    idle: Boolean = true,
    atSeconds: Float? = null,
    glance: Boolean = false,
    view: AvatarView = AvatarView.Body,
    question: QuestionKind? = null,
    negated: Boolean = false,
    clip: Clip? = null,
    onFinished: () -> Unit = {},
    /** Once the character can be shown. */
    onReady: () -> Unit = {},
) {
    val context = LocalContext.current
    val style by AvatarRenderer.style
    val failed by AvatarRenderer.vrmFailed
    val rive = remember(style) { style == AvatarStyle.RIVE && AvatarRenderer.riveAvailable(context) }
    val flat = !rive && (style == AvatarStyle.FLAT || failed || !AvatarRenderer.webViewAvailable())
    when {
        rive -> RiveCharacter(gesture, playKey, modifier, speed, idle, atSeconds, view, clip, onFinished, onReady)
        flat -> {
            val ready by rememberUpdatedState(onReady)
            LaunchedEffect(Unit) { ready() }
            FlatAvatar(gesture, playKey, modifier, speed, idle, atSeconds, glance, view, question, clip, onFinished = onFinished)
        }
        else -> VrmAvatar(gesture, playKey, modifier, speed, idle, atSeconds, glance, view, question, negated, clip, onFinished, onReady)
    }
}

/** Milliseconds the 3D model may take to load before the drawn character replaces it. */
internal const val VRM_READY_MS = 12_000L
