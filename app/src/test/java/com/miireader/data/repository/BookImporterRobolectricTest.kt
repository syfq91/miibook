package com.miireader.data.repository

import android.content.Context
import android.net.Uri
import com.miireader.data.epub.ReadiumEpubMetadataExtractor
import com.miireader.data.local.AppDatabase
import com.miireader.test.EpubFixtures
import com.miireader.test.TestDispatchers
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * End-to-end import tests: real [DefaultBookImporter], real [ReadiumEpubMetadataExtractor]
 * and a real Room database, with a SAF-style `content://` URI fed through Robolectric's
 * [org.robolectric.shadows.ShadowContentResolver] input-stream registration.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookImporterRobolectricTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = TestDispatchers(testDispatcher)

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: DefaultBookRepository
    private lateinit var importer: DefaultBookImporter
    private lateinit var booksDir: File

    private val title = "Imported Fixture Book"
    private val author = "Grace H. Importer"
    private val epubBytes = EpubFixtures.minimalEpub(
        title = title,
        author = author,
        chapters = listOf("chapter-1" to "<p>Imported content.</p>")
    )

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        booksDir = File(context.filesDir, "books").apply { deleteRecursively() }
        db = AppDatabase.createInMemory(context)
        repository = DefaultBookRepository(db.bookDao(), dispatchers)
        importer = DefaultBookImporter(
            context = context,
            bookRepository = repository,
            metadataExtractor = ReadiumEpubMetadataExtractor(context, dispatchers),
            dispatchers = dispatchers
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * Registers a fresh input stream for the test document URI. Robolectric hands back the
     * same registered stream on every open, so this must be called again before each
     * import attempt that should see the content.
     */
    private fun registerDocument(bytes: ByteArray): Uri {
        val uri = Uri.parse("content://com.miireader.test/docs/1")
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(bytes))
        return uri
    }

    private fun listedFiles(): List<File> = booksDir.listFiles()?.toList().orEmpty()

    @Test
    fun `a valid epub is copied into private storage and listed`() = runTest(testDispatcher) {
        val uri = registerDocument(epubBytes)

        val result = importer.importBook(uri)

        assertTrue("got $result", result is ImportResult.Success)
        val book = (result as ImportResult.Success).book
        assertEquals(1, repository.getBookCount())
        assertEquals(1, repository.getAllBooks().first().size)

        val stored = File(book.filePath)
        assertTrue("stored file missing: $stored", stored.exists())
        assertEquals(booksDir.absolutePath, stored.parentFile?.absolutePath)
        assertTrue(stored.readBytes().contentEquals(epubBytes))

        val row = repository.getBookById(book.id)
        assertEquals(title, row?.title)
        assertEquals(author, row?.author)
    }

    @Test
    fun `importing the same content twice keeps a single copy`() = runTest(testDispatcher) {
        val first = importer.importBook(registerDocument(epubBytes))
        assertTrue("got $first", first is ImportResult.Success)

        val second = importer.importBook(registerDocument(epubBytes))

        assertTrue("got $second", second is ImportResult.Duplicate)
        val existing = (second as ImportResult.Duplicate).existingBook
        assertEquals((first as ImportResult.Success).book.id, existing.id)

        assertEquals(1, repository.getBookCount())
        val files = listedFiles()
        assertTrue(
            "orphaned temp files: ${files.map { it.name }}",
            files.none { it.name.startsWith("import_") }
        )
        assertEquals(1, files.size)
    }

    @Test
    fun `an invalid file is rejected without touching the library`() = runTest(testDispatcher) {
        val uri = registerDocument(EpubFixtures.zipWithoutContainer())

        val result = importer.importBook(uri)

        assertTrue("got $result", result is ImportResult.Failure)
        assertEquals(0, repository.getBookCount())
        assertEquals(0, repository.getAllBooks().first().size)
        val files = listedFiles()
        assertTrue(
            "leftover files: ${files.map { it.name }}",
            files.isEmpty()
        )
    }

    @Test
    fun `an empty file is rejected`() = runTest(testDispatcher) {
        val uri = registerDocument(ByteArray(0))

        val result = importer.importBook(uri)

        assertTrue("got $result", result is ImportResult.Failure)
        val reason = (result as ImportResult.Failure).reason
        assertTrue("reason was: $reason", reason.contains("empty"))
        assertEquals(0, repository.getBookCount())
        val files = listedFiles()
        assertTrue(
            "leftover files: ${files.map { it.name }}",
            files.isEmpty()
        )
    }
}
