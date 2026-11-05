package app.coordinate.lab.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteWaypointSwipeTest {
    @Test fun shortSwipesNeverRemove() {
        assertFalse(RouteWaypointSwipe.shouldRemove(-20f, 400f, 96f))
        assertFalse(RouteWaypointSwipe.shouldRemove(0f, 400f, 96f))
        assertFalse(RouteWaypointSwipe.shouldRemove(180f, 400f, 96f))
    }

    @Test fun deliberateLeftSwipeRemovesAtThreshold() {
        assertFalse(RouteWaypointSwipe.shouldRemove(-95f, 400f, 96f))
        assertTrue(RouteWaypointSwipe.shouldRemove(-96f, 400f, 96f))
        assertTrue(RouteWaypointSwipe.shouldRemove(-200f, 400f, 96f))
    }

    @Test fun narrowRowsUseProportionalThreshold() {
        assertFalse(RouteWaypointSwipe.shouldRemove(-63f, 200f, 96f))
        assertTrue(RouteWaypointSwipe.shouldRemove(-64f, 200f, 96f))
    }

    @Test fun unavailableOrInvalidMeasurementsNeverRemove() {
        assertFalse(RouteWaypointSwipe.shouldRemove(-100f, 0f, 96f))
        assertFalse(RouteWaypointSwipe.shouldRemove(-100f, 400f, 0f))
        assertFalse(RouteWaypointSwipe.shouldRemove(Float.NEGATIVE_INFINITY, 400f, 96f))
        assertFalse(RouteWaypointSwipe.shouldRemove(-100f, Float.NaN, 96f))
        assertFalse(RouteWaypointSwipe.shouldRemove(-100f, 400f, Float.NaN))
    }
}
