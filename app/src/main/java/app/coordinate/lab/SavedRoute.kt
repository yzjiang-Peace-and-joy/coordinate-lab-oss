package app.coordinate.lab

import app.coordinate.lab.core.Geo
import app.coordinate.lab.core.GeoPoint
import app.coordinate.lab.core.RouteGeometry
import java.util.UUID

enum class SavedRouteKind { WAYPOINTS, DRAWN }

/** A library snapshot is independent from the editable route and any active simulation. */
data class SavedRoute(
    val id: String,
    val name: String,
    val kind: SavedRouteKind,
    val waypoints: List<Place>,
    val points: List<GeoPoint>,
    val road: Boolean = false,
    val closed: Boolean = false,
    val speedKph: Double = 5.0,
    val createdAt: Long = System.currentTimeMillis(),
    // False for an unplanned road draft: points are only its straight-line preview.
    val previewGeometryReady: Boolean = false
)

object RouteLibraryPolicy {
    const val MAX_ROUTES = 50
    const val MAX_MARKERS = 100
    const val MAX_NAME_LENGTH = 40
    const val MAX_TOTAL_POINTS = 100_000
    const val MAX_TRACE_POINTS = RouteGeometry.MAX_PATH_POINTS

    fun name(value: String): String {
        val normalized = value.trim()
        require(normalized.isNotBlank()) { "请输入名称" }
        require(normalized.length <= MAX_NAME_LENGTH) { "名称最多 $MAX_NAME_LENGTH 个字" }
        require(normalized.none { it.isISOControl() }) { "名称不能包含换行或控制字符" }
        return normalized
    }

    fun validatePlace(place: Place): Place {
        require(place.name.isNotBlank() && place.name.length <= 200) { "地点名称不可用" }
        require(place.city.length <= 200 && place.detail.length <= 1000) { "地点信息过长" }
        return place
    }

    fun validate(route: SavedRoute): SavedRoute {
        require(runCatching { UUID.fromString(route.id).toString() == route.id }.getOrDefault(false)) { "路线编号不可用" }
        val normalizedName = name(route.name)
        require(route.createdAt >= 0) { "路线时间不可用" }
        require(route.speedKph.isFinite() && route.speedKph in 1.0..30.0) { "路线速度不可用" }
        require(route.points.size in 2..MAX_TRACE_POINTS) { "路线需要 2 至 $MAX_TRACE_POINTS 个轨迹点" }
        require(route.points.zipWithNext { a, b -> Geo.distance(a, b) }.sum() > 0.1) { "请绘制一条有效路线" }
        route.waypoints.forEach(::validatePlace)
        when (route.kind) {
            SavedRouteKind.WAYPOINTS -> {
                RouteGeometry.validateWaypoints(route.waypoints.map { it.point })
                require(route.points.first() == route.waypoints.first().point) { "路线起点不匹配" }
                val end = if (route.closed) route.waypoints.first().point else route.waypoints.last().point
                require(route.points.last() == end) { "路线终点不匹配" }
            }
            SavedRouteKind.DRAWN -> {
                // Sketch points are never converted into a 20-waypoint route or silently simplified.
                require(route.waypoints.isEmpty()) { "手绘路线不应包含选点草稿" }
                require(!route.road) { "手绘路线不会自动沿道路规划" }
                if (route.closed) require(route.points.first() == route.points.last()) { "闭环路线终点应连接起点" }
            }
        }
        return route.copy(name = normalizedName, waypoints = route.waypoints.toList(), points = route.points.toList())
    }

    fun add(routes: List<SavedRoute>, route: SavedRoute): List<SavedRoute> {
        val candidate = validate(route)
        require(routes.none { it.id == candidate.id }) { "路线编号已存在" }
        require(routes.size < MAX_ROUTES) { "最多收藏 $MAX_ROUTES 条路线，请先删除不需要的路线" }
        require(routes.sumOf { it.points.size } + candidate.points.size <= MAX_TOTAL_POINTS) { "路线收藏空间已满，请先删除不需要的路线" }
        return listOf(candidate) + routes
    }

    fun rename(routes: List<SavedRoute>, id: String, value: String): List<SavedRoute> {
        val normalizedName = name(value)
        require(routes.any { it.id == id }) { "路线不存在" }
        return routes.map { if (it.id == id) it.copy(name = normalizedName) else it }
    }

    fun delete(routes: List<SavedRoute>, id: String): List<SavedRoute> = routes.filterNot { it.id == id }

    fun drawn(name: String, points: List<GeoPoint>, now: Long = System.currentTimeMillis()): SavedRoute =
        validate(SavedRoute(UUID.randomUUID().toString(), name, SavedRouteKind.DRAWN, emptyList(), points,
            createdAt = now, previewGeometryReady = true))

    fun current(name: String, waypoints: List<Place>, points: List<GeoPoint>, road: Boolean,
                closed: Boolean, speedKph: Double, ready: Boolean, now: Long = System.currentTimeMillis()): SavedRoute {
        RouteGeometry.validateWaypoints(waypoints.map { it.point })
        val geometry = if (ready) points else RouteGeometry.straight(waypoints.map { it.point }, closed).points
        return validate(SavedRoute(UUID.randomUUID().toString(), name, SavedRouteKind.WAYPOINTS, waypoints,
            geometry, road, closed, speedKph, now, previewGeometryReady = ready || !road))
    }
}
