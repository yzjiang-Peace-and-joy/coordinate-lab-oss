package app.coordinate.lab.core

object RealFixPolicy {
    fun accepts(mock: Boolean, ageMs: Long, latitude: Double, longitude: Double): Boolean =
        !mock && ageMs in 0..30_000 && latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0
}
