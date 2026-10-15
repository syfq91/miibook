package com.miireader.test

import com.miireader.data.local.DownloadEntity
import com.miireader.data.local.DownloadStatus
import com.miireader.data.download.DownloadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted

/**
 * [DownloadRepository] over an in-memory list.
 */
class FakeDownloadRepository(
    initialDownloads: List<DownloadEntity> = emptyList()
) : DownloadRepository {

    private val _downloads = MutableStateFlow(initialDownloads)

    /** All downloads, newest first. */
    val downloadsFlow: Flow<List<DownloadEntity>> = _downloads
        .map { list -> list.sortedByDescending { it.createdDate } }

    /** Set to make the next write fail, for exercising the error path. */
    var shouldFailWrites: Boolean = false

    override fun getAllDownloads(): Flow<List<DownloadEntity>> =
        _downloads.map { list -> list.sortedByDescending { it.createdDate } }

    override fun getActiveDownloads(): Flow<List<DownloadEntity>> =
        _downloads.map { list -> list.filter { !it.isTerminal }.sortedBy { it.createdDate } }

    override fun getDownloadByIdFlow(id: String): Flow<DownloadEntity?> =
        _downloads.map { list -> list.find { it.id == id } }

    override suspend fun getDownloadById(id: String): DownloadEntity? =
        _downloads.value.find { it.id == id }

    override suspend fun getDownloadByBookId(bookId: String): DownloadEntity? =
        _downloads.value.find { it.bookId == bookId }

    override suspend fun upsertDownload(download: DownloadEntity) {
        if (shouldFailWrites) throw IllegalStateException("write failed")
        _downloads.value = _downloads.value.filterNot { it.id == download.id } + download
    }

    /** Mirrors Room's `@Update`: an update never re-creates a deleted row. */
    override suspend fun updateDownload(download: DownloadEntity) {
        if (shouldFailWrites) throw IllegalStateException("write failed")
        if (_downloads.value.none { it.id == download.id }) return
        _downloads.value = _downloads.value.filterNot { it.id == download.id } + download
    }

    override suspend fun removeDownload(id: String) {
        if (shouldFailWrites) throw IllegalStateException("write failed")
        _downloads.value = _downloads.value.filterNot { it.id == id }
    }

    override suspend fun cleanupOldDownloads(olderThan: Long): Int {
        val before = _downloads.value.size
        _downloads.value = _downloads.value.filterNot {
            it.isTerminal && (it.completedDate ?: Long.MAX_VALUE) < olderThan
        }
        return before - _downloads.value.size
    }

    /** Adds a download for testing. */
    fun addDownload(download: DownloadEntity) {
        _downloads.value = _downloads.value + download
    }

    /** Creates a test download with the given status. */
    companion object {
        fun createTestDownload(
            id: String = "dl-1",
            status: String = DownloadStatus.QUEUED,
            progress: Float = 0f,
            title: String? = "Test Book",
            author: String? = "Test Author"
        ): DownloadEntity {
            return DownloadEntity(
                id = id,
                url = "https://example.com/book.epub",
                title = title,
                author = author,
                mediaType = "application/epub+zip",
                status = status,
                progress = progress
            )
        }
    }
}