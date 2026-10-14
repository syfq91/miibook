package com.miireader.di

import android.content.Context
import com.miireader.core.dispatchers.CoroutineDispatchers
import com.miireader.core.dispatchers.DefaultCoroutineDispatchers
import com.miireader.core.error.DefaultErrorHandler
import com.miireader.core.error.ErrorHandler
import com.miireader.core.input.PageTurnKeys
import com.miireader.data.epub.EpubMetadataExtractor
import com.miireader.data.epub.EpubPublicationRepository
import com.miireader.data.epub.ReadiumEpubMetadataExtractor
import com.miireader.data.epub.ReadiumEpubPublicationRepository
import com.miireader.data.local.AppDatabase
import com.miireader.data.preferences.DefaultUserPreferencesRepository
import com.miireader.data.preferences.UserPreferencesRepository
import com.miireader.data.preferences.dataStore
import com.miireader.data.repository.BookImporter
import com.miireader.data.repository.BookRepository
import com.miireader.data.repository.BookmarkRepository
import com.miireader.data.repository.DefaultBookImporter
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
    val bookImporter: BookImporter
    val publicationRepository: EpubPublicationRepository
    val progressRepository: ProgressRepository
    val bookmarkRepository: BookmarkRepository
    val epaperController: EpaperController

    /**
     * App-scoped bridge between hardware page-turn keys and whichever reader is open.
     * Lives here rather than on the Activity so the reader registers against something
     * that survives configuration changes.
     */
    val pageTurnKeys: PageTurnKeys
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

    override val bookImporter: BookImporter by lazy {
        DefaultBookImporter(
            context = context,
            bookRepository = bookRepository,
            metadataExtractor = epubMetadataExtractor,
            dispatchers = dispatchers
        )
    }

    private val epubMetadataExtractor: EpubMetadataExtractor by lazy {
        ReadiumEpubMetadataExtractor(context, dispatchers)
    }

    override val publicationRepository: EpubPublicationRepository by lazy {
        ReadiumEpubPublicationRepository(context, dispatchers)
    }

    override val pageTurnKeys: PageTurnKeys by lazy { PageTurnKeys() }

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
