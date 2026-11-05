package app.coordinate.lab.core

import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToLong

/** Pure decisions shared by the asynchronous search adapter and JVM tests. */
object SearchPolicy {
    const val PAGE_SIZE = 20
    const val INITIAL_RADIUS_METERS = 20_000
    const val EXPANDED_RADIUS_METERS = 50_000

    fun shouldExpandNearby(radiusMeters: Int, uniqueCount: Int, page: Int): Boolean =
        page == 1 && radiusMeters == INITIAL_RADIUS_METERS && uniqueCount < 3

    fun shouldFallbackNationwide(radiusMeters: Int, uniqueCount: Int, page: Int): Boolean =
        page == 1 && radiusMeters == EXPANDED_RADIUS_METERS && uniqueCount == 0

    fun nextPage(page: Int, pageCount: Int, rawItemCount: Int): Int? {
        require(page >= 1) { "搜索页码必须从 1 开始" }
        return if (rawItemCount > 0 && page < pageCount) page + 1 else null
    }

    fun usableCoordinate(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 &&
            longitude in -180.0..180.0 && !(latitude == 0.0 && longitude == 0.0)

    /** Keep different branches with the same name, and different POIs at the same coordinate. */
    fun <T> distinctPlaces(items: List<T>, name: (T) -> String, point: (T) -> GeoPoint): List<T> =
        items.distinctBy {
            val location = point(it)
            Triple(
                (location.latitude * 1_000_000).roundToLong(),
                (location.longitude * 1_000_000).roundToLong(),
                name(it).trim().lowercase(Locale.ROOT)
            )
        }

    /** Fail closed for address matches when a strict city cannot be verified. */
    fun addressMatchesCity(city: String, province: String, resultCity: String, district: String, adcode: String): Boolean {
        val requested = city.trim()
        if (requested.isEmpty()) return false
        if (requested.all(Char::isDigit)) {
            if (requested.length != 6 || adcode.length != 6) return false
            val prefix = when {
                requested.endsWith("0000") -> requested.take(2)
                requested.endsWith("00") -> requested.take(4)
                else -> requested
            }
            return adcode.startsWith(prefix)
        }
        fun normalized(value: String) = value.trim().removeSuffix("市").removeSuffix("省")
        val expected = normalized(requested)
        return listOf(province, resultCity, district).any { normalized(it) == expected }
    }
}

/** All callbacks (including timeout/cancel races) share the same terminal gate. */
internal class SearchCompletionGate {
    private val terminal = AtomicBoolean(false)
    val isOpen: Boolean get() = !terminal.get()
    fun tryFinish(): Boolean = terminal.compareAndSet(false, true)
    fun cancel() { terminal.set(true) }
}
