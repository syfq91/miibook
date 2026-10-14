package com.miireader.test

import com.miireader.data.local.entity.BookmarkEntity
import com.miireader.data.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeBookmarkRepository(
    initialBookmarks: List<BookmarkEntity> = emptyList()
) : BookmarkRepository {

    private val _bookmarks = MutableStateFlow(initialBookmarks)

    override fun getBookmarksForBook(bookId: String): Flow<List<BookmarkEntity>> =
        _bookmarks.map { list -> list.filter { it.bookId == bookId } }

    override suspend fun getBookmarkById(id: String): BookmarkEntity? =
        _bookmarks.value.find { it.id == id }

    override suspend fun addBookmark(bookmark: BookmarkEntity) {
        _bookmarks.value = _bookmarks.value.filterNot { it.id == bookmark.id } + bookmark
    }

    override suspend fun deleteBookmark(bookmark: BookmarkEntity) {
        deleteBookmarkById(bookmark.id)
    }

    override suspend fun deleteBookmarkById(id: String) {
        _bookmarks.value = _bookmarks.value.filterNot { it.id == id }
    }
}
