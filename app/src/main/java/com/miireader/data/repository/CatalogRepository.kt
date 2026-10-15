package com.miireader.data.repository

import com.miireader.core.dispatchers.CoroutineDispatchers
import com.miireader.data.local.dao.CatalogDao
import com.miireader.data.local.entity.CatalogEntity
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Persistence for the OPDS catalogs the user has chosen to keep.
 *
 * Catalog *contents* are never cached here — only the addresses. Each open re-fetches the
 * feed so the browser reflects what the catalog actually serves right now.
 */
interface CatalogRepository {

    /** Saved catalogs, newest first. */
    fun getCatalogs(): Flow<List<CatalogEntity>>

    suspend fun getCatalogById(id: String): CatalogEntity?

    /**
     * Saves a catalog for [url].
     *
     * The id is derived from the normalized URL (scheme and host lowercased, default ports
     * and trailing slashes dropped), so adding the same catalog twice is a no-op rather
     * than a duplicate entry.
     *
     * @return the saved entity, or null if [url] is not a usable http(s) address.
     */
    suspend fun addCatalog(url: String, title: String? = null): CatalogEntity?

    suspend fun removeCatalog(id: String)
}

class DefaultCatalogRepository(
    private val catalogDao: CatalogDao,
    private val dispatchers: CoroutineDispatchers
) : CatalogRepository {

    override fun getCatalogs(): Flow<List<CatalogEntity>> =
        catalogDao.getAllCatalogs().flowOn(dispatchers.io)

    override suspend fun getCatalogById(id: String): CatalogEntity? =
        withContext(dispatchers.io) { catalogDao.getCatalogById(id) }

    override suspend fun addCatalog(url: String, title: String?): CatalogEntity? {
        val normalized = normalizeUrl(url) ?: return null
        val entity = CatalogEntity(
            id = stableIdFor(normalized),
            url = normalized,
            title = title?.trim()?.takeIf { it.isNotEmpty() }
        )
        withContext(dispatchers.io) { catalogDao.upsertCatalog(entity) }
        return entity
    }

    override suspend fun removeCatalog(id: String) {
        withContext(dispatchers.io) { catalogDao.deleteCatalogById(id) }
    }

    /**
     * Lowercases scheme and host, drops a redundant `:80`/`:443` and any trailing slash on
     * the path, and rejects anything that is not an absolute http(s) URL with a host.
     * Query strings and non-default ports are preserved, because some catalogs key feeds
     * off them.
     */
    private fun normalizeUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank()) return null

        val defaultPort = if (scheme == "https") 443 else 80
        val port = if (uri.port == -1 || uri.port == defaultPort) null else uri.port

        val path = uri.rawPath?.trimEnd('/')
            ?.takeIf { it.isNotEmpty() } ?: ""

        val rebuilt = StringBuilder()
        rebuilt.append(scheme).append("://").append(uri.host.lowercase())
        port?.let { rebuilt.append(':').append(it) }
        rebuilt.append(path)
        uri.rawQuery?.let { rebuilt.append('?').append(it) }
        return rebuilt.toString()
    }

    /** A stable, collision-free id derived from the normalized URL. */
    private fun stableIdFor(normalizedUrl: String): String =
        "cat-" + UUID.nameUUIDFromBytes(normalizedUrl.toByteArray()).toString()
}
