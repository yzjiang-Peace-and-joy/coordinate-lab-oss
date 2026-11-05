package app.coordinate.lab.core

/**
 * Worker-owned elapsed-realtime clock for a route. It has no Android dependency so pause/resume
 * accounting can be verified deterministically.
 */
class RouteTimeline(startElapsedRealtimeMs: Long) {
    private var lastElapsedRealtimeMs = startElapsedRealtimeMs
    private var accumulatedMotionMillis = 0L

    var paused: Boolean = false
        private set

    init {
        require(startElapsedRealtimeMs >= 0) { "elapsedRealtime 不能为负数" }
    }

    /** Advances motion only while active, then returns the state at [nowElapsedRealtimeMs]. */
    fun tick(nowElapsedRealtimeMs: Long): RouteTimelineSnapshot {
        require(nowElapsedRealtimeMs >= lastElapsedRealtimeMs) { "elapsedRealtime 不能倒退" }
        val delta = nowElapsedRealtimeMs - lastElapsedRealtimeMs
        if (!paused) accumulatedMotionMillis = saturatingAdd(accumulatedMotionMillis, delta)
        lastElapsedRealtimeMs = nowElapsedRealtimeMs
        return snapshot()
    }

    /** Accounts for time up to [nowElapsedRealtimeMs] before changing state, preventing a resume jump. */
    fun togglePause(nowElapsedRealtimeMs: Long): RouteTimelineSnapshot {
        tick(nowElapsedRealtimeMs)
        paused = !paused
        return snapshot()
    }

    private fun snapshot() = RouteTimelineSnapshot(paused, accumulatedMotionMillis)

    private fun saturatingAdd(left: Long, right: Long): Long =
        if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
}

data class RouteTimelineSnapshot(val paused: Boolean, val motionMillis: Long) {
    val motionSeconds: Double get() = motionMillis / 1_000.0
}
