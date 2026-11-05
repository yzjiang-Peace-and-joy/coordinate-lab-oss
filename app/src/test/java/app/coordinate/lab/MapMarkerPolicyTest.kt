package app.coordinate.lab

import org.junit.Assert.*
import org.junit.Test

class MapMarkerPolicyTest {
    @Test fun noPlaceholderPinBeforeFix() { assertNull(MapMarkerPolicy.single(false,false));assertNull(MapMarkerPolicy.single(false,true)) }
    @Test fun realAndPickedRemainDistinct() {
        assertEquals(MapMarkerRole.REAL,MapMarkerPolicy.single(true,false))
        assertEquals(MapMarkerRole.SELECTED,MapMarkerPolicy.single(true,true))
    }
    @Test fun routeEndsAndIntermediateHaveDistinctRoles() {
        assertEquals(MapMarkerRole.START,MapMarkerPolicy.route(0,19,false))
        assertEquals(MapMarkerRole.VIA,MapMarkerPolicy.route(1,19,false))
        assertEquals(MapMarkerRole.END,MapMarkerPolicy.route(19,19,false))
    }
    @Test fun loopDoesNotMislabelLastWaypointAsFinalDestination() {
        assertEquals(MapMarkerRole.LOOP,MapMarkerPolicy.route(0,19,true))
        assertEquals(MapMarkerRole.VIA,MapMarkerPolicy.route(19,19,true))
    }
    @Test fun firstDraftPointIsAStart() { assertEquals(MapMarkerRole.START,MapMarkerPolicy.route(0,0,false)) }
    @Test(expected=IllegalArgumentException::class) fun invalidIndexRejected() { MapMarkerPolicy.route(20,19,false) }
}
