package app.coordinate.lab.core

import java.util.Collections
import kotlin.math.*

data class GeoPoint(val latitude: Double, val longitude: Double) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) { "纬度必须在 -90 至 90 之间" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "经度必须在 -180 至 180 之间" }
    }
}

object Geo {
    private const val R = 6_371_008.8
    fun distance(a: GeoPoint, b: GeoPoint): Double {
        val lat = Math.toRadians(b.latitude - a.latitude)
        val lon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(lat / 2).pow(2) + cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(lon / 2).pow(2)
        return R * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
    fun bearing(a: GeoPoint, b: GeoPoint): Float {
        val p = Math.toRadians(a.latitude)
        val q = Math.toRadians(b.latitude)
        val d = Math.toRadians(b.longitude - a.longitude)
        return ((Math.toDegrees(atan2(sin(d) * cos(q), cos(p) * sin(q) - sin(p) * cos(q) * cos(d))) + 360) % 360).toFloat()
    }
    fun interpolate(a: GeoPoint, b: GeoPoint, fraction: Double): GeoPoint {
        val f = fraction.coerceIn(0.0, 1.0)
        if (f == 0.0) return a
        if (f == 1.0) return b
        val lat1 = Math.toRadians(a.latitude); val lon1 = Math.toRadians(a.longitude)
        val lat2 = Math.toRadians(b.latitude); val lon2 = Math.toRadians(b.longitude)
        val angle = distance(a, b) / R
        if (angle < 1e-10) return a
        require(abs(sin(angle)) > 1e-9) { "不支持跨越对跖点的直线路线" }
        val u = sin((1 - f) * angle) / sin(angle)
        val v = sin(f * angle) / sin(angle)
        val x = u * cos(lat1) * cos(lon1) + v * cos(lat2) * cos(lon2)
        val y = u * cos(lat1) * sin(lon1) + v * cos(lat2) * sin(lon2)
        val z = u * sin(lat1) + v * sin(lat2)
        return GeoPoint(Math.toDegrees(atan2(z, hypot(x, y))), Math.toDegrees(atan2(y, x)))
    }
}

/** Explicit boundary: persistence and Android Location use WGS84; AMap mainland display uses GCJ-02.
 * The standard bounding-box applicability check is approximate, not a national boundary dataset.
 */
object Coordinates {
    private const val A = 6378245.0
    private const val EE = 0.006693421622965943
    private fun applicable(p: GeoPoint) = p.longitude in 72.004..137.8347 && p.latitude in 0.8293..55.8271
    fun toMap(p: GeoPoint): GeoPoint {
        if (!applicable(p)) return p
        val x = p.longitude - 105; val y = p.latitude - 35
        var dLat = -100 + 2*x + 3*y + 0.2*y*y + 0.1*x*y + 0.2*sqrt(abs(x))
        dLat += (20*sin(6*x*PI)+20*sin(2*x*PI))*2/3
        dLat += (20*sin(y*PI)+40*sin(y*PI/3))*2/3
        dLat += (160*sin(y*PI/12)+320*sin(y*PI/30))*2/3
        var dLon = 300 + x + 2*y + 0.1*x*x + 0.1*x*y + 0.1*sqrt(abs(x))
        dLon += (20*sin(6*x*PI)+20*sin(2*x*PI))*2/3
        dLon += (20*sin(x*PI)+40*sin(x*PI/3))*2/3
        dLon += (150*sin(x*PI/12)+300*sin(x*PI/30))*2/3
        val rad = Math.toRadians(p.latitude)
        val magic = 1 - EE*sin(rad).pow(2)
        val root = sqrt(magic)
        dLat = dLat*180 / ((A*(1-EE))/(magic*root)*PI)
        dLon = dLon*180 / (A/root*cos(rad)*PI)
        return GeoPoint(p.latitude+dLat, p.longitude+dLon)
    }
    fun fromMap(p: GeoPoint): GeoPoint {
        if (!applicable(p)) return p
        var result = p
        repeat(12) {
            val mapped = toMap(result)
            result = GeoPoint(result.latitude-(mapped.latitude-p.latitude), result.longitude-(mapped.longitude-p.longitude))
        }
        return result
    }
}

data class RouteSample(val point: GeoPoint, val speedMps: Float, val bearing: Float, val progress: Float, val finished: Boolean)

class RoutePlayer(points: List<GeoPoint>, val speedKph: Double) {
    /** A defensive, read-only route snapshot. Only exactly equal adjacent points are removed, so exact waypoints and a return leg remain intact. */
    val points: List<GeoPoint>
    private val lengths: DoubleArray
    /** Distance from the first point through the point at each matching index. */
    private val cumulativeLengths: DoubleArray
    val distanceMeters: Double
    val durationSeconds: Double

    init {
        require(speedKph.isFinite() && speedKph in 1.0..60.0) { "速度必须在 1 至 60 km/h 之间" }
        val cleaned = ArrayList<GeoPoint>(points.size)
        points.forEach { point ->
            if (cleaned.isEmpty() || cleaned.last() != point) cleaned.add(point)
        }
        this.points = Collections.unmodifiableList(cleaned.toList())
        require(this.points.size >= 2) { "请设置两个不同的地点" }
        lengths = DoubleArray(this.points.size - 1) { index -> Geo.distance(this.points[index], this.points[index + 1]) }
        cumulativeLengths = DoubleArray(this.points.size)
        for (index in lengths.indices) cumulativeLengths[index + 1] = cumulativeLengths[index] + lengths[index]
        distanceMeters = cumulativeLengths.last()
        require(distanceMeters > 0.1) { "请设置两个不同的地点" }
        durationSeconds = distanceMeters / (speedKph/3.6)
    }

    fun at(seconds: Double): RouteSample {
        require(seconds.isFinite() && seconds >= 0)
        // Compare time first so the mathematically exact arrival instant cannot miss because of floating-point multiplication.
        if (seconds >= durationSeconds) return RouteSample(points.last(),0f,0f,1f,true)
        val meters = (seconds * metersPerSecond).coerceIn(0.0, distanceMeters)
        val segment = segmentAt(meters)
        val distanceIntoSegment = meters - cumulativeLengths[segment]
        val fraction = (distanceIntoSegment / lengths[segment]).coerceIn(0.0, 1.0)
        return RouteSample(
            Geo.interpolate(points[segment], points[segment + 1], fraction),
            metersPerSecond.toFloat(),
            Geo.bearing(points[segment], points[segment + 1]),
            (meters / distanceMeters).toFloat(),
            false
        )
    }

    /** At a waypoint, select the following segment so bearing reflects the direction about to be travelled. */
    private fun segmentAt(meters: Double): Int {
        var low = 1
        var high = cumulativeLengths.lastIndex
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (cumulativeLengths[middle] <= meters) low = middle + 1 else high = middle - 1
        }
        return (low - 1).coerceIn(0, lengths.lastIndex)
    }

    private val metersPerSecond: Double get() = speedKph / 3.6

}
