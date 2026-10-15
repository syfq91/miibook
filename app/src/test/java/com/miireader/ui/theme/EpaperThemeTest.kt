package com.miireader.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Phase 5 checks for the e-paper palette: high contrast, fully solid (no translucency),
 * and a 48dp touch-target baseline.
 *
 * Contrast ratios use the WCAG 2.1 relative-luminance formula. Text pairings must clear
 * 4.5:1 (AA normal text). `MediumGray` is intentionally excluded from the text checks: it
 * is only ever used for disabled icon tint, where WCAG exempts the control from contrast
 * requirements.
 *
 * These assertions are computed from the real [Color] values in `Color.kt`, so a palette
 * edit that weakens readability fails the build without needing a device.
 */
class EpaperThemeTest {

    private fun linearize(channel: Float): Float =
        if (channel <= 0.03928f) channel / 12.92f else ((channel + 0.055f) / 1.055f).pow(2.4f)

    private fun luminance(color: Color): Float =
        0.2126f * linearize(color.red) +
            0.7152f * linearize(color.green) +
            0.0722f * linearize(color.blue)

    private fun contrast(a: Color, b: Color): Float {
        val first = luminance(a)
        val second = luminance(b)
        val lighter = maxOf(first, second)
        val darker = minOf(first, second)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun assertTextContrast(name: String, foreground: Color, background: Color) {
        val ratio = contrast(foreground, background)
        assertTrue(
            "$name must reach 4.5:1 for readable text on e-paper but was ${"%.2f".format(ratio)}:1",
            ratio >= 4.5f
        )
    }

    @Test
    fun `primary text colors meet wcag aa on every background`() {
        assertTextContrast("NearBlack on PureWhite", NearBlack, PureWhite)
        assertTextContrast("NearBlack on WarmWhite", NearBlack, WarmWhite)
        assertTextContrast("NearBlack on SubtleGray", NearBlack, SubtleGray)
        assertTextContrast("DarkGray on PureWhite", DarkGray, PureWhite)
        assertTextContrast("DarkGray on WarmWhite", DarkGray, WarmWhite)
        assertTextContrast("DarkGray on SubtleGray", DarkGray, SubtleGray)
        assertTextContrast("InvertedText on InvertedBackground", InvertedText, InvertedBackground)
        assertTextContrast("HighContrastErrorText on PureWhite", HighContrastErrorText, PureWhite)
        assertTextContrast("HighContrastErrorText on WarmWhite", HighContrastErrorText, WarmWhite)
        assertTextContrast("HighContrastErrorBorder on PureWhite", HighContrastErrorBorder, PureWhite)
    }

    @Test
    fun `borders and dividers stay visible against the background`() {
        // Hairlines are decorative separators, so they do not need the 4.5:1 text ratio —
        // but they must not disappear. Measured today: 1.61:1 on white and 1.47:1 on the
        // warm background (#CCCCCC vs #F7F5EE), so the floor sits at 1.4. Raising the
        // border contrast further is a visual-tuning decision for Phase 5 hardware
        // testing, recorded in plan.md.
        assertTrue(
            "LightBorderGray must remain visible on white",
            contrast(LightBorderGray, PureWhite) >= 1.4f
        )
        assertTrue(
            "LightBorderGray must remain visible on the warm background",
            contrast(LightBorderGray, WarmWhite) >= 1.4f
        )
    }

    @Test
    fun `every palette color is fully opaque`() {
        val palette = mapOf(
            "PureWhite" to PureWhite,
            "WarmWhite" to WarmWhite,
            "NearBlack" to NearBlack,
            "DarkGray" to DarkGray,
            "MediumGray" to MediumGray,
            "LightBorderGray" to LightBorderGray,
            "SubtleGray" to SubtleGray,
            "InvertedBackground" to InvertedBackground,
            "InvertedText" to InvertedText,
            "HighContrastErrorText" to HighContrastErrorText,
            "HighContrastErrorBorder" to HighContrastErrorBorder
        )
        val translucent = palette.filterValues { it.alpha != 1f }.keys
        assertTrue("Translucent colors ghost on e-paper: $translucent", translucent.isEmpty())
    }

    @Test
    fun `touch target baseline is at least 48dp`() {
        assertTrue(
            "Minimum touch target must stay at 48dp, was ${EpaperDimensions.MinTouchTarget}",
            EpaperDimensions.MinTouchTarget >= 48.dp
        )
        assertTrue(
            "Corner radius must stay small so redraws stay cheap",
            EpaperDimensions.CornerSmall <= 8.dp
        )
    }
}
