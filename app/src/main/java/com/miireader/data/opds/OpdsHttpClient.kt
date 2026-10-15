package com.miireader.data.opds

import com.miireader.core.error.AppError
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.http.HttpError
import org.readium.r2.shared.util.http.HttpRequest
import org.readium.r2.shared.util.http.HttpStreamResponse

/**
 * The one network operation the catalog needs: GET a URL and hand back its body bytes.
 *
 * Narrow and injectable so [OpdsCatalogSource] can be unit-tested against a fake without
 * touching the network or a real `InputStream`.
 */
interface OpdsHttpClient {
    /**
     * Fetches [url] and returns the response body.
     *
     * @throws AppError.NetworkError on transport failure or a non-2xx status.
     */
    suspend fun get(url: String): ByteArray
}

/**
 * [OpdsHttpClient] backed by Readium's `DefaultHttpClient`, which handles redirects,
 * timeouts, and TLS the same way the rest of the toolkit does.
 */
class ReadiumOpdsHttpClient : OpdsHttpClient {

    private val client = DefaultHttpClient()

    override suspend fun get(url: String): ByteArray {
        val absolute = AbsoluteUrl(url)
            ?: throw AppError.NetworkError("That doesn't look like a valid address.").asException()

        val request = HttpRequest(absolute) {
            method = HttpRequest.Method.GET
        }

        return when (val result = client.stream(request)) {
            is Try.Success -> readBody(result.value)
            is Try.Failure -> throw httpFailure(result.failureOrNull())
        }
    }

    private fun readBody(response: HttpStreamResponse): ByteArray {
        val status = response.response.statusCode.code
        if (status !in 200..299) {
            throw AppError.NetworkError("The catalog returned an error (HTTP $status).").asException()
        }
        return response.body.use { it.readBytes() }
    }

    private fun httpFailure(error: HttpError): CatalogException =
        (when (error) {
            is HttpError.Timeout -> AppError.NetworkError("The catalog took too long to respond.")
            is HttpError.Unreachable -> AppError.NetworkError("Couldn't reach the catalog. Check the address and your connection.")
            is HttpError.ErrorResponse -> AppError.NetworkError("The catalog returned an error (HTTP ${error.status}).")
            else -> AppError.NetworkError("Couldn't read the catalog: ${error.message}.")
        }).asException()
}
