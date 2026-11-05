package app.coordinate.lab.core

/** Bounded, immutable draft snapshots; an undo never edits the running session. */
class WaypointHistory<T>(initial: List<T> = emptyList(), private val maxPoints: Int = 20, private val maxUndo: Int = 30) {
    init { require(maxPoints >= 2 && maxUndo > 0 && initial.size <= maxPoints) }
    var points: List<T> = initial.toList()
        private set
    private val history = ArrayDeque<List<T>>()
    val canUndo: Boolean get() = history.isNotEmpty()

    /** A library selection is a new draft boundary, not an undo into an unrelated route. */
    fun reset(next: List<T>) {
        require(next.size <= maxPoints)
        points = next.toList()
        history.clear()
    }

    fun change(next: List<T>): Boolean {
        require(next.size <= maxPoints) { "路线最多支持 $maxPoints 个选点" }
        if (next == points) return false
        history.addLast(points)
        while (history.size > maxUndo) history.removeFirst()
        points = next.toList()
        return true
    }

    fun undo(): Boolean {
        if (history.isEmpty()) return false
        points = history.removeLast()
        return true
    }
}
