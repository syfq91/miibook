package com.miireader.ui.settings

import com.miireader.core.model.SortOrder

data class SettingsUiState(
    val warmBackground: Boolean = false,
    val fontSizeSp: Int = 18,
    val animationsEnabled: Boolean = false,
    val refreshIntervalPages: Int = 10,
    val defaultSortOrder: SortOrder = SortOrder.LAST_OPENED,
    val deviceManufacturer: String = "",
    val deviceModel: String = "",
    val isEpaperDevice: Boolean = false
)
