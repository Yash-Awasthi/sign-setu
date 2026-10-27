package app.signsetu.ui.avatar

import android.annotation.SuppressLint
import android.content.Context
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
) {
    val context = LocalContext.current
    val web = remember { AvatarWebView(context) }
    val finished by rememberUpdatedState(onFinished)
    DisposableEffect(web) { onDispose { web.destroy() } }
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

/** Serves only assets/avatar to its page and runs commands once the model has loaded. */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
private class AvatarWebView(context: Context) : WebView(context) {
    private var ready = false
    private var pending = linkedMapOf<String, String>()
    private var token = 0
    private var onFinished: () -> Unit = {}

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
        }
        loadUrl("https://$HOST/avatar/index.html")
    }

    fun nextToken(onFinished: () -> Unit): Int {
        this.onFinished = onFinished
        return ++token
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
