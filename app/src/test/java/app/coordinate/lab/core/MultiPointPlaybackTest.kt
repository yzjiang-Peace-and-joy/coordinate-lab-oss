package app.coordinate.lab.core

import org.junit.Assert.*
import org.junit.Test

class MultiPointPlaybackTest {
    private val a=GeoPoint(30.0,120.0)
    private val b=GeoPoint(30.001,120.0)
    private val c=GeoPoint(30.001,120.001)
    @Test fun turnsAtWaypointAndTravelsNextLegAtSameSpeed() {
        val player=RoutePlayer(listOf(a,b,c),3.6)
        val firstLeg=Geo.distance(a,b)
        assertTrue(Geo.distance(b,player.at(firstLeg).point)<0.001)
        val afterTurn=player.at(firstLeg+10)
        assertEquals(10.0,Geo.distance(b,afterTurn.point),0.01)
        assertEquals(90f,afterTurn.bearing,0.01f)
        assertEquals(1f,afterTurn.speedMps,0.0001f)
    }
    @Test fun returnTripDoesNotSkipRepeatedNonAdjacentWaypoint() {
        val player=RoutePlayer(listOf(a,b,a),3.6)
        val leg=Geo.distance(a,b)
        assertEquals(listOf(a,b,a),player.points)
        assertTrue(Geo.distance(b,player.at(leg).point)<0.001)
        assertEquals(10.0,Geo.distance(b,player.at(leg+10).point),0.01)
        assertEquals(a,player.at(player.durationSeconds).point)
    }
    @Test fun exactArrivalAndHoldingAreStationary() {
        val player=RoutePlayer(listOf(a,b,c),7.0)
        listOf(player.durationSeconds,player.durationSeconds+100).forEach {time->
            val sample=player.at(time)
            assertEquals(c,sample.point);assertTrue(sample.finished)
            assertEquals(1f,sample.progress,0f);assertEquals(0f,sample.speedMps,0f)
        }
    }
    @Test fun pauseAndResumeHoldSamePointWithoutSkippingTime() {
        val timeline=RouteTimeline(0)
        val player=RoutePlayer(listOf(a,b,c),3.6)
        val before=player.at(timeline.togglePause(10_000).motionSeconds)
        assertEquals(before.point,player.at(timeline.tick(50_000).motionSeconds).point)
        assertEquals(before.point,player.at(timeline.togglePause(50_000).motionSeconds).point)
        assertEquals(11.0,Geo.distance(a,player.at(timeline.tick(51_000).motionSeconds).point),0.01)
    }
    @Test fun routeSnapshotIsIndependentOfMutableDraft() {
        val draft=mutableListOf(a,b,c)
        val player=RoutePlayer(draft,5.0)
        draft.clear()
        assertEquals(listOf(a,b,c),player.points)
        try {(player.points as MutableList<GeoPoint>).clear();fail()}catch(_: UnsupportedOperationException){}
    }
}
