package com.miireader.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.miireader.ui.bookshelf.BookshelfViewModel
import com.miireader.ui.catalog.CatalogViewModel
import com.miireader.ui.downloads.DownloadsViewModel
import com.miireader.ui.reader.ReaderViewModel
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
                    bookImporter = container.bookImporter,
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
            modelClass.isAssignableFrom(CatalogViewModel::class.java) -> {
                CatalogViewModel(
                    catalogRepository = container.catalogRepository,
                    catalogSource = container.opdsCatalogSource,
                    downloadManager = container.downloadManager
                ) as T
            }
            modelClass.isAssignableFrom(DownloadsViewModel::class.java) -> {
                DownloadsViewModel(
                    downloadRepository = container.downloadRepository,
                    downloadManager = container.downloadManager,
                    errorHandler = container.errorHandler
                ) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    /**
     * Builds a [ReaderViewModel] for the book identified by [bookId].
     *
     * The book id comes from the navigation route, which is not available to [create],
     * so the reader is assembled here instead of through the shared factory.
     */
    fun readerViewModelFactory(bookId: String): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(ReaderViewModel::class.java)) {
                    "Unknown ViewModel class: ${modelClass.name}"
                }
                return ReaderViewModel(
                    bookId = bookId,
                    bookRepository = container.bookRepository,
                    publicationRepository = container.publicationRepository,
                    progressRepository = container.progressRepository,
                    bookmarkRepository = container.bookmarkRepository,
                    userPreferencesRepository = container.userPreferencesRepository,
                    dispatchers = container.dispatchers,
                    errorHandler = container.errorHandler,
                    pageTurnKeys = container.pageTurnKeys
                ) as T
            }
        }
}
