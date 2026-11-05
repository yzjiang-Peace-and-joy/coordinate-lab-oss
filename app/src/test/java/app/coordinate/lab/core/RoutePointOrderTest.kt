package app.coordinate.lab.core
import org.junit.Assert.*
import org.junit.Test

class RoutePointOrderTest {
    @Test fun dragUsesSlotThresholdAndClampsAtEnds() {
        assertEquals(1,RoutePointOrder.target(1,20f,46f,4))
        assertEquals(2,RoutePointOrder.target(1,24f,46f,4))
        assertEquals(0,RoutePointOrder.target(1,-500f,46f,4))
        assertEquals(3,RoutePointOrder.target(1,500f,46f,4))
    }
    @Test fun movesBothDirectionsWithoutChangingInput() {
        val points=listOf("A","B","C","D")
        assertEquals(listOf("B","C","D","A"),RoutePointOrder.move(points,0,3))
        assertEquals(listOf("D","A","B","C"),RoutePointOrder.move(points,3,0))
        assertEquals(listOf("A","B","C","D"),points)
    }
    @Test fun repeatedLocationsRetainIndividualOccurrences() {
        assertEquals(listOf("A","A","B"),RoutePointOrder.move(listOf("A","B","A"),2,1))
    }
    @Test fun singlePointAndNoOpStayStable() {
        assertEquals(0,RoutePointOrder.target(0,200f,46f,1))
        assertEquals(listOf("A","B"),RoutePointOrder.move(listOf("A","B"),1,1))
    }
}
