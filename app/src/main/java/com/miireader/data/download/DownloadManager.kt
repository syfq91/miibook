package com.miireader.data.download

import android.content.Context
import com.miireader.core.dispatchers.CoroutineDispatchers
import com.miireader.core.error.AppError
import com.miireader.core.error.ErrorHandler
import com.miireader.data.epub.EpubMetadataExtractor
import com.miireader.data.epub.PublicationOpenException
import com.miireader.data.local.entity.BookEntity
import com.miireader.data.local.DownloadEntity
import com.miireader.data.local.DownloadStatus
import com.miireader.data.opds.CatalogLink
import com.miireader.data.repository.BookRepository
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The download queue as the UI sees it.
 *
 * An interface so ViewModels can be tested against a fake without a real queue running in
 * the background; [DefaultDownloadManager] is the production implementation.
 */
interface DownloadManager {

    /**
     * Enqueues a download from an OPDS acquisition link.
     *
     * @return the download id, or null when the link is not a format we can open (only
     *         EPUB is supported today).
     */
    suspend fun enqueueDownload(link: CatalogLink, title: String?, author: String?): String?

    /** Removes a download from the queue. Cancelling an in-flight fetch is best-effort. */
    suspend fun cancelDownload(id: String)

    /** Re-queues a failed download from the start. */
    suspend fun retryDownload(id: String)

    /** Drops completed and failed entries older than [olderThan] (epoch millis). */
    suspend fun cleanupOldDownloads(
        olderThan: Long = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000
    )

    /** Stops the queue worker. */
    fun shutdown()
}

/**
 * Manages the download queue: dequeues pending downloads, fetches them, validates,
 * and imports into the library.
 *
 * Only one download runs at a time to avoid saturating the network or disk on
 * e-paper devices. The queue is persisted in Room so it survives process death.
 */
class DefaultDownloadManager(
    private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val bookRepository: BookRepository,
    private val metadataExtractor: EpubMetadataExtractor,
    private val downloader: BookDownloader,
    private val errorHandler: ErrorHandler,
    private val dispatchers: CoroutineDispatchers
) : DownloadManager {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val downloadsDir = File(context.filesDir, "downloads").apply { if (!exists()) mkdirs() }
    private val downloadsChannel = Channel<DownloadEntity>(Channel.UNLIMITED)

    init {
        // Start the worker coroutine
        scope.launch { processQueue() }
    }

    /**
     * Enqueues a new download from an OPDS acquisition link.
     *
     * Returns the download ID, or null if the link is not a downloadable EPUB.
     */
    override suspend fun enqueueDownload(
        link: CatalogLink,
        title: String?,
        author: String?
    ): String? {
        // Only EPUB for now; PDF/CBZ support can be added later
        val isEpub = link.mediaType?.contains("epub") == true
        if (!isEpub) {
            return null
        }

        val id = UUID.randomUUID().toString()
        val download = DownloadEntity(
            id = id,
            url = link.href,
            title = title,
            author = author,
            mediaType = link.mediaType,
            status = DownloadStatus.QUEUED
        )

        downloadRepository.upsertDownload(download)
        downloadsChannel.send(download)
        return id
    }

    /** Removes a download from the queue. If it's in progress, cancellation is best-effort. */
    override suspend fun cancelDownload(id: String) {
        downloadRepository.removeDownload(id)
    }

    /** Retries a failed download. */
    override suspend fun retryDownload(id: String) {
        val download = downloadRepository.getDownloadById(id)
            ?: return
        val reset = download.copy(
            status = DownloadStatus.QUEUED,
            progress = 0f,
            downloadedBytes = 0,
            errorMessage = null,
            tempFilePath = null
        )
        downloadRepository.upsertDownload(reset)
        downloadsChannel.send(reset)
    }

    /** Cleans up old completed/failed downloads. */
    override suspend fun cleanupOldDownloads(olderThan: Long) {
        downloadRepository.cleanupOldDownloads(olderThan)
    }

    private suspend fun processQueue() {
        for (download in downloadsChannel) {
            // Skip if already terminal (might have been cancelled while queued)
            if (download.isTerminal) continue

            // Check if a newer version of this download exists with a different status
            val current = downloadRepository.getDownloadById(download.id)
                ?: continue

            if (current.isTerminal) continue

            executeDownload(current)
        }
    }

    private suspend fun executeDownload(download: DownloadEntity) {
        val updated = download.copy(status = DownloadStatus.DOWNLOADING)
        downloadRepository.upsertDownload(updated)

        try {
            val tempFile = downloadFile(updated)
            // A download removed while its bytes were in flight is a cancellation: drop
            // the partial file instead of importing something the user already declined.
            if (downloadRepository.getDownloadById(updated.id) == null) {
                tempFile.delete()
                return
            }
            val validatedFile = validateAndPrepareFile(tempFile, updated)
            importIntoLibrary(validatedFile, updated)
        } catch (e: Exception) {
            // The record is gone, so there is nothing to mark as failed.
            if (downloadRepository.getDownloadById(updated.id) != null) {
                handleFailure(updated, e)
            }
        }
    }

    private suspend fun downloadFile(download: DownloadEntity): File {
        val tempFile = File(downloadsDir, "${download.id}.tmp")
        downloadRepository.updateDownload(download.copy(tempFilePath = tempFile.absolutePath))

        var lastReportedPercent = -1
        try {
            downloader.downloadTo(download.url, tempFile) { downloaded, total ->
                val progress = if (total > 0) downloaded.toFloat() / total else 0f
                // One database write per whole percent keeps churn low on e-paper devices.
                val percent = (progress * 100).toInt()
                if (percent != lastReportedPercent) {
                    lastReportedPercent = percent
                    downloadRepository.updateDownload(
                        download.copy(
                            totalBytes = total,
                            downloadedBytes = downloaded,
                            progress = progress
                        )
                    )
                }
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }

        return tempFile
    }

    private suspend fun validateAndPrepareFile(file: File, download: DownloadEntity): File {
        // Basic validation: file exists and is not empty
        if (!file.exists() || file.length() == 0L) {
            throw IOException("Downloaded file is empty or missing")
        }

        // Validate it's a readable EPUB by attempting to open it
        try {
            metadataExtractor.extractFrom(file.absolutePath)
        } catch (e: PublicationOpenException) {
            throw IOException("Downloaded file is not a valid EPUB: ${e.message}")
        }

        return file
    }

    private suspend fun importIntoLibrary(file: File, download: DownloadEntity) {
        // The row carries the byte counts recorded while streaming; re-read it so the
        // completion write does not reset them to the values the download started with.
        val current = downloadRepository.getDownloadById(download.id) ?: download

        // Compute checksum for duplicate detection
        val checksum = computeSha256(file)
        val existing = bookRepository.getBookByChecksum(checksum)
        if (existing != null) {
            // Duplicate - mark as completed but point to existing book
            file.delete()
            val completed = current.copy(
                status = DownloadStatus.COMPLETED,
                progress = 1f,
                bookId = existing.id,
                completedDate = System.currentTimeMillis()
            )
            downloadRepository.upsertDownload(completed)
            return
        }

        // Move to permanent location
        val finalFile = File(context.filesDir, "books/${System.currentTimeMillis()}.epub")
        finalFile.parentFile?.mkdirs()
        if (!file.renameTo(finalFile)) {
            file.copyTo(finalFile, overwrite = true)
            file.delete()
        }

        // Extract metadata for the book entity
        val metadata = metadataExtractor.extractFrom(finalFile.absolutePath)

        val book = BookEntity(
            id = UUID.randomUUID().toString(),
            title = metadata.title,
            author = metadata.author,
            filePath = finalFile.absolutePath,
            importDate = System.currentTimeMillis(),
            contentChecksum = checksum,
            downloadStatus = "DOWNLOADED"
        )

        bookRepository.insertBook(book)

        val completed = current.copy(
            status = DownloadStatus.COMPLETED,
            progress = 1f,
            bookId = book.id,
            completedDate = System.currentTimeMillis(),
            tempFilePath = null
        )
        downloadRepository.upsertDownload(completed)
    }

    private suspend fun handleFailure(download: DownloadEntity, error: Exception) {
        val message = when (error) {
            is IOException -> error.message ?: "Network or disk error"
            is PublicationOpenException -> "Not a valid EPUB: ${error.message}"
            else -> "Download failed: ${error.message}"
        }

        // Keep the AppError type honest: the screen shows the message either way, but the
        // category decides how a failure is logged and grouped.
        val appError = when (error) {
            is IOException -> AppError.NetworkError(message)
            is PublicationOpenException -> AppError.BookParseError(message)
            else -> AppError.GeneralError(message)
        }
        errorHandler.emitError(appError)

        val failed = download.copy(
            status = DownloadStatus.FAILED,
            errorMessage = message,
            completedDate = System.currentTimeMillis(),
            tempFilePath = null
        )
        downloadRepository.upsertDownload(failed)
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } >= 0) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Shuts down the download manager. */
    override fun shutdown() {
        scope.cancel()
    }
}