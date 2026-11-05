package app.coordinate.lab

/**
 * Theme changes temporarily choose the night layer, never replacing the user's stored preference.
 * An explicit layer selection takes precedence until the next theme transition. This override is
 * deliberately session-only: a fresh dark launch starts in NIGHT again.
 */
class MapThemePolicy(initialPreferredStyle: MapStyle, initialDark: Boolean=false) {
    var preferredStyle: MapStyle=initialPreferredStyle
        private set
    var dark: Boolean=initialDark
        private set
    var style: MapStyle=if(initialDark)MapStyle.NIGHT else initialPreferredStyle
        private set

    fun selectStyle(value: MapStyle) {
        preferredStyle=value
        style=value
    }

    /** False means a recomposition, not a transition; retain any explicit manual override. */
    fun applyTheme(value: Boolean): Boolean {
        if(dark==value)return false
        dark=value
        style=if(value)MapStyle.NIGHT else preferredStyle
        return true
    }
}
