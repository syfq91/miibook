package com.miireader.test

import com.miireader.data.local.dao.CatalogDao
import com.miireader.data.local.entity.CatalogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [CatalogDao] mirroring Room's REPLACE-on-conflict semantics for the primary
 * key, so idempotency can be asserted without a database.
 */
class FakeCatalogDao(
    initialCatalogs: List<CatalogEntity> = emptyList()
) : CatalogDao {

    private val _catalogs = MutableStateFlow(initialCatalogs)
    val catalogsFlow = _catalogs.asStateFlow()

    /** Number of upserts accepted, so tests can assert on write behaviour. */
    var upsertCount: Int = 0
        private set

    /**
     * Newest first, matching the `ORDER BY added_date DESC` the real DAO applies.
     */
    override fun getAllCatalogs(): Flow<List<CatalogEntity>> =
        _catalogs.map { list -> list.sortedByDescending { it.addedDate } }

    override suspend fun getCatalogById(id: String): CatalogEntity? =
        _catalogs.value.find { it.id == id }

    override fun getCatalogByIdFlow(id: String): Flow<CatalogEntity?> =
        _catalogs.map { list -> list.find { it.id == id } }

    override suspend fun upsertCatalog(catalog: CatalogEntity) {
        upsertCount++
        _catalogs.value = _catalogs.value.filterNot { it.id == catalog.id } + catalog
    }

    override suspend fun deleteCatalogById(id: String) {
        _catalogs.value = _catalogs.value.filterNot { it.id == id }
    }

    override suspend fun deleteAllCatalogs() {
        _catalogs.value = emptyList()
    }

    override suspend fun countCatalogs(): Int = _catalogs.value.size
}
