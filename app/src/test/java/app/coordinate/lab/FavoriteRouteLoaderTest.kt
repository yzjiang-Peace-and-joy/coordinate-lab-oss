package app.coordinate.lab

import app.coordinate.lab.core.GeoPoint
import app.coordinate.lab.core.Geo
import app.coordinate.lab.core.RoutePlayer
import app.coordinate.lab.core.RouteStartPolicy
import org.junit.Assert.*
import org.junit.Test

class FavoriteRouteLoaderTest {
    private val a=GeoPoint(30.0,120.0)
    private val b=GeoPoint(30.001,120.001)
    private val c=GeoPoint(30.002,120.0)
    private val places=listOf(Place("公园", "杭州", a),Place("河边", "杭州", c))
    private fun road(ready: Boolean)=RouteLibraryPolicy.current("晨跑",places,listOf(a,b,c),true,false,8.0,ready)
    private fun startable(s: UiState)=RouteStartPolicy.canStart(s.routeEditing,s.planning,s.routeError,s.routeWaypoints.size,s.route.size)

    @Test fun placeSelectionSwitchesOutOfRouteWithoutAppendingOrDeletingDraft() {
        val route=road(true)
        val old=UiState(routeMode=true,routeEditing=true,routeWaypoints=places,route=route.points,
            savedRoutes=listOf(route),previewSavedRoute=route,routeError="旧错误",planning=true)
        val chosen=Place("车站","上海",b)
        val next=FavoriteRouteLoader.selectPlace(old,chosen)
        assertFalse(next.routeMode);assertFalse(next.routeEditing)
        assertEquals(chosen,next.selected);assertEquals("上海",next.city)
        assertTrue(next.locationReady);assertTrue(next.hasPickedPoint)
        assertEquals(places,next.routeWaypoints);assertEquals(old.savedRoutes,next.savedRoutes)
        assertNull(next.previewSavedRoute);assertTrue(next.route.isEmpty());assertFalse(next.planning);assertNull(next.routeError)
    }

    @Test fun readyRoadLoadsExactPathAndBecomesStartableWithoutPlanning() {
        val saved=road(true)
        val loaded=FavoriteRouteLoader.load(saved)
        val state=FavoriteRouteLoader.selectRoute(UiState(routeEditing=true,routeError="旧失败",planning=true),loaded)
        assertTrue(state.routeMode);assertFalse(state.routeEditing);assertTrue(startable(state))
        assertEquals(listOf(a,b,c),state.route);assertEquals(places,state.routeWaypoints)
        assertEquals("晨跑",state.loadedFavoriteRoute?.name);assertEquals(8.0,state.speed,0.0)
        assertTrue(state.road);assertFalse(loaded.needsPlanning);assertNull(state.previewSavedRoute)
    }

    @Test fun unplannedRoadNeverUsesItsStraightPreviewAsRunnableGeometry() {
        val loaded=FavoriteRouteLoader.load(road(false))
        val state=FavoriteRouteLoader.selectRoute(UiState(),loaded)
        assertTrue(loaded.needsPlanning);assertTrue(state.route.isEmpty());assertFalse(startable(state))
        assertTrue(state.road);assertFalse(state.routeEditing)
    }

    @Test fun straightClosedRouteRestoresClosureAndSpeed() {
        val saved=RouteLibraryPolicy.current("环线",places,emptyList(),false,true,12.0,false)
        val state=FavoriteRouteLoader.selectRoute(UiState(),FavoriteRouteLoader.load(saved))
        assertTrue(startable(state));assertTrue(state.routeClosed);assertFalse(state.road)
        assertEquals(listOf(a,c,a),state.route);assertEquals(12.0,state.speed,0.0)
    }

    @Test fun tenThousandDrawnPointsStayExactAndArePlayable() {
        val trace=List(10_000){GeoPoint(30.0+it*0.0000001,120.0)}
        val saved=RouteLibraryPolicy.drawn("手绘长线",trace)
        val state=FavoriteRouteLoader.selectRoute(UiState(),FavoriteRouteLoader.load(saved))
        assertEquals(trace,state.route);assertEquals(2,state.routeWaypoints.size)
        assertTrue(state.loadedFavoriteRoute!!.waypoints.isEmpty());assertTrue(startable(state))
        assertTrue(RoutePlayer(state.route,state.speed).durationSeconds>0)
    }

    @Test fun loadedTraceMovementPassesThroughInteriorBendAtSavedSpeed() {
        val saved=RouteLibraryPolicy.drawn("折线",listOf(a,b,c)).copy(speedKph=9.0)
        val state=FavoriteRouteLoader.selectRoute(UiState(),FavoriteRouteLoader.load(saved))
        val player=RoutePlayer(state.route,state.speed)
        val secondsToBend=Geo.distance(a,b)/(state.speed/3.6)
        val bend=player.at(secondsToBend).point
        assertEquals(b.latitude,bend.latitude,0.00000001)
        assertEquals(b.longitude,bend.longitude,0.00000001)
        assertEquals(c,player.at(player.durationSeconds).point)
    }

    @Test fun closingFullTraceRejectsOverflowWithoutLosingAnySavedPoints() {
        val saved=RouteLibraryPolicy.drawn("满轨迹",List(10_000){GeoPoint(30.0+it*0.0000001,120.0)})
        assertTrue(runCatching {FavoriteRouteLoader.closeTrace(saved,true)}.isFailure)
        assertEquals(10_000,saved.points.size);assertFalse(saved.closed)
    }

    @Test fun closedDrawnRouteWithCoincidentEndpointsDoesNotFailWaypointValidation() {
        val saved=RouteLibraryPolicy.drawn("手绘环线",listOf(a,b,c,a)).copy(closed=true)
        val state=FavoriteRouteLoader.selectRoute(UiState(),FavoriteRouteLoader.load(saved))
        assertTrue(startable(state));assertEquals(saved.points,state.route)
        assertEquals(a,state.routeWaypoints.first().point);assertEquals(a,state.routeWaypoints.last().point)
    }

    @Test fun reverseAndCloseDoNotSimplifyTheTraceOrMutateLibrary() {
        val saved=RouteLibraryPolicy.drawn("轨迹",listOf(a,b,b,c))
        val closed=FavoriteRouteLoader.closeTrace(saved,true)
        assertEquals(listOf(a,b,b,c,a),closed.points);assertTrue(closed.closed)
        assertEquals(listOf(a,b,b,c),FavoriteRouteLoader.closeTrace(closed,false).points)
        assertEquals(listOf(c,b,b,a),FavoriteRouteLoader.load(saved.copy(points=saved.points.reversed())).geometry)
        assertEquals(listOf(a,b,b,c),saved.points);assertFalse(saved.closed)
    }

    @Test fun rejectsBrokenSnapshotBeforeReplacingSelection() {
        assertTrue(runCatching {FavoriteRouteLoader.load(road(true).copy(points=listOf(b,c)))}.isFailure)
        assertTrue(runCatching {FavoriteRouteLoader.load(road(true).copy(speedKph=Double.NaN))}.isFailure)
    }

    @Test fun selectingDifferentFavoriteReplacesRatherThanJoinsRoutes() {
        val first=FavoriteRouteLoader.selectRoute(UiState(),FavoriteRouteLoader.load(road(true)))
        val second=RouteLibraryPolicy.drawn("第二条",listOf(c,b,a))
        val next=FavoriteRouteLoader.selectRoute(first,FavoriteRouteLoader.load(second))
        assertEquals(second.points,next.route);assertEquals(second.id,next.loadedFavoriteRoute?.id)
        assertEquals(2,next.routeWaypoints.size);assertFalse(next.canUndoRoute)
    }
}
