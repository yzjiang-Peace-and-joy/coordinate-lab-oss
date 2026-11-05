package app.coordinate.lab.core

/** The final start gate is shared by the UI and permission-return path, not just button styling. */
object RouteStartPolicy {
    fun canStart(editing: Boolean, planning: Boolean, error: String?, waypointCount: Int, pathPointCount: Int): Boolean =
        !editing && !planning && error == null && waypointCount in 2..RouteGeometry.MAX_WAYPOINTS &&
            pathPointCount in 2..RouteGeometry.MAX_PATH_POINTS
}
