package app.coordinate.lab.core

import kotlin.math.hypot

/** A single screen gesture, stored in WGS84. Rejected strokes are never partially committed. */
class DrawingStrokeSampler(
    private val minimumScreenDistance: Float,
    private val maximumPoints: Int = MAX_STROKE_POINTS
) {
    companion object {
        const val MAX_STROKE_POINTS = 2_000
        const val MAX_DRAWING_POINTS = 10_000
        const val MINIMUM_LENGTH_METERS = 1.0
    }

    private val accepted = mutableListOf<GeoPoint>()
    private var lastX = 0f
    private var lastY = 0f

    init {
        require(minimumScreenDistance.isFinite() && minimumScreenDistance > 0f)
        require(maximumPoints in 2..MAX_DRAWING_POINTS)
    }

    val points: List<GeoPoint> get() = accepted.toList()

    /** Sampling uses either screen motion or ground distance; [force] preserves the exact UP endpoint. */
    fun append(x: Float, y: Float, point: GeoPoint, force: Boolean = false): Boolean {
        require(x.isFinite() && y.isFinite()) { "绘制坐标不可用，请重新描绘" }
        val previous = accepted.lastOrNull()
        if (previous == point) return false
        if (previous != null && !force && hypot((x - lastX).toDouble(), (y - lastY).toDouble()) < minimumScreenDistance &&
            Geo.distance(previous, point) < MINIMUM_LENGTH_METERS) return false
        require(accepted.size < maximumPoints) { "单次描绘超过 $maximumPoints 个轨迹点，请分段描绘" }
        accepted.add(point)
        lastX = x
        lastY = y
        return true
    }

    /** A tap or tiny accidental stroke is not a route. This returns a defensive snapshot. */
    fun complete(): List<GeoPoint>? = accepted.takeIf { path ->
        path.size >= 2 && path.zipWithNext { a, b -> Geo.distance(a, b) }.sum() >= MINIMUM_LENGTH_METERS
    }?.toList()

    /** Join intentionally separate gestures without globally deduplicating return/closed paths. */
    fun appendTo(existing: List<GeoPoint>): List<GeoPoint> {
        val stroke = complete() ?: return existing.toList()
        val joined = existing.toMutableList()
        stroke.forEach { point ->
            if (joined.lastOrNull() != point) {
                require(joined.size < MAX_DRAWING_POINTS) { "描绘路线超过 $MAX_DRAWING_POINTS 个轨迹点，请缩短路线" }
                joined.add(point)
            }
        }
        return joined.toList()
    }
}
