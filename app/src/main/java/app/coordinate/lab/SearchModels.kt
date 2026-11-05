package app.coordinate.lab

import app.coordinate.lab.core.GeoPoint

enum class SearchScope { SMART, CITY, NATIONWIDE }
enum class SearchArea { NEARBY, CITY, NATIONWIDE }

/** center is the user's selected WGS84 point, never the device's real location. */
data class PlaceSearchRequest(
    val keyword: String,
    val city: String,
    val center: GeoPoint,
    val scope: SearchScope
)

data class PlaceSuggestion(
    val name: String,
    val city: String,
    val detail: String,
    val point: GeoPoint?
)

/** A cursor fixes the effective range for the remainder of this result set. Pages start at 1. */
data class PlaceSearchCursor(
    val request: PlaceSearchRequest,
    val area: SearchArea,
    val radiusMeters: Int = 0,
    val page: Int = 1
)

data class PlaceSearchPage(
    val places: List<Place>,
    val next: PlaceSearchCursor?,
    val description: String,
    /** 0 means unavailable: this SDK exposes page count, not an exact POI total. */
    val totalCount: Int = 0,
    val citySuggestions: List<String> = emptyList()
)

/** AMap has no public abort API here; cancellation prevents all subsequent delivery. */
fun interface SearchCancellation {
    fun cancel()
}
