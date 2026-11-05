package app.coordinate.lab.core

import org.junit.Assert.*
import org.junit.Test

class ClosedRouteTest {
    private fun north(meters: Double)=GeoPoint(30.0+meters/111_195.0,120.0)
    private val a=north(0.0)
    private val b=north(100.0)
    private val c=GeoPoint(30.0,120.001)

    @Test fun openRouteIsNotAltered() {assertEquals(listOf(a,b,c),RouteGeometry.straight(listOf(a,b,c)).points)}
    @Test fun closingAddsReturnWithoutMutatingDraft() {
        val draft=mutableListOf(a,b,c)
        assertEquals(listOf(a,b,c,a),RouteGeometry.straight(draft,true).points)
        assertEquals(listOf(a,b,c),draft)
    }
    @Test fun closingLegCountsTowardDistanceAndTimeAndArrivesAtStart() {
        val open=RoutePlayer(listOf(a,b,c),3.6)
        val closed=RoutePlayer(RouteGeometry.straight(listOf(a,b,c),true).points,3.6)
        assertEquals(open.distanceMeters+Geo.distance(c,a),closed.distanceMeters,.001)
        assertEquals(closed.distanceMeters,closed.durationSeconds,.001)
        assertEquals(a,closed.at(closed.durationSeconds).point)
        assertTrue(closed.at(closed.durationSeconds).finished)
    }
    @Test fun alreadyClosedDoesNotAddDuplicateReturn() {assertEquals(listOf(a,b,a),RouteGeometry.straight(listOf(a,b,a),true).points)}
    @Test fun twentySelectedPointsCanCloseButTwentyOneCannotBeSelected() {
        val draft=(0 until 20).map {north(it*10.0)}
        assertEquals(21,RouteGeometry.planningWaypoints(draft,true).size)
        assertEquals(20,draft.size)
        expectFailure {RouteGeometry.planningWaypoints(draft+north(200.0),true)}
    }
    @Test fun walkingRequiresSuccessfulReturnLegAndPreservesRoadGeometry() {
        val first=listOf(a,north(50.0),b)
        val second=listOf(b,c)
        val back=listOf(c,a)
        expectFailure {RouteGeometry.walking(listOf(a,b,c),listOf(first,second),true)}
        assertEquals(listOf(a,north(50.0),b,c,a),RouteGeometry.walking(listOf(a,b,c),listOf(first,second,back),true).points)
    }
    @Test fun prefixIsValidatedWithoutRequiringReturnToBeComplete() {
        assertEquals(listOf(a,b),RouteGeometry.walkingPrefix(listOf(a,b,c),listOf(listOf(a,b)),true).points)
        expectFailure {RouteGeometry.walkingPrefix(listOf(a,b,c),emptyList(),true)}
        expectFailure {RouteGeometry.walkingPrefix(listOf(a,b),listOf(listOf(a,b),listOf(b,a),listOf(a,b)),true)}
    }
    @Test fun tinySyntheticReturnPreservesBothExactPoints() {
        val near=north(.01)
        val result=RouteGeometry.walking(listOf(a,b,near),listOf(listOf(a,b),listOf(b,near),listOf(near,a)),true)
        assertEquals(listOf(a,b,near,a),result.points)
    }
    @Test fun emptyOrZeroLengthReturnStillFails() {
        expectFailure {RouteGeometry.walking(listOf(a,b),listOf(listOf(a,b),emptyList()),true)}
        expectFailure {RouteGeometry.walking(listOf(a,b),listOf(listOf(a,b),listOf(b,b)),true)}
    }
    @Test fun returnDoesNotBypassCumulativePointLimit() {
        val outward=(0 until 9999).map {north(it.toDouble())}
        val end=outward.last()
        expectFailure {RouteGeometry.walking(listOf(a,end),listOf(outward,listOf(end,north(123.5),a)),true)}
    }
    @Test fun closingTwentyPointWalkingRouteUsesTwentyLegs() {
        val draft=(0 until 20).map {north(it*10.0)}
        val planned=RouteGeometry.planningWaypoints(draft,true)
        val result=RouteGeometry.walking(draft,planned.zipWithNext {start,end->listOf(start,end)},true)
        assertEquals(planned,result.points)
    }
    private fun expectFailure(block: ()->Unit) {try {block();fail("Expected route rejection")} catch(_: IllegalArgumentException) {}}
}
