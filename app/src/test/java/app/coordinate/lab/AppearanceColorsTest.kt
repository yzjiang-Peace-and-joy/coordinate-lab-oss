package app.coordinate.lab

import org.junit.Assert.*
import org.junit.Test

class AppearanceColorsTest {
    @Test fun defaultsKeepSystemThemeAndOriginalEmerald() {
        assertEquals(ThemeMode.SYSTEM, AppearanceSettings().theme)
        assertEquals("176349", AppearanceSettings().accentHex)
        assertEquals(0xff176349.toInt(), AppearanceColors.palette("176349", false).primary)
        assertEquals(-1, AppearanceColors.palette("176349", false).surface)
        assertEquals(0xff176349.toInt(), AppearanceColors.mapAccent("176349"))
    }

    @Test fun customHexAcceptsExactlyOpaqueRgb() {
        assertEquals("A0B1C2",AppearanceColors.normalizeHex(" #a0b1C2 "))
        listOf("", "123", "#12345", "11223344", "##123456", "G12345", "12 345", "１２３４５６").forEach {
            assertNull(it,AppearanceColors.normalizeHex(it))
        }
    }

    @Test fun presetsAreUniqueAndNormalized() {
        assertEquals(8, AppearanceColors.presets.size)
        assertEquals(8, AppearanceColors.presets.map { it.hex }.toSet().size)
        AppearanceColors.presets.forEach { assertEquals(it.hex,AppearanceColors.normalizeHex(it.hex)) }
    }

    @Test fun invalidStoredAccentCanAlwaysFallBackToDefaultPalette() {
        assertEquals(AppearanceColors.palette("176349",false),AppearanceColors.palette("not a color",false))
        assertEquals(AppearanceColors.mapAccent("176349"),AppearanceColors.mapAccent("#bad"))
    }

    @Test fun contrastMathMatchesBlackAndWhite() {
        assertEquals(21.0,AppearanceColors.contrastRatio(0xff000000.toInt(),-1),0.000001)
        assertEquals(1.0,AppearanceColors.contrastRatio(0xff888888.toInt(),0xff888888.toInt()),0.000001)
    }

    @Test fun allPaletteTextRolesMeetNormalTextContrastForPresetsAndExtremeCustomColors() {
        for (hex in testColors()) for (dark in listOf(false,true)) {
            val p=AppearanceColors.palette(hex,dark)
            val pairs=listOf(
                p.primary to p.onPrimary, p.secondary to p.onSecondary, p.tertiary to p.onTertiary,
                p.primaryContainer to p.onPrimaryContainer,p.secondaryContainer to p.onSecondaryContainer,p.tertiaryContainer to p.onTertiaryContainer,
                p.surface to p.primary,p.surface to p.secondary,p.surface to p.tertiary,
                p.background to p.onBackground,p.surface to p.onSurface,p.surfaceVariant to p.onSurfaceVariant,
                p.inverseSurface to p.inverseOnSurface,p.inverseSurface to p.inversePrimary,
                p.error to p.onError,p.errorContainer to p.onErrorContainer,
                p.primaryFixed to p.onPrimaryFixed,p.primaryFixed to p.onPrimaryFixedVariant,
                p.primaryFixedDim to p.onPrimaryFixed,p.primaryFixedDim to p.onPrimaryFixedVariant,
                p.secondaryFixed to p.onSecondaryFixed,p.secondaryFixed to p.onSecondaryFixedVariant,
                p.secondaryFixedDim to p.onSecondaryFixed,p.secondaryFixedDim to p.onSecondaryFixedVariant,
                p.tertiaryFixed to p.onTertiaryFixed,p.tertiaryFixed to p.onTertiaryFixedVariant,
                p.tertiaryFixedDim to p.onTertiaryFixed,p.tertiaryFixedDim to p.onTertiaryFixedVariant
            )
            pairs.forEachIndexed { index,(background,foreground) ->
                assertTrue("$hex dark=$dark pair=$index",AppearanceColors.contrastRatio(background,foreground)>=4.5)
            }
            val surfaces=listOf(p.surface,p.background,p.surfaceBright,p.surfaceDim,p.surfaceContainer,
                p.surfaceContainerHigh,p.surfaceContainerHighest,p.surfaceContainerLow,p.surfaceContainerLowest)
            surfaces.forEachIndexed { index,surface ->
                assertTrue("$hex dark=$dark neutral=$index primary",AppearanceColors.contrastRatio(surface,p.primary)>=4.5)
                assertTrue("$hex dark=$dark neutral=$index ink",AppearanceColors.contrastRatio(surface,p.onSurface)>=4.5)
                assertTrue("$hex dark=$dark neutral=$index secondary ink",AppearanceColors.contrastRatio(surface,p.onSurfaceVariant)>=4.5)
            }
        }
    }

    @Test fun mapAccentIsOpaqueReadableAndDoesNotDependOnTheme() {
        for (hex in testColors()) {
            val map=AppearanceColors.mapAccent(hex)
            assertEquals(255,map ushr 24)
            assertTrue(hex,AppearanceColors.contrastRatio(map,-1)>=4.5)
        }
    }

    @Test fun surfaceAndSemanticErrorRespondPredictablyToMode() {
        val light=AppearanceColors.palette("2864BD",false)
        val dark=AppearanceColors.palette("2864BD",true)
        assertNotEquals(light.primary,dark.primary)
        assertNotEquals(light.surface,dark.surface)
        assertNotEquals(light.primary,AppearanceColors.palette("AD4569",false).primary)
        assertEquals(light.error,AppearanceColors.palette("AD4569",false).error)
        assertEquals(dark.error,AppearanceColors.palette("AD4569",true).error)
        assertEquals(dark,AppearanceColors.palette("#2864bd",true))
    }

    private fun testColors() = AppearanceColors.presets.map { it.hex } +
        listOf("000000","FFFFFF","FFFF00","00FF00","00FFFF","FF0000","0000FF","FF00FF","808080","121212","FAFAF0")
}
