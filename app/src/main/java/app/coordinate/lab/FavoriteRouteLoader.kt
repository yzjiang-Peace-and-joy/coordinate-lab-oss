package app.coordinate.lab

import app.coordinate.lab.core.GeoPoint
import app.coordinate.lab.core.RoutePlayer

/** Loading is selection only. No Android service or network request is started here. */
data class LoadedFavoriteRoute(val source: SavedRoute, val handles: List<Place>, val geometry: List<GeoPoint>) {
    val needsPlanning: Boolean get() = geometry.isEmpty()
}

object FavoriteRouteLoader {
    fun selectPlace(state: UiState, place: Place): UiState = state.copy(
        routeMode=false, routeEditing=false, route=emptyList(), planning=false,
        routeError=null, previewSavedRoute=null, selected=place,
        city=place.city.ifBlank {state.city}, hasPickedPoint=true, locationReady=true,
        resolvingPlace=false, searchResults=emptyList(), searchCompleted=false,
        searchDescription="", searchError=null, searchMoreError=null, suggestedCities=emptyList()
    )

    fun selectRoute(state: UiState, loaded: LoadedFavoriteRoute): UiState = state.copy(
        routeMode=true, routeEditing=false, routeWaypoints=loaded.handles,
        route=loaded.geometry, road=loaded.source.road, routeClosed=loaded.source.closed,
        speed=loaded.source.speedKph, loadedFavoriteRoute=loaded.source,
        previewSavedRoute=null, canUndoRoute=false, planning=false, routeError=null,
        hasPickedPoint=true, locationReady=true, selected=loaded.handles.first(),
        city=loaded.handles.first().city.ifBlank {state.city}, resolvingPlace=false,
        searchResults=emptyList(), searchCompleted=false, searchError=null,
        searchMoreError=null, searchDescription="", suggestedCities=emptyList()
    )

    fun load(saved: SavedRoute): LoadedFavoriteRoute {
        val route = RouteLibraryPolicy.validate(saved)
        // These two labels are display handles, not a simplification of a drawn trajectory.
        val handles = if (route.kind == SavedRouteKind.DRAWN) listOf(
            Place("起点", "", route.points.first()),
            Place(if (route.closed) "返回起点" else "终点", "", route.points.last())
        ) else route.waypoints
        val ready = route.kind == SavedRouteKind.DRAWN || route.previewGeometryReady || !route.road
        if (ready) RoutePlayer(route.points, route.speedKph)
        return LoadedFavoriteRoute(route, handles, if (ready) route.points else emptyList())
    }

    fun closeTrace(route: SavedRoute, closed: Boolean): SavedRoute {
        require(route.kind == SavedRouteKind.DRAWN)
        if (route.closed == closed) return route
        val points = if (closed) {
            if (route.points.last() == route.points.first()) route.points else route.points + route.points.first()
        } else if (route.points.last() == route.points.first()) route.points.dropLast(1) else route.points
        return RouteLibraryPolicy.validate(route.copy(points = points, closed = closed))
    }
}
