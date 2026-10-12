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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miireader.ui.theme.EpaperDimensions
import com.miireader.ui.theme.InvertedBackground
import com.miireader.ui.theme.InvertedText
import com.miireader.ui.theme.NearBlack

enum class EpaperButtonStyle {
    PRIMARY,
    OUTLINE,
    TEXT
}

@Composable
fun EpaperButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: EpaperButtonStyle = EpaperButtonStyle.PRIMARY,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }

    val (bgColor, textColor, border) = when (style) {
        EpaperButtonStyle.PRIMARY -> {
            if (enabled) {
                Triple(InvertedBackground, InvertedText, null)
            } else {
                Triple(Color(0xFFCCCCCC), Color(0xFF777777), null)
            }
        }
        EpaperButtonStyle.OUTLINE -> {
            if (enabled) {
                Triple(Color.Transparent, NearBlack, BorderStroke(EpaperDimensions.BorderMedium, NearBlack))
            } else {
                Triple(Color.Transparent, Color(0xFF888888), BorderStroke(EpaperDimensions.BorderThin, Color(0xFF888888)))
            }
        }
        EpaperButtonStyle.TEXT -> {
            if (enabled) {
                Triple(Color.Transparent, NearBlack, null)
            } else {
                Triple(Color.Transparent, Color(0xFF888888), null)
            }
        }
    }

    Surface(
        modifier = modifier
            .defaultMinSize(
                minWidth = 64.dp,
                minHeight = EpaperDimensions.MinTouchTarget
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null, // No ripple or animation on e-paper
                onClick = onClick
            ),
        shape = RoundedCornerShape(EpaperDimensions.CornerSmall),
        color = bgColor,
        border = border,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = textColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
