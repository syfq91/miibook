package com.miireader.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miireader.core.model.SortOrder
import com.miireader.ui.components.EpaperButton
import com.miireader.ui.components.EpaperButtonStyle
import com.miireader.ui.components.EpaperCard
import com.miireader.ui.components.EpaperDivider
import com.miireader.ui.components.EpaperSwitch
import com.miireader.ui.components.EpaperTopBar
import com.miireader.ui.theme.DarkGray
import com.miireader.ui.theme.EpaperDimensions
import com.miireader.ui.theme.InvertedBackground
import com.miireader.ui.theme.InvertedText
import com.miireader.ui.theme.LightBorderGray
import com.miireader.ui.theme.NearBlack

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onNavigateBack: () -> Unit,
    onWarmBackgroundChanged: (Boolean) -> Unit,
    onFontSizeChanged: (Int) -> Unit,
    onAnimationsToggled: (Boolean) -> Unit,
    onRefreshIntervalChanged: (Int) -> Unit,
    onSortOrderChanged: (SortOrder) -> Unit,
    onTriggerFullRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(0.dp)
    ) {
        EpaperTopBar(
            title = "Settings",
            navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
            navigationIconContentDescription = "Back to Bookshelf",
            onNavigationClick = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Section 1: E-Paper Display
            SettingsSectionHeader(title = "E-Paper & Display")

            EpaperCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    // Background Tone: Pure White vs Warm White
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Warm White Background",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = NearBlack
                            )
                            Text(
                                text = if (uiState.warmBackground) "Warm tone active (#F7F5EE)" else "Pure monochrome active (#FFFFFF)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = DarkGray
                            )
                        }
                        EpaperSwitch(
                            checked = uiState.warmBackground,
                            onCheckedChange = onWarmBackgroundChanged
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    EpaperDivider(thickness = EpaperDimensions.BorderThin)
                    Spacer(modifier = Modifier.height(14.dp))

                    // UI Animations
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Allow UI Animations",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = NearBlack
                            )
                            Text(
                                text = "Keep disabled on e-paper to avoid ghosting and flicker",
                                style = MaterialTheme.typography.bodyMedium,
                                color = DarkGray
                            )
                        }
                        EpaperSwitch(
                            checked = uiState.animationsEnabled,
                            onCheckedChange = onAnimationsToggled
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    EpaperDivider(thickness = EpaperDimensions.BorderThin)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Full Refresh Page Interval
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Full Screen Refresh",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = NearBlack
                            )
                            Text(
                                text = if (uiState.refreshIntervalPages > 0) "Every ${uiState.refreshIntervalPages} pages" else "Manual refresh only",
                                style = MaterialTheme.typography.bodyMedium,
                                color = DarkGray
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(0, 5, 10, 20).forEach { pages ->
                                val isSelected = uiState.refreshIntervalPages == pages
                                Surface(
                                    modifier = Modifier.clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                        onClick = { onRefreshIntervalChanged(pages) }
                                    ),
                                    shape = RoundedCornerShape(EpaperDimensions.CornerSmall),
                                    color = if (isSelected) InvertedBackground else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(EpaperDimensions.BorderThin, NearBlack)
                                ) {
                                    Text(
                                        text = if (pages == 0) "Off" else "$pages",
                                        color = if (isSelected) InvertedText else NearBlack,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    EpaperDivider(thickness = EpaperDimensions.BorderThin)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Manual Full Refresh Action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Flash Refresh Now",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = NearBlack
                            )
                            Text(
                                text = "Clears residual ghosting immediately",
                                style = MaterialTheme.typography.bodyMedium,
                                color = DarkGray
                            )
                        }
                        EpaperButton(
                            text = "Refresh",
                            onClick = onTriggerFullRefresh,
                            style = EpaperButtonStyle.OUTLINE
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 2: Reading Defaults
            SettingsSectionHeader(title = "Reading Defaults")

            EpaperCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Default Font Size",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = NearBlack
                            )
                            Text(
                                text = "${uiState.fontSizeSp} sp",
                                style = MaterialTheme.typography.bodyMedium,
                                color = DarkGray
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            EpaperButton(
                                text = "A -",
                                onClick = { onFontSizeChanged((uiState.fontSizeSp - 2).coerceAtLeast(12)) },
                                style = EpaperButtonStyle.OUTLINE,
                                enabled = uiState.fontSizeSp > 12
                            )
                            EpaperButton(
                                text = "A +",
                                onClick = { onFontSizeChanged((uiState.fontSizeSp + 2).coerceAtMost(36)) },
                                style = EpaperButtonStyle.OUTLINE,
                                enabled = uiState.fontSizeSp < 36
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(EpaperDimensions.BorderThin, LightBorderGray),
                        shape = RoundedCornerShape(EpaperDimensions.CornerSmall)
                    ) {
                        Text(
                            text = "Sample EPUB text at ${uiState.fontSizeSp}sp. The quick brown fox jumps over the lazy dog.",
                            fontSize = uiState.fontSizeSp.sp,
                            color = NearBlack,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 3: Library Defaults
            SettingsSectionHeader(title = "Library Defaults")

            EpaperCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "Default Bookshelf Sort",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NearBlack
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SortOrder.entries.forEach { order ->
                            val isSelected = uiState.defaultSortOrder == order
                            Surface(
                                modifier = Modifier.clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = { onSortOrderChanged(order) }
                                ),
                                shape = RoundedCornerShape(EpaperDimensions.CornerSmall),
                                color = if (isSelected) InvertedBackground else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    if (isSelected) EpaperDimensions.BorderMedium else EpaperDimensions.BorderThin,
                                    NearBlack
                                )
                            ) {
                                Text(
                                    text = order.label,
                                    color = if (isSelected) InvertedText else NearBlack,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 4: Device & Diagnostics
            SettingsSectionHeader(title = "Device & Diagnostics")

            EpaperCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DeviceInfoRow(label = "Hardware Manufacturer", value = uiState.deviceManufacturer.ifBlank { "Standard Android" })
                    DeviceInfoRow(label = "Hardware Model", value = uiState.deviceModel.ifBlank { "Generic Device" })
                    DeviceInfoRow(
                        label = "E-Paper Profile",
                        value = if (uiState.isEpaperDevice) "Detected (E-Ink device profile active)" else "Standard Profile (Compatibility mode)"
                    )
                    DeviceInfoRow(label = "Application Version", value = "0.1.0-phase1")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = DarkGray,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun DeviceInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = DarkGray)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = NearBlack)
    }
}
