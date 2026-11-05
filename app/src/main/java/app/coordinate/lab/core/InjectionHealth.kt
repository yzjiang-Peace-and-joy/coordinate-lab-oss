package app.coordinate.lab.core

enum class InjectionStatus { STOPPED, STARTING, FRESH, STALE }

/** Use monotonic time so wall-clock changes cannot hide a stalled writer. */
data class InjectionHealth(
    val lastSuccessElapsedMs: Long? = null,
    val lastSuccessWallMs: Long = 0,
    val writes: Long = 0,
    val longestGapMs: Long = 0
) {
    fun success(elapsedMs: Long, wallMs: Long): InjectionHealth {
        require(elapsedMs >= 0 && (lastSuccessElapsedMs == null || elapsedMs >= lastSuccessElapsedMs))
        val gap = lastSuccessElapsedMs?.let { elapsedMs - it } ?: 0
        return InjectionHealth(elapsedMs, wallMs, writes + 1, maxOf(longestGapMs, gap))
    }
    fun ageMs(nowElapsedMs: Long): Long? = lastSuccessElapsedMs?.let { (nowElapsedMs - it).coerceAtLeast(0) }
    fun status(active: Boolean, nowElapsedMs: Long): InjectionStatus = when {
        !active -> InjectionStatus.STOPPED
        lastSuccessElapsedMs == null -> InjectionStatus.STARTING
        ageMs(nowElapsedMs)!! > STALE_AFTER_MS -> InjectionStatus.STALE
        else -> InjectionStatus.FRESH
    }
    companion object { const val STALE_AFTER_MS = 5_000L }
}
