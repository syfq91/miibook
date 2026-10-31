package com.miireader.test

import com.miireader.data.local.entity.CatalogEntity
import com.miireader.data.opds.CatalogCredentials
import com.miireader.data.repository.CatalogRepository
import java.net.URI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * [CatalogRepository] over an in-memory list.
 *
 * Normalization is intentionally *not* reproduced here — that is the real repository's
 * job and gets its own test. This fake only tracks saved entries so the ViewModel can be
 * driven without a database, and rejects the same unusable addresses the real one does.
 */
class FakeCatalogRepository(
    initialCatalogs: List<CatalogEntity> = emptyList()
) : CatalogRepository {

    private val _catalogs = MutableStateFlow(initialCatalogs)

    /** Saved catalogs, newest first. */
    val catalogsFlow: Flow<List<CatalogEntity>> =
        _catalogs.map { list -> list.sortedByDescending { it.addedDate } }

    /** Set to make the next write fail, for exercising the error path. */
    var shouldFailWrites: Boolean = false

    /** Number of add attempts accepted. */
    var addCount: Int = 0
        private set

    /** Number of remove attempts accepted. */
    var removeCount: Int = 0
        private set

    override fun getCatalogs(): Flow<List<CatalogEntity>> = catalogsFlow

    override suspend fun getCatalogById(id: String): CatalogEntity? =
        _catalogs.value.find { it.id == id }

    override suspend fun addCatalog(
        url: String,
        title: String?,
        username: String?,
        password: String?
    ): CatalogEntity? {
        if (shouldFailWrites) throw IllegalStateException("write failed")

        val trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return null

        addCount++
        val credentials = CatalogCredentials.ofOrNull(username, password)
        val entity = CatalogEntity(
            id = trimmed,
            url = trimmed,
            title = title,
            username = credentials?.username,
            password = credentials?.password,
            addedDate = System.currentTimeMillis()
        )
        _catalogs.value = _catalogs.value.filterNot { it.id == entity.id } + entity
        return entity
    }

    /**
     * Mirrors the real repository's origin match closely enough for ViewModel tests: any
     * saved, credentialed catalog on the same scheme/host/port claims the request.
     */
    override suspend fun findCatalogForUrl(url: String): CatalogEntity? {
        val target = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        return _catalogs.value.firstOrNull { entity ->
            if (entity.username == null) return@firstOrNull false
            val saved = runCatching { URI(entity.url) }.getOrNull() ?: return@firstOrNull false
            originOf(saved) == originOf(target)
        }
    }

    private fun originOf(uri: URI): String? {
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val port = if (uri.port == -1) {
            if (scheme == "https") 443 else 80
        } else {
            uri.port
        }
        return "$scheme://$host:$port"
    }

    override suspend fun removeCatalog(id: String) {
        if (shouldFailWrites) throw IllegalStateException("write failed")
        removeCount++
        _catalogs.value = _catalogs.value.filterNot { it.id == id }
    }
}
