package app.signsetu.camera

import android.content.Context

/** Release builds: the bundled landmark models (SplitTracker.kt). */
object LandmarkTrackers {
    fun create(context: Context): LandmarkTracker? = landmarkTracker(context)
}
