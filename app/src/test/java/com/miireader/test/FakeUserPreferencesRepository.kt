package com.miireader.test

import com.miireader.core.model.FilterMode
import com.miireader.core.model.ReaderTheme
import com.miireader.core.model.SortOrder
import com.miireader.data.preferences.UserPreferences
import com.miireader.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeUserPreferencesRepository(
    initialPreferences: UserPreferences = UserPreferences()
) : UserPreferencesRepository {

    private val _preferences = MutableStateFlow(initialPreferences)
    override val preferences: Flow<UserPreferences> = _preferences.asStateFlow()

    override suspend fun setWarmBackground(enabled: Boolean) {
        _preferences.value = _preferences.value.copy(warmBackground = enabled)
    }

    override suspend fun setFontSizeSp(sizeSp: Int) {
        _preferences.value = _preferences.value.copy(fontSizeSp = sizeSp)
    }

    override suspend fun setReaderTheme(theme: ReaderTheme) {
        _preferences.value = _preferences.value.copy(readerTheme = theme)
    }

    override suspend fun setSortOrder(order: SortOrder) {
        _preferences.value = _preferences.value.copy(sortOrder = order)
    }

    override suspend fun setFilterMode(mode: FilterMode) {
        _preferences.value = _preferences.value.copy(filterMode = mode)
    }
}
