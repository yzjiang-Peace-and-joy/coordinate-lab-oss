package app.coordinate.lab

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppearanceSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val accentHex: String = AppearanceColors.DEFAULT_HEX
)

data class AccentPreset(val label: String, val hex: String)

/** Platform-independent, opaque sRGB roles. No device wallpaper or map-layer dependency. */
data class AppearancePalette(
    val primary: Int, val onPrimary: Int, val primaryContainer: Int, val onPrimaryContainer: Int,
    val inversePrimary: Int,
    val secondary: Int, val onSecondary: Int, val secondaryContainer: Int, val onSecondaryContainer: Int,
    val tertiary: Int, val onTertiary: Int, val tertiaryContainer: Int, val onTertiaryContainer: Int,
    val background: Int, val onBackground: Int,
    val surface: Int, val onSurface: Int, val surfaceVariant: Int, val onSurfaceVariant: Int,
    val surfaceTint: Int, val inverseSurface: Int, val inverseOnSurface: Int,
    val error: Int, val onError: Int, val errorContainer: Int, val onErrorContainer: Int,
    val outline: Int, val outlineVariant: Int, val scrim: Int,
    val surfaceBright: Int, val surfaceDim: Int, val surfaceContainer: Int,
    val surfaceContainerHigh: Int, val surfaceContainerHighest: Int,
    val surfaceContainerLow: Int, val surfaceContainerLowest: Int,
    val primaryFixed: Int, val primaryFixedDim: Int, val onPrimaryFixed: Int, val onPrimaryFixedVariant: Int,
    val secondaryFixed: Int, val secondaryFixedDim: Int, val onSecondaryFixed: Int, val onSecondaryFixedVariant: Int,
    val tertiaryFixed: Int, val tertiaryFixedDim: Int, val onTertiaryFixed: Int, val onTertiaryFixedVariant: Int
)

object AppearanceColors {
    const val DEFAULT_HEX = "176349"
    private const val WHITE = -1
    private const val BLACK = -0x1000000
    val presets: List<AccentPreset> = listOf(
        AccentPreset("松绿", DEFAULT_HEX), AccentPreset("湖青", "007D83"),
        AccentPreset("晴蓝", "2864BD"), AccentPreset("靛蓝", "5455A9"),
        AccentPreset("鸢紫", "8651A5"), AccentPreset("玫红", "AD4569"),
        AccentPreset("暖橙", "A65721"), AccentPreset("岩灰", "596777")
    )

    fun normalizeHex(input: String): String? {
        val value = input.trim().removePrefix("#")
        return value.takeIf { it.length == 6 && it.all { c -> c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F' } }
            ?.uppercase(java.util.Locale.ROOT)
    }

    private fun color(hex: String): Int = (0xff000000L or hex.toLong(16)).toInt()
    private fun seed(hex: String) = color(normalizeHex(hex) ?: DEFAULT_HEX)

    /** Keep map pins readable regardless of app theme; bright custom colors are gently darkened. */
    fun mapAccent(hex: String): Int = readable(seed(hex), WHITE, 4.5)

    fun contrastRatio(first: Int, second: Int): Double {
        val a = luminance(first)
        val b = luminance(second)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    private fun luminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((argb ushr shift) and 255) / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun mix(first: Int, second: Int, amount: Double): Int {
        fun channel(shift: Int) = ((((first ushr shift) and 255) * (1 - amount) +
            ((second ushr shift) and 255) * amount).roundToInt()).coerceIn(0, 255)
        return BLACK or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun ink(background: Int): Int =
        if (contrastRatio(BLACK, background) >= contrastRatio(WHITE, background)) BLACK else WHITE

    /** Move the smallest necessary distance toward the better contrasting endpoint. */
    private fun readable(foreground: Int, background: Int, minimum: Double = 4.5): Int {
        if (contrastRatio(foreground, background) >= minimum) return foreground
        val target = ink(background)
        var low = 0.0
        var high = 1.0
        repeat(24) {
            val mid = (low + high) / 2
            if (contrastRatio(mix(foreground, target, mid), background) >= minimum) high = mid else low = mid
        }
        return mix(foreground, target, high)
    }

    fun palette(hex: String, dark: Boolean): AppearancePalette {
        val accent = seed(hex)
        // A small, consistent tint rather than a saturated page background.
        val neutral = mix(accent, color("777D79"), 0.86)
        fun light(amount: Double) = mix(neutral, WHITE, amount)
        fun dim(amount: Double) = mix(neutral, BLACK, amount)
        val surface = if (dark) dim(0.72) else WHITE
        val background = if (dark) dim(0.80) else light(0.94)
        val variant = if (dark) dim(0.48) else light(0.83)
        // Text is reused on sheets/cards, so test against the least favorable neutral surface.
        val textSurface = if (dark) dim(0.47) else light(0.69)
        val onSurface = readable(if (dark) light(0.90) else dim(0.69), textSurface)
        val onVariant = readable(if (dark) light(0.64) else dim(0.28), textSurface)
        val inverseSurface = if (dark) light(0.88) else dim(0.71)
        val inverseOnSurface = readable(if (dark) dim(0.71) else light(0.90), inverseSurface)

        data class Family(val main: Int, val on: Int, val container: Int, val onContainer: Int,
            val fixed: Int, val fixedDim: Int, val fixedInk: Int, val fixedVariant: Int)
        fun family(base: Int): Family {
            val main = readable(if (dark) mix(base, WHITE, 0.62) else base, textSurface)
            val container = if (dark) mix(base, BLACK, 0.55) else mix(base, WHITE, 0.87)
            val fixed = mix(base, WHITE, 0.80)
            val fixedDim = mix(base, WHITE, 0.62)
            // Fixed roles must work on both the normal and dim fixed background.
            val fixedInk = readable(readable(mix(base, BLACK, 0.73), fixed), fixedDim)
            val fixedVariant = readable(readable(mix(base, BLACK, 0.45), fixed), fixedDim)
            return Family(main, ink(main), container, readable(if (dark) mix(base, WHITE, 0.86) else mix(base, BLACK, 0.55), container),
                fixed, fixedDim, fixedInk, fixedVariant)
        }
        val p = family(accent)
        val s = family(mix(accent, neutral, 0.25))
        val t = family(mix(accent, color("665A88"), 0.32))
        val error = if (dark) color("FFB4AB") else color("BA1A1A")
        val errorContainer = if (dark) color("93000A") else color("FFDAD6")
        return AppearancePalette(
            primary=p.main, onPrimary=p.on, primaryContainer=p.container, onPrimaryContainer=p.onContainer,
            inversePrimary=readable(if (dark) accent else mix(accent,WHITE,0.62),inverseSurface),
            secondary=s.main,onSecondary=s.on,secondaryContainer=s.container,onSecondaryContainer=s.onContainer,
            tertiary=t.main,onTertiary=t.on,tertiaryContainer=t.container,onTertiaryContainer=t.onContainer,
            background=background,onBackground=readable(onSurface,background),
            surface=surface,onSurface=onSurface,surfaceVariant=variant,onSurfaceVariant=onVariant,
            surfaceTint=p.main,inverseSurface=inverseSurface,inverseOnSurface=inverseOnSurface,
            error=error,onError=ink(error),errorContainer=errorContainer,onErrorContainer=ink(errorContainer),
            outline=readable(if(dark) light(0.40) else dim(0.15),surface,3.0),
            outlineVariant=if(dark) dim(0.34) else light(0.72),scrim=BLACK,
            surfaceBright=if(dark) dim(0.47) else WHITE,
            surfaceDim=if(dark) dim(0.80) else light(0.69),
            surfaceContainer=if(dark) dim(0.65) else light(0.90),
            surfaceContainerHigh=if(dark) dim(0.56) else light(0.84),
            surfaceContainerHighest=if(dark) dim(0.47) else light(0.77),
            surfaceContainerLow=if(dark) dim(0.74) else light(0.96),
            surfaceContainerLowest=if(dark) dim(0.87) else WHITE,
            primaryFixed=p.fixed,primaryFixedDim=p.fixedDim,onPrimaryFixed=p.fixedInk,onPrimaryFixedVariant=p.fixedVariant,
            secondaryFixed=s.fixed,secondaryFixedDim=s.fixedDim,onSecondaryFixed=s.fixedInk,onSecondaryFixedVariant=s.fixedVariant,
            tertiaryFixed=t.fixed,tertiaryFixedDim=t.fixedDim,onTertiaryFixed=t.fixedInk,onTertiaryFixedVariant=t.fixedVariant
        )
    }
}
