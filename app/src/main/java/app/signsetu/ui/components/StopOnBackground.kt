package app.signsetu.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Runs [stop] when the app leaves the foreground. A sheet or stage stays composed while the activity is stopped,
 * so onDispose never runs: without this a mic or a player keeps going behind another app. */
@Composable
fun StopOnBackground(stop: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val latest = rememberUpdatedState(stop)
    DisposableEffect(owner) {
        val observer = owner.lifecycle.observeStop { latest.value() }
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

/** Attaches [stop] to ON_STOP only, not to a rotation, a recomposition or a dialog above this screen. */
internal fun Lifecycle.observeStop(stop: () -> Unit): LifecycleEventObserver {
    val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) stop() }
    addObserver(observer)
    return observer
}
