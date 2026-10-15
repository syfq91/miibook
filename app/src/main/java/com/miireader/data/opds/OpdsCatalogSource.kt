package com.miireader.data.opds

import com.miireader.core.error.AppError

/**
 * Fetches and parses OPDS feeds into the stable [CatalogFeed] model.
 *
 * The HTTP client is injected so this class can be unit-tested against a fake instead of
 * the network; [ReadiumOpdsHttpClient] is the production implementation.
 */
class OpdsCatalogSource(
    private val httpClient: OpdsHttpClient,
    private val parser: OpdsFeedParser = OpdsFeedParser()
) {

    /**
     * Fetches [url] and parses it as an OPDS 1 (Atom) or OPDS 2 (JSON) feed.
     *
     * @throws AppError on an unreachable host, a non-2xx response, or a payload that
     *         neither Readium parser recognizes.
     */
    suspend fun loadFeed(url: String): CatalogFeed {
        val bytes = httpClient.get(url)
        return when (val result = parser.parse(bytes, url)) {
            is OpdsFeedParser.Result.Success -> result.feed
            is OpdsFeedParser.Result.Failure -> throw result.error.asException()
        }
    }

    companion object {
        /**
         * A cheap syntactic check so obviously malformed addresses are rejected before any
         * network round-trip. This is deliberately lenient: it confirms an http(s) scheme and
         * a non-empty host, and leaves real reachability to [loadFeed].
         */
        fun isValidUrl(url: String): Boolean {
            val trimmed = url.trim()
            if (!trimmed.startsWith("http://", ignoreCase = true) &&
                !trimmed.startsWith("https://", ignoreCase = true)
            ) {
                return false
            }
            val withoutScheme = trimmed.substringAfter("://")
            val host = withoutScheme.substringBefore('/').substringBefore('?')
            return host.isNotBlank()
        }
    }
}
