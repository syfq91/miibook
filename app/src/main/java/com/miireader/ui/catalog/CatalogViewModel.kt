package com.miireader.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miireader.data.download.DownloadManager
import com.miireader.data.local.entity.CatalogEntity
import com.miireader.data.opds.CatalogException
import com.miireader.data.opds.CatalogFeed
import com.miireader.data.opds.CatalogLink
import com.miireader.data.opds.OpdsCatalogSource
import com.miireader.data.repository.CatalogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Content for the feed view, or nulls when no feed is open. */
private data class BrowseContent(
    val feed: CatalogFeed?,
    val feedUrl: String?,
    val entries: List<CatalogEntryUi>
)

/** Transient flags and the current error message. */
private data class ScreenStatus(
    val isLoadingFeed: Boolean,
    val isAddingCatalog: Boolean,
    val errorMessage: String?
)

/**
 * Drives the OPDS catalog screen: the saved-catalog list, adding and removing catalogs,
 * browsing a feed's sub-feeds and entries, and enqueuing downloads.
 */
class CatalogViewModel(
    private val catalogRepository: CatalogRepository,
    private val catalogSource: OpdsCatalogSource,
    private val downloadManager: DownloadManager
) : ViewModel() {

    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _isAddingCatalog = MutableStateFlow(false)
    private val _isLoadingFeed = MutableStateFlow(false)

    /** The feed currently on screen, or null when showing the saved-catalog list. */
    private val _currentFeed = MutableStateFlow<CatalogFeed?>(null)
    private val _currentFeedUrl = MutableStateFlow<String?>(null)

    private val catalogItemsFlow =
        catalogRepository.getCatalogs().map { catalogs -> catalogs.map { it.toItemUi() } }

    /**
     * The feed view's content: title, URL, and mapped entries. Derived from the two feed
     * flows, and null-valued when no feed is open.
     */
    private val browseFlow = combine(
        _currentFeed,
        _currentFeedUrl
    ) { feed, feedUrl ->
        BrowseContent(
            feed = feed,
            feedUrl = feedUrl,
            entries = feed?.entries.orEmpty().map { entry ->
                CatalogEntryUi(
                    title = entry.title,
                    author = entry.author,
                    summary = entry.summary,
                    acquisitions = entry.acquisitions
                )
            }
        )
    }

    /** Transient screen flags and the current error message. */
    private val statusFlow = combine(
        _isLoadingFeed,
        _isAddingCatalog,
        _errorMessage
    ) { isLoadingFeed, isAddingCatalog, errorMessage ->
        ScreenStatus(isLoadingFeed, isAddingCatalog, errorMessage)
    }

    val uiState: StateFlow<CatalogUiState> = combine(
        catalogItemsFlow,
        browseFlow,
        statusFlow
    ) { catalogs, browse, status ->
        CatalogUiState(
            savedCatalogs = catalogs,
            feedTitle = browse.feed?.title,
            feedUrl = browse.feedUrl,
            entries = browse.entries,
            navigation = browse.feed?.navigation.orEmpty(),
            isLoadingFeed = status.isLoadingFeed,
            isAddingCatalog = status.isAddingCatalog,
            errorMessage = status.errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CatalogUiState()
    )

    /**
     * Validates [url], fetches and parses its feed, and only then saves it. A catalog that
     * cannot be read is never persisted, so the list only ever holds addresses that have
     * worked at least once.
     */
    fun addCatalog(url: String) {
        if (_isAddingCatalog.value) return
        if (!OpdsCatalogSource.isValidUrl(url)) {
            _errorMessage.value = "That doesn't look like a valid catalog address."
            return
        }
        viewModelScope.launch {
            _isAddingCatalog.value = true
            _errorMessage.value = null
            try {
                val feed = catalogSource.loadFeed(url)
                catalogRepository.addCatalog(url = url, title = feed.title)
            } catch (e: CatalogException) {
                _errorMessage.value = e.error.message
            } catch (e: Exception) {
                _errorMessage.value = "Couldn't add that catalog: ${e.message}"
            } finally {
                _isAddingCatalog.value = false
            }
        }
    }

    fun removeCatalog(id: String) {
        viewModelScope.launch {
            _errorMessage.value = null
            try {
                catalogRepository.removeCatalog(id)
            } catch (e: Exception) {
                _errorMessage.value = "Couldn't remove that catalog: ${e.message}"
            }
        }
    }

    /**
     * Opens a feed for browsing. Used for both a saved catalog and a nested sub-feed link,
     * which is why it takes a URL rather than an id.
     */
    fun openFeed(url: String) {
        if (_isLoadingFeed.value) return
        viewModelScope.launch {
            _isLoadingFeed.value = true
            _errorMessage.value = null
            try {
                _currentFeed.value = catalogSource.loadFeed(url)
                _currentFeedUrl.value = url
            } catch (e: CatalogException) {
                _errorMessage.value = e.error.message
            } catch (e: Exception) {
                _errorMessage.value = "Couldn't open that catalog: ${e.message}"
            } finally {
                _isLoadingFeed.value = false
            }
        }
    }

    /**
     * Reloads the feed currently on screen.
     */
    fun refreshFeed() {
        _currentFeedUrl.value?.let { openFeed(it) }
    }

    /**
     * Leaves the feed view. Returns true when a feed was on screen and has been closed,
     * and false when the caller was already at the list level and should pop navigation.
     */
    fun closeFeed(): Boolean {
        if (_currentFeedUrl.value == null) return false
        _currentFeed.value = null
        _currentFeedUrl.value = null
        return true
    }

    /**
     * Enqueues a download for the selected acquisition link.
     *
     * @param url the acquisition link URL
     * @param mediaType the MIME type (e.g., application/epub+zip)
     * @param title optional book title from the catalog entry
     * @param author optional book author from the catalog entry
     */
    fun enqueueDownload(url: String, mediaType: String?, title: String?, author: String?) {
        viewModelScope.launch {
            _errorMessage.value = null
            val link = CatalogLink(
                href = url,
                rel = "http://opds-spec.org/acquisition",
                title = title,
                mediaType = mediaType
            )
            val id = try {
                downloadManager.enqueueDownload(link, title, author)
            } catch (e: Exception) {
                _errorMessage.value = "Couldn't start that download: ${e.message}"
                return@launch
            }
            // The manager declines formats it cannot open; say so instead of doing nothing.
            if (id == null) {
                _errorMessage.value =
                    "That catalog only offers this book in a format the reader can't open yet."
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun CatalogEntity.toItemUi() = CatalogItemUi(
        id = id,
        url = url,
        displayTitle = title ?: hostOf(url)
    )

    /**
     * A readable fallback title for a catalog saved before its feed title was known: the
     * URL's host, or the raw URL when the host is missing.
     */
    private fun hostOf(url: String): String = url
        .substringAfter("://", url)
        .substringBefore('/')
        .substringBefore('?')
        .ifBlank { url }
}
