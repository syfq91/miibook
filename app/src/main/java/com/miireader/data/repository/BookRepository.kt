package com.miireader.data.repository

import com.miireader.core.dispatchers.CoroutineDispatchers
import com.miireader.data.local.dao.BookDao
import com.miireader.data.local.entity.BookEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

interface BookRepository {
    fun getAllBooks(): Flow<List<BookEntity>>
    fun searchBooks(query: String): Flow<List<BookEntity>>
    fun getBookByIdFlow(id: String): Flow<BookEntity?>
    suspend fun getBookById(id: String): BookEntity?
    suspend fun insertBook(book: BookEntity)
    suspend fun insertBooks(books: List<BookEntity>)
    suspend fun updateBook(book: BookEntity)
    suspend fun deleteBook(book: BookEntity)
    suspend fun deleteBookById(id: String)
    suspend fun getBookCount(): Int
}

class DefaultBookRepository(
    private val bookDao: BookDao,
    private val dispatchers: CoroutineDispatchers
) : BookRepository {

    override fun getAllBooks(): Flow<List<BookEntity>> {
        return bookDao.getAllBooks().flowOn(dispatchers.io)
    }

    override fun searchBooks(query: String): Flow<List<BookEntity>> {
        return bookDao.searchBooks(query).flowOn(dispatchers.io)
    }

    override fun getBookByIdFlow(id: String): Flow<BookEntity?> {
        return bookDao.getBookByIdFlow(id).flowOn(dispatchers.io)
    }

    override suspend fun getBookById(id: String): BookEntity? {
        return withContext(dispatchers.io) {
            bookDao.getBookById(id)
        }
    }

    override suspend fun insertBook(book: BookEntity) {
        withContext(dispatchers.io) {
            bookDao.insertBook(book)
        }
    }

    override suspend fun insertBooks(books: List<BookEntity>) {
        withContext(dispatchers.io) {
            bookDao.insertBooks(books)
        }
    }

    override suspend fun updateBook(book: BookEntity) {
        withContext(dispatchers.io) {
            bookDao.updateBook(book)
        }
    }

    override suspend fun deleteBook(book: BookEntity) {
        withContext(dispatchers.io) {
            bookDao.deleteBook(book)
        }
    }

    override suspend fun deleteBookById(id: String) {
        withContext(dispatchers.io) {
            bookDao.deleteBookById(id)
        }
    }

    override suspend fun getBookCount(): Int {
        return withContext(dispatchers.io) {
            bookDao.getBookCount()
        }
    }
}
