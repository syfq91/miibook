package com.miireader.data.opds

/**
 * Stable domain models for the OPDS catalog browser.
 *
 * These deliberately hide Readium's parsed types so the UI layer depends on plain
 * data classes rather than on `org.readium.r2.*` internals. Readium's models hold
 * references to `Url`/`Manifest`/`Publication` types that are expensive to hold onto
 * and awkward to test against, so every parsed feed is flattened into these shapes as
 * soon as it is read.
 */

/**
 * A single downloadable (or navigable) link inside an OPDS feed.
 *
 * [href] is always stored resolved to an absolute URL string, so a relative link in a
 * feed can be fetched later without re-deriving the feed's base URL.
 */
data class CatalogLink(
    val href: String,
    val rel: String?,
    val title: String?,
    /** Lowercase MIME type (for example `application/epub+zip`), or null if unknown. */
    val mediaType: String?
)

/** A book or item entry within a catalog feed, with its available formats. */
data class CatalogEntry(
    val title: String,
    val author: String?,
    val summary: String?,
    /** Acquisition links, filtered down to formats we can actually download. */
    val acquisitions: List<CatalogLink>,
    /** Any links that open a sub-feed (nested catalogs) for this entry. */
    val subfeeds: List<CatalogLink>
)

/**
 * One screen worth of catalog content: a feed's title plus the things on it —
 * navigable sub-feeds, book entries, and (in OPDS 2) facet links used for paging.
 */
data class CatalogFeed(
    val title: String,
    val navigation: List<CatalogLink>,
    val entries: List<CatalogEntry>,
    val facetLinks: List<CatalogLink>
)
