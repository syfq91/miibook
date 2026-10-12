package com.miireader.di

import android.content.Context
import com.miireader.core.dispatchers.CoroutineDispatchers
import com.miireader.core.dispatchers.DefaultCoroutineDispatchers
import com.miireader.core.error.DefaultErrorHandler
import com.miireader.core.error.ErrorHandler
import com.miireader.data.local.AppDatabase
import com.miireader.data.preferences.DefaultUserPreferencesRepository
import com.miireader.data.preferences.UserPreferencesRepository
import com.miireader.data.preferences.dataStore
import com.miireader.data.repository.BookRepository
import com.miireader.data.repository.BookmarkRepository
import com.miireader.data.repository.DefaultBookRepository
import com.miireader.data.repository.DefaultBookmarkRepository
import com.miireader.data.repository.DefaultProgressRepository
import com.miireader.data.repository.ProgressRepository
import com.miireader.platform.epaper.DefaultEpaperController
import com.miireader.platform.epaper.EpaperController

/**
 * Dependency container providing singletons across the application lifecycle.
 */
interface AppContainer {
    val context: Context
    val dispatchers: CoroutineDispatchers
    val errorHandler: ErrorHandler
    val database: AppDatabase
    val userPreferencesRepository: UserPreferencesRepository
    val bookRepository: BookRepository
    val progressRepository: ProgressRepository
    val bookmarkRepository: BookmarkRepository
    val epaperController: EpaperController
}

class DefaultAppContainer(override val context: Context) : AppContainer {
    override val dispatchers: CoroutineDispatchers by lazy {
        DefaultCoroutineDispatchers()
    }

    override val errorHandler: ErrorHandler by lazy {
        DefaultErrorHandler()
    }

    override val database: AppDatabase by lazy {
        AppDatabase.create(context)
    }

    override val userPreferencesRepository: UserPreferencesRepository by lazy {
        DefaultUserPreferencesRepository(context.dataStore)
    }

    override val bookRepository: BookRepository by lazy {
        DefaultBookRepository(database.bookDao(), dispatchers)
    }

    override val progressRepository: ProgressRepository by lazy {
        DefaultProgressRepository(database.readingProgressDao(), dispatchers)
    }

    override val bookmarkRepository: BookmarkRepository by lazy {
        DefaultBookmarkRepository(database.bookmarkDao(), dispatchers)
    }

    override val epaperController: EpaperController by lazy {
        DefaultEpaperController()
    }
}
