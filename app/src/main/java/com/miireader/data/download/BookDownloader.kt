package com.miireader.data.download

import java.io.File
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.http.HttpClient
import org.readium.r2.shared.util.http.HttpRequest

/**
 * The one network operation the download queue needs: stream a book into a local file.
 *
 * Narrow and injectable so [DefaultDownloadManager] can be unit-tested against a fake
 * without a socket, and so progress reporting has a single well-defined shape.
 */
interface BookDownloader {

    /**
     * Downloads [url] into [target], creating or overwriting it.
     *
     * @param onProgress called as bytes arrive, with the bytes written so far and the
     *         total advertised by the server, or -1 when the length is unknown.
     * @throws java.io.IOException on transport failure or a non-2xx status.
     */
    suspend fun downloadTo(
        url: String,
        target: File,
        onProgress: suspend (downloaded: Long, total: Long) -> Unit
    )
}

/**
 * [BookDownloader] backed by Readium's `DefaultHttpClient`, which handles redirects,
 * timeouts and TLS the same way the rest of the toolkit does.
 */
class ReadiumBookDownloader(
    private val client: HttpClient = DefaultHttpClient()
) : BookDownloader {

    override suspend fun downloadTo(
        url: String,
        target: File,
        onProgress: suspend (downloaded: Long, total: Long) -> Unit
    ) {
        val absolute = AbsoluteUrl(url)
            ?: throw IOException("That doesn't look like a valid download address.")

        val request = HttpRequest(absolute) { method = HttpRequest.Method.GET }

        val response = when (val result = client.stream(request)) {
            is Try.Success -> result.value
            is Try.Failure -> throw IOException(
                result.failureOrNull()?.message ?: "The download could not be started."
            )
        }

        val status = response.response.statusCode.code
        if (status !in 200..299) {
            throw IOException("The catalog returned an error (HTTP $status).")
        }

        val total = response.response.headers["Content-Length"]
            ?.firstOrNull()
            ?.toLongOrNull() ?: -1L

        var downloaded = 0L
        target.outputStream().use { output ->
            response.body.use { input ->
                val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    onProgress(downloaded, total)
                }
            }
        }
    }

    private companion object {
        const val DOWNLOAD_BUFFER_SIZE = 8192
    }
}
