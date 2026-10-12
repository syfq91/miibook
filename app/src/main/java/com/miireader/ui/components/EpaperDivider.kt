package com.miireader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.miireader.ui.theme.EpaperDimensions
import com.miireader.ui.theme.LightBorderGray

@Composable
fun EpaperDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = EpaperDimensions.BorderThin,
    color: Color = LightBorderGray
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness)
    ) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = size.height
        )
    }
}
