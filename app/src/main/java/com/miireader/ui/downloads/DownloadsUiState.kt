package com.miireader.ui.downloads

import com.miireader.data.local.DownloadEntity
import com.miireader.data.local.DownloadStatus

/**
 * UI state for the downloads screen.
 */
data class DownloadsUiState(
    val downloads: List<DownloadItemUi> = emptyList(),
    val errorMessage: String? = null
) {

    /** Active (non-terminal) downloads. */
    val activeDownloads: List<DownloadItemUi>
        get() = downloads.filter { !it.isTerminal }

    /** Completed downloads. */
    val completedDownloads: List<DownloadItemUi>
        get() = downloads.filter { it.status == DownloadStatus.COMPLETED }

    /** Failed downloads. */
    val failedDownloads: List<DownloadItemUi>
        get() = downloads.filter { it.status == DownloadStatus.FAILED }
}

/** A download as shown in the UI. */
data class DownloadItemUi(
    val id: String,
    val title: String?,
    val author: String?,
    val mediaType: String?,
    val status: String,
    val progress: Float, // 0.0 to 1.0
    val totalBytes: Long,
    val downloadedBytes: Long,
    val errorMessage: String?,
    val bookId: String?
) {

    val isTerminal: Boolean
        get() = status == DownloadStatus.COMPLETED || status == DownloadStatus.FAILED

    val isActive: Boolean
        get() = !isTerminal

    val displayTitle: String
        get() = title ?: "Unknown title"

    val displaySubtitle: String
        get() = author ?: mediaType ?: ""

    val progressPercent: Int
        get() = (progress * 100).toInt()

    val formattedProgress: String
        get() {
            if (totalBytes > 0) {
                return "${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)}"
            }
            return "${progressPercent}%"
        }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000.0)
            bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
            else -> "$bytes B"
        }
    }
}

fun DownloadEntity.toUi(): DownloadItemUi = DownloadItemUi(
    id = id,
    title = title,
    author = author,
    mediaType = mediaType,
    status = status,
    progress = progress,
    totalBytes = totalBytes,
    downloadedBytes = downloadedBytes,
    errorMessage = errorMessage,
    bookId = bookId
)