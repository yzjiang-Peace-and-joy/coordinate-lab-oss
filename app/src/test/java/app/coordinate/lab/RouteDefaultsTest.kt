package app.coordinate.lab

import org.junit.Assert.assertFalse
import org.junit.Test

class RouteDefaultsTest {
    @Test fun newUiStateDefaultsToStraightRoute() {assertFalse(UiState().road)}
    @Test fun newUiStateDoesNotCloseRouteWithoutOptIn() {assertFalse(UiState().routeClosed)}
}
