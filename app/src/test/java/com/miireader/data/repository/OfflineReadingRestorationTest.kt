package com.miireader.data.repository

import com.miireader.data.epub.ReadiumEpubPublicationRepository
import com.miireader.data.local.AppDatabase
import com.miireader.data.local.entity.BookEntity
import com.miireader.data.local.entity.BookmarkEntity
import com.miireader.data.local.entity.ReadingProgressEntity
import com.miireader.test.EpubFixtures
import com.miireader.test.TestDispatchers
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
 * Phase 6: offline reading and progress restoration, end to end against the real pieces.
 *
 * Unlike `ReaderViewModelTest` (which uses fakes for the whole stack), this exercises the
 * production Room database, the production repositories, and Readium opening a real EPUB
 * file that lives in app-private storage — with no network anywhere in the path, which is
 * the "offline" contract. Closing and reopening the database between the two halves
 * reproduces what a process restart looks like.
 *
 * Requires a device to verify the same flow through the actual reader screen; this pins
 * the storage layer only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineReadingRestorationTest {

    private val context = RuntimeEnvironment.getApplication()
    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = TestDispatchers(testDispatcher)

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        context.deleteDatabase("miireader.db")
        File(context.filesDir, "books").deleteRecursively()
        database = AppDatabase.create(context)
    }

    @After
    fun tearDown() {
        if (database.isOpen) database.close()
        context.deleteDatabase("miireader.db")
        File(context.filesDir, "books").deleteRecursively()
    }

    private fun writeSampleBook(): File {
        val dir = File(context.filesDir, "books").apply { mkdirs() }
        val file = File(dir, "offline-sample.epub")
        file.writeBytes(
            EpubFixtures.minimalEpub(
                title = "Offline Sample",
                author = "Ada Lovelace",
                chapters = listOf(
                    "c1" to "<html><body><h1>One</h1><p>First chapter.</p></body></html>",
                    "c2" to "<html><body><h1>Two</h1><p>Second chapter.</p></body></html>"
                )
            )
        )
        return file
    }

    @Test
    fun `a saved position and bookmark are restored after the database restarts`() =
        runTest(testDispatcher) {
            val file = writeSampleBook()
            val book = BookEntity(
                id = "book-1",
                title = "Offline Sample",
                author = "Ada Lovelace",
                filePath = file.absolutePath
            )
            database.bookDao().insertBook(book)

            val progress = DefaultProgressRepository(database.readingProgressDao(), dispatchers)
            val bookmarks = DefaultBookmarkRepository(database.bookmarkDao(), dispatchers)

            progress.saveProgress(
                ReadingProgressEntity(
                    bookId = book.id,
                    locatorJson = """{"href":"c2.xhtml","locations":[]}""",
                    progressPercentage = 55f
                )
            )
            bookmarks.addBookmark(
                BookmarkEntity(
                    id = "bm-1",
                    bookId = book.id,
                    locatorJson = """{"href":"c2.xhtml"}""",
                    title = "Second chapter"
                )
            )

            // --- process death: close everything and start over ---------------
            database.close()
            database = AppDatabase.create(context)

            val restoredProgress =
                DefaultProgressRepository(database.readingProgressDao(), dispatchers)
                    .getProgressForBook(book.id)
            assertNotNull(restoredProgress)
            assertEquals(55f, restoredProgress!!.progressPercentage, 0.001f)
            assertEquals("""{"href":"c2.xhtml","locations":[]}""", restoredProgress.locatorJson)

            val restoredBookmark =
                DefaultBookmarkRepository(database.bookmarkDao(), dispatchers)
                    .getBookmarkById("bm-1")
            assertNotNull(restoredBookmark)
            assertEquals("Second chapter", restoredBookmark!!.title)

            val restoredBook = database.bookDao().getBookById(book.id)
            assertNotNull(restoredBook)
            assertTrue(File(restoredBook!!.filePath).exists())
        }

    @Test
    fun `the stored file opens offline with its full table of contents`() =
        runTest(testDispatcher) {
            val file = writeSampleBook()

            val repository = ReadiumEpubPublicationRepository(context, dispatchers)
            val publication = repository.openPublication(file.absolutePath)
            try {
                assertEquals(2, publication.readingOrder.size)
                assertTrue(
                    "the table of contents should list the chapters",
                    publication.tableOfContents.isNotEmpty()
                )
                assertEquals("Offline Sample", publication.metadata.title)
            } finally {
                publication.close()
            }
        }

    @Test
    fun `deleting a book removes its progress and bookmarks with it`() = runTest(testDispatcher) {
        val file = writeSampleBook()
        val book = BookEntity(
            id = "book-2",
            title = "Doomed",
            author = "Nobody",
            filePath = file.absolutePath
        )
        database.bookDao().insertBook(book)
        database.readingProgressDao().upsertProgress(
            ReadingProgressEntity(bookId = book.id, locatorJson = "{}")
        )
        database.bookmarkDao().insertBookmark(
            BookmarkEntity(id = "bm-2", bookId = book.id, locatorJson = "{}")
        )

        database.bookDao().deleteBookById(book.id)

        // Foreign keys are ON in Room: no orphaned rows may linger.
        assertNull(database.readingProgressDao().getProgressForBook(book.id))
        assertTrue(database.bookmarkDao().getBookmarksForBook(book.id).first().isEmpty())
    }
}
