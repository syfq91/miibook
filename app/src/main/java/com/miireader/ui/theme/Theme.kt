package com.miireader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalWarmBackground = staticCompositionLocalOf { false }

@Composable
fun MiiReaderTheme(
    warmBackground: Boolean = false,
    content: @Composable () -> Unit
) {
    val backgroundColor = if (warmBackground) WarmWhite else PureWhite

    val colorScheme = lightColorScheme(
        primary = NearBlack,
        onPrimary = PureWhite,
        primaryContainer = SubtleGray,
        onPrimaryContainer = NearBlack,
        secondary = DarkGray,
        onSecondary = PureWhite,
        secondaryContainer = SubtleGray,
        onSecondaryContainer = NearBlack,
        background = backgroundColor,
        onBackground = NearBlack,
        surface = backgroundColor,
        onSurface = NearBlack,
        surfaceVariant = SubtleGray,
        onSurfaceVariant = DarkGray,
        outline = NearBlack,
        outlineVariant = LightBorderGray
    )

    CompositionLocalProvider(
        LocalWarmBackground provides warmBackground
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = EpaperTypography,
            content = content
        )
    }
}
