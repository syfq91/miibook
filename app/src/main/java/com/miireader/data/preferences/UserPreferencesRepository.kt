package com.miireader.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.miireader.core.model.FilterMode
import com.miireader.core.model.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

data class UserPreferences(
    val warmBackground: Boolean = false,
    val fontSizeSp: Int = 18,
    val animationsEnabled: Boolean = false,
    val refreshIntervalPages: Int = 10,
    val sortOrder: SortOrder = SortOrder.LAST_OPENED,
    val filterMode: FilterMode = FilterMode.ALL
)

interface UserPreferencesRepository {
    val preferences: Flow<UserPreferences>
    suspend fun setWarmBackground(enabled: Boolean)
    suspend fun setFontSizeSp(sizeSp: Int)
    suspend fun setAnimationsEnabled(enabled: Boolean)
    suspend fun setRefreshIntervalPages(pages: Int)
    suspend fun setSortOrder(order: SortOrder)
    suspend fun setFilterMode(mode: FilterMode)
}

class DefaultUserPreferencesRepository(
    private val dataStore: DataStore<Preferences>
) : UserPreferencesRepository {

    private object PreferencesKeys {
        val WARM_BACKGROUND = booleanPreferencesKey("warm_background")
        val FONT_SIZE_SP = intPreferencesKey("font_size_sp")
        val ANIMATIONS_ENABLED = booleanPreferencesKey("animations_enabled")
        val REFRESH_INTERVAL_PAGES = intPreferencesKey("refresh_interval_pages")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val FILTER_MODE = stringPreferencesKey("filter_mode")
    }

    override val preferences: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            UserPreferences(
                warmBackground = preferences[PreferencesKeys.WARM_BACKGROUND] ?: false,
                fontSizeSp = preferences[PreferencesKeys.FONT_SIZE_SP] ?: 18,
                animationsEnabled = preferences[PreferencesKeys.ANIMATIONS_ENABLED] ?: false,
                refreshIntervalPages = preferences[PreferencesKeys.REFRESH_INTERVAL_PAGES] ?: 10,
                sortOrder = SortOrder.fromName(preferences[PreferencesKeys.SORT_ORDER]),
                filterMode = FilterMode.fromName(preferences[PreferencesKeys.FILTER_MODE])
            )
        }

    override suspend fun setWarmBackground(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.WARM_BACKGROUND] = enabled }
    }

    override suspend fun setFontSizeSp(sizeSp: Int) {
        dataStore.edit { it[PreferencesKeys.FONT_SIZE_SP] = sizeSp.coerceIn(12, 36) }
    }

    override suspend fun setAnimationsEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.ANIMATIONS_ENABLED] = enabled }
    }

    override suspend fun setRefreshIntervalPages(pages: Int) {
        dataStore.edit { it[PreferencesKeys.REFRESH_INTERVAL_PAGES] = pages.coerceIn(0, 50) }
    }

    override suspend fun setSortOrder(order: SortOrder) {
        dataStore.edit { it[PreferencesKeys.SORT_ORDER] = order.name }
    }

    override suspend fun setFilterMode(mode: FilterMode) {
        dataStore.edit { it[PreferencesKeys.FILTER_MODE] = mode.name }
    }
}
