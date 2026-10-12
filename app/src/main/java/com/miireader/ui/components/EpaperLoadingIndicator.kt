package com.miireader.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miireader.ui.theme.DarkGray
import com.miireader.ui.theme.EpaperDimensions
import com.miireader.ui.theme.LightBorderGray

/**
 * Static loading indicator adhering to e-paper guidelines:
 * No continuous spinning animations or shimmer. Explicit static text indicator.
 */
@Composable
fun EpaperLoadingIndicator(
    text: String = "Loading...",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .border(
                width = EpaperDimensions.BorderThin,
                color = LightBorderGray,
                shape = RoundedCornerShape(EpaperDimensions.CornerSmall)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "[ $text ]",
            color = DarkGray,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
