package app.coordinate.lab

import org.junit.Assert.*
import org.junit.Test

class MapThemePolicyTest {
    @Test fun lightLaunchUsesExactManualPreference() {
        MapStyle.entries.forEach {style->
            val policy=MapThemePolicy(style)
            assertEquals(style,policy.style)
            assertEquals(style,policy.preferredStyle)
            assertFalse(policy.dark)
        }
    }

    @Test fun darkLaunchTemporarilyUsesNightWithoutOverwritingPreference() {
        MapStyle.entries.forEach {style->
            val policy=MapThemePolicy(style,true)
            assertEquals(MapStyle.NIGHT,policy.style)
            assertEquals(style,policy.preferredStyle)
            assertTrue(policy.applyTheme(false))
            assertEquals(style,policy.style)
        }
    }

    @Test fun themeRoundTripRestoresManualSatelliteAndManualNight() {
        listOf(MapStyle.SATELLITE,MapStyle.NIGHT).forEach {style->
            val policy=MapThemePolicy(style)
            assertTrue(policy.applyTheme(true))
            assertEquals(MapStyle.NIGHT,policy.style)
            assertTrue(policy.applyTheme(false))
            assertEquals(style,policy.style)
        }
    }

    @Test fun manualChoiceWhileDarkAppliesImmediatelyAndSurvivesSameThemeUpdates() {
        val policy=MapThemePolicy(MapStyle.NORMAL,true)
        policy.selectStyle(MapStyle.TRANSIT)
        assertEquals(MapStyle.TRANSIT,policy.style)
        repeat(3) {
            assertFalse(policy.applyTheme(true))
            assertEquals(MapStyle.TRANSIT,policy.style)
        }
        assertTrue(policy.applyTheme(false))
        assertEquals(MapStyle.TRANSIT,policy.style)
        assertTrue(policy.applyTheme(true))
        assertEquals(MapStyle.NIGHT,policy.style)
        assertEquals(MapStyle.TRANSIT,policy.preferredStyle)
    }

    @Test fun manualOverrideDoesNotSurviveFreshDarkSession() {
        val previous=MapThemePolicy(MapStyle.NORMAL,true)
        previous.selectStyle(MapStyle.NAVIGATION)
        val reopened=MapThemePolicy(previous.preferredStyle,true)
        assertEquals(MapStyle.NIGHT,reopened.style)
        reopened.applyTheme(false)
        assertEquals(MapStyle.NAVIGATION,reopened.style)
    }

    @Test fun controllerThemeChangesNeverPersistStyleOrTraffic() {
        val savedStyles=mutableListOf<MapStyle>()
        val savedTraffic=mutableListOf<Boolean>()
        val controller=MapUiController(MapStyle.SATELLITE,true,savedStyles::add,savedTraffic::add)
        controller.applyTheme(true)
        assertEquals(MapStyle.NIGHT,controller.style)
        controller.applyTheme(false)
        assertEquals(MapStyle.SATELLITE,controller.style)
        assertTrue(controller.traffic)
        assertTrue(savedStyles.isEmpty())
        assertTrue(savedTraffic.isEmpty())
    }

    @Test fun controllerManualDarkChoicePersistsOnlyTheExplicitSelection() {
        val savedStyles=mutableListOf<MapStyle>()
        val savedTraffic=mutableListOf<Boolean>()
        val controller=MapUiController(MapStyle.NORMAL,false,savedStyles::add,savedTraffic::add,initialDark=true)
        assertEquals(MapStyle.NIGHT,controller.style)
        controller.selectStyle(MapStyle.NAVIGATION_NIGHT)
        controller.applyTheme(true)
        assertEquals(MapStyle.NAVIGATION_NIGHT,controller.style)
        controller.applyTheme(false)
        assertEquals(MapStyle.NAVIGATION_NIGHT,controller.style)
        assertEquals(listOf(MapStyle.NAVIGATION_NIGHT),savedStyles)
        assertFalse(controller.traffic)
        assertTrue(savedTraffic.isEmpty())
    }
}
