package com.miireader.ui.downloads

import com.miireader.data.local.DownloadStatus
import com.miireader.test.FakeDownloadManager
import com.miireader.test.FakeDownloadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var downloadRepository: FakeDownloadRepository
    private lateinit var downloadManager: FakeDownloadManager
    private lateinit var viewModel: DownloadsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        downloadRepository = FakeDownloadRepository()
        downloadManager = FakeDownloadManager()

        viewModel = DownloadsViewModel(
            downloadRepository = downloadRepository,
            downloadManager = downloadManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `shows empty state when no downloads`() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0, state.downloads.size)
        assertEquals(0, state.activeDownloads.size)
        assertEquals(0, state.completedDownloads.size)
        assertEquals(0, state.failedDownloads.size)
        assertNull(state.errorMessage)

        collectJob.cancel()
    }

    @Test
    fun `shows active downloads in downloading section`() = testScope.runTest {
        val downloading = FakeDownloadRepository.createTestDownload(
            id = "dl-1",
            status = DownloadStatus.DOWNLOADING,
            progress = 0.5f,
            title = "Downloading Book"
        )
        downloadRepository.addDownload(downloading)

        val collectJob = launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.downloads.size)
        assertEquals(1, state.activeDownloads.size)
        assertEquals(0, state.completedDownloads.size)
        assertEquals(0, state.failedDownloads.size)
        assertEquals("Downloading Book", state.activeDownloads.first().displayTitle)
        assertEquals(50, state.activeDownloads.first().progressPercent)

        collectJob.cancel()
    }

    @Test
    fun `shows completed downloads in completed section`() = testScope.runTest {
        val completed = FakeDownloadRepository.createTestDownload(
            id = "dl-2",
            status = DownloadStatus.COMPLETED,
            progress = 1f,
            title = "Completed Book"
        ).copy(bookId = "book-1")
        downloadRepository.addDownload(completed)

        val collectJob = launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.downloads.size)
        assertEquals(0, state.activeDownloads.size)
        assertEquals(1, state.completedDownloads.size)
        assertEquals(0, state.failedDownloads.size)
        assertEquals("Completed Book", state.completedDownloads.first().displayTitle)
        assertEquals("book-1", state.completedDownloads.first().bookId)

        collectJob.cancel()
    }

    @Test
    fun `shows failed downloads in failed section`() = testScope.runTest {
        val failed = FakeDownloadRepository.createTestDownload(
            id = "dl-3",
            status = DownloadStatus.FAILED,
            progress = 0f,
            title = "Failed Book"
        ).copy(errorMessage = "Network error")
        downloadRepository.addDownload(failed)

        val collectJob = launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.downloads.size)
        assertEquals(0, state.activeDownloads.size)
        assertEquals(0, state.completedDownloads.size)
        assertEquals(1, state.failedDownloads.size)
        assertEquals("Failed Book", state.failedDownloads.first().displayTitle)
        assertEquals("Network error", state.failedDownloads.first().errorMessage)

        collectJob.cancel()
    }

    @Test
    fun `retryDownload asks the manager to retry`() = testScope.runTest {
        val failed = FakeDownloadRepository.createTestDownload(
            id = "dl-4",
            status = DownloadStatus.FAILED
        )
        downloadRepository.addDownload(failed)

        val collectJob = launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.retryDownload("dl-4")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("dl-4"), downloadManager.retriedIds)
        assertNull(viewModel.uiState.value.errorMessage)

        collectJob.cancel()
    }

    @Test
    fun `cancelDownload asks the manager to cancel`() = testScope.runTest {
        val downloading = FakeDownloadRepository.createTestDownload(
            id = "dl-5",
            status = DownloadStatus.DOWNLOADING
        )
        downloadRepository.addDownload(downloading)

        val collectJob = launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.cancelDownload("dl-5")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("dl-5"), downloadManager.cancelledIds)

        collectJob.cancel()
    }

    @Test
    fun `removeDownload asks the manager to drop it from the queue`() = testScope.runTest {
        val completed = FakeDownloadRepository.createTestDownload(
            id = "dl-6",
            status = DownloadStatus.COMPLETED
        ).copy(bookId = "book-1")
        downloadRepository.addDownload(completed)

        val collectJob = launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.downloads.size)

        viewModel.removeDownload("dl-6")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("dl-6"), downloadManager.cancelledIds)
        assertNull(viewModel.uiState.value.errorMessage)

        collectJob.cancel()
    }

    @Test
    fun `clearError clears error message`() = testScope.runTest {
        downloadManager.retryFailure = IllegalStateException("write failed")

        val collectJob = launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.retryDownload("dl-missing")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(
            "expected an error message, got ${viewModel.uiState.value.errorMessage}",
            viewModel.uiState.value.errorMessage != null
        )

        viewModel.clearError()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.errorMessage)

        collectJob.cancel()
    }
}
