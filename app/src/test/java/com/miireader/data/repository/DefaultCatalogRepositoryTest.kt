package com.miireader.data.repository

import com.miireader.data.local.dao.CatalogDao
import com.miireader.test.FakeCatalogDao
import com.miireader.test.TestDispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the behaviour the catalog list depends on: addresses are normalized before they
 * are stored, and adding the same catalog twice never produces a second entry.
 */
class DefaultCatalogRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = TestDispatchers(testDispatcher)
    private lateinit var fakeDao: CatalogDao
    private lateinit var repository: DefaultCatalogRepository

    @Before
    fun setUp() {
        fakeDao = FakeCatalogDao()
        repository = DefaultCatalogRepository(fakeDao, dispatchers)
    }

    @Test
    fun `adding a catalog stores the normalized address`() = runTest(testDispatcher) {
        val saved = repository.addCatalog("HTTPS://Catalog.Example.ORG:443/opds/root/")

        assertEquals("https://catalog.example.org/opds/root", saved?.url)
        assertEquals(1, fakeDao.countCatalogs())
    }

    @Test
    fun `adding the same address twice is a no-op`() = runTest(testDispatcher) {
        val first = repository.addCatalog("https://catalog.example.org/opds/root")
        val second = repository.addCatalog("https://catalog.example.org/opds/root/")

        assertEquals(1, fakeDao.countCatalogs())
        assertEquals(first?.id, second?.id)
    }

    @Test
    fun `addresses that differ keep distinct ids`() = runTest(testDispatcher) {
        val first = repository.addCatalog("https://catalog.example.org/opds/root")
        val second = repository.addCatalog("https://catalog.example.org/opds/other")

        assertNotEquals(first?.id, second?.id)
        assertEquals(2, fakeDao.countCatalogs())
    }

    @Test
    fun `query strings and non-default ports survive normalization`() = runTest(testDispatcher) {
        val saved = repository.addCatalog("https://catalog.example.org:8443/opds?profile=opds")

        assertEquals("https://catalog.example.org:8443/opds?profile=opds", saved?.url)
    }

    @Test
    fun `unusable addresses are rejected without touching storage`() = runTest(testDispatcher) {
        val rejected = listOf(
            "",
            "   ",
            "not a url",
            "ftp://catalog.example.org/opds",
            "https://",
            "file:///sdcard/books"
        )

        for (address in rejected) {
            assertNull("expected '$address' to be rejected", repository.addCatalog(address))
        }
        assertEquals(0, fakeDao.countCatalogs())
    }

    @Test
    fun `removing a catalog deletes only that entry`() = runTest(testDispatcher) {
        val keep = repository.addCatalog("https://catalog.example.org/keep")
        val drop = repository.addCatalog("https://catalog.example.org/drop")

        repository.removeCatalog(drop!!.id)

        val remaining = fakeDao.getAllCatalogs().first()
        assertEquals(1, remaining.size)
        assertEquals(keep?.id, remaining.first().id)
    }

    @Test
    fun `saved catalogs are listed newest first`() = runTest(testDispatcher) {
        repository.addCatalog("https://catalog.example.org/first")
        repository.addCatalog("https://catalog.example.org/second")

        val urls = fakeDao.getAllCatalogs().first().map { it.url }
        assertTrue(urls.contains("https://catalog.example.org/first"))
        assertEquals(2, urls.size)
    }

    @Test
    fun `title is stored when the caller knows the feed title`() = runTest(testDispatcher) {
        val saved = repository.addCatalog("https://catalog.example.org/opds", title = "  Example  ")

        assertEquals("Example", saved?.title)
        assertEquals(1, fakeDao.countCatalogs())
    }

    @Test
    fun `a blank title is stored as null`() = runTest(testDispatcher) {
        val saved = repository.addCatalog("https://catalog.example.org/opds", title = "   ")

        assertNull(saved?.title)
    }

    @Test
    fun `an unknown catalog id reads back as null`() = runTest(testDispatcher) {
        repository.addCatalog("https://catalog.example.org/opds")

        assertNull(repository.getCatalogById("cat-does-not-exist"))
    }

    @Test
    fun `basic credentials are stored with the catalog`() = runTest(testDispatcher) {
        val saved = repository.addCatalog(
            "https://catalog.example.org/opds",
            username = "  reader  ",
            password = "s3cret"
        )

        assertEquals("reader", saved?.username)
        assertEquals("s3cret", saved?.password)
        assertEquals("reader", fakeDao.getCatalogById(saved!!.id)?.username)
    }

    @Test
    fun `a blank username stores no credentials`() = runTest(testDispatcher) {
        val saved = repository.addCatalog(
            "https://catalog.example.org/opds",
            username = "   ",
            password = "should be ignored"
        )

        assertNull(saved?.username)
        assertNull(saved?.password)
    }

    @Test
    fun `re-adding a catalog refreshes its credentials instead of duplicating it`() =
        runTest(testDispatcher) {
            repository.addCatalog("https://catalog.example.org/opds", username = "old", password = "old")
            val updated = repository.addCatalog(
                "https://catalog.example.org/opds/",
                username = "new",
                password = "new"
            )

            assertEquals(1, fakeDao.countCatalogs())
            assertEquals("new", updated?.username)
            assertEquals("new", fakeDao.getCatalogById(updated!!.id)?.password)
        }

    @Test
    fun `a nested feed inherits the credentials saved for its origin`() = runTest(testDispatcher) {
        repository.addCatalog(
            "https://catalog.example.org/opds",
            username = "reader",
            password = "s3cret"
        )

        val match = repository.findCatalogForUrl("https://catalog.example.org/opds/books")

        assertEquals("reader", match?.username)
    }

    @Test
    fun `a sibling path on the same origin still gets the credentials`() = runTest(testDispatcher) {
        // BookFlow links to /opdsx3 outside the /opds path; the realm covers both.
        repository.addCatalog(
            "https://catalog.example.org/opds",
            username = "reader",
            password = "s3cret"
        )

        val match = repository.findCatalogForUrl("https://catalog.example.org/opdsx3")

        assertEquals("reader", match?.username)
    }

    @Test
    fun `credentials never cross to another host or port`() = runTest(testDispatcher) {
        repository.addCatalog(
            "https://catalog.example.org/opds",
            username = "reader",
            password = "s3cret"
        )

        assertNull(repository.findCatalogForUrl("https://other.example.org/opds"))
        assertNull(repository.findCatalogForUrl("https://catalog.example.org:8443/opds"))
        assertNull(repository.findCatalogForUrl("http://catalog.example.org/opds"))
    }

    @Test
    fun `a public catalog is never used as a credential match`() = runTest(testDispatcher) {
        repository.addCatalog("https://catalog.example.org/opds")

        assertNull(repository.findCatalogForUrl("https://catalog.example.org/opds/books"))
    }

    @Test
    fun `the most specific catalog path wins when two share an origin`() = runTest(testDispatcher) {
        repository.addCatalog(
            "https://catalog.example.org/opds",
            username = "general",
            password = "general"
        )
        repository.addCatalog(
            "https://catalog.example.org/opds/private",
            username = "private",
            password = "private"
        )

        assertEquals(
            "private",
            repository.findCatalogForUrl("https://catalog.example.org/opds/private/books")?.username
        )
        assertEquals(
            "general",
            repository.findCatalogForUrl("https://catalog.example.org/opds/books")?.username
        )
    }
}
