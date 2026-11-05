package app.coordinate.lab

import org.junit.Assert.*
import org.junit.Test

class MapUiControllerTest {
    @Test fun loadedStyleAndTrafficArePreservedWithoutRewritingPreferences() {
        var writes=0
        val controller=MapUiController(MapStyle.TRANSIT,true,{writes++},{writes++})
        assertEquals(MapStyle.TRANSIT,controller.style)
        assertTrue(controller.traffic)
        assertEquals(0,writes)
    }
    @Test fun changingBaseMapDoesNotTurnOffTraffic() {
        var saved=MapStyle.NORMAL
        val controller=MapUiController(initialTraffic=true,saveStyle={saved=it})
        controller.selectStyle(MapStyle.NIGHT)
        assertEquals(MapStyle.NIGHT,controller.style)
        assertEquals(MapStyle.NIGHT,saved)
        assertTrue(controller.traffic)
    }
    @Test fun changingTrafficDoesNotChangeBaseMapAndPersistsChoice() {
        var saved=true
        val controller=MapUiController(MapStyle.SATELLITE,true,saveTraffic={saved=it})
        controller.selectTraffic(false)
        assertFalse(controller.traffic)
        assertFalse(saved)
        assertEquals(MapStyle.SATELLITE,controller.style)
    }
}
