package com.miireader.data.repository

import com.miireader.core.dispatchers.CoroutineDispatchers
import com.miireader.data.local.dao.BookmarkDao
import com.miireader.data.local.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

interface BookmarkRepository {
    fun getBookmarksForBook(bookId: String): Flow<List<BookmarkEntity>>
    suspend fun getBookmarkById(id: String): BookmarkEntity?
    suspend fun addBookmark(bookmark: BookmarkEntity)
    suspend fun deleteBookmark(bookmark: BookmarkEntity)
    suspend fun deleteBookmarkById(id: String)
}

class DefaultBookmarkRepository(
    private val bookmarkDao: BookmarkDao,
    private val dispatchers: CoroutineDispatchers
) : BookmarkRepository {

    override fun getBookmarksForBook(bookId: String): Flow<List<BookmarkEntity>> {
        return bookmarkDao.getBookmarksForBook(bookId).flowOn(dispatchers.io)
    }

    override suspend fun getBookmarkById(id: String): BookmarkEntity? {
        return withContext(dispatchers.io) {
            bookmarkDao.getBookmarkById(id)
        }
    }

    override suspend fun addBookmark(bookmark: BookmarkEntity) {
        withContext(dispatchers.io) {
            bookmarkDao.insertBookmark(bookmark)
        }
    }

    override suspend fun deleteBookmark(bookmark: BookmarkEntity) {
        withContext(dispatchers.io) {
            bookmarkDao.deleteBookmark(bookmark)
        }
    }

    override suspend fun deleteBookmarkById(id: String) {
        withContext(dispatchers.io) {
            bookmarkDao.deleteBookmarkById(id)
        }
    }
}
