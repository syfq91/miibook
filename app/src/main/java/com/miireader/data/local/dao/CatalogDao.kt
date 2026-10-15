package com.miireader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.miireader.data.local.entity.CatalogEntity
import kotlinx.coroutines.flow.Flow

/**
 * Persistence for the user's saved OPDS catalog addresses.
 *
 * Catalogs are keyed by their normalized URL rather than by an opaque id, so adding the
 * same address twice is a harmless no-op instead of a duplicate entry.
 */
@Dao
interface CatalogDao {

    @Query("SELECT * FROM catalogs ORDER BY added_date DESC")
    fun getAllCatalogs(): Flow<List<CatalogEntity>>

    @Query("SELECT * FROM catalogs WHERE id = :id LIMIT 1")
    suspend fun getCatalogById(id: String): CatalogEntity?

    @Query("SELECT * FROM catalogs WHERE id = :id LIMIT 1")
    fun getCatalogByIdFlow(id: String): Flow<CatalogEntity?>

    /**
     * Inserts a catalog, replacing any existing row with the same id. Callers rely on this
     * for idempotent adds rather than checking first.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCatalog(catalog: CatalogEntity)

    @Query("DELETE FROM catalogs WHERE id = :id")
    suspend fun deleteCatalogById(id: String)

    @Query("DELETE FROM catalogs")
    suspend fun deleteAllCatalogs()

    @Query("SELECT COUNT(*) FROM catalogs")
    suspend fun countCatalogs(): Int
}
