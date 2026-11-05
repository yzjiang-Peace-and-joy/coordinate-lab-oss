package app.coordinate.lab.core

import org.junit.Assert.*
import org.junit.Test

class RouteGeometryTest {
    private fun north(meters: Double) = GeoPoint(30.0 + meters / 111_195.0, 120.0)

    @Test fun straightPreservesEveryWaypointAndDefensivelyCopiesInput() {
        val points = mutableListOf(north(0.0), north(50.0), north(100.0))
        val result = RouteGeometry.straight(points)
        assertEquals(points, result.points)
        assertEquals(0, result.connectorCount)
        points.removeAt(1)
        assertEquals(3, result.points.size)
    }

    @Test fun assemblesMultipleLegsAndRemovesAdjacentDuplicates() {
        val a = north(0.0); val b = north(100.0); val c = north(200.0)
        val mid1 = north(50.0); val mid2 = north(150.0)
        val result = RouteGeometry.walking(listOf(a, b, c), listOf(listOf(a, a, mid1, b, b), listOf(b, mid2, mid2, c)))
        assertEquals(listOf(a, mid1, b, mid2, c), result.points)
        assertEquals(0, result.connectorCount)
    }

    @Test fun returnTripPreservesOrderAndDoesNotGloballyDeduplicate() {
        val a = north(0.0); val b = north(100.0); val middle = north(50.0)
        val result = RouteGeometry.walking(listOf(a, b, a), listOf(listOf(a, middle, b), listOf(b, middle, a)))
        assertEquals(listOf(a, middle, b, middle, a), result.points)
    }

    @Test fun explicitlyConnectsRoadEndpointsAndCountsEachNoticeableGap() {
        val a = north(0.0); val b = north(200.0); val c = north(400.0)
        val result = RouteGeometry.walking(listOf(a, b, c), listOf(listOf(north(10.0), north(190.0)), listOf(north(220.0), north(390.0))))
        assertEquals(listOf(a, north(10.0), north(190.0), b, north(220.0), north(390.0), c), result.points)
        assertEquals(4, result.connectorCount)
        assertEquals(b, result.points[3])
    }

    @Test fun preservesExactWaypointEvenForSubHalfMeterEndpointDifference() {
        val a = north(0.0); val b = north(100.0)
        val result = RouteGeometry.walking(listOf(a, b), listOf(listOf(north(0.1), north(99.9))))
        assertEquals(a, result.points.first())
        assertEquals(b, result.points.last())
        assertEquals(0, result.connectorCount)
    }

    @Test fun connectsRoadEndpointsOver100MetersWithoutMovingSelectedPoints() {
        val a = north(0.0); val b = north(500.0)
        val startGap=RouteGeometry.walking(listOf(a,b),listOf(listOf(north(110.0),b)))
        assertEquals(listOf(a,north(110.0),b),startGap.points)
        assertEquals(1,startGap.connectorCount)
        val endGap=RouteGeometry.walking(listOf(a,b),listOf(listOf(a,north(390.0))))
        assertEquals(listOf(a,north(390.0),b),endGap.points)
        assertEquals(1,endGap.connectorCount)
        assertEquals(2, RouteGeometry.walking(listOf(a, b), listOf(listOf(north(80.0), north(420.0)))).connectorCount)
    }

    @Test fun longConnectorsCountTowardDistanceAndTravelTime() {
        val a=north(0.0); val b=north(1000.0)
        val result=RouteGeometry.walking(listOf(a,b),listOf(listOf(north(200.0),north(800.0))))
        assertEquals(2,result.connectorCount)
        val player=RoutePlayer(result.points,3.6)
        assertEquals(Geo.distance(a,b),player.distanceMeters,.01)
        assertEquals(player.distanceMeters,player.durationSeconds,.01)
        assertEquals(a,player.points.first())
        assertEquals(b,player.points.last())
    }

    @Test fun closeSelectedPointsCanHaveRoadEndpointsFarAway() {
        val a=north(0.0); val b=north(20.0)
        val result=RouteGeometry.walking(listOf(a,b),listOf(listOf(north(200.0),north(220.0))))
        assertEquals(listOf(a,north(200.0),north(220.0),b),result.points)
        assertEquals(2,result.connectorCount)
    }

    @Test fun rejectsMissingLegsEmptyLegsAndZeroLengthRoadGeometry() {
        val a = north(0.0); val b = north(10.0); val c = north(20.0)
        expectFailure { RouteGeometry.walking(listOf(a, b, c), listOf(listOf(a, b))) }
        expectFailure { RouteGeometry.walking(listOf(a, b), listOf(emptyList())) }
        expectFailure { RouteGeometry.walking(listOf(a, b), listOf(listOf(a, a, a))) }
    }

    @Test fun rejectsTooFewTooManyAndAdjacentCoincidentWaypoints() {
        expectFailure { RouteGeometry.validateWaypoints(emptyList()) }
        expectFailure { RouteGeometry.straight(listOf(north(0.0))) }
        expectFailure { RouteGeometry.straight((0..20).map { north(it * 10.0) }) }
        expectFailure { RouteGeometry.straight(listOf(north(0.0), north(0.0))) }
        expectFailure { RouteGeometry.straight(listOf(north(0.0), north(0.5))) }
        assertEquals(20, RouteGeometry.straight((0..19).map { north(it * 10.0) }).points.size)
    }

    @Test fun pointLimitIsExactAndNeverSilentlyTruncates() {
        val allowed = (0 until RouteGeometry.MAX_PATH_POINTS).map { north(it.toDouble()) }
        assertEquals(RouteGeometry.MAX_PATH_POINTS, RouteGeometry.walking(listOf(allowed.first(), allowed.last()), listOf(allowed)).points.size)
        val tooLong = allowed + north(RouteGeometry.MAX_PATH_POINTS.toDouble())
        expectFailure { RouteGeometry.walking(listOf(tooLong.first(), tooLong.last()), listOf(tooLong)) }
    }

    @Test fun pointLimitAppliesToCombinedLegsAndInsertedWaypoints() {
        val first = (0..5000).map { north(it.toDouble()) }
        val second = (5000..10000).map { north(it.toDouble()) }
        expectFailure { RouteGeometry.walking(listOf(first.first(), first.last(), second.last()), listOf(first, second)) }
        val road = (0 until RouteGeometry.MAX_PATH_POINTS).map { north(it.toDouble()) }
        expectFailure { RouteGeometry.walking(listOf(north(-10.0), road.last()), listOf(road)) }
    }

    @Test fun cancellingSharedCompletionGateRejectsLateRouteDelivery() {
        val gate = SearchCompletionGate()
        gate.cancel()
        var deliveries = 0
        if (gate.tryFinish()) deliveries++
        if (gate.tryFinish()) deliveries++
        assertEquals(0, deliveries)
        assertFalse(gate.isOpen)
    }

    private fun expectFailure(block: () -> Unit) {
        try { block(); fail("Expected invalid route") } catch (_: IllegalArgumentException) { }
    }
}
