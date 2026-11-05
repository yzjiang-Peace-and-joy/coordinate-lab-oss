package app.coordinate.lab.core

import org.junit.Assert.*
import org.junit.Test

class WaypointHistoryTest {
    @Test fun favoriteSelectionStartsFreshUndoBoundaryAndCopiesHandles() {
        val draft=WaypointHistory(listOf("旧起点"))
        draft.change(listOf("旧起点","旧终点"))
        val handles=mutableListOf("新起点","新终点")
        draft.reset(handles);handles.clear()
        assertEquals(listOf("新起点","新终点"),draft.points)
        assertFalse(draft.canUndo);assertFalse(draft.undo())
        draft.change(emptyList());draft.undo()
        assertEquals(listOf("新起点","新终点"),draft.points)
    }
    @Test fun appendedPointsUndoInOrder() {
        val draft = WaypointHistory(listOf("A"))
        assertTrue(draft.change(listOf("A", "B")))
        draft.change(listOf("A", "B", "C"))
        assertTrue(draft.undo())
        assertEquals(listOf("A", "B"), draft.points)
        assertTrue(draft.undo())
        assertEquals(listOf("A"), draft.points)
        assertFalse(draft.undo())
    }
    @Test fun clearCanBeUndone() {
        val draft = WaypointHistory(listOf("A", "B", "C"))
        draft.change(emptyList())
        assertTrue(draft.points.isEmpty())
        draft.undo()
        assertEquals(listOf("A", "B", "C"), draft.points)
    }
    @Test fun reorderAndDeletionHaveIndependentSnapshots() {
        val draft = WaypointHistory(listOf("A", "B", "C"))
        draft.change(listOf("B", "A", "C"))
        draft.change(listOf("B", "C"))
        draft.undo()
        assertEquals(listOf("B", "A", "C"), draft.points)
        draft.undo()
        assertEquals(listOf("A", "B", "C"), draft.points)
    }
    @Test fun sameDraftDoesNotConsumeUndo() {
        val draft = WaypointHistory(listOf("A", "B"))
        assertFalse(draft.change(listOf("A", "B")))
        assertFalse(draft.canUndo)
    }
    @Test fun callerCannotMutateSavedLists() {
        val initial = mutableListOf("A")
        val next = mutableListOf("A", "B")
        val draft = WaypointHistory(initial)
        initial.add("not a waypoint")
        draft.change(next)
        next.clear()
        assertEquals(listOf("A", "B"), draft.points)
        draft.undo()
        assertEquals(listOf("A"), draft.points)
    }
    @Test fun undoStorageIsBounded() {
        val draft = WaypointHistory(listOf(0), maxUndo = 2)
        (1..4).forEach { draft.change(listOf(it)) }
        draft.undo(); draft.undo()
        assertEquals(listOf(2), draft.points)
        assertFalse(draft.undo())
    }
    @Test fun overLimitIsRejectedWithoutChangingDraft() {
        val draft = WaypointHistory(listOf(1, 2), maxPoints = 3)
        try { draft.change(listOf(1, 2, 3, 4)); fail() } catch (_: IllegalArgumentException) { }
        assertEquals(listOf(1, 2), draft.points)
        assertFalse(draft.canUndo)
    }
    @Test fun freshChangeAfterUndoUsesCurrentDraft() {
        val draft = WaypointHistory(listOf("A"))
        draft.change(listOf("A", "B")); draft.undo()
        draft.change(listOf("A", "C")); draft.undo()
        assertEquals(listOf("A"), draft.points)
    }
}
