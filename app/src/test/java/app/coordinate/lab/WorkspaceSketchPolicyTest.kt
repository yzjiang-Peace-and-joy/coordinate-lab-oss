package app.coordinate.lab

import app.coordinate.lab.core.GeoPoint
import org.junit.Assert.*
import org.junit.Test

class WorkspaceSketchPolicyTest {
    private fun points(size: Int) = List(size) { GeoPoint(30.0 + it * 0.00001, 120.0) }

    @Test fun preservesStrokeBoundariesAndExactPointsWithDefensiveCopies() {
        val first = points(4).toMutableList()
        val second = points(3).toMutableList()
        val old = mutableListOf<List<GeoPoint>>(first)
        val result = WorkspaceSketchPolicy.append(old, second)
        assertEquals(listOf(first, second), result)
        first.clear(); second.clear(); old.clear()
        assertEquals(listOf(points(4), points(3)), result)
    }

    @Test fun capRejectsEntireStrokeWithoutChangingExistingDraft() {
        val initial = listOf(points(9_999))
        assertTrue(runCatching { WorkspaceSketchPolicy.append(initial, points(2)) }.isFailure)
        assertEquals(9_999, initial.single().size)
        assertEquals(10_000, WorkspaceSketchPolicy.append(listOf(points(9_998)), points(2)).flatten().size)
    }

    @Test fun zeroOrOnePointDoesNotBecomeCompletedStroke() {
        assertTrue(runCatching { WorkspaceSketchPolicy.append(emptyList(), emptyList()) }.isFailure)
        assertTrue(runCatching { WorkspaceSketchPolicy.append(emptyList(), points(1)) }.isFailure)
    }

    @Test fun undoRemovesOnlyLastCompleteStrokeAndHandlesEmptyDraft() {
        val first = points(4); val second = points(3)
        assertEquals(listOf(first), WorkspaceSketchPolicy.undo(listOf(first, second)))
        assertTrue(WorkspaceSketchPolicy.undo(listOf(first)).isEmpty())
        assertTrue(WorkspaceSketchPolicy.undo(emptyList()).isEmpty())
    }
}
