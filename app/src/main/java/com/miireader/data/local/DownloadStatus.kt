package com.miireader.data.local

/** Possible status values for a download. */
object DownloadStatus {
    const val QUEUED = "QUEUED"
    const val DOWNLOADING = "DOWNLOADING"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"

    fun isValid(status: String): Boolean = status in setOf(QUEUED, DOWNLOADING, COMPLETED, FAILED)
}