package com.miireader.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.miireader.ui.theme.EpaperDimensions
import com.miireader.ui.theme.NearBlack

@Composable
fun EpaperCard(
    modifier: Modifier = Modifier,
    borderWidth: Dp = EpaperDimensions.BorderThin,
    borderColor: Color = NearBlack,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null, // No ripple animation on e-paper
            onClick = onClick
        )
    } else {
        Modifier
    }

    Surface(
        modifier = modifier.then(clickableModifier),
        shape = RoundedCornerShape(EpaperDimensions.CornerSmall),
        color = backgroundColor,
        border = BorderStroke(borderWidth, borderColor),
        shadowElevation = 0.dp
    ) {
        Box(modifier = Modifier.padding(EpaperDimensions.PaddingMedium)) {
            content()
        }
    }
}
