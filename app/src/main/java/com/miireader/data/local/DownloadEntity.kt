package com.miireader.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a download job in the queue.
 *
 * [status] transitions: QUEUED -> DOWNLOADING -> COMPLETED | FAILED
 * On completion, [bookId] is set to the ID of the imported book.
 */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey
    val id: String,

    /** The absolute URL being downloaded. */
    @ColumnInfo(name = "url")
    val url: String,

    /** Optional title from the catalog entry. */
    @ColumnInfo(name = "title")
    val title: String?,

    /** Optional author from the catalog entry. */
    @ColumnInfo(name = "author")
    val author: String?,

    /** MIME type of the acquisition link (e.g., application/epub+zip). */
    @ColumnInfo(name = "media_type")
    val mediaType: String?,

    /** Download status: QUEUED, DOWNLOADING, COMPLETED, FAILED. */
    @ColumnInfo(name = "status")
    val status: String = "QUEUED",

    /** Progress from 0.0 to 1.0. */
    @ColumnInfo(name = "progress")
    val progress: Float = 0f,

    /** Total bytes expected, or -1 if unknown. */
    @ColumnInfo(name = "total_bytes")
    val totalBytes: Long = -1,

    /** Bytes downloaded so far. */
    @ColumnInfo(name = "downloaded_bytes")
    val downloadedBytes: Long = 0,

    /** Error message when status is FAILED. */
    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,

    /** The ID of the book created on successful import. */
    @ColumnInfo(name = "book_id")
    val bookId: String? = null,

    /** Timestamp when the download was enqueued. */
    @ColumnInfo(name = "created_date")
    val createdDate: Long = System.currentTimeMillis(),

    /** Timestamp when the download finished (completed or failed). */
    @ColumnInfo(name = "completed_date")
    val completedDate: Long? = null,

    /** Path to the temporary file being written. */
    @ColumnInfo(name = "temp_file_path")
    val tempFilePath: String? = null
) {

    /** True when the download is in a terminal state. */
    val isTerminal: Boolean
        get() = status == "COMPLETED" || status == "FAILED"
}