package app.signsetu

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import app.signsetu.ui.components.observeStop
import org.junit.Assert.assertEquals
import org.junit.Test

/** Sheets and stages stay composed while the app is backgrounded; only ON_STOP may stop a mic or a player, and
 * nothing else (a rotation, a pause, a dialog) may. */
class StopOnBackgroundTest {
    private class FakeLifecycle : Lifecycle(), LifecycleOwner {
        override val lifecycle: Lifecycle get() = this
        val observers = mutableListOf<LifecycleObserver>()
        override val currentState: State get() = State.RESUMED
        override fun addObserver(observer: LifecycleObserver) { observers += observer }
        override fun removeObserver(observer: LifecycleObserver) { observers -= observer }
        fun event(event: Lifecycle.Event) = observers.toList().forEach { (it as LifecycleEventObserver).onStateChanged(this, event) }
    }

    @Test
    fun stops_only_when_the_app_leaves_the_foreground() {
        val lifecycle = FakeLifecycle()
        var stops = 0
        lifecycle.observeStop { stops++ }
        for (event in listOf(Lifecycle.Event.ON_CREATE, Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME, Lifecycle.Event.ON_PAUSE)) lifecycle.event(event)
        assertEquals(0, stops)
        lifecycle.event(Lifecycle.Event.ON_STOP)
        assertEquals(1, stops)
        lifecycle.event(Lifecycle.Event.ON_START)
        lifecycle.event(Lifecycle.Event.ON_STOP)
        assertEquals(2, stops)
    }

    @Test
    fun detaching_stops_the_callback() {
        val lifecycle = FakeLifecycle()
        var stops = 0
        lifecycle.removeObserver(lifecycle.observeStop { stops++ })
        lifecycle.event(Lifecycle.Event.ON_STOP)
        assertEquals(0, stops)
    }
}
