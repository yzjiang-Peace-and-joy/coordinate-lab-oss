package app.coordinate.lab.core

import org.junit.Assert.*
import org.junit.Test

class GeoTest {
    @Test fun rejectsInvalidCoordinates() {
        listOf({GeoPoint(91.0,0.0)},{GeoPoint(0.0,181.0)},{GeoPoint(Double.NaN,0.0)}).forEach { block ->
            try { block(); fail("Expected invalid coordinate") } catch (_: IllegalArgumentException) {}
        }
    }
    @Test fun gcjRoundTripForMainlandCities() {
        listOf(GeoPoint(39.9,116.4),GeoPoint(31.2,121.4),GeoPoint(30.2,120.1),GeoPoint(22.5,114.0),GeoPoint(30.6,104.0)).forEach {
            val mapped = Coordinates.toMap(it)
            assertTrue(Geo.distance(it,mapped)>50)
            assertTrue(Geo.distance(it,Coordinates.fromMap(mapped))<0.02)
        }
    }
    @Test fun overseasCoordinatesStayUnchanged() {
        val paris=GeoPoint(48.8566,2.3522)
        assertEquals(paris,Coordinates.toMap(paris)); assertEquals(paris,Coordinates.fromMap(paris))
    }
    @Test fun interpolationCrossesDateLineCorrectly() {
        val mid=Geo.interpolate(GeoPoint(0.0,179.0),GeoPoint(0.0,-179.0),0.5)
        assertTrue(kotlin.math.abs(mid.longitude)>179.99)
    }
    @Test fun advancesAtRequestedSpeedAndHoldsEnd() {
        val a=GeoPoint(30.0,120.0);val b=GeoPoint(30.01,120.0)
        val player=RoutePlayer(listOf(a,b),3.6)
        assertEquals(10.0,Geo.distance(a,player.at(10.0).point),0.01)
        val atEnd=player.at(player.durationSeconds)
        assertEquals(b,atEnd.point);assertTrue(atEnd.finished);assertEquals(0f,atEnd.speedMps,0f)
        val end=player.at(player.durationSeconds+10)
        assertEquals(b,end.point);assertTrue(end.finished);assertEquals(0f,end.speedMps,0f)
    }
    @Test fun duplicatePointsDoNotDivideByZero() {
        val a=GeoPoint(30.0,120.0);val b=GeoPoint(30.01,120.0)
        val player=RoutePlayer(listOf(a,a,b,b),5.0)
        assertEquals(2,player.points.size);assertFalse(player.at(0.0).finished)
    }
    @Test fun refusesStationaryRouteAndInvalidSpeed() {
        val p=GeoPoint(30.0,120.0)
        try { RoutePlayer(listOf(p,p),5.0);fail() } catch (_: IllegalArgumentException) {}
        try { RoutePlayer(listOf(p,GeoPoint(31.0,120.0)),0.0);fail() } catch (_: IllegalArgumentException) {}
    }
    @Test fun multiSegmentRouteVisitsWaypointThenUsesOutgoingLeg() {
        val start=GeoPoint(0.0,0.0);val turn=GeoPoint(0.0,0.001);val end=GeoPoint(0.001,0.001)
        val player=RoutePlayer(listOf(start,turn,end),3.6)
        val firstLegSeconds=Geo.distance(start,turn)
        val atTurn=player.at(firstLegSeconds)
        assertTrue(Geo.distance(turn,atTurn.point)<0.01)
        assertEquals(0f,atTurn.bearing,0.1f)
        assertFalse(atTurn.finished)
        val afterTurn=player.at(firstLegSeconds+10)
        assertTrue(Geo.distance(turn,afterTurn.point) in 9.99..10.01)
        assertTrue(afterTurn.point.latitude>turn.latitude)
        assertEquals(turn.longitude,afterTurn.point.longitude,1e-8)
    }
    @Test fun removesOnlyExactAdjacentDuplicatesAndKeepsReturnLeg() {
        val a=GeoPoint(30.0,120.0);val closeButDistinct=GeoPoint(30.000001,120.0);val b=GeoPoint(30.01,120.0)
        val closeRoute=RoutePlayer(listOf(a,closeButDistinct,b),5.0)
        assertEquals(listOf(a,closeButDistinct,b),closeRoute.points)

        val thereAndBack=RoutePlayer(listOf(a,a,b,b,a),5.0)
        assertEquals(listOf(a,b,a),thereAndBack.points)
        assertEquals(2*Geo.distance(a,b),thereAndBack.distanceMeters,0.01)
        assertTrue(Geo.distance(b,thereAndBack.at(Geo.distance(a,b)/(5.0/3.6)).point)<0.01)
    }
    @Test fun routeSnapshotCannotBeChangedThroughInputOrExposedList() {
        val a=GeoPoint(30.0,120.0);val b=GeoPoint(30.01,120.0);val replacement=GeoPoint(31.0,121.0)
        val source=mutableListOf(a,b)
        val player=RoutePlayer(source,5.0)
        source[0]=replacement
        assertEquals(a,player.points.first())
        try {
            @Suppress("UNCHECKED_CAST")
            (player.points as MutableList<GeoPoint>).add(replacement)
            fail("Route points must be read-only")
        } catch (_: UnsupportedOperationException) {}
        assertEquals(listOf(a,b),player.points)
    }
    @Test fun distanceAndBearingAreConsistent() {
        val a=GeoPoint(0.0,0.0);val b=GeoPoint(0.0,0.001)
        assertEquals(111.195,Geo.distance(a,b),0.01)
        assertEquals(90f,Geo.bearing(a,b),0.001f)
    }
}
