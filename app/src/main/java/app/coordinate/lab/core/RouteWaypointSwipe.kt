package app.coordinate.lab.core

/** A deliberate left swipe, not a short nudge or a right swipe, removes a row on release. */
object RouteWaypointSwipe {
    fun shouldRemove(displacementPixels: Float, widthPixels: Float, maxThresholdPixels: Float): Boolean {
        if (!displacementPixels.isFinite() || !widthPixels.isFinite() || widthPixels <= 0f ||
            !maxThresholdPixels.isFinite() || maxThresholdPixels <= 0f) return false
        return -displacementPixels >= minOf(widthPixels * .32f, maxThresholdPixels)
    }
}
