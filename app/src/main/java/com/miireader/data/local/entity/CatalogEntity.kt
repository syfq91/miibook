package com.miireader.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-saved OPDS catalog address, plus optional HTTP Basic credentials.
 *
 * The parsed feed itself is not cached in the database: feeds are re-fetched on open so
 * the browser always reflects the catalog's current contents. Credentials are stored in
 * the app-private database in plain text — there is no encryption-at-rest layer yet.
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

    /**
     * Optional HTTP Basic username. Null means the catalog is public. Credentials are
     * applied to any feed or acquisition link on the same scheme/host/port, which is how
     * servers like BookFlow keep nested feeds (`/opdsx3`) behind the same realm.
     */
    @ColumnInfo(name = "username")
    val username: String? = null,

    /** HTTP Basic password paired with [username]; may be empty but is never logged. */
    @ColumnInfo(name = "password")
    val password: String? = null,

    @ColumnInfo(name = "added_date")
    val addedDate: Long = System.currentTimeMillis()
)
