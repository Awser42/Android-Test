package com.awser.counter

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class CounterThemesTest {
    @Test fun sixteenNamedPalettesHaveUniqueStableIds() {
        val themes = CounterThemes.all
        assertEquals(16, themes.size)
        assertEquals(themes.size, themes.map { it.id }.toSet().size)
        assertEquals(themes.size, themes.map { it.name }.toSet().size)
        themes.forEach { theme ->
            assertTrue(theme.id.matches(Regex("[a-z]+")))
            assertTrue(theme.name.isNotBlank())
            assertEquals(theme, CounterThemes.resolve(theme.id))
        }
    }

    @Test fun unknownSelectionsKeepTheExistingEmeraldAppearance() {
        val default = CounterThemes.resolve(CounterThemes.DEFAULT_ID)
        assertEquals("Emerald", default.name)
        assertEquals(0xFF74DFA5.toInt(), default.darkAccent)
        assertEquals(0xFF23754C.toInt(), default.lightAccent)
        assertEquals(0xFF74E38F.toInt(), default.lcdTop)
        assertEquals(0xFF4DCF71.toInt(), default.lcdBottom)
        assertEquals(0xFF113B1F.toInt(), default.lcdInk)
        listOf("", "unknown", "EMERALD").forEach { assertEquals(default, CounterThemes.resolve(it)) }
    }

    @Test fun everyPaletteKeepsSmallAccentTextReadableOnTheAppSurfaces() {
        CounterThemes.all.forEach { theme ->
            assertTrue("${theme.name} dark accent must remain readable", contrast(theme.darkAccent, 0xFF272B29.toInt()) >= 4.5)
            assertTrue("${theme.name} light accent must remain readable", contrast(theme.lightAccent, 0xFFF3F4F0.toInt()) >= 4.5)
        }
    }

    @Test fun everyPaletteUsesOpaqueLcdColorsAndClearlyReadableDigits() {
        CounterThemes.all.forEach { theme ->
            listOf(theme.darkAccent, theme.lightAccent, theme.lcdTop, theme.lcdBottom, theme.lcdInk)
                .forEach { assertEquals("${theme.name} colors must be opaque", 255, it ushr 24) }
            assertNotEquals("${theme.name} should have a visible LCD gradient", theme.lcdTop, theme.lcdBottom)
            assertTrue("${theme.name} digits readable at gradient top", contrast(theme.lcdInk, theme.lcdTop) >= 5.0)
            assertTrue("${theme.name} digits readable at gradient bottom", contrast(theme.lcdInk, theme.lcdBottom) >= 5.0)
        }
    }

    private fun contrast(first: Int, second: Int): Double {
        val firstLuminance = luminance(first)
        val secondLuminance = luminance(second)
        return (maxOf(firstLuminance, secondLuminance) + 0.05) / (minOf(firstLuminance, secondLuminance) + 0.05)
    }

    private fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val value = ((color ushr shift) and 255) / 255.0
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
