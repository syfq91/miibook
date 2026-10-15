package com.miireader.data.local

import android.database.sqlite.SQLiteDatabase
import com.miireader.data.local.entity.BookEntity
import com.miireader.data.local.entity.BookmarkEntity
import com.miireader.data.local.entity.ReadingProgressEntity
import kotlinx.coroutines.flow.first
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
 * Phase 6: database migration tests.
 *
 * The project deliberately declares **no** migrations — `AppDatabase.create` uses
 * `fallbackToDestructiveMigration(dropAllTables = true)`, which is acceptable
 * pre-release (see AGENTS.md). These tests pin that contract down so it cannot drift
 * silently:
 *
 *  - a database left behind by an older install (schema version 1 or 2) opens at the
 *    current version 3 **without crashing**, with every legacy row dropped,
 *  - a fresh database starts empty at version 3,
 *  - books, reading progress, and bookmarks survive closing and reopening the database,
 *    which is the process-death path the reader depends on.
 *
 * The legacy schemas are reconstructed from the historical `AppDatabase` versions in
 * git (v1: Phase 1 tables; v2: adds `books.content_checksum`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDatabaseMigrationTest {

    private val context = RuntimeEnvironment.getApplication()

    /** Must match `AppDatabase.DATABASE_NAME`, which is private. */
    private val databaseFile
        get() = context.getDatabasePath("miireader.db")

    @Before
    fun setUp() {
        context.deleteDatabase("miireader.db")
    }

    @After
    fun tearDown() {
        context.deleteDatabase("miireader.db")
    }

    // ------------------------------------------------------------ legacy schemas

    private fun createLegacyDatabase(version: Int) {
        databaseFile.parentFile?.mkdirs()
        val legacy = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        legacy.execSQL(
            """CREATE TABLE books (
                id TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                author TEXT NOT NULL,
                file_path TEXT NOT NULL,
                cover_path TEXT,
                import_date INTEGER NOT NULL,
                last_opened_date INTEGER,
                download_status TEXT NOT NULL
            )"""
        )
        if (version >= 2) {
            legacy.execSQL("ALTER TABLE books ADD COLUMN content_checksum TEXT")
        }
        legacy.execSQL(
            """CREATE TABLE reading_progress (
                book_id TEXT NOT NULL PRIMARY KEY,
                locator_json TEXT NOT NULL,
                progress_percentage REAL NOT NULL,
                last_updated INTEGER NOT NULL,
                FOREIGN KEY(book_id) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )"""
        )
        legacy.execSQL(
            "CREATE INDEX IF NOT EXISTS index_reading_progress_book_id ON reading_progress (book_id)"
        )
        legacy.execSQL(
            """CREATE TABLE bookmarks (
                id TEXT NOT NULL PRIMARY KEY,
                book_id TEXT NOT NULL,
                locator_json TEXT NOT NULL,
                title TEXT,
                chapter_title TEXT,
                created_at INTEGER NOT NULL,
                FOREIGN KEY(book_id) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )"""
        )
        legacy.execSQL(
            "CREATE INDEX IF NOT EXISTS index_bookmarks_book_id ON bookmarks (book_id)"
        )
        legacy.execSQL(
            """INSERT INTO books (id, title, author, file_path, import_date, download_status)
                VALUES ('legacy-1', 'Legacy Book', 'Old Author', '/tmp/legacy.epub', 1, 'LOCAL')"""
        )
        legacy.execSQL(
            """INSERT INTO reading_progress (book_id, locator_json, progress_percentage, last_updated)
                VALUES ('legacy-1', '{"href":"ch1"}', 42.0, 1)"""
        )
        legacy.version = version
        legacy.close()
    }

    private fun currentVersion(): Int {
        val db = SQLiteDatabase.openDatabase(
            databaseFile.path,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
        return db.version.also { db.close() }
    }

    // -------------------------------------------------------------------- tests

    @Test
    fun `a version 1 database opens without crashing and is wiped clean`() = runTest {
        createLegacyDatabase(version = 1)
        assertEquals(1, currentVersion())

        val db = AppDatabase.create(context)
        try {
            // The destructive fallback drops every legacy table instead of crashing on
            // a schema it has no migration for.
            assertEquals(0, db.bookDao().getBookCount())
            assertNull(db.readingProgressDao().getProgressForBook("legacy-1"))

            // ...and the recreated schema is fully usable afterwards.
            db.bookDao().insertBook(
                BookEntity(
                    id = "fresh-1",
                    title = "New Book",
                    author = "New Author",
                    filePath = "/tmp/new.epub"
                )
            )
            assertEquals(1, db.bookDao().getBookCount())
        } finally {
            db.close()
        }

        assertEquals(3, currentVersion())
    }

    @Test
    fun `a version 2 database with legacy rows is wiped and upgraded`() = runTest {
        createLegacyDatabase(version = 2)
        assertEquals(2, currentVersion())

        val db = AppDatabase.create(context)
        try {
            assertEquals(0, db.bookDao().getBookCount())
            assertNull(db.readingProgressDao().getProgressForBook("legacy-1"))

            // Tables introduced at version 3 (catalogs, downloads) must exist and work.
            val catalog = com.miireader.data.local.entity.CatalogEntity(
                id = "cat-1",
                url = "https://example.org/opds"
            )
            db.catalogDao().upsertCatalog(catalog)
            assertNotNull(db.catalogDao().getCatalogById("cat-1"))
            assertEquals(1, db.catalogDao().countCatalogs())
        } finally {
            db.close()
        }

        assertEquals(3, currentVersion())
    }

    @Test
    fun `a fresh install starts at version 3 with an empty library`() = runTest {
        val db = AppDatabase.create(context)
        try {
            assertEquals(0, db.bookDao().getBookCount())
            assertTrue(db.bookDao().getAllBooks().first().isEmpty())
        } finally {
            db.close()
        }

        assertEquals(3, currentVersion())
    }

    @Test
    fun `books progress and bookmarks survive closing and reopening the database`() =
        runTest {
            val book = BookEntity(
                id = "book-1",
                title = "Dune",
                author = "Frank Herbert",
                filePath = "/tmp/dune.epub"
            )

            val first = AppDatabase.create(context)
            try {
                first.bookDao().insertBook(book)
                first.readingProgressDao().upsertProgress(
                    ReadingProgressEntity(
                        bookId = book.id,
                        locatorJson = """{"href":"ch5.xhtml"}""",
                        progressPercentage = 37.5f
                    )
                )
                first.bookmarkDao().insertBookmark(
                    BookmarkEntity(
                        id = "bm-1",
                        bookId = book.id,
                        locatorJson = """{"href":"ch5.xhtml"}""",
                        title = "Saved here"
                    )
                )
            } finally {
                // RoomDatabase is not Closeable, so no `use { }` here — close explicitly.
                first.close()
            }

            // Reopen: this is what a process restart looks like to the database.
            val second = AppDatabase.create(context)
            try {
                val restored = second.bookDao().getBookById(book.id)
                assertNotNull(restored)
                assertEquals("Dune", restored!!.title)

                val progress = second.readingProgressDao().getProgressForBook(book.id)
                assertNotNull(progress)
                assertEquals(37.5f, progress!!.progressPercentage, 0.001f)
                assertEquals("""{"href":"ch5.xhtml"}""", progress.locatorJson)

                val bookmark = second.bookmarkDao().getBookmarkById("bm-1")
                assertNotNull(bookmark)
                assertEquals("Saved here", bookmark!!.title)
            } finally {
                second.close()
            }
        }
}
