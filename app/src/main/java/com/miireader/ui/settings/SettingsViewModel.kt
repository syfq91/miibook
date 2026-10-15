package com.miireader.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miireader.core.model.SortOrder
import com.miireader.data.preferences.UserPreferencesRepository
import com.miireader.platform.epaper.EpaperController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val epaperController: EpaperController
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = userPreferencesRepository.preferences
        .map { prefs ->
            SettingsUiState(
                warmBackground = prefs.warmBackground,
                fontSizeSp = prefs.fontSizeSp,
                defaultSortOrder = prefs.sortOrder,
                deviceManufacturer = epaperController.deviceManufacturer,
                deviceModel = epaperController.deviceModel,
                isEpaperDevice = epaperController.isEpaperDevice
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsUiState(
                deviceManufacturer = epaperController.deviceManufacturer,
                deviceModel = epaperController.deviceModel,
                isEpaperDevice = epaperController.isEpaperDevice
            )
        )

    fun onWarmBackgroundChanged(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setWarmBackground(enabled)
        }
    }

    fun onFontSizeChanged(sizeSp: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setFontSizeSp(sizeSp)
        }
    }

    fun onSortOrderChanged(order: SortOrder) {
        viewModelScope.launch {
            userPreferencesRepository.setSortOrder(order)
        }
    }
}
