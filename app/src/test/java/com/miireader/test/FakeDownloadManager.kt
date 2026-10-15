package com.miireader.test

import com.miireader.data.download.DownloadManager
import com.miireader.data.opds.CatalogLink

/**
 * [DownloadManager] that records calls instead of touching the network or the disk.
 *
 * The real manager owns a queue coroutine and writes files, neither of which belongs in a
 * ViewModel test; this fake lets the tests assert on what the screen *asked* for.
 */
class FakeDownloadManager : DownloadManager {

    /** Every acquisition link passed to [enqueueDownload], in order. */
    val enqueuedLinks = mutableListOf<CatalogLink>()

    /** Titles and authors requested alongside each enqueue. */
    val enqueuedTitles = mutableListOf<Pair<String?, String?>>()

    /** Download ids passed to [cancelDownload]. */
    val cancelledIds = mutableListOf<String>()

    /** Download ids passed to [retryDownload]. */
    val retriedIds = mutableListOf<String>()

    /** How many times [cleanupOldDownloads] was called. */
    var cleanupCount: Int = 0
        private set

    /** True once [shutdown] has been called. */
    var wasShutdown: Boolean = false
        private set

    /** Id returned by the next [enqueueDownload]. */
    var nextEnqueueId: String? = "dl-1"

    /** When set, [enqueueDownload] throws this instead of recording a call. */
    var enqueueFailure: Exception? = null

    /** When set, [retryDownload] throws this instead of recording a call. */
    var retryFailure: Exception? = null

    override suspend fun enqueueDownload(
        link: CatalogLink,
        title: String?,
        author: String?
    ): String? {
        enqueueFailure?.let { throw it }
        enqueuedLinks += link
        enqueuedTitles += title to author
        return nextEnqueueId
    }

    override suspend fun cancelDownload(id: String) {
        cancelledIds += id
    }

    override suspend fun retryDownload(id: String) {
        retryFailure?.let { throw it }
        retriedIds += id
    }

    override suspend fun cleanupOldDownloads(olderThan: Long) {
        cleanupCount++
    }

    override fun shutdown() {
        wasShutdown = true
    }
}
