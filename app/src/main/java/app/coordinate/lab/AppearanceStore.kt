package app.coordinate.lab

import android.content.Context

/** Appearance stays separate from location, AMap credentials, route drafts and favorites. */
class AppearanceStore(context: Context) {
    private val preferences = context.getSharedPreferences("appearance_private", Context.MODE_PRIVATE)

    fun load(): AppearanceSettings {
        val theme = runCatching { ThemeMode.valueOf(preferences.getString("theme", null).orEmpty()) }
            .getOrDefault(ThemeMode.SYSTEM)
        val accent = runCatching { preferences.getString("accent_hex", null) }
            .getOrNull()?.let(AppearanceColors::normalizeHex) ?: AppearanceColors.DEFAULT_HEX
        return AppearanceSettings(theme, accent)
    }

    fun save(settings: AppearanceSettings): Boolean {
        val hex = AppearanceColors.normalizeHex(settings.accentHex) ?: return false
        return runCatching {
            preferences.edit().putString("theme", settings.theme.name).putString("accent_hex", hex).commit()
        }.getOrDefault(false)
    }
}
