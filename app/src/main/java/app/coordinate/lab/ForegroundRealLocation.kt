package app.coordinate.lab

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import app.coordinate.lab.core.GeoPoint
import app.coordinate.lab.core.RealFixPolicy

/** Foreground, one-shot physical fix only. Never accept a mock or stale cached position. */
internal class ForegroundRealLocation(context: Context) {
    private val manager = context.getSystemService(LocationManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var listener: LocationListener? = null
    private var timeout: Runnable? = null

    @SuppressLint("MissingPermission") // Activity checks foreground coarse/fine permission before calling.
    fun start(onFix: (GeoPoint) -> Unit, onUnavailable: () -> Unit) {
        stop()
        val observer = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                @Suppress("DEPRECATION") val mock = location.isFromMockProvider
                val age = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
                if (!RealFixPolicy.accepts(mock, age, location.latitude, location.longitude)) return
                stop()
                onFix(GeoPoint(location.latitude, location.longitude))
            }
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
            @Deprecated("Legacy Android callback")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        listener = observer
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        var registered = false
        providers.forEach { provider ->
            runCatching {
                if (manager.isProviderEnabled(provider)) {
                    manager.requestLocationUpdates(provider, 1000L, 0f, observer, Looper.getMainLooper())
                    registered = true
                }
            }
        }
        if (!registered) { stop(); onUnavailable(); return }
        timeout = Runnable { stop(); onUnavailable() }.also { handler.postDelayed(it, 25_000L) }
    }

    fun stop() {
        listener?.let { runCatching { manager.removeUpdates(it) } }
        listener = null
        timeout?.let(handler::removeCallbacks)
        timeout = null
    }
}
