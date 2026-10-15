package com.miireader.test

import com.miireader.data.download.BookDownloader
import java.io.File
import java.io.IOException

/**
 * [BookDownloader] that writes canned bytes instead of opening a socket.
 *
 * The body is written in two halves so a test can hook the halfway point — that is how a
 * download cancelled while bytes are in flight is reproduced.
 */
class FakeBookDownloader(
    private val bodies: Map<String, ByteArray> = emptyMap()
) : BookDownloader {

    /** Bytes served per URL. */
    private val registered = bodies.toMutableMap()

    /** Every URL passed to [downloadTo], in order. */
    val requestedUrls = mutableListOf<String>()

    /** Every (downloaded, total) pair reported through the progress callback. */
    val progressReports = mutableListOf<Pair<Long, Long>>()

    /** When set, [downloadTo] throws this instead of writing anything. */
    var failure: IOException? = null

    /** Runs between the two halves of a write, to simulate work mid-download. */
    var midwayHook: suspend () -> Unit = {}

    fun put(url: String, body: ByteArray) {
        registered[url] = body
    }

    override suspend fun downloadTo(
        url: String,
        target: File,
        onProgress: suspend (downloaded: Long, total: Long) -> Unit
    ) {
        requestedUrls += url
        failure?.let { throw it }

        val body = registered[url]
            ?: throw IOException("No fake body registered for $url")

        val total = body.size.toLong()
        val halfway = body.size / 2
        target.outputStream().use { output ->
            output.write(body, 0, halfway)
            onProgress(halfway.toLong(), total)
            progressReports += halfway.toLong() to total

            midwayHook()

            output.write(body, halfway, body.size - halfway)
            onProgress(total, total)
            progressReports += total to total
        }
    }
}
