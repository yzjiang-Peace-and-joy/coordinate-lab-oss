package app.coordinate.lab.core

enum class RoutePrimaryAction { CONFIRM, PLANNING, START, STOP }
data class RoutePrimaryButton(val action: RoutePrimaryAction,val enabled: Boolean)

/** Confirmation only prepares geometry; starting requires a separate click on the ready state. */
object RoutePrimaryButtons {
    fun resolve(active: Boolean,routeMode: Boolean,editing: Boolean,planning: Boolean,error: String?,waypointCount: Int,pathPointCount: Int): RoutePrimaryButton = when {
        active -> RoutePrimaryButton(RoutePrimaryAction.STOP,true)
        !routeMode -> RoutePrimaryButton(RoutePrimaryAction.START,true)
        planning -> RoutePrimaryButton(RoutePrimaryAction.PLANNING,false)
        RouteStartPolicy.canStart(editing,planning,error,waypointCount,pathPointCount) -> RoutePrimaryButton(RoutePrimaryAction.START,true)
        else -> RoutePrimaryButton(RoutePrimaryAction.CONFIRM,waypointCount in 2..RouteGeometry.MAX_WAYPOINTS)
    }
}
