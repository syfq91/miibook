package com.miireader.data.repository

import com.miireader.core.dispatchers.CoroutineDispatchers
import com.miireader.data.local.dao.CatalogDao
import com.miireader.data.local.entity.CatalogEntity
import com.miireader.data.opds.CatalogCredentials
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Persistence for the OPDS catalogs the user has chosen to keep.
 *
 * Catalog *contents* are never cached here — only the addresses (and, for catalogs that
 * require it, HTTP Basic credentials). Each open re-fetches the feed so the browser
 * reflects what the catalog actually serves right now.
 */
interface CatalogRepository {

    /** Saved catalogs, newest first. */
    fun getCatalogs(): Flow<List<CatalogEntity>>

    suspend fun getCatalogById(id: String): CatalogEntity?

    /**
     * Saves a catalog for [url].
     *
     * The id is derived from the normalized URL (scheme and host lowercased, default ports
     * and trailing slashes dropped), so adding the same catalog twice updates the stored
     * credentials rather than creating a duplicate entry.
     *
     * @param username optional HTTP Basic username; a blank value means "public catalog".
     * @param password paired with [username] and stored verbatim.
     * @return the saved entity, or null if [url] is not a usable http(s) address.
     */
    suspend fun addCatalog(
        url: String,
        title: String? = null,
        username: String? = null,
        password: String? = null
    ): CatalogEntity?

    /**
     * Finds the saved catalog whose credentials should be sent with a request to [url].
     *
     * Matches on scheme, host and effective port, because OPDS servers routinely link to
     * sibling feeds outside the catalog's own path (BookFlow's `/opdsx3`, for example) and
     * the same realm guards them all. When several catalogs share an origin, the one with
     * the longest path prefix of [url] wins. Catalogs without a username are ignored.
     */
    suspend fun findCatalogForUrl(url: String): CatalogEntity?

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

    override suspend fun addCatalog(
        url: String,
        title: String?,
        username: String?,
        password: String?
    ): CatalogEntity? {
        val normalized = normalizeUrl(url) ?: return null
        val credentials = CatalogCredentials.ofOrNull(username, password)
        val entity = CatalogEntity(
            id = stableIdFor(normalized),
            url = normalized,
            title = title?.trim()?.takeIf { it.isNotEmpty() },
            username = credentials?.username,
            password = credentials?.password
        )
        withContext(dispatchers.io) { catalogDao.upsertCatalog(entity) }
        return entity
    }

    override suspend fun findCatalogForUrl(url: String): CatalogEntity? {
        val target = parseHttpUri(url) ?: return null
        val targetOrigin = originOf(target) ?: return null
        val targetPath = target.rawPath.orEmpty()

        return withContext(dispatchers.io) {
            catalogDao.getAllCatalogsOnce()
                .asSequence()
                .filter { it.username != null }
                .mapNotNull { entity ->
                    val uri = parseHttpUri(entity.url) ?: return@mapNotNull null
                    if (originOf(uri) != targetOrigin) return@mapNotNull null
                    entity to pathScore(uri.rawPath.orEmpty(), targetPath)
                }
                .maxByOrNull { (_, score) -> score }
                ?.first
        }
    }

    override suspend fun removeCatalog(id: String) {
        withContext(dispatchers.io) { catalogDao.deleteCatalogById(id) }
    }

    /**
     * The length of the saved catalog's path when it is a prefix of the requested path,
     * or -1 when it is not. The boundary check stops `/opds` from matching `/opdsx3`.
     */
    private fun pathScore(basePath: String, targetPath: String): Int {
        val base = basePath.trimEnd('/')
        if (base.isEmpty()) return 0
        val isPrefix = targetPath == base || targetPath.startsWith("$base/")
        return if (isPrefix) base.length else -1
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

    private fun parseHttpUri(value: String): URI? =
        runCatching { URI(value.trim()) }.getOrNull()
            ?.takeIf {
                it.scheme?.lowercase() in setOf("http", "https") && !it.host.isNullOrBlank()
            }

    /** Scheme + host + effective port, with default ports made explicit for comparison. */
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

    /** A stable, collision-free id derived from the normalized URL. */
    private fun stableIdFor(normalizedUrl: String): String =
        "cat-" + UUID.nameUUIDFromBytes(normalizedUrl.toByteArray()).toString()
}
