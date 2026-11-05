package app.coordinate.lab

/** Roles carry meaning without making marker appearance affect coordinates or route order. */
internal object MapMarkerPolicy {
    fun single(ready: Boolean, picked: Boolean): MapMarkerRole? = when {
        !ready -> null
        picked -> MapMarkerRole.SELECTED
        else -> MapMarkerRole.REAL
    }

    fun route(index: Int, last: Int, closed: Boolean): MapMarkerRole {
        require(index in 0..last)
        return when {
            index == 0 && closed -> MapMarkerRole.LOOP
            index == 0 -> MapMarkerRole.START
            index == last && !closed -> MapMarkerRole.END
            else -> MapMarkerRole.VIA
        }
    }
}
