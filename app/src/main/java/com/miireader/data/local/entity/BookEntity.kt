package com.miireader.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "author")
    val author: String,

    @ColumnInfo(name = "file_path")
    val filePath: String,

    @ColumnInfo(name = "cover_path")
    val coverPath: String? = null,

    @ColumnInfo(name = "import_date")
    val importDate: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "last_opened_date")
    val lastOpenedDate: Long? = null,

    @ColumnInfo(name = "download_status")
    val downloadStatus: String = "LOCAL", // LOCAL, DOWNLOADING, DOWNLOADED, FAILED

    /**
     * SHA-256 checksum of the imported file's content, used to detect duplicate
     * imports. Null for books imported before this field existed or for books
     * whose checksum could not be computed.
     */
    @ColumnInfo(name = "content_checksum")
    val contentChecksum: String? = null
)
