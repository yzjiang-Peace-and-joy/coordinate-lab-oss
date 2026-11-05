package app.coordinate.lab

import app.coordinate.lab.core.GeoPoint

internal enum class MapWorkspaceTool { BROWSE, DRAW }

internal data class MapWorkspaceState(
    val focused: Boolean = false,
    val tool: MapWorkspaceTool = MapWorkspaceTool.BROWSE,
    val markers: List<Place> = emptyList(),
    val pickedPlace: Place? = null,
    val sketch: List<GeoPoint> = emptyList(),
    val previewPath: List<GeoPoint> = emptyList(),
    val previewName: String = ""
)
