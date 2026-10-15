package com.miireader.data.download

import com.miireader.core.dispatchers.CoroutineDispatchers
import com.miireader.data.local.DownloadEntity
import com.miireader.data.local.dao.DownloadDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Persistence for the download queue.
 */
interface DownloadRepository {

    /** All downloads, newest first. */
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    /** Only downloads that are not in a terminal state. */
    fun getActiveDownloads(): Flow<List<DownloadEntity>>

    /** Active (queued or downloading) downloads read once, ordered oldest first. */
    suspend fun getPendingDownloads(): List<DownloadEntity>

    /** A single download by its ID. */
    fun getDownloadByIdFlow(id: String): Flow<DownloadEntity?>

    /** A single download by its ID, read once. */
    suspend fun getDownloadById(id: String): DownloadEntity?

    /** A single download by the book ID it produced. */
    suspend fun getDownloadByBookId(bookId: String): DownloadEntity?

    /** Adds or replaces a download. */
    suspend fun upsertDownload(download: DownloadEntity)

    /** Updates an existing download (status, progress, etc.). */
    suspend fun updateDownload(download: DownloadEntity)

    /** Removes a download from the queue. */
    suspend fun removeDownload(id: String)

    /** Cleans up old completed/failed downloads. */
    suspend fun cleanupOldDownloads(olderThan: Long): Int
}

class DefaultDownloadRepository(
    private val downloadDao: DownloadDao,
    private val dispatchers: CoroutineDispatchers
) : DownloadRepository {

    override fun getAllDownloads(): Flow<List<DownloadEntity>> =
        downloadDao.getAllDownloadsFlow().flowOn(dispatchers.io)

    override fun getActiveDownloads(): Flow<List<DownloadEntity>> =
        downloadDao.getActiveDownloadsFlow().flowOn(dispatchers.io)

    override suspend fun getPendingDownloads(): List<DownloadEntity> =
        withContext(dispatchers.io) { downloadDao.getActiveDownloads() }

    override fun getDownloadByIdFlow(id: String): Flow<DownloadEntity?> =
        downloadDao.getDownloadByIdFlow(id).flowOn(dispatchers.io)

    override suspend fun getDownloadById(id: String): DownloadEntity? =
        withContext(dispatchers.io) { downloadDao.getDownloadById(id) }

    override suspend fun getDownloadByBookId(bookId: String): DownloadEntity? =
        withContext(dispatchers.io) { downloadDao.getDownloadByBookId(bookId) }

    override suspend fun upsertDownload(download: DownloadEntity) {
        withContext(dispatchers.io) { downloadDao.insertDownload(download) }
    }

    override suspend fun updateDownload(download: DownloadEntity) {
        withContext(dispatchers.io) { downloadDao.updateDownload(download) }
    }

    override suspend fun removeDownload(id: String) {
        withContext(dispatchers.io) { downloadDao.deleteDownload(id) }
    }

    override suspend fun cleanupOldDownloads(olderThan: Long): Int =
        withContext(dispatchers.io) { downloadDao.deleteOldCompletedDownloads(olderThan) }
}