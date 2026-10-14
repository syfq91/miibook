package com.miireader.data.repository

import com.miireader.data.local.dao.BookDao
import com.miireader.data.local.entity.BookEntity
import com.miireader.test.TestDispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DefaultBookRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = TestDispatchers(testDispatcher)
    private lateinit var fakeDao: FakeBookDao
    private lateinit var repository: DefaultBookRepository

    private val testBook = BookEntity(
        id = "test-1",
        title = "Dune",
        author = "Frank Herbert",
        filePath = "/storage/dune.epub"
    )

    @Before
    fun setUp() {
        fakeDao = FakeBookDao()
        repository = DefaultBookRepository(fakeDao, dispatchers)
    }

    @Test
    fun insertAndGetBook() = runTest(testDispatcher) {
        repository.insertBook(testBook)
        assertEquals(1, repository.getBookCount())

        val retrieved = repository.getBookById("test-1")
        assertEquals("Dune", retrieved?.title)
        assertEquals("Frank Herbert", retrieved?.author)
    }

    @Test
    fun deleteBookRemovesItem() = runTest(testDispatcher) {
        repository.insertBook(testBook)
        assertEquals(1, repository.getBookCount())

        repository.deleteBookById("test-1")
        assertEquals(0, repository.getBookCount())
        assertNull(repository.getBookById("test-1"))
    }

    @Test
    fun searchBooksMatchesTitle() = runTest(testDispatcher) {
        repository.insertBook(testBook)
        repository.insertBook(
            BookEntity(
                id = "test-2",
                title = "Foundation",
                author = "Isaac Asimov",
                filePath = "/storage/foundation.epub"
            )
        )

        val results = repository.searchBooks("dune").first()
        assertEquals(1, results.size)
        assertEquals("Dune", results.first().title)
    }
}

class FakeBookDao : BookDao {
    private val items = MutableStateFlow<List<BookEntity>>(emptyList())

    override fun getAllBooks(): Flow<List<BookEntity>> = items

    override suspend fun getBookById(id: String): BookEntity? = items.value.find { it.id == id }

    override suspend fun getBookByChecksum(checksum: String): BookEntity? =
        items.value.find { it.contentChecksum == checksum }

    override fun getBookByIdFlow(id: String): Flow<BookEntity?> = items.map { list -> list.find { it.id == id } }

    override fun searchBooks(query: String): Flow<List<BookEntity>> {
        return items.map { list ->
            list.filter { it.title.contains(query, ignoreCase = true) || it.author.contains(query, ignoreCase = true) }
        }
    }

    override suspend fun insertBook(book: BookEntity) {
        items.value = items.value.filter { it.id != book.id } + book
    }

    override suspend fun insertBooks(books: List<BookEntity>) {
        val newIds = books.map { it.id }.toSet()
        items.value = items.value.filter { it.id !in newIds } + books
    }

    override suspend fun updateBook(book: BookEntity) {
        insertBook(book)
    }

    override suspend fun deleteBook(book: BookEntity) {
        deleteBookById(book.id)
    }

    override suspend fun deleteBookById(id: String) {
        items.value = items.value.filter { it.id != id }
    }

    override suspend fun getBookCount(): Int = items.value.size
}
