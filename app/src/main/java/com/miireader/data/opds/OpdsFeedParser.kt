package com.miireader.data.opds

import com.miireader.core.error.AppError
import org.readium.r2.opds.OPDS1Parser
import org.readium.r2.opds.OPDS2Parser
import org.readium.r2.shared.opds.Feed
import org.readium.r2.shared.opds.ParseData
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Url

/**
 * Parses OPDS 1 (Atom) and OPDS 2 (JSON) feeds using Readium's OPDS module, then
 * flattens the result into the stable [CatalogFeed]/[CatalogEntry] domain models.
 *
 * This class does no I/O — it only turns bytes plus a base URL into a model. Fetching
 * the bytes is the responsibility of [OpdsCatalogSource], which lets the whole mapping
 * layer be unit-tested against fixture files.
 */
class OpdsFeedParser {

    sealed interface Result {
        data class Success(val feed: CatalogFeed) : Result
        data class Failure(val error: AppError) : Result
    }

    /**
     * Parses [bytes] as either an OPDS 2 (JSON) or OPDS 1 (Atom) feed, choosing by the
     * leading non-whitespace byte. Both parsers are tried, because feed detection from a
     * bare URL is unreliable and a catalog may serve either format.
     *
     * @param baseUrl the URL the bytes were fetched from, used to resolve relative hrefs.
     */
    fun parse(bytes: ByteArray, baseUrl: String): Result {
        val url = parseUrl(baseUrl)
            ?: return Result.Failure(
                AppError.NetworkError("That doesn't look like a valid catalog address.")
            )

        val head = bytes.copyOfRange(0, minOf(bytes.size, 64)).decodeToString()
        val looksLikeJson = head.trimStart().startsWith('{') || head.trimStart().startsWith('[')

        val candidates = if (looksLikeJson) {
            listOf(CatalogFormat.OPDS2, CatalogFormat.OPDS1)
        } else {
            listOf(CatalogFormat.OPDS1, CatalogFormat.OPDS2)
        }

        var lastError: String? = null
        for (format in candidates) {
            val parsed = runCatching { parseWith(format, bytes, url) }
                .getOrElse { lastError = it.message; null }

            val data = parsed ?: continue
            val feed = data.feed
                ?: return Result.Failure(
                    AppError.BookParseError("That address returned a single book, not a browsable catalog.")
                )

            return Result.Success(mapFeed(feed, url))
        }

        return Result.Failure(
            AppError.BookParseError("Could not read that catalog: ${lastError ?: "unrecognized feed format"}.")
        )
    }

    private enum class CatalogFormat { OPDS1, OPDS2 }

    private fun parseWith(format: CatalogFormat, bytes: ByteArray, url: Url): ParseData =
        when (format) {
            CatalogFormat.OPDS1 -> OPDS1Parser.parse(bytes, url)
            CatalogFormat.OPDS2 -> OPDS2Parser.parse(bytes, url)
        }

    private fun mapFeed(feed: Feed, base: Url): CatalogFeed {
        // In OPDS 1, Readium fills `navigation` from entries that carry no acquisition
        // link; in OPDS 2, from the `navigation` array. Feed-level <link> elements are
        // *not* navigation — they are self/search descriptors — so they are ignored here.
        val navigation = feed.navigation.mapNotNull { it.toCatalogLink(base) }
        val entries = feed.publications.map { it.toCatalogEntry(base) }

        // Facets carry paging and filtering links, so surface them as navigable too.
        val facetLinks = feed.facets.flatMap { facet ->
            facet.links.mapNotNull { it.toCatalogLink(base) }
        }

        return CatalogFeed(
            title = feed.title,
            navigation = navigation,
            entries = entries,
            facetLinks = facetLinks
        )
    }

    private fun Publication.toCatalogEntry(base: Url): CatalogEntry {
        val acquisitionLinks = links.filter { it.isAcquisition }.mapNotNull { it.toCatalogLink(base) }

        val subfeeds = links
            .filter { !it.isAcquisition && it.title != null }
            .mapNotNull { it.toCatalogLink(base) }

        return CatalogEntry(
            title = metadata.title ?: "Untitled",
            // Readium models authors as Contributors, whose name lives in a LocalizedString.
            author = metadata.authors.firstOrNull()?.localizedName?.string,
            summary = metadata.description,
            acquisitions = acquisitionLinks,
            subfeeds = subfeeds
        )
    }

    private val Link.isAcquisition: Boolean
        get() = rels.any { it.startsWith("http://opds-spec.org/acquisition") }

    private fun Link.toCatalogLink(base: Url): CatalogLink? {
        val absolute = url(base).toString()
        if (absolute.isBlank()) return null
        return CatalogLink(
            href = absolute,
            rel = rels.firstOrNull(),
            title = title,
            mediaType = mediaType?.toString()?.lowercase()
        )
    }

    /**
     * Readium's `AbsoluteUrl` is built on `android.net.Uri`, so this needs a real Android
     * runtime (Robolectric in tests) and returns null for anything that is not an
     * absolute address.
     */
    private fun parseUrl(value: String): Url? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        return AbsoluteUrl(trimmed)
    }
}
