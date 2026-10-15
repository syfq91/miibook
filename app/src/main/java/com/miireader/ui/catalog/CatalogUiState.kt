package com.miireader.ui.catalog

import com.miireader.data.opds.CatalogLink

/**
 * A saved catalog as shown in the catalog list.
 *
 * [displayTitle] is never blank: it prefers the user's label, then the host, so the list
 * stays readable even for a catalog that has not been opened yet.
 */
data class CatalogItemUi(
    val id: String,
    val url: String,
    val displayTitle: String
)

/** One book entry, with its downloadable formats. */
data class CatalogEntryUi(
    val title: String,
    val author: String?,
    val summary: String?,
    val acquisitions: List<CatalogLink>
)

/**
 * Immutable UI state for the catalog screen.
 *
 * @property savedCatalogs the user's saved catalog addresses.
 * @property feedTitle title of the feed currently being browsed, or null at the list level.
 * @property feedUrl address of the feed currently being browsed, used as the base for
 *           relative links and to detect when a cached feed is stale.
 * @property entries book entries of the current feed.
 * @property navigation sub-feeds of the current feed.
 * @property isLoadingFeed true while a feed request is in flight.
 * @property isAddingCatalog true while a new catalog URL is being validated and fetched.
 */
data class CatalogUiState(
    val savedCatalogs: List<CatalogItemUi> = emptyList(),
    val feedTitle: String? = null,
    val feedUrl: String? = null,
    val entries: List<CatalogEntryUi> = emptyList(),
    val navigation: List<CatalogLink> = emptyList(),
    val isLoadingFeed: Boolean = false,
    val isAddingCatalog: Boolean = false,
    val errorMessage: String? = null
) {
    /** True when a feed is on screen rather than the saved-catalog list. */
    val isBrowsingFeed: Boolean get() = feedUrl != null

    /** An empty state for a feed that loaded but has nothing browsable on it. */
    val isFeedEmpty: Boolean
        get() = isBrowsingFeed && !isLoadingFeed && entries.isEmpty() && navigation.isEmpty()
}
