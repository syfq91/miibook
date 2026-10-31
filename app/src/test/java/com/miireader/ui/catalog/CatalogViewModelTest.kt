package com.miireader.ui.catalog

import com.miireader.data.opds.CatalogCredentials
import com.miireader.data.opds.OpdsCatalogSource
import com.miireader.data.opds.OpdsFixtures
import com.miireader.test.FakeCatalogRepository
import com.miireader.test.FakeDownloadManager
import com.miireader.test.FakeOpdsHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Drives the catalog screen against a fake HTTP client.
 *
 * Robolectric is needed even without an Activity: the feed parser builds Readium URLs,
 * which are backed by `android.net.Uri`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CatalogViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var catalogRepository: FakeCatalogRepository
    private lateinit var httpClient: FakeOpdsHttpClient
    private lateinit var downloadManager: FakeDownloadManager
    private lateinit var viewModel: CatalogViewModel

    private val catalogUrl = OpdsFixtures.BASE_URL

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        catalogRepository = FakeCatalogRepository()
        httpClient = FakeOpdsHttpClient()
        downloadManager = FakeDownloadManager()

        viewModel = CatalogViewModel(
            catalogRepository = catalogRepository,
            catalogSource = OpdsCatalogSource(httpClient),
            downloadManager = downloadManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Keeps the stateIn(WhileSubscribed) sharing alive so `.value` is meaningful. */
    private fun TestScope.observeState() = launch { viewModel.uiState.collect {} }

    @Test
    fun `starts on an empty catalog list`() = testScope.runTest {
        val collectJob = observeState()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.savedCatalogs.isEmpty())
        assertFalse(state.isBrowsingFeed)
        assertNull(state.errorMessage)

        collectJob.cancel()
    }

    @Test
    fun `adding a catalog saves it under the feed title`() = testScope.runTest {
        httpClient.put(catalogUrl, OpdsFixtures.OPDS1_ATOM)

        val collectJob = observeState()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addCatalog(catalogUrl)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.savedCatalogs.size)
        assertEquals("Example Catalog", state.savedCatalogs.first().displayTitle)
        assertNull(state.errorMessage)
        assertFalse(state.isAddingCatalog)

        collectJob.cancel()
    }

    @Test
    fun `adding an unreachable catalog reports an error and saves nothing`() = testScope.runTest {
        val collectJob = observeState()
        testDispatcher.scheduler.advanceUntilIdle()

        // No body registered for this address, so the fake client throws.
        viewModel.addCatalog("https://catalog.example.org/missing")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.savedCatalogs.isEmpty())
        assertNotNull("expected an error message", state.errorMessage)
        assertFalse(state.isAddingCatalog)

        collectJob.cancel()
    }

    @Test
    fun `removing a catalog drops it from the list`() = testScope.runTest {
        httpClient.put(catalogUrl, OpdsFixtures.OPDS1_ATOM)

        val collectJob = observeState()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addCatalog(catalogUrl)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.removeCatalog(viewModel.uiState.value.savedCatalogs.first().id)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.savedCatalogs.isEmpty())

        collectJob.cancel()
    }

    @Test
    fun `opening a feed shows its entries and sub-feeds`() = testScope.runTest {
        httpClient.put(catalogUrl, OpdsFixtures.OPDS1_ATOM)

        val collectJob = observeState()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.openFeed(catalogUrl)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isBrowsingFeed)
        assertEquals("Example Catalog", state.feedTitle)
        assertEquals(catalogUrl, state.feedUrl)
        assertFalse(state.isLoadingFeed)
        assertNull(state.errorMessage)

        assertEquals(1, state.entries.size)
        assertEquals("Moby Dick", state.entries.first().title)
        assertEquals("Herman Melville", state.entries.first().author)
        assertEquals(2, state.entries.first().acquisitions.size)

        assertTrue("expected a sub-feed link", state.navigation.isNotEmpty())
        assertEquals("New Releases", state.navigation.first().title)

        collectJob.cancel()
    }

    @Test
    fun `opening a feed that cannot be read keeps the list on screen`() = testScope.runTest {
        val collectJob = observeState()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.openFeed("https://catalog.example.org/missing")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isBrowsingFeed)
        assertNotNull("expected an error message", state.errorMessage)
        assertFalse(state.isLoadingFeed)

        collectJob.cancel()
    }

    @Test
    fun `closing a feed returns to the list and is only reported once`() = testScope.runTest {
        httpClient.put(catalogUrl, OpdsFixtures.OPDS1_ATOM)

        val collectJob = observeState()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.openFeed(catalogUrl)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isBrowsingFeed)

        assertTrue("first close should consume the open feed", viewModel.closeFeed())
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isBrowsingFeed)

        assertFalse("already at the list level", viewModel.closeFeed())

        collectJob.cancel()
    }

    @Test
    fun `reloading refetches the feed on screen`() = testScope.runTest {
        httpClient.put(catalogUrl, OpdsFixtures.OPDS1_ATOM)

        val collectJob = observeState()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.openFeed(catalogUrl)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.refreshFeed()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, httpClient.requestedUrls.size)
        assertEquals(catalogUrl, viewModel.uiState.value.feedUrl)

        collectJob.cancel()
    }

    @Test
    fun `selecting an acquisition hands the link to the download queue`() = testScope.runTest {
        httpClient.put(catalogUrl, OpdsFixtures.OPDS1_ATOM)

        val collectJob = observeState()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.openFeed(catalogUrl)
        testDispatcher.scheduler.advanceUntilIdle()

        val acquisition = viewModel.uiState.value.entries.first().acquisitions.first()
        viewModel.enqueueDownload(
            url = acquisition.href,
            mediaType = acquisition.mediaType,
            title = "Moby Dick",
            author = "Herman Melville"
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(acquisition.href), downloadManager.enqueuedLinks.map { it.href })
        assertEquals(listOf("Moby Dick" to "Herman Melville"), downloadManager.enqueuedTitles)
        assertNull(viewModel.uiState.value.errorMessage)

        collectJob.cancel()
    }

    @Test
    fun `a download the queue declines is reported instead of silently dropped`() = testScope.runTest {
        // The real manager returns null for formats it cannot open; the fake mirrors that.
        downloadManager.nextEnqueueId = null

        val collectJob = observeState()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.enqueueDownload(
            url = "https://catalog.example.org/moby-dick.pdf",
            mediaType = "application/pdf",
            title = "Moby Dick",
            author = "Herman Melville"
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull("expected an error message", viewModel.uiState.value.errorMessage)

        collectJob.cancel()
    }

    @Test
    fun `a queueing failure is reported to the screen`() = testScope.runTest {
        downloadManager.enqueueFailure = IllegalStateException("queue is closed")

        val collectJob = observeState()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.enqueueDownload(
            url = "https://catalog.example.org/moby-dick.epub",
            mediaType = "application/epub+zip",
            title = "Moby Dick",
            author = null
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.errorMessage)

        viewModel.clearError()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.errorMessage)

        collectJob.cancel()
    }

    @Test
    fun `a saved catalog falls back to its host when no title is known`() = testScope.runTest {
        catalogRepository.addCatalog("https://gutenberg.example.org/opds", title = null)

        val collectJob = observeState()
        testDispatcher.scheduler.advanceUntilIdle()

        val item = viewModel.uiState.value.savedCatalogs.first()
        assertEquals("gutenberg.example.org", item.displayTitle)

        collectJob.cancel()
    }

    @Test
    fun `adding a catalog sends and saves optional credentials`() = testScope.runTest {
        httpClient.put(catalogUrl, OpdsFixtures.OPDS1_ATOM)

        val collectJob = observeState()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addCatalog(catalogUrl, username = " reader ", password = "s3cret")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            listOf(CatalogCredentials("reader", "s3cret")),
            httpClient.requestedCredentials
        )
        val saved = catalogRepository.catalogsFlow.first().single()
        assertEquals("reader", saved.username)
        assertEquals("s3cret", saved.password)
        assertNull(viewModel.uiState.value.errorMessage)

        collectJob.cancel()
    }

    @Test
    fun `adding a public catalog sends no credentials`() = testScope.runTest {
        httpClient.put(catalogUrl, OpdsFixtures.OPDS1_ATOM)

        val collectJob = observeState()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addCatalog(catalogUrl)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(null), httpClient.requestedCredentials)
        assertNull(catalogRepository.catalogsFlow.first().single().username)

        collectJob.cancel()
    }

    @Test
    fun `browsing a nested feed reuses the saved catalog credentials`() = testScope.runTest {
        catalogRepository.addCatalog(
            catalogUrl,
            title = "Example",
            username = "reader",
            password = "s3cret"
        )
        val nested = "https://catalog.example.org/opds/new"
        httpClient.put(nested, OpdsFixtures.OPDS1_ATOM)

        val collectJob = observeState()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.openFeed(nested)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            listOf(CatalogCredentials("reader", "s3cret")),
            httpClient.requestedCredentials
        )
        assertEquals("Example Catalog", viewModel.uiState.value.feedTitle)

        collectJob.cancel()
    }

    @Test
    fun `downloads carry the catalog credentials`() = testScope.runTest {
        catalogRepository.addCatalog(
            catalogUrl,
            title = "Example",
            username = "reader",
            password = "s3cret"
        )

        val collectJob = observeState()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.enqueueDownload(
            url = "https://catalog.example.org/download/moby-dick.epub",
            mediaType = "application/epub+zip",
            title = "Moby Dick",
            author = "Herman Melville"
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            listOf(CatalogCredentials("reader", "s3cret")),
            downloadManager.enqueuedCredentials
        )

        collectJob.cancel()
    }

    @Test
    fun `editing a catalog prefills the form without exposing the password`() = testScope.runTest {
        catalogRepository.addCatalog(
            catalogUrl,
            title = "Example",
            username = "reader",
            password = "s3cret"
        )

        val collectJob = observeState()
        testDispatcher.scheduler.advanceUntilIdle()

        val id = viewModel.uiState.value.savedCatalogs.single().id
        viewModel.startEditingCatalog(id)
        testDispatcher.scheduler.advanceUntilIdle()

        val editing = viewModel.uiState.value.editingCatalog
        assertNotNull(editing)
        assertEquals(catalogUrl, editing?.url)
        assertEquals("reader", editing?.username)
        assertEquals("Example", editing?.displayTitle)
        assertTrue("the form should know that a password is stored", editing?.hasPassword == true)

        collectJob.cancel()
    }

    @Test
    fun `saving an edit with a blank password keeps the stored one`() = testScope.runTest {
        catalogRepository.addCatalog(
            catalogUrl,
            title = "Example",
            username = "reader",
            password = "s3cret"
        )
        httpClient.put(catalogUrl, OpdsFixtures.OPDS1_ATOM)

        val collectJob = observeState()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startEditingCatalog(viewModel.uiState.value.savedCatalogs.single().id)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.saveEditedCatalog(url = catalogUrl, username = "reader", password = null)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            listOf(CatalogCredentials("reader", "s3cret")),
            httpClient.requestedCredentials
        )
        val saved = catalogRepository.catalogsFlow.first().single()
        assertEquals("reader", saved.username)
        assertEquals("s3cret", saved.password)
        assertNull("edit mode should end after a successful save", viewModel.uiState.value.editingCatalog)
        assertNull(viewModel.uiState.value.errorMessage)

        collectJob.cancel()
    }

    @Test
    fun `changing a catalog address updates it in place instead of duplicating it`() = testScope.runTest {
        val newUrl = "https://catalog.example.org/opds/v2"
        catalogRepository.addCatalog(catalogUrl, title = "Example")
        httpClient.put(newUrl, OpdsFixtures.OPDS1_ATOM)

        val collectJob = observeState()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startEditingCatalog(viewModel.uiState.value.savedCatalogs.single().id)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.saveEditedCatalog(url = newUrl, username = null, password = null)
        testDispatcher.scheduler.advanceUntilIdle()

        val catalogs = catalogRepository.catalogsFlow.first()
        assertEquals(1, catalogs.size)
        assertEquals(newUrl, catalogs.single().url)
        assertEquals(newUrl, viewModel.uiState.value.savedCatalogs.single().url)
        assertEquals(1, catalogRepository.updateCount)

        collectJob.cancel()
    }

    @Test
    fun `clearing the username during an edit makes the catalog public`() = testScope.runTest {
        catalogRepository.addCatalog(
            catalogUrl,
            title = "Example",
            username = "reader",
            password = "s3cret"
        )
        httpClient.put(catalogUrl, OpdsFixtures.OPDS1_ATOM)

        val collectJob = observeState()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startEditingCatalog(viewModel.uiState.value.savedCatalogs.single().id)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.saveEditedCatalog(url = catalogUrl, username = null, password = null)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(null), httpClient.requestedCredentials)
        val saved = catalogRepository.catalogsFlow.first().single()
        assertNull(saved.username)
        assertNull(saved.password)

        collectJob.cancel()
    }

    @Test
    fun `cancelling an edit leaves the saved catalog untouched`() = testScope.runTest {
        catalogRepository.addCatalog(catalogUrl, title = "Example")

        val collectJob = observeState()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startEditingCatalog(viewModel.uiState.value.savedCatalogs.single().id)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.cancelEditingCatalog()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.editingCatalog)
        assertEquals(catalogUrl, catalogRepository.catalogsFlow.first().single().url)
        assertTrue(httpClient.requestedUrls.isEmpty())

        collectJob.cancel()
    }

    @Test
    fun `an edit that cannot read the feed keeps the form open and stores nothing`() =
        testScope.runTest {
            val newUrl = "https://catalog.example.org/broken"
            catalogRepository.addCatalog(catalogUrl, title = "Example")

            val collectJob = observeState()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.startEditingCatalog(viewModel.uiState.value.savedCatalogs.single().id)
            testDispatcher.scheduler.advanceUntilIdle()

            // No body registered for newUrl, so the fake client rejects it.
            viewModel.saveEditedCatalog(url = newUrl, username = null, password = null)
            testDispatcher.scheduler.advanceUntilIdle()

            assertNotNull("the form must stay open so the address can be fixed", viewModel.uiState.value.editingCatalog)
            assertNotNull(viewModel.uiState.value.errorMessage)
            assertEquals(catalogUrl, catalogRepository.catalogsFlow.first().single().url)

            collectJob.cancel()
        }

    @Test
    fun `an invalid edited address is refused before any network call`() = testScope.runTest {
        catalogRepository.addCatalog(catalogUrl, title = "Example")

        val collectJob = observeState()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startEditingCatalog(viewModel.uiState.value.savedCatalogs.single().id)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.saveEditedCatalog(url = "not a url", username = null, password = null)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(httpClient.requestedUrls.isEmpty())
        assertNotNull(viewModel.uiState.value.editingCatalog)
        assertNotNull(viewModel.uiState.value.errorMessage)

        collectJob.cancel()
    }

    @Test
    fun `removing the catalog being edited ends edit mode`() = testScope.runTest {
        catalogRepository.addCatalog(catalogUrl, title = "Example")

        val collectJob = observeState()
        testDispatcher.scheduler.advanceUntilIdle()

        val id = viewModel.uiState.value.savedCatalogs.single().id
        viewModel.startEditingCatalog(id)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.editingCatalog)

        viewModel.removeCatalog(id)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.editingCatalog)
        assertTrue(viewModel.uiState.value.savedCatalogs.isEmpty())

        collectJob.cancel()
    }

    @Test
    fun `an edit whose row vanished is reported instead of resurrecting it`() = testScope.runTest {
        catalogRepository.addCatalog(catalogUrl, title = "Example")
        httpClient.put(catalogUrl, OpdsFixtures.OPDS1_ATOM)

        val collectJob = observeState()
        testDispatcher.scheduler.advanceUntilIdle()

        val id = viewModel.uiState.value.savedCatalogs.single().id
        viewModel.startEditingCatalog(id)
        testDispatcher.scheduler.advanceUntilIdle()

        // Removed behind the screen's back (for example by a background cleanup).
        catalogRepository.removeCatalog(id)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.saveEditedCatalog(url = catalogUrl, username = null, password = null)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.editingCatalog)
        assertTrue(catalogRepository.catalogsFlow.first().isEmpty())

        collectJob.cancel()
    }

    @Test
    fun `rejects a blank address before any network call`() = testScope.runTest {
        val collectJob = observeState()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addCatalog("   ")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(httpClient.requestedUrls.isEmpty())
        assertTrue(viewModel.uiState.value.savedCatalogs.isEmpty())

        collectJob.cancel()
    }
}
