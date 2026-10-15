package com.miireader.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-saved OPDS catalog address.
 *
 * Only the address and a display title are persisted. The parsed feed itself is not
 * cached in the database: feeds are re-fetched on open so the browser always reflects
 * the catalog's current contents.
 */
@Entity(tableName = "catalogs")
data class CatalogEntity(
    @PrimaryKey
    val id: String,

    /** Catalog URL exactly as the user entered it. */
    @ColumnInfo(name = "url")
    val url: String,

    /**
     * Optional display name. When null the browser falls back to the catalog's own feed
     * title, or the host, so the list is never blank.
     */
    @ColumnInfo(name = "title")
    val title: String? = null,

    @ColumnInfo(name = "added_date")
    val addedDate: Long = System.currentTimeMillis()
)
