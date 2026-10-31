package com.miireader.data.opds

import com.miireader.core.dispatchers.CoroutineDispatchers
import com.miireader.core.dispatchers.DefaultCoroutineDispatchers
import com.miireader.core.error.AppError
import kotlinx.coroutines.withContext
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
     * @param credentials when non-null, sent as an HTTP Basic `Authorization` header.
     * @throws AppError.NetworkError on transport failure or a non-2xx status.
     */
    suspend fun get(url: String, credentials: CatalogCredentials? = null): ByteArray
}

/**
 * [OpdsHttpClient] backed by Readium's `DefaultHttpClient`, which handles redirects,
 * timeouts, and TLS the same way the rest of the toolkit does.
 */
class ReadiumOpdsHttpClient(
    private val dispatchers: CoroutineDispatchers = DefaultCoroutineDispatchers()
) : OpdsHttpClient {

    private val client = DefaultHttpClient()

    override suspend fun get(url: String, credentials: CatalogCredentials?): ByteArray =
        // Readium's client reads the body synchronously, and several callers start from
        // viewModelScope (main thread). Without this hop Android throws
        // NetworkOnMainThreadException the moment the response body is consumed.
        withContext(dispatchers.io) {
            val absolute = AbsoluteUrl(url)
                ?: throw AppError.NetworkError("That doesn't look like a valid address.")
                    .asException()

            val request = HttpRequest(absolute) {
                method = HttpRequest.Method.GET
                credentials?.let { setHeader("Authorization", it.toAuthorizationHeader()) }
            }

            val authenticated = credentials != null
            when (val result = client.stream(request)) {
                is Try.Success -> readBody(result.value, authenticated)
                is Try.Failure -> throw httpFailure(result.failureOrNull(), authenticated)
            }
        }

    private fun readBody(response: HttpStreamResponse, authenticated: Boolean): ByteArray {
        val status = response.response.statusCode.code
        if (status !in 200..299) {
            throw statusFailure(status, authenticated).asException()
        }
        return response.body.use { it.readBytes() }
    }

    /**
     * Status-specific wording matters here: on an authenticated catalog, a bare
     * "HTTP 401" leaves the user with no idea that a username and password exist — or,
     * when one was supplied, that it was rejected.
     */
    private fun statusFailure(status: Int, authenticated: Boolean): AppError = when (status) {
        401 -> AppError.NetworkError(
            if (authenticated) {
                "The catalog rejected that username or password (HTTP 401)."
            } else {
                "This catalog needs a username and password (HTTP 401)."
            }
        )
        403 -> AppError.NetworkError(
            if (authenticated) {
                "The catalog rejected that username or password (HTTP 403)."
            } else {
                "This catalog refused access (HTTP 403)."
            }
        )
        else -> AppError.NetworkError("The catalog returned an error (HTTP $status).")
    }

    private fun httpFailure(error: HttpError, authenticated: Boolean): CatalogException =
        (when (error) {
            is HttpError.Timeout -> AppError.NetworkError("The catalog took too long to respond.")
            is HttpError.Unreachable -> AppError.NetworkError("Couldn't reach the catalog. Check the address and your connection.")
            is HttpError.ErrorResponse -> statusFailure(error.status.code, authenticated)
            else -> AppError.NetworkError("Couldn't read the catalog: ${error.message}.")
        }).asException()
}
