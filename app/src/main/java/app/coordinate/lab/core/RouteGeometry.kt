package app.coordinate.lab.core

data class AssembledRoute(val points: List<GeoPoint>, val connectorCount: Int = 0)

/** Route assembly operates only on WGS84 points, preserving the user's explicit waypoint order. */
object RouteGeometry {
    const val MAX_WAYPOINTS = 20
    const val MAX_PATH_POINTS = 10_000
    const val MIN_WAYPOINT_GAP_METERS = 1.0
    private const val CONNECTOR_NOTICE_METERS = 0.5

    fun validateWaypoints(waypoints: List<GeoPoint>) {
        require(waypoints.size in 2..MAX_WAYPOINTS) { "路线需要 2 至 $MAX_WAYPOINTS 个地点（包含起终点）" }
        waypoints.zipWithNext().forEachIndexed { index, (start, end) ->
            require(Geo.distance(start, end) >= MIN_WAYPOINT_GAP_METERS) { "第 ${index + 1} 段的两个地点过近，请至少间隔 1 米" }
        }
    }

    /** The synthetic return point does not consume the user's 20 selectable waypoint slots. */
    fun planningWaypoints(waypoints: List<GeoPoint>,closed: Boolean=false): List<GeoPoint> {
        validateWaypoints(waypoints)
        return if(closed && waypoints.last()!=waypoints.first())waypoints+waypoints.first() else waypoints.toList()
    }

    fun straight(waypoints: List<GeoPoint>,closed: Boolean=false): AssembledRoute = AssembledRoute(planningWaypoints(waypoints,closed))

    fun walking(waypoints: List<GeoPoint>,legs: List<List<GeoPoint>>,closed: Boolean=false): AssembledRoute {
        val planned=planningWaypoints(waypoints,closed)
        require(legs.size==planned.size-1) { "步行路线段数不完整，整条路线未更新" }
        return assembleWalking(planned,legs,closed)
    }

    /** Validate every received leg without ever publishing an incomplete route as ready. */
    fun walkingPrefix(waypoints: List<GeoPoint>,legs: List<List<GeoPoint>>,closed: Boolean=false): AssembledRoute {
        val planned=planningWaypoints(waypoints,closed)
        require(legs.size in 1 until planned.size) { "步行路线段数不可用，整条路线未更新" }
        return assembleWalking(planned,legs,closed)
    }

    private fun assembleWalking(waypoints: List<GeoPoint>,legs: List<List<GeoPoint>>,closed: Boolean): AssembledRoute {
        val assembled = mutableListOf<GeoPoint>()
        var connectors = 0
        fun append(point: GeoPoint) {
            // Exact duplicates are removed; near-but-different user waypoints are never rounded away.
            if (assembled.lastOrNull() == point) return
            require(assembled.size < MAX_PATH_POINTS) { "路线超过 $MAX_PATH_POINTS 个轨迹点，请减少地点或缩短路线" }
            assembled.add(point)
        }
        for ((index, raw) in legs.withIndex()) {
            val leg = raw.fold(mutableListOf<GeoPoint>()) { points, point ->
                if (points.lastOrNull() != point) points.add(point)
                points
            }
            val start = waypoints[index]
            val end = waypoints[index + 1]
            val shortReturn=closed && index==waypoints.lastIndex-1 && Geo.distance(start,end)<MIN_WAYPOINT_GAP_METERS
            require(leg.size >= 2 && leg.zipWithNext { a, b -> Geo.distance(a, b) }.sum() > (if(shortReturn)0.0 else 0.05)) { "第 ${index + 1} 段没有有效步行轨迹，整条路线未更新" }
            val startGap = Geo.distance(start, leg.first())
            val endGap = Geo.distance(leg.last(), end)
            // Keep exact selected points. Off-road endpoints are explicit straight connectors,
            // not a reason to reject otherwise valid road geometry or silently move a waypoint.
            if (startGap > CONNECTOR_NOTICE_METERS) connectors++
            if (endGap > CONNECTOR_NOTICE_METERS) connectors++
            append(start)
            leg.forEach(::append)
            append(end)
        }
        return AssembledRoute(assembled.toList(), connectors)
    }
}
