package app.signsetu.ui.components

/** A resource built the first time it is needed. [take] must not build one: release() runs on every screen exit, and
 * constructing a TextToSpeech engine only to shut it down delays the next screen. */
internal class LazyResource<T>(private val build: () -> T) {
    private var value: T? = null

    fun peek(): T? = value

    fun get(): T = value ?: build().also { value = it }

    /** Hands the resource to the caller and forgets it, or null when it was never built. */
    fun take(): T? = value.also { value = null }
}
