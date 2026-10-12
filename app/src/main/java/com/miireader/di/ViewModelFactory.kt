package com.miireader.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.miireader.ui.bookshelf.BookshelfViewModel
import com.miireader.ui.settings.SettingsViewModel

class ViewModelFactory(
    private val container: AppContainer
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(BookshelfViewModel::class.java) -> {
                BookshelfViewModel(
                    bookRepository = container.bookRepository,
                    progressRepository = container.progressRepository,
                    userPreferencesRepository = container.userPreferencesRepository,
                    errorHandler = container.errorHandler
                ) as T
            }
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(
                    userPreferencesRepository = container.userPreferencesRepository,
                    epaperController = container.epaperController
                ) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
