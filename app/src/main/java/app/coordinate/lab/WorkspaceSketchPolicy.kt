package app.coordinate.lab

import app.coordinate.lab.core.GeoPoint

/** Completed strokes survive activity recreation, but are not written into saved-state Bundles. */
object WorkspaceSketchPolicy {
    fun append(strokes: List<List<GeoPoint>>, stroke: List<GeoPoint>): List<List<GeoPoint>> {
        require(stroke.size >= 2) { "请先描绘一条路线" }
        val total = strokes.sumOf { it.size.toLong() } + stroke.size
        require(total <= RouteLibraryPolicy.MAX_TRACE_POINTS) { "描线最多 ${RouteLibraryPolicy.MAX_TRACE_POINTS} 个轨迹点，请先保存" }
        // No simplification, silent truncation, or inter-stroke deduplication.
        return strokes.map { it.toList() } + listOf(stroke.toList())
    }

    fun undo(strokes: List<List<GeoPoint>>): List<List<GeoPoint>> = strokes.dropLast(1).map { it.toList() }
}
