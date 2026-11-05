package app.coordinate.lab.core

import java.util.Locale

object SessionClock {
    fun seconds(active: Boolean, startedAtMs: Long, nowMs: Long): Long =
        if (!active || startedAtMs <= 0) 0 else (nowMs - startedAtMs).coerceAtLeast(0) / 1000

    fun format(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d:%02d", safe / 3600, safe / 60 % 60, safe % 60)
    }
}
