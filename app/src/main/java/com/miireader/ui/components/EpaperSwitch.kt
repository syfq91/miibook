package com.miireader.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miireader.ui.theme.DarkGray
import com.miireader.ui.theme.EpaperDimensions
import com.miireader.ui.theme.InvertedBackground
import com.miireader.ui.theme.InvertedText
import com.miireader.ui.theme.NearBlack

@Composable
fun EpaperSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier
            .defaultMinSize(minWidth = 64.dp, minHeight = EpaperDimensions.MinTouchTarget)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null, // No animation
                onClick = { onCheckedChange(!checked) }
            ),
        shape = RoundedCornerShape(EpaperDimensions.CornerSmall),
        color = if (checked) InvertedBackground else androidx.compose.ui.graphics.Color.Transparent,
        border = BorderStroke(EpaperDimensions.BorderMedium, NearBlack),
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (checked) "[ ON ]" else "[ OFF ]",
                color = if (checked) InvertedText else DarkGray,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}
