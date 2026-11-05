package app.coordinate.lab.core

import org.junit.Assert.*
import org.junit.Test

class RouteStartPolicyTest {
    @Test fun completePreviewIsReady() {
        assertTrue(RouteStartPolicy.canStart(false, false, null, 3, 350))
    }
    @Test fun draftCannotStartEvenWithPreviousGeometry() {
        assertFalse(RouteStartPolicy.canStart(true, false, null, 3, 350))
    }
    @Test fun partialPlanningCannotStart() {
        assertFalse(RouteStartPolicy.canStart(false, true, null, 3, 350))
    }
    @Test fun errorCannotReusePreviousGeometry() {
        assertFalse(RouteStartPolicy.canStart(false, false, "第2段失败", 3, 350))
    }
    @Test fun countsMustStayWithinLimits() {
        listOf(0, 1, 21).forEach {assertFalse(RouteStartPolicy.canStart(false, false, null, it, 20))}
        listOf(0, 1, 10001).forEach {assertFalse(RouteStartPolicy.canStart(false, false, null, 3, it))}
        assertTrue(RouteStartPolicy.canStart(false, false, null, 20, 10000))
    }
}
