package com.miireader.test

import com.miireader.data.local.entity.BookEntity
import com.miireader.data.repository.BookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class FakeBookRepository(
    initialBooks: List<BookEntity> = emptyList()
) : BookRepository {

    private val _books = MutableStateFlow(initialBooks)
    val booksFlow: Flow<List<BookEntity>> = _books.asStateFlow()

    override fun getAllBooks(): Flow<List<BookEntity>> = booksFlow

    override fun searchBooks(query: String): Flow<List<BookEntity>> {
        return _books.map { list ->
            list.filter { it.title.contains(query, ignoreCase = true) || it.author.contains(query, ignoreCase = true) }
        }
    }

    override fun getBookByIdFlow(id: String): Flow<BookEntity?> {
        return _books.map { list -> list.find { it.id == id } }
    }

    override suspend fun getBookById(id: String): BookEntity? {
        return _books.value.find { it.id == id }
    }

    override suspend fun getBookByChecksum(checksum: String): BookEntity? {
        return _books.value.find { it.contentChecksum == checksum }
    }

    override suspend fun insertBook(book: BookEntity) {
        val current = _books.value.filter { it.id != book.id }
        _books.value = current + book
    }

    override suspend fun insertBooks(books: List<BookEntity>) {
        val newIds = books.map { it.id }.toSet()
        val current = _books.value.filter { it.id !in newIds }
        _books.value = current + books
    }

    override suspend fun updateBook(book: BookEntity) {
        insertBook(book)
    }

    override suspend fun deleteBook(book: BookEntity) {
        deleteBookById(book.id)
    }

    override suspend fun deleteBookById(id: String) {
        _books.value = _books.value.filter { it.id != id }
    }

    override suspend fun getBookCount(): Int {
        return _books.value.size
    }
}
