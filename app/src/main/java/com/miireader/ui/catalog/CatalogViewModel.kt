package com.miireader.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miireader.data.download.DownloadManager
import com.miireader.data.local.entity.CatalogEntity
import com.miireader.data.opds.CatalogCredentials
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

    /** The saved catalog being edited in the add form, or null when adding a new one. */
    private val _editingCatalog = MutableStateFlow<CatalogEditUi?>(null)

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
        statusFlow,
        _editingCatalog
    ) { catalogs, browse, status, editingCatalog ->
        CatalogUiState(
            savedCatalogs = catalogs,
            feedTitle = browse.feed?.title,
            feedUrl = browse.feedUrl,
            entries = browse.entries,
            navigation = browse.feed?.navigation.orEmpty(),
            isLoadingFeed = status.isLoadingFeed,
            isAddingCatalog = status.isAddingCatalog,
            editingCatalog = editingCatalog,
            errorMessage = status.errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CatalogUiState()
    )

    /**
     * Validates [url] and saves it as a new catalog, fetching its feed (using optional
     * HTTP Basic [username]/[password]) and taking the title from it. A catalog that
     * cannot be read is never persisted, so the list only ever holds addresses that have
     * worked at least once.
     */
    fun addCatalog(url: String, username: String? = null, password: String? = null) {
        saveCatalog(catalogId = null, url = url, username = username, password = password)
    }

    /**
     * Loads the saved catalog [id] into the form so its address or credentials can be
     * changed. The stored password is deliberately not copied into the form; the screen
     * only learns whether one exists.
     */
    fun startEditingCatalog(id: String) {
        viewModelScope.launch {
            _errorMessage.value = null
            val catalog = catalogRepository.getCatalogById(id) ?: return@launch
            _editingCatalog.value = CatalogEditUi(
                id = catalog.id,
                url = catalog.url,
                username = catalog.username.orEmpty(),
                displayTitle = catalog.title ?: hostOf(catalog.url),
                hasPassword = !catalog.password.isNullOrEmpty()
            )
        }
    }

    /** Leaves edit mode without touching the saved catalog. */
    fun cancelEditingCatalog() {
        _editingCatalog.value = null
        _errorMessage.value = null
    }

    /** Saves the form contents as a change to the catalog currently being edited. */
    fun saveEditedCatalog(url: String, username: String?, password: String?) {
        val id = _editingCatalog.value?.id ?: return
        saveCatalog(catalogId = id, url = url, username = username, password = password)
    }

    /**
     * The one path that writes a catalog, whether it is new or being edited.
     *
     * The address is re-fetched before it is saved, so an edit that points at a dead feed
     * fails loudly instead of quietly breaking the saved entry. A blank password in edit
     * mode means "keep the stored one" as long as the username is unchanged; the form can
     * never show the old password, and asking for it again on every edit would be hostile.
     * A changed username is treated as a new account, so the old password is not reused.
     */
    private fun saveCatalog(
        catalogId: String?,
        url: String,
        username: String?,
        password: String?
    ) {
        if (_isAddingCatalog.value) return
        if (!OpdsCatalogSource.isValidUrl(url)) {
            _errorMessage.value = "That doesn't look like a valid catalog address."
            return
        }
        viewModelScope.launch {
            _isAddingCatalog.value = true
            _errorMessage.value = null
            val typedUsername = username?.trim()?.takeIf { it.isNotEmpty() }
            try {
                val existing = catalogId?.let { catalogRepository.getCatalogById(it) }
                val resolvedPassword = if (
                    typedUsername != null &&
                    password.isNullOrEmpty() &&
                    existing?.username == typedUsername
                ) {
                    existing.password
                } else {
                    password
                }
                val credentials = CatalogCredentials.ofOrNull(typedUsername, resolvedPassword)
                val feed = catalogSource.loadFeed(url, credentials)
                if (catalogId == null) {
                    catalogRepository.addCatalog(
                        url = url,
                        title = feed.title,
                        username = credentials?.username,
                        password = credentials?.password
                    )
                } else {
                    val updated = catalogRepository.updateCatalog(
                        id = catalogId,
                        url = url,
                        title = feed.title,
                        username = credentials?.username,
                        password = credentials?.password
                    )
                    if (updated == null) {
                        // The row disappeared while the feed was being fetched (removed on
                        // another screen, or the database was cleared). Do not resurrect it.
                        _editingCatalog.value = null
                        _errorMessage.value = "That catalog is no longer saved. Add it again."
                        return@launch
                    }
                    // Only leave the form if it is still showing this catalog: the user
                    // may have started editing another one while the save was in flight.
                    if (_editingCatalog.value?.id == catalogId) {
                        _editingCatalog.value = null
                    }
                }
            } catch (e: CatalogException) {
                _errorMessage.value = e.error.message
            } catch (e: Exception) {
                _errorMessage.value = if (catalogId == null) {
                    "Couldn't add that catalog: ${e.message ?: "unknown error"}"
                } else {
                    "Couldn't save that catalog: ${e.message ?: "unknown error"}"
                }
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
                // Removing the row being edited must not leave the form pointing at it.
                if (_editingCatalog.value?.id == id) _editingCatalog.value = null
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
                // Nested feeds are absolute links with no catalog id attached, so the
                // saved catalog is located by origin/path before the request is made.
                _currentFeed.value = catalogSource.loadFeed(url, credentialsFor(url))
                _currentFeedUrl.value = url
            } catch (e: CatalogException) {
                _errorMessage.value = e.error.message
            } catch (e: Exception) {
                _errorMessage.value = "Couldn't open that catalog: ${e.message ?: "unknown error"}"
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
                downloadManager.enqueueDownload(link, title, author, credentialsFor(url))
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

    /** Credentials saved for the catalog that owns [url], or null for a public feed. */
    private suspend fun credentialsFor(url: String): CatalogCredentials? =
        catalogRepository.findCatalogForUrl(url)
            ?.let { CatalogCredentials.ofOrNull(it.username, it.password) }

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
