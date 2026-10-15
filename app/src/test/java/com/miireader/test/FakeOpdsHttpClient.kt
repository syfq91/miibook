package com.miireader.test

import com.miireader.core.error.AppError
import com.miireader.data.opds.CatalogException
import com.miireader.data.opds.OpdsHttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [OpdsHttpClient] backed by registered URL -> body pairs.
 *
 * Every fetch is recorded so tests can assert on the exact URL requested, which matters
 * when checking that relative feed hrefs were resolved against the right base.
 */
class FakeOpdsHttpClient(
    initialBodies: Map<String, ByteArray> = emptyMap()
) : OpdsHttpClient {

    private val _bodies = MutableStateFlow(initialBodies.toMap())

    /** Body bytes served per URL, exposed read-only. */
    val bodies = _bodies.asStateFlow()

    /** Every URL passed to [get], in order. */
    val requestedUrls = mutableListOf<String>()

    /** When set, [get] throws this instead of serving a body. */
    var failure: CatalogException? = null

    fun put(url: String, body: ByteArray) {
        _bodies.value = _bodies.value + (url to body)
    }

    override suspend fun get(url: String): ByteArray {
        requestedUrls += url
        failure?.let { throw it }
        return _bodies.value[url]
            ?: throw CatalogException(
                AppError.NetworkError("No fake body registered for $url")
            )
    }
}
