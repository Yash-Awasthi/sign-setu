package app.signsetu.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.signsetu.R

/** A set of runtime permissions asked together: [granted] once all are allowed, following the system, also after a
 * trip to the settings page. */
@Stable
class PermissionAsk internal constructor(private val context: Context, private val launch: () -> Unit) {
    var granted by mutableStateOf(false)
        internal set
    /** Denied with no prompt left to show: only the app's settings page can allow it now. */
    var blocked by mutableStateOf(false)
        internal set

    fun request() {
        if (granted) return
        if (blocked) context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) else launch()
    }
}

/** [permission], asked at once when [askNow] and not yet allowed, and again each time [PermissionAsk.request] is called. */
@Composable
fun rememberPermission(permission: String, askNow: Boolean = true): PermissionAsk = rememberPermissions(arrayOf(permission), askNow)

/** [permissions] together; none at all counts as allowed. [onResult] hears each answer to the prompt. */
@Composable
fun rememberPermissions(permissions: Array<String>, askNow: Boolean = true, onResult: (Boolean) -> Unit = {}): PermissionAsk {
    val context = LocalContext.current
    val key = permissions.joinToString()
    fun allowed() = permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    val launch = remember { arrayOfNulls<() -> Unit>(1) }
    val ask = remember(key) { PermissionAsk(context) { launch[0]?.invoke() }.also { it.granted = allowed() } }
    val result by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val ok = allowed()
        ask.granted = ok
        val activity = context.findActivity()
        ask.blocked = !ok && activity != null && permissions.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED && !activity.shouldShowRequestPermissionRationale(it)
        }
        result(ok)
    }
    launch[0] = { launcher.launch(permissions) }
    LaunchedEffect(key, askNow) { if (askNow && !ask.granted) launcher.launch(permissions) }
    LifecycleResumeEffect(key) {
        if (!ask.granted && allowed()) { ask.granted = true; ask.blocked = false }
        onPauseOrDispose { }
    }
    return ask
}

/** Asks again, or opens the settings page once Android no longer shows the prompt. */
@Composable
fun PermissionButton(ask: PermissionAsk, modifier: Modifier = Modifier) {
    FilledTonalButton(onClick = ask::request, modifier = modifier) {
        Text(stringResource(if (ask.blocked) R.string.permission_open_settings else R.string.permission_allow))
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
