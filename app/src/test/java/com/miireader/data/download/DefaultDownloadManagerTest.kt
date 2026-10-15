package com.miireader.data.download

import android.content.Context
import com.miireader.core.error.AppError
import com.miireader.core.error.ErrorHandler
import com.miireader.data.local.DownloadEntity
import com.miireader.data.local.DownloadStatus
import com.miireader.data.local.entity.BookEntity
import com.miireader.data.opds.CatalogLink
import com.miireader.test.FakeBookDownloader
import com.miireader.test.FakeBookRepository
import com.miireader.test.FakeDownloadRepository
import com.miireader.test.FakeEpubMetadataExtractor
import com.miireader.test.TestDispatchers
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Exercises the download pipeline end to end — fetch, validate, import — against a fake
 * downloader. Robolectric is needed only for `Context.filesDir`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DefaultDownloadManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var context: Context
    private lateinit var downloadRepository: FakeDownloadRepository
    private lateinit var bookRepository: FakeBookRepository
    private lateinit var metadataExtractor: FakeEpubMetadataExtractor
    private lateinit var downloader: FakeBookDownloader
    private lateinit var errorHandler: RecordingErrorHandler
    private lateinit var manager: DefaultDownloadManager

    private val url = "https://catalog.example.org/moby-dick.epub"
    private val body = "not really an epub, but the extractor is fake".toByteArray()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        downloadRepository = FakeDownloadRepository()
        bookRepository = FakeBookRepository()
        metadataExtractor = FakeEpubMetadataExtractor()
        downloader = FakeBookDownloader(mapOf(url to body))
        errorHandler = RecordingErrorHandler()
        manager = DefaultDownloadManager(
            context = context,
            downloadRepository = downloadRepository,
            bookRepository = bookRepository,
            metadataExtractor = metadataExtractor,
            downloader = downloader,
            errorHandler = errorHandler,
            dispatchers = TestDispatchers(testDispatcher)
        )
    }

    @After
    fun tearDown() {
        manager.shutdown()
    }

    private fun epubLink(mediaType: String = "application/epub+zip") =
        CatalogLink(
            href = url,
            rel = "http://opds-spec.org/acquisition",
            title = "Moby Dick",
            mediaType = mediaType
        )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `a downloaded epub is validated and imported into the library`() = testScope.runTest {
        val id = manager.enqueueDownload(epubLink(), "Moby Dick", "Herman Melville")!!
        testDispatcher.scheduler.advanceUntilIdle()

        val row = downloadRepository.getDownloadById(id)!!
        assertEquals(DownloadStatus.COMPLETED, row.status)
        assertEquals(1f, row.progress)
        assertEquals(body.size.toLong(), row.totalBytes)
        assertEquals(body.size.toLong(), row.downloadedBytes)
        assertNotNull(row.bookId)

        val book = bookRepository.getBookById(row.bookId!!)!!
        assertEquals("Downloaded Book", book.title)
        assertEquals(sha256(body), book.contentChecksum)
        assertEquals("DOWNLOADED", book.downloadStatus)
        assertTrue(File(book.filePath).exists())

        // The scratch file is gone once the book has moved into the library.
        assertFalse(File(context.filesDir, "downloads/$id.tmp").exists())
    }

    @Test
    fun `progress is reported while the file streams in`() = testScope.runTest {
        manager.enqueueDownload(epubLink(), "Moby Dick", null)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(downloader.progressReports.isNotEmpty())
        assertTrue(downloader.progressReports.any { it.first == body.size.toLong() })
        assertEquals(listOf(url), downloader.requestedUrls)
    }

    @Test
    fun `a file the epub engine refuses to open is marked failed`() = testScope.runTest {
        metadataExtractor.rejectAll = true

        val id = manager.enqueueDownload(epubLink(), "Moby Dick", null)!!
        testDispatcher.scheduler.advanceUntilIdle()

        val row = downloadRepository.getDownloadById(id)!!
        assertEquals(DownloadStatus.FAILED, row.status)
        assertNotNull(row.errorMessage)
        assertNull(row.bookId)
        assertEquals(0, bookRepository.getBookCount())
    }

    @Test
    fun `a network failure is recorded and can be retried`() = testScope.runTest {
        downloader.failure = IOException("The connection dropped.")
        val id = manager.enqueueDownload(epubLink(), "Moby Dick", null)!!
        testDispatcher.scheduler.advanceUntilIdle()

        val failed = downloadRepository.getDownloadById(id)!!
        assertEquals(DownloadStatus.FAILED, failed.status)
        assertEquals("The connection dropped.", failed.errorMessage)

        downloader.failure = null
        manager.retryDownload(id)
        testDispatcher.scheduler.advanceUntilIdle()

        val retried = downloadRepository.getDownloadById(id)!!
        assertEquals(DownloadStatus.COMPLETED, retried.status)
        assertNull(retried.errorMessage)
        assertNotNull(retried.bookId)
    }

    @Test
    fun `a download cancelled mid-flight is dropped instead of imported`() = testScope.runTest {
        val id = manager.enqueueDownload(epubLink(), "Moby Dick", null)!!
        // Runs between the two halves of the write, as if the user hit cancel.
        downloader.midwayHook = { downloadRepository.removeDownload(id) }
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(downloadRepository.getDownloadById(id))
        assertEquals(0, bookRepository.getBookCount())
        assertFalse(File(context.filesDir, "downloads/$id.tmp").exists())
    }

    @Test
    fun `a book we already have is not imported twice`() = testScope.runTest {
        bookRepository.insertBook(
            BookEntity(
                id = "existing",
                title = "Moby Dick",
                author = "Herman Melville",
                filePath = "/storage/moby.epub",
                contentChecksum = sha256(body)
            )
        )

        val id = manager.enqueueDownload(epubLink(), "Moby Dick", null)!!
        testDispatcher.scheduler.advanceUntilIdle()

        val row = downloadRepository.getDownloadById(id)!!
        assertEquals(DownloadStatus.COMPLETED, row.status)
        assertEquals("existing", row.bookId)
        assertEquals(1, bookRepository.getBookCount())
    }

    @Test
    fun `a format we cannot open is declined before any network call`() = testScope.runTest {
        val id = manager.enqueueDownload(epubLink(mediaType = "application/pdf"), "Moby Dick", null)

        assertNull(id)
        assertTrue(downloader.requestedUrls.isEmpty())
    }

    @Test
    fun `an interrupted transfer fails without importing partial bytes`() = testScope.runTest {
        val id = manager.enqueueDownload(epubLink(), "Moby Dick", null)!!
        // Bytes are already half written when the connection dies mid-transfer.
        downloader.midwayHook = { throw IOException("The connection broke mid-transfer.") }
        testDispatcher.scheduler.advanceUntilIdle()

        val row = downloadRepository.getDownloadById(id)!!
        assertEquals(DownloadStatus.FAILED, row.status)
        assertEquals("The connection broke mid-transfer.", row.errorMessage)
        assertNull(row.bookId)
        assertNull(row.tempFilePath)

        // Nothing half-finished reaches the library, and the scratch file is cleaned up.
        assertEquals(0, bookRepository.getBookCount())
        assertFalse(File(context.filesDir, "downloads/$id.tmp").exists())

        // The failure reaches the error stream typed as a network problem, once.
        val networkErrors = errorHandler.received.filterIsInstance<AppError.NetworkError>()
        assertEquals(
            listOf("The connection broke mid-transfer."),
            networkErrors.map { it.message }
        )
    }

    @Test
    fun `an interrupted transfer can be retried once the connection recovers`() =
        testScope.runTest {
            val id = manager.enqueueDownload(epubLink(), "Moby Dick", null)!!
            downloader.midwayHook = { throw IOException("reset by peer") }
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(DownloadStatus.FAILED, downloadRepository.getDownloadById(id)!!.status)

            downloader.midwayHook = {}
            manager.retryDownload(id)
            testDispatcher.scheduler.advanceUntilIdle()

            val retried = downloadRepository.getDownloadById(id)!!
            assertEquals(DownloadStatus.COMPLETED, retried.status)
            assertEquals(1f, retried.progress)
            assertNotNull(retried.bookId)
            assertEquals(1, bookRepository.getBookCount())
        }

    @Test
    fun `pending and interrupted downloads are recovered on startup`() =
        testScope.runTest {
            manager.shutdown()

            val queuedDownload = DownloadEntity(
                id = "d-queued",
                url = url,
                title = "Queued Book",
                author = "Author",
                mediaType = "application/epub+zip",
                status = DownloadStatus.QUEUED
            )
            val interruptedDownload = DownloadEntity(
                id = "d-interrupted",
                url = url,
                title = "Interrupted Book",
                author = "Author",
                mediaType = "application/epub+zip",
                status = DownloadStatus.DOWNLOADING
            )
            downloadRepository.upsertDownload(queuedDownload)
            downloadRepository.upsertDownload(interruptedDownload)

            val newManager = DefaultDownloadManager(
                context = context,
                downloadRepository = downloadRepository,
                bookRepository = bookRepository,
                metadataExtractor = metadataExtractor,
                downloader = downloader,
                errorHandler = errorHandler,
                dispatchers = TestDispatchers(testDispatcher)
            )

            testDispatcher.scheduler.advanceUntilIdle()

            val d1 = downloadRepository.getDownloadById("d-queued")!!
            val d2 = downloadRepository.getDownloadById("d-interrupted")!!
            assertEquals(DownloadStatus.COMPLETED, d1.status)
            assertEquals(DownloadStatus.COMPLETED, d2.status)

            newManager.shutdown()
        }
}

/**
 * [ErrorHandler] that keeps every emitted error so tests can assert on typing and
 * wording. A replay buffer means nothing is lost if a collector subscribes late.
 */
private class RecordingErrorHandler : ErrorHandler {
    private val _errors = MutableSharedFlow<AppError>(replay = 8, extraBufferCapacity = 8)
    override val errors: SharedFlow<AppError> = _errors
    val received = mutableListOf<AppError>()

    override suspend fun emitError(error: AppError) {
        received += error
        _errors.emit(error)
    }

    override fun tryEmitError(error: AppError): Boolean {
        received += error
        return _errors.tryEmit(error)
    }
}
