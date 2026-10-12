package com.miireader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.miireader.ui.theme.DarkGray
import com.miireader.ui.theme.EpaperDimensions
import com.miireader.ui.theme.LightBorderGray
import com.miireader.ui.theme.NearBlack

@Composable
fun EpaperProgressBar(
    progress: Float, // 0.0f to 1.0f
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    barColor: Color = NearBlack,
    trackColor: Color = Color.Transparent,
    borderColor: Color = LightBorderGray
) {
    val clampedProgress = progress.coerceIn(0.0f, 1.0f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .border(
                width = EpaperDimensions.BorderThin,
                color = borderColor,
                shape = RoundedCornerShape(0.dp)
            )
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            // Draw background track if specified
            if (trackColor != Color.Transparent) {
                drawRect(
                    color = trackColor,
                    size = size
                )
            }
            // Draw solid static progress fill without animation
            if (clampedProgress > 0f) {
                drawRect(
                    color = barColor,
                    size = Size(width = size.width * clampedProgress, height = size.height)
                )
            }
        }
    }
}
