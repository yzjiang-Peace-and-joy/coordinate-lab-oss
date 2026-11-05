package app.coordinate.lab

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

@Composable
fun CoordinateAppearanceTheme(settings: AppearanceSettings, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.theme) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val view = LocalView.current
    SideEffect {
        context.findActivity()?.let { activity ->
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val scheme = remember(settings.accentHex, dark) { AppearanceColors.palette(settings.accentHex, dark).toColorScheme() }
    MaterialTheme(
        colorScheme = scheme,
        shapes = Shapes(small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(16.dp),large=RoundedCornerShape(24.dp)),
        content = content
    )
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    // ContextThemeWrapper chains can vary by host; never assume a direct Activity cast.
    repeat(20) {
        if (current is Activity) return current as Activity
        val base = (current as? ContextWrapper)?.baseContext ?: return null
        if (base === current) return null
        current = base
    }
    return null
}

private fun AppearancePalette.toColorScheme(): ColorScheme = ColorScheme(
    primary=Color(primary),onPrimary=Color(onPrimary),primaryContainer=Color(primaryContainer),onPrimaryContainer=Color(onPrimaryContainer),
    inversePrimary=Color(inversePrimary),
    secondary=Color(secondary),onSecondary=Color(onSecondary),secondaryContainer=Color(secondaryContainer),onSecondaryContainer=Color(onSecondaryContainer),
    tertiary=Color(tertiary),onTertiary=Color(onTertiary),tertiaryContainer=Color(tertiaryContainer),onTertiaryContainer=Color(onTertiaryContainer),
    background=Color(background),onBackground=Color(onBackground),surface=Color(surface),onSurface=Color(onSurface),
    surfaceVariant=Color(surfaceVariant),onSurfaceVariant=Color(onSurfaceVariant),surfaceTint=Color(surfaceTint),
    inverseSurface=Color(inverseSurface),inverseOnSurface=Color(inverseOnSurface),
    error=Color(error),onError=Color(onError),errorContainer=Color(errorContainer),onErrorContainer=Color(onErrorContainer),
    outline=Color(outline),outlineVariant=Color(outlineVariant),scrim=Color(scrim),
    surfaceBright=Color(surfaceBright),surfaceDim=Color(surfaceDim),surfaceContainer=Color(surfaceContainer),
    surfaceContainerHigh=Color(surfaceContainerHigh),surfaceContainerHighest=Color(surfaceContainerHighest),
    surfaceContainerLow=Color(surfaceContainerLow),surfaceContainerLowest=Color(surfaceContainerLowest),
    primaryFixed=Color(primaryFixed),primaryFixedDim=Color(primaryFixedDim),onPrimaryFixed=Color(onPrimaryFixed),onPrimaryFixedVariant=Color(onPrimaryFixedVariant),
    secondaryFixed=Color(secondaryFixed),secondaryFixedDim=Color(secondaryFixedDim),onSecondaryFixed=Color(onSecondaryFixed),onSecondaryFixedVariant=Color(onSecondaryFixedVariant),
    tertiaryFixed=Color(tertiaryFixed),tertiaryFixedDim=Color(tertiaryFixedDim),onTertiaryFixed=Color(onTertiaryFixed),onTertiaryFixedVariant=Color(onTertiaryFixedVariant)
)
