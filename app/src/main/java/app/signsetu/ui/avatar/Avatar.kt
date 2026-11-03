package app.signsetu.ui.avatar

import android.annotation.SuppressLint
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.MutableContextWrapper
import android.content.res.Configuration
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import app.signsetu.data.content.Clip
import app.signsetu.data.content.Gesture
import app.signsetu.data.content.QuestionKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/** Which part of the character a view frames: to the hips, or the head alone. */
enum class AvatarView(val js: String) { Body("body"), Head("head") }

/** Width over height of the body view; callers size the avatar with it. */
const val AVATAR_ASPECT = 220f / 258f

/** The 3D character (assets/avatar). Plays [gesture], or [clip] when given, each time [playKey] changes; speed
 * below 1 plays slowly; [atSeconds] holds one moment. With [idle] the character breathes and blinks. */
@Composable
internal fun VrmAvatar(
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
    /** Once the model has loaded and can be shown. */
    onReady: () -> Unit = {},
) {
    val context = LocalContext.current
    val web = remember { AvatarViews.take(context) }
    val finished by rememberUpdatedState(onFinished)
    val readySink by rememberUpdatedState(onReady)
    DisposableEffect(web) { web.onReady = { readySink() }; onDispose { web.onReady = {} } }
    DisposableEffect(web) { onDispose { AvatarViews.give(web) } }
    // A phone whose WebView never draws the model gets the drawn character from then on.
    // Wall time, off the frame clock: a test clock that skips ahead must not count as a model that failed to load.
    LaunchedEffect(web) { withContext(Dispatchers.Default) { delay(VRM_READY_MS) }; if (!web.isReady) AvatarRenderer.vrmFailed.value = true }
    LaunchedEffect(view) { web.run("signsetu.view('${view.js}')") }
    // Breathing and glancing change as the person starts and stops signing; that must not restart the signs.
    LaunchedEffect(idle, glance) { web.run("signsetu.mode({\"idle\": $idle, \"glance\": $glance})") }
    LaunchedEffect(playKey, gesture, clip, speed, atSeconds, question, negated) {
        val token = web.nextToken { finished() }
        web.run(
            if (clip != null) {
                "signsetu.clip(${buildJsonObject {
                    put("frames", JsonArray(clip.frames.map { f -> JsonArray(f.map(::JsonPrimitive)) }))
                    if (clip.face.isNotEmpty()) put("face", JsonArray(clip.face.map { f -> JsonArray(f.map(::JsonPrimitive)) }))
                    put("fps", clip.fps)
                    put("token", token)
                }})"
            } else {
                "signsetu.play(${buildJsonObject {
                    put("keys", json.encodeToJsonElement(gesture?.keys.orEmpty()))
                    put("speed", speed)
                    atSeconds?.let { put("at", it) }
                    put("idle", idle)
                    put("glance", glance)
                    question?.let { put("question", it.name) }
                    if (negated) put("negated", true)
                    put("token", token)
                }})"
            },
        )
    }
    AndroidView(factory = { web }, modifier = modifier)
}

private val json = Json { encodeDefaults = false }

/** Loaded 3D views kept between screens, so going back to a screen shows the character at once instead of loading
 * the model again. Each view wraps a swappable context: the screen's while shown, the app's while kept. */
object AvatarViews {
    private val idle = ArrayDeque<AvatarWebView>()
    /** One spare covers the moment two screens overlap in a transition; a third view would only cost memory. */
    private const val KEEP = 1
    private var trimHooked = false

    internal fun take(context: Context): AvatarWebView {
        hookTrim(context)
        val web = idle.removeFirstOrNull()?.also { (it.context as MutableContextWrapper).baseContext = context; it.onResume() }
            ?: AvatarWebView(MutableContextWrapper(context))
        (web.parent as? ViewGroup)?.removeView(web)
        return web
    }

    internal fun give(web: AvatarWebView) {
        (web.parent as? ViewGroup)?.removeView(web)
        web.forget()
        // A dead renderer or a full spare list: this view goes.
        if (AvatarRenderer.vrmFailed.value || idle.size >= KEEP) { web.destroy(); return }
        web.onPause()
        (web.context as MutableContextWrapper).baseContext = web.context.applicationContext
        idle.addLast(web)
    }

    private fun hookTrim(context: Context) {
        if (trimHooked) return
        trimHooked = true
        context.applicationContext.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                // Kept through the app going to the background; dropped only when the phone is short of memory.
                @Suppress("DEPRECATION")
                if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) drop()
            }
            override fun onConfigurationChanged(newConfig: Configuration) {}
            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = drop()
        })
    }

    /** Frees the kept views: memory is short, or another character style was chosen. */
    fun drop() { while (idle.isNotEmpty()) idle.removeFirst().destroy() }
}

/** Serves only assets/avatar to its page and runs commands once the model has loaded. */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
internal class AvatarWebView(context: Context) : WebView(context) {
    private var ready = false
    val isReady get() = ready
    private var pending = linkedMapOf<String, String>()
    private var token = 0
    private var onFinished: () -> Unit = {}
    var onReady: () -> Unit = {}
        set(value) { field = value; if (ready) value() }

    init {
        // Compose sizes the view; a wrap_content WebView lays its page out at zero height.
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setBackgroundColor(Color.TRANSPARENT)
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        addJavascriptInterface(Bridge(), "Android")
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                val path = request.url.path.orEmpty().removePrefix("/")
                // Nothing leaves the phone: a request for anything but the avatar's own files gets a 404.
                if (request.url.host != HOST || !path.startsWith("avatar/") || ".." in path) return NOT_FOUND()
                val type = when (path.substringAfterLast('.')) {
                    "html" -> "text/html"
                    "js" -> "text/javascript"
                    else -> "application/octet-stream"
                }
                return runCatching { WebResourceResponse(type, "utf-8", context.assets.open(path)) }.getOrElse { NOT_FOUND() }
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

            // A crashed or reclaimed renderer must not take the app with it; the drawn character takes over.
            override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                AvatarRenderer.vrmFailed.value = true
                return true
            }
        }
        loadUrl("https://$HOST/avatar/index.html")
    }

    fun nextToken(onFinished: () -> Unit): Int {
        this.onFinished = onFinished
        return ++token
    }

    /** Drops the last screen's callbacks, so a kept view does not hold that screen alive. */
    fun forget() {
        onFinished = {}
        onReady = {}
    }

    /** Commands of one kind replace each other while the model loads; only the latest matters. */
    fun run(js: String) {
        if (ready) evaluateJavascript(js, null) else pending[js.substringBefore('(')] = js
    }

    private inner class Bridge {
        @JavascriptInterface
        fun ready() = post {
            ready = true
            pending.values.forEach { evaluateJavascript(it, null) }
            pending.clear()
            onReady()
        }

        @JavascriptInterface
        fun finished(done: Int) = post { if (done == token) onFinished() }
    }

    private companion object {
        const val HOST = "appassets.androidplatform.net"
        @Suppress("FunctionName")
        fun NOT_FOUND() = WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), null)
    }
}

/** Seconds the character takes from rest into a gesture, as LEAD_IN in avatar/src/rig.ts. */
const val LEAD_IN = 0.35f

/** Seconds the character takes from a gesture's last key back to rest, as LEAD_OUT in avatar/src/rig.ts. */
const val LEAD_OUT = 0.45f
